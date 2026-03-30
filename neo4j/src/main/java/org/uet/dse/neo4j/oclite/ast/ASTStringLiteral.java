package org.uet.dse.neo4j.oclite.ast;

public class ASTStringLiteral extends ASTLiteral {
    public final String value;
    public ASTStringLiteral(String v) { this.value = v; }
}