package org.uet.dse.neo4j.mm.core.node;

import org.uet.dse.neo4j.aop.MetaNode;
import org.uet.dse.neo4j.mm.core.common.NCollectionType;

import java.util.HashMap;
import java.util.Map;

@MetaNode(type = MetaNodeData.NODE_ATTRIBUTE)
public class NodeAttribute extends AbstractMetaNode {
    private String attrName;
    private int index;
    private boolean isNestedCollection;
    private boolean isCollection;
    private String type;
    private NCollectionType collectionType;
    private boolean isDerived;

    @Override
    public String getMetaName() {
        return MetaNodeData.NODE_ATTRIBUTE.getName();
    }

    @Override
    public String getMetaLabel() {
        return MetaNodeData.NODE_ATTRIBUTE.getLabel();
    }

    @Override
    public Map<String, Object> toPropertyMap() {
        Map<String, Object> props = new HashMap<>();
        // Trong Neo4j, 'name' thường dùng làm ID định danh cho MERGE
        props.put("name", this.getName());
        props.put("attrName", this.attrName);
        props.put("index", this.index);
        props.put("isNestedCollection", this.isNestedCollection);
        props.put("isCollection", this.isCollection);
        props.put("type", this.type);
        props.put("collectionType", collectionType != null ? collectionType.name() : NCollectionType.None.name());
        props.put("isDerived", this.isDerived);
        return props;
    }

    public String getAttrName() {
        return attrName;
    }

    public void setAttrName(String attrName) {
        this.attrName = attrName;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public boolean isNestedCollection() {
        return isNestedCollection;
    }

    public void setNestedCollection(boolean nestedCollection) {
        isNestedCollection = nestedCollection;
    }

    public boolean isCollection() {
        return isCollection;
    }

    public void setCollection(boolean collection) {
        isCollection = collection;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public NCollectionType getCollectionType() {
        return collectionType;
    }

    public void setCollectionType(NCollectionType collectionType) {
        this.collectionType = collectionType;
    }

    public boolean isDerived() {
        return isDerived;
    }

    public void setDerived(boolean derived) {
        isDerived = derived;
    }
}
