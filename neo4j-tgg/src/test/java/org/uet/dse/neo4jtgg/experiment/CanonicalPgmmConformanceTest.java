package org.uet.dse.neo4jtgg.experiment;

import org.junit.jupiter.api.Test;
import org.uet.dse.neo4jtgg.ocl.ir.CanonicalPgmmConformance;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CanonicalPgmmConformanceTest {
    @Test
    void canonicalPgmmInstanceConformsToJavaEncoding() throws Exception {
        Path root = workspaceRoot();
        CanonicalPgmmConformance.Report report = CanonicalPgmmConformance.check(
                root.resolve("verification/pgmm/PGMM-canonical-v1.tsv"));
        assertTrue(report.valid(), report.errors()::toString);
        assertTrue(report.entryCount() >= 25);
    }

    private Path workspaceRoot() {
        Path working = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        if (Files.isDirectory(working.resolve("md/research/model"))) return working;
        if (working.getParent() != null && Files.isDirectory(working.getParent().resolve("md/research/model"))) {
            return working.getParent();
        }
        throw new IllegalStateException("Cannot locate workspace root from " + working);
    }
}
