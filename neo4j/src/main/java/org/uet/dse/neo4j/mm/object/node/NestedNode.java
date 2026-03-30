package org.uet.dse.neo4j.mm.object.node;

import org.uet.dse.neo4j.mm.object.common.ObjectReference;

import java.util.List;

public final class NestedNode {

    private final String id;
    private final String collectionName;
    private final Object primitiveValue;
    private final List<ObjectReference> references;
    private final List<NestedNode> children;

    public NestedNode(
            String id,
            String collectionName,
            Object primitiveValue,
            List<ObjectReference> references,
            List<NestedNode> children
    ) {
        this.id = id;
        this.collectionName = collectionName;
        this.primitiveValue = primitiveValue;
        this.references = references;
        this.children = children;
    }

    public String getId() {
        return id;
    }

    public String getCollectionName() {
        return collectionName;
    }

    public Object getPrimitiveValue() {
        return primitiveValue;
    }

    public List<ObjectReference> getReferences() {
        return references;
    }

    public List<NestedNode> getChildren() {
        return children;
    }
}
