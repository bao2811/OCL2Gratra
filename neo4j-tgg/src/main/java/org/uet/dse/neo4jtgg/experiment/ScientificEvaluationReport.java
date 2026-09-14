package org.uet.dse.neo4jtgg.experiment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * A single, dependency-free evaluation model for the empirical claims of the
 * OCL-to-Cypher research prototype.  Performance is deliberately gated by
 * semantic agreement: a fast run with different violation IDs is not a valid
 * benchmark observation.
 */
public final class ScientificEvaluationReport {
    private ScientificEvaluationReport() {
    }

    public enum Status { PASS, FAIL, NOT_CLAIMED }

    public enum MutationCategory { DATA, COMPILER, RENDERER }

    public enum FailureStage {
        INPUT, PARSE, BIND, VA_BUILD, NORMALIZE, PLAN, RENDER,
        CONNECT, LOAD, EXECUTE, COMPARE, CLEANUP, NONE
    }

    public record CorrectnessObservation(
            String invariant,
            Set<String> universeIds,
            DifferentialResult differential,
            long truePositive,
            long trueNegative,
            long falsePositive,
            long falseNegative) {

        public CorrectnessObservation {
            requireText(invariant, "invariant");
            universeIds = Set.copyOf(universeIds);
            Objects.requireNonNull(differential, "differential");
            if (!universeIds.containsAll(differential.referenceIds())
                    || !universeIds.containsAll(differential.cypherIds())) {
                throw new IllegalArgumentException("universe must contain every observed stable ID");
            }
            requireNonNegative(truePositive, "truePositive");
            requireNonNegative(trueNegative, "trueNegative");
            requireNonNegative(falsePositive, "falsePositive");
            requireNonNegative(falseNegative, "falseNegative");
            if (truePositive + trueNegative + falsePositive + falseNegative != universeIds.size()) {
                throw new IllegalArgumentException("confusion matrix does not match universe size");
            }
        }

        public static CorrectnessObservation compare(String invariant,
                                                     Set<String> universeIds,
                                                     Set<String> referenceIds,
                                                     Set<String> cypherIds) {
            Set<String> universe = Set.copyOf(universeIds);
            DifferentialResult differential = DifferentialResult.compare(referenceIds, cypherIds);
            Set<String> intersection = new LinkedHashSet<>(referenceIds);
            intersection.retainAll(cypherIds);
            Set<String> union = new LinkedHashSet<>(referenceIds);
            union.addAll(cypherIds);
            return new CorrectnessObservation(invariant, universe, differential,
                    intersection.size(), universe.size() - union.size(),
                    differential.cypherOnlyIds().size(), differential.referenceOnlyIds().size());
        }

        public boolean exactMatch() {
            return differential.equalIds();
        }

        public double precision() {
            long predictedPositive = truePositive + falsePositive;
            return predictedPositive == 0 ? (falseNegative == 0 ? 1.0 : 0.0)
                    : (double) truePositive / predictedPositive;
        }

        public double recall() {
            long referencePositive = truePositive + falseNegative;
            return referencePositive == 0 ? (falsePositive == 0 ? 1.0 : 0.0)
                    : (double) truePositive / referencePositive;
        }

        public double accuracy() {
            return universeIds.isEmpty() ? 1.0
                    : (double) (truePositive + trueNegative) / universeIds.size();
        }
    }

    public record ConformanceObservation(String feature, Status status, String evidence) {
        public ConformanceObservation {
            requireText(feature, "feature");
            Objects.requireNonNull(status, "status");
            requireText(evidence, "evidence");
        }
    }

    public record MutationObservation(String mutant, MutationCategory category,
                                      boolean detected, String smallestCounterexample) {
        public MutationObservation {
            requireText(mutant, "mutant");
            Objects.requireNonNull(category, "category");
            requireText(smallestCounterexample, "smallestCounterexample");
        }
    }

    public record RobustnessObservation(String scenario,
                                        FailureStage expectedStage,
                                        FailureStage actualStage,
                                        boolean databaseSafe,
                                        boolean diagnosticClear,
                                        boolean incompleteResultWithheld) {
        public RobustnessObservation {
            requireText(scenario, "scenario");
            Objects.requireNonNull(expectedStage, "expectedStage");
            Objects.requireNonNull(actualStage, "actualStage");
        }

        public boolean passed() {
            return expectedStage == actualStage && databaseSafe
                    && diagnosticClear && incompleteResultWithheld;
        }
    }

    /** Coverage is complete only when the construct is connected end-to-end. */
    public record CoverageObservation(String feature, boolean admitted,
                                      boolean typing, boolean binding,
                                      boolean vaTranslation, boolean normalization,
                                      boolean cypherRealization, boolean oracleCase,
                                      boolean realGraphCase) {
        public CoverageObservation {
            requireText(feature, "feature");
        }

        public boolean complete() {
            return admitted && typing && binding && vaTranslation && normalization
                    && cypherRealization && oracleCase && realGraphCase;
        }
    }

    public record EncodingObservation(long logicalObjects, long logicalLinks,
                                      long physicalNodes, long physicalRelationships,
                                      long databaseBytes, long indexBytes,
                                      long encodingNs, long indexNs, long cleanupNs) {
        public EncodingObservation {
            requireNonNegative(logicalObjects, "logicalObjects");
            requireNonNegative(logicalLinks, "logicalLinks");
            requireNonNegative(physicalNodes, "physicalNodes");
            requireNonNegative(physicalRelationships, "physicalRelationships");
            requireNonNegative(databaseBytes, "databaseBytes");
            requireNonNegative(indexBytes, "indexBytes");
            requireNonNegative(encodingNs, "encodingNs");
            requireNonNegative(indexNs, "indexNs");
            requireNonNegative(cleanupNs, "cleanupNs");
        }

        public double nodesPerObject() {
            return ratio(physicalNodes, logicalObjects);
        }

        public double relationshipsPerLogicalFact() {
            return ratio(physicalRelationships, logicalObjects + logicalLinks);
        }

        public double objectsPerSecond() {
            return encodingNs == 0 ? 0.0 : logicalObjects * 1_000_000_000.0 / encodingNs;
        }
    }

    public record RepetitionObservation(String artifact, List<Long> elapsedNs,
                                        List<Set<String>> returnedIdSets) {
        public RepetitionObservation {
            requireText(artifact, "artifact");
            elapsedNs = List.copyOf(elapsedNs);
            if (elapsedNs.isEmpty()) throw new IllegalArgumentException("elapsedNs must not be empty");
            elapsedNs.forEach(value -> requireNonNegative(value, "elapsedNs"));
            List<Set<String>> copied = new ArrayList<>();
            returnedIdSets.forEach(ids -> copied.add(Set.copyOf(ids)));
            returnedIdSets = List.copyOf(copied);
            if (elapsedNs.size() != returnedIdSets.size()) {
                throw new IllegalArgumentException("each timing needs one returned-ID set");
            }
        }

        public long medianNs() {
            return percentile(0.50);
        }

        public long p95Ns() {
            return percentile(0.95);
        }

        public double standardDeviationNs() {
            double mean = elapsedNs.stream().mapToLong(Long::longValue).average().orElse(0.0);
            double variance = elapsedNs.stream()
                    .mapToDouble(value -> (value - mean) * (value - mean))
                    .average().orElse(0.0);
            return Math.sqrt(variance);
        }

        public boolean stableIds() {
            Set<String> first = returnedIdSets.get(0);
            return returnedIdSets.stream().allMatch(first::equals);
        }

        private long percentile(double fraction) {
            List<Long> sorted = new ArrayList<>(elapsedNs);
            Collections.sort(sorted);
            int index = Math.max(0, (int) Math.ceil(sorted.size() * fraction) - 1);
            return sorted.get(index);
        }
    }

    public record DiagnosticObservation(String invariant, String stableId,
                                        String witnessPath, String expected,
                                        String actual, String cypher,
                                        long queryNs, String error) {
        public DiagnosticObservation {
            requireText(invariant, "invariant");
            requireText(stableId, "stableId");
            requireText(witnessPath, "witnessPath");
            requireText(expected, "expected");
            requireText(actual, "actual");
            requireText(cypher, "cypher");
            requireNonNegative(queryNs, "queryNs");
            error = error == null ? "" : error;
        }

        public boolean complete() {
            return !invariant.isBlank() && !stableId.isBlank() && !witnessPath.isBlank()
                    && !expected.isBlank() && !actual.isBlank() && !cypher.isBlank();
        }
    }

    public record ReproducibilityManifest(String datasetId, long seed,
                                          String metamodelSha256,
                                          String constraintsSha256,
                                          String encodingVersion,
                                          String neo4jVersion,
                                          String cypherVersion,
                                          String database,
                                          String operatingSystem,
                                          String jvm,
                                          int batchSize,
                                          int warmups,
                                          int repetitions) {
        public ReproducibilityManifest {
            requireText(datasetId, "datasetId");
            requireText(metamodelSha256, "metamodelSha256");
            requireText(constraintsSha256, "constraintsSha256");
            requireText(encodingVersion, "encodingVersion");
            requireText(neo4jVersion, "neo4jVersion");
            requireText(cypherVersion, "cypherVersion");
            requireText(database, "database");
            requireText(operatingSystem, "operatingSystem");
            requireText(jvm, "jvm");
            if (batchSize <= 0 || warmups < 0 || repetitions <= 0) {
                throw new IllegalArgumentException("invalid execution configuration");
            }
        }
    }

    public record Report(List<CorrectnessObservation> correctness,
                         List<ConformanceObservation> conformance,
                         List<MutationObservation> mutations,
                         List<RobustnessObservation> robustness,
                         List<CoverageObservation> coverage,
                         List<EncodingObservation> encoding,
                         List<RepetitionObservation> repetitions,
                         List<DiagnosticObservation> diagnostics,
                         ReproducibilityManifest manifest) {
        public Report {
            correctness = List.copyOf(correctness);
            conformance = List.copyOf(conformance);
            mutations = List.copyOf(mutations);
            robustness = List.copyOf(robustness);
            coverage = List.copyOf(coverage);
            encoding = List.copyOf(encoding);
            repetitions = List.copyOf(repetitions);
            diagnostics = List.copyOf(diagnostics);
            Objects.requireNonNull(manifest, "manifest");
        }

        public double exactMatchRate() {
            return ratio(correctness.stream().filter(CorrectnessObservation::exactMatch).count(),
                    correctness.size());
        }

        public double conformanceRate() {
            long claimed = conformance.stream().filter(item -> item.status() != Status.NOT_CLAIMED).count();
            long passed = conformance.stream().filter(item -> item.status() == Status.PASS).count();
            return ratio(passed, claimed);
        }

        public double mutationScore() {
            return ratio(mutations.stream().filter(MutationObservation::detected).count(), mutations.size());
        }

        public double robustnessRate() {
            return ratio(robustness.stream().filter(RobustnessObservation::passed).count(), robustness.size());
        }

        public double admittedCoverage() {
            long admitted = coverage.stream().filter(CoverageObservation::admitted).count();
            long complete = coverage.stream().filter(CoverageObservation::complete).count();
            return ratio(complete, admitted);
        }

        public boolean correctnessGatePassed() {
            return !correctness.isEmpty() && correctness.stream().allMatch(CorrectnessObservation::exactMatch);
        }

        /** Performance claims are publishable only after the semantic gate. */
        public boolean performanceEvidenceAdmissible() {
            return correctnessGatePassed()
                    && repetitions.stream().allMatch(RepetitionObservation::stableIds);
        }

        public boolean passed() {
            return correctnessGatePassed()
                    && conformance.stream().noneMatch(item -> item.status() == Status.FAIL)
                    && !mutations.isEmpty() && mutations.stream().allMatch(MutationObservation::detected)
                    && robustness.stream().allMatch(RobustnessObservation::passed)
                    && coverage.stream().filter(CoverageObservation::admitted)
                    .allMatch(CoverageObservation::complete)
                    && repetitions.stream().allMatch(RepetitionObservation::stableIds)
                    && diagnostics.stream().allMatch(DiagnosticObservation::complete);
        }

        public String renderSummary() {
            return String.format(Locale.ROOT,
                    "correctnessExact=%.4f conformance=%.4f mutationScore=%.4f "
                            + "robustness=%.4f admittedCoverage=%.4f stableIds=%s "
                            + "performanceAdmissible=%s result=%s",
                    exactMatchRate(), conformanceRate(), mutationScore(), robustnessRate(),
                    admittedCoverage(), repetitions.stream().allMatch(RepetitionObservation::stableIds),
                    performanceEvidenceAdmissible(), passed() ? "PASS" : "FAIL");
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }

    private static void requireNonNegative(long value, String name) {
        if (value < 0) throw new IllegalArgumentException(name + " must be non-negative");
    }

    private static double ratio(long numerator, long denominator) {
        return denominator == 0 ? 0.0 : (double) numerator / denominator;
    }
}
