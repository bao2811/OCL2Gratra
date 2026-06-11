package org.uet.dse.neo4jtgg.engine;

import org.junit.jupiter.api.Test;
import org.uet.dse.neo4j.model.FullObjectSnapshot;
import org.uet.dse.neo4j.model.LinkState;
import org.uet.dse.neo4j.model.ObjectState;
import org.uet.dse.neo4jtgg.model.ImportBatch;
import org.uet.dse.neo4jtgg.model.TggRuleInfo;
import org.uet.dse.neo4jtgg.model.TggWorkspaceContext;
import org.uet.dse.neo4jtgg.model.TggWorkspaceDefinition;
import org.uet.dse.neo4jtgg.model.WorkspaceSide;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ForwardRuleApplicationEngineTest {

    @Test
    void testForwardEngine_ProducesTargetObjects() {
        // Setup: build a simple Families2Persons rule set
        TggRuleInfo rootRule = new TggRuleInfo("Families2Persons");
        rootRule.setTypedVariables(WorkspaceSide.SOURCE, Map.of("fr", "FamilyRegister"));
        rootRule.setTypedVariables(WorkspaceSide.TARGET, Map.of("pr", "PersonRegister"));
        rootRule.setOutputCorrPatterns(List.of(
                new TggRuleInfo.CorrPattern("fr", "pr", "fr", "pr", "fr2pr", "FR2PR")));

        TggRuleInfo fatherRule = new TggRuleInfo("FatherToMale");
        fatherRule.setTypedVariables(WorkspaceSide.SOURCE, Map.of(
                "fm", "Family", "father", "FamilyMember"));
        fatherRule.setAssociationPatterns(WorkspaceSide.SOURCE, List.of(
                new TggRuleInfo.AssociationPattern("fm", "father", "Father")));
        fatherRule.setPredicates(WorkspaceSide.SOURCE, List.of("father.name <> Undefined"));
        fatherRule.setTypedVariables(WorkspaceSide.TARGET, Map.of("mp", "Male"));
        fatherRule.setOutputCorrPatterns(List.of(
                new TggRuleInfo.CorrPattern("father", "mp", "father", "mp", "f2mp", "F2MP")));

        TggWorkspaceDefinition definition = new TggWorkspaceDefinition(
                "Families2Persons", null, null, null,
                List.of(rootRule, fatherRule),
                Set.of("FamilyRegister", "Family", "FamilyMember"),
                Set.of("FR2PR", "F2MP"),
                Set.of("PersonRegister", "Male", "Female"));

        // Build source snapshot
        FullObjectSnapshot sourceSnapshot = new FullObjectSnapshot();
        sourceSnapshot.objects.put("familyRegister1",
                makeObject("familyRegister1", "FamilyRegister", Map.of()));
        sourceSnapshot.objects.put("simpson",
                makeObject("simpson", "Family", Map.of("name", "Simpson")));
        sourceSnapshot.objects.put("homer",
                makeObject("homer", "FamilyMember", Map.of("name", "Homer")));

        LinkState fatherLink = new LinkState();
        fatherLink.assocName = "Father";
        fatherLink.participants = List.of("simpson", "homer");
        sourceSnapshot.links.put(fatherLink.getIdentity(), fatherLink);

        FullObjectSnapshot targetSnapshot = new FullObjectSnapshot();
        FullObjectSnapshot corrSnapshot = new FullObjectSnapshot();

        // Execute
        TransformationReport report = new TransformationReport(TransformationDirection.FORWARD, TransformationMode.PREVIEW);
        TggWorkspaceContext context = createMockContext(definition);

        ForwardRuleApplicationEngine engine = new ForwardRuleApplicationEngine(report);
        ForwardTransformationResult result = engine.executeForward(context, sourceSnapshot, targetSnapshot, corrSnapshot);
        ImportBatch batch = result.targetBatch();

        // Verify
        assertNotNull(batch);
        assertFalse(batch.getObjects().isEmpty(), "Should produce target objects");

        // Should have PersonRegister (reused or created) and Male
        assertTrue(batch.getObjects().stream().anyMatch(o -> "PersonRegister".equals(o.getClassName())),
                "Should create PersonRegister");
        assertTrue(batch.getObjects().stream().anyMatch(o ->
                        "PersonRegister".equals(o.getClassName()) && "personRegister1".equals(o.getObjectName())),
                "Should normalize the target singleton register name to personRegister1");
        assertTrue(batch.getObjects().stream().anyMatch(o -> "Male".equals(o.getClassName())),
                "Should create Male from father");

        // Check rule applications
        assertFalse(engine.getAppliedRules().isEmpty(), "Should have applied rules");
        assertTrue(engine.getAppliedRules().stream()
                        .anyMatch(r -> "FatherToMale".equals(r.ruleName())),
                "Should have applied FatherToMale rule");

        // Check report
        assertFalse(report.getCreatedObjects().isEmpty(), "Report should list created objects");
        assertFalse(result.corrCreations().isEmpty(), "Should produce correspondence creation records");
        assertTrue(result.corrCreations().stream()
                        .anyMatch(corr -> "FatherToMale".equals(corr.record().appliedRuleName())),
                "Should keep applied rule name on corr records");
    }

    @Test
    void testForwardEngine_ReusesSingletonTargetCreatedEarlierInSameRun() {
        TggRuleInfo registerRule = new TggRuleInfo("Register2Register");
        registerRule.setTypedVariables(WorkspaceSide.SOURCE, Map.of("fr", "FamilyRegister"));
        registerRule.setTypedVariables(WorkspaceSide.TARGET, Map.of("pr", "PersonRegister"));
        registerRule.setOutputCorrPatterns(List.of(
                new TggRuleInfo.CorrPattern("fr", "pr", "fr", "pr", "fr2pr", "FR2PR")));

        TggRuleInfo fatherRule = new TggRuleInfo("Father2Male");
        fatherRule.setTypedVariables(WorkspaceSide.SOURCE, Map.of(
                "fr", "FamilyRegister",
                "fm", "Family",
                "father", "FamilyMember"));
        fatherRule.setAssociationPatterns(WorkspaceSide.SOURCE, List.of(
                new TggRuleInfo.AssociationPattern("fr", "fm", "FamilyRegistration"),
                new TggRuleInfo.AssociationPattern("fm", "father", "Father")));
        fatherRule.setTypedVariables(WorkspaceSide.TARGET, Map.of(
                "pr", "PersonRegister",
                "mp", "Male"));
        fatherRule.setAssociationPatterns(WorkspaceSide.TARGET, List.of(
                new TggRuleInfo.AssociationPattern("pr", "mp", "PersonRegistration")));
        fatherRule.setOutputCorrPatterns(List.of(
                new TggRuleInfo.CorrPattern("father", "mp", "father", "mp", "f2mp", "F2MP")));

        TggWorkspaceDefinition definition = new TggWorkspaceDefinition(
                "Families2Persons", null, null, null,
                List.of(registerRule, fatherRule),
                Set.of("FamilyRegister", "Family", "FamilyMember"),
                Set.of("FR2PR", "F2MP"),
                Set.of("PersonRegister", "Male"));

        FullObjectSnapshot sourceSnapshot = new FullObjectSnapshot();
        sourceSnapshot.objects.put("familyRegister1", makeObject("familyRegister1", "FamilyRegister", Map.of()));
        sourceSnapshot.objects.put("simpson", makeObject("simpson", "Family", Map.of("name", "Simpson")));
        sourceSnapshot.objects.put("homer", makeObject("homer", "FamilyMember", Map.of("name", "Homer")));

        LinkState registration = new LinkState();
        registration.assocName = "FamilyRegistration";
        registration.participants = List.of("familyRegister1", "simpson");
        sourceSnapshot.links.put(registration.getIdentity(), registration);

        LinkState fatherLink = new LinkState();
        fatherLink.assocName = "Father";
        fatherLink.participants = List.of("simpson", "homer");
        sourceSnapshot.links.put(fatherLink.getIdentity(), fatherLink);

        TransformationReport report = new TransformationReport(TransformationDirection.FORWARD, TransformationMode.PREVIEW);
        TggWorkspaceContext context = createMockContext(definition);

        ForwardRuleApplicationEngine engine = new ForwardRuleApplicationEngine(report);
        ForwardTransformationResult result = engine.executeForward(context, sourceSnapshot, new FullObjectSnapshot(), new FullObjectSnapshot());

        long registerCount = result.targetBatch().getObjects().stream()
                .filter(object -> "PersonRegister".equals(object.getClassName()))
                .count();
        assertEquals(1L, registerCount, "Forward run should reuse the singleton PersonRegister created earlier in the same run");
    }

    @Test
    void testForwardEngine_SkipsDuplicateCorr() {
        TggRuleInfo rule = new TggRuleInfo("TestRule");
        rule.setTypedVariables(WorkspaceSide.SOURCE, Map.of("a", "TypeA"));
        rule.setTypedVariables(WorkspaceSide.TARGET, Map.of("b", "TypeB"));
        rule.setOutputCorrPatterns(List.of(
                new TggRuleInfo.CorrPattern("a", "b", "a", "b", "corr", "CorrAB")));

        TggWorkspaceDefinition definition = new TggWorkspaceDefinition(
                "Test", null, null, null,
                List.of(rule), Set.of("TypeA"), Set.of("CorrAB"), Set.of("TypeB"));

        FullObjectSnapshot source = new FullObjectSnapshot();
        source.objects.put("obj1", makeObject("obj1", "TypeA", Map.of()));

        // Pre-existing corr
        FullObjectSnapshot corr = new FullObjectSnapshot();
        ObjectState corrObj = makeObject("corr_obj1_typeb_obj1", "CorrAB", Map.of());
        corr.objects.put("corr_obj1_typeb_obj1", corrObj);
        corr.links.put(trace("corr_obj1_typeb_obj1", "obj1", "TestRule", WorkspaceSide.SOURCE, "a").getIdentity(),
                trace("corr_obj1_typeb_obj1", "obj1", "TestRule", WorkspaceSide.SOURCE, "a"));
        corr.links.put(trace("corr_obj1_typeb_obj1", "typeb_obj1", "TestRule", WorkspaceSide.TARGET, "b").getIdentity(),
                trace("corr_obj1_typeb_obj1", "typeb_obj1", "TestRule", WorkspaceSide.TARGET, "b"));

        TransformationReport report = new TransformationReport(TransformationDirection.FORWARD, TransformationMode.PREVIEW);
        TggWorkspaceContext context = createMockContext(definition);

        ForwardRuleApplicationEngine engine = new ForwardRuleApplicationEngine(report);
        ForwardTransformationResult result = engine.executeForward(context, source, new FullObjectSnapshot(), corr);
        ImportBatch batch = result.targetBatch();

        assertNotNull(batch);
        assertTrue(batch.getObjects().isEmpty(), "Existing corr trace should prevent duplicate target creation");
        assertTrue(result.corrCreations().isEmpty(), "Existing corr trace should prevent duplicate corr creation");
    }

    @Test
    void testForwardEngine_EmptySource_NoOutput() {
        TggRuleInfo rule = new TggRuleInfo("EmptyRule");
        rule.setTypedVariables(WorkspaceSide.SOURCE, Map.of("x", "NonExistent"));
        rule.setTypedVariables(WorkspaceSide.TARGET, Map.of("y", "TypeY"));

        TggWorkspaceDefinition definition = new TggWorkspaceDefinition(
                "Test", null, null, null,
                List.of(rule), Set.of(), Set.of(), Set.of());

        TransformationReport report = new TransformationReport(TransformationDirection.FORWARD, TransformationMode.PREVIEW);
        TggWorkspaceContext context = createMockContext(definition);

        ForwardRuleApplicationEngine engine = new ForwardRuleApplicationEngine(report);
        ImportBatch batch = engine.applyForward(context, new FullObjectSnapshot(),
                new FullObjectSnapshot(), new FullObjectSnapshot());

        assertTrue(batch.getObjects().isEmpty());
        assertTrue(engine.getAppliedRules().isEmpty());
    }

    @Test
    void testSnapshotIndex_FindByClass() {
        FullObjectSnapshot snapshot = new FullObjectSnapshot();
        snapshot.objects.put("a1", makeObject("a1", "ClassA", Map.of()));
        snapshot.objects.put("a2", makeObject("a2", "ClassA", Map.of()));
        snapshot.objects.put("b1", makeObject("b1", "ClassB", Map.of()));

        SnapshotIndex index = new SnapshotIndex(snapshot);

        assertEquals(2, index.findByClass("ClassA").size());
        assertEquals(1, index.findByClass("ClassB").size());
        assertEquals(0, index.findByClass("ClassC").size());
    }

    @Test
    void testSnapshotIndex_HasAssociation() {
        FullObjectSnapshot snapshot = new FullObjectSnapshot();
        snapshot.objects.put("a1", makeObject("a1", "ClassA", Map.of()));
        snapshot.objects.put("b1", makeObject("b1", "ClassB", Map.of()));
        LinkState link = new LinkState();
        link.assocName = "TestAssoc";
        link.participants = List.of("a1", "b1");
        snapshot.links.put(link.getIdentity(), link);

        SnapshotIndex index = new SnapshotIndex(snapshot);

        assertTrue(index.hasAssociation("TestAssoc", "a1", "b1"));
        assertFalse(index.hasAssociation("TestAssoc", "b1", "a1"));
        assertFalse(index.hasAssociation("Other", "a1", "b1"));
    }

    private ObjectState makeObject(String name, String className, Map<String, Object> attributes) {
        ObjectState state = new ObjectState();
        state.name = name;
        state.className = className;
        state.primitiveValues = new LinkedHashMap<>(attributes);
        state.objectReferences = new LinkedHashMap<>();
        return state;
    }

    private TggWorkspaceContext createMockContext(TggWorkspaceDefinition definition) {
        TggWorkspaceContext context = new TggWorkspaceContext(null, null);
        context.setWorkspaceDefinition(definition);
        context.setTggFile(new java.io.File("test.tgg"));
        return context;
    }

    private LinkState trace(String corrId, String objectId, String ruleName, WorkspaceSide side, String variableName) {
        LinkState state = new LinkState();
        state.assocName = CorrRuntimeTraceHelper.bindingAssociationName(ruleName, side, variableName);
        state.participants = List.of(corrId, objectId);
        return state;
    }
}
