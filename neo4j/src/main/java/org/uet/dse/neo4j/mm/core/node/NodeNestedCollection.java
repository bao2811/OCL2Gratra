package org.uet.dse.neo4j.mm.core.node;

import org.uet.dse.neo4j.aop.MetaNode;

import java.util.HashMap;
import java.util.Map;

@MetaNode(type = MetaNodeData.NODE_NESTED_COLLECTION)
public class NodeNestedCollection extends AbstractMetaNode {
    private boolean isNestedCollection;
    private String collectionName;
    public boolean isNestedCollection() {
        return isNestedCollection;
    }

    public void setNestedCollection(boolean nestedCollection) {
        isNestedCollection = nestedCollection;
    }

    public String getCollectionName() {
        return collectionName;
    }

    public void setCollectionName(String collectionName) {
        this.collectionName = collectionName;
    }

    @Override
    public String getMetaName() {
        return MetaNodeData.NODE_NESTED_COLLECTION.getName();
    }

    @Override
    public String getMetaLabel() {
        return MetaNodeData.NODE_NESTED_COLLECTION.getLabel();
    }

    @Override
    public Map<String, Object> toPropertyMap() {
        Map<String, Object> props = new HashMap<>();
        props.put("name", this.getName());
        props.put("collectionName", this.collectionName);
        props.put("isNestedCollection", this.isNestedCollection);
        return props;
    }
}

