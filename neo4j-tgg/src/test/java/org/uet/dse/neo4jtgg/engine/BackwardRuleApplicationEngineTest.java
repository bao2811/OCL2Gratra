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

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BackwardRuleApplicationEngineTest {

    @Test
    void testBackwardEngine_ProducesSourceObjects() {
        // Rule: PersonRegister ← FamilyRegister
        TggRuleInfo rootRule = new TggRuleInfo("Families2Persons");
        rootRule.setTypedVariables(WorkspaceSide.SOURCE, Map.of("fr", "FamilyRegister"));
        rootRule.setTypedVariables(WorkspaceSide.TARGET, Map.of("pr", "PersonRegister"));
        rootRule.setOutputCorrPatterns(List.of(
                new TggRuleInfo.CorrPattern("fr", "pr", "fr", "pr", "fr2pr", "FR2PR")));

        // Rule: Male → FamilyMember (backward from Male to Father)
        TggRuleInfo fatherRule = new TggRuleInfo("FatherToMale");
        fatherRule.setTypedVariables(WorkspaceSide.TARGET, Map.of("mp", "Male"));
        fatherRule.setTypedVariables(WorkspaceSide.SOURCE, Map.of(
                "fm", "Family", "father", "FamilyMember"));
        fatherRule.setAssociationPatterns(WorkspaceSide.SOURCE, List.of(
                new TggRuleInfo.AssociationPattern("fm", "father", "Father")));
        fatherRule.setOutputCorrPatterns(List.of(
                new TggRuleInfo.CorrPattern("father", "mp", "father", "mp", "f2mp", "F2MP")));

        TggWorkspaceDefinition definition = new TggWorkspaceDefinition(
                "Families2Persons", null, null, null,
                List.of(rootRule, fatherRule),
                Set.of("FamilyRegister", "Family", "FamilyMember"),
                Set.of("FR2PR", "F2MP"),
                Set.of("PersonRegister", "Male", "Female"));

        // Build target snapshot (what we're transforming FROM)
        FullObjectSnapshot targetSnapshot = new FullObjectSnapshot();
        targetSnapshot.objects.put("personRegister1",
                makeObject("personRegister1", "PersonRegister", Map.of()));
        targetSnapshot.objects.put("homer",
                makeObject("homer", "Male", Map.of("name", "Simpson, Homer")));

        FullObjectSnapshot sourceSnapshot = new FullObjectSnapshot();
        FullObjectSnapshot corrSnapshot = new FullObjectSnapshot();

        // Execute backward
        TransformationReport report = new TransformationReport(
                TransformationDirection.BACKWARD, TransformationMode.PREVIEW);
        TggWorkspaceContext context = createMockContext(definition);

        BackwardRuleApplicationEngine engine = new BackwardRuleApplicationEngine(report);
        ImportBatch batch = engine.applyBackward(context, sourceSnapshot, targetSnapshot, corrSnapshot);

        // Verify
        assertNotNull(batch);
        assertFalse(batch.getObjects().isEmpty(), "Should produce source objects");
        assertFalse(engine.getAppliedRules().isEmpty(), "Should have applied rules");

        // All applied rules should have BACKWARD direction
        assertTrue(engine.getAppliedRules().stream()
                .allMatch(r -> r.direction() == TransformationDirection.BACKWARD));

        // Report should contain created objects
        assertFalse(report.getCreatedObjects().isEmpty());
    }

    @Test
    void testBackwardEngine_EmptyTarget_NoOutput() {
        TggRuleInfo rule = new TggRuleInfo("TestRule");
        rule.setTypedVariables(WorkspaceSide.TARGET, Map.of("x", "NonExistent"));
        rule.setTypedVariables(WorkspaceSide.SOURCE, Map.of("y", "TypeY"));

        TggWorkspaceDefinition definition = new TggWorkspaceDefinition(
                "Test", null, null, null,
                List.of(rule), Set.of(), Set.of(), Set.of());

        TransformationReport report = new TransformationReport(
                TransformationDirection.BACKWARD, TransformationMode.PREVIEW);
        TggWorkspaceContext context = createMockContext(definition);

        BackwardRuleApplicationEngine engine = new BackwardRuleApplicationEngine(report);
        ImportBatch batch = engine.applyBackward(context, new FullObjectSnapshot(),
                new FullObjectSnapshot(), new FullObjectSnapshot());

        assertTrue(batch.getObjects().isEmpty());
        assertTrue(engine.getAppliedRules().isEmpty());
    }

    @Test
    void testBackwardEngine_ReusesSingletonSource() {
        TggRuleInfo rule = new TggRuleInfo("Root");
        rule.setTypedVariables(WorkspaceSide.TARGET, Map.of("pr", "PersonRegister"));
        rule.setTypedVariables(WorkspaceSide.SOURCE, Map.of("fr", "FamilyRegister"));
        rule.setOutputCorrPatterns(List.of(
                new TggRuleInfo.CorrPattern("fr", "pr", "fr", "pr", "corr", "Corr")));

        TggWorkspaceDefinition definition = new TggWorkspaceDefinition(
                "Test", null, null, null,
                List.of(rule),
                Set.of("FamilyRegister"), Set.of("Corr"), Set.of("PersonRegister"));

        // Source already has a FamilyRegister
        FullObjectSnapshot sourceSnapshot = new FullObjectSnapshot();
        sourceSnapshot.objects.put("existingFR",
                makeObject("existingFR", "FamilyRegister", Map.of()));

        FullObjectSnapshot targetSnapshot = new FullObjectSnapshot();
        targetSnapshot.objects.put("pr1",
                makeObject("pr1", "PersonRegister", Map.of()));

        TransformationReport report = new TransformationReport(
                TransformationDirection.BACKWARD, TransformationMode.PREVIEW);
        TggWorkspaceContext context = createMockContext(definition);

        BackwardRuleApplicationEngine engine = new BackwardRuleApplicationEngine(report);
        ImportBatch batch = engine.applyBackward(context, sourceSnapshot, targetSnapshot,
                new FullObjectSnapshot());

        // Should not create a new FamilyRegister; should reuse existing
        assertTrue(batch.getObjects().stream()
                        .noneMatch(o -> "FamilyRegister".equals(o.getClassName())),
                "Should reuse existing singleton, not create new one");
    }

    @Test
    void testBackwardEngine_DerivesNameFromSplitExpression() {
        TggRuleInfo rule = new TggRuleInfo("FatherToMale");
        rule.setTypedVariables(WorkspaceSide.TARGET, Map.of("mp", "Male"));
        rule.setTypedVariables(WorkspaceSide.SOURCE, Map.of("father", "FamilyMember"));
        rule.setCorrInvariants(List.of(
                new TggRuleInfo.CorrInvariant("F2MP",
                        "self.mp.name.split(', ')->at(2) = self.father.name")));
        rule.setOutputCorrPatterns(List.of(
                new TggRuleInfo.CorrPattern("father", "mp", "father", "mp", "corr", "F2MP")));

        TggWorkspaceDefinition definition = new TggWorkspaceDefinition(
                "Test", null, null, null,
                List.of(rule),
                Set.of("FamilyMember"), Set.of("F2MP"), Set.of("Male"));

        FullObjectSnapshot targetSnapshot = new FullObjectSnapshot();
        targetSnapshot.objects.put("male1",
                makeObject("male1", "Male", Map.of("name", "Simpson, Homer")));

        TransformationReport report = new TransformationReport(
                TransformationDirection.BACKWARD, TransformationMode.PREVIEW);
        TggWorkspaceContext context = createMockContext(definition);

        BackwardRuleApplicationEngine engine = new BackwardRuleApplicationEngine(report);
        ImportBatch batch = engine.applyBackward(context, new FullObjectSnapshot(),
                targetSnapshot, new FullObjectSnapshot());

        assertFalse(batch.getObjects().isEmpty(), "Should create source FamilyMember");

        // Verify name was derived from split expression
        boolean hasNameAttr = batch.getObjects().stream()
                .anyMatch(o -> o.getAttributes().containsKey("name"));
        assertTrue(hasNameAttr, "Source object should have derived name attribute");
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
        context.setTggFile(new File("test.tgg"));
        return context;
    }
}
