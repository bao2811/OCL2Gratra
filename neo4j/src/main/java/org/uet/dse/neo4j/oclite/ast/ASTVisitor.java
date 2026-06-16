package org.uet.dse.neo4j.oclite.ast;

import org.uet.dse.neo4j.OCLBaseVisitor;
import org.uet.dse.neo4j.OCLBaseVisitor.*;
import org.uet.dse.neo4j.OCLParser;
import org.uet.dse.neo4j.OCLParser.*;

import java.util.ArrayList;
import java.util.List;

public class ASTVisitor extends OCLBaseVisitor<ASTNode> {

    @Override
    public ASTNode visitOclFile(OCLParser.OclFileContext ctx) {
        ASTFile file = new ASTFile();
        for (OCLParser.DeclarationContext declaration : ctx.declaration()) {
            file.addElement(visit(declaration));
        }
        for (OCLParser.ExpressionContext expression : ctx.expression()) {
            file.addElement(visit(expression));
        }
        return file;
    }


    @Override
    public ASTNode visitLogicalExp(OCLParser.LogicalExpContext ctx) {
        return new ASTBinary((ASTExpression)visit(ctx.left), ctx.op.getText(), (ASTExpression)visit(ctx.right));
    }

    @Override
    public ASTNode visitComparisonExp(OCLParser.ComparisonExpContext ctx) {
        return new ASTBinary((ASTExpression)visit(ctx.left), ctx.op.getText(), (ASTExpression)visit(ctx.right));
    }

    @Override
    public ASTNode visitAdditiveExp(OCLParser.AdditiveExpContext ctx) {
        return new ASTBinary((ASTExpression)visit(ctx.left), ctx.op.getText(), (ASTExpression)visit(ctx.right));
    }

    @Override
    public ASTNode visitMultiplicativeExp(OCLParser.MultiplicativeExpContext ctx) {
        return new ASTBinary((ASTExpression)visit(ctx.left), ctx.op.getText(), (ASTExpression)visit(ctx.right));
    }

    @Override
    public ASTNode visitIdExpr(OCLParser.IdExprContext ctx) {
        return new ASTVar(ctx.Identifier().getText());
    }

    @Override
    public ASTNode visitEnumLiteralExpr(OCLParser.EnumLiteralExprContext ctx) {
        return new ASTEnumLiteral(ctx.enumType.getText(), ctx.enumLiteral.getText());
    }

    @Override
    public ASTNode visitNavigationExpr(OCLParser.NavigationExprContext ctx) {
        List<ASTExpression> qualifiers = new ArrayList<>();
        if (ctx.argList() != null) {
            for (OCLParser.ExpressionContext arg : ctx.argList().expression()) {
                qualifiers.add((ASTExpression) visit(arg));
            }
        }
        return new ASTProperty((ASTExpression) visit(ctx.primary()), ctx.Identifier().getText(), qualifiers);
    }

    @Override
    public ASTNode visitCollectionOpExpr(OCLParser.CollectionOpExprContext ctx) {
        List<ASTExpression> args = new ArrayList<>();
        if (ctx.argList() != null) {
            for (OCLParser.ExpressionContext arg : ctx.argList().expression()) args.add((ASTExpression)visit(arg));
        }
        return new ASTCollectionOp((ASTExpression)visit(ctx.primary()), ctx.Identifier().getText(), args);
    }

    @Override
    public ASTNode visitParenExpr(OCLParser.ParenExprContext ctx) {
        return visit(ctx.expression());
    }

    @Override
    public ASTNode visitIfExp(OCLParser.IfExpContext ctx) {
        return new ASTIf(
                (ASTExpression) visit(ctx.condition),
                (ASTExpression) visit(ctx.thenBranch),
                (ASTExpression) visit(ctx.elseBranch));
    }

    @Override
    public ASTNode visitLetExpr(OCLParser.LetExprContext ctx) {
        return new ASTLet(
                ctx.varName.getText(),
                (ASTExpression) visit(ctx.value),
                (ASTExpression) visit(ctx.body));
    }


    @Override
    public ASTNode visitLiteral(LiteralContext ctx) {
        String text = ctx.getText();

        if (text.equals("null")) {
            return new ASTNullLiteral();
        }

        if (ctx.Number() != null) {
            if (text.contains(".")) {
                return new ASTRealLiteral(Double.parseDouble(text));
            } else {
                return new ASTIntegerLiteral(Long.parseLong(text));
            }
        }

        if (ctx.StringLiteral() != null) {
            return new ASTStringLiteral(text.substring(1, text.length() - 1));
        }

      return switch (text) {
        case "true" -> new ASTBooleanLiteral(true);
        case "false" -> new ASTBooleanLiteral(false);
        case "self" -> new ASTVar("self");
        default -> null;
      };

    }

    @Override
    public ASTNode visitIteratorExpr(OCLParser.IteratorExprContext ctx) {
        ASTExpression source = (ASTExpression) visit(ctx.primary());
        String op = ctx.iteratorOp.getText();
        String var = ctx.iteratorVar.getText();
        String typeName = ctx.iteratorType != null ? ctx.iteratorType.getText() : null;
        ASTExpression body = (ASTExpression) visit(ctx.expression());

        return new ASTIterator(source, op, var, typeName, body);
    }
    @Override
    public ASTNode visitLogicalImpliesExp(OCLParser.LogicalImpliesExpContext ctx) {
        return new ASTBinary(
            (ASTExpression) visit(ctx.left),
            "implies",
            (ASTExpression) visit(ctx.right)
        );
    }
    @Override
    public ASTNode visitNotExp(OCLParser.NotExpContext ctx) {
        ASTExpression inner = (ASTExpression) visit(ctx.expression());
        return new ASTNot(inner);
    }

    @Override
    public ASTNode visitMethodCallExpr(OCLParser.MethodCallExprContext ctx) {
        ASTExpression source = (ASTExpression) visit(ctx.primary());

        String methodName = ctx.Identifier().getText();

        List<ASTExpression> args = new ArrayList<>();
        if (ctx.argList() != null) {
            for (OCLParser.ExpressionContext argCtx : ctx.argList().expression()) {
                args.add((ASTExpression) visit(argCtx));
            }
        }

        return new ASTMethodCall(source, methodName, args);
    }

    @Override
    public ASTNode visitDeclaration(OCLParser.DeclarationContext ctx) {
        ASTExpression expr = (ASTExpression) visit(ctx.expression());
        if (ctx.invName != null || ctx.operationName == null && ctx.attributeName == null) {
            String className = ctx.className.getText();
            String invName = ctx.invName != null ? ctx.invName.getText() : "UnnamedInv";
            return new ASTContext(className, invName, expr);
        }

        if (ctx.operationName != null) {
            List<String> parameterNames = new ArrayList<>();
            List<String> parameterTypes = new ArrayList<>();
            if (ctx.paramList() != null) {
                for (OCLParser.ParamDeclContext paramDeclContext : ctx.paramList().paramDecl()) {
                    parameterNames.add(paramDeclContext.Identifier(0).getText());
                    parameterTypes.add(paramDeclContext.Identifier().size() > 1
                            ? paramDeclContext.Identifier(1).getText()
                            : null);
                }
            }
            String ruleName = ctx.ruleName != null ? ctx.ruleName.getText() : defaultRuleName(ctx.operationConstraintKind().getText());
            return new ASTOperationConstraint(
                    ctx.className.getText(),
                    ctx.operationName.getText(),
                    parameterNames,
                    parameterTypes,
                    ctx.operationConstraintKind().getText(),
                    ruleName,
                    expr);
        }

        String ruleName = ctx.ruleName != null ? ctx.ruleName.getText() : defaultRuleName(ctx.attributeConstraintKind().getText());
        return new ASTAttributeConstraint(
                ctx.className.getText(),
                ctx.attributeName.getText(),
                ctx.attributeConstraintKind().getText(),
                ruleName,
                expr);
    }

    private String defaultRuleName(String constraintKind) {
        if (constraintKind == null || constraintKind.isBlank()) {
            return "UnnamedRule";
        }
        return switch (constraintKind) {
            case "pre" -> "UnnamedPre";
            case "post" -> "UnnamedPost";
            case "body" -> "UnnamedBody";
            case "init" -> "UnnamedInit";
            case "derive" -> "UnnamedDerive";
            default -> "UnnamedRule";
        };
    }
}
