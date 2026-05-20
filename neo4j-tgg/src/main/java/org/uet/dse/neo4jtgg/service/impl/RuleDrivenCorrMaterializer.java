package org.uet.dse.neo4jtgg.service.impl;

import org.neo4j.driver.Session;
import org.uet.dse.neo4j.model.FullObjectSnapshot;
import org.uet.dse.neo4j.model.LinkState;
import org.uet.dse.neo4j.model.ObjectState;
import org.uet.dse.neo4j.repo.Neo4jObjectRepository;
import org.uet.dse.neo4j.sync.object.ObjectPushService;
import org.uet.dse.neo4jtgg.model.TggRuleInfo;
import org.uet.dse.neo4jtgg.model.TggWorkspaceContext;
import org.uet.dse.neo4jtgg.model.TggWorkspaceDefinition;
import org.uet.dse.neo4jtgg.model.WorkspaceSide;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class RuleDrivenCorrMaterializer {
    private final Neo4jWorkspaceRuntimeService runtimeService = Neo4jWorkspaceRuntimeService.getInstance();
    private final Neo4jObjectRepository objectRepository = new Neo4jObjectRepository();

    public int materialize(TggWorkspaceContext context) {
        TggWorkspaceDefinition definition = context.getWorkspaceDefinition();
        if (definition == null || definition.getCorrespondenceModel() == null) {
            return 0;
        }

        FullObjectSnapshot source = runtimeService.loadSnapshot(context, WorkspaceSide.SOURCE);
        FullObjectSnapshot target = runtimeService.loadSnapshot(context, WorkspaceSide.TARGET);

        Map<String, CorrRecord> records = new LinkedHashMap<>();
        SnapshotIndex sourceIndex = new SnapshotIndex(source);
        SnapshotIndex targetIndex = new SnapshotIndex(target);
        RuntimeLookup runtimeLookup = new RuntimeLookup(sourceIndex, targetIndex);

        for (TggRuleInfo rule : definition.getRules()) {
            List<Map<String, String>> sourceBindings = resolveBindings(rule, WorkspaceSide.SOURCE, sourceIndex, runtimeLookup);
            List<Map<String, String>> targetBindings = resolveBindings(rule, WorkspaceSide.TARGET, targetIndex, runtimeLookup);
            for (Map<String, String> sourceBinding : sourceBindings) {
                for (Map<String, String> targetBinding : targetBindings) {
                    if (!matchesRequiredCorr(rule, sourceBinding, targetBinding, records.values())) {
                        continue;
                    }
                    if (!matchesCorrInvariants(rule, sourceBinding, targetBinding, runtimeLookup)) {
                        continue;
                    }
                    appendOutputCorrRecords(rule, sourceBinding, targetBinding, runtimeLookup, records);
                }
            }
        }

        writeCorrRecords(context, records);
        return records.size();
    }

    private List<Map<String, String>> resolveBindings(TggRuleInfo rule,
                                                      WorkspaceSide side,
                                                      SnapshotIndex index,
                                                      RuntimeLookup runtimeLookup) {
        Map<String, String> typedVariables = rule.getTypedVariables(side);
        if (typedVariables.isEmpty()) {
            return List.of(Map.of());
        }

        List<String> orderedVariables = new ArrayList<>(typedVariables.keySet());
        orderedVariables.sort(Comparator.comparingInt(var -> variableScore(var, rule.getAssociationPatterns(side))));

        List<Map<String, String>> resolved = new ArrayList<>();
        backtrackBindings(orderedVariables, 0, typedVariables, rule.getAssociationPatterns(side),
                rule.getPredicates(side), index, runtimeLookup, new LinkedHashMap<>(), resolved);
        return resolved;
    }

    private int variableScore(String variableName, List<TggRuleInfo.AssociationPattern> patterns) {
        int score = 0;
        for (TggRuleInfo.AssociationPattern pattern : patterns) {
            if (pattern.leftVarName().equals(variableName) || pattern.rightVarName().equals(variableName)) {
                score++;
            }
        }
        return -score;
    }

    private void backtrackBindings(List<String> orderedVariables,
                                   int position,
                                   Map<String, String> typedVariables,
                                   List<TggRuleInfo.AssociationPattern> associations,
                                   List<String> predicates,
                                   SnapshotIndex index,
                                   RuntimeLookup runtimeLookup,
                                   Map<String, String> currentBinding,
                                   List<Map<String, String>> resolved) {
        if (position >= orderedVariables.size()) {
            if (predicates.stream().allMatch(predicate -> evaluateBoolean(predicate, currentBinding, runtimeLookup, Map.of()))) {
                resolved.add(new LinkedHashMap<>(currentBinding));
            }
            return;
        }

        String variableName = orderedVariables.get(position);
        for (ObjectState candidate : index.findByClass(typedVariables.get(variableName))) {
            currentBinding.put(variableName, candidate.name);
            if (associationsSatisfiedSoFar(associations, currentBinding, index)) {
                backtrackBindings(orderedVariables, position + 1, typedVariables, associations, predicates,
                        index, runtimeLookup, currentBinding, resolved);
            }
            currentBinding.remove(variableName);
        }
    }

    private boolean associationsSatisfiedSoFar(List<TggRuleInfo.AssociationPattern> associations,
                                               Map<String, String> currentBinding,
                                               SnapshotIndex index) {
        for (TggRuleInfo.AssociationPattern pattern : associations) {
            String leftObject = currentBinding.get(pattern.leftVarName());
            String rightObject = currentBinding.get(pattern.rightVarName());
            if (leftObject != null && rightObject != null
                    && !index.hasAssociation(pattern.associationName(), leftObject, rightObject)) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesRequiredCorr(TggRuleInfo rule,
                                        Map<String, String> sourceBinding,
                                        Map<String, String> targetBinding,
                                        Collection<CorrRecord> currentRecords) {
        for (TggRuleInfo.CorrPattern pattern : rule.getRequiredCorrPatterns()) {
            String sourceId = sourceBinding.get(pattern.sourceVarName());
            String targetId = targetBinding.get(pattern.targetVarName());
            boolean present = currentRecords.stream().anyMatch(record ->
                    record.corrClassName.equals(pattern.corrClassName())
                            && Objects.equals(record.sourceObjectId, sourceId)
                            && Objects.equals(record.targetObjectId, targetId));
            if (!present) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesCorrInvariants(TggRuleInfo rule,
                                          Map<String, String> sourceBinding,
                                          Map<String, String> targetBinding,
                                          RuntimeLookup runtimeLookup) {
        Map<String, TggRuleInfo.CorrPattern> patternsByClass = new LinkedHashMap<>();
        for (TggRuleInfo.CorrPattern pattern : rule.getRequiredCorrPatterns()) {
            patternsByClass.putIfAbsent(pattern.corrClassName(), pattern);
        }
        for (TggRuleInfo.CorrPattern pattern : rule.getOutputCorrPatterns()) {
            patternsByClass.putIfAbsent(pattern.corrClassName(), pattern);
        }

        for (TggRuleInfo.CorrInvariant invariant : rule.getCorrInvariants()) {
            TggRuleInfo.CorrPattern pattern = patternsByClass.get(invariant.corrClassName());
            if (pattern == null) {
                continue;
            }
            Map<String, String> aliases = new LinkedHashMap<>();
            aliases.put(pattern.sourceAlias(), pattern.sourceVarName());
            aliases.put(pattern.targetAlias(), pattern.targetVarName());
            if (!evaluateBoolean(invariant.expression(), combine(sourceBinding, targetBinding), runtimeLookup, aliases)) {
                return false;
            }
        }
        return true;
    }

    private void appendOutputCorrRecords(TggRuleInfo rule,
                                         Map<String, String> sourceBinding,
                                         Map<String, String> targetBinding,
                                         RuntimeLookup runtimeLookup,
                                         Map<String, CorrRecord> records) {
        for (TggRuleInfo.CorrPattern pattern : rule.getOutputCorrPatterns()) {
            String sourceObjectId = sourceBinding.get(pattern.sourceVarName());
            String targetObjectId = targetBinding.get(pattern.targetVarName());
            if (sourceObjectId == null || targetObjectId == null) {
                continue;
            }

            ObjectState sourceObject = runtimeLookup.findObject(sourceObjectId);
            ObjectState targetObject = runtimeLookup.findObject(targetObjectId);
            if (sourceObject == null || targetObject == null) {
                continue;
            }

            String corrObjectId = pattern.corrObjectName() + "_" + sourceObjectId + "_" + targetObjectId;
            records.put(corrObjectId, new CorrRecord(
                    corrObjectId,
                    pattern.corrClassName(),
                    sourceObjectId,
                    sourceObject.className,
                    targetObjectId,
                    targetObject.className
            ));
        }
    }

    private void writeCorrRecords(TggWorkspaceContext context,
                                  Map<String, CorrRecord> records) {
        try (Session session = org.uet.dse.neo4j.manager.Neo4jDriverManager.getInstance().openSession()) {
            session.executeWrite(tx -> {
                for (CorrRecord record : records.values()) {
                    objectRepository.upsertObjectNode(tx, record.objectId, record.corrClassName);
                    objectRepository.upsertBinaryLink(tx,
                            record.objectId,
                            record.sourceObjectId,
                            record.corrClassName + "_" + record.sourceClassName,
                            "LinkAssociateWith",
                            lowerFirst(record.corrClassName),
                            lowerFirst(record.sourceClassName));
                    objectRepository.upsertBinaryLink(tx,
                            record.objectId,
                            record.targetObjectId,
                            record.corrClassName + "_" + record.targetClassName,
                            "LinkAssociateWith",
                            lowerFirst(record.corrClassName),
                            lowerFirst(record.targetClassName));
                }
                return null;
            });
        }

        ObjectPushService.updateObjectVersionOnServer();
        context.appendLog(WorkspaceSide.CORRESPONDENCE,
                "Materialized " + records.size() + " correspondence object(s) on Neo4j using the rule-driven engine.");
    }

    private Map<String, String> combine(Map<String, String> left, Map<String, String> right) {
        Map<String, String> combined = new LinkedHashMap<>(left);
        combined.putAll(right);
        return combined;
    }

    private boolean evaluateBoolean(String expression,
                                    Map<String, String> variableBindings,
                                    RuntimeLookup runtimeLookup,
                                    Map<String, String> aliasToVariable) {
        String trimmed = expression.trim();
        int notEqualsIndex = trimmed.indexOf("<>");
        if (notEqualsIndex >= 0) {
            Object left = evaluateValue(trimmed.substring(0, notEqualsIndex), variableBindings, runtimeLookup, aliasToVariable);
            Object right = evaluateValue(trimmed.substring(notEqualsIndex + 2), variableBindings, runtimeLookup, aliasToVariable);
            return !valueEquals(left, right);
        }

        int equalsIndex = trimmed.indexOf('=');
        if (equalsIndex >= 0) {
            Object left = evaluateValue(trimmed.substring(0, equalsIndex), variableBindings, runtimeLookup, aliasToVariable);
            Object right = evaluateValue(trimmed.substring(equalsIndex + 1), variableBindings, runtimeLookup, aliasToVariable);
            return valueEquals(left, right);
        }

        Object value = evaluateValue(trimmed, variableBindings, runtimeLookup, aliasToVariable);
        return value instanceof Boolean bool ? bool : value != null;
    }

    private boolean valueEquals(Object left, Object right) {
        if (left == null || right == null) {
            return left == right;
        }
        if (left instanceof Number leftNumber && right instanceof Number rightNumber) {
            return Double.compare(leftNumber.doubleValue(), rightNumber.doubleValue()) == 0;
        }
        return Objects.equals(String.valueOf(left).trim(), String.valueOf(right).trim());
    }

    private Object evaluateValue(String rawExpression,
                                 Map<String, String> variableBindings,
                                 RuntimeLookup runtimeLookup,
                                 Map<String, String> aliasToVariable) {
        String expression = rawExpression.trim();
        if (expression.isEmpty()) {
            return null;
        }
        if ("Undefined".equalsIgnoreCase(expression)) {
            return null;
        }
        if ((expression.startsWith("'") && expression.endsWith("'")) || (expression.startsWith("\"") && expression.endsWith("\""))) {
            return expression.substring(1, expression.length() - 1);
        }
        if (expression.matches("-?\\d+")) {
            return Integer.parseInt(expression);
        }

        String splitMarker = ".split(";
        int splitIndex = expression.indexOf(splitMarker);
        if (splitIndex >= 0) {
            String baseExpression = expression.substring(0, splitIndex);
            Object baseValue = evaluateValue(baseExpression, variableBindings, runtimeLookup, aliasToVariable);
            if (baseValue == null) {
                return null;
            }
            String remainder = expression.substring(splitIndex + splitMarker.length());
            int closeIndex = remainder.indexOf(')');
            String delimiterToken = remainder.substring(0, closeIndex).trim();
            String delimiter = String.valueOf(evaluateValue(delimiterToken, variableBindings, runtimeLookup, aliasToVariable));
            String[] parts = String.valueOf(baseValue).split(java.util.regex.Pattern.quote(delimiter));
            String afterSplit = remainder.substring(closeIndex + 1).trim();
            if (afterSplit.startsWith("->at(") && afterSplit.endsWith(")")) {
                int atIndex = Integer.parseInt(afterSplit.substring(5, afterSplit.length() - 1).trim()) - 1;
                if (atIndex >= 0 && atIndex < parts.length) {
                    return parts[atIndex].trim();
                }
                return null;
            }
            return List.of(parts);
        }

        String normalized = expression.startsWith("self.") ? expression.substring("self.".length()) : expression;
        String[] path = normalized.split("\\.");
        if (path.length == 0) {
            return normalized;
        }

        String firstToken = path[0].trim();
        String variableName = aliasToVariable.getOrDefault(firstToken, firstToken);
        String objectId = variableBindings.get(variableName);
        Object current = objectId != null ? runtimeLookup.findObject(objectId) : null;
        if (current == null) {
            return normalized;
        }

        for (int i = 1; i < path.length; i++) {
            String segment = path[i].trim();
            if (current instanceof ObjectState objectState) {
                if (objectState.primitiveValues.containsKey(segment)) {
                    current = objectState.primitiveValues.get(segment);
                    continue;
                }
                List<Object> references = objectState.objectReferences.get(segment);
                if (references != null && !references.isEmpty()) {
                    current = runtimeLookup.findObject(String.valueOf(references.get(0)));
                    continue;
                }
                return null;
            }
            return null;
        }
        return current;
    }

    private String lowerFirst(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return Character.toLowerCase(value.charAt(0)) + value.substring(1);
    }

    private static final class SnapshotIndex {
        private final FullObjectSnapshot snapshot;
        private final Map<String, List<ObjectState>> objectsByClass = new LinkedHashMap<>();
        private final Map<String, Set<String>> linksByAssociation = new LinkedHashMap<>();

        private SnapshotIndex(FullObjectSnapshot snapshot) {
            this.snapshot = snapshot;
            for (ObjectState objectState : snapshot.objects.values()) {
                objectsByClass.computeIfAbsent(objectState.className, ignored -> new ArrayList<>()).add(objectState);
            }
            for (LinkState linkState : snapshot.links.values()) {
                if (linkState.participants.size() >= 2) {
                    linksByAssociation
                            .computeIfAbsent(linkState.assocName, ignored -> new LinkedHashSet<>())
                            .add(linkState.participants.get(0) + "->" + linkState.participants.get(1));
                }
            }
        }

        private List<ObjectState> findByClass(String className) {
            return objectsByClass.getOrDefault(className, List.of());
        }

        private boolean hasAssociation(String associationName, String leftObjectId, String rightObjectId) {
            return linksByAssociation.getOrDefault(associationName, Set.of()).contains(leftObjectId + "->" + rightObjectId);
        }
    }

    private static final class RuntimeLookup {
        private final Map<String, ObjectState> allObjects = new LinkedHashMap<>();

        private RuntimeLookup(SnapshotIndex source, SnapshotIndex target) {
            allObjects.putAll(source.snapshot.objects);
            allObjects.putAll(target.snapshot.objects);
        }

        private ObjectState findObject(String objectId) {
            return allObjects.get(objectId);
        }
    }

    private record CorrRecord(String objectId,
                              String corrClassName,
                              String sourceObjectId,
                              String sourceClassName,
                              String targetObjectId,
                              String targetClassName) {
    }
}
