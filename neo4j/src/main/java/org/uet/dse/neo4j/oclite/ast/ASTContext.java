package org.uet.dse.neo4j.oclite.ast;

public class ASTContext extends ASTNode {
    public String className, invName;
    public ASTExpression expression;
    public ASTContext(String c, String i, ASTExpression e) { this.className = c; this.invName = i; this.expression = e; }
}