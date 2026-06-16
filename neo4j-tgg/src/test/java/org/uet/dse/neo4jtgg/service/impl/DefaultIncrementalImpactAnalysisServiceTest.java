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

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultIncrementalImpactAnalysisServiceTest {
    private static final Path REPO_ROOT = Path.of("..").toAbsolutePath().normalize();

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

    @Test
    void loadsGenericCompany2ItWorkspaceAndBuildsDependencies() throws Exception {
        TggWorkspaceContext context = company2ItContext();

        TggWorkspaceDefinition definition = new Neo4jTggWorkspaceLoader()
                .load(context, new PrintWriter(new StringWriter(), true));

        assertEquals("Company2IT", definition.getTransformationName());
        assertEquals(Set.of("BenchmarkRoot", "Company", "Person"), definition.getClassNames(WorkspaceSide.SOURCE));
        assertEquals(Set.of("ITRegister", "ITCompany", "ITWorker"), definition.getClassNames(WorkspaceSide.TARGET));
        assertTrue(definition.getClassNames(WorkspaceSide.CORRESPONDENCE)
                .containsAll(Set.of("Root2Register", "Company2Node", "Person2Worker")));

        TggRuleInfo companyToNode = definition.getRuleByName("CompanyToCompanyNode");
        assertNotNull(companyToNode);
        assertEquals(Map.of("root", "BenchmarkRoot", "company", "Company"),
                companyToNode.getTypedVariables(WorkspaceSide.SOURCE));
        assertEquals(Map.of("reg", "ITRegister", "node", "ITCompany"),
                companyToNode.getTypedVariables(WorkspaceSide.TARGET));
        assertTrue(companyToNode.getAttributeMappings().contains(
                new TggRuleInfo.AttributeMapping(WorkspaceSide.TARGET, "node", "name", "self.company.name")));

        TggRuleInfo personToWorker = definition.getRuleByName("PersonToWorker");
        assertNotNull(personToWorker);
        assertTrue(personToWorker.getAttributeMappings().containsAll(List.of(
                new TggRuleInfo.AttributeMapping(WorkspaceSide.TARGET, "worker", "firstName", "self.person.firstName"),
                new TggRuleInfo.AttributeMapping(WorkspaceSide.TARGET, "worker", "age", "self.person.age"),
                new TggRuleInfo.AttributeMapping(WorkspaceSide.TARGET, "worker", "salary", "self.person.salary")
        )));

        var dependencyGraph = new RuleDependencyGraphBuilder().build(definition);
        assertEquals(Set.of("name"), dependencyGraph.sourceAttributes("CompanyToCompanyNode"));
        assertEquals(Set.of("name"), dependencyGraph.targetAttributes("CompanyToCompanyNode"));
        assertEquals(Set.of("firstName", "age", "salary"), dependencyGraph.sourceAttributes("PersonToWorker"));
        assertEquals(Set.of("firstName", "age", "salary"), dependencyGraph.targetAttributes("PersonToWorker"));
        assertEquals(Set.of("firstName"), dependencyGraph.targetAttributesForSourceAttribute("PersonToWorker", "firstName"));
        assertEquals(Set.of("age"), dependencyGraph.targetAttributesForSourceAttribute("PersonToWorker", "age"));
        assertEquals(Set.of("salary"), dependencyGraph.targetAttributesForSourceAttribute("PersonToWorker", "salary"));
    }

    @Test
    void analyzesGenericCompany2ItPersonChangeFromLoadedWorkspace() throws Exception {
        TggWorkspaceContext context = company2ItContext();
        TggWorkspaceDefinition definition = new Neo4jTggWorkspaceLoader()
                .load(context, new PrintWriter(new StringWriter(), true));
        context.setWorkspaceDefinition(definition);

        FullObjectSnapshot previous = new FullObjectSnapshot();
        previous.objects.put("person_1", object("person_1", "Person", null));
        previous.objects.get("person_1").primitiveValues.put("firstName", "Alice");
        previous.objects.get("person_1").primitiveValues.put("age", 30);
        previous.objects.get("person_1").primitiveValues.put("salary", 1000);

        FullObjectSnapshot current = new FullObjectSnapshot();
        current.objects.put("person_1", object("person_1", "Person", null));
        current.objects.get("person_1").primitiveValues.put("firstName", "Alicia");
        current.objects.get("person_1").primitiveValues.put("age", 30);
        current.objects.get("person_1").primitiveValues.put("salary", 1000);

        NormalizedChangeSet changeSet = new DefaultWorkspaceChangeDetector().detectSourceChanges(context, previous, current);
        context.setLastSnapshot(WorkspaceSide.SOURCE, current);

        FullObjectSnapshot corr = new FullObjectSnapshot();
        corr.objects.put("p2w_1", object("p2w_1", "Person2Worker", null));
        corr.links.put("trace_src", traceLink("p2w_1", "person_1", "PersonToWorker", WorkspaceSide.SOURCE, "person"));
        corr.links.put("trace_tgt", traceLink("p2w_1", "worker_1", "PersonToWorker", WorkspaceSide.TARGET, "worker"));

        ImpactAnalysisResult result = new DefaultIncrementalImpactAnalysisService()
                .analyze(context, changeSet, current, new FullObjectSnapshot(), corr);

        assertEquals(TransformationDirection.FORWARD, result.direction());
        assertTrue(result.affectedRuleNames().contains("PersonToWorker"));
        assertTrue(result.affectedSourceObjectIds().contains("person_1"));
        assertTrue(result.affectedTargetObjectIds().contains("worker_1"));
        assertTrue(result.affectedCorrObjectIds().contains("p2w_1"));
        assertEquals(Set.of("firstName"), result.affectedSourceAttributesByRule().get("PersonToWorker"));
        assertEquals(Set.of("firstName", "age", "salary"), result.affectedTargetAttributesByRule().get("PersonToWorker"));
        assertFalse(result.blocked());
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

    private TggWorkspaceContext company2ItContext() {
        TggWorkspaceContext context = new TggWorkspaceContext(null, null);
        context.setSourceFile(file("examples/company2it-benchmark/metamodels/Company2IT.use"));
        context.setTargetFile(file("examples/company2it-benchmark/metamodels/IT.use"));
        context.setTggFile(file("examples/company2it-benchmark/metamodels/Company2ITForward.tgg"));
        return context;
    }

    private File file(String relativePath) {
        return REPO_ROOT.resolve(relativePath).toFile();
    }
}
