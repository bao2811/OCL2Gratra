package org.uet.dse.neo4j.ocl.phase5;

import org.tzi.use.parser.Context;
import org.tzi.use.parser.SemanticException;
import org.tzi.use.parser.ocl.ASTExpression;
import org.tzi.use.uml.ocl.expr.Expression;

import java.util.Set;

public abstract class CASTExpression extends ASTExpression {
  @Override
  public Expression gen(Context ctx) throws SemanticException {
    return null;
  }

  @Override
  public void getFreeVariables(Set<String> freeVars) {

  }

  abstract public Expression gen();
}
