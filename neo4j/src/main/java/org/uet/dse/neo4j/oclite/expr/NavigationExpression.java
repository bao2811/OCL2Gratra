package org.uet.dse.neo4j.oclite.expr;

public class NavigationExpression implements ExpressionNode {
    private ExpressionNode source;
    private String relationshipType;
    private String name;

    public NavigationExpression(ExpressionNode sourceNode, String name) {
        this.source = sourceNode;
        this.name = name;
    }

    @Override
    public Object evaluate(ExecutionContext ctx) {
        Object startNode = source.evaluate(ctx);
        return ctx.resolveNavigation(startNode, name);
    }
}