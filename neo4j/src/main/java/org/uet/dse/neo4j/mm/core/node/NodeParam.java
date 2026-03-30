package org.uet.dse.neo4j.mm.core.node;

import org.uet.dse.neo4j.aop.MetaNode;
import org.uet.dse.neo4j.mm.core.common.NCollectionType;
import org.uet.dse.neo4j.mm.core.common.NType;

import java.util.HashMap;
import java.util.Map;

@MetaNode(type = MetaNodeData.NODE_PARAM)
public class NodeParam extends AbstractMetaNode {
    private NCollectionType collectionType;
    private boolean isCollection;
    private String pName;
    private int pOrder;
    private NType type;

    public NCollectionType getCollectionType() {
        return collectionType;
    }

    public void setCollectionType(NCollectionType collectionType) {
        this.collectionType = collectionType;
    }

    public boolean isCollection() {
        return isCollection;
    }

    public void setCollection(boolean collection) {
        isCollection = collection;
    }

    public String getpName() {
        return pName;
    }

    public void setpName(String pName) {
        this.pName = pName;
    }

    public int getpOrder() {
        return pOrder;
    }

    public void setpOrder(int pOrder) {
        this.pOrder = pOrder;
    }

    public NType getType() {
        return type;
    }

    public void setType(NType type) {
        this.type = type;
    }

    @Override
    public String getMetaName() {
        return MetaNodeData.NODE_PARAM.getName();
    }

    @Override
    public String getMetaLabel() {
        return MetaNodeData.NODE_PARAM.getLabel();
    }

    @Override
    public Map<String, Object> toPropertyMap() {
        Map<String, Object> props = new HashMap<>();
        props.put("name", this.getName());
        props.put("pName", this.pName);
        props.put("pOrder", this.pOrder);
        props.put("isCollection", this.isCollection);
        props.put("type", type != null ? type.name() : NType.Object.name());
        props.put("collectionType", collectionType != null ? collectionType.name() : NCollectionType.None.name());
        return props;
    }
}
