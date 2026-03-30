package org.uet.dse.neo4j.oclite.ast;

// (p1, self)
public class ASTVar extends ASTExpression {
    public String name;
    public ASTVar(String n) { this.name = n; }
}