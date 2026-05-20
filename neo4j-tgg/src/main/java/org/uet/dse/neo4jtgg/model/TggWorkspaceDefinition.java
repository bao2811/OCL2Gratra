package org.uet.dse.neo4jtgg.model;

import org.tzi.use.uml.mm.MModel;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TggWorkspaceDefinition {
    private final String transformationName;
    private final MModel sourceModel;
    private final MModel correspondenceModel;
    private final MModel targetModel;
    private final List<TggRuleInfo> rules;
    private final Map<WorkspaceSide, Set<String>> classNames = new EnumMap<>(WorkspaceSide.class);

    public TggWorkspaceDefinition(String transformationName,
                                  MModel sourceModel,
                                  MModel correspondenceModel,
                                  MModel targetModel,
                                  List<TggRuleInfo> rules,
                                  Set<String> sourceClassNames,
                                  Set<String> correspondenceClassNames,
                                  Set<String> targetClassNames) {
        this.transformationName = transformationName;
        this.sourceModel = sourceModel;
        this.correspondenceModel = correspondenceModel;
        this.targetModel = targetModel;
        this.rules = List.copyOf(rules);
        classNames.put(WorkspaceSide.SOURCE, Collections.unmodifiableSet(new LinkedHashSet<>(sourceClassNames)));
        classNames.put(WorkspaceSide.CORRESPONDENCE, Collections.unmodifiableSet(new LinkedHashSet<>(correspondenceClassNames)));
        classNames.put(WorkspaceSide.TARGET, Collections.unmodifiableSet(new LinkedHashSet<>(targetClassNames)));
    }

    public String getTransformationName() {
        return transformationName;
    }

    public MModel getSourceModel() {
        return sourceModel;
    }

    public MModel getTargetModel() {
        return targetModel;
    }

    public MModel getCorrespondenceModel() {
        return correspondenceModel;
    }

    public MModel getModel(WorkspaceSide side) {
        if (side == WorkspaceSide.SOURCE) {
            return sourceModel;
        }
        if (side == WorkspaceSide.TARGET) {
            return targetModel;
        }
        if (side == WorkspaceSide.CORRESPONDENCE) {
            return correspondenceModel;
        }
        throw new IllegalArgumentException("Unsupported workspace side: " + side);
    }

    public List<TggRuleInfo> getRules() {
        return rules;
    }

    public TggRuleInfo getRuleByName(String ruleName) {
        return rules.stream().filter(rule -> rule.getName().equals(ruleName)).findFirst().orElse(null);
    }

    public Set<String> getClassNames(WorkspaceSide side) {
        return classNames.getOrDefault(side, Set.of());
    }
}
