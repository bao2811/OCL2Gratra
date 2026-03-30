package org.uet.dse.neo4j.oclite.ast;

public class ASTNot extends ASTExpression {
    public ASTExpression expression;

    public ASTNot(ASTExpression expression) {
        this.expression = expression;
    }
}