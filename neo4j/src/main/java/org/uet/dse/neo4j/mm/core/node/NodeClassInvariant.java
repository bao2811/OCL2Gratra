package org.uet.dse.neo4j.mm.core.node;

import org.uet.dse.neo4j.aop.MetaNode;

import java.util.HashMap;
import java.util.Map;

@MetaNode(type = MetaNodeData.NODE_CLASS_INVARIANT)
public class NodeClassInvariant extends AbstractMetaNode {
    private String expression;
    private String invName;
    private boolean isExistential;
    private int index;

    public String getExpression() {
        return expression;
    }

    public void setExpression(String expression) {
        this.expression = expression;
    }

    public String getInvName() {
        return invName;
    }

    public void setInvName(String invName) {
        this.invName = invName;
    }

    public boolean isExistential() {
        return isExistential;
    }

    public void setExistential(boolean existential) {
        isExistential = existential;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    @Override
    public String getMetaName() {
        return MetaNodeData.NODE_CLASS_INVARIANT.getName();
    }

    @Override
    public String getMetaLabel() {
        return MetaNodeData.NODE_CLASS_INVARIANT.getLabel();
    }

    @Override
    public Map<String, Object> toPropertyMap() {
        Map<String, Object> props = new HashMap<>();
        props.put("name", this.getName());
        props.put("invName", this.invName);
        props.put("expression", this.expression);
        props.put("isExistential", this.isExistential);
        props.put("index", this.index);
        return props;
    }
}
