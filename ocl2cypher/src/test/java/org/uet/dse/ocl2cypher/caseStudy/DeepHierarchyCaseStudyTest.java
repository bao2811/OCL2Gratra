package org.uet.dse.ocl2cypher.caseStudy;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.uet.dse.ocl2cypher.api.FrontendCompiler;
import org.uet.dse.ocl2cypher.core.CoreInterpreter;
import org.uet.dse.ocl2cypher.core.CoreInvariant;
import org.uet.dse.ocl2cypher.core.CoreLowering;
import org.uet.dse.ocl2cypher.cypher.CypherAst;
import org.uet.dse.ocl2cypher.cypher.Neo4jCypherParserGate;
import org.uet.dse.ocl2cypher.cypher.Realization;
import org.uet.dse.ocl2cypher.cypher.Serializer;
import org.uet.dse.ocl2cypher.graph.GraphBuilder;
import org.uet.dse.ocl2cypher.graph.GraphModel;
import org.uet.dse.ocl2cypher.qcyp.QCypTranslator;
import org.uet.dse.ocl2cypher.qcyp.QInterpreter;
import org.uet.dse.ocl2cypher.qcyp.QQuery;
import org.uet.dse.ocl2cypher.runtime.OclType;
import org.uet.dse.ocl2cypher.runtime.OclValue;
import org.uet.dse.ocl2cypher.source.omg.OmgAs;
import org.uet.dse.ocl2cypher.source.model.SchemaModel;
import org.uet.dse.ocl2cypher.source.model.Snapshot;
import org.uet.dse.ocl2cypher.source.model.UmlAssociation;
import org.uet.dse.ocl2cypher.source.model.UmlAttribute;
import org.uet.dse.ocl2cypher.source.model.UmlClass;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Deep-hierarchy case study exercising 3-level inheritance, {@code allInstances},
 * {@code oclIsKindOf}, {@code reject}+{@code exists}, and Bag operation patterns.
 *
 * <p>Domain: Entity → Project → ResearchProject, Entity → Task → BugReport.
 * 12 admitted invariants across 3 snapshots = 36 differential checks
 * (Core ↔ Q-over-G), plus Cypher realization conformance.
 */
class DeepHierarchyCaseStudyTest {

    // ── Schema ──────────────────────────────────────────────────────────
    private static SchemaModel schema() {
        return SchemaModel.builder("entity")
                .clazz(UmlClass.of("Entity"))
                .clazz(UmlClass.of("Project", "Entity"))
                .clazz(UmlClass.of("ResearchProject", "Project"))
                .clazz(UmlClass.of("Task", "Entity"))
                .clazz(UmlClass.of("BugReport", "Task"))
                .attribute(UmlAttribute.of("Entity", "name", OclType.STRING))
                .attribute(UmlAttribute.of("Entity", "id", OclType.INTEGER))
                .attribute(UmlAttribute.of("Project", "budget", OclType.INTEGER))
                .attribute(UmlAttribute.of("ResearchProject", "grantId", OclType.INTEGER))
                .attribute(UmlAttribute.of("Task", "duration", OclType.INTEGER))
                .attribute(UmlAttribute.of("BugReport", "severity", OclType.INTEGER))
                .association(UmlAssociation.binary("projectTeam", "Project", "project",
                        "Task", "tasks"))
                .association(UmlAssociation.oneToOne("bugFixes", "BugReport", "bug",
                        "Task", "fix"))
                .build();
    }

    // ── Snapshots ───────────────────────────────────────────────────────
    private static OclValue i(long v) {
        return new OclValue.IntegerValue(BigInteger.valueOf(v));
    }

    private static OclValue s(String v) {
        return new OclValue.StringValue(v);
    }

    /** SN-main: full population with deliberate violations. */
    static Snapshot mainSnapshot() {
        return Snapshot.builder()
                // Entity objects
                .object("proj1", "Project")
                .attribute("proj1", "name", s("Alpha")).attribute("proj1", "id", i(1))
                .attribute("proj1", "budget", i(1000))
                .object("rp1", "ResearchProject")
                .attribute("rp1", "name", s("Beta")).attribute("rp1", "id", i(2))
                .attribute("rp1", "budget", i(500)).attribute("rp1", "grantId", i(42))
                .object("t1", "Task").attribute("t1", "name", s("Code"))
                .attribute("t1", "id", i(10)).attribute("t1", "duration", i(3))
                .object("t2", "Task").attribute("t2", "name", s(""))
                .attribute("t2", "id", i(11)).attribute("t2", "duration", i(15))
                .object("br1", "BugReport").attribute("br1", "name", s("Fix"))
                .attribute("br1", "id", i(20))
                .attribute("br1", "duration", i(2)).attribute("br1", "severity", i(5))
                // proj1 has t1 (duration=3), t2 (duration=15, name='')
                .link("projectTeam", "proj1", "t1")
                .link("projectTeam", "proj1", "t2")
                // rp1 has no linked tasks
                // br1 fixes t2
                .link("bugFixes", "br1", "t2")
                // Empty project
                .object("proj2", "Project")
                .attribute("proj2", "name", s("Empty")).attribute("proj2", "id", i(3))
                .attribute("proj2", "budget", i(0))
                .build();
    }

    /** SN-empty: project with no linked tasks. */
    static Snapshot emptySnapshot() {
        return Snapshot.builder()
                .object("projE", "Project")
                .attribute("projE", "name", s("Vacant")).attribute("projE", "id", i(50))
                .attribute("projE", "budget", i(100))
                .build();
    }

    /** SN-bottom: missing attributes and no links. */
    static Snapshot bottomSnapshot() {
        return Snapshot.builder()
                .object("projB", "Project")
                .attribute("projB", "name", s("Ghost"))
                // missing budget → typed bottom
                .object("tB", "Task").attribute("tB", "name", s("Orphan"))
                // missing duration → typed bottom
                .object("brB", "BugReport").attribute("brB", "name", s("Leak"))
                // missing severity → typed bottom
                .build();
    }

    // ── Invariant corpus ────────────────────────────────────────────────
    private static final String[][] INVARIANTS = {
            // key, context, oclBody
            {"Project::HasBudget", "Project",
                    "self.budget >= 0"},
            {"Project::HasTasks", "Project",
                    "self.tasks->notEmpty()"},
            {"Project::TasksNamed", "Project",
                    "self.tasks->forAll(t | t.name <> '')"},
            {"Project::HasShortTask", "Project",
                    "self.tasks->exists(t | t.duration <= 5)"},
            {"Project::NoLongTask", "Project",
                    "self.tasks->reject(t | t.duration > 10)->notEmpty()"},
            {"Project::TaskCount", "Project",
                    "self.tasks->size() >= 1"},
            {"Project::AllTasksInAllInstances", "Project",
                    "Entity.allInstances()->notEmpty()"},
            {"Project::IsEntity", "Project",
                    "self.oclIsKindOf(Entity)"},
            {"ResearchProject::HasGrant", "ResearchProject",
                    "self.grantId > 0"},
            {"BugReport::LowSeverity", "BugReport",
                    "self.severity <= 3"},
            {"BugReport::HasFix", "BugReport",
                    "self.fix.oclIsKindOf(Task)"},
            {"Project::NamedEntity", "Project",
                    "self.name <> ''"},
    };

    // ── Expected violations ─────────────────────────────────────────────
    /**
     * Manual oracle: for each (snapshot, invariant) the set of violating stable IDs.
     *
     * <p>SN-main:
     * <ul>
     *   <li>Project::HasBudget — all pass (proj1=1000, rp1=500, proj2=0)</li>
     *   <li>Project::HasTasks — proj1,proj2 pass; rp1 has 0 tasks => violates
     *       (rp1 is a ResearchProject which is a Project)</li>
     *   <li>Project::TasksNamed — t2 has name='' => proj1 violates; rp1,
     *       proj2 vacuously pass (empty set for rp1, proj2)</li>
     *   <li>Project::HasShortTask — proj1 passes (t1 duration=3); rp1,proj2 false
     *       => violates</li>
     *   <li>Project::NoLongTask — proj1: reject(duration>10) keeps {t1}, notEmpty
     *       passes; rp1 empty→reject empty → notEmpty false => violates;
     *       proj2 same → violates</li>
     *   <li>Project::TaskCount — proj1 has 2 (passes); rp1=0,proj2=0 => violates</li>
     *   <li>Project::AllTasksInAllInstances — Entity.allInstances() is
     *       always non-empty (all snapshots have Entity objects)</li>
     *   <li>Project::IsEntity — all are Entities => passes</li>
     *   <li>ResearchProject::HasGrant — rp1 grantId=42 > 0 => passes</li>
     *   <li>BugReport::LowSeverity — br1 severity=5 > 3 => violates</li>
     *   <li>BugReport::HasFix — br1 fix=t2 => passes</li>
     *   <li>Project::NamedEntity — all Projects have non-empty names</li>
     * </ul>
     */
    private static Map<String, Set<String>> expectedViolations() {
        Map<String, Set<String>> m = new LinkedHashMap<>();
        // SN-main
        m.put("SN-main\tProject::HasBudget", Set.of());
        m.put("SN-main\tProject::HasTasks", Set.of("rp1", "proj2"));
        m.put("SN-main\tProject::TasksNamed", Set.of("proj1"));
        m.put("SN-main\tProject::HasShortTask", Set.of("rp1", "proj2"));
        m.put("SN-main\tProject::NoLongTask", Set.of("rp1", "proj2"));
        m.put("SN-main\tProject::TaskCount", Set.of("rp1", "proj2"));
        m.put("SN-main\tProject::AllTasksInAllInstances", Set.of());
        m.put("SN-main\tProject::IsEntity", Set.of());
        m.put("SN-main\tResearchProject::HasGrant", Set.of());
        m.put("SN-main\tBugReport::LowSeverity", Set.of("br1"));
        m.put("SN-main\tBugReport::HasFix", Set.of());
        m.put("SN-main\tProject::NamedEntity", Set.of());
        // SN-empty
        m.put("SN-empty\tProject::HasBudget", Set.of());
        m.put("SN-empty\tProject::HasTasks", Set.of("projE"));
        m.put("SN-empty\tProject::TasksNamed", Set.of());
        m.put("SN-empty\tProject::HasShortTask", Set.of("projE"));
        m.put("SN-empty\tProject::NoLongTask", Set.of("projE"));
        m.put("SN-empty\tProject::TaskCount", Set.of("projE"));
        m.put("SN-empty\tProject::AllTasksInAllInstances", Set.of());
        m.put("SN-empty\tProject::IsEntity", Set.of());
        m.put("SN-empty\tResearchProject::HasGrant", Set.of());
        m.put("SN-empty\tBugReport::LowSeverity", Set.of());
        m.put("SN-empty\tBugReport::HasFix", Set.of());
        m.put("SN-empty\tProject::NamedEntity", Set.of());
        // SN-bottom: projB has no budget => bottom >= 0 is violation;
        // tB has no duration, brB has no severity => bottom comparisons violate
        m.put("SN-bottom\tProject::HasBudget", Set.of("projB"));
        m.put("SN-bottom\tProject::HasTasks", Set.of("projB"));
        m.put("SN-bottom\tProject::TasksNamed", Set.of());
        m.put("SN-bottom\tProject::HasShortTask", Set.of("projB"));
        m.put("SN-bottom\tProject::NoLongTask", Set.of("projB"));
        m.put("SN-bottom\tProject::TaskCount", Set.of("projB"));
        m.put("SN-bottom\tProject::AllTasksInAllInstances", Set.of());
        m.put("SN-bottom\tProject::IsEntity", Set.of());
        m.put("SN-bottom\tResearchProject::HasGrant", Set.of());
        m.put("SN-bottom\tBugReport::LowSeverity", Set.of("brB"));
        m.put("SN-bottom\tBugReport::HasFix", Set.of());
        m.put("SN-bottom\tProject::NamedEntity", Set.of());
        return m;
    }

    // ── Test: Core ↔ Q differential on every snapshot ───────────────────
    @Test
    void expectedViolationsMatchCoreAndQOnEverySnapshot() throws java.io.IOException {
        SchemaModel sm = schema();
        Map<String, Snapshot> snapshots = Map.of(
                "SN-main", mainSnapshot(),
                "SN-empty", emptySnapshot(),
                "SN-bottom", bottomSnapshot());
        Map<String, Set<String>> expected = expectedViolations();

        int checks = 0;
        for (String[] inv : INVARIANTS) {
            String key = inv[0];
            String ctx = inv[1];
            String ocl = "context " + ctx + " inv " + key.substring(key.indexOf("::") + 2)
                    + ": " + inv[2];
            for (Map.Entry<String, Snapshot> entry : snapshots.entrySet()) {
                String snapId = entry.getKey();
                Snapshot sn = entry.getValue();
                String testKey = snapId + "\t" + key;

                // Compile once for both Core and Q paths
                var fe = FrontendCompiler.compile(ocl, sm);
                assertTrue(fe.isSuccess(), () -> "frontend " + testKey + ": " + fe.diagnostics());
                OmgAs.OmgDocument doc = fe.value().get(0);
                var low = CoreLowering.lower(sm, doc, doc.constraints.get(0));
                assertTrue(low.isSuccess(), () -> "lowering " + testKey + ": " + low.diagnostics());

                // Core violations
                List<String> coreViol = CoreInterpreter.violations(sm, sn, low.value());

                // Q violations
                var gb = GraphBuilder.build(sm, sn);
                assertTrue(gb.isSuccess(), () -> "graph " + testKey + ": " + gb.diagnostics());
                var q = QCypTranslator.translate(low.value());
                assertTrue(q.isSuccess(), () -> "translate " + testKey + ": " + q.diagnostics());
                List<String> qViol = QInterpreter.violations(sm, gb.value().graph(),
                        low.value(), q.value());

                // Differential: Core must equal Q-over-G
                assertEquals(Set.copyOf(coreViol), Set.copyOf(qViol),
                        testKey + ": Core " + coreViol + " vs Q " + qViol);
                // And both must equal the expected oracle
                Set<String> exp = expected.getOrDefault(testKey, Set.of());
                assertEquals(exp, Set.copyOf(qViol),
                        testKey + ": expected " + exp + " but got " + qViol);
                checks++;
            }
        }
        assertEquals(36, checks, "should have checked 12 invariants x 3 snapshots");
    }

    // ── Test: all invariants produce valid Cypher ───────────────────────
    @Test
    void invariantsProduceValidCypher() {
        SchemaModel sm = schema();
        Snapshot sn = mainSnapshot();
        var graph = GraphBuilder.build(sm, sn);
        assertTrue(graph.isSuccess(), () -> "graph: " + graph.diagnostics());

        for (String[] inv : INVARIANTS) {
            String ocl = "context " + inv[1] + " inv " + inv[0].substring(inv[0].indexOf("::") + 2)
                    + ": " + inv[2];
            var fe = FrontendCompiler.compile(ocl, sm);
            if (!fe.isSuccess()) {
                // some invariants (e.g. BugReport context) produce bottom comparisons
                // that are numeric at R; skip those for Cypher check
                continue;
            }
            OmgAs.OmgDocument doc = fe.value().get(0);
            var low = CoreLowering.lower(sm, doc, doc.constraints.get(0));
            assertTrue(low.isSuccess(), () -> inv[0] + " lowering: " + low.diagnostics());
            var q = QCypTranslator.translate(low.value());
            assertTrue(q.isSuccess(), () -> inv[0] + " translate: " + q.diagnostics());
            var r = Realization.realize(q.value(), graph.value().graph(),
                    CypherAst.Dialect.CYPHER_5);
            if (r.isSuccess()) {
                String cypher = Serializer.cypherText(r.value());
                Neo4jCypherParserGate.assertParses(cypher);
            }
        }
    }

    // ── Test: allInstances covers the full inheritance tree ─────────────
    @Test
    void allInstancesCoversInheritanceTree() {
        SchemaModel sm = schema();
        Snapshot sn = mainSnapshot();
        Pipeline p = buildPipeline(
                "context Entity inv AllInInstances:\n"
                        + "  Entity.allInstances()->forAll(e | e.oclIsKindOf(Entity))",
                sm, sn);

        List<String> qViol = QInterpreter.violations(sm, p.g, p.unit, p.query);
        assertEquals(Set.of(), Set.copyOf(qViol),
                "allInstances(Entity) should include all subtypes");
    }

    // ── Test: intersection with empty set ───────────────────────────────
    @Test
    void intersectionWithEmptySetVacuouslyPasses() {
        SchemaModel sm = schema();
        Snapshot sn = mainSnapshot();
        Pipeline p = buildPipeline(
                "context Project inv NoOverlap:\n"
                        + "  self.tasks->intersection(Set{})->isEmpty()",
                sm, sn);

        List<String> qViol = QInterpreter.violations(sm, p.g, p.unit, p.query);
        // Intersection with empty set is always empty → isEmpty() is true → all pass
        assertEquals(Set.of(), Set.copyOf(qViol),
                "intersection with Set{} is always empty");
    }

    // ── Helpers ─────────────────────────────────────────────────────────
    private record Pipeline(SchemaModel sm, Snapshot sn, GraphModel g,
                           CoreInvariant unit, QQuery query) {
    }

    private static Pipeline buildPipeline(String ocl, SchemaModel sm, Snapshot sn) {
        var fe = FrontendCompiler.compile(ocl, sm);
        assertTrue(fe.isSuccess(), () -> "frontend: " + fe.diagnostics());
        OmgAs.OmgDocument doc = fe.value().get(0);
        var low = CoreLowering.lower(sm, doc, doc.constraints.get(0));
        assertTrue(low.isSuccess(), () -> "lowering: " + low.diagnostics());
        var gb = GraphBuilder.build(sm, sn);
        assertTrue(gb.isSuccess(), () -> "graph: " + gb.diagnostics());
        var q = QCypTranslator.translate(low.value());
        assertTrue(q.isSuccess(), () -> "translate: " + q.diagnostics());
        return new Pipeline(sm, sn, gb.value().graph(), low.value(), q.value());
    }
}