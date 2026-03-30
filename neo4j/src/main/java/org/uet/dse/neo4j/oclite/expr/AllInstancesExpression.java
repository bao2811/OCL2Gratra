package org.uet.dse.neo4j.oclite.expr;


public class AllInstancesExpression implements ExpressionNode {
    private String className;

    public AllInstancesExpression(String className) {
        this.className = className;
    }

    @Override
    public Object evaluate(ExecutionContext ctx) {
        return ctx.resolveAllInstances(className);
    }
}