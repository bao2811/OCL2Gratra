package org.uet.dse.ocl2cypher.cypher;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.uet.dse.ocl2cypher.cypher.CypherAst.*;
import org.uet.dse.ocl2cypher.diagnostics.RuleId;
import org.uet.dse.ocl2cypher.trace.Trace;
import org.uet.dse.ocl2cypher.trace.TraceCollector;

/**
 * Deterministic serializer {@code S}: total on {@code WF_CypherAS} and
 * structure-preserving (no invented DISTINCT/OPTIONAL, no native-null fiction).
 *
 * <p>Identifiers that were introduced by {@code R} are quoted; parameters are
 * the only non-quoted {@code $name} site, and kinds are checked by
 * {@code WF_CypherAS} before rendering.
 */
public final class Serializer {

    private Serializer() {
    }

    public record Serialized(Dialect dialect, String cypherText,
                             List<QueryParameter> parameters,
                             ResultContract contract) {
        public Serialized {
            Objects.requireNonNull(dialect, "dialect");
            Objects.requireNonNull(cypherText, "cypherText");
            parameters = List.copyOf(parameters);
            Objects.requireNonNull(contract, "contract");
        }
    }

    public static Serialized serialize(GeneratedArtifact artifact) {
        CypherAstWellFormednessValidator.validate(artifact);
        StringBuilder out = new StringBuilder();
        // Make dialect selection explicit for both supported profiles.
        out.append(artifact.dialect() == Dialect.CYPHER_5
                ? "CYPHER 5\n" : "CYPHER 25\n");
        out.append(CypherText.serialize(artifact.query()));
        List<QueryParameter> parameters = artifact.parameters().stream()
                .sorted(Comparator.comparing(QueryParameter::name))
                .toList();
        return new Serialized(artifact.dialect(), out.toString(), parameters,
                artifact.contract());
    }

    public static Serialized serialize(GeneratedArtifact artifact, TraceCollector traces) {
        Serialized serialized = serialize(artifact);
        traces.record(Trace.Stage.S,
                "cypher-artifact:" + (artifact.contract().shape() == ResultShape.IDS
                        ? "violations" : "value"),
                "serialized-cypher",
                RuleId.S_ARTIFACT);
        return serialized;
    }

    /** Accessor shared by the adapter's round-trip check; body is the line above. */
    public static String cypherText(GeneratedArtifact artifact) {
        return serialize(artifact).cypherText();
    }

    static final class CypherText {
        static String serialize(CypherQuery q) {
            StringBuilder sb = new StringBuilder();
            for (CypherClause c : q.clauses()) {
                if (c instanceof MatchClause m) {
                    sb.append("MATCH ").append(pattern(m.pattern()));
                    CypherExpr where = m.where();
                    if (where != null) {
                        sb.append(" WHERE ").append(expr(where));
                    }
                    sb.append("\n");
                } else if (c instanceof WithClause w) {
                    sb.append(w.distinct() ? "WITH DISTINCT " : "WITH ");
                    sb.append(w.items().stream()
                            .map(i -> expr(i.expression())
                                    + (i.alias() == null ? "" : " AS " + quote(i.alias())))
                            .collect(Collectors.joining(", ")));
                    CypherExpr where = w.where();
                    if (where != null) {
                        sb.append(" WHERE ").append(expr(where));
                    }
                    sb.append("\n");
                } else if (c instanceof UnwindClause u) {
                    sb.append("UNWIND ").append(expr(u.expression()))
                            .append(" AS ").append(quote(u.alias())).append("\n");
                } else if (c instanceof ReturnClause r) {
                    sb.append(r.distinct() ? "RETURN DISTINCT " : "RETURN ");
                    sb.append(r.items().stream()
                            .map(i -> expr(i.expression())
                                    + (i.alias() == null ? "" : " AS " + quote(i.alias())))
                            .collect(Collectors.joining(", ")));
                    sb.append("\n");
                } else {
                    throw new IllegalStateException("unknown clause " + c.getClass().getSimpleName());
                }
            }
            return sb.toString();
        }

        static String pattern(Pattern p) {
            List<String> ps = new java.util.ArrayList<>();
            for (PathPattern pp : p.paths()) {
                StringBuilder row = new StringBuilder();
                for (int i = 0; i < pp.nodes().size(); i++) {
                    row.append(node(pp.nodes().get(i)));
                    if (i < pp.relationships().size()) {
                        row.append(rel(pp.relationships().get(i)));
                    }
                }
                ps.add(row.toString());
            }
            return String.join(", ", ps);
        }

        static String node(NodePattern n) {
            StringBuilder sb = new StringBuilder("(");
            if (n.variable() != null) {
                sb.append(quote(n.variable()));
            }
            if (!n.labels().isEmpty()) {
                for (String lbl : n.labels()) {
                    sb.append(":").append(quote(lbl));
                }
            }
            if (!n.properties().isEmpty()) {
                sb.append(" {");
                for (int i = 0; i < n.properties().size(); i++) {
                    PropertyMapEntry e = n.properties().get(i);
                    if (i > 0) {
                        sb.append(", ");
                    }
                    sb.append(quote(e.key())).append(": ").append(expr(e.value()));
                }
                sb.append("}");
            }
            return sb.append(")").toString();
        }

        static String rel(RelPattern r) {
            String t = r.typeName() == null ? "" : ":" + quote(r.typeName());
            String hops = r.minHops() == null ? ""
                    : "*" + r.minHops() + ".." + r.maxHops();
            String inner = (r.variable() == null ? "" : quote(r.variable())) + t + hops;
            if (!r.properties().isEmpty()) {
                inner += " {" + r.properties().stream()
                        .map(e -> quote(e.key()) + ": " + expr(e.value()))
                        .collect(Collectors.joining(", ")) + "}";
            }
            String pat = "-[" + inner + "]-";
            return switch (r.direction()) {
                case OUTGOING -> pat.replace("]-", "]->");
                case INCOMING -> pat.replace("-[", "<-[");
                default -> pat;
            };
        }

        static String expr(CypherExpr e) {
            if (e instanceof LocatedExpr located) {
                return expr(located.expression());
            }
            if (e instanceof VariableExpr v) {
                return quote(v.name());
            }
            if (e instanceof ParameterExpr p) {
                return "$" + p.name();
            }
            if (e instanceof NullLiteral) {
                return "null";
            }
            if (e instanceof BooleanLiteral b) {
                return b.value() ? "true" : "false";
            }
            if (e instanceof IntegerLiteral i) {
                return i.value().toString();
            }
            if (e instanceof FloatLiteral f) {
                return floatText(f.value());
            }
            if (e instanceof StringLiteral s) {
                return quoteString(s.value());
            }
            if (e instanceof ListExpr le) {
                return "[" + le.items().stream().map(CypherText::expr).collect(Collectors.joining(", "))
                        + "]";
            }
            if (e instanceof MapExpr me) {
                return "{" + me.entries().stream()
                        .map(en -> quote(en.key()) + ": " + expr(en.value()))
                        .collect(Collectors.joining(", ")) + "}";
            }
            if (e instanceof PropertyAccess pa) {
                return atomize(pa.source()) + "." + quote(pa.propertyName());
            }
            if (e instanceof UnaryExpr u) {
                return switch (u.operator()) {
                    case NOT -> "NOT (" + expr(u.operand()) + ")";
                    // Always delimit unary negation.  Without the parentheses,
                    // NEGATE(IntegerLiteral(1)) and IntegerLiteral(-1) both
                    // serialize as "-1", so syntax round-trip is not injective.
                    case NEGATE -> "-(" + expr(u.operand()) + ")";
                    case IS_NULL -> "(" + expr(u.operand()) + ") IS NULL";
                    case IS_NOT_NULL -> "(" + expr(u.operand()) + ") IS NOT NULL";
                };
            }
            if (e instanceof BinaryExpr b) {
                String op = switch (b.operator()) {
                    case OR -> "OR";
                    case XOR -> "XOR";
                    case AND -> "AND";
                    case EQUAL -> "=";
                    case NOT_EQUAL -> "<>";
                    case LESS_THAN -> "<";
                    case LESS_THAN_OR_EQUAL -> "<=";
                    case GREATER_THAN -> ">";
                    case GREATER_THAN_OR_EQUAL -> ">=";
                    case IN -> "IN";
                    case STARTS_WITH -> "STARTS WITH";
                    case ADD -> "+";
                    case SUBTRACT -> "-";
                    case MULTIPLY -> "*";
                    case DIVIDE -> "/";
                    case MODULO -> "%";
                    case LIST_CONCAT -> "||";
                };
                return "(" + expr(b.left()) + " " + op + " " + expr(b.right()) + ")";
            }
            if (e instanceof FunctionCall f) {
                String args = f.arguments().stream().map(CypherText::expr)
                        .collect(Collectors.joining(", "));
                String dis = f.distinct() ? "DISTINCT " : "";
                return f.functionName() + "(" + dis + args + ")";
            }
            if (e instanceof CaseExpr ce) {
                StringBuilder sb = new StringBuilder("CASE");
                for (WhenThen wt : ce.branches()) {
                    sb.append(" WHEN ").append(expr(wt.when()))
                            .append(" THEN ").append(expr(wt.then()));
                }
                if (ce.elseExpr() != null) {
                    sb.append(" ELSE ").append(expr(ce.elseExpr()));
                }
                return sb.append(" END").toString();
            }
            if (e instanceof ListComprehension lc) {
                // [x IN list WHERE predicate | projection]; projection defaults to x.
                StringBuilder sb = new StringBuilder("[");
                sb.append(quote(lc.variable())).append(" IN ").append(expr(lc.list()));
                if (lc.predicate() != null) {
                    sb.append(" WHERE ").append(expr(lc.predicate()));
                }
                if (lc.projection() != null) {
                    sb.append(" | ").append(expr(lc.projection()));
                }
                return sb.append("]").toString();
            }
            if (e instanceof QuantifiedPredicateExpression qp) {
                String keyword = qp.quantifier() == QuantifierKind.ANY ? "any" : "all";
                return keyword + "(" + quote(qp.variable()) + " IN " + expr(qp.list())
                        + " WHERE " + expr(qp.predicate()) + ")";
            }
            if (e instanceof ReduceExpr r) {
                return "reduce(" + quote(r.accumulator()) + " = " + expr(r.initial())
                        + ", " + quote(r.variable()) + " IN " + expr(r.list())
                        + " | " + expr(r.step()) + ")";
            }
            if (e instanceof ExistsSubquery es) {
                return "EXISTS {" + serialize(new CypherQuery(es.query().clauses(),
                        es.query().requiresFinalReturn())) + "}";
            }
            if (e instanceof CollectSubquery cs) {
                return "COLLECT {" + serialize(new CypherQuery(cs.query().clauses(),
                        cs.query().requiresFinalReturn())) + "}";
            }
            throw new IllegalStateException("no serialize case for " + e.getClass().getSimpleName());
        }

        private static String paren(CypherExpr e) {
            if (e instanceof VariableExpr || e instanceof ParameterExpr
                    || e instanceof BooleanLiteral || e instanceof IntegerLiteral
                    || e instanceof FloatLiteral || e instanceof StringLiteral) {
                return expr(e);
            }
            return "(" + expr(e) + ")";
        }

        private static String atomize(CypherExpr expression) {
            CypherExpr e = expression instanceof LocatedExpr located
                    ? located.expression() : expression;
            if (e instanceof VariableExpr || e instanceof ParameterExpr
                    || e instanceof PropertyAccess || e instanceof FunctionCall) {
                return expr(e);
            }
            return "(" + expr(e) + ")";
        }

        private static String quoteString(String value) {
            requireWellFormedUnicode(value, "string literal", true);
            StringBuilder escaped = new StringBuilder(value.length() + 2).append('\'');
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                switch (c) {
                    case '\\' -> escaped.append("\\\\");
                    case '\'' -> escaped.append("\\'");
                    case '\b' -> escaped.append("\\b");
                    case '\f' -> escaped.append("\\f");
                    case '\n' -> escaped.append("\\n");
                    case '\r' -> escaped.append("\\r");
                    case '\t' -> escaped.append("\\t");
                    default -> {
                        if (Character.isISOControl(c)) {
                            escaped.append(String.format("\\u%04X", (int) c));
                        } else {
                            escaped.append(c);
                        }
                    }
                }
            }
            return escaped.append('\'').toString();
        }

        /**
         * Emits a locale-independent, non-exponent Cypher floating literal.
         * The explicit decimal point prevents an integral BigDecimal (for
         * example {@code 1}) from being reparsed as an integer literal.
         */
        private static String floatText(java.math.BigDecimal value) {
            Objects.requireNonNull(value, "float literal");
            String text = value.stripTrailingZeros().toPlainString();
            return text.indexOf('.') >= 0 ? text : text + ".0";
        }

        private static String quote(String name) {
            requireWellFormedUnicode(name, "identifier", false);
            String escaped = name.replace("`", "``");
            return "`" + escaped + "`";
        }

        private static void requireWellFormedUnicode(String value, String role,
                                                     boolean allowControls) {
            if (value == null || (!allowControls && value.isEmpty())) {
                throw new IllegalArgumentException("invalid " + role);
            }
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (Character.isHighSurrogate(c)) {
                    if (i + 1 >= value.length()
                            || !Character.isLowSurrogate(value.charAt(i + 1))) {
                        throw new IllegalArgumentException("unpaired surrogate in " + role);
                    }
                    i++;
                } else if (Character.isLowSurrogate(c)) {
                    throw new IllegalArgumentException("unpaired surrogate in " + role);
                } else if (!allowControls && Character.isISOControl(c)) {
                    throw new IllegalArgumentException("control character in " + role);
                }
            }
        }
    }
}
