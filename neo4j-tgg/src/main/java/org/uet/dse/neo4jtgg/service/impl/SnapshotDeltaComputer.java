package org.uet.dse.neo4jtgg.service.impl;

import org.uet.dse.neo4j.model.FullObjectSnapshot;
import org.uet.dse.neo4j.model.LinkState;
import org.uet.dse.neo4j.model.ObjectState;
import org.uet.dse.neo4jtgg.model.LinkChange;
import org.uet.dse.neo4jtgg.model.ModelDelta;
import org.uet.dse.neo4jtgg.model.ObjectChange;
import org.uet.dse.neo4jtgg.model.WorkspaceSide;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Computes the delta between two {@link FullObjectSnapshot}s of the same workspace side.
 * The result is a {@link ModelDelta} that identifies added, modified, and deleted objects and links.
 * This delta drives incremental synchronization: only changed elements need to be propagated.
 */
public final class SnapshotDeltaComputer {

    private SnapshotDeltaComputer() {
    }

    /**
     * Compute delta between previous and current snapshots.
     *
     * @param side     the workspace side being compared
     * @param previous the previous snapshot (may be null for initial import)
     * @param current  the current snapshot
     * @return a {@link ModelDelta} describing all changes
     */
    public static ModelDelta compute(WorkspaceSide side,
                                     FullObjectSnapshot previous,
                                     FullObjectSnapshot current) {
        if (previous == null) {
            return computeInitialDelta(side, current);
        }

        List<ObjectChange> addedObjects = new ArrayList<>();
        List<ObjectChange> modifiedObjects = new ArrayList<>();
        List<ObjectChange> deletedObjects = new ArrayList<>();

        // Detect added and modified objects
        for (Map.Entry<String, ObjectState> entry : current.objects.entrySet()) {
            String objectId = entry.getKey();
            ObjectState currentState = entry.getValue();
            ObjectState previousState = previous.objects.get(objectId);

            if (previousState == null) {
                addedObjects.add(ObjectChange.added(
                        objectId,
                        currentState.className,
                        extractAttributes(currentState)));
            } else if (hasAttributeDifference(previousState, currentState)) {
                modifiedObjects.add(ObjectChange.modified(
                        objectId,
                        currentState.className,
                        extractAttributes(previousState),
                        extractAttributes(currentState)));
            }
        }

        // Detect deleted objects
        for (Map.Entry<String, ObjectState> entry : previous.objects.entrySet()) {
            String objectId = entry.getKey();
            if (!current.objects.containsKey(objectId)) {
                ObjectState deletedState = entry.getValue();
                deletedObjects.add(ObjectChange.deleted(
                        objectId,
                        deletedState.className,
                        extractAttributes(deletedState)));
            }
        }

        // Detect link changes
        Map<String, LinkState> previousLinks = indexLinks(previous);
        Map<String, LinkState> currentLinks = indexLinks(current);

        List<LinkChange> addedLinks = new ArrayList<>();
        for (Map.Entry<String, LinkState> entry : currentLinks.entrySet()) {
            if (!previousLinks.containsKey(entry.getKey())) {
                LinkState link = entry.getValue();
                addedLinks.add(LinkChange.of(link.assocName, link.participants));
            }
        }

        List<LinkChange> deletedLinks = new ArrayList<>();
        for (Map.Entry<String, LinkState> entry : previousLinks.entrySet()) {
            if (!currentLinks.containsKey(entry.getKey())) {
                LinkState link = entry.getValue();
                deletedLinks.add(LinkChange.of(link.assocName, link.participants));
            }
        }

        return new ModelDelta(side,
                addedObjects, modifiedObjects, deletedObjects,
                addedLinks, deletedLinks,
                0L, System.currentTimeMillis());
    }

    private static ModelDelta computeInitialDelta(WorkspaceSide side, FullObjectSnapshot current) {
        List<ObjectChange> addedObjects = new ArrayList<>();
        for (ObjectState state : current.objects.values()) {
            addedObjects.add(ObjectChange.added(state.name, state.className, extractAttributes(state)));
        }
        List<LinkChange> addedLinks = new ArrayList<>();
        for (LinkState link : current.links.values()) {
            addedLinks.add(LinkChange.of(link.assocName, link.participants));
        }
        return new ModelDelta(side,
                addedObjects, List.of(), List.of(),
                addedLinks, List.of(),
                0L, System.currentTimeMillis());
    }

    private static Map<String, Object> extractAttributes(ObjectState state) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        if (state.primitiveValues != null) {
            attributes.putAll(state.primitiveValues);
        }
        return attributes;
    }

    private static boolean hasAttributeDifference(ObjectState previous, ObjectState current) {
        if (!Objects.equals(previous.className, current.className)) {
            return true;
        }
        Map<String, Object> prevAttrs = previous.primitiveValues != null ? previous.primitiveValues : Map.of();
        Map<String, Object> currAttrs = current.primitiveValues != null ? current.primitiveValues : Map.of();

        if (prevAttrs.size() != currAttrs.size()) {
            return true;
        }
        for (Map.Entry<String, Object> entry : prevAttrs.entrySet()) {
            Object currValue = currAttrs.get(entry.getKey());
            if (!Objects.equals(entry.getValue(), currValue)) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, LinkState> indexLinks(FullObjectSnapshot snapshot) {
        Map<String, LinkState> index = new LinkedHashMap<>();
        for (LinkState link : snapshot.links.values()) {
            String identity = link.getIdentity();
            index.put(identity, link);
        }
        return index;
    }
}
