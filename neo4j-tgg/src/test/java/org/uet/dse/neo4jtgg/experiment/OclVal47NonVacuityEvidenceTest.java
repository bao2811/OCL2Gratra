package org.uet.dse.neo4jtgg.experiment;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertThrows;

/** Freshness and exact-result guard for the real-Neo4j OCL_val-47 non-vacuity run. */
class OclVal47NonVacuityEvidenceTest {
    private static final String EVIDENCE =
            "verification/evidence/oclval47-nonvacuity-runtime-2026-08-01.tsv";
    private static final String RUNTIME_TEST =
            "OclValRealNeo4jCoverageTest#allAdmittedConstructsAgreeWithUseOnRealNeo4j";

    @Test
    void checkedInRuntimeRowsExactlyMatchReviewedExpectationsAndCurrentSources() throws Exception {
        Path workspace = workspace();
        validate(parse(Files.readAllLines(workspace.resolve(EVIDENCE))), workspace);
    }

    @Test
    void missingAlteredFailedAndStaleEvidenceAreRejected() throws Exception {
        Path workspace = workspace();
        List<String> original = Files.readAllLines(workspace.resolve(EVIDENCE));

        List<String> missing = new ArrayList<>(original);
        missing.removeIf(line -> line.startsWith("Person::C04\t"));
        assertThrows(IllegalStateException.class, () -> validate(parse(missing), workspace));

        List<String> altered = replace(original, "Company::C32\t", "companyMisc", "companyGood");
        assertThrows(IllegalStateException.class, () -> validate(parse(altered), workspace));

        List<String> failed = replace(original, "Library::C10\t", "\tPASS", "\tFAIL");
        assertThrows(IllegalStateException.class, () -> validate(parse(failed), workspace));

        List<String> stale = replace(original, "# fixtureSha256\t", "# fixtureSha256\t", "# fixtureSha256\t00");
        assertThrows(IllegalStateException.class, () -> validate(parse(stale), workspace));

        List<String> invalidProvenance = replace(original, "# gitDirtyAtCapture\t",
                "true", "unknown");
        assertThrows(IllegalStateException.class, () -> validate(parse(invalidProvenance), workspace));
    }

    private static void validate(Evidence evidence, Path workspace) throws Exception {
        Map<String, String> metadata = evidence.metadata();
        requireEquals("oclval47-nonvacuity-runtime-evidence-v2", metadata.get("schema"), "schema");
        requireEquals("PC-2026-07-22.3", metadata.get("proofContract"), "proofContract");
        requireEquals(Cypher5ValAssumptionMatrix.NEO4J_KERNEL, metadata.get("neo4jKernel"), "neo4jKernel");
        requireEquals(Cypher5ValAssumptionMatrix.CYPHER_COMPONENT,
                metadata.get("cypherComponent"), "cypherComponent");
        requireEquals(Cypher5ValAssumptionMatrix.EDITION, metadata.get("edition"), "edition");
        requireEquals(Cypher5ValAssumptionMatrix.DATABASE, metadata.get("database"), "database");
        requireEquals(Cypher5ValAssumptionMatrix.JAVA_DRIVER_DEPENDENCY,
                metadata.get("javaDriverDependency"), "javaDriverDependency");
        requireEquals(Cypher5ValAssumptionMatrix.ENCODING_PROFILE,
                metadata.get("encodingProfile"), "encodingProfile");
        requireEquals("oclval47-nonvacuity-v1", metadata.get("fixtureProfile"), "fixtureProfile");
        requireEquals("47", metadata.get("cases"), "cases");
        requireEquals("47", metadata.get("equivalent"), "equivalent");
        requireEquals("28", metadata.get("mixed"), "mixed");
        requireEquals("19", metadata.get("profileTautology"), "profileTautology");
        requireEquals("0", metadata.get("allViolate"), "allViolate");
        requireEquals("0", metadata.get("emptyContext"), "emptyContext");
        requireEquals(RUNTIME_TEST, metadata.get("runtimeTest"), "runtimeTest");
        requireEquals(EvidenceSourceHash.MODE, metadata.get("sourceHashMode"), "sourceHashMode");
        requireMatches(metadata.get("executedAt"), "\\d{4}-\\d{2}-\\d{2}T.+[+-]\\d{2}:\\d{2}", "executedAt");
        EvidenceGitProvenance.validate(metadata, workspace);

        requireHash(metadata, "expectationManifestSha256", workspace.resolve(
                "neo4j-tgg/src/test/resources/org/uet/dse/neo4jtgg/experiment/oclval47-nonvacuity.tsv"));
        requireHash(metadata, "fixtureSha256", workspace.resolve(
                "neo4j-tgg/src/test/java/org/uet/dse/neo4jtgg/experiment/OclVal47NonVacuityFixture.java"));
        requireHash(metadata, "runtimeTestSha256", workspace.resolve(
                "neo4j-tgg/src/test/java/org/uet/dse/neo4jtgg/experiment/OclValRealNeo4jCoverageTest.java"));
        requireHash(metadata, "rendererSha256", workspace.resolve(
                "neo4j-tgg/src/main/java/org/uet/dse/neo4jtgg/ocl/ir/OclCypherRenderer.java"));
        requireHash(metadata, "binderSha256", workspace.resolve(
                "neo4j-tgg/src/main/java/org/uet/dse/neo4jtgg/ocl/OclSemanticBinder.java"));
        requireHash(metadata, "admissionSha256", workspace.resolve(
                "neo4j-tgg/src/main/java/org/uet/dse/neo4jtgg/ocl/OclValBoundAdmissionPolicy.java"));
        requireHash(metadata, "irBuilderSha256", workspace.resolve(
                "neo4j-tgg/src/main/java/org/uet/dse/neo4jtgg/ocl/ir/OclIrBuilder.java"));
        requireHash(metadata, "optimizerSha256", workspace.resolve(
                "neo4j-tgg/src/main/java/org/uet/dse/neo4jtgg/ocl/ir/OclIrOptimizer.java"));
        requireHash(metadata, "adapterCertificateSha256", workspace.resolve(
                "neo4j-tgg/src/main/java/org/uet/dse/neo4jtgg/experiment/AdapterAdequacyCertificate.java"));
        requireHash(metadata, "adapterSnapshotReaderSha256", workspace.resolve(
                "neo4j-tgg/src/main/java/org/uet/dse/neo4jtgg/experiment/AdapterAdequacySnapshotReader.java"));
        requireHash(metadata, "encodingSha256", workspace.resolve(
                "neo4j/src/main/java/org/uet/dse/neo4j/encoding/CanonicalGraphEncoding.java"));
        requireEquals(Cypher5ValAssumptionMatrix.JAVA_DRIVER_DEPENDENCY,
                dependencyVersion(workspace.resolve("neo4j/pom.xml"), "neo4j-java-driver"),
                "neo4j-java-driver dependency");

        Map<String, OclVal47NonVacuityFixture.ExpectedCase> expected =
                OclVal47NonVacuityFixture.expectations();
        requireEquals(new ArrayList<>(expected.keySet()), new ArrayList<>(evidence.rows().keySet()),
                "evidence row IDs/order");
        for (var entry : expected.entrySet()) {
            String id = entry.getKey();
            OclVal47NonVacuityFixture.ExpectedCase expectedCase = entry.getValue();
            Row row = evidence.rows().get(id);
            requireEquals(expectedCase.obligation().name(), row.obligation(), id + " obligation");
            requireEquals(expectedCase.expectedClass().name(), row.classification(), id + " classification");
            requireEquals(expectedCase.expectedViolations(), row.expected(), id + " expected IDs");
            requireEquals(expectedCase.expectedViolations(), row.useObserved(), id + " USE IDs");
            requireEquals(expectedCase.expectedViolations(), row.neo4jObserved(), id + " Neo4j IDs");
            requireEquals("PASS", row.status(), id + " status");
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
                requireEquals("id\tobligation\tclassification\texpectedViolations\tuseObserved\tneo4jObserved\tstatus",
                        line, "header");
                headerSeen = true;
                continue;
            }
            String[] fields = line.split("\\t", -1);
            if (fields.length != 7) throw new IllegalStateException("Malformed evidence row: " + line);
            Row row = new Row(fields[0], fields[1], fields[2], ids(fields[3]),
                    ids(fields[4]), ids(fields[5]), fields[6]);
            if (rows.put(row.id(), row) != null) throw new IllegalStateException("Duplicate row: " + row.id());
        }
        if (!headerSeen) throw new IllegalStateException("Missing evidence header");
        return new Evidence(Collections.unmodifiableMap(new LinkedHashMap<>(metadata)),
                Collections.unmodifiableMap(new LinkedHashMap<>(rows)));
    }

    private static Set<String> ids(String value) {
        if (value.isBlank()) return Set.of();
        return Collections.unmodifiableSet(new LinkedHashSet<>(List.of(value.split(";"))));
    }

    private static List<String> replace(List<String> source, String prefix, String from, String to) {
        List<String> result = new ArrayList<>(source);
        for (int i = 0; i < result.size(); i++) {
            String line = result.get(i);
            if (line.startsWith(prefix)) {
                String changed = line.replace(from, to);
                if (changed.equals(line)) throw new IllegalStateException("Mutation did not change " + prefix);
                result.set(i, changed);
                return result;
            }
        }
        throw new IllegalStateException("Missing mutation target: " + prefix);
    }

    private static void requireHash(Map<String, String> metadata, String key, Path source) throws Exception {
        requireEquals(metadata.get(key), EvidenceSourceHash.sha256(source), key);
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

    private record Row(String id, String obligation, String classification,
                       Set<String> expected, Set<String> useObserved,
                       Set<String> neo4jObserved, String status) {
    }
}
