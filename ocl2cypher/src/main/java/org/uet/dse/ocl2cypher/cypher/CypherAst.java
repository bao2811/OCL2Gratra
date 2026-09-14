package org.uet.dse.ocl2cypher.cypher;

import java.util.List;
import java.util.Objects;
import org.uet.dse.ocl2cypher.diagnostics.SourceSpan;

/**
 * CypherAS — the read-only Cypher target profile abstract syntax.
 *
 * <p>This is not full Cypher/GQL: no writes, no procedures, no dynamic labels,
 * no {@code OPTIONAL MATCH}. Every value crossing a read path is a tagged map
 * so that OCL typed bottom, an empty collection, and a collection containing an
 * element-bottom stay pairwise distinct — native {@code null} is only ever the
 * guarded payload of a bottom tag, never an OCL value on its own.
 *
 * <p>{@code WHERE} is a subclause of {@code MATCH}/{@code WITH}, not a clause;
 * an {@code ExistsSubquery} node may omit its final {@code RETURN}
 * ({@code requiresFinalReturn=false}) while every top-level / {@code COLLECT}
 * query keeps it.
 */
public final class CypherAst {

    private CypherAst() {
    }

    public enum Dialect {
        CYPHER_5,
        CYPHER_25
    }

    public enum RelDirection {
        OUTGOING,
        INCOMING,
        UNDIRECTED
    }

    public enum ResultShape {
        SCALAR,
        SET,
        BAG,
        IDS
    }

    // ---- artifact + contract --------------------------------------------

    public record GeneratedArtifact(Dialect dialect,
                                    CypherQuery query,
                                    ResultContract contract,
                                    List<QueryParameter> parameters) {
        public GeneratedArtifact {
            Objects.requireNonNull(dialect, "dialect");
            Objects.requireNonNull(query, "query");
            Objects.requireNonNull(contract, "result contract");
            parameters = List.copyOf(parameters);
        }
    }

    /** {@code Δπ} entry; a public caller parameter or a compiler-generated binding. */
    public record QueryParameter(String name, String logicalTypeTag, Origin origin,
                                 String canonicalValue) {
        public enum Origin { PUBLIC, GENERATED }

        public QueryParameter {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(origin, "origin");
        }
    }

    public record ResultContract(ResultShape shape, String resultVariable,
                                 String elementTypeTag, boolean distinctRequired,
                                 String wholeBottomTag) {
    }

    // ---- query + clauses -------------------------------------------------

    /** Common, semantics-free source-location view for target AST nodes. */
    public interface Spanned {
        default SourceSpan span() {
            return SourceSpan.UNKNOWN;
        }
    }

    public record CypherQuery(List<CypherClause> clauses, boolean requiresFinalReturn,
                              SourceSpan span) implements Spanned {
        public CypherQuery(List<CypherClause> clauses, boolean requiresFinalReturn) {
            this(clauses, requiresFinalReturn, SourceSpan.UNKNOWN);
        }

        public CypherQuery {
            clauses = List.copyOf(clauses);
            span = Objects.requireNonNull(span, "query span");
        }
    }

    public sealed interface CypherClause extends Spanned
            permits MatchClause, WithClause, UnwindClause, ReturnClause {
    }

    public record MatchClause(Pattern pattern, CypherExpr where) implements CypherClause {
    }

    public record WithClause(boolean distinct, List<ProjectionItem> items, CypherExpr where)
            implements CypherClause {
        public WithClause {
            items = List.copyOf(items);
        }
    }

    public record UnwindClause(CypherExpr expression, String alias) implements CypherClause {
    }

    public record ReturnClause(boolean distinct, List<ProjectionItem> items)
            implements CypherClause {
        public ReturnClause {
            items = List.copyOf(items);
        }
    }

    public record ProjectionItem(CypherExpr expression, String alias) {
    }

    // ---- patterns --------------------------------------------------------

    public record Pattern(List<PathPattern> paths) {
        public Pattern {
            paths = List.copyOf(paths);
        }
    }

    public record PathPattern(List<NodePattern> nodes, List<RelPattern> relationships) {
        public PathPattern {
            nodes = List.copyOf(nodes);
            relationships = List.copyOf(relationships);
        }
    }

    public record NodePattern(String variable, List<String> labels,
                              List<PropertyMapEntry> properties) {
        public NodePattern {
            labels = List.copyOf(labels);
            properties = List.copyOf(properties);
        }
    }

    public record RelPattern(String variable, String typeName, RelDirection direction,
                             List<PropertyMapEntry> properties,
                             Integer minHops, Integer maxHops) {
        public RelPattern(String variable, String typeName, RelDirection direction,
                          List<PropertyMapEntry> properties) {
            this(variable, typeName, direction, properties, null, null);
        }

        public RelPattern {
            properties = List.copyOf(properties);
            if ((minHops == null) != (maxHops == null)
                    || (minHops != null && (minHops < 0 || maxHops < minHops))) {
                throw new IllegalArgumentException("invalid relationship hop range");
            }
        }
    }

    public record PropertyMapEntry(String key, CypherExpr value) {
    }

    // ---- expressions -----------------------------------------------------

    public sealed interface CypherExpr extends Spanned
            permits VariableExpr, ParameterExpr, NullLiteral, BooleanLiteral, IntegerLiteral,
                    FloatLiteral, StringLiteral, ListExpr, MapExpr, PropertyAccess,
                    UnaryExpr, BinaryExpr, FunctionCall, CaseExpr, ListComprehension,
                    QuantifiedPredicateExpression, ReduceExpr, ExistsSubquery, CollectSubquery,
                    LocatedExpr {
    }

    /**
     * Transparent source-location carrier. Serializers and validators unwrap it;
     * therefore trace metadata cannot change target Cypher semantics.
     */
    public record LocatedExpr(CypherExpr expression, SourceSpan span) implements CypherExpr {
        public LocatedExpr {
            Objects.requireNonNull(expression, "located expression");
            Objects.requireNonNull(span, "expression span");
            if (expression instanceof LocatedExpr nested) {
                expression = nested.expression();
            }
        }
    }

    public static CypherExpr withSpan(CypherExpr expression, SourceSpan span) {
        Objects.requireNonNull(expression, "expression");
        Objects.requireNonNull(span, "span");
        if (!span.isKnown()) {
            return expression;
        }
        return new LocatedExpr(expression, span);
    }

    public record VariableExpr(String name) implements CypherExpr {
    }

    public record ParameterExpr(String name) implements CypherExpr {
    }

    public record NullLiteral() implements CypherExpr {
    }

    public record BooleanLiteral(boolean value) implements CypherExpr {
    }

    public record IntegerLiteral(java.math.BigInteger value) implements CypherExpr {
    }

    public record FloatLiteral(java.math.BigDecimal value) implements CypherExpr {
    }

    public record StringLiteral(String value) implements CypherExpr {
    }

    public record ListExpr(List<CypherExpr> items) implements CypherExpr {
        public ListExpr {
            items = List.copyOf(items);
        }
    }

    public record MapExpr(List<MapEntry> entries) implements CypherExpr {
        public MapExpr {
            entries = List.copyOf(entries);
        }
    }

    public record MapEntry(String key, CypherExpr value) {
    }

    public record PropertyAccess(CypherExpr source, String propertyName) implements CypherExpr {
    }

    public enum UnaryOp { NOT, NEGATE, IS_NULL, IS_NOT_NULL }

    public record UnaryExpr(UnaryOp operator, CypherExpr operand) implements CypherExpr {
    }

    public enum BinaryOp {
        OR, XOR, AND, EQUAL, NOT_EQUAL, LESS_THAN, LESS_THAN_OR_EQUAL,
        GREATER_THAN, GREATER_THAN_OR_EQUAL, IN, STARTS_WITH,
        ADD, SUBTRACT, MULTIPLY, DIVIDE, MODULO, LIST_CONCAT
    }

    public record BinaryExpr(BinaryOp operator, CypherExpr left, CypherExpr right)
            implements CypherExpr {
    }

    public record FunctionCall(String functionName, boolean distinct, List<CypherExpr> arguments)
            implements CypherExpr {
        public FunctionCall {
            arguments = List.copyOf(arguments);
        }
    }

    public record CaseExpr(List<WhenThen> branches, CypherExpr elseExpr) implements CypherExpr {
        public CaseExpr {
            branches = List.copyOf(branches);
        }
    }

    public record WhenThen(CypherExpr when, CypherExpr then) {
    }

    /**
     * List comprehension {@code [x IN list WHERE predicate | projection]}.
     * At least one of {@code predicate}/{@code projection} is non-null (§ profile).
     * Used by {@code R} for filter/select-reject and for the exists/forAll folds
     * over the tagged occurrence list.
     */
    public record ListComprehension(String variable, CypherExpr list,
                                    CypherExpr predicate, CypherExpr projection)
            implements CypherExpr {
    }

    public enum QuantifierKind { ANY, ALL }

    /**
     * Cypher quantified predicate {@code any(x IN list WHERE predicate)} or
     * {@code all(x IN list WHERE predicate)}.  This is deliberately distinct
     * from a function call whose argument happens to be a list comprehension:
     * the latter is not the target syntax defined by the CypherAS profile.
     */
    public record QuantifiedPredicateExpression(QuantifierKind quantifier,
                                                String variable,
                                                CypherExpr list,
                                                CypherExpr predicate)
            implements CypherExpr {
        public QuantifiedPredicateExpression {
            Objects.requireNonNull(quantifier, "quantifier");
            Objects.requireNonNull(variable, "variable");
            Objects.requireNonNull(list, "list");
            Objects.requireNonNull(predicate, "predicate");
        }
    }

    /** Cypher's reduce(acc = init, item IN list | step) expression. */
    public record ReduceExpr(String accumulator, CypherExpr initial,
                             String variable, CypherExpr list, CypherExpr step)
            implements CypherExpr {
    }

    public record ExistsSubquery(CypherQuery query) implements CypherExpr {
    }

    public record CollectSubquery(CypherQuery query) implements CypherExpr {
    }
}
