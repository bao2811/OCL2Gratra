package org.uet.dse.neo4jtgg.ocl.ir;

/**
 * Namespace for the Optimized IR metamodel.
 *
 * <p>Optimized expressions are the output of {@link OclIrOptimizer}. They may
 * be unchanged semantic nodes or graph-oriented rewrite nodes such as
 * {@link OclIr.NavigationPredicateCheck}. Keeping this facade separate from
 * {@link OclSemanticIr} makes the M2M step {@code T_OPT} visible in the code
 * while preserving the existing {@link OclIr.Expression} API.</p>
 */
public final class OclOptimizedIr {
    private OclOptimizedIr() {
    }

    public static boolean isOptimized(OclIr.Expression expression) {
        return expression instanceof OclIr.OptimizedExpression;
    }

    public static OclIr.OptimizedExpression requireOptimized(OclIr.Expression expression) {
        if (expression instanceof OclIr.OptimizedExpression optimizedExpression) {
            return optimizedExpression;
        }
        throw new IllegalArgumentException(
                "Expected Optimized IR expression but got " + expression.getClass().getSimpleName());
    }

    public static OclIr.OptimizedExpression requireOptimized(OclIr.InvariantQuery query) {
        if (query.stage() != OclIr.Stage.OPTIMIZED
                || !OclIrOptimizer.VERSION.equals(query.producerVersion())) {
            throw new IllegalArgumentException(
                    "Cypher planning requires an invariant produced by " + OclIrOptimizer.VERSION);
        }
        return requireOptimized(query.predicate());
    }

    /** Opaque public planning input; only the optimizer can construct it. */
    public static final class Artifact {
        private final OclIr.OptimizedExpression expression;
        private final String producerVersion;

        private Artifact(OclIr.OptimizedExpression expression, String producerVersion) {
            this.expression = expression;
            this.producerVersion = producerVersion;
        }

        OclIr.OptimizedExpression requireCurrent() {
            if (!OclIrOptimizer.VERSION.equals(producerVersion)) {
                throw new IllegalArgumentException("Stale Optimized IR artifact: " + producerVersion);
            }
            return expression;
        }
    }

    static Artifact artifact(OclIr.OptimizedExpression expression, String producerVersion) {
        return new Artifact(expression, producerVersion);
    }

    public static OclIr.NavigationPredicateCheck navigationPredicateCheck(
            OclIr.NavigationAccess navigation,
            String iteratorName,
            OclIr.Expression predicate,
            OclIr.NavigationPredicateKind kind,
            org.uet.dse.neo4jtgg.ocl.OclTypeBinding type) {
        return new OclIr.NavigationPredicateCheck(navigation, iteratorName, predicate, kind, type);
    }

    public static OclIr.NavigationCountComparison navigationCountComparison(
            OclIr.NavigationAccess navigation,
            String iteratorName,
            OclIr.Expression predicate,
            String operator,
            long literal,
            org.uet.dse.neo4jtgg.ocl.OclTypeBinding type) {
        return new OclIr.NavigationCountComparison(navigation, iteratorName, predicate, operator, literal, type);
    }

    public static OclIr.NavigationAggregation navigationAggregation(
            OclIr.NavigationAccess navigation,
            String iteratorName,
            OclIr.Expression predicate,
            OclIr.Expression projection,
            String operationName,
            org.uet.dse.neo4jtgg.ocl.OclTypeBinding type) {
        return new OclIr.NavigationAggregation(navigation, iteratorName, predicate, projection, operationName, type);
    }

    public static OclIr.NavigationUniquenessCheck navigationUniquenessCheck(
            OclIr.NavigationAccess navigation,
            String iteratorName,
            OclIr.Expression predicate,
            OclIr.Expression projection,
            org.uet.dse.neo4jtgg.ocl.OclTypeBinding type) {
        return new OclIr.NavigationUniquenessCheck(navigation, iteratorName, predicate, projection, type);
    }
}
