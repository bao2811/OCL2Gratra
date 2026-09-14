package org.uet.dse.ocl2cypher.graph;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.uet.dse.ocl2cypher.source.model.Snapshot;

class InstanceIdentityBijectionTest {

    private static void node(GraphModel g, String key, String id, String role, GraphModel.Projection projection) {
        g.addNode(new GraphModel.Node(key, "m", projection, role, List.of(),
                Map.of("modelKey", "m", "use_id", id)));
    }

    private static boolean has(GraphModel g, Snapshot sn, String code) {
        return InstanceRepValidator.validate(g, sn).stream().anyMatch(e -> e.code().equals(code));
    }

    @Test
    void rejectsTwoNodesForOneSourceIdentity() {
        var sn = Snapshot.builder().object("ac", "A").build();
        var g = new GraphModel("m");
        node(g, "one", "ac", "ASSOCIATION_CLASS_OBJECT", GraphModel.Projection.INSTANCE);
        node(g, "two", "ac", "OBJECT", GraphModel.Projection.INSTANCE);
        assertTrue(has(g, sn, "G_DUPLICATE_OBJECT_NODE"));
    }

    @Test
    void schemaNodeCannotWitnessAnObject() {
        var sn = Snapshot.builder().object("ac", "A").build();
        var g = new GraphModel("m");
        node(g, "schema", "ac", "UML_CLASS", GraphModel.Projection.SCHEMA);
        assertTrue(has(g, sn, "G_MISSING_OBJECT_NODE"));
    }

    @Test
    void rejectsWrongPartitionAndGhost() {
        var sn = Snapshot.builder().build();
        var g = new GraphModel("m");
        node(g, "ghost", "missing", "ASSOCIATION_CLASS_OBJECT", GraphModel.Projection.SCHEMA);
        assertTrue(has(g, sn, "G_OBJECT_PROJECTION"));
        assertTrue(has(g, sn, "G_GHOST_OBJECT"));
    }

    @Test
    void distinctOccurrencesWithSameParticipantsKeepDistinctIdentities() {
        var sn = Snapshot.builder().object("s", "S").object("t", "T")
                .object("ac1", "A").object("ac2", "A").build();
        var g = new GraphModel("m");
        for (String id : List.of("s", "t", "ac1", "ac2")) {
            node(g, id, id, id.startsWith("ac") ? "ASSOCIATION_CLASS_OBJECT" : "OBJECT", GraphModel.Projection.INSTANCE);
        }
        for (String ac : List.of("ac1", "ac2")) {
            g.addRelationship(new GraphModel.Relationship(ac + "-s", "m", GraphModel.Projection.INSTANCE,
                    "A_SourceParticipant", ac, "s", Map.of("modelKey", "m")));
            g.addRelationship(new GraphModel.Relationship(ac + "-t", "m", GraphModel.Projection.INSTANCE,
                    "A_TargetParticipant", ac, "t", Map.of("modelKey", "m")));
        }
        assertTrue(InstanceRepValidator.validate(g, sn).isEmpty());
        assertNotEquals(g.node("ac1").stableKey(), g.node("ac2").stableKey());
        assertEquals(g.outgoing("ac1", "A_SourceParticipant").get(0).targetKey(),
                g.outgoing("ac2", "A_SourceParticipant").get(0).targetKey());
        // This is an identity witness, not validation of source AC membership/participants.
    }

    @Test
    void acObjectPreservesParticipantLinks() {
        var sn = Snapshot.builder().object("s",
                "S").object("t", "T")
                .object("ac", "A").build();
        var g = new GraphModel("m");
        node(g, "s", "s", "OBJECT",
                GraphModel.Projection.INSTANCE);
        node(g, "t", "t", "OBJECT",
                GraphModel.Projection.INSTANCE);
        node(g, "ac", "ac",
                "ASSOCIATION_CLASS_OBJECT",
                GraphModel.Projection.INSTANCE);
        g.addRelationship(new GraphModel.Relationship("ac-s", "m",
                GraphModel.Projection.INSTANCE,
                "A_SourceParticipant", "ac", "s",
                Map.of("modelKey", "m")));
        g.addRelationship(new GraphModel.Relationship("ac-t", "m",
                GraphModel.Projection.INSTANCE,
                "A_TargetParticipant", "ac", "t",
                Map.of("modelKey", "m")));
        assertTrue(InstanceRepValidator.validate(g,
                sn).isEmpty());
        assertEquals(1, g.outgoing("ac",
                "A_SourceParticipant").size());
        assertEquals(1, g.outgoing("ac",
                "A_TargetParticipant").size());
        assertEquals("s", g.outgoing("ac",
                "A_SourceParticipant").get(0).targetKey());
        assertEquals("t", g.outgoing("ac",
                "A_TargetParticipant").get(0).targetKey());
    }

    @Test
    void twoOccurrencesWithSameTuplePreserveParticipantLinks() {
        var sn = Snapshot.builder().object("s",
                "S").object("t", "T")
                .object("ac1", "A").object("ac2",
                "A").build();
        var g = new GraphModel("m");
        for (String id : List.of("s", "t", "ac1",
                "ac2")) {
            node(g, id, id, id.startsWith("ac")
                    ? "ASSOCIATION_CLASS_OBJECT" : "OBJECT",
                    GraphModel.Projection.INSTANCE);
        }
        for (String ac : List.of("ac1", "ac2")) {
            g.addRelationship(new GraphModel.Relationship(ac + "-s", "m",
                    GraphModel.Projection.INSTANCE,
                    "A_SourceParticipant", ac, "s",
                    Map.of("modelKey", "m")));
            g.addRelationship(new GraphModel.Relationship(ac + "-t", "m",
                    GraphModel.Projection.INSTANCE,
                    "A_TargetParticipant", ac, "t",
                    Map.of("modelKey", "m")));
        }
        assertTrue(InstanceRepValidator.validate(g,
                sn).isEmpty());
        for (String ac : List.of("ac1", "ac2")) {
            assertEquals(1, g.outgoing(ac,
                    "A_SourceParticipant").size());
            assertEquals(1, g.outgoing(ac,
                    "A_TargetParticipant").size());
            assertEquals("s", g.outgoing(ac,
                    "A_SourceParticipant").get(0).targetKey());
            assertEquals("t", g.outgoing(ac,
                    "A_TargetParticipant").get(0).targetKey());
        }
        assertNotEquals(g.node("ac1").stableKey(),
                g.node("ac2").stableKey());
    }
}
