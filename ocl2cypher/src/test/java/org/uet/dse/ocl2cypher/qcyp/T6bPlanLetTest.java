package org.uet.dse.ocl2cypher.qcyp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.uet.dse.ocl2cypher.core.CoreDeclaration;
import org.uet.dse.ocl2cypher.core.CoreExpr;
import org.uet.dse.ocl2cypher.core.CoreInterpreter;
import org.uet.dse.ocl2cypher.diagnostics.SourceSpan;
import org.uet.dse.ocl2cypher.graph.GraphModel;
import org.uet.dse.ocl2cypher.runtime.OclType;
import org.uet.dse.ocl2cypher.runtime.OclValue;
import org.uet.dse.ocl2cypher.source.model.SchemaModel;

class T6bPlanLetTest {
    private static final SourceSpan S = SourceSpan.UNKNOWN;
    private static final SchemaModel SM = SchemaModel.builder("t6b-plan-let").build();
    private static final GraphModel G = new GraphModel("t6b-plan-let");

    @Test
    void planLetUsesDeclarationIdentityOldInitializerEnvironmentAndLexicalChild() {
        int checked = 0;
        for (CoreExpr.CollectionKind kind : CoreExpr.CollectionKind.values()) {
            CoreDeclaration outer = new CoreDeclaration(
                    1, "x", CoreDeclaration.Kind.LET, OclType.INTEGER);
            CoreDeclaration inner = new CoreDeclaration(
                    2, "x", CoreDeclaration.Kind.LET, OclType.INTEGER);
            CoreInterpreter.Env environment = new CoreInterpreter.Env();
            environment.bind(outer, integerValue(7));
            QNode.QExpr outerReference = new QNode.QExpr.Variable(S, outer);
            QNode.QExpr innerReference = new QNode.QExpr.Variable(S, inner);

            check(new QNode.QPlan.PlanLet(
                            S, inner, outerReference, list(kind, innerReference, outerReference)),
                    environment,
                    kind == CoreExpr.CollectionKind.SET ? List.of(7) : List.of(7, 7));
            check(new QNode.QPlan.PlanLet(
                            S, inner, integer(9), list(kind, outerReference, innerReference)),
                    environment,
                    List.of(7, 9));

            QNode.QExpr scalarBottom = new QNode.QExpr.Bottom(S, OclType.INTEGER);
            check(new QNode.QPlan.PlanLet(S, inner, scalarBottom, list(kind, integer(3))),
                    environment, List.of(3));
            check(new QNode.QPlan.PlanLet(S, inner, scalarBottom, list(kind)),
                    environment, List.of());
            check(new QNode.QPlan.PlanLet(S, inner, scalarBottom, list(kind, innerReference)),
                    environment, java.util.Collections.singletonList(null));

            OclType collectionType = kind == CoreExpr.CollectionKind.SET
                    ? OclType.set(OclType.INTEGER) : OclType.bag(OclType.INTEGER);
            CoreDeclaration collection = new CoreDeclaration(
                    3, "c", CoreDeclaration.Kind.LET, collectionType);
            QNode.QExpr collectionBottom = new QNode.QExpr.Bottom(S, collectionType);
            check(new QNode.QPlan.PlanLet(
                            S,
                            collection,
                            collectionBottom,
                            new QNode.QPlan.FromCollection(
                                    S, new QNode.QExpr.Variable(S, collection))),
                    environment,
                    null);
            check(new QNode.QPlan.PlanLet(
                            S, collection, collectionBottom, list(kind, integer(4))),
                    environment,
                    List.of(4));
            checked += 7;

            assertEquals(integerValue(7), environment.lookup(outer));
            assertThrows(IllegalStateException.class, () -> environment.lookup(inner));
        }
        assertEquals(14, checked);
    }

    private static QNode.QExpr integer(int value) {
        return new QNode.QExpr.Constant(S, OclType.INTEGER, BigInteger.valueOf(value));
    }

    private static OclValue.IntegerValue integerValue(int value) {
        return new OclValue.IntegerValue(BigInteger.valueOf(value));
    }

    private static QNode.QPlan list(CoreExpr.CollectionKind kind, QNode.QExpr... items) {
        OclType type = kind == CoreExpr.CollectionKind.SET
                ? OclType.set(OclType.INTEGER) : OclType.bag(OclType.INTEGER);
        return new QNode.QPlan.FromCollection(
                S, new QNode.QExpr.CollectionLiteral(S, kind, List.of(items), type));
    }

    private static void check(
            QNode.QPlan plan, CoreInterpreter.Env environment, List<Integer> expected) {
        OclValue value = QInterpreter.evalExpr(
                SM, G, environment, new QNode.QExpr.Materialize(S, plan));
        assertEquals(plan.type, value.type());
        if (expected == null) {
            assertTrue(value.isBottom());
            return;
        }
        Collection<OclValue> items = value instanceof OclValue.SetValue set
                ? set.members() : ((OclValue.BagValue) value).occurrences();
        List<Integer> actual = new ArrayList<>();
        for (OclValue item : items) {
            actual.add(item.isBottom()
                    ? null : ((OclValue.IntegerValue) item).value().intValueExact());
        }
        assertEquals(expected, actual);
    }
}
