package org.uet.dse.neo4j.oclite.expr;


public class TypeExpression implements ExpressionNode {
    private String typeName;

    public TypeExpression(String typeName) {
        this.typeName = typeName;
    }

    public String getTypeName() {
        return typeName;
    }

    @Override
    public Object evaluate(ExecutionContext ctx) {
        return typeName;
    }
}