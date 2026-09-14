package org.uet.dse.ocl2cypher.runtime;

import java.math.BigDecimal;
import org.uet.dse.ocl2cypher.runtime.ExactReal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared OCL_val operator semantics used by BOTH carriers: the Core
 * interpreter (evaluating over {@code SM,SN}) and the Q_CYP oracle
 * (evaluating over the graph {@code G}). Keeping one implementation of the
 * value algebra is what makes the differential
 * {@code evalCore(i) ~ evalQ(T(i))} a real test of the translation instead of
 * a test of two independently written evaluators.
 */
public final class OclOps {

    private OclOps() {
    }

    /* ---- Boolean3 ---- */

    public static OclValue boolNot(OclValue v) {
        return Boolean3.not(v);
    }

    public static OclValue boolAnd(OclValue a, OclValue b) {
        return Boolean3.and(a, b);
    }

    public static OclValue boolOr(OclValue a, OclValue b) {
        return Boolean3.or(a, b);
    }

    public static OclValue boolXor(OclValue a, OclValue b) {
        return Boolean3.xor(a, b);
    }

    public static OclValue boolImplies(OclValue a, OclValue b) {
        return Boolean3.implies(a, b);
    }

    /* ---- equality (total) ---- */

    public static OclValue equal(OclValue a, OclValue b) {
        return OclEquality.equal(a, b) == OclEquality.BoolKind.TRUE
                ? Boolean3.TRUE : Boolean3.FALSE;
    }

    public static OclValue notEqual(OclValue a, OclValue b) {
        return OclEquality.equal(a, b) == OclEquality.BoolKind.TRUE
                ? Boolean3.FALSE : Boolean3.TRUE;
    }

    /* ---- numeric ---- */

    public static OclValue numeric(OclValue a, OclValue b, OclType rt, String op) {
        if (a.isBottom() || b.isBottom()) {
            return new OclValue.BottomValue(rt);
        }
        if (rt.equals(OclType.INTEGER)) {
            BigInteger x = ((OclValue.IntegerValue) a).value();
            BigInteger y = ((OclValue.IntegerValue) b).value();
            return switch (op) {
                case "+" -> new OclValue.IntegerValue(x.add(y));
                case "-" -> new OclValue.IntegerValue(x.subtract(y));
                case "*" -> new OclValue.IntegerValue(x.multiply(y));
                case "div" -> y.equals(BigInteger.ZERO)
                        ? new OclValue.BottomValue(rt) : new OclValue.IntegerValue(x.divide(y));
                case "mod" -> y.equals(BigInteger.ZERO)
                        ? new OclValue.BottomValue(rt)
                        : new OclValue.IntegerValue(x.subtract(y.multiply(x.divide(y))));
                case "max" -> x.max(y) == x ? a : b;
                case "min" -> x.min(y) == x ? a : b;
                default -> throw new IllegalArgumentException("numeric op " + op);
            };
        }
        ExactReal x = decimal(a);
        ExactReal y = decimal(b);
        return switch (op) {
            case "+" -> new OclValue.RealValue(x.add(y));
            case "-" -> new OclValue.RealValue(x.subtract(y));
            case "*" -> new OclValue.RealValue(x.multiply(y));
            case "/" -> y.signum() == 0
                    ? new OclValue.BottomValue(rt)
                    : new OclValue.RealValue(x.divide(y));
            case "max" -> x.compareTo(y) >= 0 ? a : b;
            case "min" -> x.compareTo(y) <= 0 ? a : b;
            default -> throw new IllegalArgumentException("numeric op " + op);
        };
    }

    public static OclValue negate(OclValue v, OclType rt) {
        if (v.isBottom()) {
            return new OclValue.BottomValue(rt);
        }
        if (v instanceof OclValue.IntegerValue iv) {
            return new OclValue.IntegerValue(iv.value().negate());
        }
        return new OclValue.RealValue(((OclValue.RealValue) v).exactValue().negate());
    }

    public static OclValue abs(OclValue v, OclType rt) {
        if (v.isBottom()) {
            return new OclValue.BottomValue(rt);
        }
        if (v instanceof OclValue.IntegerValue iv) {
            return new OclValue.IntegerValue(iv.value().abs());
        }
        ExactReal d = ((OclValue.RealValue) v).exactValue();
        return new OclValue.RealValue(d.signum() < 0 ? d.negate() : d);
    }

    public static OclValue floor(OclValue v) {
        if (!(v instanceof OclValue.RealValue rv)) {
            return new OclValue.BottomValue(OclType.INTEGER);
        }
        return new OclValue.IntegerValue(
                rv.exactValue().floor());
    }

    public static OclValue round(OclValue v) {
        if (!(v instanceof OclValue.RealValue rv)) {
            return new OclValue.BottomValue(OclType.INTEGER);
        }
        return new OclValue.IntegerValue(
                rv.exactValue().round());
    }

    public static OclValue compare(OclValue a, OclValue b, String op) {
        if (a.isBottom() || b.isBottom()) {
            return new OclValue.BottomValue(OclType.BOOLEAN);
        }
        int cmp = decimal(a).compareTo(decimal(b));
        boolean won = switch (op) {
            case "<" -> cmp < 0;
            case "<=" -> cmp <= 0;
            case ">" -> cmp > 0;
            default -> cmp >= 0;
        };
        return won ? Boolean3.TRUE : Boolean3.FALSE;
    }

    /* ---- collections ---- */

    public static List<OclValue> occurrences(OclValue coll) {
        if (coll instanceof OclValue.SetValue sv) {
            return new ArrayList<>(sv.members());
        }
        return new ArrayList<>(((OclValue.BagValue) coll).occurrences());
    }

    public static OclValue size(OclValue coll, OclType collType) {
        if (coll instanceof OclValue.BottomValue) {
            return new OclValue.BottomValue(OclType.INTEGER);
        }
        long n = coll instanceof OclValue.SetValue sv ? sv.size()
                : ((OclValue.BagValue) coll).size();
        return new OclValue.IntegerValue(BigInteger.valueOf(n));
    }

    public static OclValue isEmpty(OclValue coll) {
        if (coll instanceof OclValue.BottomValue) {
            return new OclValue.BottomValue(OclType.BOOLEAN);
        }
        long n = coll instanceof OclValue.SetValue sv ? sv.size()
                : ((OclValue.BagValue) coll).size();
        return n == 0 ? Boolean3.TRUE : Boolean3.FALSE;
    }

    public static OclValue notEmpty(OclValue coll) {
        if (coll instanceof OclValue.BottomValue) {
            return new OclValue.BottomValue(OclType.BOOLEAN);
        }
        long n = coll instanceof OclValue.SetValue sv ? sv.size()
                : ((OclValue.BagValue) coll).size();
        return n > 0 ? Boolean3.TRUE : Boolean3.FALSE;
    }

    public static OclValue count(OclValue coll, OclValue sought, OclType collType) {
        if (coll instanceof OclValue.BottomValue) {
            return new OclValue.BottomValue(OclType.INTEGER);
        }
        int c = 0;
        for (OclValue m : occurrences(coll)) {
            if (OclEquality.equal(m, sought) == OclEquality.BoolKind.TRUE) {
                c++;
            }
        }
        return new OclValue.IntegerValue(BigInteger.valueOf(c));
    }

    public static OclValue sum(OclValue coll, OclType resultType) {
        if (coll instanceof OclValue.BottomValue) {
            return new OclValue.BottomValue(resultType);
        }
        List<OclValue> terms = occurrences(coll);
        if (resultType.equals(OclType.INTEGER)) {
            BigInteger sum = BigInteger.ZERO;
            for (OclValue m : terms) {
                if (m.isBottom()) {
                    return new OclValue.BottomValue(resultType);
                }
                sum = sum.add(((OclValue.IntegerValue) m).value());
            }
            return new OclValue.IntegerValue(sum);
        }
        ExactReal sum = ExactReal.ZERO;
        for (OclValue m : terms) {
            if (m.isBottom()) {
                return new OclValue.BottomValue(resultType);
            }
            sum = sum.add(decimal(m));
        }
        return new OclValue.RealValue(sum);
    }

    public static OclValue includes(OclValue coll, OclValue sought) {
        if (coll instanceof OclValue.BottomValue) {
            return new OclValue.BottomValue(OclType.BOOLEAN);
        }
        return occurs(coll, sought) ? Boolean3.TRUE : Boolean3.FALSE;
    }

    public static OclValue excludes(OclValue coll, OclValue sought) {
        if (coll instanceof OclValue.BottomValue) {
            return new OclValue.BottomValue(OclType.BOOLEAN);
        }
        return occurs(coll, sought) ? Boolean3.FALSE : Boolean3.TRUE;
    }

    public static OclValue includesAll(OclValue left, OclValue right) {
        if (left instanceof OclValue.BottomValue || right instanceof OclValue.BottomValue) {
            return new OclValue.BottomValue(OclType.BOOLEAN);
        }
        for (OclValue m : occurrences(right)) {
            if (!occurs(left, m)) {
                return Boolean3.FALSE;
            }
        }
        return Boolean3.TRUE;
    }

    public static OclValue excludesAll(OclValue left, OclValue right) {
        if (left instanceof OclValue.BottomValue || right instanceof OclValue.BottomValue) {
            return new OclValue.BottomValue(OclType.BOOLEAN);
        }
        for (OclValue m : occurrences(right)) {
            if (occurs(left, m)) {
                return Boolean3.FALSE;
            }
        }
        return Boolean3.TRUE;
    }

    public static OclValue setUnion(OclValue left, OclValue right, OclType resultType) {
        if (left instanceof OclValue.BottomValue || right instanceof OclValue.BottomValue) {
            return new OclValue.BottomValue(resultType);
        }
        List<OclValue> members = new ArrayList<>(occurrences(left));
        for (OclValue m : occurrences(right)) {
            if (!occurs(left, m)) {
                members.add(m);
            }
        }
        return new OclValue.SetValue(resultType, members);
    }

    public static OclValue setIntersection(OclValue left, OclValue right, OclType resultType) {
        if (left instanceof OclValue.BottomValue || right instanceof OclValue.BottomValue) {
            return new OclValue.BottomValue(resultType);
        }
        List<OclValue> members = new ArrayList<>();
        for (OclValue m : occurrences(left)) {
            if (occurs(right, m)) {
                members.add(m);
            }
        }
        return new OclValue.SetValue(resultType, members);
    }

    public static boolean occurs(OclValue coll, OclValue sought) {
        for (OclValue m : occurrences(coll)) {
            if (OclEquality.equal(m, sought) == OclEquality.BoolKind.TRUE) {
                return true;
            }
        }
        return false;
    }

    /** Three-valued fold helpers for exists/forAll over an occurrence list. */
    public static OclValue foldExists(List<OclValue> predicateResults) {
        boolean hasBottom = false;
        for (OclValue p : predicateResults) {
            OclValue.BooleanValue.Bool3 b = Boolean3.boolOf(p);
            if (b == OclValue.BooleanValue.Bool3.TRUE) {
                return Boolean3.TRUE;
            }
            if (b == OclValue.BooleanValue.Bool3.BOTTOM) {
                hasBottom = true;
            }
        }
        return hasBottom ? new OclValue.BottomValue(OclType.BOOLEAN) : Boolean3.FALSE;
    }

    public static OclValue foldForAll(List<OclValue> predicateResults) {
        boolean hasBottom = false;
        for (OclValue p : predicateResults) {
            OclValue.BooleanValue.Bool3 b = Boolean3.boolOf(p);
            if (b == OclValue.BooleanValue.Bool3.FALSE) {
                return Boolean3.FALSE;
            }
            if (b == OclValue.BooleanValue.Bool3.BOTTOM) {
                hasBottom = true;
            }
        }
        return hasBottom ? new OclValue.BottomValue(OclType.BOOLEAN) : Boolean3.TRUE;
    }

    private static ExactReal decimal(OclValue v) {
        if (v instanceof OclValue.IntegerValue iv) {
            return ExactReal.of(iv.value());
        }
        return ((OclValue.RealValue) v).exactValue();
    }
}
