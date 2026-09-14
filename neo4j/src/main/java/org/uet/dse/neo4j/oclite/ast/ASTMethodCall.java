package org.uet.dse.neo4j.oclite.ast;

import java.util.List;

public class ASTMethodCall extends ASTExpression {
    public ASTExpression source;
    public String methodName;
    public List<ASTExpression> args;
    public boolean atPre;

    public ASTMethodCall(ASTExpression source, String methodName, List<ASTExpression> args) {
        this(source, methodName, args, false);
    }

    public ASTMethodCall(ASTExpression source, String methodName, List<ASTExpression> args, boolean atPre) {
        this.source = source;
        this.methodName = methodName;
        this.args = List.copyOf(args);
        this.atPre = atPre;
    }
}
