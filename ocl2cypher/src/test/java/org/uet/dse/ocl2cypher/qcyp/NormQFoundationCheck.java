package org.uet.dse.ocl2cypher.qcyp;

import java.lang.reflect.Field;
import java.math.BigInteger;
import java.util.List;
import java.util.Objects;
import org.uet.dse.ocl2cypher.core.CoreDeclaration;
import org.uet.dse.ocl2cypher.core.CoreExpr;
import org.uet.dse.ocl2cypher.core.CoreInterpreter;
import org.uet.dse.ocl2cypher.diagnostics.SourceSpan;
import org.uet.dse.ocl2cypher.graph.GraphModel;
import org.uet.dse.ocl2cypher.runtime.OclType;
import org.uet.dse.ocl2cypher.source.model.SchemaModel;
import static org.uet.dse.ocl2cypher.qcyp.QNode.*;

/** Finite F-7 witnesses against actual normalization and Q evaluation, not a proof. */
public final class NormQFoundationCheck {
    private static final SourceSpan S = SourceSpan.UNKNOWN;
    private static int checked;
    private static void require(boolean b) { if (!b) throw new AssertionError(); }
    private static QExpr integer(int n) {
        return new QExpr.Constant(S, OclType.INTEGER, BigInteger.valueOf(n));
    }

    // Compare actual fields, not structuralKey (which is not certified injective).
    private static boolean same(Object a, Object b) throws IllegalAccessException {
        if (a == b) return true;
        if (a == null || b == null || a.getClass() != b.getClass()) return false;
        if (a instanceof QExpr || a instanceof QPlan) {
            for (Field f : a.getClass().getFields()) if (!same(f.get(a), f.get(b))) return false;
            return true;
        }
        if (a instanceof List<?> x && b instanceof List<?> y) {
            if (x.size() != y.size()) return false;
            for (int i = 0; i < x.size(); i++) if (!same(x.get(i), y.get(i))) return false;
            return true;
        }
        return Objects.equals(a, b);
    }

    private static void canonical(Object x) throws IllegalAccessException {
        require(!(x instanceof QExpr.CountFamily || x instanceof QExpr.IncludesFamily
                || x instanceof QExpr.SetAlgebra));
        if (x instanceof QExpr || x instanceof QPlan) {
            for (Field f : x.getClass().getFields()) canonical(f.get(x));
        } else if (x instanceof List<?> xs) for (Object child : xs) canonical(child);
    }

    private static void check(QExpr e) throws IllegalAccessException {
        var n = NormQ.normalizeExpr(e);
        canonical(n);
        require(same(n, NormQ.normalizeExpr(n)));
        require(e.type.equals(n.type));
        var schema = SchemaModel.builder("norm").build();
        var graph = new GraphModel("norm");
        var env = new CoreInterpreter.Env();
        require(QInterpreter.evalExpr(schema, graph, env, e).equals(
                QInterpreter.evalExpr(schema, graph, env, n)));
        checked++;
    }

    public static void main(String[] args) throws Exception {
        for (var kind : CoreExpr.CollectionKind.values()) {
            var type = kind == CoreExpr.CollectionKind.SET
                    ? OclType.set(OclType.INTEGER) : OclType.bag(OclType.INTEGER);
            var bottom = new QExpr.Bottom(S, OclType.INTEGER);
            var empty = new QExpr.CollectionLiteral(S, kind, List.of(), type);
            var values = new QExpr.CollectionLiteral(S, kind,
                    List.of(integer(1), integer(1), bottom), type);
            for (QExpr source : List.of(empty, values, new QExpr.Bottom(S, type))) {
                for (var op : List.of(QKind.SIZE, QKind.IS_EMPTY, QKind.NOT_EMPTY, QKind.SUM, QKind.COUNT)) {
                    check(new QExpr.CountFamily(S, op, source, op == QKind.COUNT ? bottom : null,
                            op == QKind.IS_EMPTY || op == QKind.NOT_EMPTY ? OclType.BOOLEAN : OclType.INTEGER));
                }
                for (var op : List.of(QKind.INCLUDES, QKind.EXCLUDES, QKind.INCLUDES_ALL, QKind.EXCLUDES_ALL)) {
                    check(new QExpr.IncludesFamily(S, op, source,
                            op == QKind.INCLUDES || op == QKind.EXCLUDES ? bottom : values, OclType.BOOLEAN));
                }
                if (kind == CoreExpr.CollectionKind.SET) {
                    for (var op : List.of(CoreExpr.BinaryOp.SET_UNION, CoreExpr.BinaryOp.SET_INTERSECTION))
                        check(new QExpr.SetAlgebra(S, op, source, values, type));
                }
                var iterator = new CoreDeclaration(1, "x", CoreDeclaration.Kind.ITERATOR, OclType.INTEGER);
                var binder = new CoreDeclaration(2, "x", CoreDeclaration.Kind.LET, OclType.INTEGER);
                var size = new QExpr.CountFamily(S, QKind.SIZE, source, null, OclType.INTEGER);
                var predicate = new QExpr.IncludesFamily(S, QKind.INCLUDES, values,
                        new QExpr.Variable(S, iterator), OclType.BOOLEAN);
                var plan = new QPlan.FromCollection(S, source);
                check(new QExpr.Exists3(S, plan, iterator, predicate));
                check(new QExpr.ForAll3(S, plan, iterator, predicate));
                check(new QExpr.Materialize(S, new QPlan.Filter(S, plan, iterator, predicate, true)));
                check(new QExpr.Materialize(S, new QPlan.Filter(S, plan, iterator, predicate, false)));
                check(new QExpr.Materialize(S, new QPlan.Collect(S, plan, iterator, size)));
                check(new QExpr.Materialize(S, new QPlan.PlanLet(S, binder, size, plan)));
                if (kind == CoreExpr.CollectionKind.SET)
                    check(new QExpr.Materialize(S, new QPlan.Distinct(S, plan)));
                check(new QExpr.Let(S, binder, size, new QExpr.Variable(S, binder)));
                check(new QExpr.IfExpr(S, new QExpr.Bottom(S, OclType.BOOLEAN), size, integer(0), OclType.INTEGER));
            }
        }
        boolean rejected = false;
        try { NormQ.normalizeExpr(new QExpr.CountFamily(S, QKind.CONSTANT, integer(0), null, OclType.INTEGER)); }
        catch (IllegalArgumentException expected) { rejected = true; }
        require(rejected); // malformed Java AST, outside the WF-domain theorem
        System.out.println("PASS: " + checked + " normalization witnesses; 11 aliases, Set/Bag, bottom, nested plans, idempotence; invalid family rejected");
    }
}
