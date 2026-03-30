package org.uet.dse.neo4j.oclite.expr;

import java.lang.reflect.Field;
import org.neo4j.driver.types.Node;

public class PropertyExpression implements ExpressionNode {
    private ExpressionNode sourceNode;
    private String propertyName;

    public PropertyExpression(ExpressionNode source, String propertyName) {
        this.sourceNode = source;
        this.propertyName = propertyName;
    }

    @Override
    public Object evaluate(ExecutionContext ctx) {
        Object source = sourceNode.evaluate(ctx); // return Node (:Member {use_id: "m1"})
        if (source == null) return null;
        return ctx.resolveProperty(source, propertyName); // return value 10.0
    }
}