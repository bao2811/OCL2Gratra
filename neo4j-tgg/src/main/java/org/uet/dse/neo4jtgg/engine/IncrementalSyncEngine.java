package org.uet.dse.neo4jtgg.engine;

import org.uet.dse.neo4j.model.FullObjectSnapshot;
import org.uet.dse.neo4j.model.LinkState;
import org.uet.dse.neo4j.model.ObjectState;
import org.uet.dse.neo4jtgg.model.ImportLinkSpec;
import org.uet.dse.neo4jtgg.model.ImportObjectSpec;
import org.uet.dse.neo4jtgg.model.ImpactAnalysisResult;
import org.uet.dse.neo4jtgg.model.ModelDelta;
import org.uet.dse.neo4jtgg.model.ObjectChange;
import org.uet.dse.neo4jtgg.model.TggRuleInfo;
import org.uet.dse.neo4jtgg.model.TggWorkspaceContext;
import org.uet.dse.neo4jtgg.model.TggWorkspaceDefinition;
import org.uet.dse.neo4jtgg.model.WorkspaceMutationBatch;
import org.uet.dse.neo4jtgg.model.WorkspaceSide;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class IncrementalSyncEngine {

    private final TransformationReport report;
    private final List<SyncConflict> conflicts = new ArrayList<>();
    private final List<RuleApplication> appliedRules = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();
    private boolean requiresManualTransform;

    public IncrementalSyncEngine(TransformationReport report) {
        this.report = report;
    }

    public WorkspaceMutationBatch syncForward(TggWorkspaceContext context,
                                              ModelDelta sourceDelta,
                                              FullObjectSnapshot sourceSnapshot,
                                              FullObjectSnapshot targetSnapshot,
                                              FullObjectSnapshot corrSnapshot) {
        return syncForward(context, sourceDelta, sourceSnapshot, targetSnapshot, corrSnapshot, null);
    }

    public WorkspaceMutationBatch syncForward(TggWorkspaceContext context,
                                              ModelDelta sourceDelta,
                                              FullObjectSnapshot sourceSnapshot,
                                              FullObjectSnapshot targetSnapshot,
                                              FullObjectSnapshot corrSnapshot,
                                              ImpactAnalysisResult impactAnalysis) {
        TggWorkspaceDefinition definition = context.getWorkspaceDefinition();
        WorkspaceMutationBatch batch = new WorkspaceMutationBatch(WorkspaceSide.TARGET, context.getTggFile());
        SnapshotIndex sourceIndex = new SnapshotIndex(sourceSnapshot);
        SnapshotIndex targetIndex = new SnapshotIndex(targetSnapshot);

        for (ObjectChange added : sourceDelta.addedObjects()) {
            handleAddedObjectForward(added, definition, sourceIndex, targetIndex, batch);
        }
        for (ObjectChange modified : sourceDelta.modifiedObjects()) {
            handleModifiedObjectForward(modified, definition, sourceIndex, targetIndex, corrSnapshot, batch, impactAnalysis);
        }
        for (ObjectChange deleted : sourceDelta.deletedObjects()) {
            handleDeletedObjectForward(deleted, sourceSnapshot, targetSnapshot, corrSnapshot, batch);
        }
        for (ImportLinkSpec link : translateAddedLinks(sourceDelta.addedLinks(), definition, sourceSnapshot, targetSnapshot, corrSnapshot,
                WorkspaceSide.SOURCE, WorkspaceSide.TARGET)) {
            batch.addUpsertLink(link);
        }
        for (ImportLinkSpec link : translateAddedLinks(sourceDelta.deletedLinks(), definition, sourceSnapshot, targetSnapshot, corrSnapshot,
                WorkspaceSide.SOURCE, WorkspaceSide.TARGET)) {
            batch.addDeleteLink(link);
        }

        report.addInfo("Incremental forward sync processed " + sourceDelta.totalChanges() + " source-side change(s).");
        return batch;
    }

    public WorkspaceMutationBatch syncBackward(TggWorkspaceContext context,
                                               ModelDelta targetDelta,
                                               FullObjectSnapshot sourceSnapshot,
                                               FullObjectSnapshot targetSnapshot,
                                               FullObjectSnapshot corrSnapshot) {
        return syncBackward(context, targetDelta, sourceSnapshot, targetSnapshot, corrSnapshot, null);
    }

    public WorkspaceMutationBatch syncBackward(TggWorkspaceContext context,
                                               ModelDelta targetDelta,
                                               FullObjectSnapshot sourceSnapshot,
                                               FullObjectSnapshot targetSnapshot,
                                               FullObjectSnapshot corrSnapshot,
                                               ImpactAnalysisResult impactAnalysis) {
        TggWorkspaceDefinition definition = context.getWorkspaceDefinition();
        WorkspaceMutationBatch batch = new WorkspaceMutationBatch(WorkspaceSide.SOURCE, context.getTggFile());
        SnapshotIndex sourceIndex = new SnapshotIndex(sourceSnapshot);
        SnapshotIndex targetIndex = new SnapshotIndex(targetSnapshot);

        for (ObjectChange added : targetDelta.addedObjects()) {
            handleAddedObjectBackward(added, definition, sourceIndex, targetIndex, batch);
        }
        for (ObjectChange modified : targetDelta.modifiedObjects()) {
            handleModifiedObjectBackward(modified, definition, sourceIndex, corrSnapshot, batch, impactAnalysis);
        }
        for (ObjectChange deleted : targetDelta.deletedObjects()) {
            handleDeletedObjectBackward(deleted, sourceSnapshot, targetSnapshot, corrSnapshot, batch);
        }
        for (ImportLinkSpec link : translateAddedLinks(targetDelta.addedLinks(), definition, targetSnapshot, sourceSnapshot, corrSnapshot,
                WorkspaceSide.TARGET, WorkspaceSide.SOURCE)) {
            batch.addUpsertLink(link);
        }
        for (ImportLinkSpec link : translateAddedLinks(targetDelta.deletedLinks(), definition, targetSnapshot, sourceSnapshot, corrSnapshot,
                WorkspaceSide.TARGET, WorkspaceSide.SOURCE)) {
            batch.addDeleteLink(link);
        }

        report.addInfo("Incremental backward sync processed " + targetDelta.totalChanges() + " target-side change(s).");
        return batch;
    }

    public List<SyncConflict> getConflicts() {
        return List.copyOf(conflicts);
    }

    public List<RuleApplication> getAppliedRules() {
        return List.copyOf(appliedRules);
    }

    public List<String> getWarnings() {
        return List.copyOf(warnings);
    }

    public boolean requiresManualTransform() {
        return requiresManualTransform;
    }

    private void handleAddedObjectForward(ObjectChange added,
                                          TggWorkspaceDefinition definition,
                                          SnapshotIndex sourceIndex,
                                          SnapshotIndex targetIndex,
                                          WorkspaceMutationBatch batch) {
        for (TggRuleInfo rule : findRulesForClass(added.className(), WorkspaceSide.SOURCE, definition)) {
            Map<String, String> sourceBinding = bindSingleObject(rule, WorkspaceSide.SOURCE, added.objectId());
            Map<String, String> created = new LinkedHashMap<>();
            for (Map.Entry<String, String> targetEntry : rule.getTypedVariables(WorkspaceSide.TARGET).entrySet()) {
                String objectId = deriveObjectId(targetEntry.getValue(), sourceBinding.values());
                ImportObjectSpec spec = new ImportObjectSpec(objectId, targetEntry.getValue());
                deriveTargetAttributes(rule, sourceBinding, sourceIndex).forEach(spec.getAttributes()::put);
                batch.addUpsertObject(spec);
                created.put(targetEntry.getKey(), objectId);
                report.addCreatedObject(objectId + " : " + targetEntry.getValue() + " (incremental add)");
            }
            addAssociationLinks(rule.getAssociationPatterns(WorkspaceSide.TARGET), created, batch);
            appliedRules.add(new RuleApplication(rule.getName(), sourceBinding, created, Map.of(),
                    TransformationDirection.FORWARD, System.currentTimeMillis()));
            return;
        }
        markManual("No forward rule found for added source object `" + added.objectId() + "` : " + added.className() + ".");
    }

    private void handleAddedObjectBackward(ObjectChange added,
                                           TggWorkspaceDefinition definition,
                                           SnapshotIndex sourceIndex,
                                           SnapshotIndex targetIndex,
                                           WorkspaceMutationBatch batch) {
        for (TggRuleInfo rule : findRulesForClass(added.className(), WorkspaceSide.TARGET, definition)) {
            Map<String, String> targetBinding = bindSingleObject(rule, WorkspaceSide.TARGET, added.objectId());
            Map<String, String> created = new LinkedHashMap<>();
            for (Map.Entry<String, String> sourceEntry : rule.getTypedVariables(WorkspaceSide.SOURCE).entrySet()) {
                String objectId = deriveObjectId(sourceEntry.getValue(), targetBinding.values());
                ImportObjectSpec spec = new ImportObjectSpec(objectId, sourceEntry.getValue());
                deriveSourceAttributes(rule, targetBinding, targetIndex).forEach(spec.getAttributes()::put);
                batch.addUpsertObject(spec);
                created.put(sourceEntry.getKey(), objectId);
                report.addCreatedObject(objectId + " : " + sourceEntry.getValue() + " (incremental add)");
            }
            addAssociationLinks(rule.getAssociationPatterns(WorkspaceSide.SOURCE), created, batch);
            appliedRules.add(new RuleApplication(rule.getName(), created, targetBinding, Map.of(),
                    TransformationDirection.BACKWARD, System.currentTimeMillis()));
            return;
        }
        markManual("No backward rule found for added target object `" + added.objectId() + "` : " + added.className() + ".");
    }

    private void handleModifiedObjectForward(ObjectChange modified,
                                             TggWorkspaceDefinition definition,
                                             SnapshotIndex sourceIndex,
                                             SnapshotIndex targetIndex,
                                             FullObjectSnapshot corrSnapshot,
                                             WorkspaceMutationBatch batch,
                                             ImpactAnalysisResult impactAnalysis) {
        List<CorrLink> corrLinks = findCorrLinksForSource(modified.objectId(), corrSnapshot, definition);
        if (corrLinks.isEmpty()) {
            markManual("Modified source object `" + modified.objectId() + "` has no correspondence binding.");
            return;
        }

        for (CorrLink corrLink : corrLinks) {
            ObjectState targetObj = targetIndex.findObject(corrLink.targetObjectId());
            if (targetObj == null) {
                markManual("Corresponding target object `" + corrLink.targetObjectId() + "` is missing.");
                continue;
            }
            TggRuleInfo rule = definition.getRuleByName(corrLink.ruleName());
            if (rule == null) {
                markManual("Cannot resolve rule metadata for correspondence `" + corrLink.corrObjectId() + "`.");
                continue;
            }

            ImportObjectSpec spec = new ImportObjectSpec(corrLink.targetObjectId(), targetObj.className);
            Map<String, String> sourceBinding = resolveBinding(
                    corrLink.corrObjectId(), corrSnapshot, rule, WorkspaceSide.SOURCE,
                    sourceIndex, modified.objectId(), modified.className());
            for (String attributeName : changedAttributes(modified)) {
                if (!canSafelyPropagateAttribute(rule, attributeName, impactAnalysis, WorkspaceSide.SOURCE)) {
                    markManual("Changed source attribute `" + attributeName + "` on `" + modified.objectId()
                            + "` is not safely mappable by rule `" + rule.getName() + "`.");
                    continue;
                }
                Map<String, String> recomputed = deriveTargetAttributesForChangedSourceAttribute(rule, sourceBinding, sourceIndex, attributeName);
                for (Map.Entry<String, String> entry : recomputed.entrySet()) {
                    if (targetObj.primitiveValues.containsKey(entry.getKey())) {
                        spec.getAttributes().put(entry.getKey(), entry.getValue());
                    }
                }
            }
            if (!spec.getAttributes().isEmpty()) {
                batch.addUpsertObject(spec);
                report.addUpdatedObject(corrLink.targetObjectId() + " " + spec.getAttributes() + " (propagated from source)");
            }
        }
    }

    private void handleModifiedObjectBackward(ObjectChange modified,
                                              TggWorkspaceDefinition definition,
                                              SnapshotIndex sourceIndex,
                                              FullObjectSnapshot corrSnapshot,
                                              WorkspaceMutationBatch batch,
                                              ImpactAnalysisResult impactAnalysis) {
        List<CorrLink> corrLinks = findCorrLinksForTarget(modified.objectId(), corrSnapshot, definition);
        if (corrLinks.isEmpty()) {
            markManual("Modified target object `" + modified.objectId() + "` has no correspondence binding.");
            return;
        }

        for (CorrLink corrLink : corrLinks) {
            ObjectState sourceObj = sourceIndex.findObject(corrLink.sourceObjectId());
            if (sourceObj == null) {
                markManual("Corresponding source object `" + corrLink.sourceObjectId() + "` is missing.");
                continue;
            }
            TggRuleInfo rule = definition.getRuleByName(corrLink.ruleName());
            if (rule == null) {
                markManual("Cannot resolve rule metadata for correspondence `" + corrLink.corrObjectId() + "`.");
                continue;
            }

            ImportObjectSpec spec = new ImportObjectSpec(corrLink.sourceObjectId(), sourceObj.className);
            for (String attributeName : changedAttributes(modified)) {
                if (!canSafelyPropagateAttribute(rule, attributeName, impactAnalysis, WorkspaceSide.TARGET)) {
                    markManual("Changed target attribute `" + attributeName + "` on `" + modified.objectId()
                            + "` is not safely mappable by rule `" + rule.getName() + "`.");
                    continue;
                }
                for (String sourceAttribute : sourceAttributesForTargetAttribute(rule, attributeName, impactAnalysis)) {
                    if (sourceObj.primitiveValues.containsKey(sourceAttribute) && modified.currentAttributes().containsKey(attributeName)) {
                        spec.getAttributes().put(sourceAttribute, quoteValue(modified.currentAttributes().get(attributeName)));
                    }
                }
            }
            if (!spec.getAttributes().isEmpty()) {
                batch.addUpsertObject(spec);
                report.addUpdatedObject(corrLink.sourceObjectId() + " " + spec.getAttributes() + " (propagated from target)");
            }
        }
    }

    private void handleDeletedObjectForward(ObjectChange deleted,
                                            FullObjectSnapshot sourceSnapshot,
                                            FullObjectSnapshot targetSnapshot,
                                            FullObjectSnapshot corrSnapshot,
                                            WorkspaceMutationBatch batch) {
        for (CorrLink corrLink : findCorrLinksForSource(deleted.objectId(), corrSnapshot, null)) {
            batch.addDeleteObjectId(corrLink.targetObjectId());
            batch.addDeleteObjectId(corrLink.corrObjectId());
            report.addInfo("DELETE propagation: source `" + deleted.objectId()
                    + "` deleted -> target `" + corrLink.targetObjectId() + "` and corr `" + corrLink.corrObjectId() + "` scheduled for deletion.");
        }
    }

    private void handleDeletedObjectBackward(ObjectChange deleted,
                                             FullObjectSnapshot sourceSnapshot,
                                             FullObjectSnapshot targetSnapshot,
                                             FullObjectSnapshot corrSnapshot,
                                             WorkspaceMutationBatch batch) {
        for (CorrLink corrLink : findCorrLinksForTarget(deleted.objectId(), corrSnapshot, null)) {
            batch.addDeleteObjectId(corrLink.sourceObjectId());
            batch.addDeleteObjectId(corrLink.corrObjectId());
            report.addInfo("DELETE propagation: target `" + deleted.objectId()
                    + "` deleted -> source `" + corrLink.sourceObjectId() + "` and corr `" + corrLink.corrObjectId() + "` scheduled for deletion.");
        }
    }

    private List<ImportLinkSpec> translateAddedLinks(List<org.uet.dse.neo4jtgg.model.LinkChange> changes,
                                                     TggWorkspaceDefinition definition,
                                                     FullObjectSnapshot drivingSnapshot,
                                                     FullObjectSnapshot receiverSnapshot,
                                                     FullObjectSnapshot corrSnapshot,
                                                     WorkspaceSide drivingSide,
                                                     WorkspaceSide receiverSide) {
        List<ImportLinkSpec> results = new ArrayList<>();
        for (org.uet.dse.neo4jtgg.model.LinkChange change : changes) {
            for (TggRuleInfo rule : definition.getRules()) {
                for (TggRuleInfo.AssociationPattern pattern : rule.getAssociationPatterns(drivingSide)) {
                    if (!pattern.associationName().equals(change.associationName())) {
                        continue;
                    }
                    List<String> mapped = mapEndpoints(change.participants(), corrSnapshot, rule, drivingSide);
                    if (mapped.size() != change.participants().size()) {
                        markManual("Link `" + change.associationName() + "` could not be mapped safely using rule `" + rule.getName() + "`.");
                        continue;
                    }
                    for (TggRuleInfo.AssociationPattern receiverPattern : rule.getAssociationPatterns(receiverSide)) {
                        results.add(new ImportLinkSpec(receiverPattern.associationName(), mapped));
                        report.addCreatedLink(receiverPattern.associationName() + " " + mapped + " (incremental link propagation)");
                    }
                }
            }
        }
        return results;
    }

    private List<String> mapEndpoints(List<String> endpointIds,
                                      FullObjectSnapshot corrSnapshot,
                                      TggRuleInfo rule,
                                      WorkspaceSide drivingSide) {
        List<String> mapped = new ArrayList<>();
        for (String endpointId : endpointIds) {
            List<CorrLink> links = drivingSide == WorkspaceSide.SOURCE
                    ? findCorrLinksForSource(endpointId, corrSnapshot, null)
                    : findCorrLinksForTarget(endpointId, corrSnapshot, null);
            CorrLink matched = links.stream()
                    .filter(link -> rule.getName().equals(link.ruleName()) || link.ruleName().isBlank())
                    .findFirst()
                    .orElse(null);
            if (matched == null) {
                return List.of();
            }
            mapped.add(drivingSide == WorkspaceSide.SOURCE ? matched.targetObjectId() : matched.sourceObjectId());
        }
        return mapped;
    }

    private List<String> changedAttributes(ObjectChange change) {
        Set<String> names = new LinkedHashSet<>();
        names.addAll(change.previousAttributes().keySet());
        names.addAll(change.currentAttributes().keySet());
        List<String> changed = new ArrayList<>();
        for (String name : names) {
            if (!Objects.equals(change.previousAttributes().get(name), change.currentAttributes().get(name))) {
                changed.add(name);
            }
        }
        return changed;
    }

    private boolean canSafelyPropagateAttribute(TggRuleInfo rule,
                                                String attributeName,
                                                ImpactAnalysisResult impactAnalysis,
                                                WorkspaceSide changedSide) {
        if (impactAnalysis != null) {
            if (changedSide == WorkspaceSide.SOURCE
                    && impactAnalysis.affectedSourceAttributesByRule().getOrDefault(rule.getName(), Set.of()).contains(attributeName)) {
                return !impactAnalysis.affectedTargetAttributesByRule().getOrDefault(rule.getName(), Set.of()).isEmpty();
            }
            if (changedSide == WorkspaceSide.TARGET
                    && impactAnalysis.affectedTargetAttributesByRule().getOrDefault(rule.getName(), Set.of()).contains(attributeName)) {
                return !impactAnalysis.affectedSourceAttributesByRule().getOrDefault(rule.getName(), Set.of()).isEmpty();
            }
        }
        String token = "." + attributeName;
        return rule.getCorrInvariants().stream().anyMatch(invariant -> invariant.expression().contains(token))
                || rule.getSideConstraints(WorkspaceSide.SOURCE).contains(attributeName)
                || rule.getSideConstraints(WorkspaceSide.TARGET).contains(attributeName);
    }

    private Set<String> targetAttributesForRule(TggRuleInfo rule, ImpactAnalysisResult impactAnalysis) {
        if (impactAnalysis != null) {
            Set<String> attrs = impactAnalysis.affectedTargetAttributesByRule().getOrDefault(rule.getName(), Set.of());
            if (!attrs.isEmpty()) {
                return attrs;
            }
        }
        return Set.of();
    }

    private Set<String> sourceAttributesForRule(TggRuleInfo rule, ImpactAnalysisResult impactAnalysis) {
        if (impactAnalysis != null) {
            Set<String> attrs = impactAnalysis.affectedSourceAttributesByRule().getOrDefault(rule.getName(), Set.of());
            if (!attrs.isEmpty()) {
                return attrs;
            }
        }
        return Set.of();
    }

    private Set<String> sourceAttributesForTargetAttribute(TggRuleInfo rule, String targetAttribute, ImpactAnalysisResult impactAnalysis) {
        if (impactAnalysis != null && impactAnalysis.dependencyGraph() != null) {
            Set<String> attrs = impactAnalysis.dependencyGraph().sourceAttributesForTargetAttribute(rule.getName(), targetAttribute);
            if (!attrs.isEmpty()) {
                return attrs;
            }
        }
        return sourceAttributesForRule(rule, impactAnalysis);
    }

    private Map<String, String> deriveTargetAttributesForChangedSourceAttribute(TggRuleInfo rule,
                                                                                Map<String, String> sourceBinding,
                                                                                SnapshotIndex sourceIndex,
                                                                                String changedSourceAttribute) {
        Map<String, String> attributes = new LinkedHashMap<>();
        for (TggRuleInfo.AttributeMapping mapping : rule.getAttributeMappings()) {
            if (mapping.targetSide() != WorkspaceSide.TARGET) {
                continue;
            }
            if (!mapping.sourceExpression().contains("." + changedSourceAttribute)) {
                continue;
            }
            String value = evaluateExpression(mapping.sourceExpression(), sourceBinding, sourceIndex);
            if (value != null) {
                attributes.put(mapping.targetAttributeName(), quoteValue(value));
            }
        }
        return attributes;
    }

    private Map<String, String> bindSingleObject(TggRuleInfo rule, WorkspaceSide side, String objectId) {
        Map<String, String> binding = new LinkedHashMap<>();
        for (String variableName : rule.getTypedVariables(side).keySet()) {
            binding.put(variableName, objectId);
            break;
        }
        return binding;
    }

    private Map<String, String> bindObjectToMatchingVariable(TggRuleInfo rule,
                                                             WorkspaceSide side,
                                                             String objectId,
                                                             String className) {
        Map<String, String> binding = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : rule.getTypedVariables(side).entrySet()) {
            if (Objects.equals(entry.getValue(), className)) {
                binding.put(entry.getKey(), objectId);
                return binding;
            }
        }
        return bindSingleObject(rule, side, objectId);
    }

    private Map<String, String> completeBindingFromAssociations(TggRuleInfo rule,
                                                                WorkspaceSide side,
                                                                Map<String, String> seedBinding,
                                                                SnapshotIndex index) {
        Map<String, String> binding = new LinkedHashMap<>(seedBinding);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (TggRuleInfo.AssociationPattern pattern : rule.getAssociationPatterns(side)) {
                String leftId = binding.get(pattern.leftVarName());
                String rightId = binding.get(pattern.rightVarName());
                if (leftId != null && rightId != null) {
                    continue;
                }
                if (leftId != null) {
                    String resolved = resolveNeighbor(pattern.associationName(), leftId,
                            rule.getTypedVariables(side).get(pattern.rightVarName()), index);
                    if (resolved != null) {
                        binding.put(pattern.rightVarName(), resolved);
                        changed = true;
                    }
                } else if (rightId != null) {
                    String resolved = resolveNeighbor(pattern.associationName(), rightId,
                            rule.getTypedVariables(side).get(pattern.leftVarName()), index);
                    if (resolved != null) {
                        binding.put(pattern.leftVarName(), resolved);
                        changed = true;
                    }
                }
            }
        }
        return binding;
    }

    private String resolveNeighbor(String associationName,
                                   String objectId,
                                   String expectedClassName,
                                   SnapshotIndex index) {
        for (LinkState link : index.findLinksFrom(objectId)) {
            if (!Objects.equals(link.assocName, associationName) || link.participants.size() < 2) {
                continue;
            }
            for (String participant : link.participants) {
                if (Objects.equals(participant, objectId)) {
                    continue;
                }
                ObjectState neighbor = index.findObject(participant);
                if (neighbor != null && (expectedClassName == null || Objects.equals(neighbor.className, expectedClassName))) {
                    return neighbor.name;
                }
            }
        }
        return null;
    }

    private void addAssociationLinks(List<TggRuleInfo.AssociationPattern> patterns,
                                     Map<String, String> binding,
                                     WorkspaceMutationBatch batch) {
        for (TggRuleInfo.AssociationPattern pattern : patterns) {
            String leftId = binding.get(pattern.leftVarName());
            String rightId = binding.get(pattern.rightVarName());
            if (leftId != null && rightId != null) {
                batch.addUpsertLink(new ImportLinkSpec(pattern.associationName(), List.of(leftId, rightId)));
            }
        }
    }

    private Map<String, String> deriveTargetAttributes(TggRuleInfo rule,
                                                       Map<String, String> sourceBinding,
                                                       SnapshotIndex sourceIndex) {
        Map<String, String> attributes = new LinkedHashMap<>();
        for (TggRuleInfo.AttributeMapping mapping : rule.getAttributeMappings()) {
            if (mapping.targetSide() != WorkspaceSide.TARGET) {
                continue;
            }
            String value = evaluateExpression(mapping.sourceExpression(), sourceBinding, sourceIndex);
            if (value != null) {
                attributes.put(mapping.targetAttributeName(), quoteValue(value));
            }
        }
        return attributes;
    }

    private Map<String, String> deriveSourceAttributes(TggRuleInfo rule,
                                                       Map<String, String> targetBinding,
                                                       SnapshotIndex targetIndex) {
        Map<String, String> attributes = new LinkedHashMap<>();
        for (TggRuleInfo.CorrInvariant invariant : rule.getCorrInvariants()) {
            String expr = invariant.expression();
            if (!expr.contains("split") || !expr.contains("at(")) {
                continue;
            }
            for (String targetObjId : targetBinding.values()) {
                ObjectState targetObj = targetIndex.findObject(targetObjId);
                if (targetObj == null) {
                    continue;
                }
                Object nameValue = targetObj.primitiveValues.get("name");
                if (nameValue == null) {
                    continue;
                }
                String[] parts = String.valueOf(nameValue).split(",\\s*");
                if (parts.length >= 2) {
                    attributes.put("name", quoteValue(parts[1].trim()));
                } else if (parts.length == 1) {
                    attributes.put("name", quoteValue(parts[0].trim()));
                }
            }
        }
        return attributes;
    }

    private String evaluateExpression(String expression,
                                      Map<String, String> binding,
                                      SnapshotIndex index) {
        String trimmed = expression.trim();
        if (trimmed.contains(" + ")) {
            StringBuilder sb = new StringBuilder();
            for (String part : trimmed.split("\\s*\\+\\s*")) {
                String value = evaluateSimpleExpression(part.trim(), binding, index);
                if (value == null) {
                    return null;
                }
                sb.append(value);
            }
            return sb.toString();
        }
        return evaluateSimpleExpression(trimmed, binding, index);
    }

    private String evaluateSimpleExpression(String expression,
                                            Map<String, String> binding,
                                            SnapshotIndex index) {
        if ((expression.startsWith("'") && expression.endsWith("'"))
                || (expression.startsWith("\"") && expression.endsWith("\""))) {
            return expression.substring(1, expression.length() - 1);
        }
        String normalized = expression.startsWith("self.") ? expression.substring(5) : expression;
        String[] path = normalized.split("\\.");
        if (path.length == 0) {
            return null;
        }
        String objectId = binding.get(path[0].trim());
        if (objectId == null) {
            return null;
        }
        Object current = index.findObject(objectId);
        for (int i = 1; i < path.length && current instanceof ObjectState objectState; i++) {
            String segment = path[i].trim();
            if (objectState.primitiveValues.containsKey(segment)) {
                current = objectState.primitiveValues.get(segment);
            } else if (objectState.objectReferences != null && objectState.objectReferences.containsKey(segment)
                    && !objectState.objectReferences.get(segment).isEmpty()) {
                current = index.findObject(String.valueOf(objectState.objectReferences.get(segment).get(0)));
            } else {
                ObjectState navigated = resolveNavigation(objectState, segment, index, binding);
                if (navigated != null) {
                    current = navigated;
                } else {
                    return null;
                }
            }
        }
        return current != null ? String.valueOf(current) : null;
    }

    private ObjectState resolveNavigation(ObjectState objectState,
                                          String navigationName,
                                          SnapshotIndex index,
                                          Map<String, String> binding) {
        if (objectState.objectReferences != null && objectState.objectReferences.containsKey(navigationName)
                && !objectState.objectReferences.get(navigationName).isEmpty()) {
            return index.findObject(String.valueOf(objectState.objectReferences.get(navigationName).get(0)));
        }
        for (LinkState link : index.findLinksFrom(objectState.name)) {
            if (!link.assocName.equalsIgnoreCase(navigationName) || link.participants.size() < 2) {
                continue;
            }
            for (String participant : link.participants) {
                if (!participant.equals(objectState.name)) {
                    ObjectState neighbor = index.findObject(participant);
                    if (neighbor != null) {
                        return neighbor;
                    }
                }
            }
        }
        for (LinkState link : index.findLinksFrom(objectState.name)) {
            if (link.participants.size() < 2) {
                continue;
            }
            for (String participant : link.participants) {
                if (participant.equals(objectState.name)) {
                    continue;
                }
                if (binding.containsValue(participant)) {
                    ObjectState neighbor = index.findObject(participant);
                    if (neighbor != null) {
                        return neighbor;
                    }
                }
            }
        }
        return null;
    }

    private String extractLastPathSegment(String path) {
        if (path == null) {
            return null;
        }
        int lastDot = path.lastIndexOf('.');
        return lastDot >= 0 ? path.substring(lastDot + 1).trim() : path.trim();
    }

    private List<CorrLink> findCorrLinksForSource(String sourceObjectId,
                                                  FullObjectSnapshot corrSnapshot,
                                                  TggWorkspaceDefinition definition) {
        return findCorrLinks(sourceObjectId, corrSnapshot, definition, true);
    }

    private List<CorrLink> findCorrLinksForTarget(String targetObjectId,
                                                  FullObjectSnapshot corrSnapshot,
                                                  TggWorkspaceDefinition definition) {
        return findCorrLinks(targetObjectId, corrSnapshot, definition, false);
    }

    private List<CorrLink> findCorrLinks(String objectId,
                                         FullObjectSnapshot corrSnapshot,
                                         TggWorkspaceDefinition definition,
                                         boolean sourceLookup) {
        List<CorrLink> links = new ArrayList<>();
        for (ObjectState corrObj : corrSnapshot.objects.values()) {
            CorrRuntimeTraceHelper.ResolvedCorrBinding resolved = CorrRuntimeTraceHelper.resolveCorrBinding(corrObj, corrSnapshot, definition);
            if (resolved == null) {
                continue;
            }
            Set<String> relatedObjects = new LinkedHashSet<>();
            if (resolved.sourceObjectId() != null) {
                relatedObjects.add(resolved.sourceObjectId());
            }
            if (resolved.targetObjectId() != null) {
                relatedObjects.add(resolved.targetObjectId());
            }
            relatedObjects.addAll(resolved.sourceBindings().values());
            relatedObjects.addAll(resolved.targetBindings().values());
            if (!relatedObjects.contains(objectId)
                    || resolved.sourceObjectId() == null
                    || resolved.targetObjectId() == null) {
                continue;
            }
            boolean sideMatch = sourceLookup
                    ? resolved.sourceBindings().containsValue(objectId) || Objects.equals(resolved.sourceObjectId(), objectId)
                    : resolved.targetBindings().containsValue(objectId) || Objects.equals(resolved.targetObjectId(), objectId);
            if (!sideMatch) {
                continue;
            }
            links.add(new CorrLink(corrObj.name, corrObj.className,
                    resolved.sourceObjectId(), resolved.targetObjectId(),
                    resolved.ruleName().isBlank() ? resolveRuleName(definition, corrObj.className) : resolved.ruleName()));
        }
        return links;
    }

    private Map<String, String> resolveBinding(String corrObjectId,
                                               FullObjectSnapshot corrSnapshot,
                                               TggRuleInfo rule,
                                               WorkspaceSide side,
                                               SnapshotIndex index,
                                               String changedObjectId,
                                               String changedClassName) {
        Map<String, String> binding = CorrRuntimeTraceHelper.extractBindingForSide(corrObjectId, corrSnapshot, side);
        if (binding.isEmpty()) {
            binding = bindObjectToMatchingVariable(rule, side, changedObjectId, changedClassName);
        } else if (!binding.containsValue(changedObjectId)) {
            binding.putAll(bindObjectToMatchingVariable(rule, side, changedObjectId, changedClassName));
        }
        return completeBindingFromAssociations(rule, side, binding, index);
    }

    private String resolveRuleName(TggWorkspaceDefinition definition, String corrClassName) {
        if (definition == null) {
            return "";
        }
        for (TggRuleInfo rule : definition.getRules()) {
            if (rule.getOutputCorrPatterns().stream().anyMatch(pattern -> pattern.corrClassName().equals(corrClassName))) {
                return rule.getName();
            }
        }
        return "";
    }

    private List<TggRuleInfo> findRulesForClass(String className, WorkspaceSide side, TggWorkspaceDefinition definition) {
        List<TggRuleInfo> matching = new ArrayList<>();
        for (TggRuleInfo rule : definition.getRules()) {
            if (rule.getTypedVariables(side).containsValue(className)) {
                matching.add(rule);
            }
        }
        return matching;
    }

    private String deriveObjectId(String className, Iterable<String> seedIds) {
        StringBuilder builder = new StringBuilder(className.toLowerCase());
        for (String seedId : seedIds) {
            builder.append("_").append(sanitize(seedId));
        }
        return builder.toString();
    }

    private String sanitize(String value) {
        return Objects.requireNonNullElse(value, "x").replaceAll("[^A-Za-z0-9_]", "_");
    }

    private String quoteValue(Object value) {
        return "'" + String.valueOf(value).replace("'", "") + "'";
    }

    private void markManual(String warning) {
        requiresManualTransform = true;
        warnings.add(warning);
        report.addWarning(warning);
    }

    private record CorrLink(String corrObjectId, String corrClassName,
                            String sourceObjectId, String targetObjectId,
                            String ruleName) {
    }
}
