package org.uet.dse.neo4j.oclite.ast;

public class ASTAttributeConstraint extends ASTNode {
    public String className;
    public String attributeName;
    public String attributeTypeName;
    public String constraintKind;
    public String ruleName;
    public ASTExpression expression;

    public ASTAttributeConstraint(String className,
                                  String attributeName,
                                  String constraintKind,
                                  String ruleName,
                                  ASTExpression expression) {
        this(className, attributeName, null, constraintKind, ruleName, expression);
    }

    public ASTAttributeConstraint(String className,
                                  String attributeName,
                                  String attributeTypeName,
                                  String constraintKind,
                                  String ruleName,
                                  ASTExpression expression) {
        this.className = className;
        this.attributeName = attributeName;
        this.attributeTypeName = attributeTypeName;
        this.constraintKind = constraintKind;
        this.ruleName = ruleName;
        this.expression = expression;
    }
}
