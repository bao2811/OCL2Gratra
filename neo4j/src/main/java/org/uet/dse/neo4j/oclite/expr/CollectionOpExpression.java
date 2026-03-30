package org.uet.dse.neo4j.oclite.expr;

import java.util.List;
import java.util.stream.Collectors;

public class CollectionOpExpression implements ExpressionNode {
    private ExpressionNode source;
    private String opName;
    private List<ExpressionNode> args;

    public CollectionOpExpression(ExpressionNode source, String opName, List<ExpressionNode> args) {
        this.source = source;
        this.opName = opName;
        this.args = args;
    }

    @Override
    public Object evaluate(ExecutionContext ctx) {
        Object sourceVal = source.evaluate(ctx);

        List<Object> evaluatedArgs = args.stream()
            .map(a -> a.evaluate(ctx))
            .collect(Collectors.toList());

        return ctx.resolveCollectionOperation(sourceVal, opName, evaluatedArgs);
    }
}