package org.uet.dse.neo4j.oclite.expr;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class SelectExpression implements ExpressionNode {
    private ExpressionNode source;
    private String iteratorName;
    private ExpressionNode body;

    public SelectExpression(ExpressionNode source, String var, ExpressionNode body) {
        this.source = source;
        this.iteratorName = var;
        this.body = body;
    }

    @Override
    public Object evaluate(ExecutionContext ctx) {
        //p1.friends
        Object sourceVal = source.evaluate(ctx);
        Collection<?> collection = convertToCollection(sourceVal);
        
        List<Object> result = new ArrayList<>();

        for (Object item : collection) {
            ctx.pushScope(iteratorName, item);
            Object isMatch = body.evaluate(ctx);
            
            if (isMatch instanceof Boolean && (Boolean) isMatch) {
                result.add(item);
            }

            ctx.popScope();
        }
        
        return result;
    }

    private Collection<?> convertToCollection(Object obj) {
        if (obj instanceof Collection) return (Collection<?>) obj;
        if (obj == null) return Collections.emptyList();
        return Collections.singletonList(obj);
    }
}