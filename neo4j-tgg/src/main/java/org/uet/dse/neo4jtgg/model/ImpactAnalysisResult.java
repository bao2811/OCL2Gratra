package org.uet.dse.neo4jtgg.model;

import org.uet.dse.neo4jtgg.engine.TransformationDirection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record ImpactAnalysisResult(
        TransformationDirection direction,
        WorkspaceSide drivingSide,
        Set<String> affectedSourceObjectIds,
        Set<String> affectedTargetObjectIds,
        Set<String> affectedCorrObjectIds,
        Set<String> affectedRuleNames,
        Set<String> affectedLinkIdentities,
        Map<String, Set<String>> affectedSourceAttributesByRule,
        Map<String, Set<String>> affectedTargetAttributesByRule,
        RuleDependencyGraph dependencyGraph,
        List<String> warnings,
        boolean blocked,
        boolean requiresManualTransform) {

    public ImpactAnalysisResult {
        affectedSourceObjectIds = Set.copyOf(new LinkedHashSet<>(affectedSourceObjectIds != null ? affectedSourceObjectIds : Set.of()));
        affectedTargetObjectIds = Set.copyOf(new LinkedHashSet<>(affectedTargetObjectIds != null ? affectedTargetObjectIds : Set.of()));
        affectedCorrObjectIds = Set.copyOf(new LinkedHashSet<>(affectedCorrObjectIds != null ? affectedCorrObjectIds : Set.of()));
        affectedRuleNames = Set.copyOf(new LinkedHashSet<>(affectedRuleNames != null ? affectedRuleNames : Set.of()));
        affectedLinkIdentities = Set.copyOf(new LinkedHashSet<>(affectedLinkIdentities != null ? affectedLinkIdentities : Set.of()));
        affectedSourceAttributesByRule = immutableNestedSetMap(affectedSourceAttributesByRule);
        affectedTargetAttributesByRule = immutableNestedSetMap(affectedTargetAttributesByRule);
        warnings = List.copyOf(new ArrayList<>(warnings != null ? warnings : List.of()));
    }

    public String toDisplayText() {
        StringBuilder sb = new StringBuilder();
        sb.append("Direction: ").append(direction).append("\n");
        sb.append("Driving side: ").append(drivingSide.getDisplayName()).append("\n");
        sb.append("Affected rules: ").append(affectedRuleNames).append("\n");
        sb.append("Affected source objects: ").append(affectedSourceObjectIds).append("\n");
        sb.append("Affected target objects: ").append(affectedTargetObjectIds).append("\n");
        sb.append("Affected corr objects: ").append(affectedCorrObjectIds).append("\n");
        sb.append("Affected links: ").append(affectedLinkIdentities).append("\n");
        sb.append("Affected source attrs by rule: ").append(affectedSourceAttributesByRule).append("\n");
        sb.append("Affected target attrs by rule: ").append(affectedTargetAttributesByRule).append("\n");
        sb.append("Blocked: ").append(blocked).append("\n");
        sb.append("Manual required: ").append(requiresManualTransform);
        if (dependencyGraph != null) {
            sb.append("\nDependency graph:\n").append(dependencyGraph.toDisplayText());
        }
        if (!warnings.isEmpty()) {
            sb.append("\nWarnings:");
            for (String warning : warnings) {
                sb.append("\n- ").append(warning);
            }
        }
        return sb.toString();
    }

    private static Map<String, Set<String>> immutableNestedSetMap(Map<String, Set<String>> input) {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        if (input != null) {
            for (Map.Entry<String, Set<String>> entry : input.entrySet()) {
                result.put(entry.getKey(), Set.copyOf(new LinkedHashSet<>(entry.getValue() != null ? entry.getValue() : Set.of())));
            }
        }
        return Map.copyOf(result);
    }
}
