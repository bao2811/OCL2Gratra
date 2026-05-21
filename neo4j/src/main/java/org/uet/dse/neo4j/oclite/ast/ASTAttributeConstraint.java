package org.uet.dse.neo4j.oclite.ast;

public class ASTAttributeConstraint extends ASTNode {
    public String className;
    public String attributeName;
    public String constraintKind;
    public String ruleName;
    public ASTExpression expression;

    public ASTAttributeConstraint(String className,
                                  String attributeName,
                                  String constraintKind,
                                  String ruleName,
                                  ASTExpression expression) {
        this.className = className;
        this.attributeName = attributeName;
        this.constraintKind = constraintKind;
        this.ruleName = ruleName;
        this.expression = expression;
    }
}
