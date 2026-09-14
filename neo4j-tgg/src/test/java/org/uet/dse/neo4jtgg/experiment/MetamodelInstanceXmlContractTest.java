package org.uet.dse.neo4jtgg.experiment;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Serialization-boundary checks for the checked-in OVA, NVA, and CQM instances. */
class MetamodelInstanceXmlContractTest {
    @Test
    void certifiedInstancesAreWellFormedAndCarryRequiredRoots() throws Exception {
        Path root = workspaceRoot();
        Document ova = parse(root.resolve("verification/instances/ova-certified-v1.xmi"));
        Document nva = parse(root.resolve("verification/instances/nva-certified-v1.xmi"));
        Document cqm = parse(root.resolve("verification/instances/cqm-certified-v1.xmi"));
        assertEquals("ValidationModule", ova.getDocumentElement().getLocalName() == null
                ? ova.getDocumentElement().getNodeName().replace("ova:", "")
                : ova.getDocumentElement().getLocalName());
        assertEquals("QueryModule", cqm.getDocumentElement().getLocalName() == null
                ? cqm.getDocumentElement().getNodeName().replace("cqm:", "")
                : cqm.getDocumentElement().getLocalName());
        assertEquals("NvaModule", nva.getDocumentElement().getLocalName() == null
                ? nva.getDocumentElement().getNodeName().replace("nva:", "")
                : nva.getDocumentElement().getLocalName());
        // EMF containment features serialize under their feature names; the
        // xsi:type supplies the concrete classifier when needed.
        assertEquals(1, ova.getElementsByTagName("invariants").getLength());
        assertEquals(1, nva.getElementsByTagName("invariants").getLength());
        assertEquals(1, cqm.getElementsByTagName("invariants").getLength());
        assertNotNull(cqm.getElementsByTagName("astLoweringContract").item(0));
    }

    private Document parse(Path path) throws Exception {
        assertTrue(Files.exists(path), "Missing model instance " + path);
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(path.toFile());
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
