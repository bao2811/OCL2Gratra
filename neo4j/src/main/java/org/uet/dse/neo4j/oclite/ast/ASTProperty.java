package org.uet.dse.neo4j.oclite.ast;

public class ASTProperty extends ASTExpression {
    public ASTExpression source;
    public String name;
    public ASTProperty(ASTExpression s, String n) { this.source = s; this.name = n; }
}