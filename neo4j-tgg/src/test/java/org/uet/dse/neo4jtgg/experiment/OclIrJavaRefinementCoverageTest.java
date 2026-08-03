package org.uet.dse.neo4jtgg.experiment;

import org.junit.jupiter.api.Test;
import org.uet.dse.neo4jtgg.ocl.ir.OclIr;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reflection-backed Java-to-Lean constructor coverage contract for Theorem 4. */
class OclIrJavaRefinementCoverageTest {
    private static final List<String> HEADER = List.of(
            "java_constructor", "lean_constructor", "lean_agreement_case", "planner_method",
            "java_refinement_method", "evidence_test", "evidence_method");
    private static final String EVIDENCE_METHOD =
            "everyProductionOptimizedConstructorHasLeanAndJavaRefinementEvidence";

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

        String ir = Files.readString(workspace.resolve(
                "neo4j-tgg/src/main/java/org/uet/dse/neo4jtgg/ocl/ir/OclIr.java"));
        String planner = Files.readString(workspace.resolve(
                "neo4j-tgg/src/main/java/org/uet/dse/neo4jtgg/ocl/ir/OclCypherPlanner.java"));
        String verifier = Files.readString(workspace.resolve(
                "neo4j-tgg/src/test/java/org/uet/dse/neo4jtgg/experiment/PipelineRefinementVerifier.java"));
        String lean = Files.readString(workspace.resolve("verification/lean/Ocl2CypherProof.lean"));

        assertTrue(lean.contains("theorem java_ir_eval_refinement"));
        for (Row row : rows) {
            assertPattern(ir, "\\brecord\\s+" + Pattern.quote(row.javaConstructor()) + "\\b");
            assertPattern(planner, "instanceof\\s+OclIr\\." + Pattern.quote(row.javaConstructor()) + "\\b");
            assertPattern(planner, "\\b" + Pattern.quote(row.plannerMethod()) + "\\s*\\(");
            assertPattern(verifier, "instanceof\\s+OclIr\\." + Pattern.quote(row.javaConstructor()) + "\\b");
            assertPattern(verifier, "\\b" + Pattern.quote(row.javaRefinementMethod()) + "\\s*\\(");
            assertPattern(lean, "\\|\\s+" + Pattern.quote(row.leanConstructor()) + "(?:\\s|$)");
            assertPattern(lean, "\\b" + Pattern.quote(row.leanAgreementCase()) + "\\s*:");
            assertEquals(getClass().getSimpleName(), row.evidenceTest());
            assertEquals(EVIDENCE_METHOD, row.evidenceMethod());
            getClass().getDeclaredMethod(row.evidenceMethod());
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
            result.add(new Row(value[0], value[1], value[2], value[3], value[4], value[5], value[6]));
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

    private record Row(String javaConstructor, String leanConstructor, String leanAgreementCase,
                       String plannerMethod, String javaRefinementMethod,
                       String evidenceTest, String evidenceMethod) {
    }
}
