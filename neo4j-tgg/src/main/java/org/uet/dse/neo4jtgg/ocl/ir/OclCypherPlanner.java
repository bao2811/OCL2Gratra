package org.uet.dse.neo4jtgg.ocl.ir;

import org.uet.dse.neo4j.encoding.CanonicalGraphEncoding;
import org.uet.dse.neo4j.encoding.CanonicalGraphVocabulary;
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
    private final String modelName;

    public OclCypherPlanner() {
        this(null);
    }

    public OclCypherPlanner(String modelName) {
        this.modelName = modelName;
    }

    public OclCypherPlan.InvariantPlan planInvariant(OclIr.InvariantQuery invariantQuery) {
        return OclCypherQueryModel.invariant(
                invariantQuery.contextClassName(),
                invariantQuery.invariantName(),
                planOptimizedExpression(OclOptimizedIr.requireOptimized(invariantQuery)),
                graphContextBinding(invariantQuery.contextClassName()));
    }

    public OclCypherPlan.ExpressionPlan planExpression(OclOptimizedIr.Artifact artifact) {
        if (artifact == null) throw new IllegalArgumentException("Optimized IR artifact is required");
        return planOptimizedExpression(artifact.requireCurrent());
    }

    private OclCypherPlan.ExpressionPlan planExpressionInternal(OclIr.Expression expression) {
        if (expression instanceof OclIr.Variable variable) {
            return OclCypherQueryModel.variable(variable.name(), variable.type());
        }
        if (expression instanceof OclIr.Literal literal) {
            return OclCypherQueryModel.literal(literal.value(), literal.type());
        }
        if (expression instanceof OclIr.SetLiteral setLiteral) {
            return OclCypherQueryModel.setLiteral(
                    setLiteral.elements().stream().map(this::planExpressionInternal).toList(),
                    setLiteral.type());
        }
        if (expression instanceof OclIr.Not not) {
            return OclCypherQueryModel.not(planExpressionInternal(not.expression()), not.type());
        }
        if (expression instanceof OclIr.If ifExpression) {
            return OclCypherQueryModel.ifExpression(
                    planExpressionInternal(ifExpression.condition()),
                    planExpressionInternal(ifExpression.thenBranch()),
                    planExpressionInternal(ifExpression.elseBranch()),
                    ifExpression.type());
        }
        if (expression instanceof OclIr.Let letExpression) {
            return OclCypherQueryModel.let(
                    letExpression.variableName(),
                    planExpressionInternal(letExpression.value()),
                    letExpression.variableType(),
                    planExpressionInternal(letExpression.body()),
                    letExpression.type());
        }
        if (expression instanceof OclIr.Binary binary) {
            return OclCypherQueryModel.binary(
                    binary.operator(),
                    planExpressionInternal(binary.left()),
                    planExpressionInternal(binary.right()),
                    binary.type());
        }
        if (expression instanceof OclIr.AttributeAccess attributeAccess) {
            return OclCypherQueryModel.attributeAccess(
                    planExpressionInternal(attributeAccess.source()),
                    attributeAccess.attributeName(),
                    attributeAccess.attributeType(),
                    attributeAccess.type(),
                    attributeAccess.attribute(),
                    attributeBinding(attributeAccess));
        }
        if (expression instanceof OclIr.NavigationAccess navigationAccess) {
            return OclCypherQueryModel.navigationAccess(
                    planExpressionInternal(navigationAccess.source()),
                    navigationAccess.navigation(),
                    navigationAccess.qualifiers().stream().map(this::planExpressionInternal).toList(),
                    navigationAccess.type(),
                    navigationBinding(navigationAccess));
        }
        if (expression instanceof OclIr.MethodCall methodCall) {
            return OclCypherQueryModel.methodCall(
                    planExpressionInternal(methodCall.source()),
                    methodCall.methodName(),
                    methodCall.arguments().stream().map(this::planExpressionInternal).toList(),
                    methodCall.type());
        }
        if (expression instanceof OclIr.CollectionOperation collectionOperation) {
            return OclCypherQueryModel.collectionOperation(
                    planExpressionInternal(collectionOperation.source()),
                    collectionOperation.sourceCollectionType(),
                    collectionOperation.operationName(),
                    collectionOperation.arguments().stream().map(this::planExpressionInternal).toList(),
                    collectionOperation.type());
        }
        if (expression instanceof OclIr.IteratorOperation iteratorOperation) {
            return OclCypherQueryModel.iteratorOperation(
                    planExpressionInternal(iteratorOperation.source()),
                    iteratorOperation.sourceCollectionType(),
                    iteratorOperation.operationName(),
                    iteratorOperation.iteratorName(),
                    iteratorOperation.iteratorVariableType(),
                    planExpressionInternal(iteratorOperation.body()),
                    iteratorOperation.type());
        }
        if (expression instanceof OclIr.NavigationPredicateCheck predicateCheck) {
            OclCypherPlan.NavigationMatchPlan matchPlan = createNavigationMatchPlan(
                    requireNavigationPlan(planExpressionInternal(predicateCheck.navigation())),
                    predicateCheck.iteratorName(),
                    predicateCheck.predicate() != null ? planExpressionInternal(predicateCheck.predicate()) : null,
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
                    requireNavigationPlan(planExpressionInternal(countComparison.navigation())),
                    countComparison.iteratorName(),
                    countComparison.predicate() != null ? planExpressionInternal(countComparison.predicate()) : null,
                    OclCypherPlan.PredicateMode.NORMAL);
            return planCountComparison(matchPlan, countComparison.operator(), countComparison.literal(), countComparison.type());
        }
        if (expression instanceof OclIr.NavigationAggregation aggregation) {
            OclCypherPlan.NavigationMatchPlan matchPlan = createNavigationMatchPlan(
                    requireNavigationPlan(planExpressionInternal(aggregation.navigation())),
                    aggregation.iteratorName(),
                    aggregation.predicate() != null ? planExpressionInternal(aggregation.predicate()) : null,
                    OclCypherPlan.PredicateMode.NORMAL);
            return OclCypherQueryModel.navigationAggregation(
                    matchPlan,
                    planExpressionInternal(aggregation.projection()),
                    aggregation.operationName(),
                    aggregation.type());
        }
        if (expression instanceof OclIr.NavigationUniquenessCheck uniquenessCheck) {
            OclCypherPlan.NavigationMatchPlan matchPlan = createNavigationMatchPlan(
                    requireNavigationPlan(planExpressionInternal(uniquenessCheck.navigation())),
                    uniquenessCheck.iteratorName(),
                    uniquenessCheck.predicate() != null ? planExpressionInternal(uniquenessCheck.predicate()) : null,
                    OclCypherPlan.PredicateMode.NORMAL);
            return OclCypherQueryModel.navigationUniqueness(
                    matchPlan,
                    planExpressionInternal(uniquenessCheck.projection()),
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

    /** Named production witness retained for the constructor/refinement evidence matrices. */
    private OclCypherPlan.ExpressionPlan planOptimizedExpression(OclIr.Expression expression) {
        return planExpressionInternal(expression);
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

    private OclCypherPlan.GraphContextBinding graphContextBinding(String contextClassName) {
        if (modelName == null || modelName.isBlank()) return null;
        return new OclCypherPlan.GraphContextBinding(
                CanonicalGraphEncoding.PROFILE_ID,
                CanonicalGraphEncoding.modelKey(modelName),
                CanonicalGraphEncoding.classKey(modelName, contextClassName),
                "Object",
                "UmlClass",
                CanonicalGraphVocabulary.OBJECT_INSTANCE_OF,
                "use_id");
    }

    private OclCypherPlan.AttributeBinding attributeBinding(OclIr.AttributeAccess access) {
        if (modelName == null || modelName.isBlank()) return null;
        return new OclCypherPlan.AttributeBinding(
                CanonicalGraphEncoding.attributeKey(
                        modelName, access.attribute().owner().name(), access.attributeName()),
                "AttributeValue",
                "ObjectHasAttribute",
                "value");
    }

    private OclCypherPlan.NavigationBinding navigationBinding(OclIr.NavigationAccess access) {
        if (modelName == null || modelName.isBlank()) return null;
        var navigation = access.navigation();
        return new OclCypherPlan.NavigationBinding(
                CanonicalGraphEncoding.associationKey(modelName, navigation.associationName()),
                navigation.sourceRoleName(),
                navigation.targetRoleName(),
                navigation.direction(),
                "Link",
                "sourceQualifiers",
                "targetQualifiers");
    }
}
