package org.uet.dse.neo4j.mm.core.node;

import org.uet.dse.neo4j.aop.MetaNode;

import java.util.HashMap;
import java.util.Map;

@MetaNode(type = MetaNodeData.NODE_POST_CONDITION)
public class NodePostCondition extends AbstractConditionNode {

    @Override
    public String getMetaName() {
        return MetaNodeData.NODE_POST_CONDITION.getName();
    }

    @Override
    public String getMetaLabel() {
        return MetaNodeData.NODE_POST_CONDITION.getLabel();
    }

    @Override
    public Map<String, Object> toPropertyMap() {
        Map<String, Object> props = new HashMap<>();
        props.put("name", this.getName());
        props.put("condName", this.condName);
        props.put("expression", this.expression);
        props.put("index", this.index);

        return props;
    }
}
