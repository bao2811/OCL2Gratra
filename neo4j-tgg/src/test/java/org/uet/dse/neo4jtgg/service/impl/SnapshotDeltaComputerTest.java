package org.uet.dse.neo4jtgg.service.impl;

import org.junit.jupiter.api.Test;
import org.uet.dse.neo4j.model.FullObjectSnapshot;
import org.uet.dse.neo4j.model.LinkState;
import org.uet.dse.neo4j.model.ObjectState;
import org.uet.dse.neo4jtgg.model.ModelDelta;
import org.uet.dse.neo4jtgg.model.WorkspaceSide;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SnapshotDeltaComputerTest {

    @Test
    void testInitialDelta_AllObjectsAreAdded() {
        FullObjectSnapshot current = new FullObjectSnapshot();
        current.objects.put("family1", makeObject("family1", "Family", Map.of("name", "Simpson")));
        current.objects.put("member1", makeObject("member1", "FamilyMember", Map.of("name", "Bart")));

        ModelDelta delta = SnapshotDeltaComputer.compute(WorkspaceSide.SOURCE, null, current);

        assertFalse(delta.isEmpty());
        assertEquals(2, delta.addedObjects().size());
        assertEquals(0, delta.modifiedObjects().size());
        assertEquals(0, delta.deletedObjects().size());
        assertTrue(delta.addedObjects().stream().anyMatch(o -> "family1".equals(o.objectId()) && "Family".equals(o.className())));
        assertTrue(delta.addedObjects().stream().anyMatch(o -> "member1".equals(o.objectId()) && "FamilyMember".equals(o.className())));
    }

    @Test
    void testNoChanges_EmptyDelta() {
        FullObjectSnapshot snapshot = new FullObjectSnapshot();
        snapshot.objects.put("family1", makeObject("family1", "Family", Map.of("name", "Simpson")));

        ModelDelta delta = SnapshotDeltaComputer.compute(WorkspaceSide.SOURCE, snapshot, snapshot);

        assertTrue(delta.isEmpty());
        assertEquals(0, delta.totalChanges());
    }

    @Test
    void testAddedObject_DetectedCorrectly() {
        FullObjectSnapshot previous = new FullObjectSnapshot();
        previous.objects.put("family1", makeObject("family1", "Family", Map.of("name", "Simpson")));

        FullObjectSnapshot current = new FullObjectSnapshot();
        current.objects.put("family1", makeObject("family1", "Family", Map.of("name", "Simpson")));
        current.objects.put("member1", makeObject("member1", "FamilyMember", Map.of("name", "Lisa")));

        ModelDelta delta = SnapshotDeltaComputer.compute(WorkspaceSide.SOURCE, previous, current);

        assertEquals(1, delta.addedObjects().size());
        assertEquals("member1", delta.addedObjects().get(0).objectId());
        assertEquals("FamilyMember", delta.addedObjects().get(0).className());
        assertEquals(0, delta.modifiedObjects().size());
        assertEquals(0, delta.deletedObjects().size());
    }

    @Test
    void testDeletedObject_DetectedCorrectly() {
        FullObjectSnapshot previous = new FullObjectSnapshot();
        previous.objects.put("family1", makeObject("family1", "Family", Map.of("name", "Simpson")));
        previous.objects.put("member1", makeObject("member1", "FamilyMember", Map.of("name", "Bart")));

        FullObjectSnapshot current = new FullObjectSnapshot();
        current.objects.put("family1", makeObject("family1", "Family", Map.of("name", "Simpson")));

        ModelDelta delta = SnapshotDeltaComputer.compute(WorkspaceSide.SOURCE, previous, current);

        assertEquals(0, delta.addedObjects().size());
        assertEquals(0, delta.modifiedObjects().size());
        assertEquals(1, delta.deletedObjects().size());
        assertEquals("member1", delta.deletedObjects().get(0).objectId());
    }

    @Test
    void testModifiedAttribute_DetectedCorrectly() {
        FullObjectSnapshot previous = new FullObjectSnapshot();
        previous.objects.put("member1", makeObject("member1", "FamilyMember", Map.of("name", "Bart")));

        FullObjectSnapshot current = new FullObjectSnapshot();
        current.objects.put("member1", makeObject("member1", "FamilyMember", Map.of("name", "Hugo")));

        ModelDelta delta = SnapshotDeltaComputer.compute(WorkspaceSide.SOURCE, previous, current);

        assertEquals(0, delta.addedObjects().size());
        assertEquals(1, delta.modifiedObjects().size());
        assertEquals(0, delta.deletedObjects().size());
        assertEquals("member1", delta.modifiedObjects().get(0).objectId());
        assertEquals("Bart", delta.modifiedObjects().get(0).previousAttributes().get("name"));
        assertEquals("Hugo", delta.modifiedObjects().get(0).currentAttributes().get("name"));
    }

    @Test
    void testAddedLink_DetectedCorrectly() {
        FullObjectSnapshot previous = new FullObjectSnapshot();
        previous.objects.put("family1", makeObject("family1", "Family", Map.of("name", "Simpson")));

        FullObjectSnapshot current = new FullObjectSnapshot();
        current.objects.put("family1", makeObject("family1", "Family", Map.of("name", "Simpson")));
        LinkState newLink = new LinkState();
        newLink.assocName = "Sons";
        newLink.participants = List.of("family1", "member1");
        current.links.put(newLink.getIdentity(), newLink);

        ModelDelta delta = SnapshotDeltaComputer.compute(WorkspaceSide.SOURCE, previous, current);

        assertEquals(1, delta.addedLinks().size());
        assertEquals("Sons", delta.addedLinks().get(0).associationName());
        assertEquals(List.of("family1", "member1"), delta.addedLinks().get(0).participants());
    }

    @Test
    void testDeletedLink_DetectedCorrectly() {
        LinkState link = new LinkState();
        link.assocName = "Father";
        link.participants = List.of("family1", "member1");

        FullObjectSnapshot previous = new FullObjectSnapshot();
        previous.objects.put("family1", makeObject("family1", "Family", Map.of("name", "Simpson")));
        previous.links.put(link.getIdentity(), link);

        FullObjectSnapshot current = new FullObjectSnapshot();
        current.objects.put("family1", makeObject("family1", "Family", Map.of("name", "Simpson")));

        ModelDelta delta = SnapshotDeltaComputer.compute(WorkspaceSide.SOURCE, previous, current);

        assertEquals(1, delta.deletedLinks().size());
        assertEquals("Father", delta.deletedLinks().get(0).associationName());
    }

    @Test
    void testMixedChanges_AllDetected() {
        FullObjectSnapshot previous = new FullObjectSnapshot();
        previous.objects.put("family1", makeObject("family1", "Family", Map.of("name", "Simpson")));
        previous.objects.put("member1", makeObject("member1", "FamilyMember", Map.of("name", "Bart")));
        previous.objects.put("member2", makeObject("member2", "FamilyMember", Map.of("name", "Lisa")));

        FullObjectSnapshot current = new FullObjectSnapshot();
        current.objects.put("family1", makeObject("family1", "Family", Map.of("name", "Simpson")));
        current.objects.put("member1", makeObject("member1", "FamilyMember", Map.of("name", "Hugo")));
        // member2 deleted
        current.objects.put("member3", makeObject("member3", "FamilyMember", Map.of("name", "Maggie")));

        ModelDelta delta = SnapshotDeltaComputer.compute(WorkspaceSide.SOURCE, previous, current);

        assertEquals(1, delta.addedObjects().size(), "member3 added");
        assertEquals(1, delta.modifiedObjects().size(), "member1 modified");
        assertEquals(1, delta.deletedObjects().size(), "member2 deleted");
        assertEquals(3, delta.totalChanges());
    }

    @Test
    void testDisplayText_FormatsCorrectly() {
        FullObjectSnapshot previous = new FullObjectSnapshot();
        FullObjectSnapshot current = new FullObjectSnapshot();
        current.objects.put("obj1", makeObject("obj1", "Family", Map.of("name", "Test")));

        ModelDelta delta = SnapshotDeltaComputer.compute(WorkspaceSide.SOURCE, previous, current);
        String display = delta.toDisplayText();

        assertTrue(display.contains("added=1"));
        assertTrue(display.contains("obj1 : Family"));
    }

    private ObjectState makeObject(String name, String className, Map<String, Object> attributes) {
        ObjectState state = new ObjectState();
        state.name = name;
        state.className = className;
        state.primitiveValues = new LinkedHashMap<>(attributes);
        state.objectReferences = new LinkedHashMap<>();
        return state;
    }
}
