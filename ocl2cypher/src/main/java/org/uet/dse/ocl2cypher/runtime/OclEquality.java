package org.uet.dse.ocl2cypher.runtime;

/**
 * Total OCL equality used by every collection observer and by the fallback of
 * the graph materialize pass for sets.
 *
 * <p>The defining equation (OCL_val-formal section 4.5):
 *
 * <pre>
 *   tau = sigma                     tau = Set(sigma) / Bag(sigma)
 *   x eq y = T   if x = bottom_tau and y = bottom_tau  (same carrier)
 *            F   if exactly one is bottom
 *            typedPayloadEqual      otherwise
 * </pre>
 *
 * There is no placement that turns a total equality check into a Boolean
 * {@code bottom}.
 */
public final class OclEquality {

    private OclEquality() {
    }

    /**
     * Returns {@link BoolKind#TRUE} iff {@code a} is total-equal to {@code b};
     * {@link BoolKind#BOTTOM} is never returned — this operation is total.
     */
    public static BoolKind equal(OclValue a, OclValue b) {
        if (a.isBottom() && b.isBottom()) {
            // Each OclType family has its own bottom value. The total equality
            // equations describe exactly: bottom_per_type = bottom_per_type is T
            // only inside the same family. A bottom and bottom are both strictly
            // typed bottom, but a Set bottom is never equal to the bottom of the
            // element type. Plain structural equality of the two BottomValue
            // instances already coincides with typed equality only when the two
            // share the same family. Typed equality treats every bottom as a
            // per-family value: two bottom values with the same family are equal;
            // two bottom values that differ in family are not.
            return a.type().equals(b.type()) ? BoolKind.TRUE : BoolKind.FALSE;
        }
        if (a.isBottom() || b.isBottom()) {
            return BoolKind.FALSE;
        }
        if (a instanceof OclValue.CollectionValue && b instanceof OclValue.CollectionValue) {
            return a.equals(b) ? BoolKind.TRUE : BoolKind.FALSE;
        }
        if (a instanceof OclValue.BooleanValue && b instanceof OclValue.BooleanValue) {
            return ((OclValue.BooleanValue) a).bool() == ((OclValue.BooleanValue) b).bool()
                    ? BoolKind.TRUE
                    : BoolKind.FALSE;
        }
        return a.equals(b) ? BoolKind.TRUE : BoolKind.FALSE;
    }

    public enum BoolKind {
        TRUE,
        FALSE,
        BOTTOM
    }
}
