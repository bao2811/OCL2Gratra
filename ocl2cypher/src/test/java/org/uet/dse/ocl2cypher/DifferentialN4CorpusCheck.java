package org.uet.dse.ocl2cypher;

import java.math.*; import java.util.*;
import org.uet.dse.ocl2cypher.core.*; import org.uet.dse.ocl2cypher.qcyp.*;
import org.uet.dse.ocl2cypher.diagnostics.SourceSpan; import org.uet.dse.ocl2cypher.graph.GraphModel;
import org.uet.dse.ocl2cypher.runtime.*; import org.uet.dse.ocl2cypher.source.model.*;

/** Small differential corpus for N-4: same Core/Q denotation on finite carriers. */
public final class DifferentialN4CorpusCheck {
  static final SourceSpan S=SourceSpan.UNKNOWN; static int n;
  static void eq(OclValue a,OclValue b){if(!a.equals(b))throw new AssertionError(a+" != "+b);n++;}
  static QNode.QExpr q(QNode.QExpr x){return x;}
  public static void main(String[] z){
    var sm=SchemaModel.builder("d").build(); var sn=Snapshot.builder().build(); var g=new GraphModel("d"); var ce=new CoreInterpreter.Env();
    var one=new CoreExpr.LiteralInteger(S,BigInteger.ONE); var two=new CoreExpr.LiteralInteger(S,BigInteger.TWO);
    var qb=new QNode.QExpr.Binary(S,CoreExpr.BinaryOp.NUMERIC_ADD,new QNode.QExpr.Constant(S,OclType.INTEGER,BigInteger.ONE),new QNode.QExpr.Constant(S,OclType.INTEGER,BigInteger.TWO),OclType.INTEGER);
    eq(CoreInterpreter.eval(sm,sn,ce,new CoreExpr.Binary(S,CoreExpr.BinaryOp.NUMERIC_ADD,one,two,OclType.INTEGER)),QInterpreter.evalExpr(sm,g,ce,qb));
    for(boolean v: new boolean[]{true,false}){
      var c=new CoreExpr.LiteralBoolean(S,v); var qx=new QNode.QExpr.Constant(S,OclType.BOOLEAN,v);
      eq(CoreInterpreter.eval(sm,sn,ce,new CoreExpr.Unary(S,CoreExpr.UnaryOp.BOOLEAN_NOT,c,OclType.BOOLEAN)),QInterpreter.evalExpr(sm,g,ce,new QNode.QExpr.Unary(S,CoreExpr.UnaryOp.BOOLEAN_NOT,qx,OclType.BOOLEAN)));
    }
    System.out.println("PASS: N-4 differential corpus "+n+" Core/Q cases");
  }
}
