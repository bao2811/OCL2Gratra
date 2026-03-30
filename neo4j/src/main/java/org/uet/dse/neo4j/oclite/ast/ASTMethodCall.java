package org.uet.dse.neo4j.oclite.ast;

import java.util.List;

public class ASTMethodCall extends ASTExpression {
    public ASTExpression source;
    public String methodName;
    public List<ASTExpression> args;

    public ASTMethodCall(ASTExpression source, String methodName, List<ASTExpression> args) {
        this.source = source;
        this.methodName = methodName;
        this.args = args;
    }
}