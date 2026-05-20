package org.uet.dse.neo4jtgg.ocl.ir;

import org.tzi.use.uml.mm.MAttribute;
import org.tzi.use.uml.ocl.type.Type;
import org.uet.dse.neo4jtgg.ocl.OclMetamodelIndex;
import org.uet.dse.neo4jtgg.ocl.OclTypeBinding;

import java.util.List;

public final class OclCypherPlan {
    private OclCypherPlan() {
    }

    public record InvariantPlan(String contextClassName, String invariantName, ExpressionPlan predicate) {
    }

    public sealed interface ExpressionPlan permits VariablePlan, LiteralPlan, NotPlan, BinaryPlan,
            AttributeAccessPlan, NavigationAccessPlan, MethodCallPlan, CollectionOperationPlan,
            IfPlan, LetPlan,
            IteratorOperationPlan, ExistsSubqueryPlan, NotExistsSubqueryPlan, CountSubqueryComparisonPlan {
        OclTypeBinding type();
    }

    public record VariablePlan(String name, OclTypeBinding type) implements ExpressionPlan {
    }

    public record LiteralPlan(Object value, OclTypeBinding type) implements ExpressionPlan {
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

    public record NavigationMatchPlan(ExpressionPlan owner, String targetAlias, NavigationAccessPlan navigation,
                                      ExpressionPlan predicate, PredicateMode predicateMode,
                                      OclTypeBinding targetType) {
    }

    public record ExistsSubqueryPlan(NavigationMatchPlan match, OclTypeBinding type) implements ExpressionPlan {
    }

    public record NotExistsSubqueryPlan(NavigationMatchPlan match, OclTypeBinding type) implements ExpressionPlan {
    }

    public record CountSubqueryComparisonPlan(NavigationMatchPlan match, String operator,
                                              long literal, OclTypeBinding type) implements ExpressionPlan {
    }

    public enum PredicateMode {
        NONE,
        NORMAL,
        NEGATED
    }
}
