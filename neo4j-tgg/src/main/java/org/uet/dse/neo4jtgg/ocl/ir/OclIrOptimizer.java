package org.uet.dse.neo4jtgg.ocl.ir;

import org.uet.dse.neo4jtgg.ocl.OclTypeBinding;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class OclIrOptimizer {
    public OclIr.InvariantQuery optimizeInvariant(OclIr.InvariantQuery invariantQuery) {
        return new OclIr.InvariantQuery(
                invariantQuery.contextClassName(),
                invariantQuery.invariantName(),
                optimizeExpression(invariantQuery.predicate()));
    }

    public OclIr.Expression optimizeExpression(OclIr.Expression expression) {
        return optimizeExpression(expression, Map.of());
    }

    private OclIr.Expression optimizeExpression(OclIr.Expression expression, Map<String, OclIr.Expression> bindings) {
        if (expression instanceof OclIr.Variable variable) {
            return bindings.getOrDefault(variable.name(), variable);
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
            return folded != null ? folded : new OclIr.Binary(binary.operator(), left, right, binary.type());
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
            return new OclIr.NavigationAccess(optimizeExpression(navigationAccess.source(), bindings), navigationAccess.navigation(), navigationAccess.type());
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
                    optimizedSource, iteratorOperation.operationName(), iteratorOperation.iteratorName(), optimizedBody, iteratorOperation.type());
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
            return countVariableUses(navigationAccess.source(), variableName);
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
        return 0;
    }

    private OclIr.Expression optimizeIterator(String operation, OclIr.Expression source, String iteratorName,
                                              OclIr.Expression body, OclTypeBinding type) {
        if (!(source instanceof OclIr.NavigationAccess navigationAccess)) {
            return null;
        }
        return switch (operation.toLowerCase()) {
            case "exists" -> new OclIr.NavigationPredicateCheck(navigationAccess, iteratorName, body,
                    OclIr.NavigationPredicateKind.EXISTS, type);
            case "forall" -> new OclIr.NavigationPredicateCheck(navigationAccess, iteratorName, body,
                    OclIr.NavigationPredicateKind.FORALL, type);
            default -> null;
        };
    }

    private OclIr.Expression optimizeCollectionOperation(String operation, OclIr.Expression source, OclTypeBinding type) {
        if (source instanceof OclIr.NavigationAccess navigationAccess) {
            return switch (operation) {
                case "isEmpty" -> new OclIr.NavigationPredicateCheck(navigationAccess, "nav", null,
                        OclIr.NavigationPredicateKind.NOT_EXISTS, type);
                case "notEmpty" -> new OclIr.NavigationPredicateCheck(navigationAccess, "nav", null,
                        OclIr.NavigationPredicateKind.EXISTS, type);
                default -> null;
            };
        }
        if (source instanceof OclIr.IteratorOperation iteratorOperation
                && "select".equalsIgnoreCase(iteratorOperation.operationName())
                && iteratorOperation.source() instanceof OclIr.NavigationAccess navigationAccess) {
            return switch (operation) {
                case "isEmpty" -> new OclIr.NavigationPredicateCheck(navigationAccess, iteratorOperation.iteratorName(),
                        iteratorOperation.body(), OclIr.NavigationPredicateKind.NOT_EXISTS, type);
                case "notEmpty" -> new OclIr.NavigationPredicateCheck(navigationAccess, iteratorOperation.iteratorName(),
                        iteratorOperation.body(), OclIr.NavigationPredicateKind.EXISTS, type);
                default -> null;
            };
        }
        return null;
    }

    private OclIr.Expression optimizeSizeComparison(String operator, OclIr.Expression left, OclIr.Expression right,
                                                    OclTypeBinding type) {
        NavigationSizeSource sizeSource = extractSizeSource(left);
        Long literal = extractWholeNumber(right);
        if (sizeSource == null || literal == null) {
            return null;
        }
        return new OclIr.NavigationCountComparison(
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
        if (collectionOperation.source() instanceof OclIr.IteratorOperation iteratorOperation
                && "select".equalsIgnoreCase(iteratorOperation.operationName())
                && iteratorOperation.source() instanceof OclIr.NavigationAccess navigationAccess) {
            return new NavigationSizeSource(navigationAccess, iteratorOperation.iteratorName(), iteratorOperation.body());
        }
        return null;
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
}
