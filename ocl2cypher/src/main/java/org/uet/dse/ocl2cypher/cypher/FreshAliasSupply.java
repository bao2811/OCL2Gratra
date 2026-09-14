package org.uet.dse.ocl2cypher.cypher;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Per-realization, non-wrapping supply for internal Cypher identifiers.
 *
 * <p>The counter is unbounded and every returned candidate is also recorded,
 * so freshness does not depend on machine-integer wraparound or on bases being
 * pairwise different. Source-level declaration names never enter this supply.
 */
final class FreshAliasSupply {
    private BigInteger next;
    private final Set<String> allocated = new HashSet<>();

    FreshAliasSupply() {
        this(BigInteger.ZERO);
    }

    FreshAliasSupply(BigInteger initial) {
        next = Objects.requireNonNull(initial, "initial counter");
        if (initial.signum() < 0) {
            throw new IllegalArgumentException("initial counter must be non-negative");
        }
    }

    String fresh(String base) {
        requireIdentifier(base, "alias base");
        while (true) {
            String candidate = base + "_" + next;
            next = next.add(BigInteger.ONE);
            if (allocated.add(candidate)) {
                return candidate;
            }
        }
    }

    void reserve(String name) {
        requireIdentifier(name, "reserved alias");
        if (!allocated.add(name)) {
            throw new IllegalArgumentException("alias already allocated: " + name);
        }
    }

    private static void requireIdentifier(String value, String label) {
        Objects.requireNonNull(value, label);
        if (!value.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException(label + " is not a plain Cypher identifier: "
                    + value);
        }
    }
}
