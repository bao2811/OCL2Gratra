package org.uet.dse.neo4j.mm.core.node;

import org.uet.dse.neo4j.aop.MetaNode;

import java.util.HashMap;
import java.util.Map;

@MetaNode(type = MetaNodeData.NODE_TERNARY_ASSOCIATION)
public class NodeTernaryAssociation extends AbstractMetaNode {

    private boolean isDerived;

    public boolean isDerived() {
        return isDerived;
    }

    public void setDerived(boolean derived) {
        isDerived = derived;
    }

    @Override
    public String getMetaName() {
        return MetaNodeData.NODE_TERNARY_ASSOCIATION.getName();
    }

    @Override
    public String getMetaLabel() {
        return MetaNodeData.NODE_TERNARY_ASSOCIATION.getLabel();
    }

    @Override
    public Map<String, Object> toPropertyMap() {
        Map<String, Object> props = new HashMap<>();
        props.put("name", this.getName());
        props.put("isDerived", this.isDerived);

        return props;
    }
}
