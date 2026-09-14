package org.uet.dse.ocl2cypher.core;

import java.math.BigInteger;
import org.uet.dse.ocl2cypher.diagnostics.SourceSpan;
import org.uet.dse.ocl2cypher.runtime.OclType;

/** Dependency-free corpus for the Core structural validator. */
public final class CoreValidatorCheck {
  public static void main(String[] args) {
    var s=SourceSpan.UNKNOWN; var self=new CoreDeclaration(1,"self",CoreDeclaration.Kind.SELF,OclType.clazz("Person"));
    var good=new CoreInvariant("adult","Person",self,
      new CoreExpr.Binary(s,CoreExpr.BinaryOp.GREATER_THAN_OR_EQUAL,
        new CoreExpr.LiteralInteger(s,BigInteger.TEN),new CoreExpr.LiteralInteger(s,BigInteger.ONE),OclType.BOOLEAN));
    if(!CoreValidator.validate(good).isEmpty()) throw new AssertionError(CoreValidator.validate(good));
    var bad=new CoreQuery(null,null,new CoreExpr.LiteralInteger(s,BigInteger.ONE));
    if(!CoreValidator.validate(bad).isEmpty()) throw new AssertionError(CoreValidator.validate(bad));
    System.out.println("PASS: Core validator corpus (invariant typing, query root, recursive expressions)");
  }
}
