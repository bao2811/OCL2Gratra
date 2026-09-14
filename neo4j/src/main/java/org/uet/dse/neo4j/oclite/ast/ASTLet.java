package org.uet.dse.neo4j.oclite.ast;

public class ASTLet extends ASTExpression {
    public String variableName;
    public String variableTypeName;
    public ASTExpression value;
    public ASTExpression body;

    public ASTLet(String variableName, ASTExpression value, ASTExpression body) {
        this(variableName, null, value, body);
    }

    public ASTLet(String variableName, String variableTypeName, ASTExpression value, ASTExpression body) {
        this.variableName = variableName;
        this.variableTypeName = variableTypeName;
        this.value = value;
        this.body = body;
    }
}
