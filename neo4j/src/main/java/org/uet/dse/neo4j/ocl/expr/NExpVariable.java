package org.uet.dse.neo4j.ocl.expr;

import org.tzi.use.uml.ocl.expr.*;
import org.tzi.use.uml.ocl.type.Type;
import org.tzi.use.uml.ocl.value.IntegerValue;
import org.tzi.use.uml.ocl.value.Value;
import org.uet.dse.neo4j.ocl.NEvalContext;

public class NExpVariable extends NExpression {
  private final ExpVariable wrapped;

  public String fVarname;

  public NExpVariable(ExpVariable wrapped) {
    this.wrapped = wrapped;
    this.fVarname = wrapped.getVarname();
  }

  /**
   * Evaluates expression and returns result value.
   */
  public Value nEval(NEvalContext ctx) {
    ctx.enter(this);
    Value res = ctx.getVarValue(fVarname);
    if (res == null )
      throw new RuntimeException("unbound variable `" +
          fVarname + "'.");
    ctx.exit(this, res);
    return res;
  }

  public String name(){
    return "var";
  }

  public String getVarname() {
    return fVarname;
  }

  @Override
  public void processWithVisitor(ExpressionVisitor visitor) {

  }

}
