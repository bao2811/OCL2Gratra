package org.uet.dse.neo4jtgg.service.impl;

import org.junit.jupiter.api.Test;
import org.uet.dse.neo4j.model.FullObjectSnapshot;
import org.uet.dse.neo4j.model.LinkState;
import org.uet.dse.neo4j.model.ObjectState;
import org.uet.dse.neo4jtgg.engine.CorrRuntimeTraceHelper;
import org.uet.dse.neo4jtgg.engine.TransformationDirection;
import org.uet.dse.neo4jtgg.model.ImpactAnalysisResult;
import org.uet.dse.neo4jtgg.model.NormalizedChangeSet;
import org.uet.dse.neo4jtgg.model.TggRuleInfo;
import org.uet.dse.neo4jtgg.model.TggWorkspaceContext;
import org.uet.dse.neo4jtgg.model.TggWorkspaceDefinition;
import org.uet.dse.neo4jtgg.model.WorkspaceSide;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultIncrementalImpactAnalysisServiceTest {

    @Test
    void resolvesAffectedRuleAndObjectsFromCorrRuntime() {
        TggRuleInfo rule = new TggRuleInfo("MemberToMale");
        rule.setTypedVariables(WorkspaceSide.SOURCE, java.util.Map.of("member", "FamilyMember"));
        rule.setTypedVariables(WorkspaceSide.TARGET, java.util.Map.of("mp", "Male"));
        rule.setOutputCorrPatterns(List.of(new TggRuleInfo.CorrPattern("member", "mp", "source", "target", "corr", "F2MP")));
        rule.setCorrInvariants(List.of(new TggRuleInfo.CorrInvariant("F2MP", "self.mp.name := self.member.name")));
        rule.setAttributeMappings(List.of(new TggRuleInfo.AttributeMapping(WorkspaceSide.TARGET, "mp", "name", "self.member.name")));

        TggWorkspaceContext context = new TggWorkspaceContext(null, null);
        context.setWorkspaceDefinition(new TggWorkspaceDefinition("Test", null, null, null, List.of(rule),
                Set.of("FamilyMember"), Set.of("F2MP"), Set.of("Male")));

        FullObjectSnapshot previous = new FullObjectSnapshot();
        previous.objects.put("bart", object("bart", "FamilyMember", "Bart"));
        FullObjectSnapshot current = new FullObjectSnapshot();
        current.objects.put("bart", object("bart", "FamilyMember", "Hugo"));

        DefaultWorkspaceChangeDetector detector = new DefaultWorkspaceChangeDetector();
        NormalizedChangeSet changeSet = detector.detectSourceChanges(context, previous, current);
        context.setLastSnapshot(WorkspaceSide.SOURCE, current);

        FullObjectSnapshot corr = new FullObjectSnapshot();
        ObjectState corrObj = object("corr1", "F2MP", null);
        corrObj.objectReferences = new LinkedHashMap<>();
        corrObj.objectReferences.put("source", List.of("bart"));
        corrObj.objectReferences.put("target", List.of("male_bart"));
        corr.objects.put("corr1", corrObj);
        corr.links.put("trace_src", traceLink("corr1", "bart", "MemberToMale", WorkspaceSide.SOURCE, "member"));
        corr.links.put("trace_tgt", traceLink("corr1", "male_bart", "MemberToMale", WorkspaceSide.TARGET, "mp"));

        ImpactAnalysisResult result = new DefaultIncrementalImpactAnalysisService()
                .analyze(context, changeSet, current, new FullObjectSnapshot(), corr);

        assertEquals(TransformationDirection.FORWARD, result.direction());
        assertTrue(result.affectedRuleNames().contains("MemberToMale"));
        assertTrue(result.affectedSourceObjectIds().contains("bart"));
        assertTrue(result.affectedTargetObjectIds().contains("male_bart"));
        assertTrue(result.affectedSourceAttributesByRule().get("MemberToMale").contains("name"));
        assertTrue(result.affectedTargetAttributesByRule().get("MemberToMale").contains("name"));
        assertFalse(result.blocked());
        assertFalse(result.requiresManualTransform());
    }

    @Test
    void resolvesAffectedRuleFromCorrTraceLinksWithoutObjectReferenceAttributes() {
        TggRuleInfo rule = new TggRuleInfo("MemberToMale");
        rule.setTypedVariables(WorkspaceSide.SOURCE, java.util.Map.of("member", "FamilyMember"));
        rule.setTypedVariables(WorkspaceSide.TARGET, java.util.Map.of("mp", "Male"));
        rule.setOutputCorrPatterns(List.of(new TggRuleInfo.CorrPattern("member", "mp", "source", "target", "corr", "F2MP")));
        rule.setCorrInvariants(List.of(new TggRuleInfo.CorrInvariant("F2MP", "self.mp.name := self.member.name")));
        rule.setAttributeMappings(List.of(new TggRuleInfo.AttributeMapping(WorkspaceSide.TARGET, "mp", "name", "self.member.name")));

        TggWorkspaceContext context = new TggWorkspaceContext(null, null);
        context.setWorkspaceDefinition(new TggWorkspaceDefinition("Test", null, null, null, List.of(rule),
                Set.of("FamilyMember"), Set.of("F2MP"), Set.of("Male")));

        FullObjectSnapshot previous = new FullObjectSnapshot();
        previous.objects.put("bart", object("bart", "FamilyMember", "Bart"));
        FullObjectSnapshot current = new FullObjectSnapshot();
        current.objects.put("bart", object("bart", "FamilyMember", "Hugo"));

        NormalizedChangeSet changeSet = new DefaultWorkspaceChangeDetector().detectSourceChanges(context, previous, current);
        context.setLastSnapshot(WorkspaceSide.SOURCE, current);

        FullObjectSnapshot corr = new FullObjectSnapshot();
        corr.objects.put("corr1", object("corr1", "F2MP", null));
        corr.links.put("trace_src", traceLink("corr1", "bart", "MemberToMale", WorkspaceSide.SOURCE, "member"));
        corr.links.put("trace_tgt", traceLink("corr1", "male_bart", "MemberToMale", WorkspaceSide.TARGET, "mp"));

        ImpactAnalysisResult result = new DefaultIncrementalImpactAnalysisService()
                .analyze(context, changeSet, current, new FullObjectSnapshot(), corr);

        assertTrue(result.affectedRuleNames().contains("MemberToMale"));
        assertTrue(result.affectedTargetObjectIds().contains("male_bart"));
        assertFalse(result.requiresManualTransform());
    }

    private ObjectState object(String id, String className, String name) {
        ObjectState state = new ObjectState();
        state.name = id;
        state.className = className;
        state.primitiveValues = new LinkedHashMap<>();
        if (name != null) {
            state.primitiveValues.put("name", name);
        }
        state.objectReferences = new LinkedHashMap<>();
        return state;
    }

    private LinkState traceLink(String corrId, String objectId, String ruleName, WorkspaceSide side, String variableName) {
        LinkState state = new LinkState();
        state.assocName = CorrRuntimeTraceHelper.bindingAssociationName(ruleName, side, variableName);
        state.participants = List.of(corrId, objectId);
        return state;
    }
}
