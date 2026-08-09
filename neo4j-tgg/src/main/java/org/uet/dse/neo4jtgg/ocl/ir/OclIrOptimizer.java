package org.uet.dse.neo4jtgg.ocl.ir;

import org.uet.dse.neo4jtgg.ocl.OclTypeBinding;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Semantics-preserving model-to-model transformation from
 * {@link OclSemanticIr} to {@link OclOptimizedIr}.
 *
 * <p>The optimizer rewrites IR expressions into graph-friendly semantic forms
 * without changing their denotation. It is intentionally placed before the
 * Cypher planner so that optimizations such as navigation existence checks,
 * count comparisons, let inlining, and constant folding are expressed at the
 * IR level rather than hidden inside string rendering.</p>
 *
 * <pre>
 * T_OPT : M_SemanticIR -> M_OptimizedIR
 * [[ ir ]]_IR(M, rho) = [[ T_OPT(ir) ]]_OPT(M, rho)
 * </pre>
 */
public class OclIrOptimizer {
    public OclIr.InvariantQuery optimizeInvariant(OclIr.InvariantQuery invariantQuery) {
        return new OclIr.InvariantQuery(
                invariantQuery.contextClassName(),
                invariantQuery.invariantName(),
                optimizeExpression(OclSemanticIr.requireSemantic(invariantQuery.predicate())));
    }

    public OclIr.OptimizedExpression optimizeExpression(OclIr.SemanticExpression expression) {
        return OclOptimizedIr.requireOptimized(optimizeExpression((OclIr.Expression) expression, Map.of()));
    }

    public OclIr.OptimizedExpression optimizeExpression(OclIr.Expression expression) {
        return OclOptimizedIr.requireOptimized(optimizeExpression(expression, Map.of()));
    }

    private OclIr.Expression optimizeExpression(OclIr.Expression expression, Map<String, OclIr.Expression> bindings) {
        if (expression instanceof OclIr.Variable variable) {
            return bindings.getOrDefault(variable.name(), variable);
        }
        if (expression instanceof OclIr.SetLiteral setLiteral) {
            return new OclIr.SetLiteral(
                    setLiteral.elements().stream().map(element -> optimizeExpression(element, bindings)).toList(),
                    setLiteral.type());
        }
        if (expression instanceof OclIr.Not not) {
            OclIr.Expression inner = optimizeExpression(not.expression(), bindings);
            if (inner instanceof OclIr.Literal literal && literal.value() instanceof Boolean bool) {
                return new OclIr.Literal(!bool, not.type());
            }
            return new OclIr.Not(inner, not.type());
        }
        if (expression instanceof OclIr.If ifExpression) {
            OclIr.Expression condition = optimizeExpression(ifExpression.condition(), bindings);
            OclIr.Expression thenBranch = optimizeExpression(ifExpression.thenBranch(), bindings);
            OclIr.Expression elseBranch = optimizeExpression(ifExpression.elseBranch(), bindings);
            Boolean foldedCondition = extractIfConditionTruth(condition);
            if (foldedCondition != null) {
                return foldedCondition ? thenBranch : elseBranch;
            }
            if (thenBranch.equals(elseBranch)) {
                return thenBranch;
            }
            return new OclIr.If(condition, thenBranch, elseBranch, ifExpression.type());
        }
        if (expression instanceof OclIr.Let letExpression) {
            OclIr.Expression optimizedValue = optimizeExpression(letExpression.value(), bindings);
            int uses = countVariableUses(letExpression.body(), letExpression.variableName());
            if (uses == 0) {
                return optimizeExpression(letExpression.body(), bindings);
            }
            if (uses == 1 || isCheap(optimizedValue)) {
                Map<String, OclIr.Expression> childBindings = new LinkedHashMap<>(bindings);
                childBindings.put(letExpression.variableName(), optimizedValue);
                return optimizeExpression(letExpression.body(), childBindings);
            }
            return new OclIr.Let(
                    letExpression.variableName(),
                    optimizedValue,
                    optimizeExpression(letExpression.body(), bindings),
                    letExpression.type());
        }
        if (expression instanceof OclIr.Binary binary) {
            OclIr.Expression left = optimizeExpression(binary.left(), bindings);
            OclIr.Expression right = optimizeExpression(binary.right(), bindings);
            OclIr.Expression optimized = optimizeSizeComparison(binary.operator(), left, right, binary.type());
            if (optimized != null) {
                return optimized;
            }
            OclIr.Expression folded = foldLiteralBinary(binary.operator(), left, right, binary.type());
            if (folded != null) {
                return folded;
            }
            if ("implies".equals(binary.operator())) {
                return new OclIr.Binary(
                        "or",
                        new OclIr.Not(left, binary.type()),
                        right,
                        binary.type());
            }
            if ("xor".equals(binary.operator())) {
                return new OclIr.Binary(
                        "or",
                        new OclIr.Binary("and", left, new OclIr.Not(right, binary.type()), binary.type()),
                        new OclIr.Binary("and", new OclIr.Not(left, binary.type()), right, binary.type()),
                        binary.type());
            }
            return new OclIr.Binary(binary.operator(), left, right, binary.type());
        }
        if (expression instanceof OclIr.AttributeAccess attributeAccess) {
            return new OclIr.AttributeAccess(
                    optimizeExpression(attributeAccess.source(), bindings),
                    attributeAccess.attributeName(),
                    attributeAccess.attributeType(),
                    attributeAccess.type(),
                    attributeAccess.attribute());
        }
        if (expression instanceof OclIr.NavigationAccess navigationAccess) {
            return new OclIr.NavigationAccess(
                    optimizeExpression(navigationAccess.source(), bindings),
                    navigationAccess.navigation(),
                    navigationAccess.qualifiers().stream().map(argument -> optimizeExpression(argument, bindings)).toList(),
                    navigationAccess.type());
        }
        if (expression instanceof OclIr.MethodCall methodCall) {
            return new OclIr.MethodCall(
                    optimizeExpression(methodCall.source(), bindings),
                    methodCall.methodName(),
                    methodCall.arguments().stream().map(argument -> optimizeExpression(argument, bindings)).toList(),
                    methodCall.type());
        }
        if (expression instanceof OclIr.CollectionOperation collectionOperation) {
            OclIr.Expression optimizedSource = optimizeExpression(collectionOperation.source(), bindings);
            OclIr.Expression optimized = optimizeCollectionOperation(collectionOperation.operationName(), optimizedSource, collectionOperation.type());
            return optimized != null ? optimized : new OclIr.CollectionOperation(
                    optimizedSource,
                    collectionOperation.sourceCollectionType(),
                    collectionOperation.operationName(),
                    collectionOperation.arguments().stream().map(argument -> optimizeExpression(argument, bindings)).toList(),
                    collectionOperation.type());
        }
        if (expression instanceof OclIr.IteratorOperation iteratorOperation) {
            OclIr.Expression optimizedSource = optimizeExpression(iteratorOperation.source(), bindings);
            Map<String, OclIr.Expression> childBindings = new LinkedHashMap<>(bindings);
            childBindings.remove(iteratorOperation.iteratorName());
            OclIr.Expression optimizedBody = optimizeExpression(iteratorOperation.body(), childBindings);
            OclIr.Expression optimized = optimizeIterator(iteratorOperation.operationName(), optimizedSource,
                    iteratorOperation.iteratorName(), optimizedBody, iteratorOperation.type());
            return optimized != null ? optimized : new OclIr.IteratorOperation(
                    optimizedSource, iteratorOperation.sourceCollectionType(), iteratorOperation.operationName(),
                    iteratorOperation.iteratorName(), optimizedBody, iteratorOperation.type());
        }
        return expression;
    }

    private Boolean extractIfConditionTruth(OclIr.Expression expression) {
        if (expression instanceof OclIr.Literal literal && literal.value() instanceof Boolean bool) {
            return bool;
        }
        return null;
    }

    private OclIr.Expression foldLiteralBinary(String operator, OclIr.Expression left, OclIr.Expression right,
                                               OclTypeBinding type) {
        if (!(left instanceof OclIr.Literal leftLiteral) || !(right instanceof OclIr.Literal rightLiteral)) {
            return null;
        }

        Object leftValue = leftLiteral.value();
        Object rightValue = rightLiteral.value();
        return switch (operator) {
            case "=" -> new OclIr.Literal(Objects.equals(normalizeNumber(leftValue), normalizeNumber(rightValue)), type);
            case "<>" -> new OclIr.Literal(!Objects.equals(normalizeNumber(leftValue), normalizeNumber(rightValue)), type);
            case "and" -> new OclIr.Literal(toBoolean(leftValue) && toBoolean(rightValue), type);
            case "or" -> new OclIr.Literal(toBoolean(leftValue) || toBoolean(rightValue), type);
            case "xor" -> new OclIr.Literal(toBoolean(leftValue) ^ toBoolean(rightValue), type);
            case "implies" -> new OclIr.Literal(!toBoolean(leftValue) || toBoolean(rightValue), type);
            case ">", "<", ">=", "<=" -> foldComparison(operator, leftValue, rightValue, type);
            case "+", "-", "*", "/" -> foldArithmetic(operator, leftValue, rightValue, type);
            default -> null;
        };
    }

    private OclIr.Expression foldComparison(String operator, Object leftValue, Object rightValue, OclTypeBinding type) {
        if (!(leftValue instanceof Number leftNumber) || !(rightValue instanceof Number rightNumber)) {
            return null;
        }
        double left = leftNumber.doubleValue();
        double right = rightNumber.doubleValue();
        boolean result = switch (operator) {
            case ">" -> left > right;
            case "<" -> left < right;
            case ">=" -> left >= right;
            case "<=" -> left <= right;
            default -> false;
        };
        return new OclIr.Literal(result, type);
    }

    private OclIr.Expression foldArithmetic(String operator, Object leftValue, Object rightValue, OclTypeBinding type) {
        if (!(leftValue instanceof Number leftNumber) || !(rightValue instanceof Number rightNumber)) {
            return null;
        }
        if ("/".equals(operator) && rightNumber.doubleValue() == 0d) {
            return null;
        }
        double left = leftNumber.doubleValue();
        double right = rightNumber.doubleValue();
        double result = switch (operator) {
            case "+" -> left + right;
            case "-" -> left - right;
            case "*" -> left * right;
            case "/" -> left / right;
            default -> 0d;
        };
        if ("Integer".equals(type.typeName()) && Math.rint(result) == result) {
            return new OclIr.Literal((long) result, type);
        }
        return new OclIr.Literal(result, type);
    }

    private boolean toBoolean(Object value) {
        return value instanceof Boolean bool && bool;
    }

    private Object normalizeNumber(Object value) {
        return value instanceof Number number ? number.doubleValue() : value;
    }

    private boolean isCheap(OclIr.Expression expression) {
        return expression instanceof OclIr.Literal || expression instanceof OclIr.Variable;
    }

    private int countVariableUses(OclIr.Expression expression, String variableName) {
        if (expression instanceof OclIr.Variable variable) {
            return variableName.equals(variable.name()) ? 1 : 0;
        }
        if (expression instanceof OclIr.Literal) {
            return 0;
        }
        if (expression instanceof OclIr.SetLiteral setLiteral) {
            return setLiteral.elements().stream()
                    .mapToInt(element -> countVariableUses(element, variableName))
                    .sum();
        }
        if (expression instanceof OclIr.Not not) {
            return countVariableUses(not.expression(), variableName);
        }
        if (expression instanceof OclIr.If ifExpression) {
            return countVariableUses(ifExpression.condition(), variableName)
                    + countVariableUses(ifExpression.thenBranch(), variableName)
                    + countVariableUses(ifExpression.elseBranch(), variableName);
        }
        if (expression instanceof OclIr.Let letExpression) {
            int valueUses = countVariableUses(letExpression.value(), variableName);
            if (variableName.equals(letExpression.variableName())) {
                return valueUses;
            }
            return valueUses + countVariableUses(letExpression.body(), variableName);
        }
        if (expression instanceof OclIr.Binary binary) {
            return countVariableUses(binary.left(), variableName) + countVariableUses(binary.right(), variableName);
        }
        if (expression instanceof OclIr.AttributeAccess attributeAccess) {
            return countVariableUses(attributeAccess.source(), variableName);
        }
        if (expression instanceof OclIr.NavigationAccess navigationAccess) {
            int result = countVariableUses(navigationAccess.source(), variableName);
            for (OclIr.Expression qualifier : navigationAccess.qualifiers()) {
                result += countVariableUses(qualifier, variableName);
            }
            return result;
        }
        if (expression instanceof OclIr.MethodCall methodCall) {
            int result = countVariableUses(methodCall.source(), variableName);
            for (OclIr.Expression argument : methodCall.arguments()) {
                result += countVariableUses(argument, variableName);
            }
            return result;
        }
        if (expression instanceof OclIr.CollectionOperation collectionOperation) {
            int result = countVariableUses(collectionOperation.source(), variableName);
            for (OclIr.Expression argument : collectionOperation.arguments()) {
                result += countVariableUses(argument, variableName);
            }
            return result;
        }
        if (expression instanceof OclIr.IteratorOperation iteratorOperation) {
            int result = countVariableUses(iteratorOperation.source(), variableName);
            if (!variableName.equals(iteratorOperation.iteratorName())) {
                result += countVariableUses(iteratorOperation.body(), variableName);
            }
            return result;
        }
        if (expression instanceof OclIr.NavigationPredicateCheck predicateCheck) {
            int result = countVariableUses(predicateCheck.navigation(), variableName);
            if (predicateCheck.predicate() != null && !variableName.equals(predicateCheck.iteratorName())) {
                result += countVariableUses(predicateCheck.predicate(), variableName);
            }
            return result;
        }
        if (expression instanceof OclIr.NavigationCountComparison countComparison) {
            int result = countVariableUses(countComparison.navigation(), variableName);
            if (countComparison.predicate() != null && !variableName.equals(countComparison.iteratorName())) {
                result += countVariableUses(countComparison.predicate(), variableName);
            }
            return result;
        }
        if (expression instanceof OclIr.NavigationAggregation aggregation) {
            int result = countVariableUses(aggregation.navigation(), variableName);
            if (aggregation.predicate() != null && !variableName.equals(aggregation.iteratorName())) {
                result += countVariableUses(aggregation.predicate(), variableName);
            }
            if (!variableName.equals(aggregation.iteratorName())) {
                result += countVariableUses(aggregation.projection(), variableName);
            }
            return result;
        }
        if (expression instanceof OclIr.NavigationUniquenessCheck uniquenessCheck) {
            int result = countVariableUses(uniquenessCheck.navigation(), variableName);
            if (uniquenessCheck.predicate() != null && !variableName.equals(uniquenessCheck.iteratorName())) {
                result += countVariableUses(uniquenessCheck.predicate(), variableName);
            }
            if (!variableName.equals(uniquenessCheck.iteratorName())) {
                result += countVariableUses(uniquenessCheck.projection(), variableName);
            }
            return result;
        }
        return 0;
    }

    private OclIr.Expression optimizeIterator(String operation, OclIr.Expression source, String iteratorName,
                                              OclIr.Expression body, OclTypeBinding type) {
        if (source instanceof OclIr.NavigationAccess navigationAccess) {
            return switch (operation.toLowerCase()) {
                case "exists" -> OclOptimizedIr.navigationPredicateCheck(navigationAccess, iteratorName, body,
                        OclIr.NavigationPredicateKind.EXISTS, type);
                case "forall" -> OclOptimizedIr.navigationPredicateCheck(navigationAccess, iteratorName, body,
                        OclIr.NavigationPredicateKind.FORALL, type);
                case "one" -> OclOptimizedIr.navigationCountComparison(navigationAccess, iteratorName, body,
                        "=", 1L, type);
                case "isunique" -> OclOptimizedIr.navigationUniquenessCheck(navigationAccess, iteratorName, null, body, type);
                default -> null;
            };
        }
        NavigationFilterSource filterSource = extractNavigationFilterSource(source, iteratorName);
        if (filterSource != null) {
            OclIr.Expression combinedPredicate = combinePredicates(filterSource.predicate(), body);
            return switch (operation.toLowerCase()) {
                case "exists" -> OclOptimizedIr.navigationPredicateCheck(filterSource.navigationAccess(), iteratorName, combinedPredicate,
                        OclIr.NavigationPredicateKind.EXISTS, type);
                case "one" -> OclOptimizedIr.navigationCountComparison(filterSource.navigationAccess(), iteratorName, combinedPredicate,
                        "=", 1L, type);
                case "isunique" -> OclOptimizedIr.navigationUniquenessCheck(filterSource.navigationAccess(), iteratorName,
                        filterSource.predicate(), body, type);
                default -> null;
            };
        }
        return null;
    }

    private OclIr.Expression optimizeCollectionOperation(String operation, OclIr.Expression source, OclTypeBinding type) {
        NavigationAggregationSource aggregationSource = extractNavigationAggregationSource(source);
        if (aggregationSource != null && isNavigationAggregateOperation(operation)) {
            return OclOptimizedIr.navigationAggregation(
                    aggregationSource.navigationAccess(),
                    aggregationSource.iteratorName(),
                    aggregationSource.predicate(),
                    aggregationSource.projection(),
                    operation,
                    type);
        }
        FlattenExistenceSource flattenExistenceSource = extractFlattenExistenceSource(source);
        if (flattenExistenceSource != null) {
            return switch (operation) {
                case "isEmpty" -> OclOptimizedIr.navigationPredicateCheck(
                        flattenExistenceSource.navigationAccess(),
                        flattenExistenceSource.iteratorName(),
                        flattenExistenceSource.nestedNotEmptyPredicate(),
                        OclIr.NavigationPredicateKind.NOT_EXISTS,
                        type);
                case "notEmpty" -> OclOptimizedIr.navigationPredicateCheck(
                        flattenExistenceSource.navigationAccess(),
                        flattenExistenceSource.iteratorName(),
                        flattenExistenceSource.nestedNotEmptyPredicate(),
                        OclIr.NavigationPredicateKind.EXISTS,
                        type);
                default -> null;
            };
        }
        if (source instanceof OclIr.NavigationAccess navigationAccess) {
            return switch (operation) {
                case "isEmpty" -> OclOptimizedIr.navigationPredicateCheck(navigationAccess, "nav", null,
                        OclIr.NavigationPredicateKind.NOT_EXISTS, type);
                case "notEmpty" -> OclOptimizedIr.navigationPredicateCheck(navigationAccess, "nav", null,
                        OclIr.NavigationPredicateKind.EXISTS, type);
                default -> null;
            };
        }
        NavigationFilterSource filterSource = extractNavigationFilterSource(source);
        if (filterSource != null) {
            return switch (operation) {
                case "isEmpty" -> OclOptimizedIr.navigationPredicateCheck(filterSource.navigationAccess(), filterSource.iteratorName(),
                        filterSource.predicate(), OclIr.NavigationPredicateKind.NOT_EXISTS, type);
                case "notEmpty" -> OclOptimizedIr.navigationPredicateCheck(filterSource.navigationAccess(), filterSource.iteratorName(),
                        filterSource.predicate(), OclIr.NavigationPredicateKind.EXISTS, type);
                default -> null;
            };
        }
        return null;
    }

    private boolean isNavigationAggregateOperation(String operation) {
        return "sum".equalsIgnoreCase(operation)
                || "min".equalsIgnoreCase(operation)
                || "max".equalsIgnoreCase(operation);
    }

    private FlattenExistenceSource extractFlattenExistenceSource(OclIr.Expression source) {
        if (!(source instanceof OclIr.CollectionOperation collectionOperation)
                || !"flatten".equalsIgnoreCase(collectionOperation.operationName())) {
            return null;
        }
        if (!(collectionOperation.source() instanceof OclIr.IteratorOperation iteratorOperation)
                || !"collect".equalsIgnoreCase(iteratorOperation.operationName())
                || !(iteratorOperation.source() instanceof OclIr.NavigationAccess navigationAccess)) {
            return null;
        }
        OclIr.Expression nestedNotEmpty = new OclIr.CollectionOperation(
                iteratorOperation.body(),
                iteratorOperation.body().type(),
                "notEmpty",
                List.of(),
                OclTypeBinding.scalar("Boolean"));
        return new FlattenExistenceSource(navigationAccess, iteratorOperation.iteratorName(), nestedNotEmpty);
    }

    private NavigationAggregationSource extractNavigationAggregationSource(OclIr.Expression source) {
        if (!(source instanceof OclIr.IteratorOperation iteratorOperation)
                || !"collect".equalsIgnoreCase(iteratorOperation.operationName())) {
            return null;
        }
        if (iteratorOperation.source() instanceof OclIr.NavigationAccess navigationAccess) {
            return new NavigationAggregationSource(navigationAccess, iteratorOperation.iteratorName(), null, iteratorOperation.body());
        }
        NavigationFilterSource filterSource = extractNavigationFilterSource(iteratorOperation.source(), iteratorOperation.iteratorName());
        if (filterSource == null) {
            return null;
        }
        return new NavigationAggregationSource(
                filterSource.navigationAccess(),
                filterSource.iteratorName(),
                filterSource.predicate(),
                iteratorOperation.body());
    }

    private OclIr.Expression optimizeSizeComparison(String operator, OclIr.Expression left, OclIr.Expression right,
                                                    OclTypeBinding type) {
        NavigationSizeSource sizeSource = extractSizeSource(left);
        Long literal = extractWholeNumber(right);
        if (sizeSource == null || literal == null) {
            return null;
        }
        return OclOptimizedIr.navigationCountComparison(
                sizeSource.navigationAccess(),
                sizeSource.iteratorName(),
                sizeSource.predicate(),
                operator,
                literal,
                type);
    }

    private NavigationSizeSource extractSizeSource(OclIr.Expression expression) {
        if (!(expression instanceof OclIr.CollectionOperation collectionOperation)
                || !"size".equalsIgnoreCase(collectionOperation.operationName())) {
            return null;
        }
        if (collectionOperation.source() instanceof OclIr.NavigationAccess navigationAccess) {
            return new NavigationSizeSource(navigationAccess, "nav", null);
        }
        NavigationFilterSource filterSource = extractNavigationFilterSource(collectionOperation.source());
        if (filterSource != null) {
            return new NavigationSizeSource(filterSource.navigationAccess(), filterSource.iteratorName(), filterSource.predicate());
        }
        return null;
    }

    private NavigationFilterSource extractNavigationFilterSource(OclIr.Expression source) {
        if (source instanceof OclIr.IteratorOperation iteratorOperation) {
            return extractNavigationFilterSource(source, iteratorOperation.iteratorName());
        }
        return null;
    }

    private NavigationFilterSource extractNavigationFilterSource(OclIr.Expression source, String requiredIteratorName) {
        if (!(source instanceof OclIr.IteratorOperation iteratorOperation)) {
            return null;
        }
        String operation = iteratorOperation.operationName().toLowerCase();
        if (!"select".equals(operation) && !"reject".equals(operation)) {
            return null;
        }
        String iteratorName = requiredIteratorName != null ? requiredIteratorName : iteratorOperation.iteratorName();
        OclIr.Expression localPredicate = "reject".equals(operation)
                ? new OclIr.Not(iteratorOperation.body(), OclTypeBinding.scalar("Boolean"))
                : iteratorOperation.body();
        if (!iteratorName.equals(iteratorOperation.iteratorName())) {
            if (!canRenameWithoutCapture(localPredicate, iteratorOperation.iteratorName(), iteratorName)) {
                return null;
            }
            localPredicate = renameVariable(localPredicate, iteratorOperation.iteratorName(), iteratorName);
        }
        if (iteratorOperation.source() instanceof OclIr.NavigationAccess navigationAccess) {
            return new NavigationFilterSource(navigationAccess, iteratorName, localPredicate);
        }
        NavigationFilterSource nested = extractNavigationFilterSource(iteratorOperation.source(), iteratorName);
        if (nested == null) {
            return null;
        }
        return new NavigationFilterSource(
                nested.navigationAccess(),
                nested.iteratorName(),
                combinePredicates(nested.predicate(), localPredicate));
    }

    private OclIr.Expression combinePredicates(OclIr.Expression left, OclIr.Expression right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return new OclIr.Binary("and", left, right, OclTypeBinding.scalar("Boolean"));
    }

    /**
     * A fusion rename is safe only when the target name cannot become a binder
     * for a formerly free occurrence of the source name. The test is
     * deliberately conservative: when such a binder exists anywhere in the
     * predicate, optimization falls back to the general iterator IR instead of
     * risking variable capture. The three disjuncts are mirrored by Lean's
     * {@code NamedBridge.JavaCaptureGuard}: identical names, no free source
     * use, or no target-named binder. The Lean theorem covers the Boolean
     * binder kernel; exhaustive Java-constructor-to-kernel correspondence is a
     * separate proof obligation.
     */
    private boolean canRenameWithoutCapture(OclIr.Expression expression, String from, String to) {
        return from.equals(to)
                || countVariableUses(expression, from) == 0
                || !containsBinderNamed(expression, to);
    }

    private boolean containsBinderNamed(OclIr.Expression expression, String name) {
        if (expression instanceof OclIr.Variable || expression instanceof OclIr.Literal) {
            return false;
        }
        if (expression instanceof OclIr.SetLiteral setLiteral) {
            return setLiteral.elements().stream().anyMatch(element -> containsBinderNamed(element, name));
        }
        if (expression instanceof OclIr.Not not) {
            return containsBinderNamed(not.expression(), name);
        }
        if (expression instanceof OclIr.If ifExpression) {
            return containsBinderNamed(ifExpression.condition(), name)
                    || containsBinderNamed(ifExpression.thenBranch(), name)
                    || containsBinderNamed(ifExpression.elseBranch(), name);
        }
        if (expression instanceof OclIr.Let letExpression) {
            return name.equals(letExpression.variableName())
                    || containsBinderNamed(letExpression.value(), name)
                    || containsBinderNamed(letExpression.body(), name);
        }
        if (expression instanceof OclIr.Binary binary) {
            return containsBinderNamed(binary.left(), name) || containsBinderNamed(binary.right(), name);
        }
        if (expression instanceof OclIr.AttributeAccess attributeAccess) {
            return containsBinderNamed(attributeAccess.source(), name);
        }
        if (expression instanceof OclIr.NavigationAccess navigationAccess) {
            return containsBinderNamed(navigationAccess.source(), name)
                    || navigationAccess.qualifiers().stream()
                    .anyMatch(qualifier -> containsBinderNamed(qualifier, name));
        }
        if (expression instanceof OclIr.MethodCall methodCall) {
            return containsBinderNamed(methodCall.source(), name)
                    || methodCall.arguments().stream()
                    .anyMatch(argument -> containsBinderNamed(argument, name));
        }
        if (expression instanceof OclIr.CollectionOperation collectionOperation) {
            return containsBinderNamed(collectionOperation.source(), name)
                    || collectionOperation.arguments().stream()
                    .anyMatch(argument -> containsBinderNamed(argument, name));
        }
        if (expression instanceof OclIr.IteratorOperation iteratorOperation) {
            return name.equals(iteratorOperation.iteratorName())
                    || containsBinderNamed(iteratorOperation.source(), name)
                    || containsBinderNamed(iteratorOperation.body(), name);
        }
        if (expression instanceof OclIr.NavigationPredicateCheck predicateCheck) {
            return name.equals(predicateCheck.iteratorName())
                    || containsBinderNamed(predicateCheck.navigation(), name)
                    || predicateCheck.predicate() != null
                    && containsBinderNamed(predicateCheck.predicate(), name);
        }
        if (expression instanceof OclIr.NavigationCountComparison countComparison) {
            return name.equals(countComparison.iteratorName())
                    || containsBinderNamed(countComparison.navigation(), name)
                    || countComparison.predicate() != null
                    && containsBinderNamed(countComparison.predicate(), name);
        }
        if (expression instanceof OclIr.NavigationAggregation aggregation) {
            return name.equals(aggregation.iteratorName())
                    || containsBinderNamed(aggregation.navigation(), name)
                    || aggregation.predicate() != null
                    && containsBinderNamed(aggregation.predicate(), name)
                    || containsBinderNamed(aggregation.projection(), name);
        }
        if (expression instanceof OclIr.NavigationUniquenessCheck uniquenessCheck) {
            return name.equals(uniquenessCheck.iteratorName())
                    || containsBinderNamed(uniquenessCheck.navigation(), name)
                    || uniquenessCheck.predicate() != null
                    && containsBinderNamed(uniquenessCheck.predicate(), name)
                    || containsBinderNamed(uniquenessCheck.projection(), name);
        }
        throw new IllegalStateException("Unsupported expression for binder scan: "
                + expression.getClass().getSimpleName());
    }

    private OclIr.Expression renameVariable(OclIr.Expression expression, String from, String to) {
        if (from.equals(to)) {
            return expression;
        }
        if (expression instanceof OclIr.Variable variable) {
            return from.equals(variable.name()) ? new OclIr.Variable(to, variable.type()) : variable;
        }
        if (expression instanceof OclIr.Literal) {
            return expression;
        }
        if (expression instanceof OclIr.SetLiteral setLiteral) {
            return new OclIr.SetLiteral(
                    setLiteral.elements().stream().map(element -> renameVariable(element, from, to)).toList(),
                    setLiteral.type());
        }
        if (expression instanceof OclIr.Not not) {
            return new OclIr.Not(renameVariable(not.expression(), from, to), not.type());
        }
        if (expression instanceof OclIr.If ifExpression) {
            return new OclIr.If(
                    renameVariable(ifExpression.condition(), from, to),
                    renameVariable(ifExpression.thenBranch(), from, to),
                    renameVariable(ifExpression.elseBranch(), from, to),
                    ifExpression.type());
        }
        if (expression instanceof OclIr.Let letExpression) {
            OclIr.Expression renamedValue = renameVariable(letExpression.value(), from, to);
            if (from.equals(letExpression.variableName())) {
                return new OclIr.Let(letExpression.variableName(), renamedValue, letExpression.body(), letExpression.type());
            }
            return new OclIr.Let(
                    letExpression.variableName(),
                    renamedValue,
                    renameVariable(letExpression.body(), from, to),
                    letExpression.type());
        }
        if (expression instanceof OclIr.Binary binary) {
            return new OclIr.Binary(
                    binary.operator(),
                    renameVariable(binary.left(), from, to),
                    renameVariable(binary.right(), from, to),
                    binary.type());
        }
        if (expression instanceof OclIr.AttributeAccess attributeAccess) {
            return new OclIr.AttributeAccess(
                    renameVariable(attributeAccess.source(), from, to),
                    attributeAccess.attributeName(),
                    attributeAccess.attributeType(),
                    attributeAccess.type(),
                    attributeAccess.attribute());
        }
        if (expression instanceof OclIr.NavigationAccess navigationAccess) {
            return new OclIr.NavigationAccess(
                    renameVariable(navigationAccess.source(), from, to),
                    navigationAccess.navigation(),
                    navigationAccess.qualifiers().stream().map(argument -> renameVariable(argument, from, to)).toList(),
                    navigationAccess.type());
        }
        if (expression instanceof OclIr.MethodCall methodCall) {
            return new OclIr.MethodCall(
                    renameVariable(methodCall.source(), from, to),
                    methodCall.methodName(),
                    methodCall.arguments().stream().map(argument -> renameVariable(argument, from, to)).toList(),
                    methodCall.type());
        }
        if (expression instanceof OclIr.CollectionOperation collectionOperation) {
            return new OclIr.CollectionOperation(
                    renameVariable(collectionOperation.source(), from, to),
                    collectionOperation.sourceCollectionType(),
                    collectionOperation.operationName(),
                    collectionOperation.arguments().stream().map(argument -> renameVariable(argument, from, to)).toList(),
                    collectionOperation.type());
        }
        if (expression instanceof OclIr.IteratorOperation iteratorOperation) {
            OclIr.Expression renamedSource = renameVariable(iteratorOperation.source(), from, to);
            if (from.equals(iteratorOperation.iteratorName())) {
                return new OclIr.IteratorOperation(
                        renamedSource,
                        iteratorOperation.sourceCollectionType(),
                        iteratorOperation.operationName(),
                        iteratorOperation.iteratorName(),
                        iteratorOperation.body(),
                        iteratorOperation.type());
            }
            return new OclIr.IteratorOperation(
                    renamedSource,
                    iteratorOperation.sourceCollectionType(),
                    iteratorOperation.operationName(),
                    iteratorOperation.iteratorName(),
                    renameVariable(iteratorOperation.body(), from, to),
                    iteratorOperation.type());
        }
        if (expression instanceof OclIr.NavigationPredicateCheck predicateCheck) {
            OclIr.Expression renamedNavigation = renameVariable(predicateCheck.navigation(), from, to);
            if (from.equals(predicateCheck.iteratorName())) {
                return OclOptimizedIr.navigationPredicateCheck(
                        (OclIr.NavigationAccess) renamedNavigation,
                        predicateCheck.iteratorName(),
                        predicateCheck.predicate(),
                        predicateCheck.kind(),
                        predicateCheck.type());
            }
            return OclOptimizedIr.navigationPredicateCheck(
                    (OclIr.NavigationAccess) renamedNavigation,
                    predicateCheck.iteratorName(),
                    predicateCheck.predicate() != null ? renameVariable(predicateCheck.predicate(), from, to) : null,
                    predicateCheck.kind(),
                    predicateCheck.type());
        }
        if (expression instanceof OclIr.NavigationCountComparison countComparison) {
            OclIr.Expression renamedNavigation = renameVariable(countComparison.navigation(), from, to);
            if (from.equals(countComparison.iteratorName())) {
                return OclOptimizedIr.navigationCountComparison(
                        (OclIr.NavigationAccess) renamedNavigation,
                        countComparison.iteratorName(),
                        countComparison.predicate(),
                        countComparison.operator(),
                        countComparison.literal(),
                        countComparison.type());
            }
            return OclOptimizedIr.navigationCountComparison(
                    (OclIr.NavigationAccess) renamedNavigation,
                    countComparison.iteratorName(),
                    countComparison.predicate() != null ? renameVariable(countComparison.predicate(), from, to) : null,
                    countComparison.operator(),
                    countComparison.literal(),
                    countComparison.type());
        }
        if (expression instanceof OclIr.NavigationAggregation aggregation) {
            OclIr.Expression renamedNavigation = renameVariable(aggregation.navigation(), from, to);
            if (from.equals(aggregation.iteratorName())) {
                return OclOptimizedIr.navigationAggregation(
                        (OclIr.NavigationAccess) renamedNavigation,
                        aggregation.iteratorName(),
                        aggregation.predicate(),
                        aggregation.projection(),
                        aggregation.operationName(),
                        aggregation.type());
            }
            return OclOptimizedIr.navigationAggregation(
                    (OclIr.NavigationAccess) renamedNavigation,
                    aggregation.iteratorName(),
                    aggregation.predicate() != null ? renameVariable(aggregation.predicate(), from, to) : null,
                    renameVariable(aggregation.projection(), from, to),
                    aggregation.operationName(),
                    aggregation.type());
        }
        if (expression instanceof OclIr.NavigationUniquenessCheck uniquenessCheck) {
            OclIr.Expression renamedNavigation = renameVariable(uniquenessCheck.navigation(), from, to);
            if (from.equals(uniquenessCheck.iteratorName())) {
                return OclOptimizedIr.navigationUniquenessCheck(
                        (OclIr.NavigationAccess) renamedNavigation,
                        uniquenessCheck.iteratorName(),
                        uniquenessCheck.predicate(),
                        uniquenessCheck.projection(),
                        uniquenessCheck.type());
            }
            return OclOptimizedIr.navigationUniquenessCheck(
                    (OclIr.NavigationAccess) renamedNavigation,
                    uniquenessCheck.iteratorName(),
                    uniquenessCheck.predicate() != null ? renameVariable(uniquenessCheck.predicate(), from, to) : null,
                    renameVariable(uniquenessCheck.projection(), from, to),
                    uniquenessCheck.type());
        }
        throw new IllegalStateException("Unsupported expression for rename: " + expression.getClass().getSimpleName());
    }

    private Long extractWholeNumber(OclIr.Expression expression) {
        if (expression instanceof OclIr.Literal literal && literal.value() instanceof Number number) {
            double value = number.doubleValue();
            if (Math.rint(value) == value) {
                return number.longValue();
            }
        }
        return null;
    }

    private record NavigationSizeSource(OclIr.NavigationAccess navigationAccess, String iteratorName, OclIr.Expression predicate) {
    }

    private record NavigationFilterSource(OclIr.NavigationAccess navigationAccess, String iteratorName, OclIr.Expression predicate) {
    }

    private record FlattenExistenceSource(OclIr.NavigationAccess navigationAccess, String iteratorName,
                                          OclIr.Expression nestedNotEmptyPredicate) {
    }

    private record NavigationAggregationSource(OclIr.NavigationAccess navigationAccess, String iteratorName,
                                               OclIr.Expression predicate, OclIr.Expression projection) {
    }
}
