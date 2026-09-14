package org.uet.dse.neo4jtgg.experiment;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Makes the implementation-to-proof traceability matrix an executable CI contract. */
class OclConformanceMatrixTest {
    private static final List<String> REQUIRED_COLUMNS = List.of(
            "constructor", "parse", "bind", "typing", "va", "normalize", "cypher",
            "proof_case", "positive_test", "negative_test", "real_neo4j");
    private static final Map<String, String> REQUIRED_ARTIFACTS = Map.of(
            "parse", "OclDocumentParser",
            "bind", "OclSemanticBinder",
            "typing", "BoundTypeRules",
            "va", "OclIrBuilder",
            "normalize", "OclIrOptimizer",
            "cypher", "OclCypherPlannerRenderer",
            "proof_case", "T1-T5",
            "positive_test", "OclValFragmentCoverageTest",
            "negative_test", "OclValNegativeAdmissionCoverageTest",
            "real_neo4j", "OclValRealNeo4jCoverageTest");

    @Test
    void everyAdmittedConstructorHasAllRequiredConformanceArtifacts() throws IOException {
        List<String> lines = Files.readAllLines(matrixPath()).stream()
                .filter(line -> !line.isBlank() && !line.startsWith("#"))
                .toList();
        assertFalse(lines.isEmpty());

        List<String> header = Arrays.asList(lines.get(0).replace("\uFEFF", "").split(",", -1));
        assertEquals(REQUIRED_COLUMNS, header);

        Map<String, List<String>> rows = new LinkedHashMap<>();
        for (String line : lines.subList(1, lines.size())) {
            List<String> cells = Arrays.asList(line.split(",", -1));
            assertEquals(header.size(), cells.size(), line);
            assertFalse(cells.stream().anyMatch(String::isBlank), line);
            assertTrue(rows.put(cells.get(0), cells) == null, "Duplicate constructor: " + cells.get(0));
        }

        Set<String> admitted = OclValFragmentCoverageTest.admittedCases().stream()
                .map(OclValFragmentCoverageTest.CoverageCase::feature)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        assertEquals(admitted, rows.keySet(), "Conformance registry and admitted fragment differ");
        rows.forEach((constructor, cells) -> {
            cells.subList(1, cells.size()).forEach(artifact ->
                    assertFalse("GAP".equals(artifact), constructor + " still has an open conformance gap"));
            REQUIRED_ARTIFACTS.forEach((column, artifact) -> assertEquals(
                    artifact, cells.get(header.indexOf(column)), constructor + ":" + column));
        });
    }

    private Path matrixPath() {
        Path workingDirectory = Path.of(System.getProperty("user.dir"));
        for (Path candidate : List.of(
                workingDirectory.resolve("verification/coverage/oclval_coverage_matrix.csv"),
                workingDirectory.resolve("../verification/coverage/oclval_coverage_matrix.csv"))) {
            if (Files.isRegularFile(candidate)) {
                return candidate.normalize();
            }
        }
        throw new IllegalStateException("Cannot locate oclval_coverage_matrix.csv from " + workingDirectory);
    }
}
