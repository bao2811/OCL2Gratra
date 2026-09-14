package org.uet.dse.neo4jtgg.ocl.ir;

import org.uet.dse.neo4jtgg.ocl.OclTypeBinding;
import org.uet.dse.neo4jtgg.ocl.OclBottomToken;
import org.uet.dse.neo4jtgg.ocl.diagnostic.OclCodedUnsupportedOperationException;
import org.uet.dse.neo4jtgg.ocl.diagnostic.OclDiagnosticCode;
import org.uet.dse.neo4j.encoding.CanonicalGraphEncoding;
import org.uet.dse.neo4j.encoding.CanonicalGraphVocabulary;
import org.tzi.use.uml.ocl.type.CollectionType;
import org.tzi.use.uml.ocl.type.Type;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Model-to-text transformation from the abstract Cypher Query Model to Cypher
 * concrete syntax.
 *
 * <p>This renderer should not rediscover OCL semantics. Semantic decisions are
 * made by the binder, IR builder, optimizer, and planner. This class lowers
 * {@link OclCypherPlan} query-shape elements to executable Cypher text and a
 * parameter map for the graph schema used by the plugin.</p>
 *
 * <pre>
 * T_Text : M_CypherQuery -> CypherText
 * </pre>
 */
public class OclCypherRenderer {
    private final String modelName;
    private final RawCypherParser rawCypherParser = new RawCypherParser();
    private final RawCypherRenderer rawCypherRenderer = new RawCypherRenderer();

    public OclCypherRenderer() {
        this(null);
    }

    public OclCypherRenderer(String modelName) {
        this.modelName = modelName;
    }

    public RenderedInvariant renderInvariant(OclCypherPlan.InvariantPlan invariantPlan) {
        if (invariantPlan.violationPolicy() != OclCypherPlan.ViolationPolicy.NOT_VALIDATION_TRUE) {
            throw new IllegalArgumentException("Unsupported invariant violation policy: "
                    + invariantPlan.violationPolicy());
        }
        if (invariantPlan.evaluationPolicy()
                != OclCypherPlan.EvaluationPolicy.MATERIALIZE_REUSED_EXPRESSIONS) {
            throw new IllegalArgumentException("Unsupported CQM evaluation policy: "
                    + invariantPlan.evaluationPolicy());
        }
        RenderState state = new RenderState(invariantPlan.graphBinding());
        String selfAlias = state.enterVariable("self", OclTypeBinding.node(invariantPlan.contextClassName()));
        String classAlias = state.reserveAlias("cls");
        RenderedExpression predicate = renderExpression(invariantPlan.predicate(), state);
        OclCypherPlan.GraphContextBinding binding = invariantPlan.graphBinding();
        String classParam = state.newParam(binding == null
                ? classKey(invariantPlan.contextClassName(), state)
                : binding.contextClassKey());
        String objectLabel = binding == null ? "Object" : binding.objectLabel();
        String classLabel = binding == null ? "UmlClass" : binding.classLabel();
        String conformanceRelationship = binding == null
                ? CanonicalGraphVocabulary.OBJECT_INSTANCE_OF
                : binding.conformanceRelationship();
        String identityProperty = binding == null ? "use_id" : binding.identityProperty();
        String cypher = "MATCH " + modelScopedNodePattern(selfAlias, objectLabel, state)
                + "-[:" + conformanceRelationship + "]->"
                + modelScopedKeyedNodePattern(classAlias, classLabel, "classKey", classParam, state) + "\n" +
                "WHERE " + OclValidationSemantics.violationPredicate(predicate.cypher()) + "\n" +
                "RETURN DISTINCT " + selfAlias + "." + identityProperty + " AS useId";
        Map<String, Object> parameters = state.parameters();
        OclBottomToken.requireWellFormedGeneratedParameters(parameters);
        RawCypherAst.ProductionQuery rawAst = rawCypherParser.parse(cypher);
        return new RenderedInvariant(rawCypherRenderer.render(rawAst), parameters, rawAst);
    }

    public RenderedTopLevelExpression renderTopLevelExpression(OclCypherPlan.ExpressionPlan expressionPlan) {
        RenderState state = new RenderState();
        RenderedExpression expression = renderExpression(expressionPlan, state);
        Map<String, Object> parameters = state.parameters();
        OclBottomToken.requireWellFormedGeneratedParameters(parameters);
        String cypher = "RETURN " + expression.cypher() + " AS value";
        RawCypherAst.ProductionQuery rawAst = rawCypherParser.parse(cypher);
        return new RenderedTopLevelExpression(rawCypherRenderer.render(rawAst), parameters, rawAst);
    }

    private RenderedExpression renderExpression(OclCypherPlan.ExpressionPlan expression, RenderState state) {
        if (expression instanceof OclCypherPlan.VariablePlan variable) {
            String boundExpression = state.lookupExpression(variable.name());
            if (boundExpression != null) {
                return new RenderedExpression(boundExpression, variable.type());
            }
            if (state.hasVariable(variable.name())) {
                return new RenderedExpression(state.lookupVariableAlias(variable.name()), variable.type());
            }
            return new RenderedExpression("$" + variable.name(), variable.type());
        }
        if (expression instanceof OclCypherPlan.LiteralPlan literal) {
            if (literal.value() == null) {
                return new RenderedExpression("null", literal.type());
            }
            return new RenderedExpression("$" + state.newParam(literal.value()), literal.type());
        }
        if (expression instanceof OclCypherPlan.NotPlan not) {
            RenderedExpression inner = renderExpression(not.expression(), state);
            return new RenderedExpression(OclValidationSemantics.not(inner.cypher()), not.type());
        }
        if (expression instanceof OclCypherPlan.SetLiteralPlan setLiteral) {
            List<String> renderedElements = setLiteral.elements().stream()
                    .map(element -> renderSetValue(renderExpression(element, state).cypher(), state))
                    .toList();
            return new RenderedExpression(
                    renderUniqueCollection("[" + String.join(", ", renderedElements) + "]", state),
                    setLiteral.type());
        }
        if (expression instanceof OclCypherPlan.IfPlan ifPlan) {
            RenderedExpression condition = renderExpression(ifPlan.condition(), state);
            RenderedExpression thenBranch = renderExpression(ifPlan.thenBranch(), state);
            RenderedExpression elseBranch = renderExpression(ifPlan.elseBranch(), state);
            // OCL conditionals have three semantic cases.  A bottom condition
            // is not false and must not select the else branch; the complete
            // conditional evaluates to bottom.  Materialize a compound
            // condition before checking it twice so graph navigation is not
            // duplicated by the lowering.
            String rendered = materializeOnce(condition.cypher(), "ifCondition", state, value ->
                    "(CASE WHEN " + renderIsBottom(value, state)
                            + " THEN null WHEN " + OclValidationSemantics.validationTruth(value)
                            + " THEN " + thenBranch.cypher()
                            + " ELSE " + elseBranch.cypher() + " END)");
            return new RenderedExpression(rendered, ifPlan.type());
        }
        if (expression instanceof OclCypherPlan.LetPlan letPlan) {
            RenderedExpression value = renderExpression(letPlan.value(), state);
            String declaredValue = renderDeclaredBinding(
                    value.cypher(), value.type(), letPlan.variableType(), state);
            String letAlias = state.newVariableAlias("_let");
            state.enterExpressionBinding(letPlan.variableName(), letAlias);
            RenderedExpression body = renderExpression(letPlan.body(), state);
            state.exitExpressionBinding();
            return new RenderedExpression(
                    "head([" + letAlias + " IN [(" + declaredValue + ")] | " + body.cypher() + "])",
                    letPlan.type());
        }
        if (expression instanceof OclCypherPlan.BinaryPlan binary) {
            RenderedExpression left = renderExpression(binary.left(), state);
            RenderedExpression right = renderExpression(binary.right(), state);
            String operator = switch (binary.operator()) {
                case "=" -> "=";
                case "<>" -> "<>";
                case "and" -> "AND";
                case "or" -> "OR";
                case "xor" -> "XOR";
                case "implies" -> "IMPLIES";
                case ">", "<", ">=", "<=", "+", "-", "*", "/" -> binary.operator();
                default -> throw new OclCodedUnsupportedOperationException(
                        OclDiagnosticCode.UNSUPPORTED_OPERATOR,
                        "Unsupported operator: " + binary.operator());
            };
            boolean equalityOperator = "=".equals(operator) || "<>".equals(operator);
            boolean extensionalSetEquality = equalityOperator
                    && (binary.left().type().isCollection() || binary.right().type().isCollection())
                    && isCollectionOrVoid(binary.left().type())
                    && isCollectionOrVoid(binary.right().type());
            String rendered = extensionalSetEquality
                    ? renderFiniteSetComparison(operator, left.cypher(), right.cypher(), state)
                    : equalityOperator
                    ? renderSemanticScalarEquality(operator, left.cypher(), right.cypher(), state)
                    : "IMPLIES".equals(operator)
                    ? OclValidationSemantics.implies(left.cypher(), right.cypher())
                    : switch (operator) {
                        case "AND" -> OclValidationSemantics.and(left.cypher(), right.cypher());
                        case "OR" -> OclValidationSemantics.or(left.cypher(), right.cypher());
                        case "XOR" -> OclValidationSemantics.xor(left.cypher(), right.cypher());
                        default -> renderBottomPropagatingBinary(
                                operator, left.cypher(), right.cypher(), state);
                    };
            return new RenderedExpression(rendered, binary.type());
        }
        if (expression instanceof OclCypherPlan.AttributeAccessPlan attributeAccess) {
            RenderedExpression source = renderExpression(attributeAccess.source(), state);
            return renderAttributeAccess(attributeAccess, source, state);
        }
        if (expression instanceof OclCypherPlan.NavigationAccessPlan navigationAccess) {
            RenderedExpression source = renderExpression(navigationAccess.source(), state);
            return renderNavigationAccess(navigationAccess, source, state);
        }
        if (expression instanceof OclCypherPlan.MethodCallPlan methodCall) {
            RenderedExpression source = renderExpression(methodCall.source(), state);
            return renderMethodCall(methodCall, source, state);
        }
        if (expression instanceof OclCypherPlan.CollectionOperationPlan collectionOperation) {
            RenderedExpression source = renderCollectionView(
                    renderExpression(collectionOperation.source(), state),
                    collectionOperation.sourceCollectionType(), state);
            return renderCollectionOperation(collectionOperation, source, state);
        }
        if (expression instanceof OclCypherPlan.IteratorOperationPlan iteratorOperation) {
            if (iteratorOperation.source() instanceof OclCypherPlan.NavigationAccessPlan navigationAccess
                    && !containsNestedIteratorOrSubquery(iteratorOperation.body())) {
                return renderNavigationIterator(iteratorOperation, navigationAccess, state);
            }
            RenderedExpression source = renderCollectionView(
                    renderExpression(iteratorOperation.source(), state),
                    iteratorOperation.sourceCollectionType(), state);
            String iteratorAlias = state.enterVariable(
                    iteratorOperation.iteratorName(), iteratorOperation.iteratorVariableType());
            state.enterExpressionBinding(iteratorOperation.iteratorName(), renderDeclaredBinding(
                    iteratorAlias, iteratorOperation.sourceCollectionType().elementType(),
                    iteratorOperation.iteratorVariableType(), state));
            RenderedExpression body = renderExpression(iteratorOperation.body(), state);
            state.exitExpressionBinding();
            state.exitVariable();
            String predicate = OclValidationSemantics.validationTruth(body.cypher());
            String negatedPredicate = OclValidationSemantics.not(body.cypher());

            String rendered = switch (iteratorOperation.operationName().toLowerCase()) {
                case "select" -> "[" + iteratorAlias + " IN " + source.cypher() + " WHERE " + predicate + "]";
                case "reject" -> "[" + iteratorAlias + " IN " + source.cypher() + " WHERE " + negatedPredicate + "]";
                case "exists" -> "any(" + iteratorAlias + " IN " + source.cypher() + " WHERE " + predicate + ")";
                case "forall" -> "all(" + iteratorAlias + " IN " + source.cypher() + " WHERE " + predicate + ")";
                case "one" -> "single(" + iteratorAlias + " IN " + source.cypher() + " WHERE " + predicate + ")";
                case "any" -> "head([" + iteratorAlias + " IN " + source.cypher() + " WHERE " + predicate + " | " + iteratorAlias + "])";
                case "collect" -> renderCollect(source, iteratorAlias, body.cypher(), state);
                case "isunique" -> renderIteratorIsUnique(iteratorAlias, source.cypher(), body.cypher(), state);
                case "sortedby" -> renderIteratorSortedBy(iteratorAlias, source.cypher(), body.cypher(), state);
                default -> throw new OclCodedUnsupportedOperationException(
                        OclDiagnosticCode.UNSUPPORTED_ITERATOR,
                        "Unsupported iterator: " + iteratorOperation.operationName());
            };
            return new RenderedExpression(rendered, iteratorOperation.type());
        }
        if (expression instanceof OclCypherPlan.ExistsSubqueryPlan existsPlan) {
            return renderNavigationExistence(existsPlan.match(), false, existsPlan.type(), state);
        }
        if (expression instanceof OclCypherPlan.NotExistsSubqueryPlan notExistsPlan) {
            return renderNavigationExistence(notExistsPlan.match(), true, notExistsPlan.type(), state);
        }
        if (expression instanceof OclCypherPlan.CountSubqueryComparisonPlan countComparison) {
            return renderNavigationCountComparison(countComparison, state);
        }
        if (expression instanceof OclCypherPlan.NavigationAggregationPlan navigationAggregation) {
            return renderNavigationAggregation(navigationAggregation, state);
        }
        if (expression instanceof OclCypherPlan.NavigationUniquenessPlan navigationUniqueness) {
            return renderNavigationUniqueness(navigationUniqueness, state);
        }
        throw new OclCodedUnsupportedOperationException(
                OclDiagnosticCode.UNSUPPORTED_IR_EXPRESSION,
                "Unsupported IR expression: " + expression.getClass().getSimpleName());
    }

    /**
     * Neo4j's planner can expand a correlated navigation EXISTS containing a
     * second iterator/subquery into a prohibitively large plan. In that case
     * the outer navigation is materialized once and the ordinary finite-list
     * iterator lowering is used instead. Simple navigation iterators retain
     * their direct EXISTS/NOT EXISTS plan.
     */
    private boolean containsNestedIteratorOrSubquery(OclCypherPlan.ExpressionPlan expression) {
        if (expression instanceof OclCypherPlan.IteratorOperationPlan
                || expression instanceof OclCypherPlan.ExistsSubqueryPlan
                || expression instanceof OclCypherPlan.NotExistsSubqueryPlan
                || expression instanceof OclCypherPlan.CountSubqueryComparisonPlan
                || expression instanceof OclCypherPlan.NavigationAggregationPlan
                || expression instanceof OclCypherPlan.NavigationUniquenessPlan) {
            return true;
        }
        if (expression instanceof OclCypherPlan.SetLiteralPlan setLiteral) {
            return setLiteral.elements().stream().anyMatch(this::containsNestedIteratorOrSubquery);
        }
        if (expression instanceof OclCypherPlan.NotPlan not) {
            return containsNestedIteratorOrSubquery(not.expression());
        }
        if (expression instanceof OclCypherPlan.IfPlan ifPlan) {
            return containsNestedIteratorOrSubquery(ifPlan.condition())
                    || containsNestedIteratorOrSubquery(ifPlan.thenBranch())
                    || containsNestedIteratorOrSubquery(ifPlan.elseBranch());
        }
        if (expression instanceof OclCypherPlan.LetPlan letPlan) {
            return containsNestedIteratorOrSubquery(letPlan.value())
                    || containsNestedIteratorOrSubquery(letPlan.body());
        }
        if (expression instanceof OclCypherPlan.BinaryPlan binary) {
            return containsNestedIteratorOrSubquery(binary.left())
                    || containsNestedIteratorOrSubquery(binary.right());
        }
        if (expression instanceof OclCypherPlan.AttributeAccessPlan attribute) {
            return containsNestedIteratorOrSubquery(attribute.source());
        }
        if (expression instanceof OclCypherPlan.NavigationAccessPlan navigation) {
            return containsNestedIteratorOrSubquery(navigation.source())
                    || navigation.qualifiers().stream().anyMatch(this::containsNestedIteratorOrSubquery);
        }
        if (expression instanceof OclCypherPlan.MethodCallPlan method) {
            return containsNestedIteratorOrSubquery(method.source())
                    || method.arguments().stream().anyMatch(this::containsNestedIteratorOrSubquery);
        }
        if (expression instanceof OclCypherPlan.CollectionOperationPlan operation) {
            return containsNestedIteratorOrSubquery(operation.source())
                    || operation.arguments().stream().anyMatch(this::containsNestedIteratorOrSubquery);
        }
        return false;
    }

    private RenderedExpression renderNavigationIterator(OclCypherPlan.IteratorOperationPlan iteratorOperation,
                                                        OclCypherPlan.NavigationAccessPlan navigationAccess,
                                                        RenderState state) {
        String operation = iteratorOperation.operationName().toLowerCase();
        if (!"exists".equals(operation) && !"forall".equals(operation)) {
            RenderedExpression source = renderCollectionView(
                    renderExpression(iteratorOperation.source(), state),
                    iteratorOperation.sourceCollectionType(), state);
            String iteratorAlias = state.enterVariable(
                    iteratorOperation.iteratorName(), iteratorOperation.iteratorVariableType());
            state.enterExpressionBinding(iteratorOperation.iteratorName(), renderDeclaredBinding(
                    iteratorAlias, iteratorOperation.sourceCollectionType().elementType(),
                    iteratorOperation.iteratorVariableType(), state));
            RenderedExpression body = renderExpression(iteratorOperation.body(), state);
            state.exitExpressionBinding();
            state.exitVariable();
            String predicate = OclValidationSemantics.validationTruth(body.cypher());
            String negatedPredicate = OclValidationSemantics.not(body.cypher());
            String rendered = switch (operation) {
                case "select" -> "[" + iteratorAlias + " IN " + source.cypher() + " WHERE " + predicate + "]";
                case "reject" -> "[" + iteratorAlias + " IN " + source.cypher() + " WHERE " + negatedPredicate + "]";
                case "collect" -> renderCollect(source, iteratorAlias, body.cypher(), state);
                case "any" -> "head([" + iteratorAlias + " IN " + source.cypher() + " WHERE " + predicate + " | " + iteratorAlias + "])";
                case "one" -> "single(" + iteratorAlias + " IN " + source.cypher() + " WHERE " + predicate + ")";
                case "isunique" -> renderIteratorIsUnique(iteratorAlias, source.cypher(), body.cypher(), state);
                case "sortedby" -> renderIteratorSortedBy(iteratorAlias, source.cypher(), body.cypher(), state);
                default -> throw new OclCodedUnsupportedOperationException(
                        OclDiagnosticCode.UNSUPPORTED_ITERATOR,
                        "Unsupported iterator: " + iteratorOperation.operationName());
            };
            return new RenderedExpression(rendered, iteratorOperation.type());
        }

        OclCypherPlan.NavigationMatchPlan matchPlan = OclCypherQueryModel.navigationMatch(
                navigationAccess.source(),
                iteratorOperation.iteratorName(),
                navigationAccess,
                iteratorOperation.body(),
                "forall".equals(operation) ? OclCypherPlan.PredicateMode.NEGATED : OclCypherPlan.PredicateMode.NORMAL,
                navigationAccess.type().elementType());
        return renderNavigationExistence(matchPlan, "forall".equals(operation),
                iteratorOperation.type(), state);
    }

    private RenderedExpression renderNavigationExistence(OclCypherPlan.NavigationMatchPlan matchPlan,
                                                         boolean negated,
                                                         OclTypeBinding resultType,
                                                         RenderState state) {
        RenderedExpression owner = renderExpression(matchPlan.owner(), state);
        List<RenderedExpression> qualifiers = renderQualifierExpressions(
                matchPlan.navigation(), state);
        String targetAlias = state.enterVariable(matchPlan.targetAlias(), matchPlan.targetType());
        String clause = renderNavigationMatch(matchPlan, owner, qualifiers, targetAlias, state)
                + renderPredicateClause(matchPlan, state);
        state.exitVariable();
        return new RenderedExpression((negated ? "NOT EXISTS { " : "EXISTS { ") + clause + " }", resultType);
    }

    /** Applies the canonical value embedding proved for a declared binder type. */
    private String renderDeclaredBinding(String cypher, OclTypeBinding actual,
                                         OclTypeBinding declared, RenderState state) {
        if (actual.equals(declared)
                || actual.isNode() && declared.isNode()
                || "Void".equals(actual.typeName())
                || "OclAny".equals(declared.typeName())) {
            return cypher;
        }
        if (!actual.isCollection() && !declared.isCollection()
                && "Integer".equals(actual.typeName()) && "Real".equals(declared.typeName())) {
            return "toFloat(" + cypher + ")";
        }
        if (actual.isCollection() && declared.isCollection()) {
            String itemAlias = state.newVariableAlias("_typed");
            String convertedItem = renderDeclaredBinding(
                    itemAlias, actual.elementType(), declared.elementType(), state);
            String converted = "[" + itemAlias + " IN " + cypher + " | " + convertedItem + "]";
            return renderUniqueCollection(converted, state);
        }
        return cypher;
    }

    private RenderedExpression renderMethodCall(OclCypherPlan.MethodCallPlan methodCall, RenderedExpression source, RenderState state) {
        if ("allInstances".equalsIgnoreCase(methodCall.methodName())) {
            String classParam = state.newParam(classKey(source.type().typeName(), state));
            String objectAlias = state.newVariableAlias("obj");
            String classAlias = state.newVariableAlias("cls");
            String cypher = "COLLECT { MATCH " + modelScopedNodePattern(objectAlias, "Object", state)
                    + "-[:" + CanonicalGraphVocabulary.OBJECT_INSTANCE_OF + "]->"
                    + modelScopedKeyedNodePattern(
                            classAlias, "UmlClass", "classKey", classParam, state)
                    + " RETURN DISTINCT " + objectAlias + " }";
            return new RenderedExpression(cypher, methodCall.type());
        }
        if ("split".equalsIgnoreCase(methodCall.methodName())) {
            if (methodCall.arguments().size() != 1) {
                throw new OclCodedUnsupportedOperationException(
                        OclDiagnosticCode.INVALID_METHOD_ARGUMENT,
                        "split() requires a single delimiter argument.");
            }
            RenderedExpression delimiter = renderExpression(methodCall.arguments().get(0), state);
            return new RenderedExpression("split(" + source.cypher() + ", " + delimiter.cypher() + ")", methodCall.type());
        }
        if ("isDefined".equalsIgnoreCase(methodCall.methodName())) {
            String cypher = source.type().isCollection()
                    ? "size(" + renderFiniteCollectionValue(source.cypher(), state) + ") > 0"
                    : source.cypher() + " IS NOT NULL";
            return new RenderedExpression("(" + cypher + ")", methodCall.type());
        }
        if ("isUndefined".equalsIgnoreCase(methodCall.methodName())) {
            String cypher = source.type().isCollection()
                    ? "size(" + renderFiniteCollectionValue(source.cypher(), state) + ") = 0"
                    : source.cypher() + " IS NULL";
            return new RenderedExpression("(" + cypher + ")", methodCall.type());
        }
        // String operations
        if ("concat".equalsIgnoreCase(methodCall.methodName())) {
            if (methodCall.arguments().size() != 1) {
                throw new OclCodedUnsupportedOperationException(
                        OclDiagnosticCode.INVALID_METHOD_ARGUMENT,
                        "concat() requires a single argument.");
            }
            RenderedExpression arg = renderExpression(methodCall.arguments().get(0), state);
            return new RenderedExpression("(" + source.cypher() + " + " + arg.cypher() + ")", methodCall.type());
        }
        if ("substring".equalsIgnoreCase(methodCall.methodName())) {
            if (methodCall.arguments().size() != 2) {
                throw new OclCodedUnsupportedOperationException(
                        OclDiagnosticCode.INVALID_METHOD_ARGUMENT,
                        "substring() requires lower and upper index arguments.");
            }
            RenderedExpression lower = renderExpression(methodCall.arguments().get(0), state);
            RenderedExpression upper = renderExpression(methodCall.arguments().get(1), state);
            // OCL substring(lower, upper) is 1-based inclusive → Cypher substring(str, start, length) is 0-based
            return new RenderedExpression(
                    "substring(" + source.cypher() + ", (" + lower.cypher() + ") - 1, (" + upper.cypher() + ") - (" + lower.cypher() + ") + 1)",
                    methodCall.type());
        }
        if ("toLower".equalsIgnoreCase(methodCall.methodName())) {
            return new RenderedExpression("toLower(" + source.cypher() + ")", methodCall.type());
        }
        if ("toUpper".equalsIgnoreCase(methodCall.methodName())) {
            return new RenderedExpression("toUpper(" + source.cypher() + ")", methodCall.type());
        }
        if ("trim".equalsIgnoreCase(methodCall.methodName())) {
            return new RenderedExpression("trim(" + source.cypher() + ")", methodCall.type());
        }
        if ("toInteger".equalsIgnoreCase(methodCall.methodName())) {
            return new RenderedExpression("toInteger(" + source.cypher() + ")", methodCall.type());
        }
        if ("toReal".equalsIgnoreCase(methodCall.methodName())) {
            return new RenderedExpression("toFloat(" + source.cypher() + ")", methodCall.type());
        }
        if ("toString".equalsIgnoreCase(methodCall.methodName())) {
            return new RenderedExpression("toString(" + source.cypher() + ")", methodCall.type());
        }
        if ("oclIsTypeOf".equalsIgnoreCase(methodCall.methodName())) {
            throw new OclCodedUnsupportedOperationException(
                    OclDiagnosticCode.OCL_IS_TYPE_OF_OUTSIDE_CERTIFIED_FRAGMENT,
                    "oclIsTypeOf() cannot be rendered by the certified pipeline without a proved "
                            + "direct runtime-class accessor.");
        }
        if ("oclIsKindOf".equalsIgnoreCase(methodCall.methodName())) {
            if (methodCall.arguments().size() != 1) {
                throw new OclCodedUnsupportedOperationException(
                        OclDiagnosticCode.INVALID_METHOD_ARGUMENT,
                        "oclIsKindOf() requires a type argument.");
            }
            RenderedExpression typeArg = renderExpression(methodCall.arguments().get(0), state);
            String classParam = state.newParam(classKey(typeArg.type().typeName(), state));
            return new RenderedExpression(renderGuardedIsKindOfCheck(source.cypher(), classParam, state), methodCall.type());
        }
        if ("oclAsType".equalsIgnoreCase(methodCall.methodName())) {
            if (methodCall.arguments().size() != 1) {
                throw new OclCodedUnsupportedOperationException(
                        OclDiagnosticCode.INVALID_METHOD_ARGUMENT,
                        "oclAsType() requires a type argument.");
            }
            RenderedExpression typeArg = renderExpression(methodCall.arguments().get(0), state);
            String classParam = state.newParam(classKey(typeArg.type().typeName(), state));
            return new RenderedExpression(renderGuardedCast(source.cypher(), classParam, state), methodCall.type());
        }
        throw new OclCodedUnsupportedOperationException(
                OclDiagnosticCode.UNSUPPORTED_METHOD_CALL,
                "Unsupported method call: " + methodCall.methodName());
    }

    private RenderedExpression renderAttributeAccess(OclCypherPlan.AttributeAccessPlan attributeAccess,
                                                     RenderedExpression source,
                                                     RenderState state) {
        String attributeParam = state.newParam(attributeKey(attributeAccess, state));
        if (source.type().isCollection()) {
            String itemAlias = state.newVariableAlias("attrOwner");
            String receiverAlias = state.newVariableAlias("attrRecv");
            RenderedExpression mappedValue = renderAttributeValueAccess(
                    attributeAccess, receiverAlias, attributeParam, state);
            String cypher = "[" + itemAlias + " IN "
                    + renderFiniteCollectionValue(source.cypher(), state) + " | "
                    + renderGuardedEntityValue(itemAlias, receiverAlias, mappedValue.cypher(), state) + "]";
            return new RenderedExpression(cypher, attributeAccess.type());
        }

        String itemAlias = state.newVariableAlias("attrOwner");
        RenderedExpression mappedValue = renderAttributeValueAccess(attributeAccess, itemAlias, attributeParam, state);
        String cypher = renderGuardedEntityValue(source.cypher(), itemAlias, mappedValue.cypher(), state);
        return new RenderedExpression(cypher, attributeAccess.type());
    }

    private RenderedExpression renderAttributeValueAccess(OclCypherPlan.AttributeAccessPlan attributeAccess,
                                                          String sourceAlias,
                                                          String attributeParam,
                                                          RenderState state) {
        if (attributeAccess.type().isNode()) {
            String references = referenceAttributeLookup(sourceAlias, attributeParam, attributeAccess.binding(), state);
            return new RenderedExpression("head(" + references + ")", attributeAccess.type());
        }
        if (attributeAccess.type().isCollection() && attributeAccess.type().elementType().isNode()) {
            return new RenderedExpression(
                    referenceAttributeLookup(sourceAlias, attributeParam, attributeAccess.binding(), state),
                    attributeAccess.type());
        }
        if (attributeAccess.type().isCollection() && attributeAccess.type().elementType().isCollection()) {
            return new RenderedExpression(
                    nestedCollectionAttributeLookup(sourceAlias, attributeParam, attributeAccess.attributeType(),
                            attributeAccess.binding(), state),
                    attributeAccess.type());
        }
        String raw = attributeLookup(sourceAlias, attributeParam, attributeAccess.binding(), state);
        String normalized = attributeAccess.type().isCollection()
                ? normalizeCollectionAttributeValue(raw, attributeAccess.attributeType(), state)
                : normalizeAttributeValue(raw, attributeAccess.attributeType(), state);
        return new RenderedExpression(normalized, attributeAccess.type());
    }

    private RenderedExpression renderNavigationAccess(OclCypherPlan.NavigationAccessPlan navigationAccess,
                                                      RenderedExpression source,
                                                      RenderState state) {
        String ownerAlias = state.newVariableAlias("navOwner");
        String targetAlias = state.newVariableAlias("navTarget");
        String accAlias = state.newVariableAlias("navAcc");
        String ownerSource = renderEntitySourceList(source, state);
        String perOwnerTargets = "[" + renderNavigationPattern(
                ownerAlias, targetAlias, navigationAccess, state) + " | " + targetAlias + "]";
        String flattened = "reduce(" + accAlias + " = [], " + ownerAlias + " IN " + ownerSource
                + " | " + accAlias + " + " + perOwnerTargets + ")";
        String uniqueTargets = renderUniqueCollection(flattened, state);
        String cypher = navigationAccess.type().isCollection() ? uniqueTargets : "head(" + uniqueTargets + ")";
        return new RenderedExpression(cypher, navigationAccess.type());
    }

    private RenderedExpression renderCollectionOperation(OclCypherPlan.CollectionOperationPlan collectionOperation,
                                                         RenderedExpression source, RenderState state) {
        return switch (collectionOperation.operationName()) {
            case "size" -> new RenderedExpression("size(" + source.cypher() + ")", collectionOperation.type());
            case "count" -> {
                if (collectionOperation.arguments().size() != 1) {
                    throw new OclCodedUnsupportedOperationException(
                            OclDiagnosticCode.INVALID_COLLECTION_ARGUMENT,
                            "count() requires a single argument.");
                }
                RenderedExpression candidate = renderExpression(collectionOperation.arguments().get(0), state);
                String alias = state.newVariableAlias("item");
                yield new RenderedExpression("size([" + alias + " IN " + source.cypher() +
                        " WHERE " + renderSetEquality(alias, candidate.cypher(), state) + "])",
                        collectionOperation.type());
            }
            case "isEmpty" -> new RenderedExpression("size(" + source.cypher() + ") = 0", collectionOperation.type());
            case "notEmpty" -> new RenderedExpression("size(" + source.cypher() + ") > 0", collectionOperation.type());
            case "sum" -> {
                requireNoCollectionArguments("sum", collectionOperation.arguments());
                String itemAlias = state.newVariableAlias("item");
                String accAlias = state.newVariableAlias("acc");
                String zero = "Real".equals(collectionOperation.type().typeName()) ? "0.0" : "0";
                yield new RenderedExpression(
                        "reduce(" + accAlias + " = " + zero + ", " + itemAlias + " IN " + source.cypher() +
                                " | " + accAlias + " + " + itemAlias + ")",
                        collectionOperation.type());
            }
            case "min" -> {
                requireNoCollectionArguments("min", collectionOperation.arguments());
                yield new RenderedExpression(renderExtremumCollection("min", source.cypher(), state), collectionOperation.type());
            }
            case "max" -> {
                requireNoCollectionArguments("max", collectionOperation.arguments());
                yield new RenderedExpression(renderExtremumCollection("max", source.cypher(), state), collectionOperation.type());
            }
            case "includes" -> {
                if (collectionOperation.arguments().size() != 1) {
                    throw new OclCodedUnsupportedOperationException(
                            OclDiagnosticCode.INVALID_COLLECTION_ARGUMENT,
                            "includes() requires a single argument.");
                }
                RenderedExpression candidate = renderExpression(collectionOperation.arguments().get(0), state);
                String alias = state.newVariableAlias("item");
                yield new RenderedExpression("any(" + alias + " IN " + source.cypher() +
                        " WHERE " + renderSetEquality(alias, candidate.cypher(), state) + ")",
                        collectionOperation.type());
            }
            case "excludes" -> {
                if (collectionOperation.arguments().size() != 1) {
                    throw new OclCodedUnsupportedOperationException(
                            OclDiagnosticCode.INVALID_COLLECTION_ARGUMENT,
                            "excludes() requires a single argument.");
                }
                RenderedExpression candidate = renderExpression(collectionOperation.arguments().get(0), state);
                String alias = state.newVariableAlias("item");
                yield new RenderedExpression("none(" + alias + " IN " + source.cypher() +
                        " WHERE " + renderSetEquality(alias, candidate.cypher(), state) + ")",
                        collectionOperation.type());
            }
            case "includesAll" -> {
                if (collectionOperation.arguments().size() != 1) {
                    throw new OclCodedUnsupportedOperationException(
                            OclDiagnosticCode.INVALID_COLLECTION_ARGUMENT,
                            "includesAll() requires a single argument.");
                }
                OclCypherPlan.ExpressionPlan argument = collectionOperation.arguments().get(0);
                RenderedExpression candidates = renderCollectionView(
                        renderExpression(argument, state), argument.type(), state);
                String outerAlias = state.newVariableAlias("candidate");
                String innerAlias = state.newVariableAlias("item");
                yield new RenderedExpression("all(" + outerAlias + " IN " + candidates.cypher() +
                        " WHERE any(" + innerAlias + " IN " + source.cypher() +
                        " WHERE " + renderSetEquality(innerAlias, outerAlias, state) + "))",
                        collectionOperation.type());
            }
            case "excludesAll" -> {
                if (collectionOperation.arguments().size() != 1) {
                    throw new OclCodedUnsupportedOperationException(
                            OclDiagnosticCode.INVALID_COLLECTION_ARGUMENT,
                            "excludesAll() requires a single argument.");
                }
                OclCypherPlan.ExpressionPlan argument = collectionOperation.arguments().get(0);
                RenderedExpression candidates = renderCollectionView(
                        renderExpression(argument, state), argument.type(), state);
                String outerAlias = state.newVariableAlias("candidate");
                String innerAlias = state.newVariableAlias("item");
                yield new RenderedExpression("none(" + outerAlias + " IN " + candidates.cypher() +
                        " WHERE any(" + innerAlias + " IN " + source.cypher() +
                        " WHERE " + renderSetEquality(innerAlias, outerAlias, state) + "))",
                        collectionOperation.type());
            }
            case "including" -> {
                if (collectionOperation.arguments().size() != 1) {
                    throw new OclCodedUnsupportedOperationException(
                            OclDiagnosticCode.INVALID_COLLECTION_ARGUMENT,
                            "including() requires a single argument.");
                }
                RenderedExpression candidate = renderExpression(collectionOperation.arguments().get(0), state);
                String combined = "(" + source.cypher() + " + [" + candidate.cypher() + "])";
                String cypher = collectionOperation.type().isUniqueCollection()
                        ? renderUniqueCollection(combined, state)
                        : combined;
                yield new RenderedExpression(cypher, collectionOperation.type());
            }
            case "excluding" -> {
                if (collectionOperation.arguments().size() != 1) {
                    throw new OclCodedUnsupportedOperationException(
                            OclDiagnosticCode.INVALID_COLLECTION_ARGUMENT,
                            "excluding() requires a single argument.");
                }
                RenderedExpression candidate = renderExpression(collectionOperation.arguments().get(0), state);
                String alias = state.newVariableAlias("item");
                yield new RenderedExpression("[" + alias + " IN " + source.cypher() +
                        " WHERE NOT " + renderSetEquality(alias, candidate.cypher(), state) + "]",
                        collectionOperation.type());
            }
            case "append" -> {
                if (collectionOperation.arguments().size() != 1) {
                    throw new OclCodedUnsupportedOperationException(
                            OclDiagnosticCode.INVALID_COLLECTION_ARGUMENT,
                            "append() requires a single argument.");
                }
                if (!collectionOperation.source().type().isOrderedCollection()) {
                    throw new OclCodedUnsupportedOperationException(
                            OclDiagnosticCode.UNORDERED_POSITIONAL_ACCESS,
                            "append() is only supported on ordered collections (Sequence/OrderedSet).");
                }
                RenderedExpression candidate = renderExpression(collectionOperation.arguments().get(0), state);
                String combined = "(" + source.cypher() + " + [" + candidate.cypher() + "])";
                String cypher = collectionOperation.type().isUniqueCollection()
                        ? renderUniqueCollection(combined, state)
                        : combined;
                yield new RenderedExpression(cypher, collectionOperation.type());
            }
            case "prepend" -> {
                if (collectionOperation.arguments().size() != 1) {
                    throw new OclCodedUnsupportedOperationException(
                            OclDiagnosticCode.INVALID_COLLECTION_ARGUMENT,
                            "prepend() requires a single argument.");
                }
                if (!collectionOperation.source().type().isOrderedCollection()) {
                    throw new OclCodedUnsupportedOperationException(
                            OclDiagnosticCode.UNORDERED_POSITIONAL_ACCESS,
                            "prepend() is only supported on ordered collections (Sequence/OrderedSet).");
                }
                RenderedExpression candidate = renderExpression(collectionOperation.arguments().get(0), state);
                String combined = "([" + candidate.cypher() + "] + " + source.cypher() + ")";
                String cypher = collectionOperation.type().isUniqueCollection()
                        ? renderUniqueCollection(combined, state)
                        : combined;
                yield new RenderedExpression(cypher, collectionOperation.type());
            }
            case "union" -> {
                if (collectionOperation.arguments().size() != 1) {
                    throw new OclCodedUnsupportedOperationException(
                            OclDiagnosticCode.INVALID_COLLECTION_ARGUMENT,
                            "union() requires a single argument.");
                }
                OclCypherPlan.ExpressionPlan argument = collectionOperation.arguments().get(0);
                RenderedExpression candidates = renderCollectionView(
                        renderExpression(argument, state), argument.type(), state);
                String combined = "(" + source.cypher() + " + " + candidates.cypher() + ")";
                String cypher = collectionOperation.type().isUniqueCollection()
                        ? renderUniqueCollection(combined, state)
                        : combined;
                yield new RenderedExpression(cypher, collectionOperation.type());
            }
            case "asBag" -> {
                yield new RenderedExpression(source.cypher(), collectionOperation.type());
            }
            case "asSet" -> {
                yield new RenderedExpression(renderUniqueCollection(source.cypher(), state), collectionOperation.type());
            }
            case "asOrderedSet" -> {
                yield new RenderedExpression(renderUniqueCollection(source.cypher(), state), collectionOperation.type());
            }
            case "flatten" -> {
                String itemAlias = state.newVariableAlias("item");
                String accAlias = state.newVariableAlias("acc");
                String flattened = "reduce(" + accAlias + " = [], " + itemAlias + " IN " + source.cypher() +
                        " | " + accAlias + " + " + renderFiniteCollectionValue(itemAlias, state) + ")";
                String cypher = collectionOperation.type().isUniqueCollection()
                        ? renderUniqueCollection(flattened, state)
                        : flattened;
                yield new RenderedExpression(cypher, collectionOperation.type());
            }
            case "intersection" -> {
                if (collectionOperation.arguments().size() != 1) {
                    throw new OclCodedUnsupportedOperationException(
                            OclDiagnosticCode.INVALID_COLLECTION_ARGUMENT,
                            "intersection() requires a single argument.");
                }
                OclCypherPlan.ExpressionPlan argument = collectionOperation.arguments().get(0);
                RenderedExpression candidates = renderCollectionView(
                        renderExpression(argument, state), argument.type(), state);
                String itemAlias = state.newVariableAlias("item");
                String candidateAlias = state.newVariableAlias("candidate");
                String cypher;
                if (collectionOperation.type().isUniqueCollection()) {
                    String filtered = "[" + itemAlias + " IN " + source.cypher() +
                            " WHERE any(" + candidateAlias + " IN " + candidates.cypher() +
                            " WHERE " + renderSetEquality(candidateAlias, itemAlias, state) + ")]";
                    cypher = renderUniqueCollection(filtered, state);
                } else {
                    String accAlias = state.newVariableAlias("acc");
                    String existingAlias = state.newVariableAlias("existing");
                    String itemsExpr = accAlias + ".items";
                    String remainingExpr = accAlias + ".remaining";
                    String matchingIndex = "head([idx IN range(0, size(" + remainingExpr + ") - 1) WHERE " +
                            renderSetEquality(remainingExpr + "[idx]", itemAlias, state) + "])";
                    cypher = "reduce(" + accAlias + " = {items: [], remaining: " + candidates.cypher() + "}, " +
                            itemAlias + " IN " + source.cypher() + " | CASE WHEN any(" + existingAlias + " IN " +
                            remainingExpr + " WHERE " + renderSetEquality(existingAlias, itemAlias, state) + ") THEN " +
                            "{items: " + itemsExpr + " + [" + renderSetValue(itemAlias, state) + "], remaining: " +
                            remainingExpr + "[0.." + matchingIndex + "] + " +
                            remainingExpr + "[" + matchingIndex + " + 1..]} ELSE " + accAlias + " END).items";
                }
                yield new RenderedExpression(cypher, collectionOperation.type());
            }
            case "first" -> new RenderedExpression("head(" + source.cypher() + ")", collectionOperation.type());
            case "last" -> new RenderedExpression(source.cypher() + "[size(" + source.cypher() + ") - 1]", collectionOperation.type());
            case "at" -> {
                if (collectionOperation.arguments().size() != 1) {
                    throw new OclCodedUnsupportedOperationException(
                            OclDiagnosticCode.INVALID_COLLECTION_ARGUMENT,
                            "at() requires an index argument.");
                }
                RenderedExpression index = renderExpression(collectionOperation.arguments().get(0), state);
                yield new RenderedExpression(source.cypher() + "[(" + index.cypher() + ") - 1]", collectionOperation.type());
            }
            case "subSequence" -> {
                if (collectionOperation.arguments().size() != 2) {
                    throw new OclCodedUnsupportedOperationException(
                            OclDiagnosticCode.INVALID_COLLECTION_ARGUMENT,
                            "subSequence() requires start and end index arguments.");
                }
                if (!collectionOperation.source().type().isOrderedCollection()) {
                    throw new OclCodedUnsupportedOperationException(
                            OclDiagnosticCode.UNORDERED_POSITIONAL_ACCESS,
                            "subSequence() is only supported on ordered collections (Sequence/OrderedSet).");
                }
                RenderedExpression start = renderExpression(collectionOperation.arguments().get(0), state);
                RenderedExpression end = renderExpression(collectionOperation.arguments().get(1), state);
                yield new RenderedExpression(
                        source.cypher() + "[(" + start.cypher() + ") - 1..(" + end.cypher() + ") - 1]",
                        collectionOperation.type());
            }
            default -> throw new OclCodedUnsupportedOperationException(
                    OclDiagnosticCode.UNSUPPORTED_COLLECTION_OPERATION,
                    "Unsupported collection operation: " + collectionOperation.operationName());
        };
    }

    private RenderedExpression renderNavigationCountComparison(OclCypherPlan.CountSubqueryComparisonPlan countComparison, RenderState state) {
        RenderedExpression owner = renderExpression(countComparison.match().owner(), state);
        List<RenderedExpression> qualifiers = renderQualifierExpressions(
                countComparison.match().navigation(), state);
        String targetAlias = state.enterVariable(
                countComparison.match().targetAlias(), countComparison.match().targetType());
        String matchClause = renderNavigationMatch(
                countComparison.match(), owner, qualifiers, targetAlias, state);
        String predicateClause = renderPredicateClause(countComparison.match(), state);
        String distinctTargets = "COLLECT { " + matchClause + predicateClause
                + " RETURN DISTINCT " + targetAlias + " }";
        state.exitVariable();

        long literal = countComparison.literal();
        return switch (countComparison.operator()) {
            case ">" -> new RenderedExpression("(size(" + distinctTargets + ") > $" + state.newParam(literal) + ")", countComparison.type());
            case ">=" -> new RenderedExpression("(size(" + distinctTargets + ") >= $" + state.newParam(literal) + ")", countComparison.type());
            case "=" -> new RenderedExpression("(size(" + distinctTargets + ") = $" + state.newParam(literal) + ")", countComparison.type());
            case "<>" -> new RenderedExpression("(size(" + distinctTargets + ") <> $" + state.newParam(literal) + ")", countComparison.type());
            case "<" -> new RenderedExpression("(size(" + distinctTargets + ") < $" + state.newParam(literal) + ")", countComparison.type());
            case "<=" -> new RenderedExpression("(size(" + distinctTargets + ") <= $" + state.newParam(literal) + ")", countComparison.type());
            default -> throw new OclCodedUnsupportedOperationException(
                    OclDiagnosticCode.UNSUPPORTED_COUNT_OPERATOR,
                    "Unsupported count operator: " + countComparison.operator());
        };
    }

    private RenderedExpression renderNavigationAggregation(OclCypherPlan.NavigationAggregationPlan aggregationPlan,
                                                           RenderState state) {
        String projectionList = renderNavigationProjectionList(aggregationPlan.match(), aggregationPlan.projection(), state);
        return switch (aggregationPlan.operationName().toLowerCase()) {
            case "sum" -> {
                String itemAlias = state.newVariableAlias("item");
                String accAlias = state.newVariableAlias("acc");
                String zero = "Real".equals(aggregationPlan.type().typeName()) ? "0.0" : "0";
                yield new RenderedExpression(
                        "reduce(" + accAlias + " = " + zero + ", " + itemAlias + " IN " + projectionList +
                                " | " + accAlias + " + " + itemAlias + ")",
                        aggregationPlan.type());
            }
            case "min" -> new RenderedExpression(renderExtremumCollection("min", projectionList, state), aggregationPlan.type());
            case "max" -> new RenderedExpression(renderExtremumCollection("max", projectionList, state), aggregationPlan.type());
            default -> throw new OclCodedUnsupportedOperationException(
                    OclDiagnosticCode.UNSUPPORTED_COLLECTION_OPERATION,
                    "Unsupported navigation aggregation: " + aggregationPlan.operationName());
        };
    }

    private RenderedExpression renderNavigationUniqueness(OclCypherPlan.NavigationUniquenessPlan uniquenessPlan,
                                                          RenderState state) {
        String projectionList = renderNavigationProjectionList(uniquenessPlan.match(), uniquenessPlan.projection(), state);
        String uniqueProjection = renderUniqueCollection(projectionList, state);
        return new RenderedExpression(
                "size(" + projectionList + ") = size(" + uniqueProjection + ")",
                uniquenessPlan.type());
    }

    private String renderNavigationProjectionList(OclCypherPlan.NavigationMatchPlan matchPlan,
                                                  OclCypherPlan.ExpressionPlan projection,
                                                  RenderState state) {
        RenderedExpression owner = renderExpression(matchPlan.owner(), state);
        List<RenderedExpression> qualifiers = renderQualifierExpressions(
                matchPlan.navigation(), state);
        String ownerAlias = state.newVariableAlias("navOwner");
        String ownerSource = renderEntitySourceList(owner, state);
        String accAlias = state.newVariableAlias("navAggAcc");
        String perOwner = renderSingleOwnerProjection(
                ownerAlias, matchPlan, qualifiers, projection, state);
        return "reduce(" + accAlias + " = [], " + ownerAlias + " IN " + ownerSource + " | " + accAlias + " + " + perOwner + ")";
    }

    private String renderSingleOwnerProjection(String ownerAlias,
                                               OclCypherPlan.NavigationMatchPlan matchPlan,
                                               List<RenderedExpression> qualifiers,
                                               OclCypherPlan.ExpressionPlan projection,
                                               RenderState state) {
        String targetAlias = state.enterVariable(matchPlan.targetAlias(), matchPlan.targetType());
        RenderedExpression projected = renderExpression(projection, state);
        String predicate = renderPredicateTruth(matchPlan, state);
        String targets = renderUniqueCollection(
                "[" + renderNavigationPattern(
                        ownerAlias, targetAlias, matchPlan.navigation(), qualifiers, state)
                        + " | " + targetAlias + "]", state);
        String predicateClause = predicate == null ? "" : " WHERE " + predicate;
        state.exitVariable();
        return "[" + targetAlias + " IN " + targets + predicateClause + " | " + projected.cypher() + "]";
    }

    private String renderNavigationMatch(OclCypherPlan.NavigationMatchPlan matchPlan,
                                         RenderedExpression owner,
                                         List<RenderedExpression> qualifiers,
                                         String targetAlias, RenderState state) {
        String ownerAlias = state.newVariableAlias("navOwner");
        String ownerSource = renderEntitySourceList(owner, state);
        return "UNWIND " + ownerSource + " AS " + ownerAlias + "\nMATCH "
                + renderNavigationPattern(
                        ownerAlias, targetAlias, matchPlan.navigation(), qualifiers, state);
    }

    private String renderNavigationPattern(String sourceAlias, String targetAlias,
                                           OclCypherPlan.NavigationAccessPlan navigationAccess,
                                           RenderState state) {
        return renderNavigationPattern(sourceAlias, targetAlias, navigationAccess,
                renderQualifierExpressions(navigationAccess, state), state);
    }

    private String renderNavigationPattern(String sourceAlias, String targetAlias,
                                           OclCypherPlan.NavigationAccessPlan navigationAccess,
                                           List<RenderedExpression> qualifiers,
                                           RenderState state) {
        org.uet.dse.neo4jtgg.ocl.OclMetamodelIndex.NavigationInfo navigationInfo = navigationAccess.navigation();
        OclCypherPlan.NavigationBinding binding = navigationAccess.binding();
        String associationParam = state.newParam(binding == null
                ? associationKey(navigationInfo.associationName(), state)
                : binding.associationKey());
        String sourceRoleParam = state.newParam(binding == null
                ? navigationInfo.sourceRoleName() : binding.sourceRole());
        String targetRoleParam = state.newParam(binding == null
                ? navigationInfo.targetRoleName() : binding.targetRole());
        String relationshipAlias = state.newRelationshipAlias();
        String qualifierPredicate = renderQualifierPredicate(
                navigationAccess, qualifiers, relationshipAlias, state);
        String relationshipModelPredicate = modelPropertyPredicate(relationshipAlias, state);
        String sourcePattern = modelScopedNodePattern(sourceAlias, "Object", state);
        String targetPattern = modelScopedNodePattern(targetAlias, "Object", state);
        String relationshipPrefix = binding == null ? "Link" : binding.relationshipTypePrefix();
        org.uet.dse.neo4jtgg.ocl.OclMetamodelIndex.NavigationDirection direction = binding == null
                ? navigationInfo.direction() : binding.direction();
        if (navigationInfo.supportsCanonicalNAryNavigation()) {
            if (!qualifiers.isEmpty()) {
                throw new OclCodedUnsupportedOperationException(
                        OclDiagnosticCode.QUALIFIED_ASSOCIATION_UNSUPPORTED,
                        "Qualified n-ary association navigation is outside the production profile.");
            }
            String hubAlias = state.newVariableAlias("linkHub");
            String sourceSpoke = state.newRelationshipAlias();
            String targetSpoke = state.newRelationshipAlias();
            String hubPattern = modelScopedNodePattern(hubAlias, "LinkHub", state);
            return sourcePattern + "-[" + sourceSpoke + "]->" + hubPattern
                    + "<-[" + targetSpoke + "]-" + targetPattern
                    + " WHERE type(" + sourceSpoke + ") STARTS WITH '" + relationshipPrefix + "'"
                    + " AND type(" + targetSpoke + ") STARTS WITH '" + relationshipPrefix + "' "
                    + modelPropertyPredicate(sourceSpoke, state)
                    + modelPropertyPredicate(targetSpoke, state)
                    + "AND " + hubAlias + ".associationKey = $" + associationParam
                    + " AND " + sourceSpoke + ".associationKey = $" + associationParam
                    + " AND " + targetSpoke + ".associationKey = $" + associationParam
                    + " AND " + sourceSpoke + ".linkKey = " + hubAlias + ".linkKey"
                    + " AND " + targetSpoke + ".linkKey = " + hubAlias + ".linkKey"
                    + " AND " + sourceSpoke + ".role = $" + sourceRoleParam
                    + " AND " + targetSpoke + ".role = $" + targetRoleParam;
        }
        return switch (direction) {
            case OUTGOING -> sourcePattern + "-[" + relationshipAlias + "]->" + targetPattern
                    + " WHERE type(" + relationshipAlias + ") STARTS WITH '" + relationshipPrefix + "' " +
                    relationshipModelPredicate +
                    "AND " + relationshipAlias + ".associationKey = $" + associationParam +
                    " AND " + relationshipAlias + ".sourceRole = $" + sourceRoleParam +
                    " AND " + relationshipAlias + ".targetRole = $" + targetRoleParam +
                    qualifierPredicate;
            case INCOMING -> sourcePattern + "<-[" + relationshipAlias + "]-" + targetPattern
                    + " WHERE type(" + relationshipAlias + ") STARTS WITH '" + relationshipPrefix + "' " +
                    relationshipModelPredicate +
                    "AND " + relationshipAlias + ".associationKey = $" + associationParam +
                    " AND " + relationshipAlias + ".sourceRole = $" + targetRoleParam +
                    " AND " + relationshipAlias + ".targetRole = $" + sourceRoleParam +
                    qualifierPredicate;
            case UNDIRECTED -> sourcePattern + "-[" + relationshipAlias + "]-" + targetPattern
                    + " WHERE type(" + relationshipAlias + ") STARTS WITH '" + relationshipPrefix + "' " +
                    relationshipModelPredicate +
                    "AND " + relationshipAlias + ".associationKey = $" + associationParam +
                    " AND ((" + relationshipAlias + ".sourceRole = $" + sourceRoleParam + " AND "
                    + relationshipAlias + ".targetRole = $" + targetRoleParam + ")" +
                    " OR (" + relationshipAlias + ".sourceRole = $" + targetRoleParam + " AND "
                    + relationshipAlias + ".targetRole = $" + sourceRoleParam + "))" +
                    qualifierPredicate;
        };
    }

    private List<RenderedExpression> renderQualifierExpressions(
            OclCypherPlan.NavigationAccessPlan navigationAccess, RenderState state) {
        return navigationAccess.qualifiers().stream()
                .map(qualifier -> renderExpression(qualifier, state))
                .toList();
    }

    private String renderQualifierPredicate(OclCypherPlan.NavigationAccessPlan navigationAccess,
                                            List<RenderedExpression> qualifiers,
                                            String relationshipAlias, RenderState state) {
        if (navigationAccess.qualifiers().isEmpty()) {
            return "";
        }
        if (qualifiers.size() != navigationAccess.qualifiers().size()) {
            throw new IllegalArgumentException("Rendered qualifier arity does not match navigation metadata.");
        }
        OclCypherPlan.NavigationBinding binding = navigationAccess.binding();
        org.uet.dse.neo4jtgg.ocl.OclMetamodelIndex.NavigationDirection direction = binding == null
                ? navigationAccess.navigation().direction() : binding.direction();
        String propertyName = switch (direction) {
            case OUTGOING, UNDIRECTED -> binding == null
                    ? "sourceQualifiers" : binding.sourceQualifierProperty();
            case INCOMING -> binding == null ? "targetQualifiers" : binding.targetQualifierProperty();
        };
        StringBuilder predicate = new StringBuilder();
        for (int i = 0; i < navigationAccess.qualifiers().size(); i++) {
            int qualifierIndex = i;
            OclCypherPlan.ExpressionPlan qualifier = navigationAccess.qualifiers().get(i);
            RenderedExpression rendered = qualifiers.get(i);
            String condition = materializeOnce(rendered.cypher(), "qualifier", state, value -> {
                RenderedExpression boundQualifier = new RenderedExpression(value, qualifier.type());
                return "NOT " + renderIsBottom(value, state) + " AND " + relationshipAlias + "."
                        + propertyName + "[" + qualifierIndex + "] = "
                        + renderSerializedQualifierValue(boundQualifier, qualifier.type(), state);
            });
            predicate.append(" AND ").append(condition);
        }
        return predicate.toString();
    }

    private String renderSerializedQualifierValue(RenderedExpression rendered, OclTypeBinding type,
                                                  RenderState state) {
        if (type.isCollection() || type.isNode() || type.isClassReference()) {
            throw new OclCodedUnsupportedOperationException(
                    OclDiagnosticCode.QUALIFIED_ASSOCIATION_UNSUPPORTED,
                    "Qualified navigation currently supports only scalar qualifier expressions.");
        }
        String escaped = "replace(replace(toString(" + rendered.cypher()
                + "), '%', '%25'), '|', '%7C')";
        if ("String".equals(type.typeName())) {
            return taggedQualifierValue(rendered.cypher(), "v1|S|", escaped, state);
        }
        if ("Integer".equals(type.typeName())) {
            return taggedQualifierValue(rendered.cypher(), "v1|I|",
                    "toString(toInteger(" + rendered.cypher() + "))", state);
        }
        if ("Real".equals(type.typeName())) {
            String realValue = "toFloat(" + rendered.cypher() + ")";
            return taggedQualifierValue(rendered.cypher(), "v1|R|",
                    "CASE WHEN " + realValue + " = 0.0 THEN '0.0' ELSE toString("
                            + realValue + ") END", state);
        }
        if ("Boolean".equals(type.typeName())) {
            return taggedQualifierValue(rendered.cypher(), "v1|B|",
                    "CASE WHEN " + rendered.cypher() + " THEN 'true' ELSE 'false' END", state);
        }
        // Enum and other admitted scalar domains have a distinct tag even when
        // their lexical payload happens to equal a String payload.
        if (!type.isCollection() && !type.isNode() && !type.isClassReference()) {
            return taggedQualifierValue(rendered.cypher(), "v1|E|", escaped, state);
        }
        throw new OclCodedUnsupportedOperationException(
                OclDiagnosticCode.QUALIFIED_ASSOCIATION_UNSUPPORTED,
                "Qualified navigation currently supports only primitive scalar qualifier expressions.");
    }

    private String taggedQualifierValue(String value, String prefix, String payload, RenderState state) {
        return "(CASE WHEN " + renderIsBottom(value, state) + " THEN 'v1|V' ELSE '" + prefix + "' + ("
                + payload + ") END)";
    }

    private String renderPredicateClause(OclCypherPlan.NavigationMatchPlan matchPlan, RenderState state) {
        String predicate = renderPredicateTruth(matchPlan, state);
        return predicate == null ? "" : " AND " + predicate;
    }

    /** The target variable must already be present in the renderer alpha-map. */
    private String renderPredicateTruth(OclCypherPlan.NavigationMatchPlan matchPlan, RenderState state) {
        if (matchPlan.predicateMode() == OclCypherPlan.PredicateMode.NONE || matchPlan.predicate() == null) {
            return null;
        }
        RenderedExpression body = renderExpression(matchPlan.predicate(), state);
        return switch (matchPlan.predicateMode()) {
            case NONE -> null;
            case NORMAL -> OclValidationSemantics.validationTruth(body.cypher());
            case NEGATED -> OclValidationSemantics.not(body.cypher());
        };
    }

    private String normalizeAttributeValue(String raw, org.tzi.use.uml.ocl.type.Type type,
                                           RenderState state) {
        String prefix;
        if (type.isTypeOfInteger()) prefix = "v1|I|";
        else if (type.isTypeOfReal()) prefix = "v1|R|";
        else if (type.isTypeOfBoolean()) prefix = "v1|B|";
        else if (type.isTypeOfString()) prefix = "v1|S|";
        else prefix = "v1|E|";
        return materializeOnce(raw, "rawAttr", state, rawAlias -> {
            String payload = "substring(" + rawAlias + ", " + prefix.length() + ")";
            String decoded = "replace(replace(" + payload + ", '%7C', '|'), '%25', '%')";
            String taggedPayload = "CASE WHEN " + rawAlias + " = 'v1|V' THEN null "
                    + "WHEN " + rawAlias + " STARTS WITH '" + prefix + "' THEN " + decoded + " ELSE null END";
            if (type.isTypeOfInteger()) {
                return "toInteger(" + taggedPayload + ")";
            }
            if (type.isTypeOfReal()) {
                return "toFloat(" + taggedPayload + ")";
            }
            if (type.isTypeOfBoolean()) {
                return "CASE " + taggedPayload + " WHEN 'true' THEN true WHEN 'false' THEN false ELSE null END";
            }
            return taggedPayload;
        });
    }

    private String normalizeCollectionAttributeValue(String raw, Type type, RenderState state) {
        if (!(type instanceof CollectionType collectionType)) {
            throw new OclCodedUnsupportedOperationException(
                    OclDiagnosticCode.COLLECTION_VALUED_ATTRIBUTE_UNSUPPORTED,
                    "Collection-valued attribute metadata is invalid for Cypher rendering.");
        }
        Type elementType = collectionType.elemType();
        if (elementType.isKindOfCollection(Type.VoidHandling.EXCLUDE_VOID)) {
            throw new OclCodedUnsupportedOperationException(
                    OclDiagnosticCode.COLLECTION_VALUED_ATTRIBUTE_UNSUPPORTED,
                    "Nested collection-valued attributes must use the nested collection graph path during Cypher rendering.");
        }
        return materializeOnce(raw, "rawCollectionAttr", state, rawAlias -> {
            String itemAlias = state.newVariableAlias("attrItem");
            String emptyCheck = rawAlias + " IS NULL OR " + rawAlias + " = 'Undefined' OR "
                    + rawAlias + " = 'COLLECTION_EMPTY'";
            String splitExpr = "split(" + rawAlias + ", ' | ')";
            String itemValue = normalizeAttributeValue(itemAlias, elementType, state);
            return "CASE WHEN " + emptyCheck + " THEN [] ELSE [" + itemAlias + " IN "
                    + splitExpr + " | " + itemValue + "] END";
        });
    }

    private String nestedCollectionAttributeLookup(String sourceAlias, String attributeParam, Type type,
                                                   OclCypherPlan.AttributeBinding binding, RenderState state) {
        if (!(type instanceof CollectionType outerCollectionType)
                || !outerCollectionType.elemType().isKindOfCollection(Type.VoidHandling.EXCLUDE_VOID)) {
            throw new OclCodedUnsupportedOperationException(
                    OclDiagnosticCode.COLLECTION_VALUED_ATTRIBUTE_UNSUPPORTED,
                    "Nested collection-valued attribute metadata is invalid for Cypher rendering.");
        }
        String nestedAlias = state.newVariableAlias("nestedAttr");
        String ownerRelationship = binding == null ? "ObjectHasAttribute" : binding.ownerRelationship();
        String slotLabel = binding == null ? "AttributeValue" : binding.slotLabel();
        String outerPattern = "MATCH (" + sourceAlias + ")-[:" + ownerRelationship + "]->(val:" + slotLabel + ") " +
                "WHERE val.attributeKey = $" + attributeParam
                + modelPropertyConjunction("val", state) + " " +
                "MATCH (val)-[outer:HasNestedCollectionValue]->(" + nestedAlias + ":NestedCollectionValue)";
        return "COLLECT { " + outerPattern +
                " RETURN " + renderNestedCollectionNode(nestedAlias, outerCollectionType.elemType(), state) +
                " ORDER BY outer.index }";
    }

    private String renderNestedCollectionNode(String nodeAlias, Type type, RenderState state) {
        if (!(type instanceof CollectionType collectionType)) {
            throw new OclCodedUnsupportedOperationException(
                    OclDiagnosticCode.COLLECTION_VALUED_ATTRIBUTE_UNSUPPORTED,
                    "Each NestedCollectionValue node must be decoded against its collection type.");
        }
        Type elementType = collectionType.elemType();
        if (elementType.isKindOfCollection(Type.VoidHandling.EXCLUDE_VOID)) {
            String childAlias = state.newVariableAlias("nestedAttr");
            return "COLLECT { MATCH (" + nodeAlias + ")-[edge:HasNestedCollectionValue]->(" + childAlias + ":NestedCollectionValue) " +
                    "RETURN " + renderNestedCollectionNode(childAlias, elementType, state) +
                    " ORDER BY edge.index }";
        }
        if (elementType.isKindOfClass(Type.VoidHandling.EXCLUDE_VOID)) {
            return "COLLECT { MATCH (" + nodeAlias + ")-[r:objectReference|HasReferenceValue]->(target) " +
                    "RETURN target ORDER BY r.index }";
        }
        String itemAlias = state.newVariableAlias("nestedItem");
        String emptyCheck = nodeAlias + ".value IS NULL OR " + nodeAlias + ".value = 'Undefined' OR " +
                nodeAlias + ".value = 'COLLECTION_EMPTY'";
        String splitExpr = "split(" + nodeAlias + ".value, ' | ')";
        String itemValue = normalizeAttributeValue(itemAlias, elementType, state);
        return "CASE WHEN " + emptyCheck + " THEN [] ELSE [" + itemAlias + " IN " + splitExpr +
                " | " + itemValue + "] END";
    }

    private String attributeLookup(String sourceAlias, String attributeParam,
                                   OclCypherPlan.AttributeBinding binding, RenderState state) {
        String ownerRelationship = binding == null ? "ObjectHasAttribute" : binding.ownerRelationship();
        String slotLabel = binding == null ? "AttributeValue" : binding.slotLabel();
        String valueProperty = binding == null ? "value" : binding.valueProperty();
        return "head([(" + sourceAlias + ")-[:" + ownerRelationship + "]->(val:" + slotLabel + ") " +
                "WHERE val.attributeKey = $" + attributeParam
                + modelPropertyConjunction("val", state) + " | val." + valueProperty + "])";
    }

    private String referenceAttributeLookup(String sourceAlias, String attributeParam,
                                            OclCypherPlan.AttributeBinding binding, RenderState state) {
        String targetAlias = state.newVariableAlias("attrTarget");
        String referenceAlias = state.newRelationshipAlias();
        String ownerRelationship = binding == null ? "ObjectHasAttribute" : binding.ownerRelationship();
        String slotLabel = binding == null ? "AttributeValue" : binding.slotLabel();
        return "COLLECT { " +
                "MATCH (" + sourceAlias + ")-[:" + ownerRelationship + "]->(val:" + slotLabel + ") " +
                "WHERE val.attributeKey = $" + attributeParam
                + modelPropertyConjunction("val", state) + " " +
                "MATCH (val)-[" + referenceAlias + ":objectReference|HasReferenceValue]->"
                + modelScopedNodePattern(targetAlias, "Object", state) + " " +
                "RETURN " + targetAlias + " ORDER BY " + referenceAlias + ".index " +
                "}";
    }

    /**
     * Implements the formal asSources/ExpandSrc boundary. Every entity-like
     * value is re-identified by objectKey, bottom/look-alike values are removed,
     * and duplicate owners are collapsed before MATCH sees a node variable.
     */
    private String renderEntitySourceList(RenderedExpression source, RenderState state) {
        String candidateAlias = state.newVariableAlias("sourceCandidate");
        String keyAlias = state.newVariableAlias("sourceKey");
        String nodeAlias = state.newVariableAlias("sourceNode");
        String rawSource = source.type().isCollection()
                ? renderFiniteCollectionValue(source.cypher(), state)
                : "[" + source.cypher() + "]";
        return "COLLECT { UNWIND " + rawSource + " AS " + candidateAlias
                + " WITH " + candidateAlias + ".objectKey AS " + keyAlias
                + " WHERE " + keyAlias + " IS NOT NULL"
                + " MATCH " + modelScopedKeyedNodePattern(
                        nodeAlias, "Object", "objectKey", keyAlias, false, state)
                + " RETURN DISTINCT " + nodeAlias + " }";
    }

    private String renderSingletonNodeList(String expression, RenderState state) {
        return materializeOnce(expression, "singleton", state,
                value -> "CASE WHEN " + value + " IS NULL THEN [] ELSE [" + value + "] END");
    }

    /**
     * Materializes the explicit collection view recorded by the semantic binder.
     * In the certified profile the only scalar source allowed here is a resolved
     * native to-one navigation, whose collection semantics is empty/singleton.
     */
    private RenderedExpression renderCollectionView(RenderedExpression source,
                                                     OclTypeBinding sourceCollectionType,
                                                     RenderState state) {
        if (source.type().isCollection()) {
            // OCL_val completes a whole collection bottom to the empty finite set.
            // Cypher null is emitted directly by a collection-valued Void branch,
            // while bottom selected from a finite-set position is represented by
            // the reserved token. Both representations denote the same empty view.
            return new RenderedExpression(
                    renderFiniteCollectionValue(source.cypher(), state), sourceCollectionType);
        }
        if (sourceCollectionType.isCollection()) {
            return new RenderedExpression(renderSingletonNodeList(source.cypher(), state), sourceCollectionType);
        }
        return source;
    }

    /** Completes either runtime representation of whole-collection bottom to []. */
    private String renderFiniteCollectionValue(String collectionCypher, RenderState state) {
        String bottomToken = "$" + state.bottomTokenParam();
        return materializeOnce(collectionCypher, "finiteSet", state,
                value -> "(CASE WHEN " + value + " IS NULL OR coalesce(" + value
                        + " = " + bottomToken + ", false) THEN [] ELSE " + value + " END)");
    }

    private String renderIteratorIsUnique(String iteratorName, String sourceCypher, String bodyCypher, RenderState state) {
        String projected = "[" + iteratorName + " IN " + sourceCypher + " | " + bodyCypher + "]";
        return materializeOnce(projected, "projection", state,
                value -> "size(" + value + ") = size(" + renderUniqueCollection(value, state) + ")");
    }

    private String renderGuardedIsKindOfCheck(String sourceCypher, String classParam, RenderState state) {
        String receiverAlias = state.newVariableAlias("typeRecv");
        String receiverKeyAlias = receiverAlias + "Key";
        String classAlias = state.newVariableAlias("typeCls");
        return "EXISTS { WITH " + sourceCypher + ".objectKey AS " + receiverKeyAlias
                + " WHERE " + receiverKeyAlias + " IS NOT NULL"
                + " MATCH " + modelScopedKeyedNodePattern(
                        receiverAlias, "Object", "objectKey", receiverKeyAlias, false, state)
                + "-[:" + CanonicalGraphVocabulary.OBJECT_INSTANCE_OF + "]->"
                + modelScopedKeyedNodePattern(
                        classAlias, "UmlClass", "classKey", classParam, state)
                + " RETURN " + receiverAlias + " }";
    }

    private String renderGuardedCast(String sourceCypher, String classParam, RenderState state) {
        String receiverAlias = state.newVariableAlias("castRecv");
        String receiverKeyAlias = receiverAlias + "Key";
        String classAlias = state.newVariableAlias("castCls");
        return "head(COLLECT { WITH " + sourceCypher + ".objectKey AS " + receiverKeyAlias
                + " WHERE " + receiverKeyAlias + " IS NOT NULL"
                + " MATCH " + modelScopedKeyedNodePattern(
                        receiverAlias, "Object", "objectKey", receiverKeyAlias, false, state)
                + "-[:" + CanonicalGraphVocabulary.OBJECT_INSTANCE_OF + "]->"
                + modelScopedKeyedNodePattern(
                        classAlias, "UmlClass", "classKey", classParam, state)
                + " RETURN " + receiverAlias + " AS value })";
    }

    private String renderGuardedEntityValue(String sourceCypher, String receiverAlias,
                                            String entityValueCypher, RenderState state) {
        String receiverKeyAlias = receiverAlias + "Key";
        return "head(COLLECT { WITH " + sourceCypher + ".objectKey AS " + receiverKeyAlias
                + " WHERE " + receiverKeyAlias + " IS NOT NULL"
                + " MATCH " + modelScopedKeyedNodePattern(
                        receiverAlias, "Object", "objectKey", receiverKeyAlias, false, state)
                + " RETURN " + entityValueCypher + " AS value })";
    }

    private String modelScopedNodePattern(String alias, String label, RenderState state) {
        String modelKey = activeModelKey(state);
        if (modelKey == null || modelKey.isBlank()) {
            return "(" + alias + ":" + label + ")";
        }
        String modelParam = state.newParam(modelKey);
        return "(" + alias + ":" + label + " {modelKey: $" + modelParam + "})";
    }

    private String modelScopedKeyedNodePattern(String alias, String label,
                                               String keyName, String keyParam,
                                               RenderState state) {
        return modelScopedKeyedNodePattern(alias, label, keyName, keyParam, true, state);
    }

    private String modelScopedKeyedNodePattern(String alias, String label,
                                               String keyName, String keyValue,
                                               boolean parameterValue, RenderState state) {
        String value = parameterValue ? "$" + keyValue : keyValue;
        String modelKey = activeModelKey(state);
        if (modelKey == null || modelKey.isBlank()) {
            return "(" + alias + ":" + label + " {" + keyName + ": " + value + "})";
        }
        String modelParam = state.newParam(modelKey);
        return "(" + alias + ":" + label + " {modelKey: $" + modelParam
                + ", " + keyName + ": " + value + "})";
    }

    private String modelPropertyPredicate(String alias, RenderState state) {
        String modelKey = activeModelKey(state);
        if (modelKey == null || modelKey.isBlank()) return "";
        String modelParam = state.newParam(modelKey);
        return "AND " + alias + ".modelKey = $" + modelParam + " ";
    }

    private String modelPropertyConjunction(String alias, RenderState state) {
        String modelKey = activeModelKey(state);
        if (modelKey == null || modelKey.isBlank()) return "";
        String modelParam = state.newParam(modelKey);
        return " AND " + alias + ".modelKey = $" + modelParam;
    }

    private String classKey(String className, RenderState state) {
        String modelKey = activeModelKey(state);
        return modelKey == null || modelKey.isBlank()
                ? className
                : CanonicalGraphEncoding.classKey(modelKey, className);
    }

    private String attributeKey(OclCypherPlan.AttributeAccessPlan access, RenderState state) {
        if (access.binding() != null) return access.binding().attributeKey();
        String owner = access.attribute().owner().name();
        String modelKey = activeModelKey(state);
        return modelKey == null || modelKey.isBlank()
                ? owner + "::" + access.attributeName()
                : CanonicalGraphEncoding.attributeKey(modelKey, owner, access.attributeName());
    }

    private String associationKey(String associationName, RenderState state) {
        String modelKey = activeModelKey(state);
        return modelKey == null || modelKey.isBlank()
                ? associationName
                : CanonicalGraphEncoding.associationKey(modelKey, associationName);
    }

    private String activeModelKey(RenderState state) {
        if (state.graphBinding() != null) return state.graphBinding().modelKey();
        return modelName == null || modelName.isBlank()
                ? null : CanonicalGraphEncoding.modelKey(modelName);
    }

    private String renderIteratorSortedBy(String iteratorName, String sourceCypher, String bodyCypher, RenderState state) {
        String indexAlias = state.newVariableAlias("sortIdx");
        String keyAlias = state.newVariableAlias("sortKey");
        return "COLLECT { UNWIND range(0, size(" + sourceCypher + ") - 1) AS " + indexAlias +
                " WITH " + indexAlias + ", " + sourceCypher + "[" + indexAlias + "] AS " + iteratorName +
                " WITH " + indexAlias + ", " + iteratorName + ", " + bodyCypher + " AS " + keyAlias +
                " ORDER BY " + keyAlias + ", " + indexAlias +
                " RETURN " + iteratorName + " }";
    }

    private void requireNoCollectionArguments(String operationName, java.util.List<OclCypherPlan.ExpressionPlan> arguments) {
        if (!arguments.isEmpty()) {
            throw new OclCodedUnsupportedOperationException(
                    OclDiagnosticCode.INVALID_COLLECTION_ARGUMENT,
                    operationName + "() does not accept arguments.");
        }
    }

    private boolean isSimpleIdentifier(String expression) {
        return expression != null && expression.matches("[A-Za-z_][A-Za-z0-9_]*");
    }

    /**
     * Introduces a scalar expression exactly once before a semantic rule uses
     * it more than once. This is the concrete lowering of CQM's certified
     * {@code MATERIALIZE_REUSED_EXPRESSIONS} policy and prevents recursive
     * textual substitution from multiplying graph pattern comprehensions.
     */
    private String materializeOnce(String expression, String aliasStem, RenderState state,
                                   Function<String, String> body) {
        if (isAtomicCypher(expression)) return body.apply(expression);
        String alias = state.newVariableAlias(aliasStem);
        return "head(COLLECT { WITH " + expression + " AS " + alias
                + " RETURN " + body.apply(alias) + " AS value })";
    }

    /** Materializes both operands once before applying a binary semantic rule. */
    private String materializePair(String left, String right, String aliasStem, RenderState state,
                                   BiFunction<String, String, String> body) {
        if (isAtomicCypher(left) && isAtomicCypher(right)) return body.apply(left, right);
        String leftAlias = state.newVariableAlias(aliasStem + "Left");
        String rightAlias = state.newVariableAlias(aliasStem + "Right");
        return "head(COLLECT { WITH " + left + " AS " + leftAlias + ", " + right + " AS "
                + rightAlias + " RETURN " + body.apply(leftAlias, rightAlias) + " AS value })";
    }

    private boolean isAtomicCypher(String expression) {
        return expression != null && expression.matches(
                "(?:[A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)?|\\$[A-Za-z_][A-Za-z0-9_]*|null|true|false)");
    }

    private String renderExtremumCollection(String operationName, String sourceCypher, RenderState state) {
        String itemAlias = state.newVariableAlias("item");
        String bestAlias = state.newVariableAlias("best");
        String comparator = "min".equals(operationName) ? "<" : ">";
        return "reduce(" + bestAlias + " = null, " + itemAlias + " IN " + sourceCypher +
                " | CASE WHEN " + bestAlias + " IS NULL OR " + itemAlias + " " + comparator + " " + bestAlias +
                " THEN " + itemAlias + " ELSE " + bestAlias + " END)";
    }

    private String renderUniqueCollection(String sourceCypher, RenderState state) {
        String itemAlias = state.newVariableAlias("item");
        String accAlias = state.newVariableAlias("acc");
        String representedItem = renderSetValue(itemAlias, state);
        return "reduce(" + accAlias + " = [], " + itemAlias + " IN " + sourceCypher +
                " | CASE WHEN any(existing IN " + accAlias + " WHERE " +
                renderSetEquality("existing", itemAlias, state) + ")" +
                " THEN " + accAlias + " ELSE " + accAlias + " + " + representedItem + " END)";
    }

    private String renderCollect(RenderedExpression source, String iteratorAlias, String bodyCypher,
                                 RenderState state) {
        String projection = "[" + iteratorAlias + " IN " + source.cypher() + " | " + bodyCypher + "]";
        return source.type().isUniqueCollection() ? renderUniqueCollection(projection, state) : projection;
    }

    /** Converts scalar Cypher null into the theorem's non-null set-bottom representation. */
    private String renderSetValue(String valueCypher, RenderState state) {
        return "coalesce(" + valueCypher + ", $" + state.bottomTokenParam() + ")";
    }

    /** Total equality for values occurring in a finite-set position. */
    private String renderSetEquality(String leftCypher, String rightCypher, RenderState state) {
        return "coalesce(" + renderSetValue(leftCypher, state) + " = " +
                renderSetValue(rightCypher, state) + ", false)";
    }

    /**
     * Total equality for scalar/entity values in the canonical profile.
     * Cypher's native {@code null = null} is {@code null}; OCL_val instead has
     * one bottom value and defines bottom equality as true only against bottom.
     */
    private String renderSemanticScalarEquality(String operator, String leftCypher, String rightCypher,
                                                 RenderState state) {
        return materializePair(leftCypher, rightCypher, "equality", state, (left, right) -> {
            String leftBottom = renderIsBottom(left, state);
            String rightBottom = renderIsBottom(right, state);
            String equality = "(CASE WHEN (" + leftBottom + " AND " + rightBottom
                    + ") THEN true WHEN (" + leftBottom + " OR " + rightBottom
                    + ") THEN false ELSE coalesce(" + left + " = " + right
                    + ", false) END)";
            return "<>".equals(operator) ? "(NOT " + equality + ")" : equality;
        });
    }

    /** Bottom is absorbing for certified ordering and arithmetic operations. */
    private String renderBottomPropagatingBinary(String operator, String leftCypher, String rightCypher,
                                                  RenderState state) {
        return materializePair(leftCypher, rightCypher, "binary", state,
                (left, right) -> "(CASE WHEN " + renderIsBottom(left, state) + " OR "
                        + renderIsBottom(right, state) + " THEN null ELSE ("
                        + left + " " + operator + " " + right + ") END)");
    }

    /** Recognizes both concrete representations of the typed semantic bottom. */
    private String renderIsBottom(String valueCypher, RenderState state) {
        return "(" + valueCypher + " IS NULL OR coalesce(" + valueCypher + " = $"
                + state.bottomTokenParam() + ", false))";
    }

    private boolean isCollectionOrVoid(OclTypeBinding type) {
        return type.isCollection() || (!type.isNode() && !type.isClassReference()
                && "Void".equals(type.typeName()));
    }

    /** Extensional, order-independent equality for the certified finite-set fragment. */
    private String renderFiniteSetComparison(String operator, String leftCypher, String rightCypher,
                                              RenderState state) {
        String leftAlias = state.newVariableAlias("setLeft");
        String rightAlias = state.newVariableAlias("setRight");
        String left = renderFiniteCollectionValue(leftCypher, state);
        String right = renderFiniteCollectionValue(rightCypher, state);
        return materializePair(left, right, "setComparison", state, (leftValue, rightValue) -> {
            String equality = "(all(" + leftAlias + " IN " + leftValue + " WHERE any(" + rightAlias
                    + " IN " + rightValue + " WHERE " + renderSetEquality(leftAlias, rightAlias, state)
                    + ")) AND all(" + rightAlias + " IN " + rightValue + " WHERE any(" + leftAlias
                    + " IN " + leftValue + " WHERE " + renderSetEquality(leftAlias, rightAlias, state) + ")))";
            return "<>".equals(operator) ? "(NOT " + equality + ")" : equality;
        });
    }

    public record RenderedInvariant(String cypher, Map<String, Object> parameters,
                                    RawCypherAst.ProductionQuery rawAst) {
        public RenderedInvariant {
            parameters = Map.copyOf(parameters);
            if (!cypher.equals(new RawCypherRenderer().render(rawAst))) {
                throw new IllegalArgumentException("Cypher text does not match its typed Raw AST");
            }
        }

        public RenderedInvariant(String cypher, Map<String, Object> parameters) {
            this(cypher, parameters, new RawCypherParser().parse(cypher));
        }
    }

    public record RenderedTopLevelExpression(String cypher, Map<String, Object> parameters,
                                             RawCypherAst.ProductionQuery rawAst) {
        public RenderedTopLevelExpression {
            parameters = Map.copyOf(parameters);
            if (!cypher.equals(new RawCypherRenderer().render(rawAst))) {
                throw new IllegalArgumentException("Cypher text does not match its typed Raw AST");
            }
        }

        public RenderedTopLevelExpression(String cypher, Map<String, Object> parameters) {
            this(cypher, parameters, new RawCypherParser().parse(cypher));
        }
    }

    private record RenderedExpression(String cypher, OclTypeBinding type) {
    }

    private static final class RenderState {
        private final Map<String, Object> parameters = new LinkedHashMap<>();
        private final Deque<Map<String, OclTypeBinding>> scopes = new ArrayDeque<>();
        private final Deque<Map<String, String>> variableAliases = new ArrayDeque<>();
        private final Deque<Map<String, String>> expressionBindings = new ArrayDeque<>();
        private final Set<String> usedAliases = new HashSet<>();
        private int parameterCounter = 0;
        private int variableCounter = 0;
        private int relationshipCounter = 0;
        private String bottomTokenParam;
        private final OclCypherPlan.GraphContextBinding graphBinding;

        private RenderState() {
            this(null);
        }

        private RenderState(OclCypherPlan.GraphContextBinding graphBinding) {
            this.graphBinding = graphBinding;
            scopes.push(new LinkedHashMap<>());
            variableAliases.push(new LinkedHashMap<>());
            expressionBindings.push(new LinkedHashMap<>());
        }

        private String newParam(Object value) {
            String name = "p" + (++parameterCounter);
            parameters.put(name, value);
            return name;
        }

        private String reserveAlias(String preferred) {
            if (usedAliases.add(preferred)) return preferred;
            String candidate;
            do {
                candidate = preferred + "_v" + (++variableCounter);
            } while (!usedAliases.add(candidate));
            return candidate;
        }

        private String newVariableAlias(String prefix) {
            String candidate;
            do {
                candidate = prefix + (++variableCounter);
            } while (!usedAliases.add(candidate));
            return candidate;
        }

        private String newRelationshipAlias() {
            relationshipCounter++;
            return reserveAlias(relationshipCounter == 1 ? "r" : "r" + relationshipCounter);
        }

        private String bottomTokenParam() {
            if (bottomTokenParam == null) {
                bottomTokenParam = newParam(OclBottomToken.value());
            }
            return bottomTokenParam;
        }

        private String enterVariable(String name, OclTypeBinding binding) {
            String alias = reserveAlias(name);
            Map<String, OclTypeBinding> nextScope = new LinkedHashMap<>(scopes.peek());
            nextScope.put(name, binding);
            scopes.push(nextScope);
            Map<String, String> nextAliases = new LinkedHashMap<>(variableAliases.peek());
            nextAliases.put(name, alias);
            variableAliases.push(nextAliases);
            Map<String, String> nextExpressions = new LinkedHashMap<>(expressionBindings.peek());
            nextExpressions.remove(name);
            expressionBindings.push(nextExpressions);
            return alias;
        }

        private void exitVariable() {
            scopes.pop();
            variableAliases.pop();
            expressionBindings.pop();
        }

        private void enterExpressionBinding(String name, String expressionCypher) {
            Map<String, String> nextScope = new LinkedHashMap<>(expressionBindings.peek());
            nextScope.put(name, expressionCypher);
            expressionBindings.push(nextScope);
        }

        private void exitExpressionBinding() {
            expressionBindings.pop();
        }

        private String lookupExpression(String name) {
            return expressionBindings.peek().get(name);
        }

        private boolean hasVariable(String name) {
            return scopes.peek().containsKey(name);
        }

        private String lookupVariableAlias(String name) {
            return variableAliases.peek().get(name);
        }

        private Map<String, Object> parameters() {
            return Map.copyOf(parameters);
        }

        private OclCypherPlan.GraphContextBinding graphBinding() {
            return graphBinding;
        }
    }
}
