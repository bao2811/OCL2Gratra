package org.uet.dse.neo4j.oclite.ast;

public class ASTBinary extends ASTExpression {
    public ASTExpression left, right;
    public String op;
    public ASTBinary(ASTExpression l, String op, ASTExpression r) { this.left = l; this.op = op; this.right = r; }
}