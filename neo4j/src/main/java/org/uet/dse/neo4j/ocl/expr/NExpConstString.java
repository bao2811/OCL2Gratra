package org.uet.dse.neo4j.ocl.expr;

import org.tzi.use.uml.ocl.expr.ExpConstInteger;
import org.tzi.use.uml.ocl.expr.ExpConstString;
import org.tzi.use.uml.ocl.expr.ExpInvalidException;
import org.tzi.use.uml.ocl.expr.ExpressionVisitor;
import org.tzi.use.uml.ocl.type.TypeFactory;
import org.tzi.use.uml.ocl.value.IntegerValue;
import org.tzi.use.uml.ocl.value.StringValue;
import org.tzi.use.uml.ocl.value.Value;
import org.uet.dse.neo4j.ocl.NEvalContext;

/**
 * Constant string expression.
 *
 * @author  Mark Richters
 */
public final class NExpConstString extends NExpression {
  private final ExpConstString wrapped;

  //business
  public String fValue;

  public NExpConstString(ExpConstString wrapped, String s) {
    super();
    this.wrapped = wrapped;
    this.fValue = s;
  }

  public NExpConstString(ExpConstString wrapped) {
    this.wrapped = wrapped;
  }

  public String value() {
    return fValue;
  }



  @Override
  public StringBuilder toString(StringBuilder sb) {
    return null;
  }

  @Override
  public Value nEval(NEvalContext ctx) {
    ctx.enter(this);
    Value res = new StringValue(fValue);
    ctx.exit(this, res);
    return res;
  }

  @Override
  public void processWithVisitor(ExpressionVisitor visitor) {

  }
}

