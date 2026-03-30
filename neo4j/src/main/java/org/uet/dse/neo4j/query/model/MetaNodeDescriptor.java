package org.uet.dse.neo4j.query.model;

import org.uet.dse.neo4j.aop.MetaNode;
import org.uet.dse.neo4j.mm.core.node.MetaNodeData;


public record MetaNodeDescriptor(String name, String label) {

    public static MetaNodeDescriptor from(Class<?> clazz) {
        MetaNode ann = clazz.getAnnotation(MetaNode.class);
        if (ann == null) {
            throw new IllegalArgumentException(
                    clazz.getSimpleName() + " is not annotated with @MetaNode"
            );
        }

        MetaNodeData data = ann.type();
        return new MetaNodeDescriptor(data.getName(), data.getLabel());
    }
}
