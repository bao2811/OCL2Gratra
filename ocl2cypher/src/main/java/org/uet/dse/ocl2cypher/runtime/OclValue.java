package org.uet.dse.ocl2cypher.runtime;

import java.util.Objects;

/**
 * A value of an {@link OclType}. The OCL_val carrier is:
 *
 * <pre>
 *   Dhat_tau = D_tau union { bottom_tau }
 * </pre>
 *
 * All {@code OclValue} instances except {@code BottomValue} denote defined
 * elements of {@code D_tau}; {@code BottomValue} denotes the single typed
 * bottom for that type. There is no value whose type is {@code Bottom}.
 */
public abstract sealed class OclValue
        permits OclValue.BooleanValue,
                OclValue.IntegerValue,
                OclValue.RealValue,
                OclValue.StringValue,
                OclValue.ObjectValue,
                OclValue.CollectionValue,
                OclValue.BottomValue {

    protected final OclType type;

    protected OclValue(OclType type) {
        this.type = Objects.requireNonNull(type, "type");
    }

    public OclType type() {
        return type;
    }

    public boolean isBottom() {
        return this instanceof BottomValue;
    }

    /** Kind tag used by the typed-bottom and equality equations. */
    public static final class BooleanValue extends OclValue {
        public enum Bool3 {
            TRUE,
            FALSE,
            BOTTOM
        }

        private final Bool3 bool;

        public BooleanValue(OclType type, Bool3 bool) {
            super(type);
            if (!type.equals(OclType.BOOLEAN)) {
                throw new IllegalArgumentException("BooleanValue requires Boolean type");
            }
            this.bool = Objects.requireNonNull(bool, "bool3");
            if (bool == Bool3.BOTTOM) {
                throw new IllegalArgumentException(
                        "Boolean bottom must use BottomValue(Boolean)");
            }
        }

        public Bool3 bool() {
            return bool;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof BooleanValue v && v.bool == bool;
        }

        @Override
        public int hashCode() {
            return bool.hashCode();
        }

        @Override
        public String toString() {
            return bool.name();
        }
    }

    public static final class IntegerValue extends OclValue {
        private final java.math.BigInteger magnitude;

        public IntegerValue(java.math.BigInteger v) {
            super(OclType.INTEGER);
            this.magnitude = Objects.requireNonNull(v);
        }

        public java.math.BigInteger value() {
            return magnitude;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof IntegerValue v && v.magnitude.equals(magnitude);
        }

        @Override
        public int hashCode() {
            return magnitude.hashCode();
        }

        @Override
        public String toString() {
            return magnitude.toString();
        }
    }

    public static final class RealValue extends OclValue {
        private final ExactReal magnitude;

        public RealValue(java.math.BigDecimal v) {
            this(ExactReal.of(v));
        }

        public RealValue(ExactReal v) {
            super(OclType.REAL);
            this.magnitude = Objects.requireNonNull(v);
        }

        /** Finite-decimal compatibility boundary; throws rather than rounding.
         * Semantic arithmetic must use exactValue(). */
        public java.math.BigDecimal value() {
            return magnitude.toBigDecimalExact();
        }

        public ExactReal exactValue() { return magnitude; }

        @Override
        public boolean equals(Object o) {
            return o instanceof RealValue v && v.magnitude.compareTo(magnitude) == 0;
        }

        @Override
        public int hashCode() {
            return magnitude.hashCode();
        }

        @Override
        public String toString() {
            return magnitude.toString();
        }
    }

    public static final class StringValue extends OclValue {
        private final String literal;

        public StringValue(String literal) {
            super(OclType.STRING);
            this.literal = Objects.requireNonNull(literal);
        }

        public String value() {
            return literal;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof StringValue v && v.literal.equals(literal);
        }

        @Override
        public int hashCode() {
            return literal.hashCode();
        }

        @Override
        public String toString() {
            return "'" + literal.replace("'", "\\'") + "'";
        }
    }

    public static final class ObjectValue extends OclValue {
        /** Stable model-scoped identity; semantics require it, not the Java reference. */
        private final String stableId;

        public ObjectValue(OclType type, String stableId) {
            super(type);
            if (!type.isClass()) {
                throw new IllegalArgumentException("ObjectValue requires a class type");
            }
            this.stableId = Objects.requireNonNull(stableId, "stableId");
            if (stableId.isEmpty()) {
                throw new IllegalArgumentException("empty stableId");
            }
        }

        public String stableId() {
            return stableId;
        }

        /** Runtime object identity — the OCL object-equality primitive. */
        @Override
        public boolean equals(Object o) {
            return o instanceof ObjectValue v
                    && v.type.equals(type)
                    && v.stableId.equals(stableId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, stableId);
        }

        @Override
        public String toString() {
            return type.className() + "@" + stableId;
        }
    }

    public static abstract sealed class CollectionValue extends OclValue
            permits OclValue.SetValue, OclValue.BagValue {

        protected CollectionValue(OclType type) {
            super(type);
        }

        public abstract boolean isEmpty();

        public abstract int size();

        /**
         * True iff at least one member equals {@code sought} using
         * total OCL equality (so a bottom inside a collection is findable).
         * This is exactly what checkers of carrier-membership need, not Java {@code equals} of the whole value.
         */
        public abstract boolean containsUsingOclEquality(OclValue sought);
    }

    /**
     * Finite extensional set: {@code P_fin(Dhat_sigma)}. Duplication inside the
     * caller is resolved by OCL equality; an element whose value is
     * {@code bottom_sigma} remains an element and does not make the whole
     * collection bottom.
     */
    public static final class SetValue extends CollectionValue {
        /** Extensional members via OCL equality; de-duplicated before construction. */
        private final java.util.List<OclValue> members;

        public SetValue(OclType type, java.util.List<OclValue> members) {
            super(type);
            if (!type.isCollection() || type.kind() != OclType.Kind.SET) {
                throw new IllegalArgumentException("SetValue requires a set type");
            }
            java.util.List<OclValue> dedup = new java.util.ArrayList<>();
            outer:
            for (OclValue incoming : Objects.requireNonNull(members, "members")) {
                for (OclValue existing : dedup) {
                    if (OclEquality.equal(incoming, existing) == OclEquality.BoolKind.TRUE) {
                        continue outer;
                    }
                }
                dedup.add(incoming);
            }
            this.members = java.util.List.copyOf(dedup);
        }

        public java.util.List<OclValue> members() {
            return members;
        }

        @Override
        public boolean isEmpty() {
            return members.isEmpty();
        }

        @Override
        public int size() {
            return members.size();
        }

        @Override
        public boolean containsUsingOclEquality(OclValue sought) {
            for (OclValue m : members) {
                if (OclEquality.equal(m, sought) == OclEquality.BoolKind.TRUE) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof SetValue v) || !v.type.equals(type) || v.members.size() != members.size()) {
                return false;
            }
            for (OclValue m : members) {
                if (!v.containsUsingOclEquality(m)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int hashCode() {
            int h = type.hashCode();
            for (OclValue m : members) {
                h += m.hashCode();
            }
            return h;
        }

        @Override
        public String toString() {
            return "Set" + members;
        }
    }

    /**
     * Finite multiset: Bag is an occurrence-ordered sequence whose effective
     * semantics is defined by multiplicities, not by order. The runtime keeps
     * the raw occurrence list so that {@code Bag{1,1}} remains distinguishable
     * from {@code Bag{1}} and so that {@code count} is occurrence-faithful.
     */
    public static final class BagValue extends CollectionValue {
        private final java.util.List<OclValue> occurrences;

        public BagValue(OclType type, java.util.List<OclValue> occurrences) {
            super(type);
            if (!type.isCollection() || type.kind() != OclType.Kind.BAG) {
                throw new IllegalArgumentException("BagValue requires a bag type");
            }
            this.occurrences = java.util.List.copyOf(
                    Objects.requireNonNull(occurrences, "occurrences"));
        }

        public java.util.List<OclValue> occurrences() {
            return occurrences;
        }

        @Override
        public boolean isEmpty() {
            return occurrences.isEmpty();
        }

        @Override
        public int size() {
            return occurrences.size();
        }

        @Override
        public boolean containsUsingOclEquality(OclValue sought) {
            for (OclValue v : occurrences) {
                if (OclEquality.equal(v, sought) == OclEquality.BoolKind.TRUE) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof BagValue v) || !v.type.equals(type)) {
                return false;
            }
            java.util.Map<OclValue, Integer> a = multiplicity();
            java.util.Map<OclValue, Integer> b = v.multiplicity();
            if (a.size() != b.size()) {
                return false;
            }
            // Total guest bottom-bottom equality forms a single bucket, so bottom
            // occurrences can be compared using representatives rather than Java identity.
            for (java.util.Map.Entry<OclValue, Integer> e : a.entrySet()) {
                if (!b.containsKey(e.getKey()) || !b.get(e.getKey()).equals(e.getValue())) {
                    return false;
                }
            }
            return true;
        }

        private java.util.Map<OclValue, Integer> multiplicity() {
            java.util.Map<OclValue, Integer> m = new java.util.LinkedHashMap<>();
            // Two distinct BottomValue instances of the same element type are
            // equal under total equality but not under Java equals (they carry
            // distinct source identities inside the value). Collapsing their
            // buckets therefore has to be done by total equality, not map-key
            // identity; otherwise Bag{bottom, bottom} would never reach size 2.
            for (OclValue v : occurrences) {
                java.util.Optional<java.util.Map.Entry<OclValue, Integer>> hit =
                        m.entrySet().stream()
                                .filter(e -> OclEquality.equal(e.getKey(), v) == OclEquality.BoolKind.TRUE)
                                .findFirst();
                if (hit.isPresent()) {
                    OclValue bucketKey = hit.get().getKey();
                    m.put(bucketKey, m.get(bucketKey) + 1);
                } else {
                    m.put(v, 1);
                }
            }
            return m;
        }

        @Override
        public int hashCode() {
            int h = type.hashCode();
            for (OclValue v : occurrences) {
                h += 31 * v.hashCode();
            }
            return h;
        }

        @Override
        public String toString() {
            return "Bag" + occurrences;
        }
    }

    /** The single inhabitant of {@code bottom_tau} for the carried type {@code tau}. */
    public static final class BottomValue extends OclValue {

        public BottomValue(OclType type) {
            super(type);
        }

        @Override
        public boolean equals(Object o) {
            // Each OclType family has its own bottom, and a Set bottom is never
            // equal to the bottom of its element type. Plain reference/structural
            // equality of the two BottomValue instances therefore coincides with
            // typed equality only when the two share the same family. A typed
            // bottom also carries a specific OclType instance, so bottom=Integer
            // has a different carrier than bottom=Set(Integer). Two bottom values
            // that happen to wrap distinct OclType instances but represent the
            // same family are already equal because OclType equality itself is
            // structural. Two bottom instances that share a family are identical;
            // two that differ in family are never interchangeable.
            return o instanceof BottomValue v && v.type.equals(type);
        }

        @Override
        public int hashCode() {
            return type.hashCode() ^ 0xB0FF0D;
        }

        @Override
        public String toString() {
            return "Bottom(" + type + ")";
        }
    }
}
