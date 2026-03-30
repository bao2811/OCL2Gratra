package org.uet.dse.neo4j.oclite.ast;

public class ASTIterator extends ASTExpression {
    public ASTExpression source;
    public String iteratorName;
    public String operation;
    public ASTExpression body;

    public ASTIterator(ASTExpression source, String op, String var, ASTExpression body) {
        this.source = source;
        this.operation = op;
        this.iteratorName = var;
        this.body = body;
    }
}