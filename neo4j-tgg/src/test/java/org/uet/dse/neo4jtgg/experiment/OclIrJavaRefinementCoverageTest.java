package org.uet.dse.neo4jtgg.experiment;

import org.junit.jupiter.api.Test;
import org.uet.dse.neo4jtgg.ocl.ir.OclCypherPlan;
import org.uet.dse.neo4jtgg.ocl.ir.OclIr;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reflection-backed Java-to-Lean constructor coverage contract for Theorem 4. */
class OclIrJavaRefinementCoverageTest {
    private static final List<String> HEADER = List.of(
            "java_constructor", "java_payload_fields", "plan_constructors", "lean_constructor",
            "lean_agreement_case", "planner_method", "java_refinement_method", "evidence_test",
            "evidence_method", "witness_test", "witness_method");
    private static final String EVIDENCE_METHOD =
            "everyProductionOptimizedConstructorHasLeanAndJavaRefinementEvidence";
    private static final String WITNESS_TEST = "OclIrPlanPayloadRefinementTest";
    private static final String WITNESS_METHOD =
            "allSixteenOptimizedConstructorsHaveExecutableFieldPreservingWitnesses";

    @Test
    void everyProductionOptimizedConstructorHasLeanAndJavaRefinementEvidence() throws Exception {
        Path workspace = workspace();
        List<Row> rows = rows(workspace.resolve(
                "verification/coverage/java_ir_refinement_matrix.csv"));

        List<String> permitted = Arrays.stream(OclIr.OptimizedExpression.class.getPermittedSubclasses())
                .map(Class::getSimpleName).toList();
        assertEquals(permitted, rows.stream().map(Row::javaConstructor).toList());
        assertEquals(rows.size(), new LinkedHashSet<>(rows.stream().map(Row::javaConstructor).toList()).size());
        assertEquals(rows.size(), new LinkedHashSet<>(rows.stream().map(Row::leanConstructor).toList()).size());
        Set<String> permittedPlans = Arrays.stream(OclCypherPlan.ExpressionPlan.class.getPermittedSubclasses())
                .map(Class::getSimpleName).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        String ir = Files.readString(workspace.resolve(
                "neo4j-tgg/src/main/java/org/uet/dse/neo4jtgg/ocl/ir/OclIr.java"));
        String planner = Files.readString(workspace.resolve(
                "neo4j-tgg/src/main/java/org/uet/dse/neo4jtgg/ocl/ir/OclCypherPlanner.java"));
        String verifier = Files.readString(workspace.resolve(
                "neo4j-tgg/src/test/java/org/uet/dse/neo4jtgg/experiment/PipelineRefinementVerifier.java"));
        String lean = Files.readString(workspace.resolve("verification/lean/Ocl2CypherProof.lean"));

        assertTrue(lean.contains("theorem java_ir_eval_refinement"));
        assertTrue(lean.contains(
                        "(source : Expr Payload) (sourceCollectionType operationName : Payload)"),
                "Lean CollectionOperation must preserve the Java sourceCollectionType payload");
        assertTrue(lean.contains(
                        "(source : Expr Payload) (sourceCollectionType operationName iteratorName : Payload)"),
                "Lean IteratorOperation must preserve the Java sourceCollectionType payload");
        for (Row row : rows) {
            assertPattern(ir, "\\brecord\\s+" + Pattern.quote(row.javaConstructor()) + "\\b");
            Class<?> javaConstructor = Arrays.stream(OclIr.OptimizedExpression.class.getPermittedSubclasses())
                    .filter(type -> type.getSimpleName().equals(row.javaConstructor())).findFirst().orElseThrow();
            assertEquals(Arrays.asList(row.javaPayloadFields().split(";", -1)),
                    Arrays.stream(javaConstructor.getRecordComponents()).map(component -> component.getName()).toList(),
                    row.javaConstructor());
            for (String planConstructor : row.planConstructors().split("\\|", -1)) {
                assertTrue(permittedPlans.contains(planConstructor), row.javaConstructor() + " -> " + planConstructor);
            }
            assertPattern(planner, "instanceof\\s+OclIr\\." + Pattern.quote(row.javaConstructor()) + "\\b");
            assertPattern(planner, "\\b" + Pattern.quote(row.plannerMethod()) + "\\s*\\(");
            assertPattern(verifier, "instanceof\\s+OclIr\\." + Pattern.quote(row.javaConstructor()) + "\\b");
            assertPattern(verifier, "\\b" + Pattern.quote(row.javaRefinementMethod()) + "\\s*\\(");
            assertPattern(lean, "\\|\\s+" + Pattern.quote(row.leanConstructor()) + "(?:\\s|$)");
            assertPattern(lean, "\\b" + Pattern.quote(row.leanAgreementCase()) + "\\s*:");
            assertEquals(getClass().getSimpleName(), row.evidenceTest());
            assertEquals(EVIDENCE_METHOD, row.evidenceMethod());
            assertEquals(WITNESS_TEST, row.witnessTest());
            assertEquals(WITNESS_METHOD, row.witnessMethod());
            getClass().getDeclaredMethod(row.evidenceMethod());
            Class.forName(getClass().getPackageName() + "." + row.witnessTest())
                    .getDeclaredMethod(row.witnessMethod());
        }
    }

    private static void assertPattern(String source, String regex) {
        assertTrue(Pattern.compile(regex, Pattern.MULTILINE).matcher(source).find(), regex);
    }

    private static List<Row> rows(Path path) throws Exception {
        List<String> lines = Files.readAllLines(path).stream().filter(line -> !line.isBlank()).toList();
        assertFalse(lines.isEmpty());
        assertEquals(HEADER, List.of(lines.get(0).split(",", -1)));
        List<Row> result = new ArrayList<>();
        for (String line : lines.subList(1, lines.size())) {
            String[] value = line.split(",", -1);
            assertEquals(HEADER.size(), value.length, line);
            result.add(new Row(value[0], value[1], value[2], value[3], value[4], value[5],
                    value[6], value[7], value[8], value[9], value[10]));
        }
        return List.copyOf(result);
    }

    private static Path workspace() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve(
                    "verification/contract/proof-contract-registry.json"))) return current;
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate workspace root");
    }

    private record Row(String javaConstructor, String javaPayloadFields, String planConstructors,
                       String leanConstructor, String leanAgreementCase, String plannerMethod,
                       String javaRefinementMethod, String evidenceTest, String evidenceMethod,
                       String witnessTest, String witnessMethod) {
    }
}
