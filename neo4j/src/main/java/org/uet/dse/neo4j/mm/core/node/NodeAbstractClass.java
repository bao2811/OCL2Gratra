package org.uet.dse.neo4j.mm.core.node;

import org.uet.dse.neo4j.aop.MetaNode;

@MetaNode(type = MetaNodeData.NODE_ABSTRACT_CLASS)
public class NodeAbstractClass extends AbstractClassNode {

    @Override
    public String getMetaName() {
        return MetaNodeData.NODE_ABSTRACT_CLASS.getName();
    }

    @Override
    public String getMetaLabel() {
        return MetaNodeData.NODE_ABSTRACT_CLASS.getLabel();
    }
}
