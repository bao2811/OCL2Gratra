package org.uet.dse.neo4jtgg.experiment;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;

/** Freshness and provenance guard for the clean canonical-profile supplemental runtime run. */
class CanonicalProfileRuntimeEvidenceManifestTest {
    private static final String MANIFEST =
            "verification/evidence/canonical-profile-runtime-2026-08-09.tsv";
    private static final List<ExpectedRow> EXPECTED = List.of(
            new ExpectedRow("ADAPTER_ADEQUACY", "observations=6;plans=2;labelMutation=KILLED",
                    "AdapterAdequacyCertificateRealNeo4jTest#certificateAndQueriesObserveOneNeo4jSnapshot"),
            new ExpectedRow("VOID_CONTEXTS", "cases=13",
                    "OclValSemanticDiscriminatorRealNeo4jTest#nullAndDuplicateCollectFollowTheCanonicalProfile"),
            new ExpectedRow("DUPLICATE_COLLECT", "cases=1",
                    "OclValSemanticDiscriminatorRealNeo4jTest#nullAndDuplicateCollectFollowTheCanonicalProfile"),
            new ExpectedRow("TO_ONE_COLLECTION_VIEW", "cases=3",
                    "OclValSemanticDiscriminatorRealNeo4jTest#nullAndDuplicateCollectFollowTheCanonicalProfile"),
            new ExpectedRow("PERSON_CASE", "equivalent=10;missing=0;spurious=0;allPass=3;mixed=7",
                    "FamiliesToPersonsCaseStudyRealNeo4jTest#personCaseAgreesExactlyWithNativeUse"),
            new ExpectedRow("FAMILY_CASE", "equivalent=10;missing=0;spurious=0;allPass=7;mixed=3",
                    "FamiliesToPersonsCaseStudyRealNeo4jTest#familyCaseAgreesExactlyWithNativeUse"));

    @Test
    void cleanSupplementMatchesCurrentSourcesAndAllReviewedRuntimeObservations() throws Exception {
        Path workspace = workspace();
        Evidence evidence = parse(Files.readAllLines(workspace.resolve(MANIFEST)));
        validate(evidence, workspace);
    }

    @Test
    void duplicateFailedStaleAndInvalidProvenanceEvidenceAreRejected() throws Exception {
        Path workspace = workspace();
        List<String> original = Files.readAllLines(workspace.resolve(MANIFEST));

        List<String> duplicate = new ArrayList<>(original);
        duplicate.add(original.stream().filter(line -> line.startsWith("VOID_CONTEXTS\t"))
                .findFirst().orElseThrow());
        assertThrows(IllegalStateException.class, () -> parse(duplicate));

        assertThrows(IllegalStateException.class, () -> validate(parse(replace(
                original, "TO_ONE_COLLECTION_VIEW\t", "\tPASS\t", "\tFAIL\t")), workspace));
        assertThrows(IllegalStateException.class, () -> validate(parse(replace(
                original, "# discriminatorRuntimeTestSha256\t", "# discriminatorRuntimeTestSha256\t",
                "# discriminatorRuntimeTestSha256\t00")), workspace));
        assertThrows(IllegalStateException.class, () -> validate(parse(replace(
                original, "# gitDirtyAtCapture\t", "false", "unknown")), workspace));
    }

    private static void validate(Evidence evidence, Path workspace) throws Exception {
        Map<String, String> metadata = evidence.metadata();
        requireEquals("canonical-profile-runtime-evidence-v1", metadata.get("schema"), "schema");
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
        requireEquals(EvidenceSourceHash.MODE, metadata.get("sourceHashMode"), "sourceHashMode");
        requireMatches(metadata.get("executedAt"), "\\d{4}-\\d{2}-\\d{2}T.+[+-]\\d{2}:\\d{2}", "executedAt");
        EvidenceGitProvenance.validate(metadata, workspace);

        requireSameCaptureCommit(metadata, workspace,
                "verification/evidence/cypher5-val-runtime-2026-08-01.tsv");
        requireSameCaptureCommit(metadata, workspace,
                "verification/evidence/oclval47-nonvacuity-runtime-2026-08-01.tsv");

        requireHash(metadata, "rendererSha256", workspace,
                "neo4j-tgg/src/main/java/org/uet/dse/neo4jtgg/ocl/ir/OclCypherRenderer.java");
        requireHash(metadata, "binderSha256", workspace,
                "neo4j-tgg/src/main/java/org/uet/dse/neo4jtgg/ocl/OclSemanticBinder.java");
        requireHash(metadata, "admissionSha256", workspace,
                "neo4j-tgg/src/main/java/org/uet/dse/neo4jtgg/ocl/OclValBoundAdmissionPolicy.java");
        requireHash(metadata, "irBuilderSha256", workspace,
                "neo4j-tgg/src/main/java/org/uet/dse/neo4jtgg/ocl/ir/OclIrBuilder.java");
        requireHash(metadata, "optimizerSha256", workspace,
                "neo4j-tgg/src/main/java/org/uet/dse/neo4jtgg/ocl/ir/OclIrOptimizer.java");
        requireHash(metadata, "adapterCertificateSha256", workspace,
                "neo4j-tgg/src/main/java/org/uet/dse/neo4jtgg/experiment/AdapterAdequacyCertificate.java");
        requireHash(metadata, "adapterSnapshotReaderSha256", workspace,
                "neo4j-tgg/src/main/java/org/uet/dse/neo4jtgg/experiment/AdapterAdequacySnapshotReader.java");
        requireHash(metadata, "adapterRuntimeTestSha256", workspace,
                "neo4j-tgg/src/test/java/org/uet/dse/neo4jtgg/experiment/AdapterAdequacyCertificateRealNeo4jTest.java");
        requireHash(metadata, "discriminatorRuntimeTestSha256", workspace,
                "neo4j-tgg/src/test/java/org/uet/dse/neo4jtgg/experiment/OclValSemanticDiscriminatorRealNeo4jTest.java");
        requireHash(metadata, "familiesRuntimeTestSha256", workspace,
                "neo4j-tgg/src/test/java/org/uet/dse/neo4jtgg/experiment/FamiliesToPersonsCaseStudyRealNeo4jTest.java");

        requireEquals(EXPECTED.stream().map(ExpectedRow::id).toList(),
                new ArrayList<>(evidence.rows().keySet()), "row IDs/order");
        for (ExpectedRow expected : EXPECTED) {
            Row actual = evidence.rows().get(expected.id());
            requireEquals("PASS", actual.status(), expected.id() + " status");
            requireEquals(expected.observed(), actual.observed(), expected.id() + " observation");
            requireEquals(expected.evidenceTest(), actual.evidenceTest(), expected.id() + " evidence test");
        }
    }

    private static void requireSameCaptureCommit(Map<String, String> metadata, Path workspace,
                                                 String relativeManifest) throws Exception {
        Map<String, String> other = parseMetadata(Files.readAllLines(workspace.resolve(relativeManifest)));
        requireEquals(metadata.get("gitCommit"), other.get("gitCommit"),
                relativeManifest + " gitCommit");
        requireEquals("false", other.get("gitDirtyAtCapture"),
                relativeManifest + " gitDirtyAtCapture");
    }

    private static Evidence parse(List<String> lines) {
        Map<String, String> metadata = new LinkedHashMap<>();
        Map<String, Row> rows = new LinkedHashMap<>();
        boolean headerSeen = false;
        for (String line : lines) {
            if (line.isBlank()) continue;
            if (line.startsWith("# ")) {
                String[] fields = line.substring(2).split("\\t", -1);
                if (fields.length != 2 || fields[0].isBlank() || fields[1].isBlank()
                        || metadata.put(fields[0], fields[1]) != null) {
                    throw new IllegalStateException("Malformed or duplicate metadata: " + line);
                }
                continue;
            }
            if (!headerSeen) {
                requireEquals("id\tstatus\tobserved\tevidenceTest", line, "header");
                headerSeen = true;
                continue;
            }
            String[] fields = line.split("\\t", -1);
            if (fields.length != 4) throw new IllegalStateException("Malformed runtime row: " + line);
            Row row = new Row(fields[0], fields[1], fields[2], fields[3]);
            if (rows.put(row.id(), row) != null) {
                throw new IllegalStateException("Duplicate runtime row: " + row.id());
            }
        }
        if (!headerSeen) throw new IllegalStateException("Missing evidence header");
        return new Evidence(Collections.unmodifiableMap(metadata), Collections.unmodifiableMap(rows));
    }

    private static Map<String, String> parseMetadata(List<String> lines) {
        Map<String, String> metadata = new LinkedHashMap<>();
        for (String line : lines) {
            if (!line.startsWith("# ")) continue;
            String[] fields = line.substring(2).split("\\t", -1);
            if (fields.length == 2) metadata.put(fields[0], fields[1]);
        }
        return metadata;
    }

    private static List<String> replace(List<String> source, String prefix, String from, String to) {
        List<String> result = new ArrayList<>(source);
        for (int i = 0; i < result.size(); i++) {
            String line = result.get(i);
            if (!line.startsWith(prefix)) continue;
            String changed = line.replace(from, to);
            if (changed.equals(line)) throw new IllegalStateException("Mutation did not change " + prefix);
            result.set(i, changed);
            return result;
        }
        throw new IllegalStateException("Missing mutation target: " + prefix);
    }

    private static void requireHash(Map<String, String> metadata, String key,
                                    Path workspace, String relativePath) throws Exception {
        requireEquals(metadata.get(key), EvidenceSourceHash.sha256(workspace.resolve(relativePath)), key);
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

    private record Row(String id, String status, String observed, String evidenceTest) {
    }

    private record ExpectedRow(String id, String observed, String evidenceTest) {
    }
}
