package org.uet.dse.neo4j.oclite.expr;


public interface ExpressionNode {
    Object evaluate(ExecutionContext ctx);
}


