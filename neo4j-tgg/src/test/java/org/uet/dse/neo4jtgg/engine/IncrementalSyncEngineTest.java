package org.uet.dse.neo4jtgg.engine;

import org.junit.jupiter.api.Test;
import org.uet.dse.neo4j.model.FullObjectSnapshot;
import org.uet.dse.neo4j.model.LinkState;
import org.uet.dse.neo4j.model.ObjectState;
import org.uet.dse.neo4jtgg.model.ModelDelta;
import org.uet.dse.neo4jtgg.model.ObjectChange;
import org.uet.dse.neo4jtgg.model.ImpactAnalysisResult;
import org.uet.dse.neo4jtgg.model.RuleDependencyGraph;
import org.uet.dse.neo4jtgg.model.TggRuleInfo;
import org.uet.dse.neo4jtgg.model.TggWorkspaceContext;
import org.uet.dse.neo4jtgg.model.TggWorkspaceDefinition;
import org.uet.dse.neo4jtgg.model.WorkspaceSide;
import org.uet.dse.neo4jtgg.model.WorkspaceMutationBatch;
import org.uet.dse.neo4jtgg.model.LinkChange;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class IncrementalSyncEngineTest {

    @Test
    void testSyncForward_AddedSourceObject_ProducesTarget() {
        TggRuleInfo rule = new TggRuleInfo("MemberToMale");
        rule.setTypedVariables(WorkspaceSide.SOURCE, Map.of("member", "FamilyMember"));
        rule.setTypedVariables(WorkspaceSide.TARGET, Map.of("mp", "Male"));

        TggWorkspaceDefinition definition = buildDefinition(List.of(rule));
        TggWorkspaceContext context = createMockContext(definition);

        FullObjectSnapshot sourceSnapshot = new FullObjectSnapshot();
        sourceSnapshot.objects.put("bart", makeObject("bart", "FamilyMember", Map.of("name", "Bart")));

        ModelDelta delta = new ModelDelta(WorkspaceSide.SOURCE,
                List.of(ObjectChange.added("bart", "FamilyMember", Map.of("name", "Bart"))),
                List.of(), List.of(), List.of(), List.of(), 0L, 1L);

        TransformationReport report = new TransformationReport(
                TransformationDirection.FORWARD, TransformationMode.PREVIEW);
        IncrementalSyncEngine engine = new IncrementalSyncEngine(report);

        WorkspaceMutationBatch batch = engine.syncForward(context, delta, sourceSnapshot,
                new FullObjectSnapshot(), new FullObjectSnapshot());

        assertFalse(batch.getUpsertObjects().isEmpty(), "Should produce target object for added source");
        assertTrue(batch.getUpsertObjects().stream().anyMatch(o -> "Male".equals(o.getClassName())),
                "Should create Male from FamilyMember");
        assertTrue(engine.getConflicts().isEmpty(), "No conflicts expected for add");
    }

    @Test
    void testSyncForward_ModifiedSourceObject_PropagatesAttribute() {
        TggRuleInfo rule = new TggRuleInfo("MemberToMale");
        rule.setTypedVariables(WorkspaceSide.SOURCE, Map.of("member", "FamilyMember"));
        rule.setTypedVariables(WorkspaceSide.TARGET, Map.of("mp", "Male"));
        rule.setCorrInvariants(List.of(new TggRuleInfo.CorrInvariant("F2MP", "self.mp.name := self.member.name")));
        rule.setOutputCorrPatterns(List.of(new TggRuleInfo.CorrPattern("member", "mp", "source", "target", "corr", "F2MP")));
        rule.setAttributeMappings(List.of(new TggRuleInfo.AttributeMapping(WorkspaceSide.TARGET, "mp", "name", "self.member.name")));

        TggWorkspaceDefinition definition = buildDefinition(List.of(rule));
        TggWorkspaceContext context = createMockContext(definition);

        // Source snapshot after modification
        FullObjectSnapshot sourceSnapshot = new FullObjectSnapshot();
        sourceSnapshot.objects.put("bart", makeObject("bart", "FamilyMember", Map.of("name", "Hugo")));

        // Target snapshot with existing linked Male
        FullObjectSnapshot targetSnapshot = new FullObjectSnapshot();
        targetSnapshot.objects.put("male_bart", makeObject("male_bart", "Male", Map.of("name", "Bart")));

        // Corr snapshot linking bart → male_bart
        FullObjectSnapshot corrSnapshot = new FullObjectSnapshot();
        ObjectState corrObj = makeObject("corr1", "F2MP", Map.of());
        corrObj.objectReferences = new LinkedHashMap<>();
        corrObj.objectReferences.put("source", List.of("bart"));
        corrObj.objectReferences.put("target", List.of("male_bart"));
        corrSnapshot.objects.put("corr1", corrObj);

        ModelDelta delta = new ModelDelta(WorkspaceSide.SOURCE, List.of(),
                List.of(ObjectChange.modified("bart", "FamilyMember",
                        Map.of("name", "Bart"), Map.of("name", "Hugo"))),
                List.of(), List.of(), List.of(), 0L, 1L);

        TransformationReport report = new TransformationReport(
                TransformationDirection.FORWARD, TransformationMode.PREVIEW);
        IncrementalSyncEngine engine = new IncrementalSyncEngine(report);
        ImpactAnalysisResult impact = new ImpactAnalysisResult(
                TransformationDirection.FORWARD,
                WorkspaceSide.SOURCE,
                Set.of("bart"),
                Set.of("male_bart"),
                Set.of("corr1"),
                Set.of("MemberToMale"),
                Set.of(),
                Map.of("MemberToMale", Set.of("name")),
                Map.of("MemberToMale", Set.of("name")),
                new RuleDependencyGraph(
                        Map.of("MemberToMale", Set.of("name")),
                        Map.of("MemberToMale", Set.of("name")),
                        Map.of("MemberToMale", Set.of()),
                        Map.of("MemberToMale", Set.of()),
                        Map.of("MemberToMale", Map.of("name", Set.of("name"))),
                        Map.of("MemberToMale", Map.of("name", Set.of("name")))),
                List.of(),
                false,
                false);

        WorkspaceMutationBatch batch = engine.syncForward(context, delta, sourceSnapshot,
                targetSnapshot, corrSnapshot, impact);

        assertFalse(batch.getUpsertObjects().isEmpty(), "Should update target object");
        assertFalse(report.getUpdatedObjects().isEmpty(), "Should report updated target attribute");
    }

    @Test
    void testSyncForward_DeletedSourceObject_ReportsForDeletion() {
        TggRuleInfo rule = new TggRuleInfo("MemberToMale");
        rule.setTypedVariables(WorkspaceSide.SOURCE, Map.of("member", "FamilyMember"));
        rule.setTypedVariables(WorkspaceSide.TARGET, Map.of("mp", "Male"));

        TggWorkspaceDefinition definition = buildDefinition(List.of(rule));
        TggWorkspaceContext context = createMockContext(definition);

        // Corr snapshot linking bart → male_bart
        FullObjectSnapshot corrSnapshot = new FullObjectSnapshot();
        ObjectState corrObj = makeObject("corr1", "F2MP", Map.of());
        corrObj.objectReferences = new LinkedHashMap<>();
        corrObj.objectReferences.put("source", List.of("bart"));
        corrObj.objectReferences.put("target", List.of("male_bart"));
        corrSnapshot.objects.put("corr1", corrObj);

        ModelDelta delta = new ModelDelta(WorkspaceSide.SOURCE, List.of(), List.of(),
                List.of(ObjectChange.deleted("bart", "FamilyMember", Map.of("name", "Bart"))),
                List.of(), List.of(), 0L, 1L);

        TransformationReport report = new TransformationReport(
                TransformationDirection.FORWARD, TransformationMode.PREVIEW);
        IncrementalSyncEngine engine = new IncrementalSyncEngine(report);

        engine.syncForward(context, delta, new FullObjectSnapshot(),
                new FullObjectSnapshot(), corrSnapshot);

        // Should report deletion propagation
        assertTrue(report.getInfos().stream()
                        .anyMatch(m -> m.contains("DELETE propagation")),
                "Should report delete propagation for deleted source");
    }

    @Test
    void testSyncForward_ModifiedSourceObjectWithoutRuleMetadata_RequiresManualTransform() {
        TggRuleInfo rule = new TggRuleInfo("MemberToMale");
        rule.setTypedVariables(WorkspaceSide.SOURCE, Map.of("member", "FamilyMember"));
        rule.setTypedVariables(WorkspaceSide.TARGET, Map.of("mp", "Male"));

        TggWorkspaceDefinition definition = buildDefinition(List.of(rule));
        TggWorkspaceContext context = createMockContext(definition);

        FullObjectSnapshot sourceSnapshot = new FullObjectSnapshot();
        sourceSnapshot.objects.put("bart", makeObject("bart", "FamilyMember", Map.of("name", "Hugo")));

        // Target also changed independently
        FullObjectSnapshot targetSnapshot = new FullObjectSnapshot();
        targetSnapshot.objects.put("male_bart",
                makeObject("male_bart", "Male", Map.of("name", "El Barto")));

        // Corr linking source → target
        FullObjectSnapshot corrSnapshot = new FullObjectSnapshot();
        ObjectState corrObj = makeObject("corr1", "F2MP", Map.of());
        corrObj.objectReferences = new LinkedHashMap<>();
        corrObj.objectReferences.put("source", List.of("bart"));
        corrObj.objectReferences.put("target", List.of("male_bart"));
        corrSnapshot.objects.put("corr1", corrObj);

        // Source "name" changed from "Bart" to "Hugo"
        ModelDelta delta = new ModelDelta(WorkspaceSide.SOURCE, List.of(),
                List.of(ObjectChange.modified("bart", "FamilyMember",
                        Map.of("name", "Bart"), Map.of("name", "Hugo"))),
                List.of(), List.of(), List.of(), 0L, 1L);

        TransformationReport report = new TransformationReport(
                TransformationDirection.FORWARD, TransformationMode.PREVIEW);
        IncrementalSyncEngine engine = new IncrementalSyncEngine(report);

        engine.syncForward(context, delta, sourceSnapshot, targetSnapshot, corrSnapshot);

        assertTrue(engine.requiresManualTransform(), "Missing rule metadata should require manual transform");
        assertTrue(engine.getWarnings().stream().anyMatch(message -> message.contains("Cannot resolve rule metadata")
                || message.contains("not safely mappable")));
    }

    @Test
    void testSyncBackward_AddedTargetObject_ProducesSource() {
        TggRuleInfo rule = new TggRuleInfo("MemberToMale");
        rule.setTypedVariables(WorkspaceSide.SOURCE, Map.of("member", "FamilyMember"));
        rule.setTypedVariables(WorkspaceSide.TARGET, Map.of("mp", "Male"));

        TggWorkspaceDefinition definition = buildDefinition(List.of(rule));
        TggWorkspaceContext context = createMockContext(definition);

        FullObjectSnapshot targetSnapshot = new FullObjectSnapshot();
        targetSnapshot.objects.put("newMale",
                makeObject("newMale", "Male", Map.of("name", "Simpson, Maggie")));

        ModelDelta delta = new ModelDelta(WorkspaceSide.TARGET,
                List.of(ObjectChange.added("newMale", "Male",
                        Map.of("name", "Simpson, Maggie"))),
                List.of(), List.of(), List.of(), List.of(), 0L, 1L);

        TransformationReport report = new TransformationReport(
                TransformationDirection.BACKWARD, TransformationMode.PREVIEW);
        IncrementalSyncEngine engine = new IncrementalSyncEngine(report);

        WorkspaceMutationBatch batch = engine.syncBackward(context, delta, new FullObjectSnapshot(),
                targetSnapshot, new FullObjectSnapshot());

        assertFalse(batch.getUpsertObjects().isEmpty(), "Should produce source object from added target");
        assertTrue(batch.getUpsertObjects().stream().anyMatch(o -> "FamilyMember".equals(o.getClassName())),
                "Should create FamilyMember from Male");
    }

    @Test
    void testSyncForward_EmptyDelta_NoChanges() {
        TggRuleInfo rule = new TggRuleInfo("Rule");
        rule.setTypedVariables(WorkspaceSide.SOURCE, Map.of("a", "TypeA"));
        rule.setTypedVariables(WorkspaceSide.TARGET, Map.of("b", "TypeB"));

        TggWorkspaceDefinition definition = buildDefinition(List.of(rule));
        TggWorkspaceContext context = createMockContext(definition);

        ModelDelta emptyDelta = ModelDelta.empty(WorkspaceSide.SOURCE);

        TransformationReport report = new TransformationReport(
                TransformationDirection.FORWARD, TransformationMode.PREVIEW);
        IncrementalSyncEngine engine = new IncrementalSyncEngine(report);

        WorkspaceMutationBatch batch = engine.syncForward(context, emptyDelta,
                new FullObjectSnapshot(), new FullObjectSnapshot(), new FullObjectSnapshot());

        assertTrue(batch.isEmpty(), "Empty delta should produce no changes");
        assertTrue(engine.getConflicts().isEmpty());
    }

    @Test
    void testSyncForward_DeletedSourceObject_SchedulesTargetAndCorrDeletion() {
        TggRuleInfo rule = new TggRuleInfo("MemberToMale");
        rule.setTypedVariables(WorkspaceSide.SOURCE, Map.of("member", "FamilyMember"));
        rule.setTypedVariables(WorkspaceSide.TARGET, Map.of("mp", "Male"));
        rule.setOutputCorrPatterns(List.of(new TggRuleInfo.CorrPattern("member", "mp", "source", "target", "corr", "F2MP")));

        TggWorkspaceDefinition definition = buildDefinition(List.of(rule));
        TggWorkspaceContext context = createMockContext(definition);

        FullObjectSnapshot corrSnapshot = new FullObjectSnapshot();
        ObjectState corrObj = makeObject("corr1", "F2MP", Map.of());
        corrObj.objectReferences = new LinkedHashMap<>();
        corrObj.objectReferences.put("source", List.of("bart"));
        corrObj.objectReferences.put("target", List.of("male_bart"));
        corrSnapshot.objects.put("corr1", corrObj);

        ModelDelta delta = new ModelDelta(WorkspaceSide.SOURCE, List.of(), List.of(),
                List.of(ObjectChange.deleted("bart", "FamilyMember", Map.of("name", "Bart"))),
                List.of(), List.of(), 0L, 1L);

        WorkspaceMutationBatch batch = new IncrementalSyncEngine(
                new TransformationReport(TransformationDirection.FORWARD, TransformationMode.PREVIEW))
                .syncForward(context, delta, new FullObjectSnapshot(), new FullObjectSnapshot(), corrSnapshot);

        assertTrue(batch.getDeleteObjectIds().contains("male_bart"));
        assertTrue(batch.getDeleteObjectIds().contains("corr1"));
    }

    @Test
    void testSyncForward_AddedSourceLink_ProducesTargetLink() {
        TggRuleInfo rule = new TggRuleInfo("FamilyToRegister");
        rule.setTypedVariables(WorkspaceSide.SOURCE, Map.of("family", "Family", "register", "FamilyRegister"));
        rule.setTypedVariables(WorkspaceSide.TARGET, Map.of("person", "Male", "targetRegister", "PersonRegister"));
        rule.setAssociationPatterns(WorkspaceSide.SOURCE, List.of(new TggRuleInfo.AssociationPattern("family", "register", "familyRegister")));
        rule.setAssociationPatterns(WorkspaceSide.TARGET, List.of(new TggRuleInfo.AssociationPattern("person", "targetRegister", "personRegister")));
        rule.setOutputCorrPatterns(List.of(new TggRuleInfo.CorrPattern("family", "person", "source", "target", "corr", "F2MP")));

        TggWorkspaceDefinition definition = buildDefinition(List.of(rule));
        TggWorkspaceContext context = createMockContext(definition);

        FullObjectSnapshot corrSnapshot = new FullObjectSnapshot();
        ObjectState corrObj = makeObject("corr1", "F2MP", Map.of());
        corrObj.objectReferences = new LinkedHashMap<>();
        corrObj.objectReferences.put("source", List.of("family1"));
        corrObj.objectReferences.put("target", List.of("male_family1"));
        corrSnapshot.objects.put("corr1", corrObj);
        ObjectState corrObj2 = makeObject("corr2", "F2MP", Map.of());
        corrObj2.objectReferences = new LinkedHashMap<>();
        corrObj2.objectReferences.put("source", List.of("register1"));
        corrObj2.objectReferences.put("target", List.of("person_register1"));
        corrSnapshot.objects.put("corr2", corrObj2);

        ModelDelta delta = new ModelDelta(WorkspaceSide.SOURCE, List.of(), List.of(), List.of(),
                List.of(LinkChange.of("familyRegister", List.of("family1", "register1"))), List.of(), 0L, 1L);

        WorkspaceMutationBatch batch = new IncrementalSyncEngine(
                new TransformationReport(TransformationDirection.FORWARD, TransformationMode.PREVIEW))
                .syncForward(context, delta, new FullObjectSnapshot(), new FullObjectSnapshot(), corrSnapshot);

        assertFalse(batch.getUpsertLinks().isEmpty());
        assertEquals("personRegister", batch.getUpsertLinks().get(0).getAssociationName());
    }

    @Test
    void testSyncForward_ModifiedSourceObject_ReconstructsMultiVariableBindingFromAssociation() {
        TggRuleInfo rule = new TggRuleInfo("FamilyToMale");
        rule.setTypedVariables(WorkspaceSide.SOURCE, Map.of("family", "Family", "member", "FamilyMember"));
        rule.setTypedVariables(WorkspaceSide.TARGET, Map.of("male", "Male"));
        rule.setAssociationPatterns(WorkspaceSide.SOURCE, List.of(new TggRuleInfo.AssociationPattern("family", "member", "familyMember")));
        rule.setCorrInvariants(List.of(new TggRuleInfo.CorrInvariant("F2MP", "self.male.name := self.member.name + ', ' + self.family.name")));
        rule.setAttributeMappings(List.of(new TggRuleInfo.AttributeMapping(WorkspaceSide.TARGET, "male", "name", "self.member.name + ', ' + self.family.name")));
        rule.setOutputCorrPatterns(List.of(new TggRuleInfo.CorrPattern("member", "male", "source", "target", "corr", "F2MP")));

        TggWorkspaceDefinition definition = buildDefinition(List.of(rule));
        TggWorkspaceContext context = createMockContext(definition);

        FullObjectSnapshot sourceSnapshot = new FullObjectSnapshot();
        sourceSnapshot.objects.put("family1", makeObject("family1", "Family", Map.of("name", "Simpson")));
        sourceSnapshot.objects.put("bart", makeObject("bart", "FamilyMember", Map.of("name", "Hugo")));
        sourceSnapshot.links.put("familyMember_family1_bart", link("familyMember", "family1", "bart"));

        FullObjectSnapshot targetSnapshot = new FullObjectSnapshot();
        targetSnapshot.objects.put("male_bart", makeObject("male_bart", "Male", Map.of("name", "Bart, Simpson")));

        FullObjectSnapshot corrSnapshot = new FullObjectSnapshot();
        ObjectState corrObj = makeObject("corr1", "F2MP", Map.of());
        corrObj.objectReferences = new LinkedHashMap<>();
        corrObj.objectReferences.put("source", List.of("bart"));
        corrObj.objectReferences.put("target", List.of("male_bart"));
        corrSnapshot.objects.put("corr1", corrObj);

        ModelDelta delta = new ModelDelta(WorkspaceSide.SOURCE, List.of(),
                List.of(ObjectChange.modified("bart", "FamilyMember", Map.of("name", "Bart"), Map.of("name", "Hugo"))),
                List.of(), List.of(), List.of(), 0L, 1L);

        ImpactAnalysisResult impact = new ImpactAnalysisResult(
                TransformationDirection.FORWARD,
                WorkspaceSide.SOURCE,
                Set.of("bart", "family1"),
                Set.of("male_bart"),
                Set.of("corr1"),
                Set.of("FamilyToMale"),
                Set.of(),
                Map.of("FamilyToMale", Set.of("name")),
                Map.of("FamilyToMale", Set.of("name")),
                new RuleDependencyGraph(
                        Map.of("FamilyToMale", Set.of("name")),
                        Map.of("FamilyToMale", Set.of("name")),
                        Map.of("FamilyToMale", Set.of("familyMember")),
                        Map.of("FamilyToMale", Set.of()),
                        Map.of("FamilyToMale", Map.of("name", Set.of("name"))),
                        Map.of("FamilyToMale", Map.of("name", Set.of("name")))),
                List.of(),
                false,
                false);

        WorkspaceMutationBatch batch = new IncrementalSyncEngine(
                new TransformationReport(TransformationDirection.FORWARD, TransformationMode.PREVIEW))
                .syncForward(context, delta, sourceSnapshot, targetSnapshot, corrSnapshot, impact);

        assertFalse(batch.getUpsertObjects().isEmpty());
        assertEquals("'Hugo, Simpson'", batch.getUpsertObjects().get(0).getAttributes().get("name"));
    }

    @Test
    void testSyncForward_ModifiedSourceObject_UsesCorrTraceBindingBeforeGraphReconstruction() {
        TggRuleInfo rule = new TggRuleInfo("FamilyToMale");
        rule.setTypedVariables(WorkspaceSide.SOURCE, Map.of("family", "Family", "member", "FamilyMember"));
        rule.setTypedVariables(WorkspaceSide.TARGET, Map.of("male", "Male"));
        rule.setAssociationPatterns(WorkspaceSide.SOURCE, List.of(new TggRuleInfo.AssociationPattern("family", "member", "familyMember")));
        rule.setCorrInvariants(List.of(new TggRuleInfo.CorrInvariant("F2MP", "self.male.name := self.member.name + ', ' + self.family.name")));
        rule.setAttributeMappings(List.of(new TggRuleInfo.AttributeMapping(WorkspaceSide.TARGET, "male", "name", "self.member.name + ', ' + self.family.name")));
        rule.setOutputCorrPatterns(List.of(new TggRuleInfo.CorrPattern("member", "male", "source", "target", "corr", "F2MP")));

        TggWorkspaceDefinition definition = buildDefinition(List.of(rule));
        TggWorkspaceContext context = createMockContext(definition);

        FullObjectSnapshot sourceSnapshot = new FullObjectSnapshot();
        sourceSnapshot.objects.put("family1", makeObject("family1", "Family", Map.of("name", "Simpson")));
        sourceSnapshot.objects.put("bart", makeObject("bart", "FamilyMember", Map.of("name", "Hugo")));

        FullObjectSnapshot targetSnapshot = new FullObjectSnapshot();
        targetSnapshot.objects.put("male_bart", makeObject("male_bart", "Male", Map.of("name", "Bart, Simpson")));

        FullObjectSnapshot corrSnapshot = new FullObjectSnapshot();
        corrSnapshot.objects.put("corr1", makeObject("corr1", "F2MP", Map.of()));
        corrSnapshot.links.put("trace_src_member", traceLink("corr1", "bart", "FamilyToMale", WorkspaceSide.SOURCE, "member"));
        corrSnapshot.links.put("trace_src_family", traceLink("corr1", "family1", "FamilyToMale", WorkspaceSide.SOURCE, "family"));
        corrSnapshot.links.put("trace_tgt", traceLink("corr1", "male_bart", "FamilyToMale", WorkspaceSide.TARGET, "male"));

        ModelDelta delta = new ModelDelta(WorkspaceSide.SOURCE, List.of(),
                List.of(ObjectChange.modified("bart", "FamilyMember", Map.of("name", "Bart"), Map.of("name", "Hugo"))),
                List.of(), List.of(), List.of(), 0L, 1L);

        ImpactAnalysisResult impact = new ImpactAnalysisResult(
                TransformationDirection.FORWARD,
                WorkspaceSide.SOURCE,
                Set.of("bart", "family1"),
                Set.of("male_bart"),
                Set.of("corr1"),
                Set.of("FamilyToMale"),
                Set.of(),
                Map.of("FamilyToMale", Set.of("name")),
                Map.of("FamilyToMale", Set.of("name")),
                new RuleDependencyGraph(
                        Map.of("FamilyToMale", Set.of("name")),
                        Map.of("FamilyToMale", Set.of("name")),
                        Map.of("FamilyToMale", Set.of("familyMember")),
                        Map.of("FamilyToMale", Set.of()),
                        Map.of("FamilyToMale", Map.of("name", Set.of("name"))),
                        Map.of("FamilyToMale", Map.of("name", Set.of("name")))),
                List.of(),
                false,
                false);

        WorkspaceMutationBatch batch = new IncrementalSyncEngine(
                new TransformationReport(TransformationDirection.FORWARD, TransformationMode.PREVIEW))
                .syncForward(context, delta, sourceSnapshot, targetSnapshot, corrSnapshot, impact);

        assertFalse(batch.getUpsertObjects().isEmpty());
        assertEquals("'Hugo, Simpson'", batch.getUpsertObjects().get(0).getAttributes().get("name"));
    }

    // --- Helpers ---

    private TggWorkspaceDefinition buildDefinition(List<TggRuleInfo> rules) {
        return new TggWorkspaceDefinition(
                "Test", null, null, null, rules,
                Set.of("FamilyRegister", "Family", "FamilyMember"),
                Set.of("F2MP"),
                Set.of("PersonRegister", "Male", "Female"));
    }

    private ObjectState makeObject(String name, String className, Map<String, Object> attributes) {
        ObjectState state = new ObjectState();
        state.name = name;
        state.className = className;
        state.primitiveValues = new LinkedHashMap<>(attributes);
        state.objectReferences = new LinkedHashMap<>();
        return state;
    }

    private org.uet.dse.neo4j.model.LinkState link(String assocName, String left, String right) {
        org.uet.dse.neo4j.model.LinkState state = new org.uet.dse.neo4j.model.LinkState();
        state.assocName = assocName;
        state.participants = List.of(left, right);
        return state;
    }

    private LinkState traceLink(String corrId, String objectId, String ruleName, WorkspaceSide side, String variableName) {
        LinkState state = new LinkState();
        state.assocName = CorrRuntimeTraceHelper.bindingAssociationName(ruleName, side, variableName);
        state.participants = List.of(corrId, objectId);
        return state;
    }

    private TggWorkspaceContext createMockContext(TggWorkspaceDefinition definition) {
        TggWorkspaceContext context = new TggWorkspaceContext(null, null);
        context.setWorkspaceDefinition(definition);
        context.setTggFile(new File("test.tgg"));
        return context;
    }
}
