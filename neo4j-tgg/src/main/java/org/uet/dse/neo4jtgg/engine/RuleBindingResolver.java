package org.uet.dse.neo4jtgg.engine;

import org.uet.dse.neo4j.model.ObjectState;
import org.uet.dse.neo4jtgg.model.TggRuleInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Shared binding resolution logic for TGG rule engines.
 * Uses backtracking to find all valid variable-to-object bindings
 * that satisfy typed variables, association patterns, and predicates.
 * <p>
 * This eliminates the code duplication that existed between
 * ForwardRuleApplicationEngine and BackwardRuleApplicationEngine.
 */
public final class RuleBindingResolver {

    private RuleBindingResolver() {
    }

    /**
     * Resolve all valid bindings for the given typed variables against a snapshot.
     *
     * @param typedVariables variable name → class name
     * @param associations   association patterns to satisfy
     * @param predicates     OCL-like predicates to evaluate
     * @param index          the snapshot to match against
     * @return list of valid bindings (each is varName → objectId)
     */
    public static List<Map<String, String>> resolveBindings(
            Map<String, String> typedVariables,
            List<TggRuleInfo.AssociationPattern> associations,
            List<String> predicates,
            SnapshotIndex index) {
        if (typedVariables.isEmpty()) {
            return List.of(Map.of());
        }

        // Order variables by number of association references (most constrained first)
        List<String> orderedVariables = new ArrayList<>(typedVariables.keySet());
        orderedVariables.sort(Comparator.comparingInt(var ->
                -countAssociationReferences(var, associations)));

        List<Map<String, String>> results = new ArrayList<>();
        backtrack(orderedVariables, 0, typedVariables, associations, predicates,
                index, new LinkedHashMap<>(), results);
        return results;
    }

    private static int countAssociationReferences(String variableName,
                                                   List<TggRuleInfo.AssociationPattern> patterns) {
        int count = 0;
        for (TggRuleInfo.AssociationPattern p : patterns) {
            if (p.leftVarName().equals(variableName) || p.rightVarName().equals(variableName)) {
                count++;
            }
        }
        return count;
    }

    private static void backtrack(List<String> orderedVars, int position,
                                   Map<String, String> typedVariables,
                                   List<TggRuleInfo.AssociationPattern> associations,
                                   List<String> predicates,
                                   SnapshotIndex index,
                                   Map<String, String> currentBinding,
                                   List<Map<String, String>> results) {
        if (position >= orderedVars.size()) {
            if (predicates.stream().allMatch(p -> evaluatePredicate(p, currentBinding, index))) {
                results.add(new LinkedHashMap<>(currentBinding));
            }
            return;
        }

        String variableName = orderedVars.get(position);
        String className = typedVariables.get(variableName);

        for (ObjectState candidate : index.findByClass(className)) {
            currentBinding.put(variableName, candidate.name);
            if (associationsSatisfied(associations, currentBinding, index)) {
                backtrack(orderedVars, position + 1, typedVariables, associations,
                        predicates, index, currentBinding, results);
            }
            currentBinding.remove(variableName);
        }
    }

    private static boolean associationsSatisfied(List<TggRuleInfo.AssociationPattern> associations,
                                                  Map<String, String> currentBinding,
                                                  SnapshotIndex index) {
        for (TggRuleInfo.AssociationPattern pattern : associations) {
            String left = currentBinding.get(pattern.leftVarName());
            String right = currentBinding.get(pattern.rightVarName());
            if (left != null && right != null
                    && !index.hasAssociation(pattern.associationName(), left, right)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Evaluate a predicate expression against a binding.
     * Supports: {@code varName.attr <> Undefined} and {@code varName.attr = value}
     */
    static boolean evaluatePredicate(String predicate, Map<String, String> binding,
                                      SnapshotIndex index) {
        String trimmed = predicate.trim();
        if (trimmed.isEmpty()) return true;

        // Handle: expr <> expr
        int notEqualsIndex = trimmed.indexOf("<>");
        if (notEqualsIndex >= 0) {
            String left = trimmed.substring(0, notEqualsIndex).trim();
            String right = trimmed.substring(notEqualsIndex + 2).trim();
            Object leftValue = resolveValue(left, binding, index);
            if ("Undefined".equalsIgnoreCase(right)) {
                return leftValue != null && !"Undefined".equals(String.valueOf(leftValue));
            }
            Object rightValue = resolveValue(right, binding, index);
            return !Objects.equals(leftValue, rightValue);
        }

        // Handle: expr = expr
        int equalsIndex = trimmed.indexOf('=');
        if (equalsIndex >= 0) {
            String left = trimmed.substring(0, equalsIndex).trim();
            String right = trimmed.substring(equalsIndex + 1).trim();
            return Objects.equals(
                    String.valueOf(resolveValue(left, binding, index)),
                    String.valueOf(resolveValue(right, binding, index)));
        }

        return true;
    }

    private static Object resolveValue(String expression, Map<String, String> binding,
                                        SnapshotIndex index) {
        String trimmed = expression.trim();
        if ("Undefined".equalsIgnoreCase(trimmed)) return null;

        // String literal
        if ((trimmed.startsWith("'") && trimmed.endsWith("'"))
                || (trimmed.startsWith("\"") && trimmed.endsWith("\""))) {
            return trimmed.substring(1, trimmed.length() - 1);
        }

        // Navigation path: varName.attrName
        String[] path = trimmed.split("\\.");
        String objectId = binding.get(path[0].trim());
        if (objectId == null) return null;

        ObjectState obj = index.findObject(objectId);
        if (obj == null || path.length < 2) return objectId;

        return obj.primitiveValues != null ? obj.primitiveValues.get(path[1].trim()) : null;
    }

    /**
     * Find the first existing singleton object of a given class (e.g. FamilyRegister, PersonRegister).
     */
    public static String findExistingSingleton(String className, SnapshotIndex index) {
        if (isSingletonClass(className)) {
            List<ObjectState> existing = index.findByClass(className);
            if (!existing.isEmpty()) {
                return existing.get(0).name;
            }
        }
        return null;
    }

    /**
     * Build a deduplication key from rule name + binding values.
     */
    public static String buildCorrKey(String ruleName, Map<String, String> binding) {
        StringBuilder key = new StringBuilder(ruleName);
        binding.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> key.append("_")
                        .append(entry.getKey())
                        .append("=")
                        .append(entry.getValue()));
        return key.toString();
    }

    private static boolean isSingletonClass(String className) {
        return "PersonRegister".equals(className)
                || "FamilyRegister".equals(className);
    }
}
