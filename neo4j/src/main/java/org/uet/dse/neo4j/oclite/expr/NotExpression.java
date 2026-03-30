package org.uet.dse.neo4j.oclite.expr;

public class NotExpression implements ExpressionNode {
    private final ExpressionNode innerExpression;

    public NotExpression(ExpressionNode inner) {
        this.innerExpression = inner;
    }

    public Object evaluate(ExecutionContext ctx) {
        Object val = innerExpression.evaluate(ctx);
        if (val instanceof Boolean) {
            return !((Boolean) val);
        }
        return false;
    }
}