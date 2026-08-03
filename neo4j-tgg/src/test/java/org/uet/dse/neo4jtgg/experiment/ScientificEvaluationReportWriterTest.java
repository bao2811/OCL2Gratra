package org.uet.dse.neo4jtgg.experiment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScientificEvaluationReportWriterTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void writesDeterministicJsonAndCorrectnessCsv() throws Exception {
        var correctness = ScientificEvaluationReport.CorrectnessObservation.compare(
                "Person::Adult", Set.of("p2", "p1"), Set.of("p1"), Set.of("p1"));
        var report = new ScientificEvaluationReport.Report(
                List.of(correctness), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                new ScientificEvaluationReport.ReproducibilityManifest(
                        "company-case", 42L, "model-hash", "ocl-hash", "canonical-v1",
                        "2026.05", "5", "neo4j", "Windows", "Java 17", 100, 1, 3));

        Path jsonPath = temporaryDirectory.resolve("nested/report.json");
        Path csvPath = temporaryDirectory.resolve("nested/correctness.csv");
        ScientificEvaluationReportWriter.writeJson(report, jsonPath);
        ScientificEvaluationReportWriter.writeCorrectnessCsv(report, csvPath);

        String json = Files.readString(jsonPath);
        String csv = Files.readString(csvPath);
        assertTrue(json.contains("\"schemaVersion\":\"ocl2cypher-evidence-v1\""));
        assertTrue(json.contains("\"referenceIds\":[\"p1\"]"));
        assertTrue(json.contains("\"universeIds\":[\"p1\",\"p2\"]"));
        assertTrue(json.contains("\"datasetId\":\"company-case\""));
        assertEquals(2, csv.lines().count());
        assertTrue(csv.contains("\"Person::Adult\""));
        assertTrue(csv.contains(",true,1.000000,1.000000,1.000000"));
    }
}
