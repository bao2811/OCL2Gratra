package org.uet.dse.ocl2cypher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.uet.dse.ocl2cypher.source.model.CapabilityMatrix;
import org.uet.dse.ocl2cypher.source.omg.OclOperation;

/** Machine-enforced parts of docs/evidence/baseline-2026-09-06.md. */
class BaselineManifestTest {
    private static final Map<String, String> CORPUS_SHA256 = Map.of(
            "grammar-conformance.tsv",
            "1de62667e8dc54ed8fb7c4e2f6194e13f3454b8300dc5c4864546dde8d88a82d",
            "precedence-corpus.ocl",
            "9293699016ce49871d7a2fe8d3337b1ec42a5e83a03596fb72274fcb01e16e0e",
            "expected-precedence-trees.json",
            "fd7229d16f101a9ad182c9bde23cf70878fa753b43a492b2d01ebef9fe86abef");

    @Test
    void corpusFingerprintsAndCapabilityRowsMatchTheRecordedBaseline() throws IOException {
        Path resources = Path.of("src", "test", "resources");
        for (Map.Entry<String, String> artifact : CORPUS_SHA256.entrySet()) {
            Path path = resources.resolve(artifact.getKey());
            assertTrue(Files.isRegularFile(path), path.toAbsolutePath().toString());
            assertEquals(artifact.getValue(), sha256(path), artifact.getKey());
        }

        assertEquals(OclOperation.values().length, CapabilityMatrix.entries().size());
        for (OclOperation operation : OclOperation.values()) {
            CapabilityMatrix.Capability capability = CapabilityMatrix.capability(operation);
            assertNotNull(capability, operation.name());
            if (capability.admitted()) {
                assertNotNull(capability.surfaceWitness(), capability.testId());
            } else {
                assertNotNull(capability.diagnosticCode(), capability.testId());
            }
        }
    }

    private static String sha256(Path path) throws IOException {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", exception);
        }
    }
}
