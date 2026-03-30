package org.uet.dse.neo4j.mm.core.node;

import java.util.HashMap;
import java.util.Map;

public abstract class AbstractClassNode extends AbstractMetaNode {
    protected boolean hasParent;
    protected boolean hasChildren;

    public void setHasParent(boolean hasParent) { this.hasParent = hasParent; }
    public void setHasChildren(boolean hasChildren) { this.hasChildren = hasChildren; }

    @Override
    public Map<String, Object> toPropertyMap() {
        Map<String, Object> props = new HashMap<>();
        props.put("name", getName());
        props.put("hasParent", this.hasParent);
        props.put("hasChildren", this.hasChildren);
        return props;
    }

}