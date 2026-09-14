package org.uet.dse.ocl2cypher.core;

import java.math.BigDecimal;
import org.uet.dse.ocl2cypher.runtime.ExactReal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.uet.dse.ocl2cypher.runtime.Boolean3;
import org.uet.dse.ocl2cypher.runtime.OclEquality;
import org.uet.dse.ocl2cypher.runtime.OclType;
import org.uet.dse.ocl2cypher.runtime.OclValue;
import org.uet.dse.ocl2cypher.source.model.SchemaModel;
import org.uet.dse.ocl2cypher.source.model.Snapshot;

/**
 * Core interpreter oracle on {@code SM, SN, rho} — the source denotation
 * that the translation then has to preserve through {@code T_G} and {@code R}.
 *
 * <p>The interpreter never converts a diagnostic into a bottom and never
 * confuses whole-collection bottom with an empty collection: each case below
 * returns the scalar bottom, the typed collection bottom, or the defined
 * empty/element-bottom separately, matching the equations in
 * {@code research/OCLscope/OCL_val-formal.md} section 4.
 */
public final class CoreInterpreter {

    private CoreInterpreter() {
    }

    /** Evaluate one {@link CoreUnit}'s body with one binding of its self variable. */
    public static OclValue evalUnit(SchemaModel sm, Snapshot sn,
                                    CoreUnit unit, Env env) {
        return eval(sm, sn, env, unit.body());
    }

    /** Evaluate one invariant on every classified instance and collect violations (F or bottom). */
    public static List<String> violations(SchemaModel sm, Snapshot sn, CoreInvariant unit) {
        List<String> r = new ArrayList<>();
        for (String sid : sn.objectsOfClass(sm, unit.contextClassKey())) {
            Env env = new Env();
            env.bind(unit.selfVariable(),
                    new OclValue.ObjectValue(OclType.clazz(unit.contextClassKey()), sid));
            OclValue body = evalUnit(sm, sn, unit, env);
            if (!booleanIsTrue(body)) {
                r.add(sid);
            }
        }
        return java.util.Collections.unmodifiableList(r);
    }

    private static boolean booleanIsTrue(OclValue v) {
        if (v instanceof OclValue.BottomValue) {
            return false;
        }
        if (v instanceof OclValue.BooleanValue bv) {
            return Boolean3.boolOf(bv) == OclValue.BooleanValue.Bool3.TRUE;
        }
        return false;
    }

    /** Binding by declaration identity, not by surface name. */
    public static final class Env {
        private final java.util.Map<CoreDeclaration, OclValue> map = new java.util.LinkedHashMap<>();
        private final java.util.Map<String, OclValue> parameters = new java.util.LinkedHashMap<>();

        public void bind(CoreDeclaration decl, OclValue v) {
            map.put(Objects.requireNonNull(decl, "decl"), Objects.requireNonNull(v, "value"));
        }

        public Env child() {
            Env c = new Env();
            c.map.putAll(map);
            c.parameters.putAll(parameters);
            return c;
        }

        /** Bind a decoded public Q parameter; lexical declarations use {@link #bind}. */
        public void bindParameter(String name, OclValue value) {
            Objects.requireNonNull(name, "parameter name");
            Objects.requireNonNull(value, "parameter value");
            OclValue previous = parameters.putIfAbsent(name, value);
            if (previous != null && !previous.equals(value)) {
                throw new IllegalStateException("conflicting public parameter " + name);
            }
        }

        public OclValue lookupParameter(String name) {
            OclValue value = parameters.get(name);
            if (value == null) {
                throw new IllegalStateException("unbound public parameter " + name);
            }
            return value;
        }

        public OclValue lookup(CoreDeclaration decl) {
            OclValue v = map.get(decl);
            if (v == null) {
                throw new IllegalStateException("unbound declaration " + decl);
            }
            return v;
        }

        public CoreDeclaration lookup(String name) {
            for (var e : map.entrySet()) {
                if (e.getKey().name().equals(name)) {
                    return e.getKey();
                }
            }
            return null;
        }

        public void bind(String name, OclValue v) {
            // used only where the caller allocated the declaration already
        }
    }

    /* ---------------------------------------------------------------- */

    public static OclValue eval(SchemaModel sm, Snapshot sn, Env env, CoreExpr e) {
        if (e instanceof CoreExpr.LiteralBoolean lb) {
            return lb.literal ? Boolean3.TRUE : Boolean3.FALSE;
        }
        if (e instanceof CoreExpr.LiteralInteger li) {
            return new OclValue.IntegerValue(li.literal);
        }
        if (e instanceof CoreExpr.LiteralReal lr) {
            return new OclValue.RealValue(lr.literal);
        }
        if (e instanceof CoreExpr.LiteralString ls) {
            return new OclValue.StringValue(ls.literal);
        }
        if (e instanceof CoreExpr.Bottom b) {
            return new OclValue.BottomValue(b.type());
        }
        if (e instanceof CoreExpr.Variable v) {
            return env.lookup(v.declaration);
        }
        if (e instanceof CoreExpr.Let let) {
            OclValue value = eval(sm, sn, env, let.value);
            Env child = env.child();
            child.bind(let.binder, value);
            return eval(sm, sn, child, let.inExpr);
        }
        if (e instanceof CoreExpr.IfExpr i) {
            OclValue c = eval(sm, sn, env, i.condition);
            if (c instanceof OclValue.BottomValue) {
                return new OclValue.BottomValue(i.type());
            }
            if (Boolean3.boolOf((OclValue.BooleanValue) c) == OclValue.BooleanValue.Bool3.TRUE) {
                return eval(sm, sn, env, i.thenExpr);
            }
            return eval(sm, sn, env, i.elseExpr);
        }
        if (e instanceof CoreExpr.Coerce cc) {
            OclValue src = eval(sm, sn, env, cc.source);
            if (src.isBottom()) {
                return new OclValue.BottomValue(cc.type());
            }
            return switch (cc.kind) {
                case INTEGER_TO_REAL -> new OclValue.RealValue(
                        new BigDecimal(((OclValue.IntegerValue) src).value()));
                case CLASS_UPCAST -> new OclValue.ObjectValue(
                        cc.type(), ((OclValue.ObjectValue) src).stableId());
                case COLLECTION_ELEMENT_COERCION -> {
                    OclValue.CollectionValue coll = (OclValue.CollectionValue) src;
                    // Element-wise coercion keeps the outer kind (no Set/Bag swap).
                    List<OclValue> els = new ArrayList<>();
                    OclType targetEl = cc.type().elementType();
                    java.util.function.Function<OclValue, OclValue> coerceEl =
                            (OclValue el) -> {
                        if (el instanceof OclValue.BottomValue bv) {
                            return new OclValue.BottomValue(targetEl);
                        }
                        if (el instanceof OclValue.IntegerValue iv
                                && targetEl.equals(OclType.REAL)) {
                            return new OclValue.RealValue(new BigDecimal(iv.value()));
                        }
                        if (el instanceof OclValue.ObjectValue ov
                                && targetEl.isClass()) {
                            return new OclValue.ObjectValue(targetEl, ov.stableId());
                        }
                        return el;
                    };
                    if (coll instanceof OclValue.SetValue sv) {
                        for (OclValue m : sv.members()) {
                            els.add(coerceEl.apply(m));
                        }
                        yield new OclValue.SetValue(cc.type(), List.copyOf(els));
                    }
                    OclValue.BagValue bv = (OclValue.BagValue) coll;
                    for (OclValue occ : bv.occurrences()) {
                        els.add(coerceEl.apply(occ));
                    }
                    yield new OclValue.BagValue(cc.type(), List.copyOf(els));
                }
            };
        }
        if (e instanceof CoreExpr.AttributeRead ar) {
            OclValue recv = eval(sm, sn, env, ar.source);
            if (recv instanceof OclValue.BottomValue) {
                return new OclValue.BottomValue(ar.type());
            }
            String sid = ((OclValue.ObjectValue) recv).stableId();
            var slot = sn.attributeSlot(sid, ar.attributeName);
            if (slot.isEmpty()) {
                return new OclValue.BottomValue(ar.type());
            }
            return slot.get();
        }
        if (e instanceof CoreExpr.Navigation nav) {
            return evalNavigation(sm, sn, env, nav, e.type());
        }
        if (e instanceof CoreExpr.AssociationClassNavigation acn) {
            OclValue recv = eval(sm, sn, env, acn.source);
            if (recv instanceof OclValue.BottomValue) {
                return new OclValue.BottomValue(acn.type());
            }
            List<OclValue> qualifiers = new ArrayList<>();
            for (CoreExpr qualifier : acn.qualifiers) {
                OclValue value = eval(sm, sn, env, qualifier);
                if (value instanceof OclValue.BottomValue) {
                    return new OclValue.BottomValue(acn.type());
                }
                qualifiers.add(value);
            }
            List<String> occurrences = sn.associationClassObjects(sm,
                    acn.associationClassKey,
                    ((OclValue.ObjectValue) recv).stableId(), qualifiers);
            if (acn.kind == CoreExpr.NavKind.TO_ONE) {
                return occurrences.size() == 1
                        ? new OclValue.ObjectValue(acn.type(), occurrences.get(0))
                        : new OclValue.BottomValue(acn.type());
            }
            List<OclValue> members = occurrences.stream()
                    .map(id -> (OclValue) new OclValue.ObjectValue(
                            acn.type().elementType(), id)).toList();
            return acn.type().kind() == OclType.Kind.BAG
                    ? new OclValue.BagValue(acn.type(), members)
                    : new OclValue.SetValue(acn.type(), members);
        }
        if (e instanceof CoreExpr.AllInstances ai) {
            List<OclValue> members = new ArrayList<>();
            for (String sid : sn.objectsOfClass(sm, ai.classKey)) {
                members.add(new OclValue.ObjectValue(OclType.clazz(ai.classKey), sid));
            }
            return new OclValue.SetValue(OclType.set(OclType.clazz(ai.classKey)), members);
        }
        if (e instanceof CoreExpr.TypeTest tt) {
            OclValue src = eval(sm, sn, env, tt.source);
            if (src instanceof OclValue.BottomValue) {
                String bottomType = src.type().className();
                boolean match = tt.kind == CoreExpr.TypeTestKind.EXACT_TYPE
                        ? bottomType.equals(tt.targetClassKey)
                        : sm.conforms(bottomType, tt.targetClassKey);
                return match ? Boolean3.TRUE : Boolean3.FALSE;
            }
            String dyn = sn.hasObject(((OclValue.ObjectValue) src).stableId())
                    ? sn.object(((OclValue.ObjectValue) src).stableId()).dynamicClassKey
                    : ((OclValue.ObjectValue) src).stableId();
            String targetLess = tt.targetClassKey;
            // use the stored dynamicClassKey when available; otherwise the carried key
            String effectiveDyn;
            if (src instanceof OclValue.ObjectValue ov) {
                effectiveDyn = sn.hasObject(ov.stableId())
                        ? sn.object(ov.stableId()).dynamicClassKey
                        : ov.type().className();
            } else {
                effectiveDyn = dyn;
            }
            boolean match = tt.kind == CoreExpr.TypeTestKind.EXACT_TYPE
                    ? effectiveDyn.equals(targetLess)
                    : sm.conforms(effectiveDyn, targetLess);
            return match ? Boolean3.TRUE : Boolean3.FALSE;
        }
        if (e instanceof CoreExpr.TypeCast tc) {
            OclValue src = eval(sm, sn, env, tc.source);
            if (src instanceof OclValue.BottomValue) {
                return new OclValue.BottomValue(e.type());
            }
            String effectiveDyn = src instanceof OclValue.ObjectValue ov
                    ? (sn.hasObject(ov.stableId()) ? sn.object(ov.stableId()).dynamicClassKey
                            : ov.type().className())
                    : "";
            if (!sm.conforms(effectiveDyn, tc.targetClassKey)) {
                return new OclValue.BottomValue(e.type());
            }
            return new OclValue.ObjectValue(e.type(), ((OclValue.ObjectValue) src).stableId());
        }
        if (e instanceof CoreExpr.Unary u) {
            return evalUnary(sm, sn, env, u);
        }
        if (e instanceof CoreExpr.Binary b) {
            return evalBinary(sm, sn, env, b);
        }
        if (e instanceof CoreExpr.CollectionLiteral cl) {
            List<OclValue> els = new ArrayList<>();
            for (CoreExpr elem : cl.elements) {
                els.add(eval(sm, sn, env, elem));
            }
            if (cl.kind == CoreExpr.CollectionKind.SET) {
                return new OclValue.SetValue(cl.type(), els);
            }
            return new OclValue.BagValue(cl.type(), els);
        }
        if (e instanceof CoreExpr.Iterator it) {
            return evalIterator(sm, sn, env, it);
        }
        throw new IllegalStateException("no interpreter case for " + e.getClass().getSimpleName());
    }

    private static OclValue evalNavigation(SchemaModel sm, Snapshot sn, Env env,
                                           CoreExpr.Navigation nav, OclType resultType) {
        OclValue recv = eval(sm, sn, env, nav.source);
        if (recv instanceof OclValue.BottomValue) {
            return nav.kind == CoreExpr.NavKind.TO_ONE
                    ? new OclValue.BottomValue(resultType)
                    : new OclValue.BottomValue(resultType);
        }
        String sid = ((OclValue.ObjectValue) recv).stableId();
        var resolved = sm.navigation(sn.object(sid).dynamicClassKey(), nav.roleName);
        if (resolved == null) {
            throw new IllegalStateException("no association for role " + nav.roleName);
        }
        List<OclValue> qualifierValues = new ArrayList<>();
        for (CoreExpr q : nav.qualifiers) {
            OclValue qv = eval(sm, sn, env, q);
            if (qv instanceof OclValue.BottomValue) {
                return new OclValue.BottomValue(resultType);
            }
            qualifierValues.add(qv);
        }
        List<String> targets = sn.linkTargets(sm, nav.roleName, sid, nav.reverse, qualifierValues);
        if (nav.kind == CoreExpr.NavKind.TO_ONE) {
            if (targets.size() != 1) {
                return new OclValue.BottomValue(resultType);
            }
            return new OclValue.ObjectValue(resultType, targets.get(0));
        }
        List<OclValue> members = new ArrayList<>();
        for (String tid : targets) {
            members.add(new OclValue.ObjectValue(resultType.elementType(), tid));
        }
        return resultType.kind() == OclType.Kind.BAG
                ? new OclValue.BagValue(resultType, members)
                : new OclValue.SetValue(resultType, members);
    }

    private static OclValue evalUnary(SchemaModel sm, Snapshot sn, Env env, CoreExpr.Unary u) {
        OclValue op = eval(sm, sn, env, u.operand);
        if (op instanceof OclValue.BottomValue) {
            return new OclValue.BottomValue(u.type());
        }
        return switch (u.operator) {
            case BOOLEAN_NOT -> Boolean3.not(op);
            case NUMERIC_NEGATE -> {
                if (op instanceof OclValue.IntegerValue iv) {
                    yield new OclValue.IntegerValue(iv.value().negate());
                }
                yield new OclValue.RealValue(((OclValue.RealValue) op).exactValue().negate());
            }
            case NUMERIC_ABS -> {
                if (op instanceof OclValue.IntegerValue iv) {
                    yield new OclValue.IntegerValue(iv.value().abs());
                }
                ExactReal d = ((OclValue.RealValue) op).exactValue();
                yield new OclValue.RealValue(d.signum() < 0 ? d.negate() : d);
            }
            case REAL_FLOOR -> {
                ExactReal d = ((OclValue.RealValue) op).exactValue();
                BigInteger floored = d.floor();
                yield new OclValue.IntegerValue(floored);
            }
            case REAL_ROUND -> {
                ExactReal d = ((OclValue.RealValue) op).exactValue();
                BigInteger rounded = d.round();
                yield new OclValue.IntegerValue(rounded);
            }
            case COLLECTION_SIZE -> {
                OclValue.CollectionValue c = (OclValue.CollectionValue) op;
                yield new OclValue.IntegerValue(BigInteger.valueOf(c.size()));
            }
            case COLLECTION_IS_EMPTY -> {
                OclValue.CollectionValue c = (OclValue.CollectionValue) op;
                yield c.isEmpty() ? Boolean3.TRUE : Boolean3.FALSE;
            }
            case COLLECTION_NOT_EMPTY -> {
                OclValue.CollectionValue c = (OclValue.CollectionValue) op;
                yield c.isEmpty() ? Boolean3.FALSE : Boolean3.TRUE;
            }
            case COLLECTION_SUM -> {
                OclValue.CollectionValue c = (OclValue.CollectionValue) op;
                List<OclValue> terms = c instanceof OclValue.SetValue sv
                        ? sv.members() : ((OclValue.BagValue) c).occurrences();
                boolean elementNumeric = c.type().elementType().equals(OclType.INTEGER);
                if (elementNumeric) {
                    BigInteger sum = BigInteger.ZERO;
                    for (OclValue m : terms) {
                        if (m.isBottom()) {
                            yield new OclValue.BottomValue(u.type());
                        }
                        sum = sum.add(((OclValue.IntegerValue) m).value());
                    }
                    yield new OclValue.IntegerValue(sum);
                }
                ExactReal dsum = ExactReal.ZERO;
                for (OclValue m : terms) {
                    if (m.isBottom()) {
                        yield new OclValue.BottomValue(u.type());
                    }
                    dsum = dsum.add(toDecimal(m));
                }
                yield new OclValue.RealValue(dsum);
            }
        };
    }

    private static OclValue evalBinary(SchemaModel sm, Snapshot sn, Env env, CoreExpr.Binary b) {
        OclValue left = eval(sm, sn, env, b.left);
        OclValue right = eval(sm, sn, env, b.right);
        return switch (b.operator) {
            case NUMERIC_ADD -> numericAdd(left, right, b.type());
            case NUMERIC_SUBTRACT -> numericSubtract(left, right, b.type());
            case NUMERIC_MULTIPLY -> numericMultiply(left, right, b.type());
            case REAL_DIVIDE -> {
                if (left.isBottom() || right.isBottom()) {
                    yield new OclValue.BottomValue(OclType.REAL);
                }
                ExactReal rr = ((OclValue.RealValue) right).exactValue();
                if (rr.signum() == 0) {
                    yield new OclValue.BottomValue(OclType.REAL);
                }
                ExactReal lr = ((OclValue.RealValue) left).exactValue();
                yield new OclValue.RealValue(
                        lr.divide(rr));
            }
            case INTEGER_DIVIDE -> {
                if (left.isBottom() || right.isBottom()) {
                    yield new OclValue.BottomValue(OclType.INTEGER);
                }
                BigInteger ll = ((OclValue.IntegerValue) left).value();
                BigInteger rr = ((OclValue.IntegerValue) right).value();
                if (rr.equals(BigInteger.ZERO)) {
                    yield new OclValue.BottomValue(OclType.INTEGER);
                }
                // BigInteger.divide truncates toward zero, matching OCL `div`.
                yield new OclValue.IntegerValue(ll.divide(rr));
            }
            case INTEGER_MOD -> {
                if (left.isBottom() || right.isBottom()) {
                    yield new OclValue.BottomValue(OclType.INTEGER);
                }
                BigInteger ll = ((OclValue.IntegerValue) left).value();
                BigInteger rr = ((OclValue.IntegerValue) right).value();
                if (rr.equals(BigInteger.ZERO)) {
                    yield new OclValue.BottomValue(OclType.INTEGER);
                }
                // OCL_val: mod(i,j) = i - j * idiv(i,j), so the sign follows i.
                yield new OclValue.IntegerValue(ll.subtract(rr.multiply(ll.divide(rr))));
            }
            case NUMERIC_MAX -> numericMax(left, right, b.type());
            case NUMERIC_MIN -> numericMin(left, right, b.type());
            case LESS_THAN, LESS_THAN_OR_EQUAL, GREATER_THAN, GREATER_THAN_OR_EQUAL -> {
                if (left.isBottom() || right.isBottom()) {
                    yield new OclValue.BottomValue(OclType.BOOLEAN);
                }
                ExactReal lv = toDecimal(left);
                ExactReal rv = toDecimal(right);
                int cmp = lv.compareTo(rv);
                boolean won = switch (b.operator) {
                    case LESS_THAN -> cmp < 0;
                    case LESS_THAN_OR_EQUAL -> cmp <= 0;
                    case GREATER_THAN -> cmp > 0;
                    default -> cmp >= 0;
                };
                yield won ? Boolean3.TRUE : Boolean3.FALSE;
            }
            case VALUE_EQUAL -> {
                OclEquality.BoolKind k = OclEquality.equal(left, right);
                yield k == OclEquality.BoolKind.TRUE ? Boolean3.TRUE : Boolean3.FALSE;
            }
            case VALUE_NOT_EQUAL -> {
                OclEquality.BoolKind k = OclEquality.equal(left, right);
                yield k == OclEquality.BoolKind.TRUE ? Boolean3.FALSE : Boolean3.TRUE;
            }
            case BOOLEAN_AND -> {
                yield Boolean3.and(left, right);
            }
            case BOOLEAN_OR -> Boolean3.or(left, right);
            case BOOLEAN_XOR -> Boolean3.xor(left, right);
            case BOOLEAN_IMPLIES -> Boolean3.implies(left, right);
            case COLLECTION_COUNT -> {
                if (left instanceof OclValue.BottomValue) {
                    yield new OclValue.BottomValue(OclType.INTEGER);
                }
                OclValue.CollectionValue coll = (OclValue.CollectionValue) left;
                int c = coll instanceof OclValue.SetValue sv ? collCount(sv, right)
                        : bagCount((OclValue.BagValue) coll, right);
                yield new OclValue.IntegerValue(BigInteger.valueOf(c));
            }
            case COLLECTION_INCLUDES -> {
                if (left instanceof OclValue.BottomValue) {
                    yield new OclValue.BottomValue(OclType.BOOLEAN);
                }
                OclValue.CollectionValue coll = (OclValue.CollectionValue) left;
                yield occurs(coll, right) ? Boolean3.TRUE : Boolean3.FALSE;
            }
            case COLLECTION_EXCLUDES ->
                    Boolean3.not(evalBinary(sm, sn, env,
                            new CoreExpr.Binary(b.span, CoreExpr.BinaryOp.COLLECTION_INCLUDES,
                                    b.left, b.right, OclType.BOOLEAN)));
            case COLLECTION_INCLUDES_ALL -> {
                if (left instanceof OclValue.BottomValue
                        || right instanceof OclValue.BottomValue) {
                    yield new OclValue.BottomValue(OclType.BOOLEAN);
                }
                yield includesAll((OclValue.CollectionValue) left,
                        (OclValue.CollectionValue) right) ? Boolean3.TRUE : Boolean3.FALSE;
            }
            case COLLECTION_EXCLUDES_ALL -> {
                if (left instanceof OclValue.BottomValue
                        || right instanceof OclValue.BottomValue) {
                    yield new OclValue.BottomValue(OclType.BOOLEAN);
                }
                boolean disjoint = true;
                OclValue.CollectionValue leftCollection =
                        (OclValue.CollectionValue) left;
                List<OclValue> rightOccurrences = right instanceof OclValue.SetValue set
                        ? set.members() : ((OclValue.BagValue) right).occurrences();
                for (OclValue member : rightOccurrences) {
                    if (occurs(leftCollection, member)) {
                        disjoint = false;
                        break;
                    }
                }
                yield disjoint ? Boolean3.TRUE : Boolean3.FALSE;
            }
            case SET_UNION -> {
                if (left instanceof OclValue.BottomValue || right instanceof OclValue.BottomValue) {
                    yield new OclValue.BottomValue(b.type());
                }
                List<OclValue> members = new ArrayList<>();
                members.addAll(((OclValue.SetValue) left).members());
                for (OclValue m : ((OclValue.SetValue) right).members()) {
                    if (!occurs((OclValue.CollectionValue) left, m)) {
                        members.add(m);
                    }
                }
                yield new OclValue.SetValue(b.type(), members);
            }
            case SET_INTERSECTION -> {
                if (left instanceof OclValue.BottomValue || right instanceof OclValue.BottomValue) {
                    yield new OclValue.BottomValue(b.type());
                }
                List<OclValue> members = new ArrayList<>();
                for (OclValue m : ((OclValue.SetValue) left).members()) {
                    if (occurs((OclValue.CollectionValue) right, m)) {
                        members.add(m);
                    }
                }
                yield new OclValue.SetValue(b.type(), members);
            }
        };
    }

    private static boolean occurs(OclValue.CollectionValue coll, OclValue sought) {
        if (coll instanceof OclValue.SetValue sv) {
            return sv.containsUsingOclEquality(sought);
        }
        return ((OclValue.BagValue) coll).containsUsingOclEquality(sought);
    }

    private static int collCount(OclValue.SetValue sv, OclValue sought) {
        return sv.containsUsingOclEquality(sought) ? 1 : 0;
    }

    private static int bagCount(OclValue.BagValue bv, OclValue sought) {
        int c = 0;
        for (OclValue occ : bv.occurrences()) {
            if (OclEquality.equal(occ, sought) == OclEquality.BoolKind.TRUE) {
                c++;
            }
        }
        return c;
    }

    private static boolean includesAll(OclValue.CollectionValue left, OclValue.CollectionValue right) {
        List<OclValue> rightMembers = right instanceof OclValue.SetValue sv ? sv.members()
                : ((OclValue.BagValue) right).occurrences();
        for (OclValue m : rightMembers) {
            if (!occurs(left, m)) {
                return false;
            }
        }
        return true;
    }

    private static OclValue numericAdd(OclValue a, OclValue b, OclType rt) {
        if (a.isBottom() || b.isBottom()) {
            return new OclValue.BottomValue(rt);
        }
        if (rt.equals(OclType.INTEGER)) {
            return new OclValue.IntegerValue(
                    ((OclValue.IntegerValue) a).value().add(((OclValue.IntegerValue) b).value()));
        }
        return new OclValue.RealValue(
                toDecimal(a).add(toDecimal(b)));
    }

    private static OclValue numericSubtract(OclValue a, OclValue b, OclType rt) {
        if (a.isBottom() || b.isBottom()) {
            return new OclValue.BottomValue(rt);
        }
        if (rt.equals(OclType.INTEGER)) {
            return new OclValue.IntegerValue(
                    ((OclValue.IntegerValue) a).value().subtract(((OclValue.IntegerValue) b).value()));
        }
        return new OclValue.RealValue(toDecimal(a).subtract(toDecimal(b)));
    }

    private static OclValue numericMultiply(OclValue a, OclValue b, OclType rt) {
        if (a.isBottom() || b.isBottom()) {
            return new OclValue.BottomValue(rt);
        }
        if (rt.equals(OclType.INTEGER)) {
            return new OclValue.IntegerValue(
                    ((OclValue.IntegerValue) a).value().multiply(((OclValue.IntegerValue) b).value()));
        }
        return new OclValue.RealValue(toDecimal(a).multiply(toDecimal(b)));
    }

    private static OclValue numericMax(OclValue a, OclValue b, OclType rt) {
        if (a.isBottom() || b.isBottom()) {
            return new OclValue.BottomValue(rt);
        }
        ExactReal da = toDecimal(a);
        ExactReal db = toDecimal(b);
        boolean aWin = da.compareTo(db) >= 0;
        return aWin ? a : b;
    }

    private static OclValue numericMin(OclValue a, OclValue b, OclType rt) {
        if (a.isBottom() || b.isBottom()) {
            return new OclValue.BottomValue(rt);
        }
        ExactReal da = toDecimal(a);
        ExactReal db = toDecimal(b);
        return da.compareTo(db) <= 0 ? a : b;
    }

    private static ExactReal toDecimal(OclValue v) {
        if (v instanceof OclValue.IntegerValue iv) {
            return ExactReal.of(iv.value());
        }
        return ((OclValue.RealValue) v).exactValue();
    }

    private static OclValue evalIterator(SchemaModel sm, Snapshot sn, Env env, CoreExpr.Iterator it) {
        switch (it.iteratorKind) {
            case EXISTS, FORALL -> {
                List<OclValue> occ = occ(sm, sn, env, it);
                if (occ == null) {
                    return new OclValue.BottomValue(OclType.BOOLEAN);
                }
                boolean hasBottom = false;
                for (OclValue o : occ) {
                    Env child = env.child();
                    child.bind(it.iterator, o);
                    OclValue p = eval(sm, sn, child, it.body);
                    if (p instanceof OclValue.BottomValue) {
                        hasBottom = true;
                    } else if (it.iteratorKind == CoreExpr.IteratorKind.EXISTS
                            && Boolean3.boolOf((OclValue.BooleanValue) p)
                            == OclValue.BooleanValue.Bool3.TRUE) {
                        return Boolean3.TRUE;
                    } else if (it.iteratorKind == CoreExpr.IteratorKind.FORALL
                            && Boolean3.boolOf((OclValue.BooleanValue) p)
                            == OclValue.BooleanValue.Bool3.FALSE) {
                        return Boolean3.FALSE;
                    }
                }
                if (it.iteratorKind == CoreExpr.IteratorKind.EXISTS) {
                    return hasBottom ? new OclValue.BottomValue(OclType.BOOLEAN) : Boolean3.FALSE;
                }
                return hasBottom ? new OclValue.BottomValue(OclType.BOOLEAN) : Boolean3.TRUE;
            }
            case SELECT, REJECT -> {
                List<OclValue> occ = occWithBottom(sm, sn, env, it);
                if (occ == null) {
                    return new OclValue.BottomValue(it.type());
                }
                for (OclValue o : occ) {
                    Env child = env.child();
                    child.bind(it.iterator, o);
                    OclValue p = eval(sm, sn, child, it.body);
                    if (p instanceof OclValue.BottomValue) {
                        return new OclValue.BottomValue(it.type());
                    }
                }
                List<OclValue> kept = new ArrayList<>();
                for (OclValue o : occ) {
                    Env child = env.child();
                    child.bind(it.iterator, o);
                    OclValue p = eval(sm, sn, child, it.body);
                    boolean keep = it.iteratorKind == CoreExpr.IteratorKind.SELECT
                            ? Boolean3.boolOf((OclValue.BooleanValue) p) == OclValue.BooleanValue.Bool3.TRUE
                            : Boolean3.boolOf((OclValue.BooleanValue) p) == OclValue.BooleanValue.Bool3.FALSE;
                    if (keep) {
                        kept.add(o);
                    }
                }
                if (it.type().kind() == OclType.Kind.SET) {
                    return new OclValue.SetValue(it.type(), kept);
                }
                return new OclValue.BagValue(it.type(), kept);
            }
            case COLLECT -> {
                List<OclValue> occ = occ(sm, sn, env, it);
                if (occ == null) {
                    return new OclValue.BottomValue(it.type());
                }
                List<OclValue> images = new ArrayList<>();
                for (OclValue o : occ) {
                    Env child = env.child();
                    child.bind(it.iterator, o);
                    images.add(eval(sm, sn, child, it.body));
                }
                return new OclValue.BagValue(it.type(), images);
            }
            default -> throw new IllegalStateException("unknown iterator " + it.iteratorKind);
        }
    }

    private static List<OclValue> occ(SchemaModel sm, Snapshot sn, Env env, CoreExpr.Iterator it) {
        OclValue src = eval(sm, sn, env, it.source);
        if (src instanceof OclValue.BottomValue) {
            return null;
        }
        if (src instanceof OclValue.SetValue sv) {
            return new ArrayList<>(sv.members());
        }
        return new ArrayList<>(((OclValue.BagValue) src).occurrences());
    }

    private static List<OclValue> occWithBottom(SchemaModel sm, Snapshot sn, Env env, CoreExpr.Iterator it) {
        return occ(sm, sn, env, it);
    }
}
