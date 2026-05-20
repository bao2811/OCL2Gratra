package org.uet.dse.neo4j.oclite.ast;

public class ASTIf extends ASTExpression {
    public ASTExpression condition;
    public ASTExpression thenBranch;
    public ASTExpression elseBranch;

    public ASTIf(ASTExpression condition, ASTExpression thenBranch, ASTExpression elseBranch) {
        this.condition = condition;
        this.thenBranch = thenBranch;
        this.elseBranch = elseBranch;
    }
}
