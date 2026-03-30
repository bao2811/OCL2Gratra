package org.uet.dse.neo4j.aop;

import org.uet.dse.neo4j.mm.core.node.MetaNodeData;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface MetaNode {
    MetaNodeData type();
}

