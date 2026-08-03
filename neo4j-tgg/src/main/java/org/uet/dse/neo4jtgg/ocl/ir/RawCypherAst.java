package org.uet.dse.neo4jtgg.ocl.ir;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Closed, parameterized raw Cypher AST corresponding to the proof grammar. */
public final class RawCypherAst {
    private RawCypherAst() {
    }

    public record AliasId(String value) {
        public AliasId { value = identifier(value, "alias"); }
    }

    public record ParamId(String value) {
        public ParamId { value = identifier(value, "parameter"); }
    }

    public enum PropertyKey {
        MODEL_KEY("modelKey"), CLASS_KEY("classKey"), OBJECT_KEY("objectKey"), USE_ID("use_id"),
        ATTRIBUTE_KEY("attributeKey"), VALUE("value"), ASSOCIATION_KEY("associationKey"),
        SOURCE_ROLE("sourceRole"), TARGET_ROLE("targetRole"),
        SOURCE_QUALIFIERS("sourceQualifiers"), TARGET_QUALIFIERS("targetQualifiers");

        private final String text;
        PropertyKey(String text) { this.text = text; }
        public String text() { return text; }
    }

    public enum Label {
        OBJECT("Object"), CLASS("Class"), ATTRIBUTE_VALUE("AttributeValue");
        private final String text;
        Label(String text) { this.text = text; }
        public String text() { return text; }
    }

    public enum RelType {
        OBJECT_INSTANCE_OF("ObjectInstanceOf"), INSTANCE_OF("InstanceOf"),
        OBJECT_HAS_ATTRIBUTE("ObjectHasAttribute");
        private final String text;
        RelType(String text) { this.text = text; }
        public String text() { return text; }
    }

    public enum Direction { OUTGOING, INCOMING, UNDIRECTED }
    public enum UnaryOp { NOT, NEGATE, IS_NULL }
    public enum BinaryOp {
        EQ, NEQ, LT, LE, GT, GE, ADD, SUBTRACT, MULTIPLY, DIVIDE, AND, OR, IN
    }
    public enum FunctionId {
        COALESCE("coalesce"), HEAD("head"), SIZE("size"), ANY("any"), ALL("all"),
        NONE("none"), SINGLE("single"), REDUCE("reduce"), SPLIT("split"),
        REPLACE("replace"), TO_INTEGER("toInteger"), TO_FLOAT("toFloat"),
        TO_STRING("toString"), TO_LOWER("toLower"), TO_UPPER("toUpper"), TRIM("trim");
        private final String text;
        FunctionId(String text) { this.text = text; }
        public String text() { return text; }
    }

    public sealed interface Expr permits Alias, Param, NullLiteral, BoolLiteral, IntLiteral,
            Property, ListExpr, Unary, Binary, CaseExpr, Function, ListComp, ExistsExpr, CountExpr {
    }

    public record Alias(AliasId id) implements Expr { public Alias { required(id, "id"); } }
    public record Param(ParamId id) implements Expr { public Param { required(id, "id"); } }
    public record NullLiteral() implements Expr { }
    public record BoolLiteral(boolean value) implements Expr { }
    public record IntLiteral(long value) implements Expr { }
    public record Property(Expr owner, PropertyKey key) implements Expr {
        public Property { required(owner, "owner"); required(key, "key"); }
    }
    public record ListExpr(List<Expr> elements) implements Expr {
        public ListExpr { elements = copy(elements, "elements"); }
    }
    public record Unary(UnaryOp operator, Expr operand) implements Expr {
        public Unary { required(operator, "operator"); required(operand, "operand"); }
    }
    public record Binary(BinaryOp operator, Expr left, Expr right) implements Expr {
        public Binary { required(operator, "operator"); required(left, "left"); required(right, "right"); }
    }
    public record WhenThen(Expr condition, Expr value) {
        public WhenThen { required(condition, "condition"); required(value, "value"); }
    }
    public record CaseExpr(List<WhenThen> branches, Expr otherwise) implements Expr {
        public CaseExpr {
            branches = copy(branches, "branches");
            if (branches.isEmpty()) throw new IllegalArgumentException("branches must not be empty");
            required(otherwise, "otherwise");
        }
    }
    public record Function(FunctionId function, List<Expr> arguments) implements Expr {
        public Function { required(function, "function"); arguments = copy(arguments, "arguments"); }
    }
    public record ListComp(AliasId alias, Expr source, Expr predicate, Expr projection) implements Expr {
        public ListComp {
            required(alias, "alias"); required(source, "source"); required(projection, "projection");
        }
    }
    public record ExistsExpr(Query query) implements Expr { public ExistsExpr { required(query, "query"); } }
    public record CountExpr(Query query) implements Expr { public CountExpr { required(query, "query"); } }

    public sealed interface Pattern permits NodePattern, RelPattern { }
    public record PropertyEntry(PropertyKey key, Expr value) {
        public PropertyEntry { required(key, "key"); required(value, "value"); }
    }
    public record NodePattern(AliasId alias, Label label, List<PropertyEntry> properties) implements Pattern {
        public NodePattern {
            required(alias, "alias"); properties = copy(properties, "properties");
            long uniqueKeys = properties.stream().map(PropertyEntry::key).distinct().count();
            if (uniqueKeys != properties.size()) throw new IllegalArgumentException("duplicate node property key");
        }
    }
    public record RelPattern(NodePattern left, AliasId alias, Direction direction,
                             RelType type, NodePattern right) implements Pattern {
        public RelPattern {
            required(left, "left"); required(alias, "alias"); required(direction, "direction"); required(right, "right");
        }
    }

    public record Projection(Expr expression, AliasId alias) {
        public Projection { required(expression, "expression"); required(alias, "alias"); }
    }

    public sealed interface Clause permits Match, Where, Unwind, With, Return, Call { }
    public record Match(boolean optional, List<Pattern> patterns) implements Clause {
        public Match {
            patterns = copy(patterns, "patterns");
            if (patterns.isEmpty()) throw new IllegalArgumentException("patterns must not be empty");
        }
    }
    public record Where(Expr predicate) implements Clause { public Where { required(predicate, "predicate"); } }
    public record Unwind(Expr expression, AliasId alias) implements Clause {
        public Unwind { required(expression, "expression"); required(alias, "alias"); }
    }
    public record With(boolean distinct, List<Projection> projections) implements Clause {
        public With {
            projections = copy(projections, "projections");
            if (projections.isEmpty()) throw new IllegalArgumentException("projections must not be empty");
        }
    }
    public record Return(boolean distinct, List<Projection> projections) implements Clause {
        public Return {
            projections = copy(projections, "projections");
            if (projections.isEmpty()) throw new IllegalArgumentException("projections must not be empty");
        }
    }
    public record Call(List<AliasId> imports, Query query) implements Clause {
        public Call {
            imports = copy(imports, "imports"); required(query, "query");
            if (new LinkedHashSet<>(imports).size() != imports.size())
                throw new IllegalArgumentException("CALL imports must be unique");
        }
    }

    public sealed interface Query permits Seq, UnionAll { }
    public record Seq(List<Clause> clauses) implements Query {
        public Seq {
            clauses = copy(clauses, "clauses");
            if (clauses.isEmpty()) throw new IllegalArgumentException("clauses must not be empty");
        }
    }
    public record UnionAll(Query left, Query right) implements Query {
        public UnionAll { required(left, "left"); required(right, "right"); }
    }

    /** Deterministic allocator that never returns a reserved or previously returned alias. */
    public static final class FreshNames {
        private final Set<String> used = new LinkedHashSet<>();

        public FreshNames(Set<AliasId> reserved) {
            required(reserved, "reserved");
            reserved.forEach(alias -> used.add(alias.value()));
        }

        public AliasId fresh(String stem) {
            String checkedStem = identifier(stem, "stem");
            int suffix = 1;
            String candidate = checkedStem;
            while (!used.add(candidate)) candidate = checkedStem + suffix++;
            return new AliasId(candidate);
        }
    }

    private static String identifier(String value, String label) {
        Objects.requireNonNull(value, label);
        if (!value.matches("[A-Za-z_][A-Za-z0-9_]*"))
            throw new IllegalArgumentException(label + " is not a certified identifier: " + value);
        return value;
    }

    private static <T> T required(T value, String label) { return Objects.requireNonNull(value, label); }
    private static <T> List<T> copy(List<T> values, String label) {
        return List.copyOf(required(values, label));
    }
}
