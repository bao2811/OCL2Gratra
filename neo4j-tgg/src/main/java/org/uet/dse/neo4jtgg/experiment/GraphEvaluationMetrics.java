package org.uet.dse.neo4jtgg.experiment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Portable E2/E3 metrics; database-specific collectors populate this record. */
public record GraphEvaluationMetrics(
        long sourceMetamodelElements,
        long sourceObjects,
        long sourceAttributeSlots,
        long sourceLinks,
        long graphNodes,
        long graphRelationships,
        long graphProperties,
        long propertyPayloadChars,
        long maxTypeNodeDegree,
        long encodingNs,
        Map<String, LatencySummary> primitiveLatencies) {

    public GraphEvaluationMetrics {
        requireNonNegative(sourceMetamodelElements, "sourceMetamodelElements");
        requireNonNegative(sourceObjects, "sourceObjects");
        requireNonNegative(sourceAttributeSlots, "sourceAttributeSlots");
        requireNonNegative(sourceLinks, "sourceLinks");
        requireNonNegative(graphNodes, "graphNodes");
        requireNonNegative(graphRelationships, "graphRelationships");
        requireNonNegative(graphProperties, "graphProperties");
        requireNonNegative(propertyPayloadChars, "propertyPayloadChars");
        requireNonNegative(maxTypeNodeDegree, "maxTypeNodeDegree");
        requireNonNegative(encodingNs, "encodingNs");
        primitiveLatencies = Map.copyOf(primitiveLatencies);
    }

    public double nodeExpansion() {
        long denominator = sourceMetamodelElements + sourceObjects;
        return denominator == 0 ? 0.0 : (double) graphNodes / denominator;
    }

    public double relationshipExpansion() {
        long denominator = sourceObjects + sourceAttributeSlots + sourceLinks;
        return denominator == 0 ? 0.0 : (double) graphRelationships / denominator;
    }

    public double encodingObjectsPerSecond() {
        return encodingNs == 0 ? 0.0 : sourceObjects * 1_000_000_000.0 / encodingNs;
    }

    public String toCsvRow(String dataset, String profile) {
        return String.format(Locale.ROOT,
                "%s,%s,%d,%d,%d,%d,%d,%d,%d,%d,%d,%.6f,%.6f,%.3f",
                csv(dataset), csv(profile), sourceMetamodelElements, sourceObjects,
                sourceAttributeSlots, sourceLinks, graphNodes, graphRelationships,
                graphProperties, propertyPayloadChars, maxTypeNodeDegree,
                nodeExpansion(), relationshipExpansion(), encodingObjectsPerSecond());
    }

    public static String csvHeader() {
        return "dataset,profile,sourceMetamodelElements,sourceObjects,sourceAttributeSlots,sourceLinks,"
                + "graphNodes,graphRelationships,graphProperties,propertyPayloadChars,maxTypeNodeDegree,"
                + "nodeExpansion,relationshipExpansion,encodingObjectsPerSecond";
    }

    private static String csv(String value) {
        String escaped = Objects.requireNonNull(value).replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

    private static void requireNonNegative(long value, String name) {
        if (value < 0) throw new IllegalArgumentException(name + " must be non-negative");
    }

    public record LatencySummary(int samples, long minNs, long medianNs, long p95Ns, long maxNs) {
        public LatencySummary {
            if (samples <= 0 || minNs < 0 || medianNs < 0 || p95Ns < 0 || maxNs < 0) {
                throw new IllegalArgumentException("invalid latency summary");
            }
        }

        public static LatencySummary of(List<Long> nanoseconds) {
            if (nanoseconds == null || nanoseconds.isEmpty()) {
                throw new IllegalArgumentException("at least one latency sample is required");
            }
            List<Long> sorted = new ArrayList<>(nanoseconds);
            sorted.forEach(value -> requireNonNegative(value, "latency"));
            Collections.sort(sorted);
            return new LatencySummary(sorted.size(), sorted.get(0), percentile(sorted, 0.50),
                    percentile(sorted, 0.95), sorted.get(sorted.size() - 1));
        }

        private static long percentile(List<Long> sorted, double percentile) {
            int index = (int) Math.ceil(percentile * sorted.size()) - 1;
            return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
        }
    }

    public static final class LatencyCollector {
        private final Map<String, List<Long>> samples = new LinkedHashMap<>();

        public void add(String observation, long elapsedNs) {
            requireNonNegative(elapsedNs, "elapsedNs");
            samples.computeIfAbsent(Objects.requireNonNull(observation), ignored -> new ArrayList<>())
                    .add(elapsedNs);
        }

        public Map<String, LatencySummary> summarize() {
            Map<String, LatencySummary> result = new LinkedHashMap<>();
            samples.forEach((name, values) -> result.put(name, LatencySummary.of(values)));
            return Map.copyOf(result);
        }
    }
}
