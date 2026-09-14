package org.uet.dse.ocl2cypher.graph;

import java.math.BigInteger;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.uet.dse.ocl2cypher.diagnostics.Result;
import org.uet.dse.ocl2cypher.runtime.Boolean3;
import org.uet.dse.ocl2cypher.runtime.OclEquality;
import org.uet.dse.ocl2cypher.runtime.OclType;
import org.uet.dse.ocl2cypher.runtime.OclValue;
import org.uet.dse.ocl2cypher.source.model.QualifierValue;
import org.uet.dse.ocl2cypher.source.model.SchemaModel;
import org.uet.dse.ocl2cypher.source.model.Snapshot;
import org.uet.dse.ocl2cypher.source.model.UmlAssociation;
import org.uet.dse.ocl2cypher.source.model.UmlAttribute;
import org.uet.dse.ocl2cypher.source.model.UmlClass;
import org.uet.dse.ocl2cypher.source.model.UmlQualifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Executable refinement witness for the ten G-5 graph-observer sub-lemmas. */
class GraphObserverCorrespondenceTest {

    private record Fixture(SchemaModel schema, Snapshot snapshot,
                           GraphBuilder.GraphBuildArtifact artifact) {
    }

    @Test
    void identityExtentTypeAndAttributeObserversAgreeWithTheSourceSnapshot() {
        Fixture fixture = fixture();
        SchemaModel schema = fixture.schema();
        Snapshot snapshot = fixture.snapshot();
        GraphModel graph = fixture.artifact().graph();

        // G-ID: the checked correspondence is total, injective and model-scoped.
        Set<String> sourceIds = new LinkedHashSet<>();
        for (Snapshot.ObjectDef object : snapshot.objects()) {
            sourceIds.add(object.stableId());
            assertEquals(GraphKey.object(schema.modelKey(), object.stableId()),
                    fixture.artifact().correspondence().get(object.stableId()));
        }
        assertEquals(sourceIds, fixture.artifact().correspondence().keySet());

        // G-EXTENT and G-TYPE include subclass instances but preserve direct type.
        assertEquals(new LinkedHashSet<>(snapshot.objectsOfClass(schema, "Person")),
                new LinkedHashSet<>(GraphObservation.objectsOfClass(
                        graph, schema, "Person")));
        assertEquals("Employee", GraphObservation.directType(graph, "p1"));
        assertTrue(GraphObservation.conformsTo(graph, schema, "p1", "Person"));

        // G-ATTR and G-BOTTOM: defined, explicitly stored bottom, and absent slot.
        assertOclEquals(snapshot.attributeSlot("p1", "label").orElseThrow(),
                GraphObservation.attribute(graph, schema, "p1", "Person", "label"));
        assertEquals(new OclValue.BottomValue(OclType.BOOLEAN),
                GraphObservation.attribute(graph, schema, "p1", "optionalFlag"));
        assertEquals(new OclValue.BottomValue(OclType.STRING),
                GraphObservation.attribute(graph, schema, "p2", "label"));
    }

    @Test
    void navigationAssociationClassQualifierAndMultiplicityObserversAgree() {
        Fixture fixture = fixture();
        SchemaModel schema = fixture.schema();
        Snapshot snapshot = fixture.snapshot();
        GraphModel graph = fixture.artifact().graph();
        OclValue.StringValue a = new OclValue.StringValue("A");
        OclValue.StringValue b = new OclValue.StringValue("B");

        // G-NAV1: exact one and no-link are distinguished by cardinality.
        assertEquals(snapshot.linkTargets(schema, "primaryItem", "p1", false, List.of()),
                GraphObservation.linkTargets(graph, schema, "p1", "primaryItem",
                        false, List.of()));
        assertEquals(List.of(), GraphObservation.linkTargets(
                graph, schema, "p2", "primaryItem", false, List.of()));

        // G-NAVM: Set support and Bag multiplicity are both preserved.
        assertEquals(new LinkedHashSet<>(snapshot.linkTargets(
                        schema, "items", "p1", false, List.of())),
                new LinkedHashSet<>(GraphObservation.linkTargets(
                        graph, schema, "p1", "items", false, List.of())));
        assertEquals(snapshot.linkTargets(schema, "tags", "p1", false, List.of()),
                GraphObservation.linkTargets(graph, schema, "p1", "tags",
                        false, List.of()));
        assertEquals(2, GraphObservation.linkTargets(
                graph, schema, "p1", "tags", false, List.of()).size());

        // G-QUAL: order, declared codec and pointwise matching agree.
        assertEquals(snapshot.linkTargets(schema, "byCode", "p1", false, List.of(a)),
                GraphObservation.linkTargets(graph, schema, "p1", "byCode",
                        false, List.of(a)));
        assertEquals(snapshot.linkTargets(schema, "byCode", "p1", false, List.of(b)),
                GraphObservation.linkTargets(graph, schema, "p1", "byCode",
                        false, List.of(b)));
        assertEquals(List.of(), GraphObservation.linkTargets(graph, schema, "p1",
                "byCode", false, List.of(new OclValue.StringValue("missing"))));

        // G-AC: both participant navigation and link-object identity agree.
        assertEquals(snapshot.linkTargets(schema, "project", "p1", false, List.of()),
                GraphObservation.linkTargets(graph, schema, "p1", "project",
                        false, List.of()));
        assertEquals(snapshot.associationClassObjects(
                        schema, "Assignment", "p1", List.of()),
                GraphObservation.associationClassObjects(
                        graph, schema, "p1", "Assignment", List.of()));

        // G-MULT: the complete source/graph occurrence multiset has passed ValidRep.
        assertTrue(ValidRepChecker.check(schema, snapshot, graph).isSuccess());
    }

    @Test
    void invalidMultiplicityAndDuplicateSetOccurrenceCannotWitnessValidRep() {
        SchemaModel schema = schema();
        Snapshot upperViolation = baseObjects()
                .link("Primary", "p1", "i1")
                .link("Primary", "p1", "i2")
                .build();
        Result<GraphBuilder.GraphBuildArtifact> upper = GraphBuilder.build(
                schema, upperViolation);
        assertTrue(upper.isFailure());
        assertEquals("VALIDREP_MULTIPLICITY", upper.primaryDiagnostic().code());

        Snapshot duplicateSet = baseObjects()
                .link("OwnedSet", "p1", "i1")
                .link("OwnedSet", "p1", "i1")
                .build();
        Result<GraphBuilder.GraphBuildArtifact> duplicate = GraphBuilder.build(
                schema, duplicateSet);
        assertTrue(duplicate.isFailure());
        assertEquals("VALIDREP_SET_DUPLICATE", duplicate.primaryDiagnostic().code());
    }

    private static Fixture fixture() {
        SchemaModel schema = schema();
        Snapshot snapshot = baseObjects()
                .object("a1", "Assignment")
                .attribute("p1", "label", new OclValue.StringValue("Alice"))
                .attribute("p1", "optionalFlag", new OclValue.BottomValue(OclType.BOOLEAN))
                .attribute("p1", "level", integer(3))
                .attribute("a1", "hours", integer(8))
                .link("Primary", "p1", "i1")
                .link("OwnedSet", "p1", "i1")
                .link("OwnedSet", "p1", "i2")
                .link("TagBag", "p1", "i1")
                .link("TagBag", "p1", "i1")
                .link("Qualified", "p1", "i1", List.of(
                        new QualifierValue("code", new OclValue.StringValue("A"))))
                .link("Qualified", "p1", "i2", List.of(
                        new QualifierValue("code", new OclValue.StringValue("B"))))
                .associationClassLink("a1", "Assignment", "p1", "pr1")
                .build();
        Result<GraphBuilder.GraphBuildArtifact> built = GraphBuilder.build(schema, snapshot);
        assertTrue(built.isSuccess(), () -> built.isFailure()
                ? built.primaryDiagnostic().toString() : "expected successful graph build");
        return new Fixture(schema, snapshot, built.value());
    }

    private static SchemaModel schema() {
        return SchemaModel.builder("g5")
                .clazz(UmlClass.of("Person"))
                .clazz(UmlClass.of("Employee", "Person"))
                .clazz(UmlClass.of("Item"))
                .clazz(UmlClass.of("Project"))
                .clazz(new UmlClass("Assignment", "Assignment", false, true, List.of()))
                .attribute(UmlAttribute.of("Person", "label", OclType.STRING))
                .attribute(UmlAttribute.of("Person", "optionalFlag", OclType.BOOLEAN))
                .attribute(UmlAttribute.of("Employee", "level", OclType.INTEGER))
                .attribute(UmlAttribute.of("Assignment", "hours", OclType.INTEGER))
                .association(UmlAssociation.oneToOne("Primary", "Person",
                        "primaryOwner", "Item", "primaryItem"))
                .association(new UmlAssociation("OwnedSet", "OwnedSet",
                        "Person", "setOwner", 0, -1,
                        "Item", "items", 0, -1, List.of(), false, true))
                .association(new UmlAssociation("TagBag", "TagBag",
                        "Person", "bagOwner", 0, -1,
                        "Item", "tags", 0, -1, List.of(), false, false))
                .association(new UmlAssociation("Qualified", "Qualified",
                        "Person", "qualifiedOwner", 0, -1,
                        "Item", "byCode", 0, -1,
                        List.of(new UmlQualifier("code", OclType.STRING,
                                List.of(new OclValue.StringValue("A"),
                                        new OclValue.StringValue("B")))), false, true))
                .association(new UmlAssociation("Assignment", "Assignment",
                        "Person", "worker", 0, -1,
                        "Project", "project", 0, -1, List.of(), false, true))
                .build();
    }

    private static Snapshot.Builder baseObjects() {
        return Snapshot.builder()
                .object("p1", "Employee")
                .object("p2", "Person")
                .object("i1", "Item")
                .object("i2", "Item")
                .object("pr1", "Project");
    }

    private static OclValue.IntegerValue integer(long value) {
        return new OclValue.IntegerValue(BigInteger.valueOf(value));
    }

    private static void assertOclEquals(OclValue expected, OclValue actual) {
        assertEquals(OclEquality.BoolKind.TRUE, OclEquality.equal(expected, actual));
    }
}
