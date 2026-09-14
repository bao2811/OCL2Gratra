package org.uet.dse.ocl2cypher.qcyp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
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

class T6FoldTest {
    private static final SourceSpan S = SourceSpan.UNKNOWN;
    private static final SchemaModel SM = SchemaModel.builder("t6-fold").build();
    private static final GraphModel G = new GraphModel("t6-fold");
    private static final CoreInterpreter.Env ENV = new CoreInterpreter.Env();
    private static final CoreDeclaration X = new CoreDeclaration(
            1, "x", CoreDeclaration.Kind.ITERATOR, OclType.BOOLEAN);

    @Test
    void existsAndForAllMatchIndependentPresenceOracle() {
        int checked = 0;
        for (CoreExpr.CollectionKind kind : CoreExpr.CollectionKind.values()) {
            check(kind, List.of(), true);
            checked += 10;
            for (int length = 0; length <= 5; length++) {
                int limit = (int) Math.pow(3, length);
                for (int code = 0; code < limit; code++) {
                    int remaining = code;
                    List<Integer> input = new ArrayList<>();
                    for (int index = 0; index < length; index++) {
                        input.add(remaining % 3);
                        remaining /= 3;
                    }
                    check(kind, input, false);
                    checked += 10;
                }
            }
        }
        assertEquals(7300, checked);
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
        QNode.QPlan plan = new QNode.QPlan.FromCollection(S, source);
        QNode.QExpr variable = new QNode.QExpr.Variable(S, X);
        List<QNode.QExpr> predicates = List.of(
                value(0),
                value(1),
                value(2),
                variable,
                new QNode.QExpr.Unary(
                        S, CoreExpr.UnaryOp.BOOLEAN_NOT, variable, OclType.BOOLEAN));

        for (int mode = 0; mode < predicates.size(); mode++) {
            for (boolean exists : new boolean[] {true, false}) {
                boolean hasBottom = false;
                boolean hasTrue = false;
                boolean hasFalse = false;
                for (int occurrence : input) {
                    int predicate = mode < 3
                            ? mode
                            : mode == 3 ? occurrence : occurrence == 0 ? 0 : 3 - occurrence;
                    hasBottom |= predicate == 0;
                    hasFalse |= predicate == 1;
                    hasTrue |= predicate == 2;
                }
                int expected = wholeBottom
                        ? 0
                        : exists
                                ? hasTrue ? 2 : hasBottom ? 0 : 1
                                : hasFalse ? 1 : hasBottom ? 0 : 2;
                QNode.QExpr fold = exists
                        ? new QNode.QExpr.Exists3(S, plan, X, predicates.get(mode))
                        : new QNode.QExpr.ForAll3(S, plan, X, predicates.get(mode));
                OclValue result = QInterpreter.evalExpr(SM, G, ENV, fold);
                assertEquals(OclType.BOOLEAN, result.type());
                assertEquals(expected, decode(result));
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
