package org.uet.dse.ocl2cypher.graph;

import java.util.*;

/**
 * A concrete {@code GraphInstanceModel G : PGMM}.
 *
 * <p>The graph holds ONE instance model containing four logical partitions —
 * {@code G_repository, G_schema, G_type, G_snapshot} — distinguished by
 * {@link Projection}. They are partitions of this single graph, never four
 * graphs or extra metamodel roots.
 *
 * <p>Physical relationships always have direction source → target;
 * {@code INCOMING}/{@code BOTH} are logical navigation directions owned by
 * observers only. Identity is the stable model-scoped key; internal database
 * ids are never semantic identity.
 */
public final class GraphModel {

    public enum Projection {
        SCHEMA,
        INSTANCE,
        TYPING,
        REPOSITORY_CONTROL
    }

    /** Canonical relationship roles; each maps to one PGMM physical type. */
    public static final String OBJECT_INSTANCE_OF = "ObjectInstanceOf";
    public static final String INSTANCE_OF = "InstanceOf";
    public static final String HAS_ATTRIBUTE = "HasAttribute";
    public static final String OBJECT_HAS_ATTRIBUTE = "ObjectHasAttribute";
    public static final String EXTENDS = "Extends";
    public static final String ASSOCIATION_SOURCE_END = "AssociationSourceEnd";
    public static final String ASSOCIATION_TARGET_END = "AssociationTargetEnd";
    public static final String LINK_ASSOCIATE_WITH = "LinkAssociateWith";

    public static String associationClassSourceParticipant(String associationKey) {
        return relationshipTypePrefix(associationKey) + "_SourceParticipant";
    }

    public static String associationClassTargetParticipant(String associationKey) {
        return relationshipTypePrefix(associationKey) + "_TargetParticipant";
    }

    private static String relationshipTypePrefix(String key) {
        String normalized = key.replaceAll("[^A-Za-z0-9_]", "_");
        return normalized.isEmpty() ? "AssociationClass" : normalized;
    }

    public record Node(String stableKey,
                       String modelKey,
                       Projection projection,
                       String observationRole,
                       List<String> labels,
                       Map<String, String> properties) {
        public Node {
            properties = Map.copyOf(properties);
        }
    }

    public record Relationship(String stableKey,
                               String modelKey,
                               Projection projection,
                               String physicalType,
                               String sourceKey,
                               String targetKey,
                               Map<String, String> properties) {
        public Relationship {
            properties = Map.copyOf(properties);
        }
    }

    private final String modelKey;
    private final Map<String, Node> nodes = new LinkedHashMap<>();
    private final Map<String, Relationship> relationships = new LinkedHashMap<>();

    public GraphModel(String modelKey) {
        this.modelKey = Objects.requireNonNull(modelKey);
    }

    public String modelKey() {
        return modelKey;
    }

    public void addNode(Node n) {
        requireModelScope(n.modelKey(), n.properties(), n.stableKey());
        if (nodes.containsKey(n.stableKey) || relationships.containsKey(n.stableKey)) {
            throw new IllegalStateException("G_DUPLICATE_STABLE_KEY: " + n.stableKey);
        }
        nodes.put(n.stableKey, n);
    }

    public void addRelationship(Relationship r) {
        requireModelScope(r.modelKey(), r.properties(), r.stableKey());
        if (nodes.containsKey(r.stableKey) || relationships.containsKey(r.stableKey)) {
            throw new IllegalStateException("G_DUPLICATE_STABLE_KEY: " + r.stableKey);
        }
        if (!nodes.containsKey(r.sourceKey) || !nodes.containsKey(r.targetKey)) {
            throw new IllegalStateException("G_DANGLING_ENDPOINT: " + r.stableKey);
        }
        relationships.put(r.stableKey, r);
    }

    private void requireModelScope(String declaredModelKey,
                                    Map<String, String> properties,
                                    String stableKey) {
        if (!modelKey.equals(declaredModelKey)
                || !modelKey.equals(properties.get("modelKey"))) {
            throw new IllegalArgumentException("G_MODEL_SCOPE: " + stableKey);
        }
    }

    public Collection<Node> nodes() {
        return nodes.values();
    }

    public Collection<Relationship> relationships() {
        return relationships.values();
    }

    public Node node(String stableKey) {
        Node n = nodes.get(stableKey);
        if (n == null) {
            throw new NoSuchElementException("no node " + stableKey);
        }
        return n;
    }

    /** Outgoing relationships of a given physical type from a node. */
    public List<Relationship> outgoing(String fromKey, String physicalType) {
        List<Relationship> r = new ArrayList<>();
        for (Relationship rel : relationships.values()) {
            if (rel.sourceKey.equals(fromKey) && rel.physicalType.equals(physicalType)) {
                r.add(rel);
            }
        }
        return r;
    }

    /** Incoming relationships of a given physical type into a node. */
    public List<Relationship> incoming(String toKey, String physicalType) {
        List<Relationship> r = new ArrayList<>();
        for (Relationship rel : relationships.values()) {
            if (rel.targetKey.equals(toKey) && rel.physicalType.equals(physicalType)) {
                r.add(rel);
            }
        }
        return r;
    }

    /** {@code Extends*} closure over direct subclass → superclass edges. */
    public Set<String> subclassClosure(String classNodeKey) {
        Set<String> out = new LinkedHashSet<>();
        Deque<String> stack = new ArrayDeque<>();
        stack.push(classNodeKey);
        while (!stack.isEmpty()) {
            String cur = stack.pop();
            if (out.add(cur)) {
                for (Relationship r : incoming(cur, EXTENDS)) {
                    stack.push(r.sourceKey);
                }
            }
        }
        return out;
    }
}
