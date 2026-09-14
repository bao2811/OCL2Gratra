package org.uet.dse.ocl2cypher.qcyp;

import java.util.ArrayList;
import java.util.List;
import org.uet.dse.ocl2cypher.core.CoreDeclaration;
import org.uet.dse.ocl2cypher.core.CoreExpr;
import org.uet.dse.ocl2cypher.core.CoreInvariant;
import org.uet.dse.ocl2cypher.core.CoreQuery;
import org.uet.dse.ocl2cypher.core.CoreUnit;
import org.uet.dse.ocl2cypher.diagnostics.Result;
import org.uet.dse.ocl2cypher.diagnostics.RuleId;
import org.uet.dse.ocl2cypher.diagnostics.Stage;
import org.uet.dse.ocl2cypher.runtime.OclType;
import org.uet.dse.ocl2cypher.trace.Trace;
import org.uet.dse.ocl2cypher.trace.TraceCollector;

/**
 * {@code T_G}: typed Core OCL_IR to Q_CYP.
 *
 * <p>The public translation is {@code T_I × G × QueryMode → Q_CYP}. A
 * well-formed source body never depends on the concrete snapshot contents to
 * type-check; a fresh auxiliary binder is allocated for scans.
 */
public final class QCypTranslator {

    private QCypTranslator() {
    }

    /** Translate an invariant only to its violation-ID query. */
    public static Result<QQuery> translate(CoreInvariant invariant) {
        return translate(invariant, QQuery.QueryMode.VIOLATIONS);
    }

    public static Result<QQuery> translate(CoreInvariant invariant, TraceCollector traces) {
        Result<QQuery> result = translate(invariant);
        if (result.isSuccess()) {
            String name = invariant.invariantName() == null
                    ? "<anonymous>" : invariant.invariantName();
            traces.record(Trace.Stage.T_G,
                    "core-invariant:" + invariant.contextClassKey() + "::" + name,
                    "q-violations:" + invariant.contextClassKey() + "::" + name,
                    RuleId.T_Q_VIOLATIONS);
        }
        return result;
    }

    /** Translate an independent query only to its tagged VALUE query. */
    public static Result<QQuery> translate(CoreQuery query) {
        return translate(query, QQuery.QueryMode.VALUE);
    }

    public static Result<QQuery> translate(CoreQuery query, TraceCollector traces) {
        Result<QQuery> result = translate(query);
        if (result.isSuccess()) {
            String rule = result.value().planBody() == null
                    ? RuleId.T_Q_VALUE_EXPR : RuleId.T_Q_VALUE_PLAN;
            traces.record(Trace.Stage.T_G, "core-value-query", "q-value-query", rule);
        }
        return result;
    }

    private static Result<QQuery> translate(CoreUnit unit, QQuery.QueryMode mode) {
        try {
            QQuery q = switch (mode) {
                case VIOLATIONS -> {
                    if (!unit.isInvariant()) {
                        yield failure("T_Q_VIOLATIONS_ONLY_FOR_INVARIANT",
                                "VIOLATIONS requires an invariant unit");
                    }
                    QNode.QExpr body = NormQ.normalizeExpr(transE(unit.body()));
                    if (!body.type.equals(OclType.BOOLEAN)) {
                        throw new IllegalStateException("invariant body must be Boolean3");
                    }
                    yield new QQuery(body, null, QQuery.QResultShape.IDS,
                            QQuery.QueryMode.VIOLATIONS, null,
                            unit.contextClassKey(), unit.selfVariable(), true);
                }
                case VALUE -> {
                    if (unit.mode() != CoreUnit.Mode.QUERY_VALUE) {
                        yield failure("T_Q_VALUE_REQUIRES_QUERY",
                                "VALUE requires an explicit QUERY_VALUE unit");
                    }
                    if (needsPlan(unit.body())) {
                        QNode.QPlan plan = NormQ.normalizePlan(transP(unit.body()));
                        OclType rt = plan.type;
                        QQuery.QResultShape shape = rt.kind() == OclType.Kind.SET
                                ? QQuery.QResultShape.SET
                                : (rt.kind() == OclType.Kind.BAG ? QQuery.QResultShape.BAG
                                        : QQuery.QResultShape.SCALAR);
                        yield new QQuery(null, plan, shape, QQuery.QueryMode.VALUE, rt,
                                unit.contextClassKey(), unit.selfVariable(), false);
                    }
                    QNode.QExpr expr = NormQ.normalizeExpr(transE(unit.body()));
                    OclType rt = expr.type;
                    QQuery.QResultShape shape;
                    if (rt.isCollection()) {
                        shape = rt.kind() == OclType.Kind.SET ? QQuery.QResultShape.SET
                                : QQuery.QResultShape.BAG;
                    } else {
                        shape = QQuery.QResultShape.SCALAR;
                    }
                    yield new QQuery(expr, null, shape, QQuery.QueryMode.VALUE, rt,
                            unit.contextClassKey(), unit.selfVariable(), false);
                }
            };
            List<QValidator.Error> wellFormedness = QValidator.validate(q);
            if (!wellFormedness.isEmpty()) {
                throw new IllegalStateException("T_QCYP_WELL_FORMEDNESS: " + wellFormedness);
            }
            return Result.success(q);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Result.failure(Stage.T_G, "T_G_SYNTAX", e.getMessage());
        }
    }

    private static boolean needsPlan(CoreExpr e) {
        return e instanceof CoreExpr.Navigation n && n.kind == CoreExpr.NavKind.TO_MANY
                || e instanceof CoreExpr.AllInstances
                || (e instanceof CoreExpr.Iterator
                        && (e instanceof CoreExpr.Iterator it
                                && (it.iteratorKind == CoreExpr.IteratorKind.SELECT
                                        || it.iteratorKind == CoreExpr.IteratorKind.REJECT
                                        || it.iteratorKind == CoreExpr.IteratorKind.COLLECT)));
    }

    static QNode.QExpr transE(CoreExpr e) {
        if (e instanceof CoreExpr.LiteralBoolean lb) {
            return new QNode.QExpr.Constant(lb.span, OclType.BOOLEAN, lb.literal);
        }
        if (e instanceof CoreExpr.LiteralInteger li) {
            return new QNode.QExpr.Constant(li.span, OclType.INTEGER, li.literal);
        }
        if (e instanceof CoreExpr.LiteralReal lr) {
            return new QNode.QExpr.Constant(lr.span, OclType.REAL, lr.literal);
        }
        if (e instanceof CoreExpr.LiteralString ls) {
            return new QNode.QExpr.Constant(ls.span, OclType.STRING, ls.literal);
        }
        if (e instanceof CoreExpr.Bottom b) {
            return new QNode.QExpr.Bottom(b.span, b.type());
        }
        if (e instanceof CoreExpr.Variable v) {
            return new QNode.QExpr.Variable(v.span, v.declaration);
        }
        if (e instanceof CoreExpr.AttributeRead ar) {
            QNode.QExpr src = transE(ar.source);
            return new QNode.QExpr.ReadAttribute(ar.span, src, ar.ownerClassKey,
                    ar.attributeName, ar.type());
        }
        if (e instanceof CoreExpr.Navigation nav) {
            if (nav.kind == CoreExpr.NavKind.TO_ONE) {
                QNode.QExpr src = transE(nav.source);
                List<QNode.QExpr> quals = quals(nav.qualifiers);
                return new QNode.QExpr.NavigateOne(nav.span, src, nav.associationName,
                        nav.roleName, quals, nav.type(), nav.reverse, false,
                        nav.viaAssociationClass);
            }
            return new QNode.QExpr.Materialize(nav.span, planForNavigation(nav));
        }
        if (e instanceof CoreExpr.AssociationClassNavigation acn) {
            if (acn.kind == CoreExpr.NavKind.TO_ONE) {
                QNode.QExpr src = transE(acn.source);
                return new QNode.QExpr.NavigateOne(acn.span, src, acn.associationClassKey,
                        acn.navigationSource, quals2(acn.qualifiers), acn.type(),
                        acn.receiverIsTarget, true);
            }
            return new QNode.QExpr.Materialize(acn.span, planForAcNavigation(acn));
        }
        if (e instanceof CoreExpr.Let let) {
            QNode.QExpr v = transE(let.value);
            QNode.QExpr body = transE(let.inExpr);
            return new QNode.QExpr.Let(let.span, let.binder, v, body);
        }
        if (e instanceof CoreExpr.IfExpr iff) {
            QNode.QExpr c = transE(iff.condition);
            QNode.QExpr t = transE(iff.thenExpr);
            QNode.QExpr el = transE(iff.elseExpr);
            return new QNode.QExpr.IfExpr(iff.span, c, t, el, iff.type());
        }
        if (e instanceof CoreExpr.Coerce cc) {
            QNode.QExpr src = transE(cc.source);
            return new QNode.QExpr.Coerce(cc.span, cc.kind, cc.sourceType, src, cc.type());
        }
        if (e instanceof CoreExpr.AllInstances ai) {
            QNode.QPlan scan = new QNode.QPlan.ScanClass(ai.span, ai.classKey,
                    new CoreDeclaration(0, ai.classKey, CoreDeclaration.Kind.ITERATOR,
                            OclType.clazz(ai.classKey)));
            return new QNode.QExpr.Materialize(ai.span, scan);
        }
        if (e instanceof CoreExpr.TypeTest tt) {
            QNode.QExpr src = transE(tt.source);
            return new QNode.QExpr.TypeTest(tt.span, tt.kind, src, tt.targetClassKey);
        }
        if (e instanceof CoreExpr.TypeCast tc) {
            QNode.QExpr src = transE(tc.source);
            return new QNode.QExpr.TypeCast(tc.span, src, tc.targetClassKey);
        }
        if (e instanceof CoreExpr.Unary u) {
            return new QNode.QExpr.Unary(u.span, u.operator, transE(u.operand), u.type());
        }
        if (e instanceof CoreExpr.Binary b) {
            return new QNode.QExpr.Binary(b.span, b.operator, transE(b.left), transE(b.right),
                    b.type());
        }
        if (e instanceof CoreExpr.CollectionLiteral cl) {
            List<QNode.QExpr> els = new ArrayList<>();
            for (CoreExpr el : cl.elements) {
                els.add(transE(el));
            }
            return new QNode.QExpr.CollectionLiteral(cl.span, cl.kind, els, cl.type());
        }
        if (e instanceof CoreExpr.Iterator it) {
            return transIteratorAsExpr(it);
        }
        throw new IllegalStateException("no T_E case for " + e.getClass().getSimpleName());
    }

    private static QNode.QExpr transIteratorAsExpr(CoreExpr.Iterator it) {
        return switch (it.iteratorKind) {
            case EXISTS -> {
                QNode.QPlan src = asPlanTranslate(it.source);
                QNode.QExpr pred = transE(it.body);
                yield new QNode.QExpr.Exists3(it.span, src, it.iterator, pred);
            }
            case FORALL -> {
                QNode.QPlan src = asPlanTranslate(it.source);
                QNode.QExpr pred = transE(it.body);
                yield new QNode.QExpr.ForAll3(it.span, src, it.iterator, pred);
            }
            case SELECT, REJECT, COLLECT -> new QNode.QExpr.Materialize(it.span,
                    translateIteratorAsPlan(it));
        };
    }

    static QNode.QPlan transP(CoreExpr e) {
        if (e instanceof CoreExpr.Navigation nav) {
            return planForNavigation(nav);
        }
        if (e instanceof CoreExpr.AssociationClassNavigation acn) {
            return planForAcNavigation(acn);
        }
        if (e instanceof CoreExpr.AllInstances ai) {
            return new QNode.QPlan.ScanClass(ai.span, ai.classKey,
                    new CoreDeclaration(0, ai.classKey, CoreDeclaration.Kind.ITERATOR,
                            OclType.clazz(ai.classKey)));
        }
        if (e instanceof CoreExpr.Iterator it) {
            return translateIteratorAsPlan(it);
        }
        return new QNode.QPlan.FromCollection(e.span, transE(e));
    }

    static QNode.QPlan translateIteratorAsPlan(CoreExpr.Iterator it) {
        QNode.QPlan src = asPlanTranslate(it.source);
        return switch (it.iteratorKind) {
            case SELECT -> new QNode.QPlan.Filter(it.span, src, it.iterator, transE(it.body), true);
            case REJECT -> new QNode.QPlan.Filter(it.span, src, it.iterator, transE(it.body), false);
            case COLLECT -> new QNode.QPlan.Collect(it.span, src, it.iterator, transE(it.body));
            case EXISTS, FORALL -> throw new IllegalStateException(
                    "exists/forAll translate as QExpr (Exists3/ForAll3), not as a plan");
        };
    }

    private static QNode.QPlan asPlanTranslate(CoreExpr source) {
        if (source instanceof CoreExpr.Navigation
                || source instanceof CoreExpr.AssociationClassNavigation
                || source instanceof CoreExpr.AllInstances
                || source instanceof CoreExpr.Iterator) {
            return transP(source);
        }
        return new QNode.QPlan.FromCollection(source.span, transE(source));
    }

    private static QNode.QPlan planForNavigation(CoreExpr.Navigation nav) {
        QNode.QExpr src = transE(nav.source);
        List<QNode.QExpr> quals = quals(nav.qualifiers);
        return new QNode.QPlan.NavigateMany(nav.span, src, nav.associationName,
                nav.roleName, quals, nav.type(), nav.reverse, false,
                nav.viaAssociationClass);
    }

    private static QNode.QPlan planForAcNavigation(CoreExpr.AssociationClassNavigation acn) {
        QNode.QExpr src = transE(acn.source);
        List<QNode.QExpr> quals = quals2(acn.qualifiers);
        return new QNode.QPlan.NavigateMany(acn.span, src, acn.associationClassKey,
                acn.navigationSource, quals, acn.type(), acn.receiverIsTarget, true);
    }

    private static List<QNode.QExpr> quals(List<CoreExpr> xs) {
        List<QNode.QExpr> r = new ArrayList<>();
        for (CoreExpr x : xs) {
            r.add(transE(x));
        }
        return List.copyOf(r);
    }

    private static List<QNode.QExpr> quals2(List<CoreExpr> xs) {
        return quals(xs);
    }

    private static QQuery failure(String code, String msg) {
        throw new IllegalArgumentException(code + ": " + msg);
    }
}
