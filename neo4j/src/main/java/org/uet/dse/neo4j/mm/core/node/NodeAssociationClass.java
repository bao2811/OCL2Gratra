package org.uet.dse.neo4j.mm.core.node;

import org.uet.dse.neo4j.aop.MetaNode;

@MetaNode(type = MetaNodeData.NODE_ASSOCIATION_CLASS)
public class NodeAssociationClass extends AbstractClassNode {
    @Override
    public String getMetaName() {
        return MetaNodeData.NODE_ASSOCIATION_CLASS.getName();
    }

    @Override
    public String getMetaLabel() {
        return MetaNodeData.NODE_ASSOCIATION_CLASS.getLabel();
    }

}
