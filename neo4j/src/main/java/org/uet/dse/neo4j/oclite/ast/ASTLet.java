package org.uet.dse.neo4j.oclite.ast;

public class ASTLet extends ASTExpression {
    public String variableName;
    public ASTExpression value;
    public ASTExpression body;

    public ASTLet(String variableName, ASTExpression value, ASTExpression body) {
        this.variableName = variableName;
        this.value = value;
        this.body = body;
    }
}
