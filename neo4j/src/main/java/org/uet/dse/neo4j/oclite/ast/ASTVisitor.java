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
        if (!ctx.declaration().isEmpty()) {
            return visit(ctx.declaration(0));
        }

        if (!ctx.expression().isEmpty()) {
            return visit(ctx.expression(0));
        }
        return null;
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
    public ASTNode visitNavigationExpr(OCLParser.NavigationExprContext ctx) {
        return new ASTProperty((ASTExpression)visit(ctx.primary()), ctx.Identifier().getText());
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
        String op = ctx.Identifier(0).getText();
        String var = ctx.Identifier(1).getText(); // f
        ASTExpression body = (ASTExpression) visit(ctx.expression());

        return new ASTIterator(source, op, var, body);
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
        String className = ctx.className.getText();
        String invName = ctx.invName != null ? ctx.invName.getText() : "UnnamedInv";
        ASTExpression expr = (ASTExpression) visit(ctx.expression());
        return new ASTContext(className, invName, expr);
    }
}