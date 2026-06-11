package org.uet.dse.neo4jtgg.service.impl;

import org.junit.jupiter.api.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.uet.dse.neo4jtgg.model.ImportBatch;
import org.uet.dse.neo4jtgg.model.TggWorkspaceContext;
import org.uet.dse.neo4jtgg.model.TggWorkspaceDefinition;
import org.uet.dse.neo4jtgg.model.WorkspaceSide;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenericXmiImportAdapterTest {
    private static final Path REPO_ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void parsesFamiliesPrototypeXmlAgainstMetamodel() throws Exception {
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

        GenericXmiImportAdapter adapter = new GenericXmiImportAdapter();
        ImportBatch batch = adapter.parse(familiesContext(), WorkspaceSide.SOURCE, temp);

        assertEquals(6, batch.getObjects().size());
        assertEquals(5, batch.getLinks().size());
        assertTrue(batch.getObjects().stream().anyMatch(object -> "FamilyRegister".equals(object.getClassName())));
        assertTrue(batch.getObjects().stream().anyMatch(object -> "Family".equals(object.getClassName())));
        assertTrue(batch.getObjects().stream().filter(object -> "FamilyMember".equals(object.getClassName())).count() >= 4);
    }

    @Test
    void parsesPersonsPrototypeXmlAgainstMetamodel() throws Exception {
        String xml = """
                <PersonRegister>
                  <persons xmi:type="Male" name="Smith, John"/>
                  <persons xmi:type="Female" name="Smith, Jane"/>
                </PersonRegister>
                """;

        File temp = File.createTempFile("persons", ".xml");
        Files.writeString(temp.toPath(), xml);

        GenericXmiImportAdapter adapter = new GenericXmiImportAdapter();
        ImportBatch batch = adapter.parse(personsContext(), WorkspaceSide.TARGET, temp);

        assertEquals(3, batch.getObjects().size());
        assertEquals(2, batch.getLinks().size());
        assertTrue(batch.getObjects().stream().anyMatch(object -> "Male".equals(object.getClassName())));
        assertTrue(batch.getObjects().stream().anyMatch(object -> "Female".equals(object.getClassName())));
        assertTrue(batch.getLinks().stream().allMatch(link -> "PersonRegistration".equals(link.getAssociationName())));
    }

    private TggWorkspaceContext familiesContext() throws Exception {
        MModel sourceModel = compileModel(REPO_ROOT.resolve("rtl/examples/Families2Persons/Families.use"));
        TggWorkspaceDefinition definition = new TggWorkspaceDefinition(
                "Families2Persons",
                sourceModel,
                null,
                null,
                java.util.List.of(),
                Set.of("FamilyRegister", "Family", "FamilyMember"),
                Set.of(),
                Set.of());
        TggWorkspaceContext context = new TggWorkspaceContext(null, null);
        context.setWorkspaceDefinition(definition);
        return context;
    }

    private TggWorkspaceContext personsContext() throws Exception {
        MModel targetModel = compileModel(REPO_ROOT.resolve("rtl/examples/Families2Persons/Persons.use"));
        TggWorkspaceDefinition definition = new TggWorkspaceDefinition(
                "Families2Persons",
                null,
                null,
                targetModel,
                java.util.List.of(),
                Set.of(),
                Set.of(),
                Set.of("PersonRegister", "Person", "Male", "Female"));
        TggWorkspaceContext context = new TggWorkspaceContext(null, null);
        context.setWorkspaceDefinition(definition);
        return context;
    }

    private MModel compileModel(Path file) throws Exception {
        String specification = Files.readString(file);
        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(
                specification,
                file.toString(),
                new PrintWriter(buffer, true),
                new ModelFactory());
        assertNotNull(model, buffer.toString());
        return model;
    }
}
