package org.uet.dse.neo4j.oclite.expr;

import java.util.Collection;

public class IsDefinedExpression implements ExpressionNode {
    private final ExpressionNode source;
    private final boolean checkDefined; // true: isDefined, false: isUndefined

    public IsDefinedExpression(ExpressionNode source, boolean checkDefined) {
        this.source = source;
        this.checkDefined = checkDefined;
    }

    @Override
    public Object evaluate(ExecutionContext ctx) {
        Object value = source.evaluate(ctx);
        
        boolean isDefined = (value != null);
        if (value instanceof Collection && ((Collection<?>) value).isEmpty()) {
            isDefined = false; 
        }

        return checkDefined ? isDefined : !isDefined;
    }
}