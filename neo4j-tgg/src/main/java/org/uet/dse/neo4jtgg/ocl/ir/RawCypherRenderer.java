package org.uet.dse.neo4jtgg.ocl.ir;

import java.util.stream.Collectors;

/** Total structural pretty-printer for {@link RawCypherAst}. */
public final class RawCypherRenderer {
    public String render(RawCypherAst.Query query) {
        if (query instanceof RawCypherAst.Seq value)
            return value.clauses().stream().map(this::renderClause).collect(Collectors.joining("\n"));
        if (query instanceof RawCypherAst.UnionAll value)
            return render(value.left()) + "\nUNION ALL\n" + render(value.right());
        if (query instanceof RawCypherAst.ProductionQuery value) {
            StringBuilder result = new StringBuilder();
            appendRawNodes(value.nodes(), result);
            return result.append(value.trailingTrivia()).toString();
        }
        throw unknown(query);
    }

    private void appendRawNodes(java.util.List<RawCypherAst.RawNode> nodes, StringBuilder target) {
        for (RawCypherAst.RawNode node : nodes) {
            if (node instanceof RawCypherAst.RawAtom atom) {
                target.append(atom.leadingTrivia()).append(atom.text());
            } else if (node instanceof RawCypherAst.RawGroup group) {
                target.append(group.open().leadingTrivia()).append(group.open().text());
                appendRawNodes(group.nodes(), target);
                target.append(group.close().leadingTrivia()).append(group.close().text());
            } else {
                throw unknown(node);
            }
        }
    }

    public String renderExpr(RawCypherAst.Expr expression) {
        if (expression instanceof RawCypherAst.Alias value) return value.id().value();
        if (expression instanceof RawCypherAst.Param value) return "$" + value.id().value();
        if (expression instanceof RawCypherAst.NullLiteral) return "null";
        if (expression instanceof RawCypherAst.BoolLiteral value) return Boolean.toString(value.value());
        if (expression instanceof RawCypherAst.IntLiteral value) return Long.toString(value.value());
        if (expression instanceof RawCypherAst.Property value)
            return "(" + renderExpr(value.owner()) + ")." + value.key().text();
        if (expression instanceof RawCypherAst.ListExpr value)
            return "[" + joinExpr(value.elements()) + "]";
        if (expression instanceof RawCypherAst.Unary value)
            return switch (value.operator()) {
                case NOT -> "(NOT " + renderExpr(value.operand()) + ")";
                case NEGATE -> "(-" + renderExpr(value.operand()) + ")";
                case IS_NULL -> "(" + renderExpr(value.operand()) + " IS NULL)";
            };
        if (expression instanceof RawCypherAst.Binary value)
            return "(" + renderExpr(value.left()) + " " + binary(value.operator()) + " "
                    + renderExpr(value.right()) + ")";
        if (expression instanceof RawCypherAst.CaseExpr value) {
            String branches = value.branches().stream()
                    .map(branch -> " WHEN " + renderExpr(branch.condition()) + " THEN " + renderExpr(branch.value()))
                    .collect(Collectors.joining());
            return "CASE" + branches + " ELSE " + renderExpr(value.otherwise()) + " END";
        }
        if (expression instanceof RawCypherAst.Function value)
            return value.function().text() + "(" + joinExpr(value.arguments()) + ")";
        if (expression instanceof RawCypherAst.ListComp value)
            return "[" + value.alias().value() + " IN " + renderExpr(value.source())
                    + (value.predicate() == null ? "" : " WHERE " + renderExpr(value.predicate()))
                    + " | " + renderExpr(value.projection()) + "]";
        if (expression instanceof RawCypherAst.ExistsExpr value) return "EXISTS { " + render(value.query()) + " }";
        if (expression instanceof RawCypherAst.CountExpr value) return "COUNT { " + render(value.query()) + " }";
        throw unknown(expression);
    }

    private String renderClause(RawCypherAst.Clause clause) {
        if (clause instanceof RawCypherAst.Match value)
            return (value.optional() ? "OPTIONAL MATCH " : "MATCH ")
                    + value.patterns().stream().map(this::renderPattern).collect(Collectors.joining(", "));
        if (clause instanceof RawCypherAst.Where value) return "WHERE " + renderExpr(value.predicate());
        if (clause instanceof RawCypherAst.Unwind value)
            return "UNWIND " + renderExpr(value.expression()) + " AS " + value.alias().value();
        if (clause instanceof RawCypherAst.With value)
            return "WITH " + (value.distinct() ? "DISTINCT " : "") + projections(value.projections());
        if (clause instanceof RawCypherAst.Return value)
            return "RETURN " + (value.distinct() ? "DISTINCT " : "") + projections(value.projections());
        if (clause instanceof RawCypherAst.Call value) {
            return "CALL {\n" + renderWithImports(value.imports(), value.query()) + "\n}";
        }
        throw unknown(clause);
    }

    /**
     * Cypher imports are branch-local: every UNION ALL arm starts a fresh query
     * scope, so the importing WITH must be injected into each arm.
     */
    private String renderWithImports(java.util.List<RawCypherAst.AliasId> imports, RawCypherAst.Query query) {
        if (imports.isEmpty()) return render(query);
        if (query instanceof RawCypherAst.Seq) return renderImports(imports) + "\n" + render(query);
        if (query instanceof RawCypherAst.UnionAll value)
            return renderWithImports(imports, value.left()) + "\nUNION ALL\n"
                    + renderWithImports(imports, value.right());
        throw unknown(query);
    }

    private String renderImports(java.util.List<RawCypherAst.AliasId> imports) {
        return "WITH " + imports.stream().map(RawCypherAst.AliasId::value).collect(Collectors.joining(", "));
    }

    private String renderPattern(RawCypherAst.Pattern pattern) {
        if (pattern instanceof RawCypherAst.NodePattern value) return renderNode(value);
        if (pattern instanceof RawCypherAst.RelPattern value) {
            String relationship = "[" + value.alias().value()
                    + (value.type() == null ? "" : ":" + value.type().text()) + "]";
            return switch (value.direction()) {
                case OUTGOING -> renderNode(value.left()) + "-" + relationship + "->" + renderNode(value.right());
                case INCOMING -> renderNode(value.left()) + "<-" + relationship + "-" + renderNode(value.right());
                case UNDIRECTED -> renderNode(value.left()) + "-" + relationship + "-" + renderNode(value.right());
            };
        }
        throw unknown(pattern);
    }

    private String renderNode(RawCypherAst.NodePattern node) {
        String label = node.label() == null ? "" : ":" + node.label().text();
        String properties = node.properties().isEmpty() ? "" : " {"
                + node.properties().stream().map(entry -> entry.key().text() + ": " + renderExpr(entry.value()))
                .collect(Collectors.joining(", ")) + "}";
        return "(" + node.alias().value() + label + properties + ")";
    }

    private String projections(java.util.List<RawCypherAst.Projection> values) {
        return values.stream().map(value -> renderExpr(value.expression()) + " AS " + value.alias().value())
                .collect(Collectors.joining(", "));
    }

    private String joinExpr(java.util.List<RawCypherAst.Expr> expressions) {
        return expressions.stream().map(this::renderExpr).collect(Collectors.joining(", "));
    }

    private String binary(RawCypherAst.BinaryOp operator) {
        return switch (operator) {
            case EQ -> "="; case NEQ -> "<>"; case LT -> "<"; case LE -> "<=";
            case GT -> ">"; case GE -> ">="; case ADD -> "+"; case SUBTRACT -> "-";
            case MULTIPLY -> "*"; case DIVIDE -> "/"; case AND -> "AND"; case OR -> "OR"; case IN -> "IN";
        };
    }

    private IllegalArgumentException unknown(Object value) {
        return new IllegalArgumentException("Unknown raw Cypher AST constructor: "
                + (value == null ? "null" : value.getClass().getName()));
    }
}
