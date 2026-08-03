package org.uet.dse.neo4jtgg.experiment;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScientificEvaluationReportTest {
    @Test
    void computesConfusionMatrixAndStableIdMetrics() {
        var result = ScientificEvaluationReport.CorrectnessObservation.compare(
                "Person::Adult", Set.of("p1", "p2", "p3", "p4"),
                Set.of("p1", "p2"), Set.of("p2", "p3"));

        assertEquals(1, result.truePositive());
        assertEquals(1, result.trueNegative());
        assertEquals(1, result.falsePositive());
        assertEquals(1, result.falseNegative());
        assertEquals(0.5, result.precision());
        assertEquals(0.5, result.recall());
        assertEquals(0.5, result.accuracy());
        assertFalse(result.exactMatch());
    }

    @Test
    void reportsMedianP95DeviationAndIdStability() {
        var stable = new ScientificEvaluationReport.RepetitionObservation(
                "Person::Adult", List.of(10L, 30L, 20L, 40L),
                List.of(Set.of("p1"), Set.of("p1"), Set.of("p1"), Set.of("p1")));
        assertEquals(20, stable.medianNs());
        assertEquals(40, stable.p95Ns());
        assertTrue(stable.standardDeviationNs() > 0);
        assertTrue(stable.stableIds());

        var unstable = new ScientificEvaluationReport.RepetitionObservation(
                "Person::Adult", List.of(10L, 20L), List.of(Set.of("p1"), Set.of("p2")));
        assertFalse(unstable.stableIds());
    }

    @Test
    void computesEncodingExpansionAndThroughput() {
        var encoding = new ScientificEvaluationReport.EncodingObservation(
                100, 50, 390, 780, 10_000, 1_000,
                2_000_000_000L, 20, 30);
        assertEquals(3.9, encoding.nodesPerObject());
        assertEquals(5.2, encoding.relationshipsPerLogicalFact());
        assertEquals(50.0, encoding.objectsPerSecond());
    }

    @Test
    void semanticGateRejectsFastButIncorrectBenchmark() {
        var incorrect = ScientificEvaluationReport.CorrectnessObservation.compare(
                "Company::HasEmployees", Set.of("c1", "c2"), Set.of("c1"), Set.of("c2"));
        var report = report(incorrect, true);

        assertEquals(0.0, report.exactMatchRate());
        assertFalse(report.correctnessGatePassed());
        assertFalse(report.performanceEvidenceAdmissible());
        assertFalse(report.passed());
    }

    @Test
    void passesAllEvaluationGroupsAndKeepsNotClaimedFeaturesExplicit() {
        var exact = ScientificEvaluationReport.CorrectnessObservation.compare(
                "Person::Adult", Set.of("p1", "p2"), Set.of("p1"), Set.of("p1"));
        var report = report(exact, true);

        assertEquals(1.0, report.exactMatchRate());
        assertEquals(1.0, report.conformanceRate());
        assertEquals(1.0, report.mutationScore());
        assertEquals(1.0, report.robustnessRate());
        assertEquals(1.0, report.admittedCoverage());
        assertTrue(report.performanceEvidenceAdmissible());
        assertTrue(report.passed());
        assertTrue(report.renderSummary().contains("result=PASS"));
    }

    private ScientificEvaluationReport.Report report(
            ScientificEvaluationReport.CorrectnessObservation correctness, boolean stable) {
        return new ScientificEvaluationReport.Report(
                List.of(correctness),
                List.of(
                        new ScientificEvaluationReport.ConformanceObservation("navigation",
                                ScientificEvaluationReport.Status.PASS, "real-graph-case"),
                        new ScientificEvaluationReport.ConformanceObservation("closure",
                                ScientificEvaluationReport.Status.NOT_CLAIMED, "outside OCL_val")),
                List.of(new ScientificEvaluationReport.MutationObservation("reverse-navigation",
                        ScientificEvaluationReport.MutationCategory.RENDERER, true,
                        "two objects and one link")),
                List.of(new ScientificEvaluationReport.RobustnessObservation("bad metamodel",
                        ScientificEvaluationReport.FailureStage.PARSE,
                        ScientificEvaluationReport.FailureStage.PARSE,
                        true, true, true)),
                List.of(new ScientificEvaluationReport.CoverageObservation("navigation", true,
                                true, true, true, true, true, true, true),
                        new ScientificEvaluationReport.CoverageObservation("closure", false,
                                false, false, false, false, false, false, false)),
                List.of(new ScientificEvaluationReport.EncodingObservation(
                        2, 1, 7, 10, 100, 20, 1_000, 100, 100)),
                List.of(new ScientificEvaluationReport.RepetitionObservation("Person::Adult",
                        List.of(10L, 11L), stable
                        ? List.of(Set.of("p1"), Set.of("p1"))
                        : List.of(Set.of("p1"), Set.of("p2")))),
                List.of(new ScientificEvaluationReport.DiagnosticObservation(
                        "Person::Adult", "p1", "p1.age", ">= 18", "17",
                        "MATCH (p) RETURN p", 10, "")),
                new ScientificEvaluationReport.ReproducibilityManifest(
                        "company-seed-42", 42, "model-hash", "ocl-hash", "canonical-v1",
                        "5.x", "5", "demo", "Windows", "Java 17", 5000, 5, 20));
    }
}
