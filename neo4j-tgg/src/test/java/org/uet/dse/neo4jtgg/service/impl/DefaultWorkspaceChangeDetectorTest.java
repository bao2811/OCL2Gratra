package org.uet.dse.neo4jtgg.service.impl;

import org.junit.jupiter.api.Test;
import org.uet.dse.neo4j.model.FullObjectSnapshot;
import org.uet.dse.neo4j.model.LinkState;
import org.uet.dse.neo4j.model.ObjectState;
import org.uet.dse.neo4jtgg.model.NormalizedChangeSet;
import org.uet.dse.neo4jtgg.model.TggWorkspaceContext;
import org.uet.dse.neo4jtgg.model.WorkspaceSide;

import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultWorkspaceChangeDetectorTest {

    @Test
    void detectsSnapshotChangesAsNormalizedEvents() {
        DefaultWorkspaceChangeDetector detector = new DefaultWorkspaceChangeDetector();
        FullObjectSnapshot previous = new FullObjectSnapshot();
        previous.objects.put("family1", object("family1", "Family", "Simpson"));
        previous.links.put("familyRegister_family1_register1", link("familyRegister", "family1", "register1"));

        FullObjectSnapshot current = new FullObjectSnapshot();
        current.objects.put("family1", object("family1", "Family", "Bouvier"));
        current.objects.put("family2", object("family2", "Family", "Burns"));
        current.links.put("familyRegister_family2_register1", link("familyRegister", "family2", "register1"));

        NormalizedChangeSet changeSet = detector.detectSourceChanges(new TggWorkspaceContext(null, null), previous, current);

        assertEquals(4, changeSet.totalEvents());
        assertEquals(NormalizedChangeSet.ChangeSourceKind.SNAPSHOT_DIFF, changeSet.sourceKind());
        assertTrue(changeSet.toDisplayText().contains("ATTRIBUTE UPDATE Family `family1.name`"));
        assertTrue(changeSet.toDisplayText().contains("OBJECT CREATE Family `family2`"));
    }

    private ObjectState object(String id, String className, String name) {
        ObjectState state = new ObjectState();
        state.name = id;
        state.className = className;
        state.primitiveValues = new LinkedHashMap<>();
        state.primitiveValues.put("name", name);
        state.objectReferences = new LinkedHashMap<>();
        return state;
    }

    private LinkState link(String assocName, String left, String right) {
        LinkState state = new LinkState();
        state.assocName = assocName;
        state.participants = List.of(left, right);
        return state;
    }
}
