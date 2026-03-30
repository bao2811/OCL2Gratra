package org.uet.dse.neo4j.mm.core.node;

import org.uet.dse.neo4j.aop.MetaNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@MetaNode(type = MetaNodeData.NODE_ENUMERATION)
public class NodeEnumeration extends AbstractMetaNode {
    private List<String> values;

    public List<String> getValues() {
        return values;
    }

    public void setValues(List<String> values) {
        this.values = values;
    }

    @Override
    public String getMetaName() {
        return MetaNodeData.NODE_ENUMERATION.getName();
    }

    @Override
    public String getMetaLabel() {
        return MetaNodeData.NODE_ENUMERATION.getLabel();
    }


    @Override
    public Map<String, Object> toPropertyMap() {
        Map<String, Object> props = new HashMap<>();
        props.put("name", getName());
        props.put("values", this.values);
        return props;
    }
}
