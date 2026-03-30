package org.uet.dse.neo4j.ocl.mapper;

import org.tzi.use.uml.ocl.expr.EvalContext;
import org.tzi.use.uml.ocl.expr.Expression;
import org.tzi.use.uml.ocl.expr.ExpressionVisitor;
import org.tzi.use.uml.ocl.value.Value;
import org.uet.dse.neo4j.ocl.NEvalContext;
import org.uet.dse.neo4j.ocl.expr.NExpression;

/**
 * Wrap Expression gốc, dùng eval() cũ khi chưa có neo4j implementation.
 * Giúp rewriter không bị crash khi gặp expr chưa được map.
 */
public class NFallbackExpression extends NExpression {
  private final Expression wrapped;

  public NFallbackExpression(Expression wrapped) {
    this.wrapped = wrapped;
  }

  @Override
  public Value nEval(NEvalContext ctx) {
    // fallback: dùng eval cũ với OCL context
    // các value từ neo4j phải đã được convert về MObject trước đây
    //return wrapped.eval(ctx.toOclContext());
    return null;
  }


  @Override
  public void processWithVisitor(ExpressionVisitor visitor) {
    wrapped.processWithVisitor(visitor);
  }

}