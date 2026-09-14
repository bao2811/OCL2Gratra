package org.uet.dse.neo4jtgg.experiment;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Metrics for E4--E6. The records are deliberately independent of a parser or
 * database so that unit and integration experiments can use the same report.
 */
public final class PipelineEvaluationMetrics {
    private PipelineEvaluationMetrics() {
    }

    public record ConstructorCoverage(String constructor, boolean supported) {
        public ConstructorCoverage {
            Objects.requireNonNull(constructor);
        }
    }

    public record RuleCoverage(
            String constructor,
            boolean typing,
            boolean binding,
            boolean vaTranslation,
            boolean normalization,
            boolean cypherRealization,
            boolean proofCase,
            boolean testCase) {
        public RuleCoverage {
            Objects.requireNonNull(constructor);
        }

        public boolean complete() {
            return typing && binding && vaTranslation && normalization
                    && cypherRealization && proofCase && testCase;
        }
    }

    public record RejectionCase(
            String name,
            boolean rejected,
            String expectedDiagnostic,
            String actualDiagnostic) {
        public RejectionCase {
            Objects.requireNonNull(name);
            Objects.requireNonNull(expectedDiagnostic);
            Objects.requireNonNull(actualDiagnostic);
        }

        public boolean passed() {
            return rejected && expectedDiagnostic.equals(actualDiagnostic);
        }
    }

    public record DeterminismCheck(String artifact, int runs, boolean equivalent) {
        public DeterminismCheck {
            Objects.requireNonNull(artifact);
            if (runs < 2) throw new IllegalArgumentException("runs must be at least 2");
        }
    }

    public record IncrementalObservation(String operation, long changedFacts, long elapsedNs) {
        public IncrementalObservation {
            Objects.requireNonNull(operation);
            if (changedFacts < 0 || elapsedNs < 0) throw new IllegalArgumentException("negative metric");
        }
    }

    public record Report(
            List<ConstructorCoverage> constructors,
            List<RuleCoverage> rules,
            List<RejectionCase> rejectionCases,
            List<DeterminismCheck> determinismChecks,
            List<IncrementalObservation> incrementalObservations) {
        public Report {
            constructors = List.copyOf(constructors);
            rules = List.copyOf(rules);
            rejectionCases = List.copyOf(rejectionCases);
            determinismChecks = List.copyOf(determinismChecks);
            incrementalObservations = List.copyOf(incrementalObservations);
        }

        public double constructorCoverage() {
            return ratio(constructors.stream().filter(ConstructorCoverage::supported).count(), constructors.size());
        }

        public double completeRuleCoverage() {
            return ratio(rules.stream().filter(RuleCoverage::complete).count(), rules.size());
        }

        public double rejectionRate() {
            return ratio(rejectionCases.stream().filter(RejectionCase::rejected).count(), rejectionCases.size());
        }

        public double diagnosticAccuracy() {
            return ratio(rejectionCases.stream().filter(RejectionCase::passed).count(), rejectionCases.size());
        }

        public double determinismRate() {
            return ratio(determinismChecks.stream().filter(DeterminismCheck::equivalent).count(), determinismChecks.size());
        }

        public boolean passed() {
            return constructorCoverage() == 1.0
                    && completeRuleCoverage() == 1.0
                    && rejectionCases.stream().allMatch(RejectionCase::passed)
                    && determinismChecks.stream().allMatch(DeterminismCheck::equivalent);
        }

        public String toCsv(String dataset) {
            StringBuilder out = new StringBuilder("dataset,metric,value\n");
            row(out, dataset, "constructorCoverage", constructorCoverage());
            row(out, dataset, "completeRuleCoverage", completeRuleCoverage());
            row(out, dataset, "rejectionRate", rejectionRate());
            row(out, dataset, "diagnosticAccuracy", diagnosticAccuracy());
            row(out, dataset, "determinismRate", determinismRate());
            if (!incrementalObservations.isEmpty()) {
                double averageMs = incrementalObservations.stream().mapToLong(IncrementalObservation::elapsedNs)
                        .average().orElse(0.0) / 1_000_000.0;
                row(out, dataset, "incrementalAverageMs", averageMs);
                row(out, dataset, "incrementalChangedFacts", incrementalObservations.stream()
                        .mapToLong(IncrementalObservation::changedFacts).average().orElse(0.0));
            }
            return out.toString();
        }

        private static void row(StringBuilder out, String dataset, String metric, double value) {
            out.append(csv(dataset)).append(',').append(csv(metric)).append(',')
                    .append(String.format(Locale.ROOT, "%.6f", value)).append('\n');
        }

        private static String csv(String value) {
            return "\"" + Objects.requireNonNull(value).replace("\"", "\"\"") + "\"";
        }

        private static double ratio(long numerator, long denominator) {
            return denominator == 0 ? 0.0 : (double) numerator / denominator;
        }
    }
}
