package org.uet.dse.neo4jtgg.ocl.ir;

import org.uet.dse.neo4jtgg.ocl.OclTypeBinding;
import org.uet.dse.neo4jtgg.ocl.diagnostic.OclCodedUnsupportedOperationException;
import org.uet.dse.neo4jtgg.ocl.diagnostic.OclDiagnosticCode;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

public class OclCypherRenderer {
    public RenderedInvariant renderInvariant(OclCypherPlan.InvariantPlan invariantPlan) {
        RenderState state = new RenderState();
        state.enterVariable("self", OclTypeBinding.node(invariantPlan.contextClassName()));
        RenderedExpression predicate = renderExpression(invariantPlan.predicate(), state);
        String classParam = state.newParam(invariantPlan.contextClassName());
        String cypher = "MATCH (self)-[:ObjectInstanceOf]->(cls {name: $" + classParam + "})\n" +
                "WHERE NOT coalesce(" + predicate.cypher() + ", false)\n" +
                "RETURN self.use_id AS useId";
        return new RenderedInvariant(cypher, state.parameters());
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
            return new RenderedExpression("(NOT " + inner.cypher() + ")", not.type());
        }
        if (expression instanceof OclCypherPlan.IfPlan ifPlan) {
            RenderedExpression condition = renderExpression(ifPlan.condition(), state);
            RenderedExpression thenBranch = renderExpression(ifPlan.thenBranch(), state);
            RenderedExpression elseBranch = renderExpression(ifPlan.elseBranch(), state);
            return new RenderedExpression(
                    "(CASE WHEN " + condition.cypher() + " THEN " + thenBranch.cypher() +
                            " ELSE " + elseBranch.cypher() + " END)",
                    ifPlan.type());
        }
        if (expression instanceof OclCypherPlan.LetPlan letPlan) {
            RenderedExpression value = renderExpression(letPlan.value(), state);
            String letAlias = "_let" + state.newVariableSuffix();
            String loopAlias = "_letKeep" + state.newVariableSuffix();
            state.enterExpressionBinding(letPlan.variableName(), letAlias);
            RenderedExpression body = renderExpression(letPlan.body(), state);
            state.exitExpressionBinding();
            return new RenderedExpression(
                    "reduce(" + letAlias + " = (" + value.cypher() + "), " + loopAlias + " IN [1] | " + body.cypher() + ")",
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
                case "implies" -> "IMPLIES";
                case ">", "<", ">=", "<=", "+", "-", "*", "/" -> binary.operator();
                default -> throw new OclCodedUnsupportedOperationException(
                        OclDiagnosticCode.UNSUPPORTED_OPERATOR,
                        "Unsupported operator: " + binary.operator());
            };
            String rendered = "IMPLIES".equals(operator)
                    ? "((NOT " + left.cypher() + ") OR " + right.cypher() + ")"
                    : "(" + left.cypher() + " " + operator + " " + right.cypher() + ")";
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
            RenderedExpression source = renderExpression(collectionOperation.source(), state);
            return renderCollectionOperation(collectionOperation, source, state);
        }
        if (expression instanceof OclCypherPlan.IteratorOperationPlan iteratorOperation) {
            if (iteratorOperation.source() instanceof OclCypherPlan.NavigationAccessPlan navigationAccess) {
                return renderNavigationIterator(iteratorOperation, navigationAccess, state);
            }
            RenderedExpression source = renderExpression(iteratorOperation.source(), state);
            state.enterVariable(iteratorOperation.iteratorName(), source.type().elementType());
            RenderedExpression body = renderExpression(iteratorOperation.body(), state);
            state.exitVariable();

            String rendered = switch (iteratorOperation.operationName().toLowerCase()) {
                case "select" -> "[" + iteratorOperation.iteratorName() + " IN " + source.cypher() + " WHERE " + body.cypher() + "]";
                case "reject" -> "[" + iteratorOperation.iteratorName() + " IN " + source.cypher() + " WHERE NOT (" + body.cypher() + ")]";
                case "exists" -> "any(" + iteratorOperation.iteratorName() + " IN " + source.cypher() + " WHERE " + body.cypher() + ")";
                case "forall" -> "all(" + iteratorOperation.iteratorName() + " IN " + source.cypher() + " WHERE " + body.cypher() + ")";
                case "one" -> "single(" + iteratorOperation.iteratorName() + " IN " + source.cypher() + " WHERE " + body.cypher() + ")";
                case "any" -> "head([" + iteratorOperation.iteratorName() + " IN " + source.cypher() + " WHERE " + body.cypher() + " | " + iteratorOperation.iteratorName() + "])";
                case "collect" -> "[" + iteratorOperation.iteratorName() + " IN " + source.cypher() + " | " + body.cypher() + "]";
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
        throw new OclCodedUnsupportedOperationException(
                OclDiagnosticCode.UNSUPPORTED_IR_EXPRESSION,
                "Unsupported IR expression: " + expression.getClass().getSimpleName());
    }

    private RenderedExpression renderNavigationIterator(OclCypherPlan.IteratorOperationPlan iteratorOperation,
                                                        OclCypherPlan.NavigationAccessPlan navigationAccess,
                                                        RenderState state) {
        String operation = iteratorOperation.operationName().toLowerCase();
        if (!"exists".equals(operation) && !"forall".equals(operation)) {
            RenderedExpression source = renderExpression(iteratorOperation.source(), state);
            state.enterVariable(iteratorOperation.iteratorName(), source.type().elementType());
            RenderedExpression body = renderExpression(iteratorOperation.body(), state);
            state.exitVariable();
            String rendered = switch (operation) {
                case "select" -> "[" + iteratorOperation.iteratorName() + " IN " + source.cypher() + " WHERE " + body.cypher() + "]";
                case "reject" -> "[" + iteratorOperation.iteratorName() + " IN " + source.cypher() + " WHERE NOT (" + body.cypher() + ")]";
                case "collect" -> "[" + iteratorOperation.iteratorName() + " IN " + source.cypher() + " | " + body.cypher() + "]";
                case "any" -> "head([" + iteratorOperation.iteratorName() + " IN " + source.cypher() + " WHERE " + body.cypher() + " | " + iteratorOperation.iteratorName() + "])";
                case "one" -> "single(" + iteratorOperation.iteratorName() + " IN " + source.cypher() + " WHERE " + body.cypher() + ")";
                default -> throw new OclCodedUnsupportedOperationException(
                        OclDiagnosticCode.UNSUPPORTED_ITERATOR,
                        "Unsupported iterator: " + iteratorOperation.operationName());
            };
            return new RenderedExpression(rendered, iteratorOperation.type());
        }

        OclCypherPlan.NavigationMatchPlan matchPlan = new OclCypherPlan.NavigationMatchPlan(
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
            String classParam = state.newParam(source.type().typeName());
            String cypher = "[(obj)-[:ObjectInstanceOf]->(cls {name: $" + classParam + "}) | obj]";
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
            if (methodCall.arguments().size() != 1) {
                throw new OclCodedUnsupportedOperationException(
                        OclDiagnosticCode.INVALID_METHOD_ARGUMENT,
                        "oclIsTypeOf() requires a type argument.");
            }
            RenderedExpression typeArg = renderExpression(methodCall.arguments().get(0), state);
            String classParam = state.newParam(typeArg.type().typeName());
            return new RenderedExpression(
                    "EXISTS { MATCH (" + source.cypher() + ")-[:ObjectInstanceOf]->(cls {name: $" + classParam + "}) }",
                    methodCall.type());
        }
        if ("oclIsKindOf".equalsIgnoreCase(methodCall.methodName())) {
            if (methodCall.arguments().size() != 1) {
                throw new OclCodedUnsupportedOperationException(
                        OclDiagnosticCode.INVALID_METHOD_ARGUMENT,
                        "oclIsKindOf() requires a type argument.");
            }
            RenderedExpression typeArg = renderExpression(methodCall.arguments().get(0), state);
            String classParam = state.newParam(typeArg.type().typeName());
            // oclIsKindOf checks type OR any subtype → same pattern but with inheritance
            return new RenderedExpression(
                    "EXISTS { MATCH (" + source.cypher() + ")-[:ObjectInstanceOf]->(cls) WHERE cls.name = $" + classParam + " OR cls.name IN [(" + source.cypher() + ")-[:ObjectInstanceOf]->(c)-[:IsSubclassOf*0..]->(parent {name: $" + classParam + "}) | c.name] }",
                    methodCall.type());
        }
        throw new OclCodedUnsupportedOperationException(
                OclDiagnosticCode.UNSUPPORTED_METHOD_CALL,
                "Unsupported method call: " + methodCall.methodName());
    }

    private RenderedExpression renderAttributeAccess(OclCypherPlan.AttributeAccessPlan attributeAccess,
                                                     RenderedExpression source,
                                                     RenderState state) {
        String suffixParam = state.newParam("_" + attributeAccess.attributeName());
        if (!source.type().isCollection() && isSimpleIdentifier(source.cypher())) {
            String raw = attributeLookup(source.cypher(), suffixParam);
            return new RenderedExpression(normalizeAttributeValue(raw, attributeAccess.attributeType()), attributeAccess.type());
        }
        String itemAlias = "attrOwner" + state.newVariableSuffix();
        String mappedValue = normalizeAttributeValue(attributeLookup(itemAlias, suffixParam), attributeAccess.attributeType());

        if (source.type().isCollection()) {
            String cypher = "[" + itemAlias + " IN " + source.cypher() + " | " + mappedValue + "]";
            return new RenderedExpression(cypher, attributeAccess.type());
        }

        String cypher = "head([" + itemAlias + " IN " + renderSingletonNodeList(source.cypher()) + " | " + mappedValue + "])";
        return new RenderedExpression(cypher, attributeAccess.type());
    }

    private RenderedExpression renderNavigationAccess(OclCypherPlan.NavigationAccessPlan navigationAccess,
                                                      RenderedExpression source,
                                                      RenderState state) {
        if (!source.type().isCollection() && isSimpleIdentifier(source.cypher())) {
            String listExpr = "[" + renderNavigationPattern(source.cypher(), "t", navigationAccess.navigation(), state) + " | t]";
            String cypher = navigationAccess.type().isCollection() ? listExpr : "head(" + listExpr + ")";
            return new RenderedExpression(cypher, navigationAccess.type());
        }

        String ownerAlias = "navOwner" + state.newVariableSuffix();
        String targetAlias = "navTarget" + state.newVariableSuffix();

        if (source.type().isCollection()) {
            String perOwnerTargets = "[" + renderNavigationPattern(ownerAlias, targetAlias, navigationAccess.navigation(), state) + " | " + targetAlias + "]";
            String accAlias = "navAcc" + state.newVariableSuffix();
            String flattened = "reduce(" + accAlias + " = [], " + ownerAlias + " IN " + source.cypher()
                    + " | " + accAlias + " + " + perOwnerTargets + ")";
            String cypher = navigationAccess.type().isCollection() ? flattened : "head(" + flattened + ")";
            return new RenderedExpression(cypher, navigationAccess.type());
        }

        String perOwnerTargets = "[" + renderNavigationPattern(ownerAlias, targetAlias, navigationAccess.navigation(), state) + " | " + targetAlias + "]";
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
                        " WHERE " + alias + " = " + candidate.cypher() + "])", collectionOperation.type());
            }
            case "isEmpty" -> new RenderedExpression("size(" + source.cypher() + ") = 0", collectionOperation.type());
            case "notEmpty" -> new RenderedExpression("size(" + source.cypher() + ") > 0", collectionOperation.type());
            case "includes" -> {
                if (collectionOperation.arguments().size() != 1) {
                    throw new OclCodedUnsupportedOperationException(
                            OclDiagnosticCode.INVALID_COLLECTION_ARGUMENT,
                            "includes() requires a single argument.");
                }
                RenderedExpression candidate = renderExpression(collectionOperation.arguments().get(0), state);
                String alias = "item" + state.newVariableSuffix();
                yield new RenderedExpression("any(" + alias + " IN " + source.cypher() +
                        " WHERE " + alias + " = " + candidate.cypher() + ")", collectionOperation.type());
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
                        " WHERE " + alias + " = " + candidate.cypher() + ")", collectionOperation.type());
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
                        " WHERE " + innerAlias + " = " + outerAlias + "))", collectionOperation.type());
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
                        " WHERE " + innerAlias + " = " + outerAlias + "))", collectionOperation.type());
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
                String filtered = "[" + itemAlias + " IN " + source.cypher() +
                        " WHERE any(" + candidateAlias + " IN " + candidates.cypher() +
                        " WHERE " + candidateAlias + " = " + itemAlias + ")]";
                String cypher = collectionOperation.type().isUniqueCollection()
                        ? renderUniqueCollection(filtered, state)
                        : filtered;
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

    private String renderNavigationMatch(OclCypherPlan.NavigationMatchPlan matchPlan, RenderState state) {
        RenderedExpression owner = renderExpression(matchPlan.owner(), state);
        if (!owner.type().isCollection() && isSimpleIdentifier(owner.cypher())) {
            return "MATCH " + renderNavigationPattern(owner.cypher(), matchPlan.targetAlias(), matchPlan.navigation().navigation(), state);
        }
        String ownerAlias = "navOwner" + state.newVariableSuffix();
        String ownerSource = owner.type().isCollection() ? owner.cypher() : renderSingletonNodeList(owner.cypher());
        return "UNWIND " + ownerSource + " AS " + ownerAlias + "\nMATCH "
                + renderNavigationPattern(ownerAlias, matchPlan.targetAlias(), matchPlan.navigation().navigation(), state);
    }

    private String renderNavigationPattern(String sourceAlias, String targetAlias,
                                           org.uet.dse.neo4jtgg.ocl.OclMetamodelIndex.NavigationInfo navigationInfo,
                                           RenderState state) {
        String associationParam = state.newParam(navigationInfo.associationName());
        String sourceRoleParam = state.newParam(navigationInfo.sourceRoleName());
        String targetRoleParam = state.newParam(navigationInfo.targetRoleName());
        return switch (navigationInfo.direction()) {
            case OUTGOING -> "(" + sourceAlias + ")-[r]->(" + targetAlias + ") WHERE type(r) STARTS WITH 'Link' " +
                    "AND r.name = $" + associationParam +
                    " AND r.sourceRole = $" + sourceRoleParam +
                    " AND r.targetRole = $" + targetRoleParam;
            case INCOMING -> "(" + sourceAlias + ")<-[r]-(" + targetAlias + ") WHERE type(r) STARTS WITH 'Link' " +
                    "AND r.name = $" + associationParam +
                    " AND r.sourceRole = $" + targetRoleParam +
                    " AND r.targetRole = $" + sourceRoleParam;
            case UNDIRECTED -> "(" + sourceAlias + ")-[r]-(" + targetAlias + ") WHERE type(r) STARTS WITH 'Link' " +
                    "AND r.name = $" + associationParam +
                    " AND ((r.sourceRole = $" + sourceRoleParam + " AND r.targetRole = $" + targetRoleParam + ")" +
                    " OR (r.sourceRole = $" + targetRoleParam + " AND r.targetRole = $" + sourceRoleParam + "))";
        };
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
            case NORMAL -> " AND " + body.cypher();
            case NEGATED -> " AND NOT (" + body.cypher() + ")";
        };
    }

    private String normalizeAttributeValue(String raw, org.tzi.use.uml.ocl.type.Type type) {
        String stripped = "replace(coalesce(" + raw + ", ''), \"'\", \"\")";
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

    private String attributeLookup(String sourceAlias, String suffixParam) {
        return "head([(" + sourceAlias + ")-[:ObjectHasAttribute]->(val:AttributeValue) " +
                "WHERE val.name ENDS WITH $" + suffixParam + " | val.value])";
    }

    private String renderSingletonNodeList(String expression) {
        return "CASE WHEN " + expression + " IS NULL THEN [] ELSE [" + expression + "] END";
    }

    private boolean isSimpleIdentifier(String expression) {
        return expression != null && expression.matches("[A-Za-z_][A-Za-z0-9_]*");
    }

    private String renderUniqueCollection(String sourceCypher, RenderState state) {
        String itemAlias = "item" + state.newVariableSuffix();
        String accAlias = "acc" + state.newVariableSuffix();
        return "reduce(" + accAlias + " = [], " + itemAlias + " IN " + sourceCypher +
                " | CASE WHEN any(existing IN " + accAlias + " WHERE existing = " + itemAlias + ")" +
                " THEN " + accAlias + " ELSE " + accAlias + " + " + itemAlias + " END)";
    }

    public record RenderedInvariant(String cypher, Map<String, Object> parameters) {
    }

    private record RenderedExpression(String cypher, OclTypeBinding type) {
    }

    private static final class RenderState {
        private final Map<String, Object> parameters = new LinkedHashMap<>();
        private final Deque<Map<String, OclTypeBinding>> scopes = new ArrayDeque<>();
        private final Deque<Map<String, String>> expressionBindings = new ArrayDeque<>();
        private int parameterCounter = 0;
        private int variableCounter = 0;

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
