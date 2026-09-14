package org.uet.dse.neo4jtgg.ocl.ir;

import org.tzi.use.uml.mm.MAttribute;
import org.tzi.use.uml.ocl.type.Type;
import org.uet.dse.neo4jtgg.ocl.OclMetamodelIndex;
import org.uet.dse.neo4jtgg.ocl.OclTypeBinding;

import java.util.List;
import java.util.Objects;

/**
 * Facade for constructing instances of the Cypher Query Model.
 *
 * <p>{@link OclCypherPlan} keeps the compatibility record definitions used by
 * the renderer. This facade is the research-facing namespace for
 * {@code M_CypherQuery}: planner code should create query model elements
 * through these factory methods instead of directly instantiating plan records.</p>
 */
public final class OclCypherQueryModel {
    private OclCypherQueryModel() {
    }

    public static OclCypherPlan.InvariantPlan invariant(String contextClassName,
                                                        String invariantName,
                                                        OclCypherPlan.ExpressionPlan predicate) {
        return invariant(contextClassName, invariantName, predicate, null);
    }

    public static OclCypherPlan.InvariantPlan invariant(String contextClassName,
                                                        String invariantName,
                                                        OclCypherPlan.ExpressionPlan predicate,
                                                        OclCypherPlan.GraphContextBinding graphBinding) {
        OclCypherPlan.ExpressionPlan checkedPredicate = required(predicate, "predicate");
        requireBoolean(checkedPredicate.type(), "predicate");
        return new OclCypherPlan.InvariantPlan(
                nonBlank(contextClassName, "contextClassName"),
                nonBlank(invariantName, "invariantName"),
                checkedPredicate,
                OclCypherPlan.ViolationPolicy.NOT_VALIDATION_TRUE,
                graphBinding,
                OclCypherPlan.EvaluationPolicy.MATERIALIZE_REUSED_EXPRESSIONS);
    }

    public static OclCypherPlan.VariablePlan variable(String name, OclTypeBinding type) {
        return new OclCypherPlan.VariablePlan(nonBlank(name, "name"), required(type, "type"));
    }

    public static OclCypherPlan.LiteralPlan literal(Object value, OclTypeBinding type) {
        return new OclCypherPlan.LiteralPlan(value, required(type, "type"));
    }

    public static OclCypherPlan.SetLiteralPlan setLiteral(List<OclCypherPlan.ExpressionPlan> elements,
                                                           OclTypeBinding type) {
        OclTypeBinding checkedType = required(type, "type");
        if (!checkedType.isCollection() || !checkedType.isUniqueCollection()) {
            throw new IllegalArgumentException("Set literal must have a unique collection type");
        }
        return new OclCypherPlan.SetLiteralPlan(copyList(elements, "elements"), checkedType);
    }

    public static OclCypherPlan.NotPlan not(OclCypherPlan.ExpressionPlan expression, OclTypeBinding type) {
        OclCypherPlan.ExpressionPlan checkedExpression = required(expression, "expression");
        requireBoolean(checkedExpression.type(), "expression");
        return new OclCypherPlan.NotPlan(checkedExpression, requireBoolean(type, "type"));
    }

    public static OclCypherPlan.IfPlan ifExpression(OclCypherPlan.ExpressionPlan condition,
                                                    OclCypherPlan.ExpressionPlan thenBranch,
                                                    OclCypherPlan.ExpressionPlan elseBranch,
                                                    OclTypeBinding type) {
        return new OclCypherPlan.IfPlan(
                required(condition, "condition"),
                required(thenBranch, "thenBranch"),
                required(elseBranch, "elseBranch"),
                required(type, "type"));
    }

    public static OclCypherPlan.LetPlan let(String variableName,
                                            OclCypherPlan.ExpressionPlan value,
                                            OclTypeBinding variableType,
                                            OclCypherPlan.ExpressionPlan body,
                                            OclTypeBinding type) {
        return new OclCypherPlan.LetPlan(
                nonBlank(variableName, "variableName"),
                required(value, "value"),
                required(variableType, "variableType"),
                required(body, "body"),
                required(type, "type"));
    }

    public static OclCypherPlan.BinaryPlan binary(String operator,
                                                  OclCypherPlan.ExpressionPlan left,
                                                  OclCypherPlan.ExpressionPlan right,
                                                  OclTypeBinding type) {
        return new OclCypherPlan.BinaryPlan(
                nonBlank(operator, "operator"),
                required(left, "left"),
                required(right, "right"),
                required(type, "type"));
    }

    public static OclCypherPlan.AttributeAccessPlan attributeAccess(OclCypherPlan.ExpressionPlan source,
                                                                    String attributeName,
                                                                    Type attributeType,
                                                                    OclTypeBinding type,
                                                                    MAttribute attribute) {
        return attributeAccess(source, attributeName, attributeType, type, attribute, null);
    }

    public static OclCypherPlan.AttributeAccessPlan attributeAccess(OclCypherPlan.ExpressionPlan source,
                                                                    String attributeName,
                                                                    Type attributeType,
                                                                    OclTypeBinding type,
                                                                    MAttribute attribute,
                                                                    OclCypherPlan.AttributeBinding binding) {
        return new OclCypherPlan.AttributeAccessPlan(
                required(source, "source"),
                nonBlank(attributeName, "attributeName"),
                required(attributeType, "attributeType"),
                required(type, "type"),
                required(attribute, "attribute"),
                binding);
    }

    public static OclCypherPlan.NavigationAccessPlan navigationAccess(OclCypherPlan.ExpressionPlan source,
                                                                      OclMetamodelIndex.NavigationInfo navigation,
                                                                      List<OclCypherPlan.ExpressionPlan> qualifiers,
                                                                      OclTypeBinding type) {
        return navigationAccess(source, navigation, qualifiers, type, null);
    }

    public static OclCypherPlan.NavigationAccessPlan navigationAccess(OclCypherPlan.ExpressionPlan source,
                                                                      OclMetamodelIndex.NavigationInfo navigation,
                                                                      List<OclCypherPlan.ExpressionPlan> qualifiers,
                                                                      OclTypeBinding type,
                                                                      OclCypherPlan.NavigationBinding binding) {
        return new OclCypherPlan.NavigationAccessPlan(
                required(source, "source"),
                required(navigation, "navigation"),
                copyList(qualifiers, "qualifiers"),
                required(type, "type"),
                binding);
    }

    public static OclCypherPlan.MethodCallPlan methodCall(OclCypherPlan.ExpressionPlan source,
                                                          String methodName,
                                                          List<OclCypherPlan.ExpressionPlan> arguments,
                                                          OclTypeBinding type) {
        return new OclCypherPlan.MethodCallPlan(
                required(source, "source"),
                nonBlank(methodName, "methodName"),
                copyList(arguments, "arguments"),
                required(type, "type"));
    }

    public static OclCypherPlan.CollectionOperationPlan collectionOperation(OclCypherPlan.ExpressionPlan source,
                                                                             OclTypeBinding sourceCollectionType,
                                                                             String operationName,
                                                                            List<OclCypherPlan.ExpressionPlan> arguments,
                                                                            OclTypeBinding type) {
        return new OclCypherPlan.CollectionOperationPlan(
                required(source, "source"),
                required(sourceCollectionType, "sourceCollectionType"),
                nonBlank(operationName, "operationName"),
                copyList(arguments, "arguments"),
                required(type, "type"));
    }

    public static OclCypherPlan.IteratorOperationPlan iteratorOperation(OclCypherPlan.ExpressionPlan source,
                                                                         OclTypeBinding sourceCollectionType,
                                                                         String operationName,
                                                                        String iteratorName,
                                                                        OclTypeBinding iteratorVariableType,
                                                                        OclCypherPlan.ExpressionPlan body,
                                                                        OclTypeBinding type) {
        return new OclCypherPlan.IteratorOperationPlan(
                required(source, "source"),
                required(sourceCollectionType, "sourceCollectionType"),
                nonBlank(operationName, "operationName"),
                nonBlank(iteratorName, "iteratorName"),
                required(iteratorVariableType, "iteratorVariableType"),
                required(body, "body"),
                required(type, "type"));
    }

    public static OclCypherPlan.NavigationMatchPlan navigationMatch(OclCypherPlan.ExpressionPlan owner,
                                                                    String targetAlias,
                                                                    OclCypherPlan.NavigationAccessPlan navigation,
                                                                    OclCypherPlan.ExpressionPlan predicate,
                                                                    OclCypherPlan.PredicateMode predicateMode,
                                                                    OclTypeBinding targetType) {
        return new OclCypherPlan.NavigationMatchPlan(
                required(owner, "owner"),
                nonBlank(targetAlias, "targetAlias"),
                required(navigation, "navigation"),
                predicate,
                required(predicateMode, "predicateMode"),
                requireNode(targetType, "targetType"));
    }

    public static OclCypherPlan.ExistsSubqueryPlan existsSubquery(OclCypherPlan.NavigationMatchPlan match,
                                                                  OclTypeBinding type) {
        return new OclCypherPlan.ExistsSubqueryPlan(required(match, "match"), requireBoolean(type, "type"));
    }

    public static OclCypherPlan.NotExistsSubqueryPlan notExistsSubquery(OclCypherPlan.NavigationMatchPlan match,
                                                                        OclTypeBinding type) {
        return new OclCypherPlan.NotExistsSubqueryPlan(required(match, "match"), requireBoolean(type, "type"));
    }

    public static OclCypherPlan.CountSubqueryComparisonPlan countSubqueryComparison(
            OclCypherPlan.NavigationMatchPlan match,
            String operator,
            long literal,
            OclTypeBinding type) {
        return new OclCypherPlan.CountSubqueryComparisonPlan(
                required(match, "match"),
                nonBlank(operator, "operator"),
                literal,
                requireBoolean(type, "type"));
    }

    public static OclCypherPlan.NavigationAggregationPlan navigationAggregation(
            OclCypherPlan.NavigationMatchPlan match,
            OclCypherPlan.ExpressionPlan projection,
            String operationName,
            OclTypeBinding type) {
        return new OclCypherPlan.NavigationAggregationPlan(
                required(match, "match"),
                required(projection, "projection"),
                nonBlank(operationName, "operationName"),
                required(type, "type"));
    }

    public static OclCypherPlan.NavigationUniquenessPlan navigationUniqueness(
            OclCypherPlan.NavigationMatchPlan match,
            OclCypherPlan.ExpressionPlan projection,
            OclTypeBinding type) {
        return new OclCypherPlan.NavigationUniquenessPlan(
                required(match, "match"),
                required(projection, "projection"),
                required(type, "type"));
    }

    private static <T> T required(T value, String name) {
        return Objects.requireNonNull(value, name);
    }

    private static String nonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static OclTypeBinding requireBoolean(OclTypeBinding type, String name) {
        OclTypeBinding checked = required(type, name);
        if (checked.kind() != OclTypeBinding.BindingKind.SCALAR || !"Boolean".equals(checked.typeName())) {
            throw new IllegalArgumentException(name + " must have Boolean type");
        }
        return checked;
    }

    private static OclTypeBinding requireNode(OclTypeBinding type, String name) {
        OclTypeBinding checked = required(type, name);
        if (!checked.isNode()) {
            throw new IllegalArgumentException(name + " must have node type");
        }
        return checked;
    }

    private static <T> List<T> copyList(List<T> values, String name) {
        return List.copyOf(required(values, name));
    }
}
