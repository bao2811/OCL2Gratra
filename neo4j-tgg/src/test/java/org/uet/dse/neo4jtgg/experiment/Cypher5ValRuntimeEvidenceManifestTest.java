package org.uet.dse.neo4jtgg.experiment;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertThrows;

/** Static freshness and one-to-one coverage guard for selected-runtime CY1--CY9 evidence. */
class Cypher5ValRuntimeEvidenceManifestTest {
    private static final String MANIFEST = "verification/evidence/cypher5-val-runtime-2026-08-01.tsv";
    private static final String EVIDENCE_TEST =
            "Cypher5ValDialectRealNeo4jTest#selectedServerSatisfiesEveryRequiredDialectProbe";
    private static final List<String> IDS =
            List.of("CY1", "CY2", "CY3", "CY4", "CY5", "CY6", "CY7", "CY8", "CY9");

    @Test
    void checkedInEvidenceIsCurrentAndHasOnePassingRowPerAssumption() throws Exception {
        Path workspace = workspace();
        Evidence evidence = parse(Files.readAllLines(workspace.resolve(MANIFEST)));
        validate(evidence, workspace);
    }

    @Test
    void malformedDuplicateFailedAlteredAndStaleEvidenceAreRejected() throws Exception {
        Path workspace = workspace();
        List<String> original = Files.readAllLines(workspace.resolve(MANIFEST));

        List<String> duplicate = new ArrayList<>(original);
        duplicate.add(original.stream().filter(line -> line.startsWith("CY1\t")).findFirst().orElseThrow());
        assertThrows(IllegalStateException.class, () -> parse(duplicate));

        List<String> failed = replace(original, "CY4\t", "\tPASS\t", "\tFAIL\t");
        assertThrows(IllegalStateException.class, () -> validate(parse(failed), workspace));

        List<String> altered = replace(original, "CY5\t", "rows=4;", "rows=99;");
        assertThrows(IllegalStateException.class, () -> validate(parse(altered), workspace));

        List<String> stale = replace(original, "# rendererSha256\t", "F5990B", "000000");
        assertThrows(IllegalStateException.class, () -> validate(parse(stale), workspace));
    }

    private static void validate(Evidence evidence, Path workspace) throws Exception {
        Map<String, String> metadata = evidence.metadata();
        requireEquals("cypher5-val-runtime-evidence-v1", metadata.get("schema"), "schema");
        requireEquals("PC-2026-07-22.2", metadata.get("proofContract"), "proofContract");
        requireEquals(Cypher5ValAssumptionMatrix.NEO4J_KERNEL, metadata.get("neo4jKernel"), "neo4jKernel");
        requireEquals(Cypher5ValAssumptionMatrix.CYPHER_COMPONENT,
                metadata.get("cypherComponent"), "cypherComponent");
        requireEquals(Cypher5ValAssumptionMatrix.EDITION, metadata.get("edition"), "edition");
        requireEquals(Cypher5ValAssumptionMatrix.DATABASE, metadata.get("database"), "database");
        requireEquals(Cypher5ValAssumptionMatrix.JAVA_DRIVER_DEPENDENCY,
                metadata.get("javaDriverDependency"), "javaDriverDependency");
        requireEquals(Cypher5ValAssumptionMatrix.ENCODING_PROFILE,
                metadata.get("encodingProfile"), "encodingProfile");
        requireEquals("9/9", metadata.get("cypherAssumptionsPassed"), "cypherAssumptionsPassed");
        requireEquals("47/47", metadata.get("oclDifferentialPassed"), "oclDifferentialPassed");
        requireEquals(EvidenceSourceHash.MODE, metadata.get("sourceHashMode"), "sourceHashMode");
        requireMatches(metadata.get("executedAt"), "\\d{4}-\\d{2}-\\d{2}T.+[+-]\\d{2}:\\d{2}", "executedAt");
        requireMatches(metadata.get("gitCommit"), "[0-9a-f]{40}", "gitCommit");
        requireEquals("true", metadata.get("gitDirtyAtCapture"), "gitDirtyAtCapture");

        requireEquals(metadata.get("rendererSha256"), EvidenceSourceHash.sha256(workspace.resolve(
                "neo4j-tgg/src/main/java/org/uet/dse/neo4jtgg/ocl/ir/OclCypherRenderer.java")),
                "rendererSha256");
        requireEquals(metadata.get("encodingSha256"), EvidenceSourceHash.sha256(workspace.resolve(
                "neo4j/src/main/java/org/uet/dse/neo4j/encoding/CanonicalGraphEncoding.java")),
                "encodingSha256");
        requireEquals(metadata.get("matrixSha256"), EvidenceSourceHash.sha256(workspace.resolve(
                "neo4j-tgg/src/test/java/org/uet/dse/neo4jtgg/experiment/Cypher5ValAssumptionMatrix.java")),
                "matrixSha256");
        requireEquals(metadata.get("runtimeTestSha256"), EvidenceSourceHash.sha256(workspace.resolve(
                "neo4j-tgg/src/test/java/org/uet/dse/neo4jtgg/experiment/Cypher5ValDialectRealNeo4jTest.java")),
                "runtimeTestSha256");
        requireEquals(Cypher5ValAssumptionMatrix.JAVA_DRIVER_DEPENDENCY,
                dependencyVersion(workspace.resolve("neo4j/pom.xml"), "neo4j-java-driver"),
                "neo4j-java-driver dependency");

        Map<String, Cypher5ValAssumptionMatrix.Probe> probes = new LinkedHashMap<>();
        Cypher5ValAssumptionMatrix.probes().forEach(probe -> probes.put(probe.id(), probe));
        requireEquals(IDS, new ArrayList<>(evidence.rows().keySet()), "CY row IDs/order");
        requireEquals(IDS, new ArrayList<>(probes.keySet()), "executable probe IDs/order");
        for (String id : IDS) {
            Row row = evidence.rows().get(id);
            Cypher5ValAssumptionMatrix.Probe probe = probes.get(id);
            requireEquals(probe.assumption(), row.assumption(), id + " assumption");
            requireEquals(probe.expected(), row.expected(), id + " expected behavior");
            requireEquals("PASS", row.status(), id + " status");
            requireEquals(probe.expectedObservation(), row.observed(), id + " observed behavior");
            requireEquals(EVIDENCE_TEST, row.evidenceTest(), id + " evidence test");
        }
    }

    private static Evidence parse(List<String> lines) {
        Map<String, String> metadata = new LinkedHashMap<>();
        Map<String, Row> rows = new LinkedHashMap<>();
        boolean headerSeen = false;
        for (String line : lines) {
            if (line.isBlank()) continue;
            if (line.startsWith("# ")) {
                String[] fields = line.substring(2).split("\\t", -1);
                if (fields.length != 2 || fields[0].isBlank() || fields[1].isBlank()) {
                    throw new IllegalStateException("Malformed evidence metadata: " + line);
                }
                if (metadata.put(fields[0], fields[1]) != null) {
                    throw new IllegalStateException("Duplicate evidence metadata: " + fields[0]);
                }
                continue;
            }
            if (!headerSeen) {
                requireEquals("id\tassumption\texpected\tstatus\tobserved\tevidenceTest", line, "header");
                headerSeen = true;
                continue;
            }
            String[] fields = line.split("\\t", -1);
            if (fields.length != 6) throw new IllegalStateException("Malformed evidence row: " + line);
            Row row = new Row(fields[0], fields[1], fields[2], fields[3], fields[4], fields[5]);
            if (rows.put(row.id(), row) != null) throw new IllegalStateException("Duplicate CY row: " + row.id());
        }
        if (!headerSeen) throw new IllegalStateException("Missing evidence header");
        return new Evidence(Collections.unmodifiableMap(new LinkedHashMap<>(metadata)),
                Collections.unmodifiableMap(new LinkedHashMap<>(rows)));
    }

    private static List<String> replace(List<String> source, String prefix, String from, String to) {
        List<String> result = new ArrayList<>(source);
        for (int i = 0; i < result.size(); i++) {
            String line = result.get(i);
            if (line.startsWith(prefix)) {
                result.set(i, line.replace(from, to));
                return result;
            }
        }
        throw new IllegalStateException("Missing mutation target: " + prefix);
    }

    private static String dependencyVersion(Path pom, String artifactId) throws IOException {
        Pattern pattern = Pattern.compile("<artifactId>" + Pattern.quote(artifactId)
                + "</artifactId>\\s*<version>([^<]+)</version>");
        var matcher = pattern.matcher(Files.readString(pom));
        if (!matcher.find()) throw new IllegalStateException("Missing dependency version: " + artifactId);
        return matcher.group(1).trim();
    }

    private static Path workspace() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve(
                    "verification/contract/proof-contract-registry.json"))) return current;
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate workspace root");
    }

    private static void requireEquals(Object expected, Object actual, String field) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new IllegalStateException(field + ": expected <" + expected + "> but was <" + actual + ">");
        }
    }

    private static void requireMatches(String actual, String regex, String field) {
        if (actual == null || !actual.matches(regex)) {
            throw new IllegalStateException(field + " does not match " + regex + ": " + actual);
        }
    }

    private record Evidence(Map<String, String> metadata, Map<String, Row> rows) {
    }

    private record Row(String id, String assumption, String expected, String status,
                       String observed, String evidenceTest) {
    }
}
