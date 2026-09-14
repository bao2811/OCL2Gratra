package org.uet.dse.neo4jtgg.experiment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

/** Deterministic, dependency-free serialization for reproducible experiments. */
public final class ScientificEvaluationReportWriter {
    public static final String SCHEMA_VERSION = "ocl2cypher-evidence-v1";

    private ScientificEvaluationReportWriter() {
    }

    public static void writeJson(ScientificEvaluationReport.Report report, Path destination)
            throws IOException {
        write(destination, toJson(report));
    }

    public static void writeCorrectnessCsv(ScientificEvaluationReport.Report report, Path destination)
            throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("invariant,reference_ids,cypher_ids,missing_ids,spurious_ids,exact,precision,recall,accuracy");
        for (var observation : report.correctness()) {
            var differential = observation.differential();
            lines.add(csv(observation.invariant()) + ','
                    + csv(ids(differential.referenceIds())) + ','
                    + csv(ids(differential.cypherIds())) + ','
                    + csv(ids(differential.referenceOnlyIds())) + ','
                    + csv(ids(differential.cypherOnlyIds())) + ','
                    + observation.exactMatch() + ','
                    + decimal(observation.precision()) + ','
                    + decimal(observation.recall()) + ','
                    + decimal(observation.accuracy()));
        }
        write(destination, String.join(System.lineSeparator(), lines) + System.lineSeparator());
    }

    public static String toJson(ScientificEvaluationReport.Report report) {
        StringBuilder json = new StringBuilder(4096);
        json.append("{\n  \"schemaVersion\":").append(quote(SCHEMA_VERSION))
                .append(",\n  \"summary\":").append(quote(report.renderSummary()))
                .append(",\n  \"passed\":").append(report.passed())
                .append(",\n  \"performanceEvidenceAdmissible\":")
                .append(report.performanceEvidenceAdmissible())
                .append(",\n  \"manifest\":").append(manifest(report.manifest()))
                .append(",\n  \"correctness\":");
        appendArray(json, report.correctness(), ScientificEvaluationReportWriter::correctness);
        json.append(",\n  \"conformance\":");
        appendArray(json, report.conformance(), value -> object(
                "feature", value.feature(), "status", value.status().name(), "evidence", value.evidence()));
        json.append(",\n  \"mutations\":");
        appendArray(json, report.mutations(), value -> object(
                "mutant", value.mutant(), "category", value.category().name(),
                "detected", value.detected(), "counterexample", value.smallestCounterexample()));
        json.append(",\n  \"robustness\":");
        appendArray(json, report.robustness(), value -> object(
                "scenario", value.scenario(), "expectedStage", value.expectedStage().name(),
                "actualStage", value.actualStage().name(), "passed", value.passed()));
        json.append(",\n  \"coverage\":");
        appendArray(json, report.coverage(), value -> object(
                "feature", value.feature(), "admitted", value.admitted(),
                "typing", value.typing(), "binding", value.binding(),
                "vaTranslation", value.vaTranslation(), "normalization", value.normalization(),
                "cypherRealization", value.cypherRealization(), "oracleCase", value.oracleCase(),
                "realGraphCase", value.realGraphCase(), "complete", value.complete()));
        json.append(",\n  \"encoding\":");
        appendArray(json, report.encoding(), value -> object(
                "logicalObjects", value.logicalObjects(), "logicalLinks", value.logicalLinks(),
                "physicalNodes", value.physicalNodes(), "physicalRelationships", value.physicalRelationships(),
                "databaseBytes", value.databaseBytes(), "indexBytes", value.indexBytes(),
                "encodingNs", value.encodingNs(), "indexNs", value.indexNs(), "cleanupNs", value.cleanupNs()));
        json.append(",\n  \"repetitions\":");
        appendArray(json, report.repetitions(), value -> object(
                "artifact", value.artifact(), "elapsedNs", value.elapsedNs(),
                "returnedIdSets", value.returnedIdSets().stream().map(ScientificEvaluationReportWriter::sorted).toList(),
                "medianNs", value.medianNs(), "p95Ns", value.p95Ns(), "stableIds", value.stableIds()));
        json.append(",\n  \"diagnostics\":");
        appendArray(json, report.diagnostics(), value -> object(
                "invariant", value.invariant(), "stableId", value.stableId(),
                "witnessPath", value.witnessPath(), "expected", value.expected(),
                "actual", value.actual(), "cypher", value.cypher(),
                "queryNs", value.queryNs(), "error", value.error()));
        return json.append("\n}\n").toString();
    }

    private static String correctness(ScientificEvaluationReport.CorrectnessObservation value) {
        DifferentialResult differential = value.differential();
        return object("invariant", value.invariant(), "universeIds", sorted(value.universeIds()),
                "referenceIds", sorted(differential.referenceIds()),
                "cypherIds", sorted(differential.cypherIds()),
                "missingIds", sorted(differential.referenceOnlyIds()),
                "spuriousIds", sorted(differential.cypherOnlyIds()),
                "exact", value.exactMatch(), "precision", value.precision(),
                "recall", value.recall(), "accuracy", value.accuracy());
    }

    private static String manifest(ScientificEvaluationReport.ReproducibilityManifest value) {
        return object("datasetId", value.datasetId(), "seed", value.seed(),
                "metamodelSha256", value.metamodelSha256(),
                "constraintsSha256", value.constraintsSha256(),
                "encodingVersion", value.encodingVersion(), "neo4jVersion", value.neo4jVersion(),
                "cypherVersion", value.cypherVersion(), "database", value.database(),
                "operatingSystem", value.operatingSystem(), "jvm", value.jvm(),
                "batchSize", value.batchSize(), "warmups", value.warmups(),
                "repetitions", value.repetitions());
    }

    private static <T> void appendArray(StringBuilder target, List<T> values,
                                        Function<T, String> serializer) {
        target.append('[');
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) target.append(',');
            target.append("\n    ").append(serializer.apply(values.get(index)));
        }
        if (!values.isEmpty()) target.append('\n').append("  ");
        target.append(']');
    }

    private static String object(Object... entries) {
        StringBuilder result = new StringBuilder("{");
        for (int index = 0; index < entries.length; index += 2) {
            if (index > 0) result.append(',');
            result.append(quote(String.valueOf(entries[index]))).append(':')
                    .append(jsonValue(entries[index + 1]));
        }
        return result.append('}').toString();
    }

    private static String jsonValue(Object value) {
        if (value == null) return "null";
        if (value instanceof Boolean || value instanceof Byte || value instanceof Short
                || value instanceof Integer || value instanceof Long) return value.toString();
        if (value instanceof Float || value instanceof Double) {
            return String.format(Locale.ROOT, "%.10f", ((Number) value).doubleValue());
        }
        if (value instanceof Iterable<?> iterable) {
            List<String> elements = new ArrayList<>();
            iterable.forEach(item -> elements.add(jsonValue(item)));
            return '[' + String.join(",", elements) + ']';
        }
        return quote(String.valueOf(value));
    }

    private static List<String> sorted(Set<String> values) {
        return values.stream().sorted(Comparator.naturalOrder()).toList();
    }

    private static String ids(Set<String> values) {
        return String.join(";", sorted(values));
    }

    private static String csv(String value) {
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private static String decimal(double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }

    private static String quote(String value) {
        StringBuilder result = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            switch (current) {
                case '\\' -> result.append("\\\\");
                case '"' -> result.append("\\\"");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> {
                    if (current < 0x20) result.append(String.format(Locale.ROOT, "\\u%04x", (int) current));
                    else result.append(current);
                }
            }
        }
        return result.append('"').toString();
    }

    private static void write(Path destination, String content) throws IOException {
        Path absolute = destination.toAbsolutePath().normalize();
        Path parent = absolute.getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.writeString(absolute, content, StandardCharsets.UTF_8);
    }
}
