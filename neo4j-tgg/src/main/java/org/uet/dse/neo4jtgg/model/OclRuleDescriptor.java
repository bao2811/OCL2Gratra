package org.uet.dse.neo4jtgg.model;

import org.uet.dse.neo4j.oclite.ast.ASTContext;
import org.uet.dse.neo4j.oclite.ast.ASTAttributeConstraint;
import org.uet.dse.neo4j.oclite.ast.ASTExpression;
import org.uet.dse.neo4j.oclite.ast.ASTNode;
import org.uet.dse.neo4j.oclite.ast.ASTOperationConstraint;

import java.util.ArrayList;
import java.util.List;

public record OclRuleDescriptor(
        OclRuleOwnerKind ownerKind,
        OclRuleKind ruleKind,
        String className,
        String operationName,
        String attributeName,
        List<String> parameterNames,
        String ruleName,
        ASTNode ast,
        ASTExpression expression) {

    public OclRuleDescriptor {
        parameterNames = List.copyOf(new ArrayList<>(parameterNames != null ? parameterNames : List.of()));
    }

    public static OclRuleDescriptor forInvariant(ASTContext context) {
        return new OclRuleDescriptor(
                OclRuleOwnerKind.CLASS,
                OclRuleKind.INV,
                context.className,
                null,
                null,
                List.of(),
                context.invName,
                context,
                context.expression);
    }

    public static OclRuleDescriptor forFreeExpression(String expressionName, ASTExpression expression) {
        return new OclRuleDescriptor(
                OclRuleOwnerKind.CLASS,
                OclRuleKind.INV,
                null,
                null,
                null,
                List.of(),
                expressionName,
                expression,
                expression);
    }

    public static OclRuleDescriptor forOperationConstraint(ASTOperationConstraint constraint) {
        return new OclRuleDescriptor(
                OclRuleOwnerKind.OPERATION,
                mapRuleKind(constraint.constraintKind),
                constraint.className,
                constraint.operationName,
                null,
                constraint.parameterNames,
                constraint.ruleName,
                constraint,
                constraint.expression);
    }

    public static OclRuleDescriptor forAttributeConstraint(ASTAttributeConstraint constraint) {
        return new OclRuleDescriptor(
                OclRuleOwnerKind.ATTRIBUTE,
                mapRuleKind(constraint.constraintKind),
                constraint.className,
                null,
                constraint.attributeName,
                List.of(),
                constraint.ruleName,
                constraint,
                constraint.expression);
    }

    public String qualifiedName() {
        if (className == null) {
            return ruleName;
        }
        if (operationName != null && !operationName.isBlank()) {
            return className + "::" + operationName + "::" + ruleName;
        }
        if (attributeName != null && !attributeName.isBlank()) {
            return className + "::" + attributeName + "::" + ruleName;
        }
        return className + "::" + ruleName;
    }

    private static OclRuleKind mapRuleKind(String constraintKind) {
        if (constraintKind == null) {
            return OclRuleKind.INV;
        }
        return switch (constraintKind.toLowerCase()) {
            case "pre" -> OclRuleKind.PRE;
            case "post" -> OclRuleKind.POST;
            case "body" -> OclRuleKind.BODY;
            case "init" -> OclRuleKind.INIT;
            case "derive" -> OclRuleKind.DERIVE;
            default -> OclRuleKind.INV;
        };
    }
}
