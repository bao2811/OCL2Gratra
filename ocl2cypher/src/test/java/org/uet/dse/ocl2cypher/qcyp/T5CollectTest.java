package org.uet.dse.ocl2cypher.qcyp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigInteger;
import java.util.ArrayList;
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

class T5CollectTest {
    private static final SourceSpan S = SourceSpan.UNKNOWN;
    private static final SchemaModel SM = SchemaModel.builder("t5-collect").build();
    private static final GraphModel G = new GraphModel("t5-collect");
    private static final CoreInterpreter.Env ENV = new CoreInterpreter.Env();
    private static final CoreDeclaration X = new CoreDeclaration(
            1, "x", CoreDeclaration.Kind.ITERATOR, OclType.BOOLEAN);

    @Test
    void collectMatchesIndependentBagImageOracle() {
        int checked = 0;
        for (CoreExpr.CollectionKind kind : CoreExpr.CollectionKind.values()) {
            check(kind, List.of(), true);
            checked += 7;
            for (int length = 0; length <= 4; length++) {
                int limit = (int) Math.pow(3, length);
                for (int code = 0; code < limit; code++) {
                    int remaining = code;
                    List<Integer> input = new ArrayList<>();
                    for (int index = 0; index < length; index++) {
                        input.add(remaining % 3);
                        remaining /= 3;
                    }
                    check(kind, input, false);
                    checked += 7;
                }
            }
        }
        assertEquals(1708, checked);
    }

    private static void check(CoreExpr.CollectionKind kind, List<Integer> input, boolean wholeBottom) {
        OclType sourceType = kind == CoreExpr.CollectionKind.SET
                ? OclType.set(OclType.BOOLEAN) : OclType.bag(OclType.BOOLEAN);
        List<QNode.QExpr> elements = new ArrayList<>();
        for (int encoded : input) {
            elements.add(value(encoded));
        }
        QNode.QExpr source = wholeBottom
                ? new QNode.QExpr.Bottom(S, sourceType)
                : new QNode.QExpr.CollectionLiteral(S, kind, elements, sourceType);
        List<Integer> occurrences = kind == CoreExpr.CollectionKind.SET
                ? new ArrayList<>(new LinkedHashSet<>(input)) : input;
        QNode.QExpr variable = new QNode.QExpr.Variable(S, X);
        List<QNode.QExpr> bodies = List.of(
                value(0),
                value(1),
                value(2),
                variable,
                new QNode.QExpr.Unary(
                        S, CoreExpr.UnaryOp.BOOLEAN_NOT, variable, OclType.BOOLEAN),
                new QNode.QExpr.Constant(S, OclType.INTEGER, BigInteger.valueOf(7)),
                new QNode.QExpr.Bottom(S, OclType.INTEGER));

        for (int mode = 0; mode < bodies.size(); mode++) {
            QNode.QExpr body = bodies.get(mode);
            QNode.QPlan collect = new QNode.QPlan.Collect(
                    S, new QNode.QPlan.FromCollection(S, source), X, body);
            List<OclValue> plan = QInterpreter.evalPlan(SM, G, ENV, collect);
            OclValue result = QInterpreter.evalExpr(
                    SM, G, ENV, new QNode.QExpr.Materialize(S, collect));

            assertEquals(OclType.bag(body.type), result.type());
            assertEquals(wholeBottom, result.isBottom());
            if (wholeBottom) {
                assertNull(plan);
                continue;
            }

            assertNotNull(plan);
            List<Integer> expected = new ArrayList<>();
            for (int occurrence : occurrences) {
                expected.add(switch (mode) {
                    case 0, 1, 2 -> mode;
                    case 3 -> occurrence;
                    case 4 -> occurrence == 0 ? 0 : 3 - occurrence;
                    case 5 -> 3;
                    default -> 0;
                });
            }
            List<Integer> actual = new ArrayList<>();
            for (OclValue image : plan) {
                assertEquals(body.type, image.type());
                actual.add(decode(image));
            }
            assertEquals(expected, actual);

            List<OclValue> materialized = ((OclValue.BagValue) result).occurrences();
            assertEquals(occurrences.size(), materialized.size());
            int[] counts = new int[4];
            for (OclValue image : materialized) {
                counts[decode(image)]++;
            }
            for (int encoded = 0; encoded < counts.length; encoded++) {
                assertEquals(Collections.frequency(expected, encoded), counts[encoded]);
            }
        }
    }

    // Oracle encoding: 0 = typed bottom, 1 = false, 2 = true, 3 = Integer 7.
    private static QNode.QExpr value(int encoded) {
        return encoded == 0
                ? new QNode.QExpr.Bottom(S, OclType.BOOLEAN)
                : new QNode.QExpr.Constant(S, OclType.BOOLEAN, encoded == 2);
    }

    private static int decode(OclValue value) {
        if (value.isBottom()) {
            return 0;
        }
        if (value instanceof OclValue.IntegerValue integer) {
            assertEquals(BigInteger.valueOf(7), integer.value());
            return 3;
        }
        return ((OclValue.BooleanValue) value).bool() == OclValue.BooleanValue.Bool3.TRUE
                ? 2 : 1;
    }
}
