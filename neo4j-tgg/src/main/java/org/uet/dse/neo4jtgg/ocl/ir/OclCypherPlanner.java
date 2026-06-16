package org.uet.dse.neo4jtgg.ocl.ir;

import org.uet.dse.neo4jtgg.ocl.diagnostic.OclCodedUnsupportedOperationException;
import org.uet.dse.neo4jtgg.ocl.diagnostic.OclDiagnosticCode;

import java.util.List;

public class OclCypherPlanner {
    public OclCypherPlan.InvariantPlan planInvariant(OclIr.InvariantQuery invariantQuery) {
        return new OclCypherPlan.InvariantPlan(
                invariantQuery.contextClassName(),
                invariantQuery.invariantName(),
                planExpression(invariantQuery.predicate()));
    }

    public OclCypherPlan.ExpressionPlan planExpression(OclIr.Expression expression) {
        if (expression instanceof OclIr.Variable variable) {
            return new OclCypherPlan.VariablePlan(variable.name(), variable.type());
        }
        if (expression instanceof OclIr.Literal literal) {
            return new OclCypherPlan.LiteralPlan(literal.value(), literal.type());
        }
        if (expression instanceof OclIr.Not not) {
            return new OclCypherPlan.NotPlan(planExpression(not.expression()), not.type());
        }
        if (expression instanceof OclIr.If ifExpression) {
            return new OclCypherPlan.IfPlan(
                    planExpression(ifExpression.condition()),
                    planExpression(ifExpression.thenBranch()),
                    planExpression(ifExpression.elseBranch()),
                    ifExpression.type());
        }
        if (expression instanceof OclIr.Let letExpression) {
            return new OclCypherPlan.LetPlan(
                    letExpression.variableName(),
                    planExpression(letExpression.value()),
                    planExpression(letExpression.body()),
                    letExpression.type());
        }
        if (expression instanceof OclIr.Binary binary) {
            return new OclCypherPlan.BinaryPlan(
                    binary.operator(),
                    planExpression(binary.left()),
                    planExpression(binary.right()),
                    binary.type());
        }
        if (expression instanceof OclIr.AttributeAccess attributeAccess) {
            return new OclCypherPlan.AttributeAccessPlan(
                    planExpression(attributeAccess.source()),
                    attributeAccess.attributeName(),
                    attributeAccess.attributeType(),
                    attributeAccess.type(),
                    attributeAccess.attribute());
        }
        if (expression instanceof OclIr.NavigationAccess navigationAccess) {
            return new OclCypherPlan.NavigationAccessPlan(
                    planExpression(navigationAccess.source()),
                    navigationAccess.navigation(),
                    navigationAccess.qualifiers().stream().map(this::planExpression).toList(),
                    navigationAccess.type());
        }
        if (expression instanceof OclIr.MethodCall methodCall) {
            return new OclCypherPlan.MethodCallPlan(
                    planExpression(methodCall.source()),
                    methodCall.methodName(),
                    methodCall.arguments().stream().map(this::planExpression).toList(),
                    methodCall.type());
        }
        if (expression instanceof OclIr.CollectionOperation collectionOperation) {
            return new OclCypherPlan.CollectionOperationPlan(
                    planExpression(collectionOperation.source()),
                    collectionOperation.operationName(),
                    collectionOperation.arguments().stream().map(this::planExpression).toList(),
                    collectionOperation.type());
        }
        if (expression instanceof OclIr.IteratorOperation iteratorOperation) {
            return new OclCypherPlan.IteratorOperationPlan(
                    planExpression(iteratorOperation.source()),
                    iteratorOperation.operationName(),
                    iteratorOperation.iteratorName(),
                    planExpression(iteratorOperation.body()),
                    iteratorOperation.type());
        }
        if (expression instanceof OclIr.NavigationPredicateCheck predicateCheck) {
            OclCypherPlan.NavigationMatchPlan matchPlan = createNavigationMatchPlan(
                    requireNavigationPlan(planExpression(predicateCheck.navigation())),
                    predicateCheck.iteratorName(),
                    predicateCheck.predicate() != null ? planExpression(predicateCheck.predicate()) : null,
                    predicateCheck.kind() == OclIr.NavigationPredicateKind.FORALL
                            ? OclCypherPlan.PredicateMode.NEGATED
                            : OclCypherPlan.PredicateMode.NORMAL);
            return switch (predicateCheck.kind()) {
                case EXISTS -> new OclCypherPlan.ExistsSubqueryPlan(matchPlan, predicateCheck.type());
                case NOT_EXISTS, FORALL -> new OclCypherPlan.NotExistsSubqueryPlan(matchPlan, predicateCheck.type());
            };
        }
        if (expression instanceof OclIr.NavigationCountComparison countComparison) {
            OclCypherPlan.NavigationMatchPlan matchPlan = createNavigationMatchPlan(
                    requireNavigationPlan(planExpression(countComparison.navigation())),
                    countComparison.iteratorName(),
                    countComparison.predicate() != null ? planExpression(countComparison.predicate()) : null,
                    OclCypherPlan.PredicateMode.NORMAL);
            return planCountComparison(matchPlan, countComparison.operator(), countComparison.literal(), countComparison.type());
        }
        if (expression instanceof OclIr.NavigationAggregation aggregation) {
            OclCypherPlan.NavigationMatchPlan matchPlan = createNavigationMatchPlan(
                    requireNavigationPlan(planExpression(aggregation.navigation())),
                    aggregation.iteratorName(),
                    aggregation.predicate() != null ? planExpression(aggregation.predicate()) : null,
                    OclCypherPlan.PredicateMode.NORMAL);
            return new OclCypherPlan.NavigationAggregationPlan(
                    matchPlan,
                    planExpression(aggregation.projection()),
                    aggregation.operationName(),
                    aggregation.type());
        }
        if (expression instanceof OclIr.NavigationUniquenessCheck uniquenessCheck) {
            OclCypherPlan.NavigationMatchPlan matchPlan = createNavigationMatchPlan(
                    requireNavigationPlan(planExpression(uniquenessCheck.navigation())),
                    uniquenessCheck.iteratorName(),
                    uniquenessCheck.predicate() != null ? planExpression(uniquenessCheck.predicate()) : null,
                    OclCypherPlan.PredicateMode.NORMAL);
            return new OclCypherPlan.NavigationUniquenessPlan(
                    matchPlan,
                    planExpression(uniquenessCheck.projection()),
                    uniquenessCheck.type());
        }
        throw new OclCodedUnsupportedOperationException(
                OclDiagnosticCode.UNSUPPORTED_PLAN_SOURCE,
                "Unsupported plan source: " + expression.getClass().getSimpleName());
        }

    private OclCypherPlan.NavigationAccessPlan requireNavigationPlan(OclCypherPlan.ExpressionPlan expressionPlan) {
        if (expressionPlan instanceof OclCypherPlan.NavigationAccessPlan navigationAccessPlan) {
            return navigationAccessPlan;
        }
        throw new IllegalStateException("Expected navigation access plan but got " + expressionPlan.getClass().getSimpleName());
    }

    private OclCypherPlan.NavigationMatchPlan createNavigationMatchPlan(OclCypherPlan.NavigationAccessPlan navigationAccessPlan,
                                                                        String iteratorName,
                                                                        OclCypherPlan.ExpressionPlan predicatePlan,
                                                                        OclCypherPlan.PredicateMode predicateMode) {
        String targetAlias = iteratorName != null ? iteratorName : "nav";
        return new OclCypherPlan.NavigationMatchPlan(
                navigationAccessPlan.source(),
                targetAlias,
                navigationAccessPlan,
                predicatePlan,
                predicatePlan == null ? OclCypherPlan.PredicateMode.NONE : predicateMode,
                navigationAccessPlan.type().elementType());
    }

    private OclCypherPlan.ExpressionPlan planCountComparison(OclCypherPlan.NavigationMatchPlan matchPlan,
                                                             String operator,
                                                             long literal,
                                                             org.uet.dse.neo4jtgg.ocl.OclTypeBinding type) {
        return switch (operator) {
            case ">" -> literal == 0
                    ? new OclCypherPlan.ExistsSubqueryPlan(matchPlan, type)
                    : new OclCypherPlan.CountSubqueryComparisonPlan(matchPlan, operator, literal, type);
            case ">=" -> literal == 1
                    ? new OclCypherPlan.ExistsSubqueryPlan(matchPlan, type)
                    : new OclCypherPlan.CountSubqueryComparisonPlan(matchPlan, operator, literal, type);
            case "=" -> literal == 0
                    ? new OclCypherPlan.NotExistsSubqueryPlan(matchPlan, type)
                    : new OclCypherPlan.CountSubqueryComparisonPlan(matchPlan, operator, literal, type);
            case "<>" -> literal == 0
                    ? new OclCypherPlan.ExistsSubqueryPlan(matchPlan, type)
                    : new OclCypherPlan.CountSubqueryComparisonPlan(matchPlan, operator, literal, type);
            case "<" -> literal == 1
                    ? new OclCypherPlan.NotExistsSubqueryPlan(matchPlan, type)
                    : new OclCypherPlan.CountSubqueryComparisonPlan(matchPlan, operator, literal, type);
            case "<=" -> literal == 0
                    ? new OclCypherPlan.NotExistsSubqueryPlan(matchPlan, type)
                    : new OclCypherPlan.CountSubqueryComparisonPlan(matchPlan, operator, literal, type);
            default -> throw new OclCodedUnsupportedOperationException(
                    OclDiagnosticCode.UNSUPPORTED_COUNT_OPERATOR,
                    "Unsupported count operator: " + operator);
        };
    }
}
