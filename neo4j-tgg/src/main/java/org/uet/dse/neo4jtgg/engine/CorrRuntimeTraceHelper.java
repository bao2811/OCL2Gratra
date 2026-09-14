package org.uet.dse.neo4jtgg.engine;

import org.uet.dse.neo4j.model.FullObjectSnapshot;
import org.uet.dse.neo4j.model.LinkState;
import org.uet.dse.neo4j.model.ObjectState;
import org.uet.dse.neo4jtgg.model.TggRuleInfo;
import org.uet.dse.neo4jtgg.model.TggWorkspaceDefinition;
import org.uet.dse.neo4jtgg.model.WorkspaceSide;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class CorrRuntimeTraceHelper {
    private static final String TRACE_PREFIX = "__TGG_BINDING__";

    private CorrRuntimeTraceHelper() {
    }

    public static String bindingAssociationName(String ruleName, WorkspaceSide side, String variableName) {
        return TRACE_PREFIX + side.name() + "__" + encode(ruleName) + "__" + encode(variableName);
    }

    public static boolean isBindingAssociation(String associationName) {
        return associationName != null && associationName.startsWith(TRACE_PREFIX);
    }

    public static TraceLink parseTraceLink(LinkState linkState) {
        if (linkState == null || !isBindingAssociation(linkState.assocName) || linkState.participants.size() < 2) {
            return null;
        }
        String payload = linkState.assocName.substring(TRACE_PREFIX.length());
        String[] parts = payload.split("__", 3);
        if (parts.length != 3) {
            return null;
        }
        WorkspaceSide side;
        try {
            side = WorkspaceSide.valueOf(parts[0]);
        } catch (IllegalArgumentException ex) {
            return null;
        }
        String corrObjectId = linkState.participants.get(0);
        String objectId = linkState.participants.get(1);
        return new TraceLink(corrObjectId, objectId, side, decode(parts[1]), decode(parts[2]));
    }

    public static Map<String, String> extractBindingForSide(String corrObjectId,
                                                            FullObjectSnapshot corrSnapshot,
                                                            WorkspaceSide side) {
        Map<String, String> bindings = new LinkedHashMap<>();
        if (corrSnapshot == null) {
            return bindings;
        }
        for (LinkState linkState : corrSnapshot.links.values()) {
            TraceLink traceLink = parseTraceLink(linkState);
            if (traceLink == null
                    || !Objects.equals(traceLink.corrObjectId(), corrObjectId)
                    || traceLink.side() != side) {
                continue;
            }
            bindings.put(traceLink.variableName(), traceLink.objectId());
        }
        return bindings;
    }

    public static Set<String> extractRuleNames(String corrObjectId, FullObjectSnapshot corrSnapshot) {
        Set<String> ruleNames = new LinkedHashSet<>();
        if (corrSnapshot == null) {
            return ruleNames;
        }
        for (LinkState linkState : corrSnapshot.links.values()) {
            TraceLink traceLink = parseTraceLink(linkState);
            if (traceLink != null && Objects.equals(traceLink.corrObjectId(), corrObjectId) && !traceLink.ruleName().isBlank()) {
                ruleNames.add(traceLink.ruleName());
            }
        }
        return ruleNames;
    }

    public static List<String> resolveBoundObjects(String corrObjectId,
                                                   FullObjectSnapshot corrSnapshot,
                                                   WorkspaceSide side) {
        return new ArrayList<>(extractBindingForSide(corrObjectId, corrSnapshot, side).values());
    }

    public static ResolvedCorrBinding resolveCorrBinding(ObjectState corrObj,
                                                         FullObjectSnapshot corrSnapshot,
                                                         TggWorkspaceDefinition definition) {
        if (corrObj == null) {
            return null;
        }

        String ruleName = extractRuleNames(corrObj.name, corrSnapshot).stream().findFirst().orElse("");
        if (ruleName.isBlank() && definition != null) {
            ruleName = resolveRuleName(definition, corrObj.className);
        }

        Map<String, String> sourceBindings = extractBindingForSide(corrObj.name, corrSnapshot, WorkspaceSide.SOURCE);
        Map<String, String> targetBindings = extractBindingForSide(corrObj.name, corrSnapshot, WorkspaceSide.TARGET);
        String sourceObjectId = resolvePrimaryObjectId(corrObj, corrSnapshot, definition, ruleName, sourceBindings, true);
        String targetObjectId = resolvePrimaryObjectId(corrObj, corrSnapshot, definition, ruleName, targetBindings, false);
        if (sourceObjectId == null && targetObjectId == null) {
            return null;
        }
        return new ResolvedCorrBinding(corrObj.name, corrObj.className, sourceObjectId, targetObjectId,
                ruleName, sourceBindings, targetBindings);
    }

    private static String resolvePrimaryObjectId(ObjectState corrObj,
                                                 FullObjectSnapshot corrSnapshot,
                                                 TggWorkspaceDefinition definition,
                                                 String ruleName,
                                                 Map<String, String> bindings,
                                                 boolean sourceSide) {
        if (!bindings.isEmpty()) {
            if (definition != null && !ruleName.isBlank()) {
                TggRuleInfo rule = definition.getRuleByName(ruleName);
                if (rule != null) {
                    for (TggRuleInfo.CorrPattern pattern : rule.getOutputCorrPatterns()) {
                        String objectId = bindings.get(sourceSide ? pattern.sourceVarName() : pattern.targetVarName());
                        if (objectId != null) {
                            return objectId;
                        }
                    }
                }
            }
            return bindings.values().iterator().next();
        }
        if (corrObj.objectReferences != null && !corrObj.objectReferences.isEmpty()) {
            List<String> refs = new ArrayList<>();
            corrObj.objectReferences.values().forEach(values -> values.forEach(value -> refs.add(String.valueOf(value))));
            if (!refs.isEmpty()) {
                return sourceSide ? refs.get(0) : refs.get(refs.size() - 1);
            }
        }
        if (corrSnapshot != null) {
            List<String> fallbackRefs = new ArrayList<>();
            for (LinkState linkState : corrSnapshot.links.values()) {
                if (isBindingAssociation(linkState.assocName) || linkState.participants.size() < 2) {
                    continue;
                }
                if (Objects.equals(linkState.participants.get(0), corrObj.name)) {
                    fallbackRefs.add(linkState.participants.get(1));
                } else if (Objects.equals(linkState.participants.get(1), corrObj.name)) {
                    fallbackRefs.add(linkState.participants.get(0));
                }
            }
            if (!fallbackRefs.isEmpty()) {
                return sourceSide ? fallbackRefs.get(0) : fallbackRefs.get(fallbackRefs.size() - 1);
            }
        }
        return null;
    }

    private static String resolveRuleName(TggWorkspaceDefinition definition, String corrClassName) {
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

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    public record TraceLink(String corrObjectId,
                            String objectId,
                            WorkspaceSide side,
                            String ruleName,
                            String variableName) {
    }

    public record ResolvedCorrBinding(String corrObjectId,
                                      String corrClassName,
                                      String sourceObjectId,
                                      String targetObjectId,
                                      String ruleName,
                                      Map<String, String> sourceBindings,
                                      Map<String, String> targetBindings) {
    }
}
