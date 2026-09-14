package org.uet.dse.ocl2cypher.qcyp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
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

class T4FilterTest {
    private static final SourceSpan S = SourceSpan.UNKNOWN;
    private static final SchemaModel SM = SchemaModel.builder("t4-filter").build();
    private static final GraphModel G = new GraphModel("t4-filter");
    private static final CoreInterpreter.Env ENV = new CoreInterpreter.Env();
    private static final CoreDeclaration X = new CoreDeclaration(
            1, "x", CoreDeclaration.Kind.ITERATOR, OclType.BOOLEAN);

    @Test
    void filterMatchesIndependentThreeValuedOccurrenceOracle() {
        int checked = 0;
        for (CoreExpr.CollectionKind kind : CoreExpr.CollectionKind.values()) {
            check(kind, List.of(), true);
            checked += 10;
            for (int length = 0; length <= 4; length++) {
                int limit = (int) Math.pow(3, length);
                for (int code = 0; code < limit; code++) {
                    int remaining = code;
                    List<Integer> items = new ArrayList<>();
                    for (int index = 0; index < length; index++) {
                        items.add(remaining % 3);
                        remaining /= 3;
                    }
                    check(kind, items, false);
                    checked += 10;
                }
            }
        }
        assertEquals(2440, checked);
    }

    private static void check(CoreExpr.CollectionKind kind, List<Integer> input, boolean wholeBottom) {
        OclType type = kind == CoreExpr.CollectionKind.SET
                ? OclType.set(OclType.BOOLEAN) : OclType.bag(OclType.BOOLEAN);
        List<QNode.QExpr> expressions = new ArrayList<>();
        for (int value : input) {
            expressions.add(value(value));
        }
        QNode.QExpr source = wholeBottom
                ? new QNode.QExpr.Bottom(S, type)
                : new QNode.QExpr.CollectionLiteral(S, kind, expressions, type);
        List<Integer> occurrences = kind == CoreExpr.CollectionKind.SET
                ? new ArrayList<>(new LinkedHashSet<>(input)) : input;
        QNode.QExpr variable = new QNode.QExpr.Variable(S, X);
        List<QNode.QExpr> predicates = List.of(
                value(0),
                value(1),
                value(2),
                variable,
                new QNode.QExpr.Unary(
                        S, CoreExpr.UnaryOp.BOOLEAN_NOT, variable, OclType.BOOLEAN));

        for (int mode = 0; mode < predicates.size(); mode++) {
            for (boolean select : new boolean[] {true, false}) {
                boolean expectedBottom = wholeBottom;
                List<Integer> expected = new ArrayList<>();
                for (int occurrence : occurrences) {
                    int predicateValue = mode < 3
                            ? mode
                            : mode == 3 ? occurrence : occurrence == 0 ? 0 : 3 - occurrence;
                    expectedBottom |= predicateValue == 0;
                    if (predicateValue == (select ? 2 : 1)) {
                        expected.add(occurrence);
                    }
                }

                QNode.QPlan filter = new QNode.QPlan.Filter(
                        S,
                        new QNode.QPlan.FromCollection(S, source),
                        X,
                        predicates.get(mode),
                        select);
                List<OclValue> plan = QInterpreter.evalPlan(SM, G, ENV, filter);
                OclValue result = QInterpreter.evalExpr(
                        SM, G, ENV, new QNode.QExpr.Materialize(S, filter));

                assertEquals(type, result.type());
                assertEquals(expectedBottom, result.isBottom());
                if (expectedBottom) {
                    assertNull(plan);
                    continue;
                }

                assertNotNull(plan);
                List<Integer> actual = new ArrayList<>();
                for (OclValue item : plan) {
                    actual.add(decode(item));
                }
                assertEquals(expected, actual);

                Collection<OclValue> materialized = result instanceof OclValue.SetValue set
                        ? set.members() : ((OclValue.BagValue) result).occurrences();
                int[] counts = new int[3];
                for (OclValue item : materialized) {
                    counts[decode(item)]++;
                }
                for (int value = 0; value < counts.length; value++) {
                    assertEquals(Collections.frequency(expected, value), counts[value]);
                }
            }
        }
    }

    // Oracle encoding: 0 = bottom, 1 = false, 2 = true.
    private static QNode.QExpr value(int encoded) {
        return encoded == 0
                ? new QNode.QExpr.Bottom(S, OclType.BOOLEAN)
                : new QNode.QExpr.Constant(S, OclType.BOOLEAN, encoded == 2);
    }

    private static int decode(OclValue value) {
        if (value.isBottom()) {
            return 0;
        }
        return ((OclValue.BooleanValue) value).bool() == OclValue.BooleanValue.Bool3.TRUE
                ? 2 : 1;
    }
}
