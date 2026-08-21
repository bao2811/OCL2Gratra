package org.uet.dse.neo4jtgg.ocl.ir;

/**
 * Namespace for the OCL Semantic IR metamodel.
 *
 * <p>The physical node records are still declared in {@link OclIr} for API
 * compatibility, but semantic nodes implement {@link OclIr.SemanticExpression}.
 * This facade makes the research-level model boundary explicit in code without
 * forcing a large migration in one step.</p>
 */
public final class OclSemanticIr {
    private OclSemanticIr() {
    }

    public static boolean isSemantic(OclIr.Expression expression) {
        return expression instanceof OclIr.SemanticExpression;
    }

    public static OclIr.SemanticExpression requireSemantic(OclIr.Expression expression) {
        if (expression instanceof OclIr.SemanticExpression semanticExpression) {
            return semanticExpression;
        }
        throw new IllegalArgumentException(
                "Expected OCL Semantic IR expression but got " + expression.getClass().getSimpleName());
    }

    public static OclIr.SemanticExpression requireSemantic(OclIr.InvariantQuery query) {
        if (query.stage() != OclIr.Stage.SEMANTIC) {
            throw new IllegalArgumentException(
                    "Expected Semantic IR invariant but got stage " + query.stage());
        }
        return requireSemantic(query.predicate());
    }
}
