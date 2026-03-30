package org.uet.dse.neo4j.oclite.ast;

public class ASTBooleanLiteral extends ASTLiteral {
    public final boolean value;
    public ASTBooleanLiteral(boolean v) { this.value = v; }
}