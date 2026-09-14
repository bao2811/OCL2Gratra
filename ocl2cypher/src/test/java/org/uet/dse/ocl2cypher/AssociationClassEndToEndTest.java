package org.uet.dse.ocl2cypher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.uet.dse.ocl2cypher.core.CoreInterpreter;
import org.uet.dse.ocl2cypher.core.CoreLowering;
import org.uet.dse.ocl2cypher.cypher.CypherAst;
import org.uet.dse.ocl2cypher.cypher.Realization;
import org.uet.dse.ocl2cypher.cypher.Serializer;
import org.uet.dse.ocl2cypher.graph.GraphBuilder;
import org.uet.dse.ocl2cypher.graph.GraphModel;
import org.uet.dse.ocl2cypher.graph.GraphObservation;
import org.uet.dse.ocl2cypher.execution.Neo4jExecutionAdapter;
import org.uet.dse.ocl2cypher.qcyp.QCypTranslator;
import org.uet.dse.ocl2cypher.qcyp.QInterpreter;
import org.uet.dse.ocl2cypher.runtime.OclType;
import org.uet.dse.ocl2cypher.runtime.OclValue;
import org.uet.dse.ocl2cypher.source.model.SchemaModel;
import org.uet.dse.ocl2cypher.source.model.Snapshot;
import org.uet.dse.ocl2cypher.source.model.UmlAssociation;
import org.uet.dse.ocl2cypher.source.model.UmlAttribute;
import org.uet.dse.ocl2cypher.source.model.UmlClass;
import org.uet.dse.ocl2cypher.source.omg.OmgAs;

/** Full executable witness for binary association-class navigation. */
class AssociationClassEndToEndTest {

    @Test
    void associationClassMustBeALeafOfTheExecutableProfile() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> SchemaModel.builder("invalid-ac-subtyping")
                        .clazz(UmlClass.of("Doctor"))
                        .clazz(UmlClass.of("Patient"))
                        .clazz(new UmlClass("Prescription", "Prescription", false, true,
                                List.of()))
                        .clazz(new UmlClass("SpecialPrescription", "SpecialPrescription",
                                false, false, List.of("Prescription")))
                        .association(new UmlAssociation("Prescription", "Prescription",
                                "Doctor", "physician", 0, -1,
                                "Patient", "patient", 0, -1,
                                List.of(), false, true))
                        .build());
        assertTrue(failure.getMessage().contains("must be a leaf"));
    }

    @Test
    void reorderingAssociationClassLinksDoesNotRenameParticipantEdges() {
        SchemaModel schema = SchemaModel.builder("parallel-ac")
                .clazz(UmlClass.of("Doctor"))
                .clazz(UmlClass.of("Patient"))
                .clazz(new UmlClass("Prescription", "Prescription", false, true,
                        List.of()))
                .association(new UmlAssociation("Prescription", "Prescription",
                        "Doctor", "physician", 0, -1,
                        "Patient", "patient", 0, -1,
                        List.of(), false, false))
                .build();
        Snapshot forward = Snapshot.builder()
                .object("doctor1", "Doctor").object("patient1", "Patient")
                .object("rx1", "Prescription").object("rx2", "Prescription")
                .associationClassLink("rx1", "Prescription", "doctor1", "patient1")
                .associationClassLink("rx2", "Prescription", "doctor1", "patient1")
                .build();
        Snapshot reversed = Snapshot.builder()
                .object("doctor1", "Doctor").object("patient1", "Patient")
                .object("rx1", "Prescription").object("rx2", "Prescription")
                .associationClassLink("rx2", "Prescription", "doctor1", "patient1")
                .associationClassLink("rx1", "Prescription", "doctor1", "patient1")
                .build();
        var left = GraphBuilder.build(schema, forward);
        var right = GraphBuilder.build(schema, reversed);
        assertTrue(left.isSuccess(), () -> "forward graph: " + left.diagnostics());
        assertTrue(right.isSuccess(), () -> "reversed graph: " + right.diagnostics());
        for (String objectId : List.of("rx1", "rx2")) {
            String objectKey = org.uet.dse.ocl2cypher.graph.GraphKey.object(
                    "parallel-ac", objectId);
            for (String type : List.of(
                    GraphModel.associationClassSourceParticipant("Prescription"),
                    GraphModel.associationClassTargetParticipant("Prescription"))) {
                assertEquals(
                        left.value().graph().outgoing(objectKey, type).get(0).stableKey(),
                        right.value().graph().outgoing(objectKey, type).get(0).stableKey(),
                        objectId + " / " + type);
            }
        }
    }

    private static SchemaModel schema() {
        return schema("medical");
    }

    private static SchemaModel schema(String modelKey) {
        return SchemaModel.builder(modelKey)
                .clazz(UmlClass.of("Doctor"))
                .clazz(UmlClass.of("Patient"))
                .clazz(new UmlClass("Prescription", "Prescription", false, true, List.of()))
                .attribute(UmlAttribute.of("Prescription", "dosage", OclType.STRING))
                .association(new UmlAssociation("Prescription", "Prescription",
                        "Doctor", "physician", 0, -1,
                        "Patient", "patient", 0, -1,
                        List.of(), false, true))
                .build();
    }

    private static final String INVARIANT = "context Doctor inv DailyPrescriptions: "
            + "self.Prescription->forAll(rx | rx.dosage = 'daily') "
            + "and self.Prescription->size() = 2 "
            + "and self.patient->size() = 2";

    private static Snapshot snapshot() {
        return Snapshot.builder()
                .object("doctor1", "Doctor")
                .object("doctor2", "Doctor")
                .object("patient1", "Patient")
                .object("patient2", "Patient")
                .object("rx1", "Prescription")
                .attribute("rx1", "dosage", new OclValue.StringValue("daily"))
                .object("rx2", "Prescription")
                .attribute("rx2", "dosage", new OclValue.StringValue("daily"))
                .associationClassLink("rx1", "Prescription", "doctor1", "patient1")
                .associationClassLink("rx2", "Prescription", "doctor1", "patient2")
                .build();
    }

    @Test
    void participantToLinkObjectPreservesIdentityAttributesAndCypherPattern() {
        SchemaModel schema = schema();
        Snapshot snapshot = snapshot();
        String ocl = INVARIANT;

        var frontend = org.uet.dse.ocl2cypher.api.FrontendCompiler.compile(ocl, schema);
        assertTrue(frontend.isSuccess(), () -> "frontend: " + frontend.diagnostics());
        OmgAs.OmgDocument document = frontend.value().get(0);
        assertTrue(containsAssociationClassCall(
                document.constraints.get(0).specification.bodyExpression));

        var lowered = CoreLowering.lower(schema, document, document.constraints.get(0));
        assertTrue(lowered.isSuccess(), () -> "lowering: " + lowered.diagnostics());
        var built = GraphBuilder.build(schema, snapshot);
        assertTrue(built.isSuccess(), () -> "graph: " + built.diagnostics());
        GraphModel graph = built.value().graph();

        assertEquals(List.of("rx1", "rx2"), snapshot.associationClassObjects(
                schema, "Prescription", "doctor1", List.of()));
        assertEquals(List.of("rx1", "rx2"), GraphObservation.associationClassObjects(
                graph, schema, "doctor1", "Prescription", List.of()));
        String rx1Key = org.uet.dse.ocl2cypher.graph.GraphKey.object("medical", "rx1");
        assertEquals("ASSOCIATION_CLASS_OBJECT", graph.node(rx1Key).observationRole());
        assertEquals(1, graph.outgoing(rx1Key,
                GraphModel.associationClassSourceParticipant("Prescription")).size());
        assertEquals(1, graph.outgoing(rx1Key,
                GraphModel.associationClassTargetParticipant("Prescription")).size());

        var translated = QCypTranslator.translate(lowered.value());
        assertTrue(translated.isSuccess(), () -> "translation: " + translated.diagnostics());
        List<String> qViolations = QInterpreter.violations(
                schema, graph, lowered.value(), translated.value());
        assertEquals(Set.of("doctor2"), Set.copyOf(qViolations));

        CoreInterpreter.Env coreEnvironment = new CoreInterpreter.Env();
        coreEnvironment.bind(lowered.value().selfVariable(),
                new OclValue.ObjectValue(OclType.clazz("Doctor"), "doctor2"));
        OclValue coreValue = CoreInterpreter.evalUnit(
                schema, snapshot, lowered.value(), coreEnvironment);
        assertEquals(OclValue.BooleanValue.Bool3.FALSE,
                ((OclValue.BooleanValue) coreValue).bool());

        var realized = Realization.realize(translated.value(), graph,
                CypherAst.Dialect.CYPHER_5);
        assertTrue(realized.isSuccess(), () -> "realization: " + realized.diagnostics());
        String cypher = Serializer.cypherText(realized.value());
        org.uet.dse.ocl2cypher.cypher.Neo4jCypherParserGate.assertParses(cypher);
        assertTrue(cypher.contains("AssociationClassObject"), cypher);
        assertTrue(cypher.contains("Prescription_SourceParticipant"), cypher);
        assertTrue(cypher.contains("Prescription_TargetParticipant"), cypher);
    }

    @Test
    void associationClassViolationSetMatchesRealNeo4jWhenEnabled() {
        Assumptions.assumeTrue("true".equalsIgnoreCase(System.getenv(
                "OCL2CYPHER_RUN_NEO4J_E2E")),
                "enable explicitly for database writes");
        String modelKey = "ocl2cypher-e2e-association-class-" + java.util.UUID.randomUUID();
        SchemaModel schema = schema(modelKey);
        Snapshot snapshot = snapshot();
        var frontend = org.uet.dse.ocl2cypher.api.FrontendCompiler.compile(INVARIANT, schema);
        assertTrue(frontend.isSuccess(), () -> "frontend: " + frontend.diagnostics());
        var document = frontend.value().get(0);
        var core = CoreLowering.lower(schema, document, document.constraints.get(0));
        assertTrue(core.isSuccess(), () -> "lowering: " + core.diagnostics());
        var graph = GraphBuilder.build(schema, snapshot);
        assertTrue(graph.isSuccess(), () -> "graph: " + graph.diagnostics());
        var query = QCypTranslator.translate(core.value());
        assertTrue(query.isSuccess(), () -> "translation: " + query.diagnostics());
        var artifact = Realization.realize(query.value(), graph.value().graph(),
                CypherAst.Dialect.CYPHER_5);
        assertTrue(artifact.isSuccess(), () -> "realization: " + artifact.diagnostics());

        String uri = requiredEnvironment("NEO4J_URI");
        String database = requiredEnvironment("NEO4J_DB");
        String user = requiredEnvironment("NEO4J_USER");
        String password = requiredEnvironment("NEO4J_PASSWORD");
        var request = new Neo4jExecutionAdapter.ExecutionRequest(artifact.value(),
                graph.value().graph(), uri, database, user, password, Map.of());
        var execution = Neo4jExecutionAdapter.executeWithMaterializedGraph(request);
        assertTrue(execution.isSuccess(), () -> "Neo4j: " + execution.diagnostics());
        assertEquals(Set.of("doctor2"), Set.copyOf(execution.value().violationIds()));
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        assertTrue(value != null && !value.isBlank(),
                "Neo4j association-class E2E requires " + name);
        return value;
    }

    private static boolean containsAssociationClassCall(OmgAs.OclExpression expression) {
        if (expression instanceof OmgAs.AssociationClassCallExp) return true;
        if (expression instanceof OmgAs.OperationCallExp operation) {
            if (containsAssociationClassCall(operation.source)) return true;
            return operation.argument.stream().anyMatch(
                    AssociationClassEndToEndTest::containsAssociationClassCall);
        }
        if (expression instanceof OmgAs.IteratorExp iterator) {
            return containsAssociationClassCall(iterator.source)
                    || containsAssociationClassCall(iterator.body);
        }
        return false;
    }
}
