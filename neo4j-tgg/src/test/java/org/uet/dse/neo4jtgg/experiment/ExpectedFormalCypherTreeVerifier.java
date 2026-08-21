package org.uet.dse.neo4jtgg.experiment;

import org.uet.dse.neo4jtgg.ocl.ir.OclCypherPlan;
import org.uet.dse.neo4jtgg.ocl.ir.OclCypherRenderer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Constructor-directed bridge from {@link OclCypherPlan} to the normalized
 * generated-Cypher tree. Token/group facts come from the independent checked
 * fragment parser; AST-kind facts must also be witnessed by Neo4j's real
 * Cypher 5 parser and internal AST through {@link Neo4jCypherAstBridge}.
 */
final class ExpectedFormalCypherTreeVerifier {
    private static final Set<Class<?>> RULE_TYPES = Set.of(
            OclCypherPlan.VariablePlan.class,
            OclCypherPlan.LiteralPlan.class,
            OclCypherPlan.SetLiteralPlan.class,
            OclCypherPlan.NotPlan.class,
            OclCypherPlan.IfPlan.class,
            OclCypherPlan.LetPlan.class,
            OclCypherPlan.BinaryPlan.class,
            OclCypherPlan.AttributeAccessPlan.class,
            OclCypherPlan.NavigationAccessPlan.class,
            OclCypherPlan.MethodCallPlan.class,
            OclCypherPlan.CollectionOperationPlan.class,
            OclCypherPlan.IteratorOperationPlan.class,
            OclCypherPlan.ExistsSubqueryPlan.class,
            OclCypherPlan.NotExistsSubqueryPlan.class,
            OclCypherPlan.CountSubqueryComparisonPlan.class,
            OclCypherPlan.NavigationAggregationPlan.class,
            OclCypherPlan.NavigationUniquenessPlan.class);

    private ExpectedFormalCypherTreeVerifier() {
    }

    static AgreementNode verifyInvariant(OclCypherPlan.InvariantPlan invariant, String modelName) {
        OclCypherRenderer renderer = new OclCypherRenderer(modelName);
        OclCypherRenderer.RenderedInvariant rendered = renderer.renderInvariant(invariant);
        ObservedTree observed = ObservedTree.parse(rendered.cypher());
        require(observed, invariant, "invariant wrapper", ast(AstKind.SINGLE_QUERY, AstKind.MATCH, AstKind.RETURN),
                token("WHERE"), token("DISTINCT"), token("ObjectInstanceOf"), token("UmlClass"), token("classKey"),
                token("use_id"), token("useId"), token("NOT"), token("coalesce"));
        return verifyExpression(invariant.predicate(), renderer);
    }

    static AgreementNode verifyExpression(OclCypherPlan.ExpressionPlan plan, OclCypherRenderer renderer) {
        OclCypherRenderer.RenderedTopLevelExpression rendered = renderer.renderTopLevelExpression(plan);
        AgreementNode node = verifyExpressionAgainstText(plan, rendered.cypher(), rendered.parameters());
        List<AgreementNode> children = children(plan).stream()
                .map(child -> verifyExpression(child, renderer))
                .toList();
        return new AgreementNode(node.constructor(), node.discriminator(), node.expectedAst(),
                node.normalizedGeneratedText(), children);
    }

    static AgreementNode verifyExpressionAgainstText(OclCypherPlan.ExpressionPlan plan,
                                                      String cypher,
                                                      Map<String, Object> parameters) {
        ObservedTree observed = ObservedTree.parse(cypher);
        List<Requirement> expected = new ArrayList<>();
        expected.add(ast(AstKind.SINGLE_QUERY, AstKind.RETURN));
        expected.add(token("AS"));
        expected.add(token("value"));
        String discriminator = expectations(plan, parameters, expected);
        for (Requirement requirement : expected) {
            assertTrue(requirement.matches(observed), () -> plan.getClass().getSimpleName() + "[" + discriminator
                    + "] lacks " + requirement.description() + " in normalized tree: " + observed.normalized());
        }
        return new AgreementNode(plan.getClass().getSimpleName(), discriminator,
                expected.stream().map(Requirement::description).toList(), observed.normalized(), List.of());
    }

    static void assertEveryPlanConstructorHasAFormalRule() {
        assertEquals(new LinkedHashSet<>(Arrays.asList(OclCypherPlan.ExpressionPlan.class.getPermittedSubclasses())),
                new LinkedHashSet<>(RULE_TYPES));
    }

    static Set<Class<?>> constructorClasses(OclCypherPlan.ExpressionPlan root) {
        Set<Class<?>> result = new LinkedHashSet<>();
        collectClasses(root, result);
        return result;
    }

    private static void collectClasses(OclCypherPlan.ExpressionPlan plan, Set<Class<?>> target) {
        target.add(plan.getClass());
        children(plan).forEach(child -> collectClasses(child, target));
    }

    private static String expectations(OclCypherPlan.ExpressionPlan plan,
                                       Map<String, Object> parameters,
                                       List<Requirement> expected) {
        if (plan instanceof OclCypherPlan.VariablePlan value) {
            expected.add(ast(AstKind.PARAMETER));
            expected.add(token("$" + value.name()));
            return value.name();
        }
        if (plan instanceof OclCypherPlan.LiteralPlan value) {
            if (value.value() == null) expected.add(token("NULL"));
            else {
                expected.add(ast(AstKind.PARAMETER));
                expected.add(parameterValue(value.value(), parameters));
            }
            return value.value() == null ? "null" : value.value().getClass().getSimpleName();
        }
        if (plan instanceof OclCypherPlan.SetLiteralPlan) {
            expected.add(ast(AstKind.LIST_LITERAL, AstKind.REDUCE_EXPRESSION));
            expected.add(token("reduce"));
            return "Set";
        }
        if (plan instanceof OclCypherPlan.NotPlan) {
            expected.add(token("NOT"));
            expected.add(token("coalesce"));
            return "not";
        }
        if (plan instanceof OclCypherPlan.IfPlan) {
            expected.add(ast(AstKind.CASE_EXPRESSION));
            expected.add(sequence("CASE", "WHEN"));
            expected.add(token("THEN"));
            expected.add(token("ELSE"));
            expected.add(token("END"));
            return "if";
        }
        if (plan instanceof OclCypherPlan.LetPlan) {
            expected.add(ast(AstKind.LIST_COMPREHENSION, AstKind.FUNCTION_INVOCATION));
            expected.add(token("head"));
            expected.add(token("IN"));
            expected.add(token("|"));
            return "let";
        }
        if (plan instanceof OclCypherPlan.BinaryPlan value) {
            expected.add(ast(AstKind.BINARY_EXPRESSION));
            switch (value.operator().toLowerCase(Locale.ROOT)) {
                case "and" -> { expected.add(token("AND")); expected.add(token("coalesce")); }
                case "or" -> { expected.add(token("OR")); expected.add(token("coalesce")); }
                case "xor" -> { expected.add(token("AND")); expected.add(token("OR")); expected.add(token("NOT")); }
                case "implies" -> { expected.add(token("OR")); expected.add(token("NOT")); }
                case "=" -> {
                    expected.add(ast(AstKind.CASE_EXPRESSION));
                    expected.add(sequence("CASE", "WHEN"));
                    expected.add(token("IS"));
                    expected.add(token("NULL"));
                    expected.add(token("="));
                    expected.add(token("coalesce"));
                }
                case "<>" -> {
                    expected.add(ast(AstKind.CASE_EXPRESSION));
                    expected.add(sequence("CASE", "WHEN"));
                    expected.add(token("IS"));
                    expected.add(token("NULL"));
                    expected.add(token("="));
                    expected.add(token("coalesce"));
                    expected.add(token("NOT"));
                }
                default -> expected.add(token(value.operator()));
            }
            return value.operator();
        }
        if (plan instanceof OclCypherPlan.AttributeAccessPlan value) {
            expected.add(ast(AstKind.COLLECT_EXPRESSION, AstKind.WITH, AstKind.MATCH, AstKind.RETURN,
                    AstKind.PROPERTY, AstKind.PARAMETER));
            expected.add(token("ObjectHasAttribute"));
            expected.add(token("attributeKey"));
            expected.add(token("objectKey"));
            expected.add(parameterSuffix("::attribute::" + value.attribute().owner().name()
                    + "::" + value.attributeName(), parameters));
            return value.attributeName();
        }
        if (plan instanceof OclCypherPlan.NavigationAccessPlan value) {
            navigation(value, expected, parameters);
            expected.add(ast(AstKind.PATTERN_COMPREHENSION, AstKind.RELATIONSHIP_PATTERN));
            return value.navigation().associationName() + ":" + value.navigation().direction();
        }
        if (plan instanceof OclCypherPlan.MethodCallPlan value) {
            return method(value, expected, parameters);
        }
        if (plan instanceof OclCypherPlan.CollectionOperationPlan value) {
            return collection(value, expected);
        }
        if (plan instanceof OclCypherPlan.IteratorOperationPlan value) {
            return iterator(value, expected);
        }
        if (plan instanceof OclCypherPlan.ExistsSubqueryPlan value) {
            expected.add(ast(AstKind.EXISTS_EXPRESSION, AstKind.MATCH, AstKind.RELATIONSHIP_PATTERN));
            expected.add(sequence("EXISTS", "{"));
            navigation(value.match().navigation(), expected, parameters);
            predicateMode(value.match(), expected);
            return "exists:" + value.match().predicateMode();
        }
        if (plan instanceof OclCypherPlan.NotExistsSubqueryPlan value) {
            expected.add(ast(AstKind.EXISTS_EXPRESSION, AstKind.MATCH, AstKind.RELATIONSHIP_PATTERN));
            expected.add(sequence("NOT", "EXISTS", "{"));
            navigation(value.match().navigation(), expected, parameters);
            predicateMode(value.match(), expected);
            return "not-exists:" + value.match().predicateMode();
        }
        if (plan instanceof OclCypherPlan.CountSubqueryComparisonPlan value) {
            expected.add(ast(AstKind.COLLECT_EXPRESSION, AstKind.MATCH, AstKind.RELATIONSHIP_PATTERN,
                    AstKind.BINARY_EXPRESSION));
            expected.add(sequence("COLLECT", "{"));
            expected.add(token("size"));
            expected.add(token(value.operator()));
            expected.add(parameterValue(value.literal(), parameters));
            navigation(value.match().navigation(), expected, parameters);
            predicateMode(value.match(), expected);
            return "count" + value.operator() + value.literal();
        }
        if (plan instanceof OclCypherPlan.NavigationAggregationPlan value) {
            expected.add(ast(AstKind.PATTERN_COMPREHENSION, AstKind.RELATIONSHIP_PATTERN));
            navigation(value.match().navigation(), expected, parameters);
            aggregate(value.operationName(), expected);
            predicateMode(value.match(), expected);
            return value.operationName();
        }
        if (plan instanceof OclCypherPlan.NavigationUniquenessPlan value) {
            expected.add(ast(AstKind.PATTERN_COMPREHENSION, AstKind.RELATIONSHIP_PATTERN,
                    AstKind.REDUCE_EXPRESSION));
            expected.add(token("size"));
            expected.add(token("reduce"));
            navigation(value.match().navigation(), expected, parameters);
            predicateMode(value.match(), expected);
            return "navigation-isUnique";
        }
        throw new AssertionError("No formal generated-tree rule for " + plan.getClass().getName());
    }

    private static String method(OclCypherPlan.MethodCallPlan value,
                                 List<Requirement> expected,
                                 Map<String, Object> parameters) {
        String operation = value.methodName().toLowerCase(Locale.ROOT);
        switch (operation) {
            case "allinstances" -> {
                expected.add(ast(AstKind.COLLECT_EXPRESSION, AstKind.MATCH, AstKind.RETURN));
                expected.add(token("ObjectInstanceOf")); expected.add(token("UmlClass"));
                expected.add(token("classKey")); expected.add(token("DISTINCT"));
                expected.add(parameterSuffix("::class::" + value.source().type().typeName(), parameters));
            }
            case "split" -> function(expected, "split");
            case "isdefined" -> expected.add(value.source().type().isCollection() ? token("size") : sequence("IS", "NOT", "NULL"));
            case "isundefined" -> expected.add(value.source().type().isCollection() ? token("size") : sequence("IS", "NULL"));
            case "concat" -> expected.add(token("+"));
            case "substring" -> function(expected, "substring");
            case "tolower" -> function(expected, "toLower");
            case "toupper" -> function(expected, "toUpper");
            case "trim" -> function(expected, "trim");
            case "tointeger" -> function(expected, "toInteger");
            case "toreal" -> function(expected, "toFloat");
            case "tostring" -> function(expected, "toString");
            case "ocliskindof" -> {
                expected.add(ast(AstKind.EXISTS_EXPRESSION, AstKind.WITH, AstKind.MATCH));
                expected.add(token("objectKey")); expected.add(token("ObjectInstanceOf"));
                expected.add(token("UmlClass")); expected.add(token("classKey"));
            }
            case "oclastype" -> {
                expected.add(ast(AstKind.COLLECT_EXPRESSION, AstKind.WITH, AstKind.MATCH, AstKind.RETURN));
                expected.add(token("objectKey")); expected.add(token("ObjectInstanceOf"));
                expected.add(token("UmlClass")); expected.add(token("classKey"));
            }
            default -> throw new AssertionError("No formal method lowering rule for " + value.methodName());
        }
        return value.methodName();
    }

    private static String collection(OclCypherPlan.CollectionOperationPlan value, List<Requirement> expected) {
        String operation = value.operationName().toLowerCase(Locale.ROOT);
        switch (operation) {
            case "size", "isempty", "notempty" -> function(expected, "size");
            case "count" -> { function(expected, "size"); expected.add(ast(AstKind.LIST_COMPREHENSION)); }
            case "sum" -> aggregate("sum", expected);
            case "min", "max" -> aggregate(operation, expected);
            case "includes" -> function(expected, "any");
            case "excludes" -> function(expected, "none");
            case "includesall" -> { function(expected, "all"); function(expected, "any"); }
            case "excludesall" -> { function(expected, "none"); function(expected, "any"); }
            case "including", "append", "prepend", "union" -> expected.add(token("+"));
            case "excluding" -> { expected.add(ast(AstKind.LIST_COMPREHENSION)); expected.add(token("NOT")); }
            case "asbag" -> { /* identity lowering */ }
            case "asset", "asorderedset" -> { expected.add(ast(AstKind.REDUCE_EXPRESSION)); expected.add(token("reduce")); }
            case "flatten" -> { expected.add(ast(AstKind.REDUCE_EXPRESSION, AstKind.CASE_EXPRESSION)); expected.add(token("reduce")); }
            case "intersection" -> { function(expected, "any"); expected.add(ast(AstKind.LIST_COMPREHENSION)); }
            case "first" -> function(expected, "head");
            case "last" -> { function(expected, "size"); expected.add(ast(AstKind.INDEX_EXPRESSION)); }
            case "at", "subsequence" -> expected.add(ast(AstKind.INDEX_EXPRESSION));
            default -> throw new AssertionError("No formal collection lowering rule for " + value.operationName());
        }
        return value.operationName();
    }

    private static String iterator(OclCypherPlan.IteratorOperationPlan value, List<Requirement> expected) {
        String operation = value.operationName().toLowerCase(Locale.ROOT);
        switch (operation) {
            case "select" -> { expected.add(ast(AstKind.LIST_COMPREHENSION)); expected.add(token("WHERE")); }
            case "reject" -> { expected.add(ast(AstKind.LIST_COMPREHENSION)); expected.add(token("WHERE")); expected.add(token("NOT")); }
            case "exists" -> function(expected, "any");
            case "forall" -> function(expected, "all");
            case "one" -> function(expected, "single");
            case "any" -> { function(expected, "head"); expected.add(ast(AstKind.LIST_COMPREHENSION)); }
            case "collect" -> expected.add(ast(AstKind.LIST_COMPREHENSION));
            case "isunique" -> { function(expected, "size"); expected.add(ast(AstKind.REDUCE_EXPRESSION)); }
            case "sortedby" -> { expected.add(ast(AstKind.UNWIND, AstKind.WITH, AstKind.RETURN)); expected.add(token("ORDER")); expected.add(token("BY")); }
            default -> throw new AssertionError("No formal iterator lowering rule for " + value.operationName());
        }
        return value.operationName();
    }

    private static void aggregate(String operation, List<Requirement> expected) {
        expected.add(ast(AstKind.REDUCE_EXPRESSION));
        expected.add(token("reduce"));
        if (!"sum".equalsIgnoreCase(operation)) expected.add(ast(AstKind.CASE_EXPRESSION));
    }

    private static void function(List<Requirement> expected, String name) {
        expected.add(ast(AstKind.FUNCTION_INVOCATION));
        expected.add(token(name));
    }

    private static void navigation(OclCypherPlan.NavigationAccessPlan value,
                                   List<Requirement> expected,
                                   Map<String, Object> parameters) {
        expected.add(token("associationKey"));
        expected.add(token("sourceRole"));
        expected.add(token("targetRole"));
        expected.add(parameterSuffix("::association::" + value.navigation().associationName(), parameters));
        switch (value.navigation().direction()) {
            case OUTGOING -> expected.add(token("->"));
            case INCOMING -> expected.add(token("<-"));
            case UNDIRECTED -> expected.add(noToken("->", "<-"));
        }
        if (!value.qualifiers().isEmpty()) {
            expected.add(token(switch (value.navigation().direction()) {
                case INCOMING -> "targetQualifiers";
                case OUTGOING, UNDIRECTED -> "sourceQualifiers";
            }));
        }
    }

    private static void predicateMode(OclCypherPlan.NavigationMatchPlan match, List<Requirement> expected) {
        if (match.predicateMode() != OclCypherPlan.PredicateMode.NONE) expected.add(token("WHERE"));
        if (match.predicateMode() == OclCypherPlan.PredicateMode.NEGATED) expected.add(token("NOT"));
    }

    private static List<OclCypherPlan.ExpressionPlan> children(OclCypherPlan.ExpressionPlan plan) {
        if (plan instanceof OclCypherPlan.SetLiteralPlan value) return value.elements();
        if (plan instanceof OclCypherPlan.NotPlan value) return List.of(value.expression());
        if (plan instanceof OclCypherPlan.IfPlan value) return List.of(value.condition(), value.thenBranch(), value.elseBranch());
        if (plan instanceof OclCypherPlan.LetPlan value) return List.of(value.value(), value.body());
        if (plan instanceof OclCypherPlan.BinaryPlan value) return List.of(value.left(), value.right());
        if (plan instanceof OclCypherPlan.AttributeAccessPlan value) return List.of(value.source());
        if (plan instanceof OclCypherPlan.NavigationAccessPlan value) return concat(List.of(value.source()), value.qualifiers());
        if (plan instanceof OclCypherPlan.MethodCallPlan value) return concat(List.of(value.source()), value.arguments());
        if (plan instanceof OclCypherPlan.CollectionOperationPlan value) return concat(List.of(value.source()), value.arguments());
        if (plan instanceof OclCypherPlan.IteratorOperationPlan value) return List.of(value.source(), value.body());
        if (plan instanceof OclCypherPlan.ExistsSubqueryPlan value) return matchChildren(value.match());
        if (plan instanceof OclCypherPlan.NotExistsSubqueryPlan value) return matchChildren(value.match());
        if (plan instanceof OclCypherPlan.CountSubqueryComparisonPlan value) return matchChildren(value.match());
        if (plan instanceof OclCypherPlan.NavigationAggregationPlan value)
            return concat(matchChildren(value.match()), List.of(value.projection()));
        if (plan instanceof OclCypherPlan.NavigationUniquenessPlan value)
            return concat(matchChildren(value.match()), List.of(value.projection()));
        return List.of();
    }

    private static List<OclCypherPlan.ExpressionPlan> matchChildren(OclCypherPlan.NavigationMatchPlan match) {
        List<OclCypherPlan.ExpressionPlan> result = new ArrayList<>();
        result.add(match.owner());
        result.add(match.navigation());
        if (match.predicate() != null) result.add(match.predicate());
        return List.copyOf(result);
    }

    private static List<OclCypherPlan.ExpressionPlan> concat(List<OclCypherPlan.ExpressionPlan> first,
                                                              List<OclCypherPlan.ExpressionPlan> second) {
        List<OclCypherPlan.ExpressionPlan> result = new ArrayList<>(first);
        result.addAll(second);
        return List.copyOf(result);
    }

    private static void require(ObservedTree observed, Object owner, String discriminator,
                                Requirement... requirements) {
        for (Requirement requirement : requirements)
            assertTrue(requirement.matches(observed), () -> owner.getClass().getSimpleName() + "[" + discriminator
                    + "] lacks " + requirement.description() + " in " + observed.normalized());
    }

    private static Requirement ast(AstKind... kinds) {
        Set<AstKind> required = Set.of(kinds);
        return requirement("independent + Neo4j AST " + required,
                observed -> required.stream().allMatch(observed::hasAstKind));
    }

    private static Requirement token(String value) {
        return requirement("token " + value, observed -> observed.hasToken(value));
    }

    private static Requirement noToken(String... values) {
        return requirement("no token " + List.of(values), observed -> Arrays.stream(values).noneMatch(observed::hasToken));
    }

    private static Requirement sequence(String... values) {
        return requirement("sequence " + List.of(values), observed -> observed.hasSequence(values));
    }

    private static Requirement parameterValue(Object value, Map<String, Object> parameters) {
        return requirement("parameter value " + value, observed -> parameters.containsValue(value)
                && parameters.entrySet().stream().anyMatch(entry -> java.util.Objects.equals(value, entry.getValue())
                && observed.hasToken("$" + entry.getKey())));
    }

    private static Requirement parameterSuffix(String suffix, Map<String, Object> parameters) {
        return requirement("canonical parameter suffix " + suffix, observed -> parameters.entrySet().stream()
                .filter(entry -> entry.getValue() instanceof String text && text.endsWith(suffix))
                .anyMatch(entry -> observed.hasToken("$" + entry.getKey())));
    }

    private static Requirement requirement(String description, java.util.function.Predicate<ObservedTree> predicate) {
        return new Requirement(description, predicate);
    }

    record AgreementNode(String constructor, String discriminator, List<String> expectedAst,
                         String normalizedGeneratedText, List<AgreementNode> children) {
    }

    private record Requirement(String description, java.util.function.Predicate<ObservedTree> predicate) {
        boolean matches(ObservedTree observed) { return predicate.test(observed); }
    }

    private enum AstKind {
        SINGLE_QUERY("SingleQuery"),
        MATCH("Match"),
        WITH("With"),
        UNWIND("Unwind"),
        RETURN("Return"),
        CALL_SUBQUERY("SubqueryCall", "ScopeClauseSubqueryCall", "ImportingWithSubqueryCall"),
        EXISTS_EXPRESSION("ExistsExpression"),
        COUNT_EXPRESSION("CountExpression"),
        COLLECT_EXPRESSION("CollectExpression"),
        CASE_EXPRESSION("CaseExpression"),
        REDUCE_EXPRESSION("ReduceExpression"),
        LIST_COMPREHENSION("ListComprehension"),
        PATTERN_COMPREHENSION("PatternComprehension"),
        LIST_LITERAL("ListLiteral"),
        FUNCTION_INVOCATION("FunctionInvocation", "AnyIterablePredicate", "AllIterablePredicate",
                "NoneIterablePredicate", "SingleIterablePredicate"),
        PROPERTY("Property"),
        PARAMETER("Parameter", "ExplicitParameter", "AutoExtractedParameter"),
        RELATIONSHIP_PATTERN("RelationshipPattern"),
        BINARY_EXPRESSION("BinaryOperatorExpression", "Equals", "NotEquals", "LessThan",
                "LessThanOrEqual", "GreaterThan", "GreaterThanOrEqual", "Add", "Subtract",
                "Multiply", "Divide", "And", "Ands", "Or", "Ors"),
        INDEX_EXPRESSION("ContainerIndex", "ListSlice");

        private final String[] neo4jTypes;

        AstKind(String... neo4jTypes) {
            this.neo4jTypes = neo4jTypes;
        }

        boolean appearsIn(Neo4jCypherAstBridge.Observation observation) {
            return observation.hasAnyType(neo4jTypes);
        }
    }

    private record ObservedTree(GeneratedCypherSyntaxTree.Document document,
                                List<String> tokens,
                                Set<AstKind> astKinds,
                                Neo4jCypherAstBridge.Observation neo4jAst,
                                String normalized) {
        static ObservedTree parse(String cypher) {
            GeneratedCypherSyntaxTree.Document document = GeneratedCypherSyntaxTree.parse(cypher);
            List<String> tokens = new ArrayList<>();
            flatten(document.nodes(), tokens);
            Set<AstKind> kinds = infer(document.nodes(), tokens);
            Neo4jCypherAstBridge.Observation neo4jAst = Neo4jCypherAstBridge.parse(cypher);
            return new ObservedTree(document, List.copyOf(tokens), Set.copyOf(kinds), neo4jAst,
                    GeneratedCypherSyntaxTree.render(document));
        }

        boolean hasAstKind(AstKind kind) {
            return astKinds.contains(kind) && kind.appearsIn(neo4jAst);
        }

        boolean hasToken(String value) {
            return tokens.stream().anyMatch(token -> token.equalsIgnoreCase(value));
        }

        boolean hasSequence(String... values) {
            for (int start = 0; start <= tokens.size() - values.length; start++) {
                boolean matches = true;
                for (int offset = 0; offset < values.length; offset++)
                    if (!tokens.get(start + offset).equalsIgnoreCase(values[offset])) { matches = false; break; }
                if (matches) return true;
            }
            return false;
        }

        private static Set<AstKind> infer(List<GeneratedCypherSyntaxTree.Node> nodes, List<String> tokens) {
            Set<AstKind> result = new LinkedHashSet<>();
            result.add(AstKind.SINGLE_QUERY);
            if (contains(tokens, "MATCH")) result.add(AstKind.MATCH);
            if (contains(tokens, "WITH")) result.add(AstKind.WITH);
            if (contains(tokens, "UNWIND")) result.add(AstKind.UNWIND);
            if (contains(tokens, "RETURN")) result.add(AstKind.RETURN);
            if (contains(tokens, "CALL") && contains(tokens, "{")) result.add(AstKind.CALL_SUBQUERY);
            if (sequence(tokens, "EXISTS", "{")) result.add(AstKind.EXISTS_EXPRESSION);
            if (sequence(tokens, "COUNT", "{")) result.add(AstKind.COUNT_EXPRESSION);
            if (sequence(tokens, "COLLECT", "{")) result.add(AstKind.COLLECT_EXPRESSION);
            if (contains(tokens, "CASE") && contains(tokens, "WHEN")) result.add(AstKind.CASE_EXPRESSION);
            if (contains(tokens, "reduce") && contains(tokens, "|") && contains(tokens, "IN")) result.add(AstKind.REDUCE_EXPRESSION);
            if (listComprehension(nodes)) result.add(AstKind.LIST_COMPREHENSION);
            if (patternComprehension(nodes)) result.add(AstKind.PATTERN_COMPREHENSION);
            if (listLiteral(nodes)) result.add(AstKind.LIST_LITERAL);
            if (functionInvocation(nodes)) result.add(AstKind.FUNCTION_INVOCATION);
            if (contains(tokens, ".")) result.add(AstKind.PROPERTY);
            if (tokens.stream().anyMatch(token -> token.startsWith("$"))) result.add(AstKind.PARAMETER);
            if (contains(tokens, "->") || contains(tokens, "<-")) result.add(AstKind.RELATIONSHIP_PATTERN);
            if (tokens.stream().anyMatch(Set.of("=", "<>", "<", "<=", ">", ">=", "+", "-", "*", "/",
                    "AND", "OR")::contains)) result.add(AstKind.BINARY_EXPRESSION);
            if (indexExpression(nodes)) result.add(AstKind.INDEX_EXPRESSION);
            return result;
        }

        private static void flatten(List<GeneratedCypherSyntaxTree.Node> nodes, List<String> target) {
            for (GeneratedCypherSyntaxTree.Node node : nodes) {
                if (node instanceof GeneratedCypherSyntaxTree.Atom atom) target.add(atom.text());
                else if (node instanceof GeneratedCypherSyntaxTree.Group group) {
                    target.add(group.open()); flatten(group.nodes(), target); target.add(group.close());
                }
            }
        }

        private static boolean listComprehension(List<GeneratedCypherSyntaxTree.Node> nodes) {
            for (GeneratedCypherSyntaxTree.Node node : nodes) {
                if (node instanceof GeneratedCypherSyntaxTree.Group group) {
                    List<String> inner = new ArrayList<>(); flatten(group.nodes(), inner);
                    if ("[".equals(group.open()) && contains(inner, "IN")
                            && (contains(inner, "|") || contains(inner, "WHERE"))) return true;
                    if (listComprehension(group.nodes())) return true;
                }
            }
            return false;
        }

        private static boolean listLiteral(List<GeneratedCypherSyntaxTree.Node> nodes) {
            for (GeneratedCypherSyntaxTree.Node node : nodes) {
                if (node instanceof GeneratedCypherSyntaxTree.Group group) {
                    List<String> inner = new ArrayList<>(); flatten(group.nodes(), inner);
                    if ("[".equals(group.open()) && !(contains(inner, "IN")
                            && (contains(inner, "|") || contains(inner, "WHERE")))) return true;
                    if (listLiteral(group.nodes())) return true;
                }
            }
            return false;
        }

        private static boolean patternComprehension(List<GeneratedCypherSyntaxTree.Node> nodes) {
            for (GeneratedCypherSyntaxTree.Node node : nodes) {
                if (node instanceof GeneratedCypherSyntaxTree.Group group) {
                    List<String> inner = new ArrayList<>(); flatten(group.nodes(), inner);
                    if ("[".equals(group.open()) && contains(inner, "|")
                            && (contains(inner, "->") || contains(inner, "<-"))) return true;
                    if (patternComprehension(group.nodes())) return true;
                }
            }
            return false;
        }

        private static boolean functionInvocation(List<GeneratedCypherSyntaxTree.Node> nodes) {
            for (int index = 1; index < nodes.size(); index++) {
                if (nodes.get(index) instanceof GeneratedCypherSyntaxTree.Group group && "(".equals(group.open())
                        && nodes.get(index - 1) instanceof GeneratedCypherSyntaxTree.Atom atom
                        && !Set.of("MATCH", "OPTIONAL", "RETURN", "WITH", "UNWIND", "CALL").contains(
                        atom.text().toUpperCase(Locale.ROOT))) return true;
            }
            for (GeneratedCypherSyntaxTree.Node node : nodes)
                if (node instanceof GeneratedCypherSyntaxTree.Group group && functionInvocation(group.nodes())) return true;
            return false;
        }

        private static boolean indexExpression(List<GeneratedCypherSyntaxTree.Node> nodes) {
            for (int index = 1; index < nodes.size(); index++) {
                if (nodes.get(index) instanceof GeneratedCypherSyntaxTree.Group group && "[".equals(group.open())
                        && !(nodes.get(index - 1) instanceof GeneratedCypherSyntaxTree.Atom atom
                        && "IN".equalsIgnoreCase(atom.text()))) return true;
            }
            for (GeneratedCypherSyntaxTree.Node node : nodes)
                if (node instanceof GeneratedCypherSyntaxTree.Group group && indexExpression(group.nodes())) return true;
            return false;
        }

        private static boolean contains(List<String> values, String expected) {
            return values.stream().anyMatch(value -> value.equalsIgnoreCase(expected));
        }

        private static boolean sequence(List<String> values, String... expected) {
            for (int start = 0; start <= values.size() - expected.length; start++) {
                boolean matches = true;
                for (int offset = 0; offset < expected.length; offset++)
                    if (!values.get(start + offset).equalsIgnoreCase(expected[offset])) { matches = false; break; }
                if (matches) return true;
            }
            return false;
        }
    }
}
