package org.uet.dse.ocl2cypher.caseStudy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.uet.dse.ocl2cypher.api.CoreOracle;
import org.uet.dse.ocl2cypher.api.FrontendCompiler;
import org.uet.dse.ocl2cypher.core.CoreLowering;
import org.uet.dse.ocl2cypher.cypher.CypherAst;
import org.uet.dse.ocl2cypher.cypher.Neo4jCypherParserGate;
import org.uet.dse.ocl2cypher.cypher.Realization;
import org.uet.dse.ocl2cypher.cypher.Serializer;
import org.uet.dse.ocl2cypher.execution.Neo4jExecutionAdapter;
import org.uet.dse.ocl2cypher.graph.GraphBuilder;
import org.uet.dse.ocl2cypher.graph.GraphModel;
import org.uet.dse.ocl2cypher.qcyp.QCypTranslator;
import org.uet.dse.ocl2cypher.qcyp.QInterpreter;
import org.uet.dse.ocl2cypher.source.model.SchemaModel;
import org.uet.dse.ocl2cypher.source.model.Snapshot;

/**
 * Medical-domain oracle replay. Every expected set is read from the CSV before
 * either evaluator or generated Cypher is invoked.
 */
@Execution(ExecutionMode.SAME_THREAD)
class MedicalInvariantReplayTest {
    private static final Path BASE = Path.of("..", "examples", "medical");
    static final int CASES = 36;

    @TestFactory
    Stream<DynamicTest> medicalSourceCoreQAndCypherStructure() throws Exception {
        return cases(false, null);
    }

    static Stream<DynamicTest> cases(boolean live,
            MedicalNeo4jReplayTest.SharedGraph shared) throws Exception {
        CarRentalFixture.Executable sourceFixture = CarRentalFixture.read(
                BASE.resolve("medical.use"), BASE.resolve("medical.soil")).toExecutable();
        SchemaModel schema = live ? scoped(sourceFixture.schema(), shared.namespace)
                : sourceFixture.schema();
        Snapshot snapshot = sourceFixture.snapshot();
        var built = GraphBuilder.build(schema, snapshot);
        assertTrue(built.isSuccess(), () -> built.diagnostics().toString());
        GraphModel graph = built.value().graph();

        var specs = CaseStudyReplayer.loadInvariants(BASE.resolve("invariants.ocl"));
        Map<String, Set<String>> expected = expected();
        assertEquals(CASES, specs.size());
        assertEquals(new ArrayList<>(expected.keySet()), specs.stream()
                .map(spec -> spec.context() + "::" + spec.name()).toList());

        return specs.stream().map(spec -> {
            String key = spec.context() + "::" + spec.name();
            return DynamicTest.dynamicTest((live ? "NEO4J/" : "LOCAL/") + key, () -> {
                if (live) {
                    Assumptions.assumeTrue("true".equalsIgnoreCase(
                            System.getenv("OCL2CYPHER_RUN_MEDICAL_E2E")),
                            "Medical database writes require OCL2CYPHER_RUN_MEDICAL_E2E=true");
                }
                var context = snapshot.objectsOfClass(schema, spec.context());
                assertFalse(context.isEmpty(), "empty context would make the case vacuous");
                assertTrue(context.containsAll(expected.get(key)),
                        "expected IDs must belong to the invariant context");

                assertEquals(expected.get(key), new HashSet<>(CoreOracle.violationsOclEq(
                        spec.source(), schema, snapshot)), "source oracle: " + key);

                var frontend = FrontendCompiler.compile(spec.source(), schema);
                assertTrue(frontend.isSuccess(), () -> frontend.diagnostics().toString());
                var document = frontend.value().get(0);
                var core = CoreLowering.lower(schema, document, document.constraints.get(0));
                assertTrue(core.isSuccess(), () -> core.diagnostics().toString());
                var q = QCypTranslator.translate(core.value());
                assertTrue(q.isSuccess(), () -> q.diagnostics().toString());

                var qIds = QInterpreter.violations(schema, graph, core.value(), q.value());
                assertEquals(qIds.size(), new HashSet<>(qIds).size(),
                        "Q returned duplicate IDs: " + key);
                assertEquals(expected.get(key), new HashSet<>(qIds), "Q oracle: " + key);

                var realization = Realization.realize(q.value(), graph,
                        CypherAst.Dialect.CYPHER_5);
                assertTrue(realization.isSuccess(), () -> realization.diagnostics().toString());
                String cypher = Serializer.cypherText(realization.value());
                assertFalse(cypher.isBlank());
                assertTrue(cypher.length() < 20_000,
                        () -> key + " expanded to " + cypher.length() + " characters");
                Neo4jCypherParserGate.assertParses(cypher);

                if (live) {
                    System.out.println("Medical E2E START: " + key);
                    var executed = shared.execute(realization.value(), graph);
                    assertTrue(executed.isSuccess(), () -> "Neo4j execution failed for " + key
                            + ": " + executed.diagnostics());
                    var actual = executed.value().violationIds();
                    assertEquals(actual.size(), new HashSet<>(actual).size(),
                            "Neo4j returned duplicate IDs: " + key);
                    assertEquals(expected.get(key), new HashSet<>(actual),
                            "Neo4j vs expected CSV: " + key);
                    System.out.println("Medical E2E PASS: " + key);
                }
            });
        });
    }

    private static SchemaModel scoped(SchemaModel original, String modelKey) {
        SchemaModel.Builder builder = SchemaModel.builder(modelKey);
        original.classes().forEach(builder::clazz);
        original.attributes().forEach(builder::attribute);
        original.associations().forEach(builder::association);
        return builder.build();
    }

    private static Map<String, Set<String>> expected() throws Exception {
        var lines = Files.readAllLines(BASE.resolve("expected-violations.csv"));
        assertEquals("invariant,ids,classification", lines.get(0));
        Map<String, Set<String>> result = new LinkedHashMap<>();
        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) continue;
            String[] fields = line.split(",", -1);
            assertEquals(3, fields.length, line);
            Set<String> ids = fields[1].isEmpty() ? Set.of()
                    : Set.of(fields[1].split(";"));
            assertNull(result.putIfAbsent(fields[0], ids), "duplicate expected row");
            assertTrue(Set.of("ALL_PASS", "ALL_FAIL", "NON_VACUOUS_MIXED")
                    .contains(fields[2]), "unknown classification: " + line);
            if (fields[2].equals("ALL_PASS")) assertTrue(ids.isEmpty(), line);
            if (fields[2].equals("ALL_FAIL")) assertFalse(ids.isEmpty(), line);
        }
        return result;
    }

    static String required(String key) {
        String value = System.getenv(key);
        assertNotNull(value, "E2E requires " + key);
        assertFalse(value.isBlank(), "E2E requires nonblank " + key);
        return value;
    }
}
