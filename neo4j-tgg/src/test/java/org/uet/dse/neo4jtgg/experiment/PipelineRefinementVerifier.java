package org.uet.dse.neo4jtgg.experiment;

import org.uet.dse.neo4jtgg.ocl.OclSemanticBinder;
import org.uet.dse.neo4jtgg.ocl.ir.OclCypherPlan;
import org.uet.dse.neo4jtgg.ocl.ir.OclIr;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Independent executable form of the local refinement tables used in T1-T5. */
final class PipelineRefinementVerifier {
    private PipelineRefinementVerifier() {
    }

    static void verify(InstrumentedCompilationResult result) {
        assertEquals(result.bound().ast().className, result.validationAlgebra().contextClassName());
        assertEquals(result.bound().ast().invName, result.validationAlgebra().invariantName());
        verifyBoundToVa(result.bound().expression(), result.validationAlgebra().predicate());
        assertEquals(result.validationAlgebra().predicate().type(),
                result.normalizedValidationAlgebra().predicate().type());
        verifyOptimizedToPlan(result.normalizedValidationAlgebra().predicate(), result.queryPlan().predicate());
    }

    private static void verifyBoundToVa(OclSemanticBinder.BoundExpression bound, OclIr.Expression va) {
        assertNotNull(bound.type());
        assertEquals(bound.type(), va.type());
        if (bound instanceof OclSemanticBinder.BoundVariable) {
            assertTrue(va instanceof OclIr.Variable);
        } else if (bound instanceof OclSemanticBinder.BoundLiteral) {
            assertTrue(va instanceof OclIr.Literal);
        } else if (bound instanceof OclSemanticBinder.BoundSetLiteral source) {
            assertTrue(va instanceof OclIr.SetLiteral);
            verifyLists(source.elements(), ((OclIr.SetLiteral) va).elements());
        } else if (bound instanceof OclSemanticBinder.BoundNot source) {
            assertTrue(va instanceof OclIr.Not);
            verifyBoundToVa(source.expression(), ((OclIr.Not) va).expression());
        } else if (bound instanceof OclSemanticBinder.BoundIf source) {
            assertTrue(va instanceof OclIr.If);
            OclIr.If target = (OclIr.If) va;
            verifyBoundToVa(source.condition(), target.condition());
            verifyBoundToVa(source.thenBranch(), target.thenBranch());
            verifyBoundToVa(source.elseBranch(), target.elseBranch());
        } else if (bound instanceof OclSemanticBinder.BoundLet source) {
            assertTrue(va instanceof OclIr.Let);
            OclIr.Let target = (OclIr.Let) va;
            assertEquals(source.ast().variableName, target.variableName());
            verifyBoundToVa(source.value(), target.value());
            verifyBoundToVa(source.body(), target.body());
        } else if (bound instanceof OclSemanticBinder.BoundBinary source) {
            assertTrue(va instanceof OclIr.Binary);
            OclIr.Binary target = (OclIr.Binary) va;
            assertEquals(source.ast().op, target.operator());
            verifyBoundToVa(source.left(), target.left());
            verifyBoundToVa(source.right(), target.right());
        } else if (bound instanceof OclSemanticBinder.BoundProperty source) {
            verifyProperty(source, va);
        } else if (bound instanceof OclSemanticBinder.BoundMethodCall source) {
            assertTrue(va instanceof OclIr.MethodCall);
            OclIr.MethodCall target = (OclIr.MethodCall) va;
            assertEquals(source.ast().methodName, target.methodName());
            verifyBoundToVa(source.source(), target.source());
            verifyLists(source.arguments(), target.arguments());
        } else if (bound instanceof OclSemanticBinder.BoundCollectionOperation source) {
            assertTrue(va instanceof OclIr.CollectionOperation);
            OclIr.CollectionOperation target = (OclIr.CollectionOperation) va;
            assertEquals(source.ast().opName, target.operationName());
            verifyBoundToVa(source.source(), target.source());
            verifyLists(source.arguments(), target.arguments());
        } else if (bound instanceof OclSemanticBinder.BoundIterator source) {
            assertTrue(va instanceof OclIr.IteratorOperation);
            OclIr.IteratorOperation target = (OclIr.IteratorOperation) va;
            assertEquals(source.ast().operation, target.operationName());
            assertEquals(source.ast().iteratorName, target.iteratorName());
            verifyBoundToVa(source.source(), target.source());
            verifyBoundToVa(source.body(), target.body());
        } else {
            throw new AssertionError("Unverified Bound constructor: " + bound.getClass().getName());
        }
    }

    private static void verifyProperty(OclSemanticBinder.BoundProperty source, OclIr.Expression va) {
        if (source.isAttribute()) {
            assertTrue(va instanceof OclIr.AttributeAccess);
            OclIr.AttributeAccess target = (OclIr.AttributeAccess) va;
            assertSame(source.attribute(), target.attribute());
            assertEquals(source.ast().name, target.attributeName());
            verifyBoundToVa(source.source(), target.source());
        } else {
            assertTrue(va instanceof OclIr.NavigationAccess);
            OclIr.NavigationAccess target = (OclIr.NavigationAccess) va;
            assertEquals(source.navigation(), target.navigation());
            verifyBoundToVa(source.source(), target.source());
            verifyLists(source.qualifiers(), target.qualifiers());
        }
    }

    private static void verifyLists(List<? extends OclSemanticBinder.BoundExpression> source,
                                    List<? extends OclIr.Expression> target) {
        assertEquals(source.size(), target.size());
        for (int index = 0; index < source.size(); index++) {
            verifyBoundToVa(source.get(index), target.get(index));
        }
    }

    private static void verifyOptimizedToPlan(OclIr.Expression source, OclCypherPlan.ExpressionPlan target) {
        assertNotNull(source.type());
        assertEquals(source.type(), target.type());
        if (source instanceof OclIr.Variable) {
            assertTrue(target instanceof OclCypherPlan.VariablePlan);
        } else if (source instanceof OclIr.Literal) {
            assertTrue(target instanceof OclCypherPlan.LiteralPlan);
        } else if (source instanceof OclIr.SetLiteral value) {
            assertTrue(target instanceof OclCypherPlan.SetLiteralPlan);
            verifyPlanLists(value.elements(), ((OclCypherPlan.SetLiteralPlan) target).elements());
        } else if (source instanceof OclIr.Not value) {
            assertTrue(target instanceof OclCypherPlan.NotPlan);
            verifyOptimizedToPlan(value.expression(), ((OclCypherPlan.NotPlan) target).expression());
        } else if (source instanceof OclIr.If value) {
            assertTrue(target instanceof OclCypherPlan.IfPlan);
            OclCypherPlan.IfPlan plan = (OclCypherPlan.IfPlan) target;
            verifyOptimizedToPlan(value.condition(), plan.condition());
            verifyOptimizedToPlan(value.thenBranch(), plan.thenBranch());
            verifyOptimizedToPlan(value.elseBranch(), plan.elseBranch());
        } else if (source instanceof OclIr.Let value) {
            assertTrue(target instanceof OclCypherPlan.LetPlan);
            OclCypherPlan.LetPlan plan = (OclCypherPlan.LetPlan) target;
            assertEquals(value.variableName(), plan.variableName());
            verifyOptimizedToPlan(value.value(), plan.value());
            verifyOptimizedToPlan(value.body(), plan.body());
        } else if (source instanceof OclIr.Binary value) {
            assertTrue(target instanceof OclCypherPlan.BinaryPlan);
            OclCypherPlan.BinaryPlan plan = (OclCypherPlan.BinaryPlan) target;
            assertEquals(value.operator(), plan.operator());
            verifyOptimizedToPlan(value.left(), plan.left());
            verifyOptimizedToPlan(value.right(), plan.right());
        } else if (source instanceof OclIr.AttributeAccess value) {
            assertTrue(target instanceof OclCypherPlan.AttributeAccessPlan);
            verifyOptimizedToPlan(value.source(), ((OclCypherPlan.AttributeAccessPlan) target).source());
        } else if (source instanceof OclIr.NavigationAccess value) {
            assertTrue(target instanceof OclCypherPlan.NavigationAccessPlan);
            OclCypherPlan.NavigationAccessPlan plan = (OclCypherPlan.NavigationAccessPlan) target;
            assertEquals(value.navigation(), plan.navigation());
            verifyOptimizedToPlan(value.source(), plan.source());
            verifyPlanLists(value.qualifiers(), plan.qualifiers());
        } else if (source instanceof OclIr.MethodCall value) {
            assertTrue(target instanceof OclCypherPlan.MethodCallPlan);
            OclCypherPlan.MethodCallPlan plan = (OclCypherPlan.MethodCallPlan) target;
            assertEquals(value.methodName(), plan.methodName());
            verifyOptimizedToPlan(value.source(), plan.source());
            verifyPlanLists(value.arguments(), plan.arguments());
        } else if (source instanceof OclIr.CollectionOperation value) {
            assertTrue(target instanceof OclCypherPlan.CollectionOperationPlan);
            OclCypherPlan.CollectionOperationPlan plan = (OclCypherPlan.CollectionOperationPlan) target;
            assertEquals(value.operationName(), plan.operationName());
            verifyOptimizedToPlan(value.source(), plan.source());
            verifyPlanLists(value.arguments(), plan.arguments());
        } else if (source instanceof OclIr.IteratorOperation value) {
            assertTrue(target instanceof OclCypherPlan.IteratorOperationPlan);
            OclCypherPlan.IteratorOperationPlan plan = (OclCypherPlan.IteratorOperationPlan) target;
            assertEquals(value.operationName(), plan.operationName());
            assertEquals(value.iteratorName(), plan.iteratorName());
            verifyOptimizedToPlan(value.source(), plan.source());
            verifyOptimizedToPlan(value.body(), plan.body());
        } else if (source instanceof OclIr.NavigationPredicateCheck value) {
            boolean expected = switch (value.kind()) {
                case EXISTS -> target instanceof OclCypherPlan.ExistsSubqueryPlan;
                case NOT_EXISTS, FORALL -> target instanceof OclCypherPlan.NotExistsSubqueryPlan;
            };
            assertTrue(expected, "Unexpected predicate plan " + target.getClass().getSimpleName());
        } else if (source instanceof OclIr.NavigationCountComparison) {
            assertTrue(target instanceof OclCypherPlan.CountSubqueryComparisonPlan);
        } else if (source instanceof OclIr.NavigationAggregation) {
            assertTrue(target instanceof OclCypherPlan.NavigationAggregationPlan);
        } else if (source instanceof OclIr.NavigationUniquenessCheck) {
            assertTrue(target instanceof OclCypherPlan.NavigationUniquenessPlan);
        } else {
            throw new AssertionError("Unverified optimized constructor: " + source.getClass().getName());
        }
    }

    private static void verifyPlanLists(List<? extends OclIr.Expression> source,
                                        List<? extends OclCypherPlan.ExpressionPlan> target) {
        assertEquals(source.size(), target.size());
        for (int index = 0; index < source.size(); index++) {
            verifyOptimizedToPlan(source.get(index), target.get(index));
        }
    }
}
