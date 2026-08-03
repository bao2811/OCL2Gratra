package org.uet.dse.neo4jtgg.ocl.ir;

import org.uet.dse.neo4jtgg.ocl.diagnostic.OclCodedUnsupportedOperationException;
import org.uet.dse.neo4jtgg.ocl.diagnostic.OclDiagnosticCode;

import java.util.List;

/**
 * Model-to-model transformation from {@link OclOptimizedIr} to the abstract
 * Cypher Query Model.
 *
 * <p>The planner chooses query shapes such as {@code EXISTS}, {@code NOT
 * EXISTS}, and {@code COUNT { ... }} without emitting textual Cypher. This
 * keeps backend decisions separate from concrete syntax generation and makes
 * the IR-to-Cypher preservation argument explicit.</p>
 *
 * <pre>
 * T_CQ : M_OptimizedIR -> M_CypherQuery
 * </pre>
 */
public class OclCypherPlanner {
    public OclCypherPlan.InvariantPlan planInvariant(OclIr.InvariantQuery invariantQuery) {
        return OclCypherQueryModel.invariant(
                invariantQuery.contextClassName(),
                invariantQuery.invariantName(),
                planExpression(OclOptimizedIr.requireOptimized(invariantQuery.predicate())));
    }

    public OclCypherPlan.ExpressionPlan planExpression(OclIr.OptimizedExpression expression) {
        return planOptimizedExpression(expression);
    }

    public OclCypherPlan.ExpressionPlan planExpression(OclIr.Expression expression) {
        return planExpression(OclOptimizedIr.requireOptimized(expression));
    }

    private OclCypherPlan.ExpressionPlan planOptimizedExpression(OclIr.Expression expression) {
        if (expression instanceof OclIr.Variable variable) {
            return OclCypherQueryModel.variable(variable.name(), variable.type());
        }
        if (expression instanceof OclIr.Literal literal) {
            return OclCypherQueryModel.literal(literal.value(), literal.type());
        }
        if (expression instanceof OclIr.SetLiteral setLiteral) {
            return OclCypherQueryModel.setLiteral(
                    setLiteral.elements().stream().map(this::planExpression).toList(),
                    setLiteral.type());
        }
        if (expression instanceof OclIr.Not not) {
            return OclCypherQueryModel.not(planExpression(not.expression()), not.type());
        }
        if (expression instanceof OclIr.If ifExpression) {
            return OclCypherQueryModel.ifExpression(
                    planExpression(ifExpression.condition()),
                    planExpression(ifExpression.thenBranch()),
                    planExpression(ifExpression.elseBranch()),
                    ifExpression.type());
        }
        if (expression instanceof OclIr.Let letExpression) {
            return OclCypherQueryModel.let(
                    letExpression.variableName(),
                    planExpression(letExpression.value()),
                    planExpression(letExpression.body()),
                    letExpression.type());
        }
        if (expression instanceof OclIr.Binary binary) {
            return OclCypherQueryModel.binary(
                    binary.operator(),
                    planExpression(binary.left()),
                    planExpression(binary.right()),
                    binary.type());
        }
        if (expression instanceof OclIr.AttributeAccess attributeAccess) {
            return OclCypherQueryModel.attributeAccess(
                    planExpression(attributeAccess.source()),
                    attributeAccess.attributeName(),
                    attributeAccess.attributeType(),
                    attributeAccess.type(),
                    attributeAccess.attribute());
        }
        if (expression instanceof OclIr.NavigationAccess navigationAccess) {
            return OclCypherQueryModel.navigationAccess(
                    planExpression(navigationAccess.source()),
                    navigationAccess.navigation(),
                    navigationAccess.qualifiers().stream().map(this::planExpression).toList(),
                    navigationAccess.type());
        }
        if (expression instanceof OclIr.MethodCall methodCall) {
            return OclCypherQueryModel.methodCall(
                    planExpression(methodCall.source()),
                    methodCall.methodName(),
                    methodCall.arguments().stream().map(this::planExpression).toList(),
                    methodCall.type());
        }
        if (expression instanceof OclIr.CollectionOperation collectionOperation) {
            return OclCypherQueryModel.collectionOperation(
                    planExpression(collectionOperation.source()),
                    collectionOperation.operationName(),
                    collectionOperation.arguments().stream().map(this::planExpression).toList(),
                    collectionOperation.type());
        }
        if (expression instanceof OclIr.IteratorOperation iteratorOperation) {
            return OclCypherQueryModel.iteratorOperation(
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
                case EXISTS -> OclCypherQueryModel.existsSubquery(matchPlan, predicateCheck.type());
                case NOT_EXISTS, FORALL -> OclCypherQueryModel.notExistsSubquery(matchPlan, predicateCheck.type());
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
            return OclCypherQueryModel.navigationAggregation(
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
            return OclCypherQueryModel.navigationUniqueness(
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
        return OclCypherQueryModel.navigationMatch(
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
                    ? OclCypherQueryModel.existsSubquery(matchPlan, type)
                    : OclCypherQueryModel.countSubqueryComparison(matchPlan, operator, literal, type);
            case ">=" -> literal == 1
                    ? OclCypherQueryModel.existsSubquery(matchPlan, type)
                    : OclCypherQueryModel.countSubqueryComparison(matchPlan, operator, literal, type);
            case "=" -> literal == 0
                    ? OclCypherQueryModel.notExistsSubquery(matchPlan, type)
                    : OclCypherQueryModel.countSubqueryComparison(matchPlan, operator, literal, type);
            case "<>" -> literal == 0
                    ? OclCypherQueryModel.existsSubquery(matchPlan, type)
                    : OclCypherQueryModel.countSubqueryComparison(matchPlan, operator, literal, type);
            case "<" -> literal == 1
                    ? OclCypherQueryModel.notExistsSubquery(matchPlan, type)
                    : OclCypherQueryModel.countSubqueryComparison(matchPlan, operator, literal, type);
            case "<=" -> literal == 0
                    ? OclCypherQueryModel.notExistsSubquery(matchPlan, type)
                    : OclCypherQueryModel.countSubqueryComparison(matchPlan, operator, literal, type);
            default -> throw new OclCodedUnsupportedOperationException(
                    OclDiagnosticCode.UNSUPPORTED_COUNT_OPERATOR,
                    "Unsupported count operator: " + operator);
        };
    }
}
