package org.uet.dse.neo4j.oclite.ast;

public class ASTIntegerLiteral extends ASTLiteral {
    public final long value;
    public ASTIntegerLiteral(long v) { this.value = v; }
}