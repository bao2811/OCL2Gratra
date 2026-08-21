package org.uet.dse.neo4j.oclite.ast;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.uet.dse.neo4j.OCLBaseVisitor;
import org.uet.dse.neo4j.OCLParser;

import java.util.ArrayList;
import java.util.List;

/** Builds an unresolved, source-located surface AST from the plugin OCL grammar. */
public final class ASTVisitor extends OCLBaseVisitor<ASTNode> {

    @Override
    public ASTNode visitOclFile(OCLParser.OclFileContext ctx) {
        ASTFile file = at(new ASTFile(), ctx);
        for (OCLParser.DeclarationContext declaration : ctx.declaration()) {
            file.addElement(visit(declaration));
        }
        if (ctx.expression() != null) {
            file.addElement(visit(ctx.expression()));
        }
        return file;
    }

    @Override
    public ASTNode visitDeclaration(OCLParser.DeclarationContext ctx) {
        ASTExpression expression = expression(ctx.expression());
        String className = ctx.contextType.getText();
        if (ctx.INV() != null) {
            String name = ctx.ruleName == null ? "UnnamedInv" : ctx.ruleName.getText();
            return at(new ASTContext(className, name, expression), ctx);
        }

        String kind = ctx.operationConstraintKind() != null
                ? ctx.operationConstraintKind().getText()
                : ctx.attributeConstraintKind().getText();
        String name = ctx.ruleName == null ? defaultRuleName(kind) : ctx.ruleName.getText();
        if (ctx.operationName != null) {
            List<String> parameterNames = new ArrayList<>();
            List<String> parameterTypes = new ArrayList<>();
            if (ctx.paramList() != null) {
                for (OCLParser.ParamDeclContext parameter : ctx.paramList().paramDecl()) {
                    parameterNames.add(parameter.name.getText());
                    parameterTypes.add(parameter.type == null ? null : parameter.type.getText());
                }
            }
            return at(new ASTOperationConstraint(
                    className,
                    ctx.operationName.getText(),
                    parameterNames,
                    parameterTypes,
                    ctx.returnType == null ? null : ctx.returnType.getText(),
                    kind,
                    name,
                    expression), ctx);
        }

        return at(new ASTAttributeConstraint(
                className,
                ctx.attributeName.getText(),
                ctx.attributeType == null ? null : ctx.attributeType.getText(),
                kind,
                name,
                expression), ctx);
    }

    @Override
    public ASTNode visitExpression(OCLParser.ExpressionContext ctx) {
        return visit(ctx.letExpression());
    }

    @Override
    public ASTNode visitLetExpression(OCLParser.LetExpressionContext ctx) {
        if (ctx.LET() == null) {
            return visit(ctx.impliesExpression());
        }
        ASTExpression body = expression(ctx.expression());
        List<OCLParser.LetBindingContext> bindings = ctx.letBinding();
        for (int i = bindings.size() - 1; i >= 0; i--) {
            OCLParser.LetBindingContext binding = bindings.get(i);
            ASTLet let = new ASTLet(
                    binding.name.getText(),
                    binding.type == null ? null : binding.type.getText(),
                    expression(binding.expression()),
                    body);
            body = at(let, binding.getStart(), ctx.getStop());
        }
        return body;
    }

    @Override
    public ASTNode visitImpliesExpression(OCLParser.ImpliesExpressionContext ctx) {
        ASTExpression left = expression(ctx.left);
        if (ctx.right == null) {
            return left;
        }
        return at(new ASTBinary(left, "implies", expression(ctx.right)), ctx);
    }

    @Override
    public ASTNode visitOrExpression(OCLParser.OrExpressionContext ctx) {
        return fold(ctx.first, ctx.operators, ctx.rest, ctx);
    }

    @Override
    public ASTNode visitXorExpression(OCLParser.XorExpressionContext ctx) {
        return fold(ctx.first, ctx.operators, ctx.rest, ctx);
    }

    @Override
    public ASTNode visitAndExpression(OCLParser.AndExpressionContext ctx) {
        return fold(ctx.first, ctx.operators, ctx.rest, ctx);
    }

    @Override
    public ASTNode visitEqualityExpression(OCLParser.EqualityExpressionContext ctx) {
        return fold(ctx.first, ctx.operators, ctx.rest, ctx);
    }

    @Override
    public ASTNode visitRelationalExpression(OCLParser.RelationalExpressionContext ctx) {
        return fold(ctx.first, ctx.operators, ctx.rest, ctx);
    }

    @Override
    public ASTNode visitAdditiveExpression(OCLParser.AdditiveExpressionContext ctx) {
        return fold(ctx.first, ctx.operators, ctx.rest, ctx);
    }

    @Override
    public ASTNode visitMultiplicativeExpression(OCLParser.MultiplicativeExpressionContext ctx) {
        return fold(ctx.first, ctx.operators, ctx.rest, ctx);
    }

    @Override
    public ASTNode visitUnaryExpression(OCLParser.UnaryExpressionContext ctx) {
        if (ctx.op == null) {
            return visit(ctx.postfixExpression());
        }
        ASTExpression operand = expression(ctx.unaryExpression());
        ASTExpression unary = "not".equals(ctx.op.getText())
                ? new ASTNot(operand)
                : new ASTUnary(ctx.op.getText(), operand);
        return at(unary, ctx);
    }

    @Override
    public ASTNode visitPostfixExpression(OCLParser.PostfixExpressionContext ctx) {
        ASTExpression result = expression(ctx.primaryExpression());
        for (OCLParser.PostfixPartContext part : ctx.postfixPart()) {
            if (part instanceof OCLParser.DotPostfixContext dot) {
                List<ASTExpression> qualifiers = new ArrayList<>();
                for (OCLParser.QualifierListContext qualifier : dot.qualifierList()) {
                    qualifiers.addAll(arguments(qualifier.argumentValues()));
                }
                boolean atPre = dot.atPre() != null;
                if (dot.argumentList() == null) {
                    result = at(new ASTProperty(result, dot.featureName.getText(), qualifiers, atPre),
                            ctx.getStart(), dot.getStop());
                } else {
                    result = at(new ASTMethodCall(result, dot.featureName.getText(),
                                    arguments(dot.argumentList().argumentValues()), atPre),
                            ctx.getStart(), dot.getStop());
                }
                continue;
            }
            if (part instanceof OCLParser.IteratorPostfixContext iterator) {
                List<ASTVariableDeclaration> variables = new ArrayList<>();
                for (OCLParser.VariableDeclarationContext variable
                        : iterator.iteratorDeclarationList().variableDeclaration()) {
                    variables.add(new ASTVariableDeclaration(
                            variable.name.getText(),
                            variable.type == null ? null : variable.type.getText()));
                }
                result = at(new ASTIterator(result, iterator.operationName.getText(),
                                variables, expression(iterator.expression())),
                        ctx.getStart(), iterator.getStop());
                continue;
            }
            if (part instanceof OCLParser.IteratePostfixContext iterate) {
                List<ASTVariableDeclaration> variables = new ArrayList<>();
                for (OCLParser.VariableDeclarationContext variable
                        : iterate.iteratorDeclarationList().variableDeclaration()) {
                    variables.add(new ASTVariableDeclaration(
                            variable.name.getText(),
                            variable.type == null ? null : variable.type.getText()));
                }
                OCLParser.LetBindingContext accumulator = iterate.accumulator;
                result = at(new ASTIterate(
                                result,
                                iterate.operationName.getText(),
                                variables,
                                new ASTVariableDeclaration(
                                        accumulator.name.getText(),
                                        accumulator.type == null ? null : accumulator.type.getText()),
                                expression(accumulator.expression()),
                                expression(iterate.expression())),
                        ctx.getStart(), iterate.getStop());
                continue;
            }
            OCLParser.ArrowOperationPostfixContext operation =
                    (OCLParser.ArrowOperationPostfixContext) part;
            result = at(new ASTCollectionOp(result, operation.operationName.getText(),
                            arguments(operation.argumentList().argumentValues())),
                    ctx.getStart(), operation.getStop());
        }
        return result;
    }

    @Override
    public ASTNode visitPrimaryExpression(OCLParser.PrimaryExpressionContext ctx) {
        if (ctx.IF() != null) {
            return at(new ASTIf(
                    expression(ctx.condition),
                    expression(ctx.thenBranch),
                    expression(ctx.elseBranch)), ctx);
        }
        if (ctx.literal() != null) {
            return visit(ctx.literal());
        }
        if (ctx.collectionLiteral() != null) {
            return visit(ctx.collectionLiteral());
        }
        if (!ctx.expression().isEmpty()) {
            return visit(ctx.expression(0));
        }

        List<Token> identifiers = ctx.qualifiedName().Identifier().stream()
                .map(node -> node.getSymbol())
                .toList();
        if (identifiers.size() > 1) {
            String typeName = identifiers.stream().limit(identifiers.size() - 1L)
                    .map(Token::getText).reduce((left, right) -> left + "::" + right).orElse("");
            return at(new ASTEnumLiteral(typeName, identifiers.get(identifiers.size() - 1).getText()), ctx);
        }
        return at(new ASTVar(identifiers.get(0).getText()), ctx);
    }

    @Override
    public ASTNode visitCollectionLiteral(OCLParser.CollectionLiteralContext ctx) {
        List<ASTExpression> elements = new ArrayList<>();
        for (OCLParser.CollectionLiteralPartContext part : ctx.collectionLiteralPart()) {
            elements.add(expression(part));
        }
        String kind = ctx.collectionKind().getText();
        ASTExpression literal = "Set".equals(kind)
                ? new ASTSetLiteral(elements)
                : new ASTCollectionLiteral(kind, elements);
        return at(literal, ctx);
    }

    @Override
    public ASTNode visitCollectionLiteralPart(OCLParser.CollectionLiteralPartContext ctx) {
        ASTExpression first = expression(ctx.first);
        if (ctx.last == null) {
            return first;
        }
        return at(new ASTCollectionRange(first, expression(ctx.last)), ctx);
    }

    @Override
    public ASTNode visitLiteral(OCLParser.LiteralContext ctx) {
        if (ctx.IntegerLiteral() != null) {
            return at(new ASTIntegerLiteral(Long.parseLong(ctx.IntegerLiteral().getText())), ctx);
        }
        if (ctx.RealLiteral() != null) {
            return at(new ASTRealLiteral(Double.parseDouble(ctx.RealLiteral().getText())), ctx);
        }
        if (ctx.StringLiteral() != null) {
            String token = ctx.StringLiteral().getText();
            return at(new ASTStringLiteral(token.substring(1, token.length() - 1).replace("''", "'")), ctx);
        }
        if (ctx.TRUE() != null) {
            return at(new ASTBooleanLiteral(true), ctx);
        }
        if (ctx.FALSE() != null) {
            return at(new ASTBooleanLiteral(false), ctx);
        }
        if (ctx.NULL() != null) {
            return at(new ASTNullLiteral(), ctx);
        }
        return at(new ASTInvalidLiteral(), ctx);
    }

    private ASTExpression fold(
            ParserRuleContext first,
            List<Token> operators,
            List<? extends ParserRuleContext> rest,
            ParserRuleContext whole) {
        ASTExpression result = expression(first);
        for (int i = 0; i < operators.size(); i++) {
            result = at(new ASTBinary(result, operators.get(i).getText(), expression(rest.get(i))),
                    whole.getStart(), rest.get(i).getStop());
        }
        return result;
    }

    private List<ASTExpression> arguments(OCLParser.ArgumentValuesContext ctx) {
        if (ctx == null) {
            return List.of();
        }
        return ctx.expression().stream().map(this::expression).toList();
    }

    private ASTExpression expression(ParserRuleContext ctx) {
        return (ASTExpression) visit(ctx);
    }

    private <T extends ASTNode> T at(T node, ParserRuleContext ctx) {
        return at(node, ctx.getStart(), ctx.getStop());
    }

    private <T extends ASTNode> T at(T node, Token start, Token stop) {
        int endOffset = stop.getStopIndex();
        int endColumn = stop.getCharPositionInLine()
                + Math.max(stop.getText() == null ? 0 : stop.getText().length() - 1, 0);
        node.setSourceSpan(new SourceSpan(
                start.getStartIndex(),
                endOffset,
                start.getLine(),
                start.getCharPositionInLine(),
                stop.getLine(),
                endColumn));
        return node;
    }

    private String defaultRuleName(String constraintKind) {
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
