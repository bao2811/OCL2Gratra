package org.uet.dse.neo4jtgg.engine;

import org.uet.dse.neo4j.model.FullObjectSnapshot;
import org.uet.dse.neo4j.model.LinkState;
import org.uet.dse.neo4j.model.ObjectState;
import org.uet.dse.neo4jtgg.model.ImportBatch;
import org.uet.dse.neo4jtgg.model.ImportLinkSpec;
import org.uet.dse.neo4jtgg.model.ImportObjectSpec;
import org.uet.dse.neo4jtgg.model.TggRuleInfo;
import org.uet.dse.neo4jtgg.model.TggWorkspaceContext;
import org.uet.dse.neo4jtgg.model.TggWorkspaceDefinition;
import org.uet.dse.neo4jtgg.model.WorkspaceSide;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Rule-driven forward transformation engine.
 * Reads TGG rules from {@link TggWorkspaceDefinition} and applies them to the source snapshot
 * to produce target objects and correspondence objects.
 * <p>
 * Reuses the backtracking binding resolution pattern from {@code RuleDrivenCorrMaterializer}.
 */
public class ForwardRuleApplicationEngine {

    private final TransformationReport report;
    private final List<RuleApplication> appliedRules = new ArrayList<>();

    public ForwardRuleApplicationEngine(TransformationReport report) {
        this.report = report;
    }

    /**
     * Execute a full forward transformation from source to target using the rules in the workspace.
     *
     * @return an ImportBatch with all target objects and links to be written to Neo4j
     */
    public ImportBatch applyForward(TggWorkspaceContext context,
                                     FullObjectSnapshot sourceSnapshot,
                                     FullObjectSnapshot targetSnapshot,
                                     FullObjectSnapshot corrSnapshot) {
        return executeForward(context, sourceSnapshot, targetSnapshot, corrSnapshot).targetBatch();
    }

    public ForwardTransformationResult executeForward(TggWorkspaceContext context,
                                                      FullObjectSnapshot sourceSnapshot,
                                                      FullObjectSnapshot targetSnapshot,
                                                      FullObjectSnapshot corrSnapshot) {
        TggWorkspaceDefinition definition = context.getWorkspaceDefinition();
        ImportBatch targetBatch = new ImportBatch(WorkspaceSide.TARGET, context.getTggFile());

        SnapshotIndex sourceIndex = new SnapshotIndex(sourceSnapshot);
        SnapshotIndex targetIndex = new SnapshotIndex(targetSnapshot);
        Set<String> existingCorrObjectIds = extractExistingCorrObjectIds(corrSnapshot);
        Map<String, ForwardTransformationResult.CorrCreation> newCorrs = new LinkedHashMap<>();

        for (TggRuleInfo rule : definition.getRules()) {
            applyRule(rule, sourceIndex, targetIndex, existingCorrObjectIds, newCorrs, targetBatch, definition);
        }

        report.addInfo("Forward engine: applied " + appliedRules.size() + " rule application(s).");
        report.addInfo("Forward engine: produced " + targetBatch.getObjects().size() + " target object(s), "
                + targetBatch.getLinks().size() + " target link(s), "
                + newCorrs.size() + " correspondence(s).");

        return new ForwardTransformationResult(targetBatch, List.copyOf(newCorrs.values()));
    }

    /**
     * Get all correspondence records produced by this transformation run.
     */
    public List<RuleApplication> getAppliedRules() {
        return List.copyOf(appliedRules);
    }

    private void applyRule(TggRuleInfo rule,
                           SnapshotIndex sourceIndex,
                           SnapshotIndex targetIndex,
                           Set<String> existingCorrObjectIds,
                           Map<String, ForwardTransformationResult.CorrCreation> newCorrs,
                           ImportBatch targetBatch,
                           TggWorkspaceDefinition definition) {
        // Resolve source-side bindings by matching context variables against the source snapshot
        Map<String, String> sourceTypedVariables = rule.getTypedVariables(WorkspaceSide.SOURCE);
        List<TggRuleInfo.AssociationPattern> sourceAssociations = rule.getAssociationPatterns(WorkspaceSide.SOURCE);
        List<String> sourcePredicates = rule.getPredicates(WorkspaceSide.SOURCE);

        List<Map<String, String>> sourceBindings = RuleBindingResolver.resolveBindings(
                sourceTypedVariables, sourceAssociations, sourcePredicates, sourceIndex);

        if (sourceBindings.isEmpty()) {
            return;
        }

        // For each source binding, construct target objects
        Map<String, String> targetTypedVariables = rule.getTypedVariables(WorkspaceSide.TARGET);
        List<TggRuleInfo.AssociationPattern> targetAssociations = rule.getAssociationPatterns(WorkspaceSide.TARGET);

        for (Map<String, String> sourceBinding : sourceBindings) {
            Map<String, String> anticipatedTargetBinding = anticipateTargetBinding(
                    rule, sourceBinding, targetTypedVariables, sourceIndex, targetIndex);
            if (allOutputCorrespondencesExist(rule, sourceBinding, anticipatedTargetBinding, existingCorrObjectIds, newCorrs)) {
                continue;
            }

            // Construct target objects for CREATE variables
            Map<String, String> targetBinding = constructTargetObjects(
                    rule, sourceBinding, targetTypedVariables, targetAssociations,
                    sourceIndex, targetIndex, targetBatch);

            if (targetBinding.isEmpty() && !targetTypedVariables.isEmpty()) {
                continue;
            }

            // Record correspondence
            for (TggRuleInfo.CorrPattern corrPattern : rule.getOutputCorrPatterns()) {
                String sourceObjectId = sourceBinding.get(corrPattern.sourceVarName());
                String targetObjectId = targetBinding.get(corrPattern.targetVarName());
                if (sourceObjectId != null && targetObjectId != null) {
                    String corrObjectId = corrPattern.corrObjectName() + "_" + sourceObjectId + "_" + targetObjectId;
                    TracedCorrRecord record = TracedCorrRecord.create(
                            corrObjectId,
                            corrPattern.corrClassName(),
                            sourceObjectId,
                            sourceTypedVariables.getOrDefault(corrPattern.sourceVarName(), "Unknown"),
                            targetObjectId,
                            targetTypedVariables.getOrDefault(corrPattern.targetVarName(), "Unknown"),
                            rule.getName(),
                            TransformationDirection.FORWARD);
                    newCorrs.put(record.corrKey(), new ForwardTransformationResult.CorrCreation(
                            record,
                            Map.of(corrPattern.sourceVarName(), sourceObjectId),
                            Map.of(corrPattern.targetVarName(), targetObjectId)));
                    report.addCreatedCorrespondence(corrObjectId + " : " + corrPattern.corrClassName()
                            + " [" + sourceObjectId + " <-> " + targetObjectId + "]");
                }
            }

            // Record rule application
            appliedRules.add(new RuleApplication(
                    rule.getName(), sourceBinding, targetBinding, Map.of(),
                    TransformationDirection.FORWARD, System.currentTimeMillis()));
        }
    }

    private Map<String, String> anticipateTargetBinding(TggRuleInfo rule,
                                                        Map<String, String> sourceBinding,
                                                        Map<String, String> targetTypedVariables,
                                                        SnapshotIndex sourceIndex,
                                                        SnapshotIndex targetIndex) {
        Map<String, String> targetBinding = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : targetTypedVariables.entrySet()) {
            String varName = entry.getKey();
            String className = entry.getValue();
            String existingSingletonId = findExistingTargetObject(className, targetIndex);
            if (existingSingletonId != null) {
                targetBinding.put(varName, existingSingletonId);
                continue;
            }
            String objectId = deriveTargetObjectId(rule, varName, className, sourceBinding, sourceIndex);
            targetBinding.put(varName, objectId);
        }
        return targetBinding;
    }

    private boolean allOutputCorrespondencesExist(TggRuleInfo rule,
                                                  Map<String, String> sourceBinding,
                                                  Map<String, String> targetBinding,
                                                  Set<String> existingCorrObjectIds,
                                                  Map<String, ForwardTransformationResult.CorrCreation> newCorrs) {
        if (rule.getOutputCorrPatterns().isEmpty()) {
            return false;
        }
        for (TggRuleInfo.CorrPattern corrPattern : rule.getOutputCorrPatterns()) {
            String sourceObjectId = sourceBinding.get(corrPattern.sourceVarName());
            String targetObjectId = targetBinding.get(corrPattern.targetVarName());
            if (sourceObjectId == null || targetObjectId == null) {
                return false;
            }
            String corrObjectId = corrPattern.corrObjectName() + "_" + sourceObjectId + "_" + targetObjectId;
            if (!existingCorrObjectIds.contains(corrObjectId) && !newCorrs.containsKey(corrObjectId)) {
                return false;
            }
        }
        return true;
    }

    private Map<String, String> constructTargetObjects(TggRuleInfo rule,
                                                        Map<String, String> sourceBinding,
                                                        Map<String, String> targetTypedVariables,
                                                        List<TggRuleInfo.AssociationPattern> targetAssociations,
                                                        SnapshotIndex sourceIndex,
                                                        SnapshotIndex targetIndex,
                                                        ImportBatch targetBatch) {
        Map<String, String> targetBinding = new LinkedHashMap<>();

        for (Map.Entry<String, String> entry : targetTypedVariables.entrySet()) {
            String varName = entry.getKey();
            String className = entry.getValue();

            // Check if a matching target object already exists
            String existingId = findExistingTargetObject(className, targetIndex);
            if (existingId != null) {
                targetBinding.put(varName, existingId);
                report.addInfo("Rule `" + rule.getName() + "`: reusing existing target `" + existingId + "` : " + className);
                continue;
            }

            // Derive target object ID and attributes
            String objectId = deriveTargetObjectId(rule, varName, className, sourceBinding, sourceIndex);
            ObjectState existingObject = targetIndex.findObject(objectId);
            if (existingObject != null) {
                targetBinding.put(varName, objectId);
                report.addInfo("Rule `" + rule.getName() + "`: reusing existing target `" + objectId + "` : " + className);
                continue;
            }
            Map<String, String> attributes = deriveTargetAttributes(rule, varName, sourceBinding, sourceIndex);

            ImportObjectSpec spec = new ImportObjectSpec(objectId, className);
            for (Map.Entry<String, String> attr : attributes.entrySet()) {
                spec.getAttributes().put(attr.getKey(), attr.getValue());
            }

            targetBatch.addObject(spec);
            targetBinding.put(varName, objectId);
            targetIndex.registerObject(toObjectState(spec));
            report.addCreatedObject(objectId + " : " + className
                    + (attributes.isEmpty() ? "" : " " + attributes));
        }

        // Create target-side links
        for (TggRuleInfo.AssociationPattern assocPattern : targetAssociations) {
            String leftId = targetBinding.get(assocPattern.leftVarName());
            String rightId = targetBinding.get(assocPattern.rightVarName());
            if (leftId != null && rightId != null) {
                ImportLinkSpec link = new ImportLinkSpec(assocPattern.associationName(), List.of(leftId, rightId));
                targetBatch.addLink(link);
                targetIndex.registerLink(toLinkState(link));
                report.addCreatedLink(assocPattern.associationName() + " [" + leftId + ", " + rightId + "]");
            }
        }

        return targetBinding;
    }

    private String deriveTargetObjectId(TggRuleInfo rule,
                                         String varName,
                                         String className,
                                         Map<String, String> sourceBinding,
                                         SnapshotIndex sourceIndex) {
        String singletonId = deriveSingletonTargetId(className, sourceBinding);
        if (singletonId != null) {
            return singletonId;
        }

        Map<String, String> attributes = deriveTargetAttributes(rule, varName, sourceBinding, sourceIndex);
        String displayName = unquote(attributes.get("name"));
        if (displayName != null && !displayName.isBlank()) {
            return className.toLowerCase() + "_" + sanitizeId(displayName.replace(", ", "_"));
        }

        StringBuilder idBuilder = new StringBuilder();
        idBuilder.append(className.toLowerCase());
        for (String sourceObjectId : sourceBinding.values()) {
            idBuilder.append("_").append(sanitizeId(sourceObjectId));
        }
        return idBuilder.toString();
    }

    private String unquote(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if ((trimmed.startsWith("'") && trimmed.endsWith("'"))
                || (trimmed.startsWith("\"") && trimmed.endsWith("\""))) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    private String deriveSingletonTargetId(String className, Map<String, String> sourceBinding) {
        if (!"PersonRegister".equals(className)) {
            return null;
        }

        for (String sourceObjectId : sourceBinding.values()) {
            if (sourceObjectId == null || sourceObjectId.isBlank()) {
                continue;
            }
            if (sourceObjectId.startsWith("familyRegister")) {
                return "personRegister" + sourceObjectId.substring("familyRegister".length());
            }
            if (sourceObjectId.startsWith("familyregister_")) {
                return "personRegister_" + sourceObjectId.substring("familyregister_".length());
            }
        }

        return "personRegister1";
    }

    private Map<String, String> deriveTargetAttributes(TggRuleInfo rule,
                                                        String varName,
                                                        Map<String, String> sourceBinding,
                                                        SnapshotIndex sourceIndex) {
        Map<String, String> attributes = new LinkedHashMap<>();

        for (TggRuleInfo.AttributeMapping mapping : rule.getAttributeMappings()) {
            if (mapping.targetSide() != WorkspaceSide.TARGET) {
                continue;
            }
            if (!Objects.equals(mapping.targetVariableName(), varName)) {
                continue;
            }
            String computedValue = evaluateAssignmentExpression(mapping.sourceExpression(), sourceBinding, sourceIndex);
            if (computedValue != null) {
                attributes.put(mapping.targetAttributeName(), "'" + computedValue.replace("'", "") + "'");
            }
        }
        if (!attributes.isEmpty()) {
            return attributes;
        }

        // Evaluate correspondence invariants that reference this target variable
        for (TggRuleInfo.CorrInvariant invariant : rule.getCorrInvariants()) {
            AttributeAssignment assignment = AttributeAssignment.parse(invariant.expression());
            if (assignment != null) {
                String computedValue = evaluateAssignmentExpression(
                        assignment.sourceExpression(), sourceBinding, sourceIndex);
                if (computedValue != null) {
                    // Extract the attribute name from the target path (e.g., "self.mp.name" → "name")
                    String attrName = extractLastPathSegment(assignment.targetPath());
                    if (attrName != null) {
                        attributes.put(attrName, "'" + computedValue.replace("'", "") + "'");
                    }
                }
            }
        }

        return attributes;
    }

    private String evaluateAssignmentExpression(String expression,
                                                 Map<String, String> sourceBinding,
                                                 SnapshotIndex sourceIndex) {
        String trimmed = expression.trim();

        // Handle concatenation: expr1 + ', ' + expr2
        if (trimmed.contains(" + ")) {
            String[] parts = trimmed.split("\\s*\\+\\s*");
            StringBuilder result = new StringBuilder();
            for (String part : parts) {
                String partValue = evaluateSimpleExpression(part.trim(), sourceBinding, sourceIndex);
                if (partValue == null) {
                    return null;
                }
                result.append(partValue);
            }
            return result.toString();
        }

        return evaluateSimpleExpression(trimmed, sourceBinding, sourceIndex);
    }

    private String evaluateSimpleExpression(String expression,
                                             Map<String, String> sourceBinding,
                                             SnapshotIndex sourceIndex) {
        // String literal
        if ((expression.startsWith("'") && expression.endsWith("'"))
                || (expression.startsWith("\"") && expression.endsWith("\""))) {
            return expression.substring(1, expression.length() - 1);
        }

        // Navigation path: self.varName.attrName or varName.attrName
        String normalized = expression.startsWith("self.") ? expression.substring(5) : expression;
        String[] path = normalized.split("\\.");

        if (path.length == 0) {
            return null;
        }

        // Resolve the first token to a source object ID
        String firstToken = path[0].trim();
        String objectId = sourceBinding.get(firstToken);
        if (objectId == null) {
            // Try navigation reference name (e.g., "familyFather")
            for (Map.Entry<String, String> entry : sourceBinding.entrySet()) {
                ObjectState obj = sourceIndex.findObject(entry.getValue());
                if (obj != null) {
                    // Check if this object has a navigation matching firstToken
                    Object navRef = resolveNavigation(obj, firstToken, sourceIndex, sourceBinding);
                    if (navRef instanceof ObjectState navObj) {
                        objectId = navObj.name;
                        break;
                    }
                }
            }
            if (objectId == null) {
                return null;
            }
        }

        // Navigate the rest of the path
        Object current = sourceIndex.findObject(objectId);
        for (int i = 1; i < path.length && current instanceof ObjectState; i++) {
            String segment = path[i].trim();
            ObjectState objState = (ObjectState) current;

            // Try primitive attribute
            if (objState.primitiveValues.containsKey(segment)) {
                current = objState.primitiveValues.get(segment);
                continue;
            }

            // Try navigation/reference
            Object nav = resolveNavigation(objState, segment, sourceIndex, sourceBinding);
            if (nav != null) {
                current = nav;
                continue;
            }

            return null;
        }

        return current != null ? String.valueOf(current).trim() : null;
    }

    private Object resolveNavigation(ObjectState object,
                                     String navigationName,
                                     SnapshotIndex sourceIndex,
                                     Map<String, String> sourceBinding) {
        // Check object references
        if (object.objectReferences != null) {
            List<Object> refs = object.objectReferences.get(navigationName);
            if (refs != null && !refs.isEmpty()) {
                return sourceIndex.findObject(String.valueOf(refs.get(0)));
            }
        }

        // Check links
        for (LinkState link : sourceIndex.findLinksFrom(object.name)) {
            if (link.assocName.equalsIgnoreCase(navigationName) && link.participants.size() >= 2) {
                String targetId = link.participants.get(0).equals(object.name)
                        ? link.participants.get(1)
                        : link.participants.get(0);
                return sourceIndex.findObject(targetId);
            }
        }

        for (LinkState link : sourceIndex.findLinksFrom(object.name)) {
            if (link.participants.size() < 2) {
                continue;
            }
            for (String participant : link.participants) {
                if (participant.equals(object.name)) {
                    continue;
                }
                if (sourceBinding.containsValue(participant)) {
                    return sourceIndex.findObject(participant);
                }
            }
        }

        return null;
    }

    private String findExistingTargetObject(String className, SnapshotIndex targetIndex) {
        return RuleBindingResolver.findExistingSingleton(className, targetIndex);
    }

    // --- Corr key management ---

    private Set<String> extractExistingCorrObjectIds(FullObjectSnapshot corrSnapshot) {
        return new LinkedHashSet<>(corrSnapshot.objects.keySet());
    }

    // --- Utilities ---

    private String extractLastPathSegment(String path) {
        if (path == null) return null;
        int lastDot = path.lastIndexOf('.');
        return lastDot >= 0 ? path.substring(lastDot + 1).trim() : path.trim();
    }

    private String sanitizeId(String value) {
        return Objects.requireNonNullElse(value, "undefined")
                .replaceAll("[^A-Za-z0-9_]", "_");
    }

    private ObjectState toObjectState(ImportObjectSpec spec) {
        ObjectState state = new ObjectState();
        state.name = spec.getObjectName();
        state.className = spec.getClassName();
        state.primitiveValues = new LinkedHashMap<>(spec.getAttributes());
        state.objectReferences = new LinkedHashMap<>();
        return state;
    }

    private LinkState toLinkState(ImportLinkSpec spec) {
        LinkState state = new LinkState();
        state.assocName = spec.getAssociationName();
        state.participants = List.copyOf(spec.getEndpointNames());
        return state;
    }

}
