package org.uet.dse.neo4jtgg.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the delta (set of changes) between two model snapshots on the same workspace side.
 * Used to drive incremental synchronization: only changed elements need to be propagated.
 */
public record ModelDelta(
        WorkspaceSide side,
        List<ObjectChange> addedObjects,
        List<ObjectChange> modifiedObjects,
        List<ObjectChange> deletedObjects,
        List<LinkChange> addedLinks,
        List<LinkChange> deletedLinks,
        long previousTimestamp,
        long currentTimestamp) {

    public ModelDelta {
        addedObjects = List.copyOf(new ArrayList<>(addedObjects != null ? addedObjects : List.of()));
        modifiedObjects = List.copyOf(new ArrayList<>(modifiedObjects != null ? modifiedObjects : List.of()));
        deletedObjects = List.copyOf(new ArrayList<>(deletedObjects != null ? deletedObjects : List.of()));
        addedLinks = List.copyOf(new ArrayList<>(addedLinks != null ? addedLinks : List.of()));
        deletedLinks = List.copyOf(new ArrayList<>(deletedLinks != null ? deletedLinks : List.of()));
    }

    public boolean isEmpty() {
        return addedObjects.isEmpty()
                && modifiedObjects.isEmpty()
                && deletedObjects.isEmpty()
                && addedLinks.isEmpty()
                && deletedLinks.isEmpty();
    }

    public int totalChanges() {
        return addedObjects.size()
                + modifiedObjects.size()
                + deletedObjects.size()
                + addedLinks.size()
                + deletedLinks.size();
    }

    public static ModelDelta empty(WorkspaceSide side) {
        return new ModelDelta(side, List.of(), List.of(), List.of(), List.of(), List.of(), 0L, 0L);
    }

    public String toDisplayText() {
        StringBuilder sb = new StringBuilder();
        sb.append("Delta [").append(side.getDisplayName()).append("]: ");
        sb.append("added=").append(addedObjects.size());
        sb.append(", modified=").append(modifiedObjects.size());
        sb.append(", deleted=").append(deletedObjects.size());
        sb.append(", addedLinks=").append(addedLinks.size());
        sb.append(", deletedLinks=").append(deletedLinks.size());

        if (!addedObjects.isEmpty()) {
            sb.append("\n  Added objects:");
            for (ObjectChange change : addedObjects) {
                sb.append("\n    + ").append(change.toDisplayText());
            }
        }
        if (!modifiedObjects.isEmpty()) {
            sb.append("\n  Modified objects:");
            for (ObjectChange change : modifiedObjects) {
                sb.append("\n    ~ ").append(change.toDisplayText());
            }
        }
        if (!deletedObjects.isEmpty()) {
            sb.append("\n  Deleted objects:");
            for (ObjectChange change : deletedObjects) {
                sb.append("\n    - ").append(change.toDisplayText());
            }
        }
        if (!addedLinks.isEmpty()) {
            sb.append("\n  Added links:");
            for (LinkChange change : addedLinks) {
                sb.append("\n    + ").append(change.toDisplayText());
            }
        }
        if (!deletedLinks.isEmpty()) {
            sb.append("\n  Deleted links:");
            for (LinkChange change : deletedLinks) {
                sb.append("\n    - ").append(change.toDisplayText());
            }
        }
        return sb.toString();
    }
}
