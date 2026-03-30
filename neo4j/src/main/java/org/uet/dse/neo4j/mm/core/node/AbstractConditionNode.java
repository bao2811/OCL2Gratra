package org.uet.dse.neo4j.mm.core.node;

import java.util.HashMap;
import java.util.Map;

public abstract class AbstractConditionNode extends AbstractMetaNode {
    protected String condName;
    protected String expression;
    protected int index;

    public void setCondName(String condName) { this.condName = condName; }
    public void setExpression(String expression) { this.expression = expression; }
    public void setIndex(int index) { this.index = index; }

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