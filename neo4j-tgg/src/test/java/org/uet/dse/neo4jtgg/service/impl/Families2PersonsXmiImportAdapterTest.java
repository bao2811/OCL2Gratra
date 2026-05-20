package org.uet.dse.neo4jtgg.service.impl;

import org.junit.jupiter.api.Test;
import org.uet.dse.neo4jtgg.model.ImportBatch;
import org.uet.dse.neo4jtgg.model.TggWorkspaceContext;
import org.uet.dse.neo4jtgg.model.WorkspaceSide;

import java.io.File;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Families2PersonsXmiImportAdapterTest {
    @Test
    void parsesFamiliesPrototypeXml() throws Exception {
        String xml = """
                <FamilyRegister>
                  <families name="Smith">
                    <father name="John"/>
                    <mother name="Jane"/>
                    <sons name="Bob"/>
                    <daughters name="Alice"/>
                  </families>
                </FamilyRegister>
                """;

        File temp = File.createTempFile("families", ".xml");
        Files.writeString(temp.toPath(), xml);

        Families2PersonsXmiImportAdapter adapter = new Families2PersonsXmiImportAdapter();
        ImportBatch batch = adapter.parse(new TggWorkspaceContext(null, null), WorkspaceSide.SOURCE, temp);

        assertEquals(6, batch.getObjects().size());
        assertEquals(5, batch.getLinks().size());
    }
}
