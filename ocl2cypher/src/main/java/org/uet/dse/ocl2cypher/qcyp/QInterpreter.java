package org.uet.dse.ocl2cypher.qcyp;

import java.util.*;
import org.uet.dse.ocl2cypher.core.CoreDeclaration;
import org.uet.dse.ocl2cypher.core.CoreInterpreter;
import org.uet.dse.ocl2cypher.core.CoreInvariant;
import org.uet.dse.ocl2cypher.core.CoreQuery;
import org.uet.dse.ocl2cypher.graph.GraphModel;
import org.uet.dse.ocl2cypher.graph.GraphObservation;
import org.uet.dse.ocl2cypher.runtime.Boolean3;
import org.uet.dse.ocl2cypher.runtime.OclOps;
import org.uet.dse.ocl2cypher.runtime.OclType;
import org.uet.dse.ocl2cypher.runtime.OclValue;
import org.uet.dse.ocl2cypher.source.model.SchemaModel;

/**
 * Q_CYP oracle evaluating over the concrete {@link GraphModel G} — strictly
 * through the observer interface ({@link GraphObservation}), never against the
 * source snapshot directly. This is what makes the differential
 * {@code evalCore(i) ~ evalQ(T(i),G)} a genuine check of the translation,
 * {@code F_G} and the observer contract at once: if the graph builder loses a
 * link, mislabels a role, or decodes a bottom wrongly, this oracle diverges
 * from the Core oracle and the test fails.
 */
public final class QInterpreter {

    private QInterpreter() {
    }

    /** {@code [[q]]_{Q_CYP,G,eta}} — scalar/collection-valued expression. */
    public static OclValue evalExpr(SchemaModel sm, GraphModel g, CoreInterpreter.Env env,
                                    QNode.QExpr e) {
        if (e instanceof QNode.QExpr.Variable v) {
            return env.lookup(v.declaration);
        }
        if (e instanceof QNode.QExpr.Parameter p) {
            OclValue value = env.lookupParameter(p.parameter.name());
            if (!value.type().equals(p.type)) {
                throw new IllegalStateException("public parameter type mismatch for "
                        + p.parameter.name());
            }
            return value;
        }
        if (e instanceof QNode.QExpr.Bottom b) {
            return new OclValue.BottomValue(b.type);
        }
        if (e instanceof QNode.QExpr.Constant c) {
            if (c.type.equals(OclType.BOOLEAN)) {
                return Boolean.TRUE.equals(c.literalValue) ? Boolean3.TRUE : Boolean3.FALSE;
            }
            if (c.type.equals(OclType.INTEGER)) {
                return new OclValue.IntegerValue((java.math.BigInteger) c.literalValue);
            }
            if (c.type.equals(OclType.REAL)) {
                return new OclValue.RealValue((java.math.BigDecimal) c.literalValue);
            }
            return new OclValue.StringValue((String) c.literalValue);
        }
        if (e instanceof QNode.QExpr.Coerce cc) {
            OclValue src = evalExpr(sm, g, env, cc.source);
            return coerceValue(src, cc.kind, cc.type);
        }
        if (e instanceof QNode.QExpr.Let let) {
            OclValue v = evalExpr(sm, g, env, let.value);
            var child = env.child();
            child.bind(let.binder, v);
            return evalExpr(sm, g, child, let.body);
        }
        if (e instanceof QNode.QExpr.IfExpr iff) {
            OclValue c = evalExpr(sm, g, env, iff.condition);
            if (c instanceof OclValue.BottomValue) {
                return new OclValue.BottomValue(iff.type);
            }
            boolean cond = ((OclValue.BooleanValue) c).bool() == OclValue.BooleanValue.Bool3.TRUE;
            return cond ? evalExpr(sm, g, env, iff.thenExpr) : evalExpr(sm, g, env, iff.elseExpr);
        }
        if (e instanceof QNode.QExpr.ReadAttribute ar) {
            OclValue recv = evalExpr(sm, g, env, ar.source);
            if (recv instanceof OclValue.BottomValue) {
                return new OclValue.BottomValue(ar.type);
            }
            return GraphObservation.attribute(g, sm,
                    ((OclValue.ObjectValue) recv).stableId(), ar.ownerClassKey,
                    ar.attributeName);
        }
        if (e instanceof QNode.QExpr.NavigateOne nav) {
            OclValue recv = evalExpr(sm, g, env, nav.source);
            if (recv instanceof OclValue.BottomValue) {
                return new OclValue.BottomValue(nav.type);
            }
            List<OclValue> qualifierValues = new ArrayList<>();
            for (QNode.QExpr q : nav.qualifiers) {
                OclValue qv = evalExpr(sm, g, env, q);
                if (qv instanceof OclValue.BottomValue) {
                    return new OclValue.BottomValue(nav.type);
                }
                qualifierValues.add(qv);
            }
            List<String> targets = nav.associationClass
                    ? GraphObservation.associationClassObjects(g, sm,
                            ((OclValue.ObjectValue) recv).stableId(), nav.associationName,
                            qualifierValues)
                    : GraphObservation.linkTargets(g, sm,
                            ((OclValue.ObjectValue) recv).stableId(), nav.roleName,
                            nav.reverse, qualifierValues);
            if (targets.size() != 1) {
                return new OclValue.BottomValue(nav.type); // zero or many matches
            }
            return new OclValue.ObjectValue(nav.type, targets.get(0));
        }
        if (e instanceof QNode.QExpr.TypeTest tt) {
            OclValue src = evalExpr(sm, g, env, tt.source);
            if (src instanceof OclValue.BottomValue) {
                String bottomType = src.type().className();
                boolean match = tt.testKind == org.uet.dse.ocl2cypher.core.CoreExpr.TypeTestKind.EXACT_TYPE
                        ? bottomType.equals(tt.targetClassKey)
                        : sm.conforms(bottomType, tt.targetClassKey);
                return match ? Boolean3.TRUE : Boolean3.FALSE;
            }
            String oid = ((OclValue.ObjectValue) src).stableId();
            return tt.testKind == org.uet.dse.ocl2cypher.core.CoreExpr.TypeTestKind.EXACT_TYPE
                    ? (GraphObservation.directType(g, oid).equals(tt.targetClassKey)
                            ? Boolean3.TRUE : Boolean3.FALSE)
                    : (GraphObservation.conformsTo(g, sm, oid, tt.targetClassKey)
                            ? Boolean3.TRUE : Boolean3.FALSE);
        }
        if (e instanceof QNode.QExpr.TypeCast tc) {
            OclValue src = evalExpr(sm, g, env, tc.source);
            if (src instanceof OclValue.BottomValue) {
                return new OclValue.BottomValue(tc.type);
            }
            String oid = ((OclValue.ObjectValue) src).stableId();
            if (!GraphObservation.conformsTo(g, sm, oid, tc.targetClassKey)) {
                return new OclValue.BottomValue(tc.type);
            }
            return new OclValue.ObjectValue(tc.type, oid);
        }
        if (e instanceof QNode.QExpr.Unary u) {
            OclValue op = evalExpr(sm, g, env, u.operand);
            return evalUnary(op, u.operator, u.type);
        }
        if (e instanceof QNode.QExpr.Binary b) {
            OclValue l = evalExpr(sm, g, env, b.left);
            OclValue r = evalExpr(sm, g, env, b.right);
            return evalBinary(l, r, b.operator, b.type);
        }
        if (e instanceof QNode.QExpr.Exists3 ex) {
            List<OclValue> occ = evalPlan(sm, g, env, ex.source);
            if (occ == null) {
                return new OclValue.BottomValue(OclType.BOOLEAN);
            }
            return OclOps.foldExists(foldPredicates(sm, g, env, occ, ex.iterator, ex.predicate));
        }
        if (e instanceof QNode.QExpr.ForAll3 fa) {
            List<OclValue> occ = evalPlan(sm, g, env, fa.source);
            if (occ == null) {
                return new OclValue.BottomValue(OclType.BOOLEAN);
            }
            return OclOps.foldForAll(foldPredicates(sm, g, env, occ, fa.iterator, fa.predicate));
        }
        if (e instanceof QNode.QExpr.CollectionLiteral cl) {
            List<OclValue> els = new ArrayList<>();
            for (QNode.QExpr el : cl.elements) {
                els.add(evalExpr(sm, g, env, el));
            }
            return cl.collectionKind == org.uet.dse.ocl2cypher.core.CoreExpr.CollectionKind.SET
                    ? new OclValue.SetValue(cl.type, els)
                    : new OclValue.BagValue(cl.type, els);
        }
        if (e instanceof QNode.QExpr.IncludesFamily inc) {
            OclValue s = evalExpr(sm, g, env, inc.source);
            OclValue el = evalExpr(sm, g, env, inc.element);
            return switch (inc.includesKind) {
                case INCLUDES -> OclOps.includes(s, el);
                case EXCLUDES -> OclOps.excludes(s, el);
                case INCLUDES_ALL -> OclOps.includesAll(s, el);
                case EXCLUDES_ALL -> OclOps.excludesAll(s, el);
                default -> throw new IllegalStateException("includes family " + inc.includesKind);
            };
        }
        if (e instanceof QNode.QExpr.CountFamily cf) {
            OclValue s = evalExpr(sm, g, env, cf.source);
            return switch (cf.countKind) {
                case SIZE -> OclOps.size(s, cf.type);
                case IS_EMPTY -> OclOps.isEmpty(s);
                case NOT_EMPTY -> OclOps.notEmpty(s);
                case SUM -> OclOps.sum(s, cf.type);
                case COUNT -> OclOps.count(s, evalExpr(sm, g, env, cf.element), cf.type);
                default -> throw new IllegalStateException("count family " + cf.countKind);
            };
        }
        if (e instanceof QNode.QExpr.SetAlgebra sa) {
            OclValue l = evalExpr(sm, g, env, sa.left);
            OclValue r = evalExpr(sm, g, env, sa.right);
            return sa.operator == org.uet.dse.ocl2cypher.core.CoreExpr.BinaryOp.SET_UNION
                    ? OclOps.setUnion(l, r, sa.type)
                    : OclOps.setIntersection(l, r, sa.type);
        }
        if (e instanceof QNode.QExpr.Materialize mat) {
            List<OclValue> occ = evalPlan(sm, g, env, mat.plan);
            if (occ == null) {
                return new OclValue.BottomValue(mat.type);
            }
            return mat.type.kind() == OclType.Kind.SET
                    ? new OclValue.SetValue(mat.type, occ)
                    : new OclValue.BagValue(mat.type, occ);
        }
        throw new IllegalStateException("no Q eval case for " + e.getClass().getSimpleName());
    }

    /** {@code [[p]]_{Q_CYP,G,eta}} as an occurrence list; null denotes whole-collection bottom. */
    public static List<OclValue> evalPlan(SchemaModel sm, GraphModel g, CoreInterpreter.Env env,
                                          QNode.QPlan p) {
        if (p instanceof QNode.QPlan.ScanClass scan) {
            List<OclValue> r = new ArrayList<>();
            for (String oid : GraphObservation.objectsOfClass(g, sm, scan.classKey)) {
                r.add(new OclValue.ObjectValue(OclType.clazz(scan.classKey), oid));
            }
            return r;
        }
        if (p instanceof QNode.QPlan.NavigateMany nav) {
            OclValue src = evalExpr(sm, g, env, nav.source);
            if (src instanceof OclValue.BottomValue) {
                return null;
            }
            List<OclValue> qualifierValues = new ArrayList<>();
            for (QNode.QExpr q : nav.qualifiers) {
                OclValue qv = evalExpr(sm, g, env, q);
                if (qv instanceof OclValue.BottomValue) {
                    return null;
                }
                qualifierValues.add(qv);
            }
            List<OclValue> r = new ArrayList<>();
            List<String> targets = nav.associationClass
                    ? GraphObservation.associationClassObjects(g, sm,
                            ((OclValue.ObjectValue) src).stableId(), nav.associationName,
                            qualifierValues)
                    : GraphObservation.linkTargets(g, sm,
                            ((OclValue.ObjectValue) src).stableId(), nav.roleName,
                            nav.reverse, qualifierValues);
            for (String tid : targets) {
                r.add(new OclValue.ObjectValue(nav.elementType, tid));
            }
            return r;
        }
        if (p instanceof QNode.QPlan.FromCollection fc) {
            OclValue coll = evalExpr(sm, g, env, fc.collection);
            if (coll instanceof OclValue.BottomValue) {
                return null;
            }
            if (coll instanceof OclValue.SetValue sv) {
                return new ArrayList<>(sv.members());
            }
            return new ArrayList<>(((OclValue.BagValue) coll).occurrences());
        }
        if (p instanceof QNode.QPlan.Filter flt) {
            List<OclValue> occ = evalPlan(sm, g, env, flt.source);
            if (occ == null) {
                return null;
            }
            List<OclValue> preds = foldPredicates(sm, g, env, occ, flt.iterator, flt.predicate);
            for (OclValue pv : preds) {
                if (pv instanceof OclValue.BottomValue) {
                    return null; // predicate-bottom -> whole-collection bottom
                }
            }
            List<OclValue> kept = new ArrayList<>();
            for (int i = 0; i < occ.size(); i++) {
                boolean isTrue = ((OclValue.BooleanValue) preds.get(i)).bool()
                        == OclValue.BooleanValue.Bool3.TRUE;
                if (flt.isSelect == isTrue) {
                    kept.add(occ.get(i));
                }
            }
            return kept;
        }
        if (p instanceof QNode.QPlan.Collect col) {
            List<OclValue> occ = evalPlan(sm, g, env, col.source);
            if (occ == null) {
                return null;
            }
            List<OclValue> images = new ArrayList<>();
            for (OclValue o : occ) {
                var child = env.child();
                child.bind(col.iterator, o);
                images.add(evalExpr(sm, g, child, col.body));
            }
            return images;
        }
        if (p instanceof QNode.QPlan.Distinct d) {
            List<OclValue> occ = evalPlan(sm, g, env, d.source);
            if (occ == null) {
                return null;
            }
            List<OclValue> distinct = new ArrayList<>();
            outer:
            for (OclValue incoming : occ) {
                for (OclValue ex : distinct) {
                    if (org.uet.dse.ocl2cypher.runtime.OclEquality.equal(incoming, ex)
                            == org.uet.dse.ocl2cypher.runtime.OclEquality.BoolKind.TRUE) {
                        continue outer;
                    }
                }
                distinct.add(incoming);
            }
            return distinct;
        }
        if (p instanceof QNode.QPlan.PlanLet let) {
            OclValue v = evalExpr(sm, g, env, let.value);
            var child = env.child();
            child.bind(let.binder, v);
            return evalPlan(sm, g, child, let.body);
        }
        throw new IllegalStateException("no plan eval case for " + p.getClass().getSimpleName());
    }

    /**
     * Evaluate a complete VALUE query, including the plan-to-collection boundary.
     * A {@code null} occurrence stream is the semantic whole-collection bottom;
     * it is never converted to an empty Set/Bag.
     */
    public static OclValue value(SchemaModel sm, GraphModel g, CoreQuery unit,
                                 QQuery query, CoreInterpreter.Env env) {
        if (query.mode() != QQuery.QueryMode.VALUE) {
            throw new IllegalArgumentException("VALUE evaluation requires Core QUERY_VALUE and Q VALUE");
        }
        if (query.expressionBody() != null) {
            return evalExpr(sm, g, env, query.expressionBody());
        }
        List<OclValue> occurrences = evalPlan(sm, g, env, query.planBody());
        if (occurrences == null) {
            return new OclValue.BottomValue(query.resultType());
        }
        return query.resultType().kind() == OclType.Kind.SET
                ? new OclValue.SetValue(query.resultType(), occurrences)
                : new OclValue.BagValue(query.resultType(), occurrences);
    }

    /** Invariant violations computed over G: exactly the objects whose body value is not T. */
    public static List<String> violations(SchemaModel sm, GraphModel g, CoreInvariant unit,
                                          QQuery query) {
        if (query.mode() != QQuery.QueryMode.VIOLATIONS) {
            throw new IllegalArgumentException(
                    "violation evaluation requires Core invariant and Q VIOLATIONS");
        }
        List<String> r = new ArrayList<>();
        for (String oid : GraphObservation.objectsOfClass(g, sm, unit.contextClassKey())) {
            CoreInterpreter.Env env = new CoreInterpreter.Env();
            env.bind(unit.selfVariable(),
                    new OclValue.ObjectValue(OclType.clazz(unit.contextClassKey()), oid));
            OclValue v = evalExpr(sm, g, env, query.expressionBody());
            if (!(v instanceof OclValue.BooleanValue bv
                    && bv.bool() == OclValue.BooleanValue.Bool3.TRUE)) {
                r.add(oid);
            }
        }
        return Collections.unmodifiableList(r);
    }

    private static List<OclValue> foldPredicates(SchemaModel sm, GraphModel g,
                                                 CoreInterpreter.Env env, List<OclValue> occ,
                                                 CoreDeclaration binder, QNode.QExpr body) {
        List<OclValue> out = new ArrayList<>();
        for (OclValue o : occ) {
            var child = env.child();
            child.bind(binder, o);
            out.add(evalExpr(sm, g, child, body));
        }
        return out;
    }

    private static OclValue coerceValue(OclValue src,
                                        org.uet.dse.ocl2cypher.core.CoreExpr.CoercionKind kind,
                                        OclType target) {
        if (src.isBottom()) {
            return new OclValue.BottomValue(target);
        }
        return switch (kind) {
            case INTEGER_TO_REAL -> new OclValue.RealValue(
                    new java.math.BigDecimal(((OclValue.IntegerValue) src).value()));
            case CLASS_UPCAST -> new OclValue.ObjectValue(target,
                    ((OclValue.ObjectValue) src).stableId());
            case COLLECTION_ELEMENT_COERCION -> {
                OclValue.CollectionValue coll = (OclValue.CollectionValue) src;
                OclType elT = target.elementType();
                List<OclValue> els = new ArrayList<>();
                for (OclValue m : OclOps.occurrences(coll)) {
                    if (m.isBottom()) {
                        els.add(new OclValue.BottomValue(elT));
                    } else if (m instanceof OclValue.IntegerValue iv && elT.equals(OclType.REAL)) {
                        els.add(new OclValue.RealValue(new java.math.BigDecimal(iv.value())));
                    } else if (m instanceof OclValue.ObjectValue ov && elT.isClass()) {
                        els.add(new OclValue.ObjectValue(elT, ov.stableId()));
                    } else {
                        els.add(m);
                    }
                }
                yield coll instanceof OclValue.SetValue
                        ? new OclValue.SetValue(target, els)
                        : new OclValue.BagValue(target, els);
            }
        };
    }

    private static OclValue evalUnary(OclValue op,
                                      org.uet.dse.ocl2cypher.core.CoreExpr.UnaryOp uop,
                                      OclType rt) {
        return switch (uop) {
            case BOOLEAN_NOT -> OclOps.boolNot(op);
            case NUMERIC_NEGATE -> OclOps.negate(op, rt);
            case NUMERIC_ABS -> OclOps.abs(op, rt);
            case REAL_FLOOR -> OclOps.floor(op);
            case REAL_ROUND -> OclOps.round(op);
            case COLLECTION_SIZE -> OclOps.size(op, rt);
            case COLLECTION_IS_EMPTY -> OclOps.isEmpty(op);
            case COLLECTION_NOT_EMPTY -> OclOps.notEmpty(op);
            case COLLECTION_SUM -> OclOps.sum(op, rt);
        };
    }

    private static OclValue evalBinary(OclValue l, OclValue r,
                                       org.uet.dse.ocl2cypher.core.CoreExpr.BinaryOp bop,
                                       OclType rt) {
        return switch (bop) {
            case VALUE_EQUAL -> OclOps.equal(l, r);
            case VALUE_NOT_EQUAL -> OclOps.notEqual(l, r);
            case LESS_THAN -> OclOps.compare(l, r, "<");
            case LESS_THAN_OR_EQUAL -> OclOps.compare(l, r, "<=");
            case GREATER_THAN -> OclOps.compare(l, r, ">");
            case GREATER_THAN_OR_EQUAL -> OclOps.compare(l, r, ">=");
            case NUMERIC_ADD -> OclOps.numeric(l, r, rt, "+");
            case NUMERIC_SUBTRACT -> OclOps.numeric(l, r, rt, "-");
            case NUMERIC_MULTIPLY -> OclOps.numeric(l, r, rt, "*");
            case REAL_DIVIDE -> OclOps.numeric(l, r, rt, "/");
            case INTEGER_DIVIDE -> OclOps.numeric(l, r, rt, "div");
            case INTEGER_MOD -> OclOps.numeric(l, r, rt, "mod");
            case NUMERIC_MAX -> OclOps.numeric(l, r, rt, "max");
            case NUMERIC_MIN -> OclOps.numeric(l, r, rt, "min");
            case BOOLEAN_AND -> OclOps.boolAnd(l, r);
            case BOOLEAN_OR -> OclOps.boolOr(l, r);
            case BOOLEAN_XOR -> OclOps.boolXor(l, r);
            case BOOLEAN_IMPLIES -> OclOps.boolImplies(l, r);
            case COLLECTION_COUNT -> OclOps.count(l, r, rt);
            case COLLECTION_INCLUDES -> OclOps.includes(l, r);
            case COLLECTION_EXCLUDES -> OclOps.excludes(l, r);
            case COLLECTION_INCLUDES_ALL -> OclOps.includesAll(l, r);
            case COLLECTION_EXCLUDES_ALL -> OclOps.excludesAll(l, r);
            case SET_UNION -> OclOps.setUnion(l, r, rt);
            case SET_INTERSECTION -> OclOps.setIntersection(l, r, rt);
        };
    }
}
