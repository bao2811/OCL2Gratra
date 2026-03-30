package org.uet.dse.neo4j.oclite.expr;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class CollectExpression implements ExpressionNode {
    private final ExpressionNode source;
    private final String iteratorName;
    private final ExpressionNode body;

    public CollectExpression(ExpressionNode source, String var, ExpressionNode body) {
        this.source = source;
        this.iteratorName = var;
        this.body = body;
    }

    @Override
    public Object evaluate(ExecutionContext ctx) {
        Object sourceVal = source.evaluate(ctx);
        Collection<?> collection = convertToCollection(sourceVal);
        
        List<Object> resultList = new ArrayList<>();
        for (Object item : collection) {
            ctx.pushScope(iteratorName, item);
            Object transformedValue = body.evaluate(ctx);
            ctx.popScope();

            if (transformedValue != null) {
                resultList.add(transformedValue);
            }
        }
        
        return resultList;
    }

    private Collection<?> convertToCollection(Object obj) {
        if (obj instanceof Collection) return (Collection<?>) obj;
        if (obj == null) return Collections.emptyList();
        return Collections.singletonList(obj);
    }
}