package org.uet.dse.neo4jtgg.ocl.ir;

import org.uet.dse.neo4j.sync.helper.OclSerializer;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    public OclCypherRenderer() {
        this(null);
    }

    public OclCypherRenderer(String modelName) {
        this.modelName = modelName;
    }

    public RenderedInvariant renderInvariant(OclCypherPlan.InvariantPlan invariantPlan) {
        RenderState state = new RenderState();
        state.enterVariable("self", OclTypeBinding.node(invariantPlan.contextClassName()));
        RenderedExpression predicate = renderExpression(invariantPlan.predicate(), state);
        String classParam = state.newParam(classKey(invariantPlan.contextClassName()));
        String cypher = "MATCH (self:Object)-[:" + CanonicalGraphVocabulary.OBJECT_INSTANCE_OF
                + "]->(cls:UmlClass {classKey: $" + classParam + "})\n" +
                "WHERE " + OclValidationSemantics.violationPredicate(predicate.cypher()) + "\n" +
                "RETURN DISTINCT self.use_id AS useId";
        Map<String, Object> parameters = state.parameters();
        OclBottomToken.requireWellFormedGeneratedParameters(parameters);
        return new RenderedInvariant(cypher, parameters);
    }

    public RenderedTopLevelExpression renderTopLevelExpression(OclCypherPlan.ExpressionPlan expressionPlan) {
        RenderState state = new RenderState();
        RenderedExpression expression = renderExpression(expressionPlan, state);
        Map<String, Object> parameters = state.parameters();
        OclBottomToken.requireWellFormedGeneratedParameters(parameters);
        return new RenderedTopLevelExpression("RETURN " + expression.cypher() + " AS value", parameters);
    }

    private RenderedExpression renderExpression(OclCypherPlan.ExpressionPlan expression, RenderState state) {
        if (expression instanceof OclCypherPlan.VariablePlan variable) {
            String boundExpression = state.lookupExpression(variable.name());
            if (boundExpression != null) {
                return new RenderedExpression(boundExpression, variable.type());
            }
            if (state.hasVariable(variable.name())) {
                return new RenderedExpression(variable.name(), variable.type());
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
            return new RenderedExpression(
                    "(CASE WHEN " + OclValidationSemantics.validationTruth(condition.cypher()) + " THEN " + thenBranch.cypher() +
                            " ELSE " + elseBranch.cypher() + " END)",
                    ifPlan.type());
        }
        if (expression instanceof OclCypherPlan.LetPlan letPlan) {
            RenderedExpression value = renderExpression(letPlan.value(), state);
            String letAlias = "_let" + state.newVariableSuffix();
            state.enterExpressionBinding(letPlan.variableName(), letAlias);
            RenderedExpression body = renderExpression(letPlan.body(), state);
            state.exitExpressionBinding();
            return new RenderedExpression(
                    "head([" + letAlias + " IN [(" + value.cypher() + ")] | " + body.cypher() + "])",
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
            String rendered = binary.left().type().isCollection()
                    && binary.right().type().isCollection()
                    && ("=".equals(operator) || "<>".equals(operator))
                    ? renderFiniteSetComparison(operator, left.cypher(), right.cypher(), state)
                    : "=".equals(operator) || "<>".equals(operator)
                    ? renderSemanticScalarEquality(operator, left.cypher(), right.cypher())
                    : "IMPLIES".equals(operator)
                    ? OclValidationSemantics.implies(left.cypher(), right.cypher())
                    : switch (operator) {
                        case "AND" -> OclValidationSemantics.and(left.cypher(), right.cypher());
                        case "OR" -> OclValidationSemantics.or(left.cypher(), right.cypher());
                        case "XOR" -> OclValidationSemantics.xor(left.cypher(), right.cypher());
                        default -> "(" + left.cypher() + " " + operator + " " + right.cypher() + ")";
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
                    collectionOperation.sourceCollectionType());
            return renderCollectionOperation(collectionOperation, source, state);
        }
        if (expression instanceof OclCypherPlan.IteratorOperationPlan iteratorOperation) {
            if (iteratorOperation.source() instanceof OclCypherPlan.NavigationAccessPlan navigationAccess) {
                return renderNavigationIterator(iteratorOperation, navigationAccess, state);
            }
            RenderedExpression source = renderCollectionView(
                    renderExpression(iteratorOperation.source(), state),
                    iteratorOperation.sourceCollectionType());
            state.enterVariable(iteratorOperation.iteratorName(), iteratorOperation.sourceCollectionType().elementType());
            RenderedExpression body = renderExpression(iteratorOperation.body(), state);
            state.exitVariable();
            String predicate = OclValidationSemantics.validationTruth(body.cypher());
            String negatedPredicate = OclValidationSemantics.not(body.cypher());

            String rendered = switch (iteratorOperation.operationName().toLowerCase()) {
                case "select" -> "[" + iteratorOperation.iteratorName() + " IN " + source.cypher() + " WHERE " + predicate + "]";
                case "reject" -> "[" + iteratorOperation.iteratorName() + " IN " + source.cypher() + " WHERE " + negatedPredicate + "]";
                case "exists" -> "any(" + iteratorOperation.iteratorName() + " IN " + source.cypher() + " WHERE " + predicate + ")";
                case "forall" -> "all(" + iteratorOperation.iteratorName() + " IN " + source.cypher() + " WHERE " + predicate + ")";
                case "one" -> "single(" + iteratorOperation.iteratorName() + " IN " + source.cypher() + " WHERE " + predicate + ")";
                case "any" -> "head([" + iteratorOperation.iteratorName() + " IN " + source.cypher() + " WHERE " + predicate + " | " + iteratorOperation.iteratorName() + "])";
                case "collect" -> renderCollect(source, iteratorOperation.iteratorName(), body.cypher(), state);
                case "isunique" -> renderIteratorIsUnique(iteratorOperation.iteratorName(), source.cypher(), body.cypher(), state);
                case "sortedby" -> renderIteratorSortedBy(iteratorOperation.iteratorName(), source.cypher(), body.cypher(), state);
                default -> throw new OclCodedUnsupportedOperationException(
                        OclDiagnosticCode.UNSUPPORTED_ITERATOR,
                        "Unsupported iterator: " + iteratorOperation.operationName());
            };
            return new RenderedExpression(rendered, iteratorOperation.type());
        }
        if (expression instanceof OclCypherPlan.ExistsSubqueryPlan existsPlan) {
            return new RenderedExpression("EXISTS { " + renderNavigationMatch(existsPlan.match(), state) +
                    renderPredicateClause(existsPlan.match(), state) + " }", existsPlan.type());
        }
        if (expression instanceof OclCypherPlan.NotExistsSubqueryPlan notExistsPlan) {
            return new RenderedExpression("NOT EXISTS { " + renderNavigationMatch(notExistsPlan.match(), state) +
                    renderPredicateClause(notExistsPlan.match(), state) + " }", notExistsPlan.type());
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

    private RenderedExpression renderNavigationIterator(OclCypherPlan.IteratorOperationPlan iteratorOperation,
                                                        OclCypherPlan.NavigationAccessPlan navigationAccess,
                                                        RenderState state) {
        String operation = iteratorOperation.operationName().toLowerCase();
        if (!"exists".equals(operation) && !"forall".equals(operation)) {
            RenderedExpression source = renderCollectionView(
                    renderExpression(iteratorOperation.source(), state),
                    iteratorOperation.sourceCollectionType());
            state.enterVariable(iteratorOperation.iteratorName(), iteratorOperation.sourceCollectionType().elementType());
            RenderedExpression body = renderExpression(iteratorOperation.body(), state);
            state.exitVariable();
            String predicate = OclValidationSemantics.validationTruth(body.cypher());
            String negatedPredicate = OclValidationSemantics.not(body.cypher());
            String rendered = switch (operation) {
                case "select" -> "[" + iteratorOperation.iteratorName() + " IN " + source.cypher() + " WHERE " + predicate + "]";
                case "reject" -> "[" + iteratorOperation.iteratorName() + " IN " + source.cypher() + " WHERE " + negatedPredicate + "]";
                case "collect" -> renderCollect(source, iteratorOperation.iteratorName(), body.cypher(), state);
                case "any" -> "head([" + iteratorOperation.iteratorName() + " IN " + source.cypher() + " WHERE " + predicate + " | " + iteratorOperation.iteratorName() + "])";
                case "one" -> "single(" + iteratorOperation.iteratorName() + " IN " + source.cypher() + " WHERE " + predicate + ")";
                case "isunique" -> renderIteratorIsUnique(iteratorOperation.iteratorName(), source.cypher(), body.cypher(), state);
                case "sortedby" -> renderIteratorSortedBy(iteratorOperation.iteratorName(), source.cypher(), body.cypher(), state);
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
        String matchClause = renderNavigationMatch(matchPlan, state);
        if ("exists".equals(operation)) {
            return new RenderedExpression("EXISTS { " + matchClause +
                    renderPredicateClause(matchPlan, state) + " }",
                    iteratorOperation.type());
        }
        return new RenderedExpression("NOT EXISTS { " + matchClause +
                renderPredicateClause(matchPlan, state) + " }",
                iteratorOperation.type());
    }

    private RenderedExpression renderMethodCall(OclCypherPlan.MethodCallPlan methodCall, RenderedExpression source, RenderState state) {
        if ("allInstances".equalsIgnoreCase(methodCall.methodName())) {
            String classParam = state.newParam(classKey(source.type().typeName()));
            String objectAlias = "obj" + state.newVariableSuffix();
            String classAlias = "cls" + state.newVariableSuffix();
            String cypher = "COLLECT { MATCH (" + objectAlias + ")-[:"
                    + CanonicalGraphVocabulary.OBJECT_INSTANCE_OF + "]->(" + classAlias
                    + ":UmlClass {classKey: $" + classParam + "}) RETURN DISTINCT " + objectAlias + " }";
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
                    ? "size(" + source.cypher() + ") > 0"
                    : source.cypher() + " IS NOT NULL";
            return new RenderedExpression("(" + cypher + ")", methodCall.type());
        }
        if ("isUndefined".equalsIgnoreCase(methodCall.methodName())) {
            String cypher = source.type().isCollection()
                    ? "size(" + source.cypher() + ") = 0"
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
            String classParam = state.newParam(classKey(typeArg.type().typeName()));
            return new RenderedExpression(renderGuardedIsKindOfCheck(source.cypher(), classParam, state), methodCall.type());
        }
        if ("oclAsType".equalsIgnoreCase(methodCall.methodName())) {
            if (methodCall.arguments().size() != 1) {
                throw new OclCodedUnsupportedOperationException(
                        OclDiagnosticCode.INVALID_METHOD_ARGUMENT,
                        "oclAsType() requires a type argument.");
            }
            RenderedExpression typeArg = renderExpression(methodCall.arguments().get(0), state);
            String classParam = state.newParam(classKey(typeArg.type().typeName()));
            return new RenderedExpression(renderGuardedCast(source.cypher(), classParam, state), methodCall.type());
        }
        throw new OclCodedUnsupportedOperationException(
                OclDiagnosticCode.UNSUPPORTED_METHOD_CALL,
                "Unsupported method call: " + methodCall.methodName());
    }

    private RenderedExpression renderAttributeAccess(OclCypherPlan.AttributeAccessPlan attributeAccess,
                                                     RenderedExpression source,
                                                     RenderState state) {
        String attributeParam = state.newParam(attributeKey(attributeAccess));
        if (source.type().isCollection()) {
            String itemAlias = "attrOwner" + state.newVariableSuffix();
            String receiverAlias = "attrRecv" + state.newVariableSuffix();
            RenderedExpression mappedValue = renderAttributeValueAccess(
                    attributeAccess, receiverAlias, attributeParam, state);
            String cypher = "[" + itemAlias + " IN " + source.cypher() + " | "
                    + renderGuardedEntityValue(itemAlias, receiverAlias, mappedValue.cypher(), state) + "]";
            return new RenderedExpression(cypher, attributeAccess.type());
        }

        String itemAlias = "attrOwner" + state.newVariableSuffix();
        RenderedExpression mappedValue = renderAttributeValueAccess(attributeAccess, itemAlias, attributeParam, state);
        String cypher = renderGuardedEntityValue(source.cypher(), itemAlias, mappedValue.cypher(), state);
        return new RenderedExpression(cypher, attributeAccess.type());
    }

    private RenderedExpression renderAttributeValueAccess(OclCypherPlan.AttributeAccessPlan attributeAccess,
                                                          String sourceAlias,
                                                          String attributeParam,
                                                          RenderState state) {
        if (attributeAccess.type().isNode()) {
            String references = referenceAttributeLookup(sourceAlias, attributeParam);
            return new RenderedExpression("head(" + references + ")", attributeAccess.type());
        }
        if (attributeAccess.type().isCollection() && attributeAccess.type().elementType().isNode()) {
            return new RenderedExpression(referenceAttributeLookup(sourceAlias, attributeParam), attributeAccess.type());
        }
        if (attributeAccess.type().isCollection() && attributeAccess.type().elementType().isCollection()) {
            return new RenderedExpression(
                    nestedCollectionAttributeLookup(sourceAlias, attributeParam, attributeAccess.attributeType(), state),
                    attributeAccess.type());
        }
        String raw = attributeLookup(sourceAlias, attributeParam);
        String normalized = attributeAccess.type().isCollection()
                ? normalizeCollectionAttributeValue(raw, attributeAccess.attributeType(), state)
                : normalizeAttributeValue(raw, attributeAccess.attributeType());
        return new RenderedExpression(normalized, attributeAccess.type());
    }

    private RenderedExpression renderNavigationAccess(OclCypherPlan.NavigationAccessPlan navigationAccess,
                                                      RenderedExpression source,
                                                      RenderState state) {
        if (!source.type().isCollection() && isSimpleIdentifier(source.cypher())) {
            String listExpr = "[" + renderNavigationPattern(source.cypher(), "t", navigationAccess, state) + " | t]";
            String cypher = navigationAccess.type().isCollection() ? listExpr : "head(" + listExpr + ")";
            return new RenderedExpression(cypher, navigationAccess.type());
        }

        String ownerAlias = "navOwner" + state.newVariableSuffix();
        String targetAlias = "navTarget" + state.newVariableSuffix();

        if (source.type().isCollection()) {
            String perOwnerTargets = "[" + renderNavigationPattern(ownerAlias, targetAlias, navigationAccess, state) + " | " + targetAlias + "]";
            String accAlias = "navAcc" + state.newVariableSuffix();
            String flattened = "reduce(" + accAlias + " = [], " + ownerAlias + " IN " + source.cypher()
                    + " | " + accAlias + " + " + perOwnerTargets + ")";
            String cypher = navigationAccess.type().isCollection() ? flattened : "head(" + flattened + ")";
            return new RenderedExpression(cypher, navigationAccess.type());
        }

        String perOwnerTargets = "[" + renderNavigationPattern(ownerAlias, targetAlias, navigationAccess, state) + " | " + targetAlias + "]";
        String collectionExpr = "coalesce(head([" + ownerAlias + " IN " + renderSingletonNodeList(source.cypher()) + " | " + perOwnerTargets + "]), [])";
        String cypher = navigationAccess.type().isCollection() ? collectionExpr : "head(" + collectionExpr + ")";
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
                String alias = "item" + state.newVariableSuffix();
                yield new RenderedExpression("size([" + alias + " IN " + source.cypher() +
                        " WHERE " + renderSetEquality(alias, candidate.cypher(), state) + "])",
                        collectionOperation.type());
            }
            case "isEmpty" -> new RenderedExpression("size(" + source.cypher() + ") = 0", collectionOperation.type());
            case "notEmpty" -> new RenderedExpression("size(" + source.cypher() + ") > 0", collectionOperation.type());
            case "sum" -> {
                requireNoCollectionArguments("sum", collectionOperation.arguments());
                String itemAlias = "item" + state.newVariableSuffix();
                String accAlias = "acc" + state.newVariableSuffix();
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
                String alias = "item" + state.newVariableSuffix();
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
                String alias = "item" + state.newVariableSuffix();
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
                RenderedExpression candidates = renderExpression(collectionOperation.arguments().get(0), state);
                String outerAlias = "candidate" + state.newVariableSuffix();
                String innerAlias = "item" + state.newVariableSuffix();
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
                RenderedExpression candidates = renderExpression(collectionOperation.arguments().get(0), state);
                String outerAlias = "candidate" + state.newVariableSuffix();
                String innerAlias = "item" + state.newVariableSuffix();
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
                String alias = "item" + state.newVariableSuffix();
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
                RenderedExpression candidates = renderExpression(collectionOperation.arguments().get(0), state);
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
                String itemAlias = "item" + state.newVariableSuffix();
                String accAlias = "acc" + state.newVariableSuffix();
                String flattened = "reduce(" + accAlias + " = [], " + itemAlias + " IN " + source.cypher() +
                        " | CASE WHEN " + itemAlias + " IS NULL THEN " + accAlias + " ELSE " + accAlias + " + " + itemAlias + " END)";
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
                RenderedExpression candidates = renderExpression(collectionOperation.arguments().get(0), state);
                String itemAlias = "item" + state.newVariableSuffix();
                String candidateAlias = "candidate" + state.newVariableSuffix();
                String cypher;
                if (collectionOperation.type().isUniqueCollection()) {
                    String filtered = "[" + itemAlias + " IN " + source.cypher() +
                            " WHERE any(" + candidateAlias + " IN " + candidates.cypher() +
                            " WHERE " + renderSetEquality(candidateAlias, itemAlias, state) + ")]";
                    cypher = renderUniqueCollection(filtered, state);
                } else {
                    String accAlias = "acc" + state.newVariableSuffix();
                    String existingAlias = "existing" + state.newVariableSuffix();
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
        String matchClause = renderNavigationMatch(countComparison.match(), state);
        String predicateClause = renderPredicateClause(countComparison.match(), state);
        String countBody = matchClause + predicateClause;

        long literal = countComparison.literal();
        return switch (countComparison.operator()) {
            case ">" -> new RenderedExpression("(COUNT { " + countBody + " } > $" + state.newParam(literal) + ")", countComparison.type());
            case ">=" -> new RenderedExpression("(COUNT { " + countBody + " } >= $" + state.newParam(literal) + ")", countComparison.type());
            case "=" -> new RenderedExpression("(COUNT { " + countBody + " } = $" + state.newParam(literal) + ")", countComparison.type());
            case "<>" -> new RenderedExpression("(COUNT { " + countBody + " } <> $" + state.newParam(literal) + ")", countComparison.type());
            case "<" -> new RenderedExpression("(COUNT { " + countBody + " } < $" + state.newParam(literal) + ")", countComparison.type());
            case "<=" -> new RenderedExpression("(COUNT { " + countBody + " } <= $" + state.newParam(literal) + ")", countComparison.type());
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
                String itemAlias = "item" + state.newVariableSuffix();
                String accAlias = "acc" + state.newVariableSuffix();
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
        if (!owner.type().isCollection() && isSimpleIdentifier(owner.cypher())) {
            return renderSingleOwnerProjection(owner.cypher(), matchPlan, projection, state);
        }
        String ownerAlias = "navOwner" + state.newVariableSuffix();
        String ownerSource = owner.type().isCollection() ? owner.cypher() : renderSingletonNodeList(owner.cypher());
        String accAlias = "navAggAcc" + state.newVariableSuffix();
        String perOwner = renderSingleOwnerProjection(ownerAlias, matchPlan, projection, state);
        return "reduce(" + accAlias + " = [], " + ownerAlias + " IN " + ownerSource + " | " + accAlias + " + " + perOwner + ")";
    }

    private String renderSingleOwnerProjection(String ownerAlias,
                                               OclCypherPlan.NavigationMatchPlan matchPlan,
                                               OclCypherPlan.ExpressionPlan projection,
                                               RenderState state) {
        state.enterVariable(matchPlan.targetAlias(), matchPlan.targetType());
        RenderedExpression projected = renderExpression(projection, state);
        String predicateClause = renderPredicateClause(matchPlan, state);
        state.exitVariable();
        return "[" + renderNavigationPattern(ownerAlias, matchPlan.targetAlias(), matchPlan.navigation(), state)
                + predicateClause + " | " + projected.cypher() + "]";
    }

    private String renderNavigationMatch(OclCypherPlan.NavigationMatchPlan matchPlan, RenderState state) {
        RenderedExpression owner = renderExpression(matchPlan.owner(), state);
        if (!owner.type().isCollection() && isSimpleIdentifier(owner.cypher())) {
            return "MATCH " + renderNavigationPattern(owner.cypher(), matchPlan.targetAlias(), matchPlan.navigation(), state);
        }
        String ownerAlias = "navOwner" + state.newVariableSuffix();
        String ownerSource = owner.type().isCollection() ? owner.cypher() : renderSingletonNodeList(owner.cypher());
        return "UNWIND " + ownerSource + " AS " + ownerAlias + "\nMATCH "
                + renderNavigationPattern(ownerAlias, matchPlan.targetAlias(), matchPlan.navigation(), state);
    }

    private String renderNavigationPattern(String sourceAlias, String targetAlias,
                                           OclCypherPlan.NavigationAccessPlan navigationAccess,
                                           RenderState state) {
        org.uet.dse.neo4jtgg.ocl.OclMetamodelIndex.NavigationInfo navigationInfo = navigationAccess.navigation();
        String associationParam = state.newParam(associationKey(navigationInfo.associationName()));
        String sourceRoleParam = state.newParam(navigationInfo.sourceRoleName());
        String targetRoleParam = state.newParam(navigationInfo.targetRoleName());
        String relationshipAlias = state.newRelationshipAlias();
        String qualifierPredicate = renderQualifierPredicate(navigationAccess, relationshipAlias, state);
        return switch (navigationInfo.direction()) {
            case OUTGOING -> "(" + sourceAlias + ")-[" + relationshipAlias + "]->(" + targetAlias
                    + ") WHERE type(" + relationshipAlias + ") STARTS WITH 'Link' " +
                    "AND " + relationshipAlias + ".associationKey = $" + associationParam +
                    " AND " + relationshipAlias + ".sourceRole = $" + sourceRoleParam +
                    " AND " + relationshipAlias + ".targetRole = $" + targetRoleParam +
                    qualifierPredicate;
            case INCOMING -> "(" + sourceAlias + ")<-[" + relationshipAlias + "]-(" + targetAlias
                    + ") WHERE type(" + relationshipAlias + ") STARTS WITH 'Link' " +
                    "AND " + relationshipAlias + ".associationKey = $" + associationParam +
                    " AND " + relationshipAlias + ".sourceRole = $" + targetRoleParam +
                    " AND " + relationshipAlias + ".targetRole = $" + sourceRoleParam +
                    qualifierPredicate;
            case UNDIRECTED -> "(" + sourceAlias + ")-[" + relationshipAlias + "]-(" + targetAlias
                    + ") WHERE type(" + relationshipAlias + ") STARTS WITH 'Link' " +
                    "AND " + relationshipAlias + ".associationKey = $" + associationParam +
                    " AND ((" + relationshipAlias + ".sourceRole = $" + sourceRoleParam + " AND "
                    + relationshipAlias + ".targetRole = $" + targetRoleParam + ")" +
                    " OR (" + relationshipAlias + ".sourceRole = $" + targetRoleParam + " AND "
                    + relationshipAlias + ".targetRole = $" + sourceRoleParam + "))" +
                    qualifierPredicate;
        };
    }

    private String renderQualifierPredicate(OclCypherPlan.NavigationAccessPlan navigationAccess,
                                            String relationshipAlias, RenderState state) {
        if (navigationAccess.qualifiers().isEmpty()) {
            return "";
        }
        String propertyName = switch (navigationAccess.navigation().direction()) {
            case OUTGOING, UNDIRECTED -> "sourceQualifiers";
            case INCOMING -> "targetQualifiers";
        };
        StringBuilder predicate = new StringBuilder();
        for (int i = 0; i < navigationAccess.qualifiers().size(); i++) {
            OclCypherPlan.ExpressionPlan qualifier = navigationAccess.qualifiers().get(i);
            predicate.append(" AND coalesce(").append(relationshipAlias).append(".")
                    .append(propertyName).append("[").append(i).append("], '') = ")
                    .append(renderSerializedQualifierValue(qualifier, state));
        }
        return predicate.toString();
    }

    private String renderSerializedQualifierValue(OclCypherPlan.ExpressionPlan qualifier, RenderState state) {
        RenderedExpression rendered = renderExpression(qualifier, state);
        OclTypeBinding type = qualifier.type();
        if (type.isCollection() || type.isNode() || type.isClassReference()) {
            throw new OclCodedUnsupportedOperationException(
                    OclDiagnosticCode.QUALIFIED_ASSOCIATION_UNSUPPORTED,
                    "Qualified navigation currently supports only scalar qualifier expressions.");
        }
        if ("String".equals(type.typeName())) {
            return "(CASE WHEN " + rendered.cypher() + " IS NULL THEN 'Undefined' ELSE " +
                    "(\"'\" + replace(toString(" + rendered.cypher() + "), \"'\", \"\") + \"'\") END)";
        }
        if ("Integer".equals(type.typeName())) {
            return "(CASE WHEN " + rendered.cypher() + " IS NULL THEN 'Undefined' ELSE " +
                    "toString(toInteger(" + rendered.cypher() + ")) END)";
        }
        if ("Real".equals(type.typeName())) {
            String floatExpr = "toString(toFloat(" + rendered.cypher() + "))";
            return "(CASE WHEN " + rendered.cypher() + " IS NULL THEN 'Undefined' ELSE " +
                    "(CASE WHEN " + floatExpr + " CONTAINS '.' THEN " + floatExpr +
                    " ELSE " + floatExpr + " + '.0' END) END)";
        }
        if ("Boolean".equals(type.typeName())) {
            return "(CASE WHEN " + rendered.cypher() + " IS NULL THEN 'Undefined' ELSE " +
                    "(CASE WHEN " + rendered.cypher() + " THEN 'true' ELSE 'false' END) END)";
        }
        // Enum and other scalar domain types are persisted using the same quoted
        // OCL-literal shape as strings, e.g. '#A1' for enum qualifiers.
        if (!type.isCollection() && !type.isNode() && !type.isClassReference()) {
            return "(CASE WHEN " + rendered.cypher() + " IS NULL THEN 'Undefined' ELSE " +
                    "(\"'\" + replace(toString(" + rendered.cypher() + "), \"'\", \"\") + \"'\") END)";
        }
        if (qualifier instanceof OclCypherPlan.LiteralPlan literal
                && literal.value() instanceof String stringValue
                && stringValue.startsWith("#")) {
            return "(CASE WHEN " + rendered.cypher() + " IS NULL THEN 'Undefined' ELSE " +
                    "(\"'\" + replace(toString(" + rendered.cypher() + "), \"'\", \"\") + \"'\") END)";
        }
        throw new OclCodedUnsupportedOperationException(
                OclDiagnosticCode.QUALIFIED_ASSOCIATION_UNSUPPORTED,
                "Qualified navigation currently supports only primitive scalar qualifier expressions.");
    }

    private String renderPredicateClause(OclCypherPlan.NavigationMatchPlan matchPlan, RenderState state) {
        if (matchPlan.predicateMode() == OclCypherPlan.PredicateMode.NONE || matchPlan.predicate() == null) {
            return "";
        }

        state.enterVariable(matchPlan.targetAlias(), matchPlan.targetType());
        RenderedExpression body = renderExpression(matchPlan.predicate(), state);
        state.exitVariable();
        return switch (matchPlan.predicateMode()) {
            case NONE -> "";
            case NORMAL -> " AND " + OclValidationSemantics.validationTruth(body.cypher());
            case NEGATED -> " AND " + OclValidationSemantics.not(body.cypher());
        };
    }

    private String normalizeAttributeValue(String raw, org.tzi.use.uml.ocl.type.Type type) {
        String stripped = "CASE WHEN " + raw + " IS NULL OR " + raw + " = 'Undefined' " +
                "THEN null ELSE replace(" + raw + ", \"'\", \"\") END";
        if (type.isTypeOfInteger()) {
            return "toInteger(" + stripped + ")";
        }
        if (type.isTypeOfReal()) {
            return "toFloat(" + stripped + ")";
        }
        if (type.isTypeOfBoolean()) {
            return "(toLower(" + stripped + ") = 'true')";
        }
        return stripped;
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
        String itemAlias = "attrItem" + state.newVariableSuffix();
        String emptyCheck = raw + " IS NULL OR " + raw + " = 'Undefined' OR " + raw + " = 'COLLECTION_EMPTY'";
        String splitExpr = "split(" + raw + ", ' | ')";
        String itemValue = normalizeAttributeValue(itemAlias, elementType);
        return "CASE WHEN " + emptyCheck + " THEN [] ELSE [" + itemAlias + " IN " + splitExpr + " | " + itemValue + "] END";
    }

    private String nestedCollectionAttributeLookup(String sourceAlias, String attributeParam, Type type, RenderState state) {
        if (!(type instanceof CollectionType outerCollectionType)
                || !outerCollectionType.elemType().isKindOfCollection(Type.VoidHandling.EXCLUDE_VOID)) {
            throw new OclCodedUnsupportedOperationException(
                    OclDiagnosticCode.COLLECTION_VALUED_ATTRIBUTE_UNSUPPORTED,
                    "Nested collection-valued attribute metadata is invalid for Cypher rendering.");
        }
        String nestedAlias = "nestedAttr" + state.newVariableSuffix();
        String outerPattern = "(" + sourceAlias + ")-[:ObjectHasAttribute]->(val:AttributeValue) " +
                "WHERE val.attributeKey = $" + attributeParam + " " +
                "MATCH (val)-[outer:HasNestedCollectionValue]->(" + nestedAlias + ":NestedCollectionValue)";
        return "COLLECT { " + outerPattern +
                " RETURN " + renderNestedCollectionNode(nestedAlias, outerCollectionType.elemType(), state) +
                " ORDER BY outer.index }";
    }

    private String renderNestedCollectionNode(String nodeAlias, Type type, RenderState state) {
        if (type instanceof CollectionType collectionType) {
            String childAlias = "nestedAttr" + state.newVariableSuffix();
            return "COLLECT { MATCH (" + nodeAlias + ")-[edge:HasNestedCollectionValue]->(" + childAlias + ":NestedCollectionValue) " +
                    "RETURN " + renderNestedCollectionNode(childAlias, collectionType.elemType(), state) +
                    " ORDER BY edge.index }";
        }
        if (type.isKindOfClass(Type.VoidHandling.EXCLUDE_VOID)) {
            return "COLLECT { MATCH (" + nodeAlias + ")-[r:objectReference|HasReferenceValue]->(target) " +
                    "RETURN target ORDER BY r.index }";
        }
        String itemAlias = "nestedItem" + state.newVariableSuffix();
        String emptyCheck = nodeAlias + ".value IS NULL OR " + nodeAlias + ".value = 'Undefined' OR " +
                nodeAlias + ".value = 'COLLECTION_EMPTY'";
        String splitExpr = "split(" + nodeAlias + ".value, ' | ')";
        String itemValue = normalizeAttributeValue(itemAlias, type);
        return "CASE WHEN " + emptyCheck + " THEN [] ELSE [" + itemAlias + " IN " + splitExpr +
                " | " + itemValue + "] END";
    }

    private String attributeLookup(String sourceAlias, String attributeParam) {
        return "head([(" + sourceAlias + ")-[:ObjectHasAttribute]->(val:AttributeValue) " +
                "WHERE val.attributeKey = $" + attributeParam + " | val.value])";
    }

    private String referenceAttributeLookup(String sourceAlias, String attributeParam) {
        return "COLLECT { " +
                "MATCH (" + sourceAlias + ")-[:ObjectHasAttribute]->(val:AttributeValue) " +
                "WHERE val.attributeKey = $" + attributeParam + " " +
                "MATCH (val)-[r:objectReference|HasReferenceValue]->(target) " +
                "RETURN target ORDER BY r.index " +
                "}";
    }

    private String renderSingletonNodeList(String expression) {
        return "CASE WHEN " + expression + " IS NULL THEN [] ELSE [" + expression + "] END";
    }

    /**
     * Materializes the explicit collection view recorded by the semantic binder.
     * In the certified profile the only scalar source allowed here is a resolved
     * native to-one navigation, whose collection semantics is empty/singleton.
     */
    private RenderedExpression renderCollectionView(RenderedExpression source,
                                                     OclTypeBinding sourceCollectionType) {
        if (source.type().isCollection()) {
            return new RenderedExpression(source.cypher(), sourceCollectionType);
        }
        if (sourceCollectionType.isCollection()) {
            return new RenderedExpression(renderSingletonNodeList(source.cypher()), sourceCollectionType);
        }
        return source;
    }

    private String renderIteratorIsUnique(String iteratorName, String sourceCypher, String bodyCypher, RenderState state) {
        String projected = "[" + iteratorName + " IN " + sourceCypher + " | " + bodyCypher + "]";
        return "size(" + projected + ") = size(" + renderUniqueCollection(projected, state) + ")";
    }

    private String renderGuardedIsKindOfCheck(String sourceCypher, String classParam, RenderState state) {
        String receiverAlias = "typeRecv" + state.newVariableSuffix();
        String receiverKeyAlias = receiverAlias + "Key";
        String classAlias = "typeCls" + state.newVariableSuffix();
        return "EXISTS { WITH " + sourceCypher + ".objectKey AS " + receiverKeyAlias
                + " WHERE " + receiverKeyAlias + " IS NOT NULL"
                + " MATCH (" + receiverAlias + ":Object {objectKey: " + receiverKeyAlias + "})-[:"
                + CanonicalGraphVocabulary.OBJECT_INSTANCE_OF
                + "]->(" + classAlias + ":UmlClass {classKey: $" + classParam + "}) RETURN " + receiverAlias + " }";
    }

    private String renderGuardedCast(String sourceCypher, String classParam, RenderState state) {
        String receiverAlias = "castRecv" + state.newVariableSuffix();
        String receiverKeyAlias = receiverAlias + "Key";
        String classAlias = "castCls" + state.newVariableSuffix();
        return "head(COLLECT { WITH " + sourceCypher + ".objectKey AS " + receiverKeyAlias
                + " WHERE " + receiverKeyAlias + " IS NOT NULL"
                + " MATCH (" + receiverAlias + ":Object {objectKey: " + receiverKeyAlias + "})-[:"
                + CanonicalGraphVocabulary.OBJECT_INSTANCE_OF
                + "]->(" + classAlias + ":UmlClass {classKey: $" + classParam + "}) RETURN " + receiverAlias + " AS value })";
    }

    private String renderGuardedEntityValue(String sourceCypher, String receiverAlias,
                                            String entityValueCypher, RenderState state) {
        String receiverKeyAlias = receiverAlias + "Key";
        return "head(COLLECT { WITH " + sourceCypher + ".objectKey AS " + receiverKeyAlias
                + " WHERE " + receiverKeyAlias + " IS NOT NULL"
                + " MATCH (" + receiverAlias + ":Object {objectKey: " + receiverKeyAlias + "})"
                + " RETURN " + entityValueCypher + " AS value })";
    }

    private String classKey(String className) {
        return modelName == null || modelName.isBlank()
                ? className
                : CanonicalGraphEncoding.classKey(modelName, className);
    }

    private String attributeKey(OclCypherPlan.AttributeAccessPlan access) {
        String owner = access.attribute().owner().name();
        return modelName == null || modelName.isBlank()
                ? owner + "::" + access.attributeName()
                : CanonicalGraphEncoding.attributeKey(modelName, owner, access.attributeName());
    }

    private String associationKey(String associationName) {
        return modelName == null || modelName.isBlank()
                ? associationName
                : CanonicalGraphEncoding.associationKey(modelName, associationName);
    }

    private String renderIteratorSortedBy(String iteratorName, String sourceCypher, String bodyCypher, RenderState state) {
        String indexAlias = "sortIdx" + state.newVariableSuffix();
        String keyAlias = "sortKey" + state.newVariableSuffix();
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

    private String renderExtremumCollection(String operationName, String sourceCypher, RenderState state) {
        String itemAlias = "item" + state.newVariableSuffix();
        String bestAlias = "best" + state.newVariableSuffix();
        String comparator = "min".equals(operationName) ? "<" : ">";
        return "reduce(" + bestAlias + " = null, " + itemAlias + " IN " + sourceCypher +
                " | CASE WHEN " + bestAlias + " IS NULL OR " + itemAlias + " " + comparator + " " + bestAlias +
                " THEN " + itemAlias + " ELSE " + bestAlias + " END)";
    }

    private String renderUniqueCollection(String sourceCypher, RenderState state) {
        String itemAlias = "item" + state.newVariableSuffix();
        String accAlias = "acc" + state.newVariableSuffix();
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
    private String renderSemanticScalarEquality(String operator, String leftCypher, String rightCypher) {
        String equality = "(CASE WHEN (" + leftCypher + " IS NULL AND " + rightCypher
                + " IS NULL) THEN true WHEN (" + leftCypher + " IS NULL OR " + rightCypher
                + " IS NULL) THEN false ELSE coalesce(" + leftCypher + " = " + rightCypher
                + ", false) END)";
        return "<>".equals(operator) ? "(NOT " + equality + ")" : equality;
    }

    /** Extensional, order-independent equality for the certified finite-set fragment. */
    private String renderFiniteSetComparison(String operator, String leftCypher, String rightCypher,
                                             RenderState state) {
        String leftAlias = "setLeft" + state.newVariableSuffix();
        String rightAlias = "setRight" + state.newVariableSuffix();
        String left = "coalesce(" + leftCypher + ", [])";
        String right = "coalesce(" + rightCypher + ", [])";
        String equality = "(all(" + leftAlias + " IN " + left + " WHERE any(" + rightAlias + " IN " + right +
                " WHERE " + renderSetEquality(leftAlias, rightAlias, state) + ")) AND all(" + rightAlias +
                " IN " + right + " WHERE any(" + leftAlias + " IN " + left + " WHERE " +
                renderSetEquality(leftAlias, rightAlias, state) + ")))";
        return "<>".equals(operator) ? "(NOT " + equality + ")" : equality;
    }

    public record RenderedInvariant(String cypher, Map<String, Object> parameters) {
    }

    public record RenderedTopLevelExpression(String cypher, Map<String, Object> parameters) {
    }

    private record RenderedExpression(String cypher, OclTypeBinding type) {
    }

    private static final class RenderState {
        private final Map<String, Object> parameters = new LinkedHashMap<>();
        private final Deque<Map<String, OclTypeBinding>> scopes = new ArrayDeque<>();
        private final Deque<Map<String, String>> expressionBindings = new ArrayDeque<>();
        private int parameterCounter = 0;
        private int variableCounter = 0;
        private int relationshipCounter = 0;
        private String bottomTokenParam;

        private RenderState() {
            scopes.push(new LinkedHashMap<>());
            expressionBindings.push(new LinkedHashMap<>());
        }

        private String newParam(Object value) {
            String name = "p" + (++parameterCounter);
            parameters.put(name, value);
            return name;
        }

        private String newVariableSuffix() {
            return String.valueOf(++variableCounter);
        }

        private String newRelationshipAlias() {
            relationshipCounter++;
            return relationshipCounter == 1 ? "r" : "r" + relationshipCounter;
        }

        private String bottomTokenParam() {
            if (bottomTokenParam == null) {
                bottomTokenParam = newParam(OclBottomToken.value());
            }
            return bottomTokenParam;
        }

        private void enterVariable(String name, OclTypeBinding binding) {
            Map<String, OclTypeBinding> nextScope = new LinkedHashMap<>(scopes.peek());
            nextScope.put(name, binding);
            scopes.push(nextScope);
            expressionBindings.push(new LinkedHashMap<>(expressionBindings.peek()));
        }

        private void exitVariable() {
            scopes.pop();
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

        private Map<String, Object> parameters() {
            return Map.copyOf(parameters);
        }
    }
}
