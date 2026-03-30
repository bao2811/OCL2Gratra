package org.uet.dse.neo4j.mm.core.node;

import org.uet.dse.neo4j.aop.MetaNode;

@MetaNode(type = MetaNodeData.NODE_CONCRETE_CLASS)
public class NodeConcreteClass extends AbstractClassNode {
    @Override
    public String getMetaName() {
        return MetaNodeData.NODE_CONCRETE_CLASS.getName();
    }

    @Override
    public String getMetaLabel() {
        return MetaNodeData.NODE_CONCRETE_CLASS.getLabel();
    }

}
