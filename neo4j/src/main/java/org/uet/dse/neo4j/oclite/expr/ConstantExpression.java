package org.uet.dse.neo4j.oclite.expr;


//(10, "ABC", true)
public class ConstantExpression implements ExpressionNode {
    private Object value;
    public ConstantExpression(Object v) { this.value = v; }

    @Override
    public Object evaluate(ExecutionContext ctx) {
        return value;
    }
}