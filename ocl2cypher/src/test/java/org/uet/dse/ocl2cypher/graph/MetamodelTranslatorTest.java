package org.uet.dse.ocl2cypher.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.uet.dse.ocl2cypher.diagnostics.Stage;
import org.uet.dse.ocl2cypher.runtime.OclType;
import org.uet.dse.ocl2cypher.source.model.SchemaModel;
import org.uet.dse.ocl2cypher.source.model.Snapshot;
import org.uet.dse.ocl2cypher.source.model.UmlAssociation;
import org.uet.dse.ocl2cypher.source.model.UmlAttribute;
import org.uet.dse.ocl2cypher.source.model.UmlClass;

class MetamodelTranslatorTest {

    @Test
    void canonicalM2TemplateIsClosedAndHasNoM1Keys() {
        MetamodelMapping mapping = MetamodelMapping.canonical();
        assertEquals(MetamodelMapping.NodeKind.values().length, mapping.nodes().size());
        assertEquals(MetamodelMapping.RelationshipKind.values().length,
                mapping.relationships().size());
        assertEquals("Object", mapping.node(MetamodelMapping.NodeKind.OBJECT).labels().get(0));
        assertEquals(GraphModel.LINK_ASSOCIATE_WITH,
                mapping.relationship(MetamodelMapping.RelationshipKind.BINARY_LINK)
                        .physicalType());
        assertFalse(mapping.nodes().values().stream()
                .flatMap(binding -> binding.labels().stream())
                .anyMatch(label -> label.contains("Person") || label.contains("Company")));
    }

    @Test
    void schemaInstantiationCreatesUniqueDeclarationScopedObservers() {
        SchemaModel schema = SchemaModel.builder("catalogue")
                .clazz(UmlClass.of("Person"))
                .clazz(UmlClass.of("Company"))
                .attribute(UmlAttribute.of("Person", "age", OclType.INTEGER))
                .association(new UmlAssociation("Employment", "Employment",
                        "Company", "employer", 0, 1,
                        "Person", "employees", 0, -1,
                        List.of(), false, true))
                .build();

        var result = MetamodelTranslator.translate(schema);
        assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
        var catalogue = result.value();
        assertEquals(List.of("Person", "Company"),
                catalogue.classes().keySet().stream().toList());
        assertEquals("UML_CLASS", catalogue.clazz("Person")
                .declarationNode().observationRole());
        assertEquals(List.of("Person::age"), catalogue.attributes().keySet().stream().toList());
        assertEquals("ocl-integer-decimal-v1",
                catalogue.attribute("Person::age").valueProperty().codecId());
        var association = catalogue.association("Employment");
        assertEquals("Employment::employees", association.forward().semanticSourceKey());
        assertEquals(MetamodelTranslator.ResultKind.SET, association.forward().resultKind());
        assertEquals("Employment::employer", association.reverse().semanticSourceKey());
        assertEquals(MetamodelTranslator.ResultKind.SCALAR, association.reverse().resultKind());
        assertTrue(association.associationClass().isEmpty());
    }

    @Test
    void associationClassParticipantTypesAreInstantiatedByStableDeclarationKey() {
        SchemaModel schema = SchemaModel.builder("catalogue")
                .clazz(UmlClass.of("Person"))
                .clazz(UmlClass.of("Project"))
                .clazz(new UmlClass("Assignment", "Assignment", false, true, List.of()))
                .association(new UmlAssociation("Assignment", "Assignment",
                        "Person", "assignee", 0, 1,
                        "Project", "project", 0, -1,
                        List.of(), false, true))
                .build();

        var result = MetamodelTranslator.translate(schema);
        assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
        var binding = result.value().association("Assignment").associationClass().orElseThrow();
        assertEquals("Assignment_SourceParticipant",
                binding.sourceParticipant().physicalType());
        assertEquals("Assignment_TargetParticipant",
                binding.targetParticipant().physicalType());
    }

    @Test
    void graphConstructionConsumesTheInstantiatedCatalogue() {
        SchemaModel schema = SchemaModel.builder("catalogue")
                .clazz(UmlClass.of("Person"))
                .clazz(UmlClass.of("Company"))
                .attribute(UmlAttribute.of("Person", "name", OclType.STRING))
                .association(UmlAssociation.binary("Employment", "Company",
                        "employer", "Person", "employees"))
                .build();
        Snapshot snapshot = Snapshot.builder().object("p", "Person")
                .object("c", "Company").link("Employment", "c", "p").build();

        var built = GraphBuilder.build(schema, snapshot);
        assertTrue(built.isSuccess(), () -> built.diagnostics().toString());
        GraphModel graph = built.value().graph();
        assertEquals(MetamodelMapping.canonical()
                        .node(MetamodelMapping.NodeKind.OBJECT).observationRole(),
                graph.node(GraphKey.object("catalogue", "p")).observationRole());
        assertEquals(GraphModel.OBJECT_INSTANCE_OF,
                graph.outgoing(GraphKey.object("catalogue", "p"),
                        GraphModel.OBJECT_INSTANCE_OF).get(0).physicalType());
        assertEquals("ASSOCIATION_DECLARATION",
                graph.node(GraphKey.association("catalogue", "Employment")).observationRole());
        assertEquals("Company", graph.outgoing(
                        GraphKey.association("catalogue", "Employment"),
                        GraphModel.ASSOCIATION_SOURCE_END).stream()
                .map(edge -> graph.node(edge.targetKey()).properties().get("classKey"))
                .findFirst().orElseThrow());
        assertEquals("Person", graph.outgoing(
                        GraphKey.association("catalogue", "Employment"),
                        GraphModel.ASSOCIATION_TARGET_END).stream()
                .map(edge -> graph.node(edge.targetKey()).properties().get("classKey"))
                .findFirst().orElseThrow());
        assertEquals(schema.modelKey(), built.value().catalogue().modelKey());
    }

    @Test
    void invalidSchemaFailsAtTmmBeforeSnapshotConstruction() {
        SchemaModel invalid = SchemaModel.builder("catalogue")
                .clazz(UmlClass.of("Person"))
                .association(new UmlAssociation("Bad", "Bad",
                        "Person", "owner", 2, 1,
                        "Person", "items", 0, -1,
                        List.of(), false, true))
                .build();

        var translated = MetamodelTranslator.translate(invalid);
        assertTrue(translated.isFailure());
        assertEquals(Stage.T_MM, translated.primaryDiagnostic().stage());
        assertEquals("MM_INVALID_MULTIPLICITY", translated.primaryDiagnostic().code());
    }

    @Test
    void unknownSuperclassFailsAtTmmRatherThanDuringGraphConstruction() {
        SchemaModel invalid = SchemaModel.builder("catalogue")
                .clazz(UmlClass.of("Child", "MissingParent"))
                .build();

        var translated = MetamodelTranslator.translate(invalid);
        assertTrue(translated.isFailure());
        assertEquals(Stage.T_MM, translated.primaryDiagnostic().stage());
        assertEquals("MM_INVALID_GENERALIZATION", translated.primaryDiagnostic().code());
    }
}
