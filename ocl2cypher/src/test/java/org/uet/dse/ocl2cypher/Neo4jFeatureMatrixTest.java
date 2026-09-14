package org.uet.dse.ocl2cypher;

import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.*;
import org.neo4j.driver.*;
import org.uet.dse.ocl2cypher.api.*;
import org.uet.dse.ocl2cypher.core.CoreLowering;
import org.uet.dse.ocl2cypher.cypher.*;
import org.uet.dse.ocl2cypher.execution.Neo4jExecutionAdapter;
import org.uet.dse.ocl2cypher.graph.*;
import org.uet.dse.ocl2cypher.qcyp.*;
import org.uet.dse.ocl2cypher.runtime.*;
import org.uet.dse.ocl2cypher.source.model.*;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit oracle per feature, one materialization, no skip on enabled backend failure. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
class Neo4jFeatureMatrixTest {
    private final SchemaModel schema = SchemaModel.builder("ocl2cypher-e2e-features-" + UUID.randomUUID())
            .clazz(UmlClass.of("Probe"))
            .clazz(UmlClass.of("SpecialProbe", "Probe"))
            .association(UmlAssociation.binary("Next", "Probe", "previous", "Probe", "next"))
            .attribute(UmlAttribute.of("Probe", "flag", OclType.BOOLEAN))
            .attribute(UmlAttribute.of("Probe", "text", OclType.STRING)).build();
    private final Snapshot snapshot = Snapshot.builder()
            .object("yes", "SpecialProbe").attribute("yes", "flag", new OclValue.BooleanValue(OclType.BOOLEAN, OclValue.BooleanValue.Bool3.TRUE))
            .attribute("yes", "text", new OclValue.StringValue("a"))
            .object("no", "Probe").attribute("no", "flag", new OclValue.BooleanValue(OclType.BOOLEAN, OclValue.BooleanValue.Bool3.FALSE))
            .attribute("no", "text", new OclValue.StringValue(""))
            .object("bottom", "Probe").link("Next", "yes", "no").link("Next", "no", "bottom").build();
    private Driver driver;
    private Session session;
    private boolean setupAttempted;
    private RuntimeException setupFailure;
    private record Feature(String name, String body, Set<String> violations) {}
    private static Feature ok(String name, String body) { return new Feature(name, body, Set.of()); }
    private static List<Feature> features() {
        return List.of(
            new Feature("boolean_bottom", "self.flag", Set.of("no", "bottom")),
            ok("or_masks_bottom", "true or self.flag"),
            new Feature("and_propagates_bottom", "true and self.flag", Set.of("no", "bottom")),
            ok("false_implies_bottom", "false implies self.flag"),
            // OCL_val section 4.5: total equality makes bottom <> defined empty true.
            new Feature("string_missing_vs_empty", "self.text <> ''", Set.of("no")),
            ok("lazy_if", "if true then true else self.flag endif"),
            ok("set_support", "Set{'a', 'a'} = Set{'a'}"),
            new Feature("bag_occurrences", "Bag{'a', 'a'} = Bag{'a'}", Set.of("yes", "no", "bottom")),
            ok("empty_forall", "Set{'a'}->reject(x | true)->forAll(x | false)"),
            new Feature("empty_exists", "Set{'a'}->reject(x | true)->exists(x | true)", Set.of("yes", "no", "bottom")),
            ok("nested_iterators", "Set{'a', 'b'}->forAll(x | Set{'a', 'b'}->exists(y | x = y))"),
            ok("shadowed_iterator", "Set{'a'}->forAll(x | Set{'b'}->forAll(x | x = 'b'))"),
            ok("collect_duplicates", "Set{'a', 'b'}->collect(x | 'z') = Bag{'z', 'z'}"),
            ok("select_reject_union", "Set{'a', 'b'}->select(x | x = 'a')->union(Set{'a', 'b'}->reject(x | x = 'a')) = Set{'a', 'b'}"),
            ok("intersection", "Set{'a', 'b'}->intersection(Set{'b'}) = Set{'b'}"),
            ok("inherited_extent", "Probe.allInstances()->includes(self)"),
            ok("kind_of_parent", "self.oclIsKindOf(Probe)"),
            new Feature("exact_dynamic_type", "self.oclIsTypeOf(SpecialProbe)", Set.of("no", "bottom")),
            ok("cast_identity", "self.oclAsType(Probe) = self"),
            new Feature("navigation_many_empty", "self.next->notEmpty()", Set.of("bottom")),
            ok("inverse_navigation_identity", "self.next->forAll(n | n.previous = self)"),
            new Feature("nested_navigation", "self.next->exists(n | n.next->notEmpty())", Set.of("no", "bottom")),
            ok("includes_all", "Set{'a', 'b'}->includesAll(Set{'a'})"),
            ok("excludes_all", "Bag{'a', 'a'}->excludesAll(Bag{'b'})"),
            ok("integer_add_subtract_multiply", "(2 + 3) * 4 - 1 = 19"),
            ok("integer_negate_abs", "(-2).abs() = 2 and -(-7) = 7"),
            ok("integer_min_max", "2.min(3) = 2 and 2.max(3) = 3"),
            ok("integer_div_mod", "(-7) div 3 = -2 and (-7) mod 3 = -1"),
            ok("integer_set_bag_sum", "Set{2, 2, -1}->sum() = 1 and Bag{2, 2, -1}->sum() = 3"),
            new Feature("xor_bottom", "self.flag xor false", Set.of("no", "bottom")));
    }
    @TestFactory Stream<DynamicTest> frontendCoreQAndRealizationMatrix() { return cases(false); }
    @TestFactory Stream<DynamicTest> actualNeo4jMatrix() { return cases(true); }
    @Test void undefinedOperationIsAnExplicitFrontendRejection() {
        var result = FrontendCompiler.compile(
                "context Probe inv Defined: not self.text.oclIsUndefined() and self.text <> ''", schema);
        assertTrue(result.isFailure());
        assertEquals(org.uet.dse.ocl2cypher.diagnostics.Stage.E_SM, result.primaryDiagnostic().stage());
        assertEquals("E_RESOLUTION", result.primaryDiagnostic().code());
    }
    private Stream<DynamicTest> cases(boolean live) {
        return features().stream().map(f -> DynamicTest.dynamicTest((live ? "NEO4J/" : "LOCAL/") + f.name(), () -> {
            if (live) Assumptions.assumeTrue("true".equalsIgnoreCase(System.getenv("OCL2CYPHER_RUN_NEO4J_E2E")));
            String source = "context Probe inv Matrix: " + f.body();
            assertEquals(f.violations(), new HashSet<>(CoreOracle.violationsOclEq(source, schema, snapshot)));
            var fe = FrontendCompiler.compile(source, schema);
            assertTrue(fe.isSuccess(), () -> fe.diagnostics().toString());
            var doc = fe.value().get(0);
            var core = CoreLowering.lower(schema, doc, doc.constraints.get(0));
            assertTrue(core.isSuccess(), () -> core.diagnostics().toString());
            var graph = GraphBuilder.build(schema, snapshot);
            assertTrue(graph.isSuccess(), () -> graph.diagnostics().toString());
            var q = QCypTranslator.translate(core.value());
            assertTrue(q.isSuccess(), () -> q.diagnostics().toString());
            assertEquals(f.violations(), new HashSet<>(QInterpreter.violations(schema, graph.value().graph(), core.value(), q.value())));
            var r = Realization.realize(q.value(), graph.value().graph(), CypherAst.Dialect.CYPHER_5);
            assertTrue(r.isSuccess(), () -> r.diagnostics().toString());
            Neo4jCypherParserGate.assertParses(Serializer.cypherText(r.value()));
            if (live) {
                connectOnce(graph.value().graph());
                System.out.println("Neo4j feature START: " + f.name());
                var result = Neo4jExecutionAdapter.execute(session, r.value(), Map.of());
                assertTrue(result.isSuccess(), "Neo4j query/decode failed for " + f.name());
                var ids = result.value().violationIds();
                assertEquals(ids.size(), new HashSet<>(ids).size(), "duplicate violation IDs");
                assertEquals(f.violations(), new HashSet<>(ids));
                System.out.println("Neo4j feature PASS: " + f.name());
            }
        }));
    }
    private static String required(String key) {
        String value = System.getenv(key);
        assertNotNull(value, "Missing " + key);
        assertFalse(value.isBlank(), "Blank " + key);
        return value;
    }
    private void connectOnce(GraphModel graph) {
        if (!setupAttempted) {
            String uri = required("NEO4J_URI"), db = required("NEO4J_DB"), user = required("NEO4J_USER"), password = required("NEO4J_PASSWORD");
            setupAttempted = true;
            try {
                driver = GraphDatabase.driver(uri, AuthTokens.basic(user, password));
                session = driver.session(SessionConfig.forDatabase(db));
                var component = session.run("CALL dbms.components() "
                        + "YIELD name, versions, edition "
                        + "WHERE name = 'Neo4j Kernel' "
                        + "RETURN name, versions[0] AS version, edition "
                        + "LIMIT 1").single();
                System.out.println("Neo4j runtime profile: name=" + component.get("name").asString()
                        + ", version=" + component.get("version").asString()
                        + ", edition=" + component.get("edition").asString()
                        + ", database=" + db);
                Neo4jExecutionAdapter.materializeGraph(session, graph);
            } catch (RuntimeException e) { setupFailure = e; }
        }
        assertNull(setupFailure, "Enabled Neo4j feature matrix setup failed; verify connection and database constraints");
    }
    @AfterAll void close() {
        try { if (session != null) session.close(); }
        finally { if (driver != null) driver.close(); }
    }
}
