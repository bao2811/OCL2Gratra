package org.uet.dse.neo4j.ocl.expr;

import org.tzi.use.uml.ocl.expr.*;
import org.tzi.use.uml.ocl.expr.operations.OpGeneric;
import org.tzi.use.uml.ocl.type.Type;
import org.tzi.use.uml.ocl.value.UndefinedValue;
import org.tzi.use.uml.ocl.value.Value;
import org.uet.dse.neo4j.ocl.NEvalContext;
import org.uet.dse.neo4j.ocl.mapper.NExpressionRewriter;
import org.uet.dse.neo4j.ocl.operation.NOpRegistry;
import org.uet.dse.neo4j.ocl.operation.NBooleanOperation;

public class NExpStdOp extends NExpression {
  private final ExpStdOp wrapped;

  //business
  public NOpGeneric fOp;
  public NExpression fArgs[];

  /**
   * Constructor chính - được gọi từ rewriter.
   * Nhận ExpStdOp gốc, tự resolve NOpGeneric và rewrite args.
   */
  public NExpStdOp(ExpStdOp wrapped) throws ExpInvalidException {
    this.wrapped = wrapped;
  }

  private NExpStdOp(NOpGeneric op, NExpression args[], Type t) {
    fOp = op;
    fArgs = args;
    wrapped = null;
  }

  private NExpStdOp(NOpGeneric op, NExpression args[], Type t, ExpStdOp wrapped) {
    this.wrapped = wrapped;
    fOp = op;
    fArgs = args;
  }



  @Override
  public StringBuilder toString(StringBuilder sb) {
    return null;
  }

  @Override
  public Value nEval(NEvalContext ctx) {
    ctx.enter(this);
    Value res = null;

    // Boolean operations need special treatment of undefined
    // arguments. Also, short-circuit evaluation may be used to
    // speed up the evaluation process.
    if (getOperation().isBooleanOperation()) {
      res = ((NBooleanOperation) getOperation()).evalWithArgs(ctx, fArgs);
    } else {
      final Value argValues[] = new Value[fArgs.length];
      final int opKind = getOperation().kind();

      Value v;

      for (int i = 0; i < fArgs.length && res == null; i++) {
        argValues[i] = v = fArgs[i].nEval(ctx);
        // if any of the arguments is undefined, the result
        // depends on the kind of operation we are about to
        // call.
        if (v.isUndefined()) {
          switch (opKind) {
            case OpGeneric.OPERATION:
              // strict evaluation, result is undefined, no
              // need to call the operation's eval() method.
              res = UndefinedValue.instance;
              break;
            case OpGeneric.SPECIAL:
              // these operations handle undefined arguments
              // themselves
              break;
            default:
              throw new RuntimeException(
                  "Unexpected operation kind: " + opKind);
          }
        }
      }
      if (res == null) {
        try {
          res = getOperation().nEval(ctx, argValues, type());
        } catch (ArithmeticException ex) {
          // catch e.g. division by zero
          res = UndefinedValue.instance;
        }
      }
    }
    ctx.exit(this, res);
    return res;
  }

  public NOpGeneric getOperation() {
    return fOp;
  }

  @Override
  public void processWithVisitor(ExpressionVisitor visitor) {

  }
}
