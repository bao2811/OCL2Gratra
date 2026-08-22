package org.uet.dse.neo4jtgg.experiment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.uet.dse.neo4jtgg.ocl.ir.CanonicalPgmmEmfConformance;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanonicalPgmmEmfConformanceTest {
    @TempDir
    Path temp;

    @Test
    void canonicalInstanceConformsToFullPgmmAndJavaEncoding() throws Exception {
        Path root = workspaceRoot();
        var report = CanonicalPgmmEmfConformance.check(
                root.resolve("md/research/model/PGMM.ecore"),
                root.resolve("verification/instances/pgmm-canonical-v1.xmi"));
        assertTrue(report.valid(), report.errors()::toString);
    }

    @Test
    void labelRelationshipKeyCodecAndEndpointMutationsAreKilled() throws Exception {
        Path root = workspaceRoot();
        Path ecore = root.resolve("md/research/model/PGMM.ecore");
        String valid = Files.readString(root.resolve("verification/instances/pgmm-canonical-v1.xmi"));
        assertMutationKilled(ecore, valid.replace("labels=\"Object\"", "labels=\"Ghost\""), "label.xmi");
        assertMutationKilled(ecore, valid.replace("physicalTypes=\"ObjectInstanceOf\"", "physicalTypes=\"WrongType\""), "relationship.xmi");
        assertMutationKilled(ecore, valid.replace("components=\"//@nodeTypes.0/@properties.0 //@nodeTypes.0/@properties.1\"",
                "components=\"//@nodeTypes.0/@properties.1\""), "key.xmi");
        assertMutationKilled(ecore, valid.replace("bottomToken=\"v1|V\"", "bottomToken=\"null\""), "codec.xmi");
        assertMutationKilled(ecore, valid.replaceFirst("nodeType=\"//@nodeTypes.1\"", "nodeType=\"//@nodeTypes.0\""),
                "endpoint.xmi");
    }

    private void assertMutationKilled(Path ecore, String xmi, String name) throws Exception {
        Path instance = temp.resolve(name);
        Files.writeString(instance, xmi);
        assertFalse(CanonicalPgmmEmfConformance.check(ecore, instance).valid(), name);
    }

    private Path workspaceRoot() {
        Path working = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        if (Files.isDirectory(working.resolve("md/research/model"))) return working;
        if (working.getParent() != null && Files.isDirectory(working.getParent().resolve("md/research/model"))) {
            return working.getParent();
        }
        throw new IllegalStateException("Cannot locate workspace root from " + working);
    }
}
