package org.uet.dse.neo4jtgg.service.impl;

import org.uet.dse.neo4jtgg.model.RuleDependencyGraph;
import org.uet.dse.neo4jtgg.model.TggRuleInfo;
import org.uet.dse.neo4jtgg.model.TggWorkspaceDefinition;
import org.uet.dse.neo4jtgg.model.WorkspaceSide;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RuleDependencyGraphBuilder {
    private static final Pattern PATH_PATTERN = Pattern.compile("(?:self\\.)?(\\w+)\\.(\\w+)");

    public RuleDependencyGraph build(TggWorkspaceDefinition definition) {
        Map<String, Set<String>> sourceAttrs = new LinkedHashMap<>();
        Map<String, Set<String>> targetAttrs = new LinkedHashMap<>();
        Map<String, Set<String>> sourceAssocs = new LinkedHashMap<>();
        Map<String, Set<String>> targetAssocs = new LinkedHashMap<>();
        Map<String, Map<String, Set<String>>> sourceToTarget = new LinkedHashMap<>();
        Map<String, Map<String, Set<String>>> targetToSource = new LinkedHashMap<>();

        for (TggRuleInfo rule : definition.getRules()) {
            Set<String> sourceAttrSet = new LinkedHashSet<>();
            Set<String> targetAttrSet = new LinkedHashSet<>();
            Set<String> sourceAssocSet = new LinkedHashSet<>();
            Set<String> targetAssocSet = new LinkedHashSet<>();
            Map<String, Set<String>> sourceToTargetAttrs = new LinkedHashMap<>();
            Map<String, Set<String>> targetToSourceAttrs = new LinkedHashMap<>();

            for (TggRuleInfo.AssociationPattern pattern : rule.getAssociationPatterns(WorkspaceSide.SOURCE)) {
                sourceAssocSet.add(pattern.associationName());
            }
            for (TggRuleInfo.AssociationPattern pattern : rule.getAssociationPatterns(WorkspaceSide.TARGET)) {
                targetAssocSet.add(pattern.associationName());
            }

            for (TggRuleInfo.AttributeMapping mapping : rule.getAttributeMappings()) {
                if (mapping.targetSide() == WorkspaceSide.TARGET) {
                    targetAttrSet.add(mapping.targetAttributeName());
                } else if (mapping.targetSide() == WorkspaceSide.SOURCE) {
                    sourceAttrSet.add(mapping.targetAttributeName());
                }

                Matcher matcher = PATH_PATTERN.matcher(mapping.sourceExpression());
                while (matcher.find()) {
                    String variableName = matcher.group(1);
                    String attributeName = matcher.group(2);
                    if (rule.getTypedVariables(WorkspaceSide.SOURCE).containsKey(variableName)) {
                        sourceAttrSet.add(attributeName);
                        if (mapping.targetSide() == WorkspaceSide.TARGET) {
                            sourceToTargetAttrs.computeIfAbsent(attributeName, ignored -> new LinkedHashSet<>())
                                    .add(mapping.targetAttributeName());
                            targetToSourceAttrs.computeIfAbsent(mapping.targetAttributeName(), ignored -> new LinkedHashSet<>())
                                    .add(attributeName);
                        }
                    } else if (rule.getTypedVariables(WorkspaceSide.TARGET).containsKey(variableName)) {
                        targetAttrSet.add(attributeName);
                        if (mapping.targetSide() == WorkspaceSide.SOURCE) {
                            targetToSourceAttrs.computeIfAbsent(attributeName, ignored -> new LinkedHashSet<>())
                                    .add(mapping.targetAttributeName());
                            sourceToTargetAttrs.computeIfAbsent(mapping.targetAttributeName(), ignored -> new LinkedHashSet<>())
                                    .add(attributeName);
                        }
                    }
                }
            }

            sourceAttrs.put(rule.getName(), sourceAttrSet);
            targetAttrs.put(rule.getName(), targetAttrSet);
            sourceAssocs.put(rule.getName(), sourceAssocSet);
            targetAssocs.put(rule.getName(), targetAssocSet);
            sourceToTarget.put(rule.getName(), sourceToTargetAttrs);
            targetToSource.put(rule.getName(), targetToSourceAttrs);
        }

        return new RuleDependencyGraph(sourceAttrs, targetAttrs, sourceAssocs, targetAssocs, sourceToTarget, targetToSource);
    }
}
