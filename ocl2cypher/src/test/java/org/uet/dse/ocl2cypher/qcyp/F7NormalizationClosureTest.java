package org.uet.dse.ocl2cypher.qcyp;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.uet.dse.ocl2cypher.core.CoreDeclaration;
import org.uet.dse.ocl2cypher.core.CoreExpr;
import org.uet.dse.ocl2cypher.diagnostics.SourceSpan;
import org.uet.dse.ocl2cypher.runtime.OclType;

import static org.junit.jupiter.api.Assertions.*;

/** Closed-hierarchy and query-contract refinement obligations for F-7. */
class F7NormalizationClosureTest {
    private static final SourceSpan S = new SourceSpan(1, 4, 1, 2);
    private static final OclType PERSON = OclType.clazz("Person");
    private static final OclType SET_INT = OclType.set(OclType.INTEGER);

    @Test
    void normalizationDispatchCoversEverySealedExpressionAndPlanClass() {
        List<QNode.QExpr> expressions = expressionRepresentatives();
        List<QNode.QPlan> plans = planRepresentatives();

        assertEquals(permitted(QNode.QExpr.class), runtimeClasses(expressions));
        assertEquals(permitted(QNode.QPlan.class), runtimeClasses(plans));

        for (QNode.QExpr expression : expressions) {
            QNode.QExpr normalized = assertDoesNotThrow(
                    () -> NormQ.normalizeExpr(expression), expression.getClass().getSimpleName());
            assertEquals(expression.type, normalized.type);
            assertEquals(expression.span, normalized.span);
            assertEquals(NormQ.structuralKey(normalized),
                    NormQ.structuralKey(NormQ.normalizeExpr(normalized)));
            assertSame(normalized, NormQ.normalizeExpr(normalized),
                    "a canonical expression must not be copied");
            assertCanonical(normalized);
        }
        for (QNode.QPlan plan : plans) {
            QNode.QPlan normalized = assertDoesNotThrow(
                    () -> NormQ.normalizePlan(plan), plan.getClass().getSimpleName());
            assertEquals(plan.type, normalized.type);
            assertEquals(plan.span, normalized.span);
            assertEquals(NormQ.structuralKey(normalized),
                    NormQ.structuralKey(NormQ.normalizePlan(normalized)));
            assertSame(normalized, NormQ.normalizePlan(normalized),
                    "a canonical plan must not be copied");
            assertCanonical(normalized);
        }
    }

    @Test
    void queryNormalizationChangesOnlyTheUniqueBody() {
        QNode.QExpr set = setLiteral();
        QNode.QExpr alias = new QNode.QExpr.CountFamily(S, QNode.QKind.SIZE,
                set, null, OclType.INTEGER);
        QQuery expressionQuery = new QQuery(alias, null, QQuery.QResultShape.SCALAR,
                QQuery.QueryMode.VALUE, OclType.INTEGER, null, null, false);

        QQuery expressionNormalized = NormQ.normalize(expressionQuery);
        assertTrue(expressionNormalized.expressionBody() instanceof QNode.QExpr.Unary);
        assertRootContractPreserved(expressionQuery, expressionNormalized);
        assertEquals(NormQ.structuralKey(expressionNormalized.expressionBody()),
                NormQ.structuralKey(NormQ.normalize(expressionNormalized).expressionBody()));
        assertSame(expressionNormalized, NormQ.normalize(expressionNormalized),
                "a canonical query must not be copied");

        QNode.QPlan plan = new QNode.QPlan.FromCollection(S, set);
        QQuery planQuery = new QQuery(null, plan, QQuery.QResultShape.SET,
                QQuery.QueryMode.VALUE, SET_INT, null, null, false);
        QQuery planNormalized = NormQ.normalize(planQuery);
        assertNotNull(planNormalized.planBody());
        assertRootContractPreserved(planQuery, planNormalized);
        assertEquals(NormQ.structuralKey(planNormalized.planBody()),
                NormQ.structuralKey(NormQ.normalize(planNormalized).planBody()));
        assertSame(planNormalized, NormQ.normalize(planNormalized),
                "a canonical query must not be copied");
    }

    private static List<QNode.QExpr> expressionRepresentatives() {
        CoreDeclaration integer = new CoreDeclaration(1, "x", CoreDeclaration.Kind.LET,
                OclType.INTEGER);
        CoreDeclaration iterator = new CoreDeclaration(2, "i",
                CoreDeclaration.Kind.ITERATOR, OclType.INTEGER);
        CoreDeclaration object = new CoreDeclaration(3, "p",
                CoreDeclaration.Kind.ITERATOR, PERSON);
        QNode.QExpr one = integer(1);
        QNode.QExpr set = setLiteral();
        QNode.QPlan source = new QNode.QPlan.FromCollection(S, set);
        QNode.QExpr objectVariable = new QNode.QExpr.Variable(S, object);
        QNode.QExpr truth = bool(true);

        List<QNode.QExpr> nodes = new ArrayList<>();
        nodes.add(new QNode.QExpr.Variable(S, integer));
        nodes.add(new QNode.QExpr.Parameter(S, new QNode.QParameter("threshold", OclType.INTEGER)));
        nodes.add(new QNode.QExpr.Bottom(S, OclType.INTEGER));
        nodes.add(one);
        nodes.add(new QNode.QExpr.Coerce(S, CoreExpr.CoercionKind.INTEGER_TO_REAL,
                OclType.INTEGER, one, OclType.REAL));
        nodes.add(new QNode.QExpr.Let(S, integer, one, new QNode.QExpr.Variable(S, integer)));
        nodes.add(new QNode.QExpr.IfExpr(S, truth, one, integer(2), OclType.INTEGER));
        nodes.add(new QNode.QExpr.ReadAttribute(S, objectVariable, "Person", "age",
                OclType.INTEGER));
        nodes.add(new QNode.QExpr.NavigateOne(S, objectVariable, "friendship", "friend",
                List.of(one), PERSON, false, false, false));
        nodes.add(new QNode.QExpr.TypeTest(S, CoreExpr.TypeTestKind.CONFORMS_TO,
                objectVariable, "Person"));
        nodes.add(new QNode.QExpr.TypeCast(S, objectVariable, "Person"));
        nodes.add(new QNode.QExpr.Unary(S, CoreExpr.UnaryOp.NUMERIC_NEGATE, one,
                OclType.INTEGER));
        nodes.add(new QNode.QExpr.Binary(S, CoreExpr.BinaryOp.NUMERIC_ADD, one, integer(2),
                OclType.INTEGER));
        nodes.add(new QNode.QExpr.Exists3(S, source, iterator, truth));
        nodes.add(new QNode.QExpr.ForAll3(S, source, iterator, truth));
        nodes.add(set);
        nodes.add(new QNode.QExpr.IncludesFamily(S, QNode.QKind.INCLUDES, set, one,
                OclType.BOOLEAN));
        nodes.add(new QNode.QExpr.CountFamily(S, QNode.QKind.SIZE, set, null,
                OclType.INTEGER));
        nodes.add(new QNode.QExpr.SetAlgebra(S, CoreExpr.BinaryOp.SET_UNION, set, set,
                SET_INT));
        nodes.add(new QNode.QExpr.Materialize(S, source));
        return List.copyOf(nodes);
    }

    private static List<QNode.QPlan> planRepresentatives() {
        CoreDeclaration iterator = new CoreDeclaration(4, "i",
                CoreDeclaration.Kind.ITERATOR, OclType.INTEGER);
        CoreDeclaration object = new CoreDeclaration(5, "p",
                CoreDeclaration.Kind.ITERATOR, PERSON);
        CoreDeclaration binder = new CoreDeclaration(6, "n", CoreDeclaration.Kind.LET,
                OclType.INTEGER);
        QNode.QExpr set = setLiteral();
        QNode.QPlan source = new QNode.QPlan.FromCollection(S, set);
        QNode.QExpr objectVariable = new QNode.QExpr.Variable(S, object);
        return List.of(
                source,
                new QNode.QPlan.ScanClass(S, "Person", object),
                new QNode.QPlan.NavigateMany(S, objectVariable, "friendship", "friends",
                        List.of(integer(1)), OclType.set(PERSON), false, false, false),
                new QNode.QPlan.Filter(S, source, iterator, bool(true), true),
                new QNode.QPlan.Collect(S, source, iterator, integer(1)),
                new QNode.QPlan.Distinct(S, source),
                new QNode.QPlan.PlanLet(S, binder, integer(1), source));
    }

    private static QNode.QExpr setLiteral() {
        return new QNode.QExpr.CollectionLiteral(S, CoreExpr.CollectionKind.SET,
                List.of(integer(1), integer(2)), SET_INT);
    }

    private static QNode.QExpr integer(int value) {
        return new QNode.QExpr.Constant(S, OclType.INTEGER, BigInteger.valueOf(value));
    }

    private static QNode.QExpr bool(boolean value) {
        return new QNode.QExpr.Constant(S, OclType.BOOLEAN, value);
    }

    private static Set<Class<?>> permitted(Class<?> sealedClass) {
        return Arrays.stream(sealedClass.getPermittedSubclasses()).collect(Collectors.toSet());
    }

    private static Set<Class<?>> runtimeClasses(List<?> values) {
        return values.stream().map(Object::getClass).collect(Collectors.toSet());
    }

    private static void assertRootContractPreserved(QQuery before, QQuery after) {
        assertEquals(before.resultShape(), after.resultShape());
        assertEquals(before.mode(), after.mode());
        assertSame(before.resultType(), after.resultType());
        assertEquals(before.contextClassKey(), after.contextClassKey());
        assertSame(before.selfVariable(), after.selfVariable());
        assertEquals(before.identityProjection(), after.identityProjection());
    }

    private static void assertCanonical(Object node) {
        if (node instanceof QNode.QExpr expression) {
            assertFalse(expression instanceof QNode.QExpr.IncludesFamily);
            assertFalse(expression instanceof QNode.QExpr.CountFamily);
            assertFalse(expression instanceof QNode.QExpr.SetAlgebra);
        }
    }
}
