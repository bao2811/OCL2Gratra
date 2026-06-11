package org.uet.dse.neo4jtgg.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record RuleDependencyGraph(
        Map<String, Set<String>> sourceAttributesByRule,
        Map<String, Set<String>> targetAttributesByRule,
        Map<String, Set<String>> sourceAssociationsByRule,
        Map<String, Set<String>> targetAssociationsByRule,
        Map<String, Map<String, Set<String>>> sourceToTargetAttributesByRule,
        Map<String, Map<String, Set<String>>> targetToSourceAttributesByRule) {

    public RuleDependencyGraph {
        sourceAttributesByRule = immutableNestedSetMap(sourceAttributesByRule);
        targetAttributesByRule = immutableNestedSetMap(targetAttributesByRule);
        sourceAssociationsByRule = immutableNestedSetMap(sourceAssociationsByRule);
        targetAssociationsByRule = immutableNestedSetMap(targetAssociationsByRule);
        sourceToTargetAttributesByRule = immutableTripleMap(sourceToTargetAttributesByRule);
        targetToSourceAttributesByRule = immutableTripleMap(targetToSourceAttributesByRule);
    }

    public Set<String> sourceAttributes(String ruleName) {
        return sourceAttributesByRule.getOrDefault(ruleName, Set.of());
    }

    public Set<String> targetAttributes(String ruleName) {
        return targetAttributesByRule.getOrDefault(ruleName, Set.of());
    }

    public Set<String> targetAttributesForSourceAttribute(String ruleName, String sourceAttribute) {
        return sourceToTargetAttributesByRule.getOrDefault(ruleName, Map.of()).getOrDefault(sourceAttribute, Set.of());
    }

    public Set<String> sourceAttributesForTargetAttribute(String ruleName, String targetAttribute) {
        return targetToSourceAttributesByRule.getOrDefault(ruleName, Map.of()).getOrDefault(targetAttribute, Set.of());
    }

    public String toDisplayText() {
        StringBuilder sb = new StringBuilder();
        for (String ruleName : sourceAttributesByRule.keySet()) {
            sb.append(ruleName)
                    .append(": sourceAttrs=").append(sourceAttributes(ruleName))
                    .append(", targetAttrs=").append(targetAttributes(ruleName))
                    .append(", sourceToTarget=").append(sourceToTargetAttributesByRule.getOrDefault(ruleName, Map.of()))
                    .append(", targetToSource=").append(targetToSourceAttributesByRule.getOrDefault(ruleName, Map.of()))
                    .append(", sourceAssocs=").append(sourceAssociationsByRule.getOrDefault(ruleName, Set.of()))
                    .append(", targetAssocs=").append(targetAssociationsByRule.getOrDefault(ruleName, Set.of()))
                    .append('\n');
        }
        return sb.toString().trim();
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

    private static Map<String, Map<String, Set<String>>> immutableTripleMap(Map<String, Map<String, Set<String>>> input) {
        Map<String, Map<String, Set<String>>> result = new LinkedHashMap<>();
        if (input != null) {
            for (Map.Entry<String, Map<String, Set<String>>> outer : input.entrySet()) {
                result.put(outer.getKey(), immutableNestedSetMap(outer.getValue()));
            }
        }
        return Map.copyOf(result);
    }
}
