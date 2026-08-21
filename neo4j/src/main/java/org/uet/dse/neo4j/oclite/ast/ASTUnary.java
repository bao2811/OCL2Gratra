package org.uet.dse.neo4j.oclite.ast;

/** Unary plus/minus. Logical negation keeps its historical ASTNot node. */
public final class ASTUnary extends ASTExpression {
    public final String operator;
    public final ASTExpression expression;

    public ASTUnary(String operator, ASTExpression expression) {
        this.operator = operator;
        this.expression = expression;
    }
}
