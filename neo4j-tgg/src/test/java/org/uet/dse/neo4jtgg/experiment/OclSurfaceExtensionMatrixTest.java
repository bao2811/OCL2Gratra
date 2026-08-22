package org.uet.dse.neo4jtgg.experiment;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Locks each incremental surface extension to a complete vertical evidence slice. */
class OclSurfaceExtensionMatrixTest {
    private static final List<String> HEADER = List.of(
            "slice", "surface", "normal_form", "proof_equation", "static_test",
            "property_test", "runtime_test", "status");

    @Test
    void everySurfaceSliceNamesItsNormalizerProofAndRuntimeEvidence() throws IOException {
        List<String> lines = Files.readAllLines(matrixPath()).stream()
                .filter(line -> !line.isBlank() && !line.startsWith("#"))
                .toList();
        assertEquals(HEADER, Arrays.asList(lines.get(0).replace("\uFEFF", "").split(",", -1)));
        assertFalse(lines.subList(1, lines.size()).isEmpty());

        for (String line : lines.subList(1, lines.size())) {
            List<String> cells = Arrays.asList(line.split(",", -1));
            assertEquals(HEADER.size(), cells.size(), line);
            assertFalse(cells.stream().anyMatch(String::isBlank), line);
            assertEquals("implemented", cells.get(HEADER.indexOf("status")), line);
        }
    }

    private Path matrixPath() {
        Path working = Path.of(System.getProperty("user.dir"));
        for (Path candidate : List.of(
                working.resolve("verification/coverage/ocl_surface_extension_matrix.csv"),
                working.resolve("../verification/coverage/ocl_surface_extension_matrix.csv"))) {
            if (Files.isRegularFile(candidate)) return candidate.normalize();
        }
        throw new IllegalStateException("Cannot locate OCL surface-extension matrix from " + working);
    }
}
