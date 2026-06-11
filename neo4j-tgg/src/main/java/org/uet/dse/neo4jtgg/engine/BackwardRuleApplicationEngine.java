package org.uet.dse.neo4jtgg.engine;

import org.uet.dse.neo4j.model.FullObjectSnapshot;
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
 * Rule-driven backward transformation engine.
 * Reads TGG rules and applies them in reverse: matching target patterns
 * to construct source objects and correspondences.
 * <p>
 * Uses shared {@link SnapshotIndex} and {@link RuleBindingResolver} to avoid
 * code duplication with {@link ForwardRuleApplicationEngine}.
 */
public class BackwardRuleApplicationEngine {

    private final TransformationReport report;
    private final List<RuleApplication> appliedRules = new ArrayList<>();

    public BackwardRuleApplicationEngine(TransformationReport report) {
        this.report = report;
    }

    public ImportBatch applyBackward(TggWorkspaceContext context,
                                      FullObjectSnapshot sourceSnapshot,
                                      FullObjectSnapshot targetSnapshot,
                                      FullObjectSnapshot corrSnapshot) {
        TggWorkspaceDefinition definition = context.getWorkspaceDefinition();
        ImportBatch sourceBatch = new ImportBatch(WorkspaceSide.SOURCE, context.getTggFile());

        SnapshotIndex targetIndex = new SnapshotIndex(targetSnapshot);
        SnapshotIndex sourceIndex = new SnapshotIndex(sourceSnapshot);
        Set<String> existingCorrKeys = extractCorrKeys(corrSnapshot);
        Map<String, TracedCorrRecord> newCorrs = new LinkedHashMap<>();

        for (TggRuleInfo rule : definition.getRules()) {
            applyRuleBackward(rule, targetIndex, sourceIndex, existingCorrKeys,
                    newCorrs, sourceBatch, definition);
        }

        report.addInfo("Backward engine: applied " + appliedRules.size() + " rule application(s).");
        report.addInfo("Backward engine: produced " + sourceBatch.getObjects().size() + " source object(s), "
                + sourceBatch.getLinks().size() + " source link(s), "
                + newCorrs.size() + " correspondence(s).");

        return sourceBatch;
    }

    public List<RuleApplication> getAppliedRules() {
        return List.copyOf(appliedRules);
    }

    private void applyRuleBackward(TggRuleInfo rule,
                                    SnapshotIndex targetIndex,
                                    SnapshotIndex sourceIndex,
                                    Set<String> existingCorrKeys,
                                    Map<String, TracedCorrRecord> newCorrs,
                                    ImportBatch sourceBatch,
                                    TggWorkspaceDefinition definition) {
        Map<String, String> targetTypedVariables = rule.getTypedVariables(WorkspaceSide.TARGET);
        List<TggRuleInfo.AssociationPattern> targetAssociations = rule.getAssociationPatterns(WorkspaceSide.TARGET);
        List<String> targetPredicates = rule.getPredicates(WorkspaceSide.TARGET);

        // Use shared binding resolver
        List<Map<String, String>> targetBindings = RuleBindingResolver.resolveBindings(
                targetTypedVariables, targetAssociations, targetPredicates, targetIndex);

        if (targetBindings.isEmpty()) {
            return;
        }

        Map<String, String> sourceTypedVariables = rule.getTypedVariables(WorkspaceSide.SOURCE);
        List<TggRuleInfo.AssociationPattern> sourceAssociations = rule.getAssociationPatterns(WorkspaceSide.SOURCE);

        for (Map<String, String> targetBinding : targetBindings) {
            String corrKey = RuleBindingResolver.buildCorrKey(rule.getName(), targetBinding);
            if (existingCorrKeys.contains(corrKey) || newCorrs.containsKey(corrKey)) {
                continue;
            }

            Map<String, String> sourceBinding = constructSourceObjects(
                    rule, targetBinding, sourceTypedVariables, sourceAssociations,
                    targetIndex, sourceIndex, sourceBatch);

            if (sourceBinding.isEmpty() && !sourceTypedVariables.isEmpty()) {
                continue;
            }

            // Record correspondences
            for (TggRuleInfo.CorrPattern corrPattern : rule.getOutputCorrPatterns()) {
                String sourceObjectId = sourceBinding.get(corrPattern.sourceVarName());
                String targetObjectId = targetBinding.get(corrPattern.targetVarName());
                if (sourceObjectId != null && targetObjectId != null) {
                    String corrObjectId = corrPattern.corrObjectName() + "_" + sourceObjectId + "_" + targetObjectId;
                    newCorrs.put(corrObjectId, TracedCorrRecord.create(
                            corrObjectId, corrPattern.corrClassName(),
                            sourceObjectId, sourceTypedVariables.getOrDefault(corrPattern.sourceVarName(), "Unknown"),
                            targetObjectId, targetTypedVariables.getOrDefault(corrPattern.targetVarName(), "Unknown"),
                            rule.getName(), TransformationDirection.BACKWARD));
                    report.addCreatedCorrespondence(corrObjectId + " : " + corrPattern.corrClassName());
                }
            }

            appliedRules.add(new RuleApplication(rule.getName(), sourceBinding, targetBinding,
                    Map.of(), TransformationDirection.BACKWARD, System.currentTimeMillis()));
        }
    }

    private Map<String, String> constructSourceObjects(TggRuleInfo rule,
                                                        Map<String, String> targetBinding,
                                                        Map<String, String> sourceTypedVariables,
                                                        List<TggRuleInfo.AssociationPattern> sourceAssociations,
                                                        SnapshotIndex targetIndex,
                                                        SnapshotIndex sourceIndex,
                                                        ImportBatch sourceBatch) {
        Map<String, String> sourceBinding = new LinkedHashMap<>();

        for (Map.Entry<String, String> entry : sourceTypedVariables.entrySet()) {
            String varName = entry.getKey();
            String className = entry.getValue();

            // Check if a matching source singleton already exists
            String existingId = RuleBindingResolver.findExistingSingleton(className, sourceIndex);
            if (existingId != null) {
                sourceBinding.put(varName, existingId);
                continue;
            }

            // Derive source object from target data
            String objectId = deriveSourceObjectId(className, targetBinding, targetIndex);
            Map<String, String> attributes = deriveSourceAttributes(rule, targetBinding, targetIndex);

            ImportObjectSpec spec = new ImportObjectSpec(objectId, className);
            for (Map.Entry<String, String> attr : attributes.entrySet()) {
                spec.getAttributes().put(attr.getKey(), attr.getValue());
            }
            sourceBatch.addObject(spec);
            sourceBinding.put(varName, objectId);
            report.addCreatedObject(objectId + " : " + className
                    + (attributes.isEmpty() ? "" : " " + attributes));
        }

        // Create source-side links
        for (TggRuleInfo.AssociationPattern assocPattern : sourceAssociations) {
            String leftId = sourceBinding.get(assocPattern.leftVarName());
            String rightId = sourceBinding.get(assocPattern.rightVarName());
            if (leftId != null && rightId != null) {
                sourceBatch.addLink(new ImportLinkSpec(assocPattern.associationName(), List.of(leftId, rightId)));
                report.addCreatedLink(assocPattern.associationName() + " [" + leftId + ", " + rightId + "]");
            }
        }

        return sourceBinding;
    }

    private String deriveSourceObjectId(String className,
                                         Map<String, String> targetBinding,
                                         SnapshotIndex targetIndex) {
        StringBuilder id = new StringBuilder(className.toLowerCase());
        for (String targetObjId : targetBinding.values()) {
            ObjectState targetObj = targetIndex.findObject(targetObjId);
            if (targetObj != null) {
                Object name = targetObj.primitiveValues.get("name");
                if (name != null) {
                    id.append("_").append(sanitizeId(String.valueOf(name)));
                }
            }
        }
        return id.toString();
    }

    private Map<String, String> deriveSourceAttributes(TggRuleInfo rule,
                                                        Map<String, String> targetBinding,
                                                        SnapshotIndex targetIndex) {
        Map<String, String> attributes = new LinkedHashMap<>();

        for (TggRuleInfo.CorrInvariant invariant : rule.getCorrInvariants()) {
            String expr = invariant.expression();
            if (expr.contains("split") && expr.contains("at(")) {
                extractNameFromSplitExpression(targetBinding, targetIndex, attributes);
            }
        }

        return attributes;
    }

    private void extractNameFromSplitExpression(Map<String, String> targetBinding,
                                                 SnapshotIndex targetIndex,
                                                 Map<String, String> attributes) {
        for (String targetObjId : targetBinding.values()) {
            ObjectState targetObj = targetIndex.findObject(targetObjId);
            if (targetObj == null) continue;

            Object nameValue = targetObj.primitiveValues.get("name");
            if (nameValue == null) continue;

            String fullName = String.valueOf(nameValue);
            String[] parts = fullName.split(",\\s*");
            if (parts.length >= 2) {
                attributes.put("name", "'" + parts[1].trim() + "'");
            } else if (parts.length == 1) {
                attributes.put("name", "'" + parts[0].trim() + "'");
            }
        }
    }

    private Set<String> extractCorrKeys(FullObjectSnapshot corrSnapshot) {
        Set<String> keys = new LinkedHashSet<>();
        for (ObjectState o : corrSnapshot.objects.values()) {
            keys.add(o.name);
        }
        return keys;
    }

    private String sanitizeId(String value) {
        return Objects.requireNonNullElse(value, "undefined").replaceAll("[^A-Za-z0-9_]", "_");
    }

}
