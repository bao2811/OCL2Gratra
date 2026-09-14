package org.uet.dse.neo4jtgg.experiment;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PipelineEvaluationMetricsTest {
    @Test
    void computesCoverageAndNegativeAcceptanceMetrics() {
        var report = new PipelineEvaluationMetrics.Report(
                List.of(new PipelineEvaluationMetrics.ConstructorCoverage("navigation", true),
                        new PipelineEvaluationMetrics.ConstructorCoverage("closure", false)),
                List.of(new PipelineEvaluationMetrics.RuleCoverage("navigation", true, true, true,
                        true, true, true, true)),
                List.of(new PipelineEvaluationMetrics.RejectionCase("unknown-class", true,
                        "BINDING", "BINDING"),
                        new PipelineEvaluationMetrics.RejectionCase("ambiguous-navigation", true,
                                "BINDING", "TYPE")),
                List.of(new PipelineEvaluationMetrics.DeterminismCheck("cypher", 3, true)),
                List.of(new PipelineEvaluationMetrics.IncrementalObservation("add-object", 4, 2_000_000)));

        assertEquals(0.5, report.constructorCoverage());
        assertEquals(1.0, report.completeRuleCoverage());
        assertEquals(1.0, report.rejectionRate());
        assertEquals(0.5, report.diagnosticAccuracy());
        assertEquals(1.0, report.determinismRate());
        assertFalse(report.passed());
        assertTrue(report.toCsv("e4-e6").contains("diagnosticAccuracy"));
    }

    @Test
    void acceptsCompleteReproducibleEvidence() {
        var report = new PipelineEvaluationMetrics.Report(
                List.of(new PipelineEvaluationMetrics.ConstructorCoverage("literal", true)),
                List.of(new PipelineEvaluationMetrics.RuleCoverage("literal", true, true, true,
                        true, true, true, true)),
                List.of(new PipelineEvaluationMetrics.RejectionCase("unsupported", true,
                        "ADMISSION", "ADMISSION")),
                List.of(new PipelineEvaluationMetrics.DeterminismCheck("bound", 5, true),
                        new PipelineEvaluationMetrics.DeterminismCheck("cypher", 5, true)),
                List.of());

        assertTrue(report.passed());
    }
}
