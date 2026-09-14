package org.uet.dse.ocl2cypher.qcyp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.uet.dse.ocl2cypher.core.CoreExpr;
import org.uet.dse.ocl2cypher.core.CoreInterpreter;
import org.uet.dse.ocl2cypher.diagnostics.SourceSpan;
import org.uet.dse.ocl2cypher.graph.GraphModel;
import org.uet.dse.ocl2cypher.runtime.OclType;
import org.uet.dse.ocl2cypher.runtime.OclValue;
import org.uet.dse.ocl2cypher.source.model.SchemaModel;

class T3BridgeTest {
    private static final SourceSpan S = SourceSpan.UNKNOWN;
    private static final SchemaModel SM = SchemaModel.builder("t3").build();
    private static final GraphModel G = new GraphModel("t3");
    private static final CoreInterpreter.Env ENV = new CoreInterpreter.Env();

    @Test
    void fromCollectionThenMaterializePreservesEveryFiniteSetBagCarrierCase() {
        int checked = 0;
        for (CoreExpr.CollectionKind kind : CoreExpr.CollectionKind.values()) {
            OclType type = kind == CoreExpr.CollectionKind.SET
                    ? OclType.set(OclType.INTEGER) : OclType.bag(OclType.INTEGER);
            check(new QNode.QExpr.Bottom(S, type), true, null);
            checked++;
            for (int length = 0; length <= 4; length++) {
                int limit = (int) Math.pow(3, length);
                for (int code = 0; code < limit; code++) {
                    int remaining = code;
                    int[] counts = new int[3];
                    List<QNode.QExpr> items = new ArrayList<>();
                    for (int index = 0; index < length; index++) {
                        int digit = remaining % 3;
                        remaining /= 3;
                        counts[digit]++;
                        items.add(digit == 0
                                ? new QNode.QExpr.Bottom(S, OclType.INTEGER)
                                : new QNode.QExpr.Constant(S, OclType.INTEGER,
                                        BigInteger.valueOf(digit - 1)));
                    }
                    check(new QNode.QExpr.CollectionLiteral(S, kind, items, type),
                            false, counts);
                    checked++;
                }
            }
        }
        assertEquals(244, checked);
    }

    private static void check(QNode.QExpr expression, boolean bottom, int[] counts) {
        QNode.QPlan planNode = new QNode.QPlan.FromCollection(S, expression);
        QNode.QExpr materialized = new QNode.QExpr.Materialize(S, planNode);
        OclValue before = QInterpreter.evalExpr(SM, G, ENV, expression);
        List<OclValue> plan = QInterpreter.evalPlan(SM, G, ENV, planNode);
        OclValue after = QInterpreter.evalExpr(SM, G, ENV, materialized);

        assertEquals(before, after);
        assertEquals(before.type(), after.type());
        assertEquals(bottom, after.isBottom());
        if (bottom) {
            assertNull(plan);
            return;
        }
        assertNotNull(plan);
        List<OclValue> items = after instanceof OclValue.SetValue set
                ? set.members() : ((OclValue.BagValue) after).occurrences();
        int[] actual = new int[3];
        for (OclValue item : items) {
            int index = item.isBottom() ? 0
                    : ((OclValue.IntegerValue) item).value().intValueExact() + 1;
            actual[index]++;
        }
        for (int index = 0; index < 3; index++) {
            int expected = expression.type.kind() == OclType.Kind.SET
                    ? Math.min(1, counts[index]) : counts[index];
            assertEquals(expected, actual[index]);
        }
        assertEquals(items.size(), plan.size());
    }
}
