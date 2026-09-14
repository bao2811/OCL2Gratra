package org.uet.dse.neo4jtgg.ocl.ir;

import org.tzi.use.uml.mm.MAttribute;
import org.tzi.use.uml.ocl.type.Type;
import org.uet.dse.neo4jtgg.ocl.OclMetamodelIndex;
import org.uet.dse.neo4jtgg.ocl.OclTypeBinding;

import java.util.List;

/**
 * Abstract Cypher Query Model for the supported OCL validation backend.
 *
 * <p>This class is the Java implementation of the Cypher Query Model used
 * between the optimized IR layer and concrete Cypher text. Its records are
 * query-shape elements, not raw strings. The renderer lowers these model
 * elements to Cypher concrete syntax.</p>
 *
 * <p>The intended preservation statement for this layer is:</p>
 *
 * <pre>
 * [[ ir ]]_OPT(M, rho) =
 *   evalCypher(T_Text(T_CQ(ir)), encode(M), encodeEnv(rho))
 * </pre>
 *
 * <p>{@link OclCypherPlanner} implements the IR-to-query-model transformation,
 * while {@link OclCypherRenderer} implements the model-to-text transformation.</p>
 */
public final class OclCypherPlan {
    private OclCypherPlan() {
    }

    /**
     * Query model for invariant validation. Rendering this plan returns
     * violating objects, not satisfying objects.
     */
    public record InvariantPlan(String contextClassName, String invariantName, ExpressionPlan predicate,
                                ViolationPolicy violationPolicy,
                                GraphContextBinding graphBinding,
                                EvaluationPolicy evaluationPolicy) {
        public InvariantPlan(String contextClassName, String invariantName, ExpressionPlan predicate) {
            this(contextClassName, invariantName, predicate, ViolationPolicy.NOT_VALIDATION_TRUE,
                    null, EvaluationPolicy.MATERIALIZE_REUSED_EXPRESSIONS);
        }

        public InvariantPlan(String contextClassName, String invariantName, ExpressionPlan predicate,
                             ViolationPolicy violationPolicy) {
            this(contextClassName, invariantName, predicate, violationPolicy,
                    null, EvaluationPolicy.MATERIALIZE_REUSED_EXPRESSIONS);
        }

        public InvariantPlan {
            if (violationPolicy == null) {
                throw new IllegalArgumentException("Invariant violation policy is required");
            }
            if (evaluationPolicy == null) {
                throw new IllegalArgumentException("CQM evaluation policy is required");
            }
        }
    }

    /** Only Boolean true satisfies a certified invariant; every other value is a violation. */
    public enum ViolationPolicy {
        NOT_VALIDATION_TRUE
    }

    /**
     * Physical evaluation contract carried by certified CQM artifacts.  The
     * renderer may inline atomic aliases and parameters, but every compound
     * value used more than once by one lowering rule must cross a
     * materialize-once boundary.
     */
    public enum EvaluationPolicy {
        MATERIALIZE_REUSED_EXPRESSIONS
    }

    /** Canonical graph information that makes an invariant plan self-contained. */
    public record GraphContextBinding(String profileId, String modelKey, String contextClassKey,
                                      String objectLabel, String classLabel,
                                      String conformanceRelationship, String identityProperty) {
    }

    /**
     * Physical realization of the logical attribute observer used by the
     * preservation theorem.  The logical operation is {@code read_G^a}; the
     * canonical Neo4j carrier realizes it by following {@code ObjectHasAttribute}
     * to one {@code AttributeValue} slot and reading its encoded {@code value}.
     */
    public record AttributeBinding(String attributeKey, String slotLabel,
                                   String ownerRelationship, String valueProperty) {
    }

    /** Physical association accessor selected during CQM planning. */
    public record NavigationBinding(String associationKey, String sourceRole, String targetRole,
                                    OclMetamodelIndex.NavigationDirection direction,
                                    String relationshipTypePrefix,
                                    String sourceQualifierProperty,
                                    String targetQualifierProperty) {
    }

    /**
     * Common supertype for Cypher query-shape expressions.
     */
    public sealed interface ExpressionPlan permits VariablePlan, LiteralPlan, SetLiteralPlan, NotPlan, BinaryPlan,
            AttributeAccessPlan, NavigationAccessPlan, MethodCallPlan, CollectionOperationPlan,
            IfPlan, LetPlan,
            IteratorOperationPlan, ExistsSubqueryPlan, NotExistsSubqueryPlan, CountSubqueryComparisonPlan,
            NavigationAggregationPlan, NavigationUniquenessPlan {
        OclTypeBinding type();
    }

    public record VariablePlan(String name, OclTypeBinding type) implements ExpressionPlan {
    }

    public record LiteralPlan(Object value, OclTypeBinding type) implements ExpressionPlan {
    }

    public record SetLiteralPlan(List<ExpressionPlan> elements, OclTypeBinding type) implements ExpressionPlan {
        public SetLiteralPlan {
            elements = List.copyOf(elements);
        }
    }

    public record NotPlan(ExpressionPlan expression, OclTypeBinding type) implements ExpressionPlan {
    }

    public record IfPlan(ExpressionPlan condition, ExpressionPlan thenBranch, ExpressionPlan elseBranch,
                         OclTypeBinding type) implements ExpressionPlan {
    }

    public record LetPlan(String variableName, ExpressionPlan value, OclTypeBinding variableType, ExpressionPlan body,
                          OclTypeBinding type) implements ExpressionPlan {
    }

    public record BinaryPlan(String operator, ExpressionPlan left, ExpressionPlan right,
                             OclTypeBinding type) implements ExpressionPlan {
    }

    public record AttributeAccessPlan(ExpressionPlan source, String attributeName, Type attributeType,
                                      OclTypeBinding type, MAttribute attribute,
                                      AttributeBinding binding) implements ExpressionPlan {
        public AttributeAccessPlan(ExpressionPlan source, String attributeName, Type attributeType,
                                   OclTypeBinding type, MAttribute attribute) {
            this(source, attributeName, attributeType, type, attribute, null);
        }
    }

    public record NavigationAccessPlan(ExpressionPlan source, OclMetamodelIndex.NavigationInfo navigation,
                                       List<ExpressionPlan> qualifiers,
                                       OclTypeBinding type,
                                       NavigationBinding binding) implements ExpressionPlan {
        public NavigationAccessPlan(ExpressionPlan source, OclMetamodelIndex.NavigationInfo navigation,
                                    List<ExpressionPlan> qualifiers, OclTypeBinding type) {
            this(source, navigation, qualifiers, type, null);
        }
    }

    public record MethodCallPlan(ExpressionPlan source, String methodName, List<ExpressionPlan> arguments,
                                 OclTypeBinding type) implements ExpressionPlan {
    }

    public record CollectionOperationPlan(ExpressionPlan source, OclTypeBinding sourceCollectionType,
                                           String operationName, List<ExpressionPlan> arguments,
                                           OclTypeBinding type) implements ExpressionPlan {
    }

    public record IteratorOperationPlan(ExpressionPlan source, OclTypeBinding sourceCollectionType,
                                         String operationName, String iteratorName, OclTypeBinding iteratorVariableType,
                                         ExpressionPlan body, OclTypeBinding type) implements ExpressionPlan {
    }

    /**
     * Reusable query-shape element for association navigation over the
     * graph-encoded UML model. It captures owner, target alias, navigation
     * metadata, and optional iterator predicate.
     */
    public record NavigationMatchPlan(ExpressionPlan owner, String targetAlias, NavigationAccessPlan navigation,
                                      ExpressionPlan predicate, PredicateMode predicateMode,
                                      OclTypeBinding targetType) {
    }

    /**
     * Query shape for existential navigation checks.
     */
    public record ExistsSubqueryPlan(NavigationMatchPlan match, OclTypeBinding type) implements ExpressionPlan {
    }

    /**
     * Query shape for negated existential navigation checks.
     */
    public record NotExistsSubqueryPlan(NavigationMatchPlan match, OclTypeBinding type) implements ExpressionPlan {
    }

    /**
     * Query shape for count-based navigation predicates.
     */
    public record CountSubqueryComparisonPlan(NavigationMatchPlan match, String operator,
                                              long literal, OclTypeBinding type) implements ExpressionPlan {
    }

    /**
     * Query shape for navigation aggregate operations such as sum/min/max.
     */
    public record NavigationAggregationPlan(NavigationMatchPlan match, ExpressionPlan projection,
                                            String operationName, OclTypeBinding type) implements ExpressionPlan {
    }

    /**
     * Query shape for uniqueness checks over navigation results.
     */
    public record NavigationUniquenessPlan(NavigationMatchPlan match, ExpressionPlan projection,
                                           OclTypeBinding type) implements ExpressionPlan {
    }

    /**
     * Controls how an iterator predicate is embedded into a navigation match.
     */
    public enum PredicateMode {
        NONE,
        NORMAL,
        NEGATED
    }
}
