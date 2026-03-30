package org.uet.dse.neo4j.query.model;

import org.uet.dse.neo4j.mm.core.node.*;

import java.util.List;

public final class MetaNodeRegistry {

    private MetaNodeRegistry() {}

    public static final List<Class<? extends AbstractMetaNode>> ALL = List.of(
            NodeAbstractClass.class,
            NodeAttribute.class,
            NodeOperation.class,
            NodeParam.class,
            NodeAssociationClass.class,
            NodeConcreteClass.class,
            NodeEnumeration.class,
            NodeClassInvariant.class,
            NodePreCondition.class,
            NodePostCondition.class,
            NodeTernaryAssociation.class,
            NodeNestedCollection.class
    );
}
