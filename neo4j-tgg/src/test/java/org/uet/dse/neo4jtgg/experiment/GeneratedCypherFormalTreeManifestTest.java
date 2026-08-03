package org.uet.dse.neo4jtgg.experiment;

import org.junit.jupiter.api.Test;
import org.uet.dse.neo4jtgg.ocl.ir.OclCypherRenderer;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Exact checked-in manifest for complete alpha-normalized token/group trees. */
class GeneratedCypherFormalTreeManifestTest {
    private static final String MANIFEST =
            "/org/uet/dse/neo4jtgg/experiment/formal-cypher-trees.tsv";

    @Test
    void everyCheckedQueryExactlyMatchesItsCheckedInCanonicalFullTree() throws IOException {
        Map<String, String> expected = readManifest();
        OclCypherPlanFormalTreeAgreementTest corpus = new OclCypherPlanFormalTreeAgreementTest();
        Map<String, String> actual = new LinkedHashMap<>();
        for (OclCypherPlanFormalTreeAgreementTest.NamedPlan item : corpus.namedCompilationCorpus()) {
            String cypher = new OclCypherRenderer("OclValCoverage").renderInvariant(item.plan()).cypher();
            actual.put(item.name(), GeneratedCypherCanonicalTree.parse(cypher).text());
        }
        assertEquals(52, expected.size(), "Unexpected checked-in formal-tree count");
        assertEquals(expected.keySet(), actual.keySet(), "Formal-tree manifest and executable corpus differ");
        actual.forEach((name, tree) -> assertEquals(expected.get(name), tree, name));
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
}
