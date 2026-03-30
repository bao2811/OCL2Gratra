package org.uet.dse.neo4j.oclite.expr;

import java.util.Collection;
import java.util.Collections;

public class ExistsExpression implements ExpressionNode {
    private final ExpressionNode source;
    private final String iteratorName;
    private final ExpressionNode body;

    public ExistsExpression(ExpressionNode source, String var, ExpressionNode body) {
        this.source = source;
        this.iteratorName = var;
        this.body = body;
    }

    @Override
    public Object evaluate(ExecutionContext ctx) {
        Object sourceVal = source.evaluate(ctx);
        Collection<?> collection = convertToCollection(sourceVal);

        if (collection.isEmpty()) return false;

        for (Object item : collection) {
            ctx.pushScope(iteratorName, item);
            
            Object conditionResult = body.evaluate(ctx);
            
            ctx.popScope();
            if (conditionResult instanceof Boolean && (Boolean) conditionResult) {
                return true;
            }
        }

        return false;
    }

    private Collection<?> convertToCollection(Object obj) {
        if (obj instanceof Collection) return (Collection<?>) obj;
        if (obj == null) return Collections.emptyList();
        return Collections.singletonList(obj);
    }
}