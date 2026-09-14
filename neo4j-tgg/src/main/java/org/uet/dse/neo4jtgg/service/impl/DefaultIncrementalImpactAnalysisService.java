package org.uet.dse.neo4jtgg.service.impl;

import org.uet.dse.neo4j.model.FullObjectSnapshot;
import org.uet.dse.neo4j.model.ObjectState;
import org.uet.dse.neo4jtgg.engine.CorrRuntimeTraceHelper;
import org.uet.dse.neo4jtgg.engine.TransformationDirection;
import org.uet.dse.neo4jtgg.model.CdcChangeEvent;
import org.uet.dse.neo4jtgg.model.ImpactAnalysisResult;
import org.uet.dse.neo4jtgg.model.NormalizedChangeSet;
import org.uet.dse.neo4jtgg.model.RuleDependencyGraph;
import org.uet.dse.neo4jtgg.model.TggRuleInfo;
import org.uet.dse.neo4jtgg.model.TggWorkspaceContext;
import org.uet.dse.neo4jtgg.model.WorkspaceSide;
import org.uet.dse.neo4jtgg.service.IncrementalImpactAnalysisService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DefaultIncrementalImpactAnalysisService implements IncrementalImpactAnalysisService {
    private final RuleDependencyGraphBuilder dependencyGraphBuilder = new RuleDependencyGraphBuilder();

    @Override
    public ImpactAnalysisResult analyze(TggWorkspaceContext context,
                                        NormalizedChangeSet changeSet,
                                        FullObjectSnapshot sourceSnapshot,
                                        FullObjectSnapshot targetSnapshot,
                                        FullObjectSnapshot corrSnapshot) {
        Set<String> sourceIds = new LinkedHashSet<>();
        Set<String> targetIds = new LinkedHashSet<>();
        Set<String> corrIds = new LinkedHashSet<>();
        Set<String> ruleNames = new LinkedHashSet<>();
        Set<String> linkIds = new LinkedHashSet<>();
        Map<String, Set<String>> affectedSourceAttrsByRule = new LinkedHashMap<>();
        Map<String, Set<String>> affectedTargetAttrsByRule = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();
        boolean requiresManualTransform = false;
        RuleDependencyGraph dependencyGraph = dependencyGraphBuilder.build(context.getWorkspaceDefinition());

        TransformationDirection direction = changeSet.side() == WorkspaceSide.SOURCE
                ? TransformationDirection.FORWARD
                : TransformationDirection.BACKWARD;

        for (CdcChangeEvent event : changeSet.events()) {
            if (event.entityKind() == CdcChangeEvent.EntityKind.LINK) {
                linkIds.add(event.entityId());
            }
            if (changeSet.side() == WorkspaceSide.SOURCE) {
                sourceIds.add(baseObjectId(event.entityId()));
            } else {
                targetIds.add(baseObjectId(event.entityId()));
            }
            resolveCorrAndRules(context, changeSet.side(), baseObjectId(event.entityId()), corrSnapshot, sourceIds, targetIds, corrIds, ruleNames);
            if (event.entityKind() == CdcChangeEvent.EntityKind.ATTRIBUTE
                    && !hasExplicitAttributeDependency(context, event.entityType(), attributeName(event.entityId()), changeSet.side(), ruleNames,
                    dependencyGraph, affectedSourceAttrsByRule, affectedTargetAttrsByRule)) {
                requiresManualTransform = true;
                warnings.add("No explicit attribute dependency found for `" + event.entityId() + "`.");
            }
        }

        if (ruleNames.isEmpty() && !changeSet.events().isEmpty()) {
            requiresManualTransform = true;
            warnings.add("No affected rule could be resolved from the current correspondence/runtime metadata.");
        }

        return new ImpactAnalysisResult(direction, changeSet.side(), sourceIds, targetIds, corrIds, ruleNames, linkIds,
                affectedSourceAttrsByRule, affectedTargetAttrsByRule, dependencyGraph, warnings, false, requiresManualTransform);
    }

    private void resolveCorrAndRules(TggWorkspaceContext context,
                                     WorkspaceSide side,
                                     String objectId,
                                     FullObjectSnapshot corrSnapshot,
                                     Set<String> sourceIds,
                                     Set<String> targetIds,
                                     Set<String> corrIds,
                                     Set<String> ruleNames) {
        for (ObjectState corrObj : corrSnapshot.objects.values()) {
            CorrRuntimeTraceHelper.ResolvedCorrBinding resolved = CorrRuntimeTraceHelper.resolveCorrBinding(
                    corrObj, corrSnapshot, context.getWorkspaceDefinition());
            if (resolved == null) {
                continue;
            }
            Set<String> refs = new LinkedHashSet<>();
            refs.addAll(resolved.sourceBindings().values());
            refs.addAll(resolved.targetBindings().values());
            if (resolved.sourceObjectId() != null) {
                refs.add(resolved.sourceObjectId());
            }
            if (resolved.targetObjectId() != null) {
                refs.add(resolved.targetObjectId());
            }
            if (!refs.contains(objectId)) {
                continue;
            }
            corrIds.add(corrObj.name);
            if (resolved.sourceObjectId() != null) {
                sourceIds.add(resolved.sourceObjectId());
            }
            if (resolved.targetObjectId() != null) {
                targetIds.add(resolved.targetObjectId());
            }
            sourceIds.addAll(resolved.sourceBindings().values());
            targetIds.addAll(resolved.targetBindings().values());
            if (!resolved.ruleName().isBlank()) {
                ruleNames.add(resolved.ruleName());
            }
            for (TggRuleInfo rule : context.getWorkspaceDefinition().getRules()) {
                boolean corrMatch = rule.getOutputCorrPatterns().stream()
                        .anyMatch(pattern -> pattern.corrClassName().equals(corrObj.className));
                boolean typeMatch = rule.getTypedVariables(side).containsValue(inferClassName(context, side, objectId));
                if (corrMatch || typeMatch) {
                    ruleNames.add(rule.getName());
                }
            }
        }
    }

    private boolean hasExplicitAttributeDependency(TggWorkspaceContext context,
                                                   String className,
                                                   String attributeName,
                                                   WorkspaceSide side,
                                                   Set<String> ruleNames,
                                                   RuleDependencyGraph dependencyGraph,
                                                   Map<String, Set<String>> affectedSourceAttrsByRule,
                                                   Map<String, Set<String>> affectedTargetAttrsByRule) {
        if (attributeName == null || attributeName.isBlank()) {
            return true;
        }
        boolean matched = false;
        for (TggRuleInfo rule : context.getWorkspaceDefinition().getRules()) {
            if (!ruleNames.isEmpty() && !ruleNames.contains(rule.getName())) {
                continue;
            }
            if (!rule.getTypedVariables(side).containsValue(className)) {
                continue;
            }
            if (side == WorkspaceSide.SOURCE && dependencyGraph.sourceAttributes(rule.getName()).contains(attributeName)) {
                affectedSourceAttrsByRule.computeIfAbsent(rule.getName(), ignored -> new LinkedHashSet<>()).add(attributeName);
                affectedTargetAttrsByRule.computeIfAbsent(rule.getName(), ignored -> new LinkedHashSet<>())
                        .addAll(dependencyGraph.targetAttributes(rule.getName()));
                matched = true;
            } else if (side == WorkspaceSide.TARGET && dependencyGraph.targetAttributes(rule.getName()).contains(attributeName)) {
                affectedTargetAttrsByRule.computeIfAbsent(rule.getName(), ignored -> new LinkedHashSet<>()).add(attributeName);
                affectedSourceAttrsByRule.computeIfAbsent(rule.getName(), ignored -> new LinkedHashSet<>())
                        .addAll(dependencyGraph.sourceAttributes(rule.getName()));
                matched = true;
            }
        }
        return matched;
    }

    private String inferClassName(TggWorkspaceContext context, WorkspaceSide side, String objectId) {
        FullObjectSnapshot snapshot = context.getLastSnapshot(side);
        if (snapshot == null) {
            return "";
        }
        ObjectState state = snapshot.objects.get(objectId);
        return state != null ? state.className : "";
    }

    private String baseObjectId(String entityId) {
        int dot = entityId.indexOf('.');
        return dot >= 0 ? entityId.substring(0, dot) : entityId;
    }

    private String attributeName(String entityId) {
        int dot = entityId.indexOf('.');
        return dot >= 0 ? entityId.substring(dot + 1) : "";
    }
}
