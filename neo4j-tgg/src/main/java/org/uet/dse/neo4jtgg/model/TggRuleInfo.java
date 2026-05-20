package org.uet.dse.neo4jtgg.model;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

public class TggRuleInfo {
    private final String name;
    private final Map<WorkspaceSide, String> sideConstraints = new EnumMap<>(WorkspaceSide.class);
    private final Map<WorkspaceSide, Map<String, String>> typedVariables = new EnumMap<>(WorkspaceSide.class);
    private final Map<WorkspaceSide, List<AssociationPattern>> associationPatterns = new EnumMap<>(WorkspaceSide.class);
    private final Map<WorkspaceSide, List<String>> predicates = new EnumMap<>(WorkspaceSide.class);
    private final List<CorrPattern> requiredCorrPatterns = new ArrayList<>();
    private final List<CorrPattern> outputCorrPatterns = new ArrayList<>();
    private final List<CorrInvariant> corrInvariants = new ArrayList<>();

    public TggRuleInfo(String name) {
        this.name = name;
        for (WorkspaceSide side : WorkspaceSide.values()) {
            sideConstraints.put(side, "");
            typedVariables.put(side, new LinkedHashMap<>());
            associationPatterns.put(side, new ArrayList<>());
            predicates.put(side, new ArrayList<>());
        }
    }

    public String getName() {
        return name;
    }

    public String getSideConstraints(WorkspaceSide side) {
        return sideConstraints.getOrDefault(side, "");
    }

    public void setSideConstraints(WorkspaceSide side, String constraints) {
        sideConstraints.put(side, constraints == null ? "" : constraints.trim());
    }

    public Map<String, String> getTypedVariables(WorkspaceSide side) {
        return Map.copyOf(typedVariables.getOrDefault(side, Map.of()));
    }

    public void setTypedVariables(WorkspaceSide side, Map<String, String> variables) {
        typedVariables.get(side).clear();
        typedVariables.get(side).putAll(variables);
    }

    public List<AssociationPattern> getAssociationPatterns(WorkspaceSide side) {
        return List.copyOf(associationPatterns.getOrDefault(side, List.of()));
    }

    public void setAssociationPatterns(WorkspaceSide side, List<AssociationPattern> patterns) {
        associationPatterns.get(side).clear();
        associationPatterns.get(side).addAll(patterns);
    }

    public List<String> getPredicates(WorkspaceSide side) {
        return List.copyOf(predicates.getOrDefault(side, List.of()));
    }

    public void setPredicates(WorkspaceSide side, List<String> expressions) {
        predicates.get(side).clear();
        predicates.get(side).addAll(expressions);
    }

    public List<CorrPattern> getRequiredCorrPatterns() {
        return List.copyOf(requiredCorrPatterns);
    }

    public void setRequiredCorrPatterns(List<CorrPattern> patterns) {
        requiredCorrPatterns.clear();
        requiredCorrPatterns.addAll(patterns);
    }

    public List<CorrPattern> getOutputCorrPatterns() {
        return List.copyOf(outputCorrPatterns);
    }

    public void setOutputCorrPatterns(List<CorrPattern> patterns) {
        outputCorrPatterns.clear();
        outputCorrPatterns.addAll(patterns);
    }

    public List<CorrInvariant> getCorrInvariants() {
        return List.copyOf(corrInvariants);
    }

    public void setCorrInvariants(List<CorrInvariant> invariants) {
        corrInvariants.clear();
        corrInvariants.addAll(invariants);
    }

    public record AssociationPattern(String leftVarName, String rightVarName, String associationName) {
    }

    public record CorrPattern(String sourceVarName,
                              String targetVarName,
                              String sourceAlias,
                              String targetAlias,
                              String corrObjectName,
                              String corrClassName) {
    }

    public record CorrInvariant(String corrClassName, String expression) {
    }
}
