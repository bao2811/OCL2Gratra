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
    public record InvariantPlan(String contextClassName, String invariantName, ExpressionPlan predicate) {
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

    public record LetPlan(String variableName, ExpressionPlan value, ExpressionPlan body,
                          OclTypeBinding type) implements ExpressionPlan {
    }

    public record BinaryPlan(String operator, ExpressionPlan left, ExpressionPlan right,
                             OclTypeBinding type) implements ExpressionPlan {
    }

    public record AttributeAccessPlan(ExpressionPlan source, String attributeName, Type attributeType,
                                      OclTypeBinding type, MAttribute attribute) implements ExpressionPlan {
    }

    public record NavigationAccessPlan(ExpressionPlan source, OclMetamodelIndex.NavigationInfo navigation,
                                       List<ExpressionPlan> qualifiers,
                                       OclTypeBinding type) implements ExpressionPlan {
    }

    public record MethodCallPlan(ExpressionPlan source, String methodName, List<ExpressionPlan> arguments,
                                 OclTypeBinding type) implements ExpressionPlan {
    }

    public record CollectionOperationPlan(ExpressionPlan source, String operationName, List<ExpressionPlan> arguments,
                                          OclTypeBinding type) implements ExpressionPlan {
    }

    public record IteratorOperationPlan(ExpressionPlan source, String operationName, String iteratorName,
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
