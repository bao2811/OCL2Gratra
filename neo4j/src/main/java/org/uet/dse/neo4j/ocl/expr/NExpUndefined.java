package org.uet.dse.neo4j.ocl.expr;

import org.tzi.use.uml.ocl.expr.ExpUndefined;
import org.tzi.use.uml.ocl.expr.ExpVariable;
import org.tzi.use.uml.ocl.expr.ExpressionVisitor;
import org.tzi.use.uml.ocl.type.Type;
import org.tzi.use.uml.ocl.value.UndefinedValue;
import org.tzi.use.uml.ocl.value.Value;
import org.uet.dse.neo4j.ocl.NEvalContext;

public final class NExpUndefined extends NExpression {
  private final ExpUndefined wrapped;

  public NExpUndefined(ExpUndefined wrapped) {
    this.wrapped = wrapped;
  }

  public Value nEval(NEvalContext ctx) {
    ctx.enter(this);
    Value res = UndefinedValue.instance;
    ctx.exit(this, res);
    return res;
  }

  @Override
  public StringBuilder toString(StringBuilder sb) {
    return sb.append("null");
  }

  @Override
  public void processWithVisitor(ExpressionVisitor visitor) {

  }

}
