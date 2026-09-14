package org.uet.dse.ocl2cypher.runtime;

/**
 * Kleene three-valued Boolean algebra used by {@code Boolean3} in OCL_val.
 *
 * <p>The table is the one fixed by {@code research/OCLscope/OCL_val-formal.md}
 * section 4.4 and reused by the tagged {@code Bool3} realization in Rule 06.
 * There is no interning of native Cypher null here; a bottom is a typed value
 * and is tested before coercion into any strict numeric path.
 */
public final class Boolean3 {

    private Boolean3() {
    }

    public static final OclValue.BooleanValue TRUE =
            new OclValue.BooleanValue(OclType.BOOLEAN, OclValue.BooleanValue.Bool3.TRUE);

    public static final OclValue.BooleanValue FALSE =
            new OclValue.BooleanValue(OclType.BOOLEAN, OclValue.BooleanValue.Bool3.FALSE);

    /** The unique Boolean bottom carrier; it is not a second Boolean literal state. */
    public static final OclValue.BottomValue BOTTOM =
            new OclValue.BottomValue(OclType.BOOLEAN);

    public static OclValue of(OclValue.BooleanValue.Bool3 b) {
        return switch (b) {
            case TRUE -> TRUE;
            case FALSE -> FALSE;
            case BOTTOM -> BOTTOM;
        };
    }

    public static OclValue.BooleanValue.Bool3 boolOf(OclValue v) {
        if (v instanceof OclValue.BottomValue && OclType.BOOLEAN.equals(v.type())) {
            return OclValue.BooleanValue.Bool3.BOTTOM;
        }
        if (v instanceof OclValue.BooleanValue bv) {
            return bv.bool();
        }
        throw new IllegalArgumentException("not a Boolean value: " + v);
    }

    public static OclValue not(OclValue a) {
        return switch (boolOf(a)) {
            case TRUE -> FALSE;
            case FALSE -> TRUE;
            case BOTTOM -> BOTTOM;
        };
    }

    /** {@code |a|^ = T or F or B} for the tagged predicate projection. */
    public static OclValue.BooleanValue.Bool3 bool3Of(OclValue v) {
        return boolOf(v);
    }

    public static OclValue and(OclValue a, OclValue b) {
        // F dominates, B is neutral against F but dominates T.
        if (boolOf(a) == OclValue.BooleanValue.Bool3.FALSE
                || boolOf(b) == OclValue.BooleanValue.Bool3.FALSE) {
            return FALSE;
        }
        if (boolOf(a) == OclValue.BooleanValue.Bool3.BOTTOM
                || boolOf(b) == OclValue.BooleanValue.Bool3.BOTTOM) {
            return BOTTOM;
        }
        return TRUE;
    }

    public static OclValue or(OclValue a, OclValue b) {
        if (boolOf(a) == OclValue.BooleanValue.Bool3.TRUE
                || boolOf(b) == OclValue.BooleanValue.Bool3.TRUE) {
            return TRUE;
        }
        if (boolOf(a) == OclValue.BooleanValue.Bool3.BOTTOM
                || boolOf(b) == OclValue.BooleanValue.Bool3.BOTTOM) {
            return BOTTOM;
        }
        return FALSE;
    }

    public static OclValue xor(OclValue a, OclValue b) {
        if (boolOf(a) == OclValue.BooleanValue.Bool3.BOTTOM
                || boolOf(b) == OclValue.BooleanValue.Bool3.BOTTOM) {
            return BOTTOM;
        }
        if (boolOf(a) == boolOf(b)) {
            return FALSE;
        }
        return TRUE;
    }

    public static OclValue implies(OclValue a, OclValue b) {
        // a implies b = not a or b
        return or(not(a), b);
    }

    /** Validate the table explicitly for tests (a,b → and,or,xor,implies). */
    public static boolean tableCompleteAndSound() {
        var values = OclValue.BooleanValue.Bool3.values();
        for (var a : values) {
            for (var b : values) {
                OclValue av = of(a);
                OclValue bv = of(b);
                if (and(av, bv) == null || or(av, bv) == null
                        || xor(av, bv) == null || implies(av, bv) == null) {
                    return false;
                }
            }
        }
        return true;
    }
}
