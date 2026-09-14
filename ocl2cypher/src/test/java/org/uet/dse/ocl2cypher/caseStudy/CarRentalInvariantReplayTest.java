package org.uet.dse.ocl2cypher.caseStudy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.uet.dse.ocl2cypher.api.CoreOracle;
import org.uet.dse.ocl2cypher.api.FrontendCompiler;
import org.uet.dse.ocl2cypher.diagnostics.Stage;

/**
 * Fixed expectations: a new rejection must fail, never silently become
 * coverage.
 */
class CarRentalInvariantReplayTest {

    // Explicit stage-R expectations, separate from successful Core/Q evaluation.
    private static final Map<String, String> NUMERIC_REJECTIONS = Map.of();
    private static final Path BASE = Path.of("..", "examples", "carrental", "umlmm");
    // Current frontend does not implicitly convert optional objects into sets.
    // isUnique's iterator syntax is outside its admitted iterator catalogue.
    private static final Map<String, String> REJECTIONS = Map.of(
            "CarGroup::ExactlyOneLowest", "E_TYPE",
            "CarGroup::ExactlyOneHighest", "E_TYPE",
            "Car::NotRentedDuringMaintenance", "E_TYPE",
            "Rental::AssignedRequestedGroup", "E_TYPE",
            "Rental::AssignedCarInProviderFleet", "E_TYPE",
            "Branch::UniqueEmployeeFirstNames", "E_RESOLUTION");

    @TestFactory
    Stream<DynamicTest> everyInvariantHasAnExplicitOutcome() throws Exception {
        return cases(false);
    }

    static Stream<DynamicTest> cases(boolean neo4j) throws Exception {
        if (neo4j) {
            throw new IllegalArgumentException("E2E requires a suite-owned SharedGraph");
        }
        return cases(false, null);
    }

    static Stream<DynamicTest> cases(boolean neo4j, CarRentalNeo4jReplayTest.SharedGraph shared) throws Exception {
        var fixture = CarRentalFixture.read(BASE.resolve("carrentalmodel.use"),
                BASE.resolve("carrental-experiment.soil")).toExecutable();
        var specs = CaseStudyReplayer.loadInvariants(BASE.resolve("invariants-extended.ocl"));
        Map<String, Set<String>> expected = new LinkedHashMap<>();
        var lines = Files.readAllLines(BASE.resolve("expected-violations-extended.csv"));
        assertEquals("invariant,ids,classification", lines.get(0));
        for (String line : lines.subList(1, lines.size())) {
            String[] fields = line.split(",", -1);
            assertEquals(3, fields.length, line);
            Set<String> ids = fields[1].isEmpty() ? Set.of() : Set.of(fields[1].split(";"));
            assertNull(expected.putIfAbsent(fields[0], ids), "duplicate invariant key");
            assertTrue(Set.of("ALL_PASS", "NON_VACUOUS_MIXED").contains(fields[2]));
            assertEquals(ids.isEmpty(), fields[2].equals("ALL_PASS"));
        }
        assertEquals(25, specs.size());
        assertEquals(new ArrayList<>(expected.keySet()), specs.stream()
                .map(s -> s.context() + "::" + s.name()).toList());
        assertTrue(expected.keySet().containsAll(REJECTIONS.keySet()));
        var selected = specs.stream().filter(spec -> !neo4j || (!REJECTIONS.containsKey(spec.context() + "::" + spec.name())
                && !NUMERIC_REJECTIONS.containsKey(spec.context() + "::" + spec.name()))).toList();
        assertEquals(neo4j ? 19 : 25, selected.size());
        return selected.stream().map(spec -> {
            String key = spec.context() + "::" + spec.name();
            String rejection = REJECTIONS.get(key);
            String outcome = neo4j ? "NEO4J_EXACT_IDS" : rejection != null ? "FRONTEND_REJECTION"
                    : NUMERIC_REJECTIONS.containsKey(key) ? "CORE_Q_IDS_AND_NUMERIC_REJECTION" : "CORE_Q_IDS_AND_PARSER";
            return DynamicTest.dynamicTest(key + " [" + outcome + "]", () -> {
                if (neo4j) {
                    org.junit.jupiter.api.Assumptions.assumeTrue(
                            "true".equalsIgnoreCase(System.getenv("OCL2CYPHER_RUN_CARRENTAL_E2E")),
                            "CarRental database writes require OCL2CYPHER_RUN_CARRENTAL_E2E=true");
                }
                var context = fixture.snapshot().objectsOfClass(fixture.schema(), spec.context());
                assertFalse(context.isEmpty(), "empty context must not conceal a missing fixture");
                assertTrue(context.containsAll(expected.get(key)), "expected IDs must belong to context");
                var frontend = FrontendCompiler.compile(spec.source(), fixture.schema());
                if (rejection != null) {
                    assertTrue(frontend.isFailure(), "boundary changed: review admission before changing this expectation");
                    assertEquals(Stage.E_SM, frontend.primaryDiagnostic().stage());
                    assertEquals(rejection, frontend.primaryDiagnostic().code(), frontend.primaryDiagnostic().toString());
                } else {
                    assertFalse(frontend.isFailure(), () -> "unexpected frontend rejection: " + frontend.primaryDiagnostic());
                    // CoreOracle checks lowering/admission; an exception here FAILS this invariant.
                    var actual = CoreOracle.violationsOclEq(spec.source(), fixture.schema(), fixture.snapshot());
                    assertEquals(actual.size(), new HashSet<>(actual).size(), "duplicate violation IDs");
                    assertEquals(expected.get(key), new HashSet<>(actual));
                    checkQAndRealization(key, spec.source(), fixture, expected.get(key), neo4j, shared);
                }
            });
        });
    }

    private static void checkQAndRealization(String key, String source,
            CarRentalFixture.Executable fixture, Set<String> expected, boolean neo4j,
            CarRentalNeo4jReplayTest.SharedGraph shared) {
        if (neo4j) {
            var scoped = org.uet.dse.ocl2cypher.source.model.SchemaModel.builder(
                    shared.namespace);
            fixture.schema().classes().forEach(scoped::clazz);
            fixture.schema().attributes().forEach(scoped::attribute);
            fixture.schema().associations().forEach(scoped::association);
            fixture = new CarRentalFixture.Executable(scoped.build(), fixture.snapshot());
        }
        var fe = FrontendCompiler.compile(source, fixture.schema());
        assertTrue(fe.isSuccess(), () -> fe.diagnostics().toString());
        var doc = fe.value().get(0);
        var core = org.uet.dse.ocl2cypher.core.CoreLowering.lower(fixture.schema(), doc, doc.constraints.get(0));
        assertTrue(core.isSuccess(), () -> "N: " + core.diagnostics());
        var graph = org.uet.dse.ocl2cypher.graph.GraphBuilder.build(fixture.schema(), fixture.snapshot());
        assertTrue(graph.isSuccess(), () -> "Graph: " + graph.diagnostics());
        var q = org.uet.dse.ocl2cypher.qcyp.QCypTranslator.translate(core.value());
        assertTrue(q.isSuccess(), () -> "T: " + q.diagnostics());
        var ids = org.uet.dse.ocl2cypher.qcyp.QInterpreter.violations(
                fixture.schema(), graph.value().graph(), core.value(), q.value());
        assertEquals(ids.size(), new HashSet<>(ids).size(), "Q duplicate violation IDs");
        assertEquals(expected, new HashSet<>(ids), "Q must agree with CSV even if realization rejects");
        var r = org.uet.dse.ocl2cypher.cypher.Realization.realize(q.value(), graph.value().graph(),
                org.uet.dse.ocl2cypher.cypher.CypherAst.Dialect.CYPHER_5);
        String rejection = NUMERIC_REJECTIONS.get(key);
        if (rejection != null) {
            assertTrue(r.isFailure(), "uncertified numeric expression must reject: " + key);
            assertEquals(Stage.R, r.primaryDiagnostic().stage());
            assertEquals(rejection, r.primaryDiagnostic().code(), r.primaryDiagnostic().toString());
        } else {
            assertTrue(r.isSuccess(), () -> "unexpected R failure: " + r.diagnostics());
            String text = org.uet.dse.ocl2cypher.cypher.Serializer.cypherText(r.value());
            assertNotNull(text);
            assertFalse(text.isBlank(), "successful realization must serialize");
            if (key.equals("Rental::OfferedGroup") || key.equals("Car::FleetBranchOffersItsGroup")) {
                // Fixed `rel` captures an outer relationship in nested COLLECT navigation.
                assertFalse(java.util.regex.Pattern.compile("\\[\\s*`?rel`?\\s*:").matcher(text).find(),
                        "nested navigation must not reuse the fixed relationship alias rel");
            }
            org.uet.dse.ocl2cypher.cypher.Neo4jCypherParserGate.assertParses(text);
            if (neo4j) {
                assertTrue(graph.value().graph().modelKey().startsWith("ocl2cypher-e2e-carrental-"));
                var request = new org.uet.dse.ocl2cypher.execution.Neo4jExecutionAdapter.ExecutionRequest(
                        r.value(), graph.value().graph(), requiredEnv("NEO4J_URI"), requiredEnv("NEO4J_DB"),
                        requiredEnv("NEO4J_USER"), requiredEnv("NEO4J_PASSWORD"), Map.of());
                System.out.println("CarRental E2E start: " + key);
                var executed = shared.execute(request);
                assertTrue(executed.isSuccess(), "Neo4j materialization/query/decode failed for " + key
                        + "; namespace=" + graph.value().graph().modelKey()
                        + (executed.isFailure() ? "; diagnostic="
                        + redact(executed.primaryDiagnostic().toString(), request.password()) : ""));
                var actual = executed.value().violationIds();
                assertEquals(actual.size(), new HashSet<>(actual).size(), "Neo4j duplicate IDs");
                assertEquals(expected, new HashSet<>(actual), "Neo4j vs expected CSV: " + key);
                System.out.println("CarRental E2E PASS: " + key);
            }
        }
    }

    private static String requiredEnv(String key) {
        String value = System.getenv(key);
        assertNotNull(value, "E2E requires " + key);
        assertFalse(value.isBlank(), "E2E requires nonblank " + key);
        return value;
    }

    private static String redact(String message, String password) {
        String safe = message.replace(password, "[REDACTED]");
        return safe.replaceAll("(?i)((?:neo4j|bolt)(?:\\+s|\\+ssc)?://)[^\\s/@]+@", "$1[REDACTED]@");
    }
}
