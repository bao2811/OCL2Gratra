package org.uet.dse.neo4jtgg.experiment;

import org.junit.jupiter.api.Test;
import org.uet.dse.neo4jtgg.ocl.ir.OclCypherRenderer;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Exact checked-in manifest for complete alpha-normalized token/group trees. */
class GeneratedCypherFormalTreeManifestTest {
    private static final String MANIFEST =
            "/org/uet/dse/neo4jtgg/experiment/formal-cypher-trees.tsv";

    @Test
    void everyCheckedQueryExactlyMatchesItsCheckedInCanonicalFullTree() throws IOException {
        OclCypherPlanFormalTreeAgreementTest corpus = new OclCypherPlanFormalTreeAgreementTest();
        Map<String, String> actual = new LinkedHashMap<>();
        for (OclCypherPlanFormalTreeAgreementTest.NamedPlan item : corpus.namedCompilationCorpus()) {
            String cypher = new OclCypherRenderer("OclValCoverage").renderInvariant(item.plan()).cypher();
            actual.put(item.name(), GeneratedCypherCanonicalTree.parse(cypher).text());
        }
        boolean update = Boolean.getBoolean("ocl.formal.trees.update");
        if (update) writeManifest(actual);
        Map<String, String> expected = update ? Map.copyOf(actual) : readManifest();
        assertEquals(52, expected.size(), "Unexpected checked-in formal-tree count");
        assertEquals(expected.keySet(), actual.keySet(), "Formal-tree manifest and executable corpus differ");
        actual.forEach((name, tree) -> assertEquals(expected.get(name), tree, name));
    }

    private static void writeManifest(Map<String, String> trees) throws IOException {
        StringBuilder output = new StringBuilder();
        for (Map.Entry<String, String> entry : trees.entrySet()) {
            output.append(Base64.getEncoder().encodeToString(
                            entry.getKey().getBytes(StandardCharsets.UTF_8)))
                    .append('\t').append(deflate(entry.getValue())).append('\n');
        }
        Path path = Path.of("src", "test", "resources", "org", "uet", "dse", "neo4jtgg",
                "experiment", "formal-cypher-trees.tsv");
        Files.writeString(path, output.toString(), StandardCharsets.UTF_8);
    }

    private static Map<String, String> readManifest() throws IOException {
        Map<String, String> result = new LinkedHashMap<>();
        var resource = GeneratedCypherFormalTreeManifestTest.class.getResourceAsStream(MANIFEST);
        assertNotNull(resource, MANIFEST);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] fields = line.split("\\t", -1);
                if (fields.length != 2) throw new IOException("Malformed formal-tree manifest row");
                String name = new String(Base64.getDecoder().decode(fields[0]), StandardCharsets.UTF_8);
                String previous = result.put(name, inflate(fields[1]));
                if (previous != null) throw new IOException("Duplicate formal-tree case: " + name);
            }
        }
        return Map.copyOf(result);
    }

    private static String inflate(String encoded) throws IOException {
        byte[] compressed = Base64.getDecoder().decode(encoded);
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            return new String(gzip.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String deflate(String value) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
            gzip.write(value.getBytes(StandardCharsets.UTF_8));
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }
}
