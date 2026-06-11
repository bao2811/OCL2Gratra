package org.uet.dse.neo4jtgg.engine;

import org.uet.dse.neo4j.model.FullObjectSnapshot;
import org.uet.dse.neo4j.model.LinkState;
import org.uet.dse.neo4j.model.ObjectState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Indexed view of a {@link FullObjectSnapshot} for efficient lookup by class name
 * and association membership. Used by all TGG engines for pattern matching.
 */
public final class SnapshotIndex {

    private final FullObjectSnapshot snapshot;
    private final Map<String, List<ObjectState>> objectsByClass = new LinkedHashMap<>();
    private final Map<String, Set<String>> linksByAssociation = new LinkedHashMap<>();
    private final Map<String, List<LinkState>> linksByObject = new LinkedHashMap<>();

    public SnapshotIndex(FullObjectSnapshot snapshot) {
        this.snapshot = snapshot;
        for (ObjectState obj : snapshot.objects.values()) {
            objectsByClass.computeIfAbsent(obj.className, k -> new ArrayList<>()).add(obj);
        }
        for (LinkState link : snapshot.links.values()) {
            if (link.participants.size() >= 2) {
                String left = link.participants.get(0);
                String right = link.participants.get(1);
                linksByAssociation
                        .computeIfAbsent(link.assocName, k -> new LinkedHashSet<>())
                        .add(left + "->" + right);
                linksByObject
                        .computeIfAbsent(left, k -> new ArrayList<>())
                        .add(link);
                linksByObject
                        .computeIfAbsent(right, k -> new ArrayList<>())
                        .add(link);
            }
        }
    }

    public List<ObjectState> findByClass(String className) {
        return objectsByClass.getOrDefault(className, List.of());
    }

    public ObjectState findObject(String objectId) {
        return snapshot.objects.get(objectId);
    }

    public boolean hasAssociation(String assocName, String leftObjectId, String rightObjectId) {
        return linksByAssociation.getOrDefault(assocName, Set.of())
                .contains(leftObjectId + "->" + rightObjectId);
    }

    public List<LinkState> findLinksFrom(String objectId) {
        return linksByObject.getOrDefault(objectId, List.of());
    }

    public void registerObject(ObjectState object) {
        if (object == null || object.name == null) {
            return;
        }
        snapshot.objects.put(object.name, object);
        objectsByClass.computeIfAbsent(object.className, key -> new ArrayList<>()).add(object);
    }

    public void registerLink(LinkState link) {
        if (link == null || link.participants == null || link.participants.size() < 2) {
            return;
        }
        snapshot.links.put(link.getIdentity(), link);
        String left = link.participants.get(0);
        String right = link.participants.get(1);
        linksByAssociation
                .computeIfAbsent(link.assocName, key -> new LinkedHashSet<>())
                .add(left + "->" + right);
        linksByObject
                .computeIfAbsent(left, key -> new ArrayList<>())
                .add(link);
        linksByObject
                .computeIfAbsent(right, key -> new ArrayList<>())
                .add(link);
    }

    public int objectCount() {
        return snapshot.objects.size();
    }

    public int linkCount() {
        return snapshot.links.size();
    }
}
