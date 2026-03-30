package org.uet.dse.neo4j.ocl.expr;

import org.tzi.use.uml.ocl.expr.*;
import org.tzi.use.uml.ocl.value.IntegerValue;
import org.tzi.use.uml.ocl.value.Value;
import org.uet.dse.neo4j.ocl.NEvalContext;

public class NExprConstInteger extends NExpression {
  private final ExpConstInteger wrapped;

  //business
  public int fValue;

  public NExprConstInteger(ExpConstInteger wrapped, int n) {
    this.wrapped = wrapped;
    fValue = n;
  }

  public int value() {
    return fValue;
  }


  public NExprConstInteger(ExpConstInteger wrapped) throws ExpInvalidException {
    this.wrapped = wrapped;
  }


  @Override
  public StringBuilder toString(StringBuilder sb) {
    return null;
  }

  @Override
  public Value nEval(NEvalContext ctx) {
    ctx.enter(this);
    Value res = IntegerValue.valueOf(fValue);
    ctx.exit(this, res);
    return res;
  }

  @Override
  public void processWithVisitor(ExpressionVisitor visitor) {

  }
}
