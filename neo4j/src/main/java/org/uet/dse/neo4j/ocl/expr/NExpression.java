package org.uet.dse.neo4j.ocl.expr;

import org.tzi.use.uml.ocl.expr.EvalContext;
import org.tzi.use.uml.ocl.expr.Expression;
import org.tzi.use.uml.ocl.expr.ExpressionVisitor;
import org.tzi.use.uml.ocl.type.Type;
import org.tzi.use.uml.ocl.value.Value;
import org.tzi.use.util.BufferedToString;
import org.uet.dse.neo4j.ocl.NEvalContext;

public abstract class NExpression {
  private Type fType;

  public Type type() {
    return this.fType;
  }
  StringBuilder toString(StringBuilder sb) {
    return null;
  }

  /**
   * Evaluates the expression and returns a result value.
   */
  public abstract Value nEval(NEvalContext ctx);
  public void processWithVisitor(ExpressionVisitor visitor) {

  }
}
