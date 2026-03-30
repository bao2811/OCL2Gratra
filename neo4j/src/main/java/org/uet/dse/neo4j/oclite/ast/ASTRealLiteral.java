package org.uet.dse.neo4j.oclite.ast;

public class ASTRealLiteral extends ASTLiteral {
    public final double value;
    public ASTRealLiteral(double v) { this.value = v; }
}