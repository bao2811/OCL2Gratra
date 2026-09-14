package org.uet.dse.ocl2cypher.source.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.uet.dse.ocl2cypher.runtime.OclEquality;
import org.uet.dse.ocl2cypher.runtime.OclType;
import org.uet.dse.ocl2cypher.runtime.OclValue;

/** Typed qualifier declaration with an optional complete finite-domain witness. */
public record UmlQualifier(String name, OclType declaredType, List<OclValue> finiteDomain,
                           boolean finiteDomainComplete) {

    public UmlQualifier(String name, OclType declaredType, List<OclValue> finiteDomain) {
        this(name, declaredType, finiteDomain, true);
    }

    public UmlQualifier {
        Objects.requireNonNull(name, "qualifier name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("qualifier name must not be blank");
        }
        Objects.requireNonNull(declaredType, "qualifier type");
        if (!declaredType.isAtomic()) {
            throw new IllegalArgumentException("qualifier type must be atomic");
        }
        finiteDomain = List.copyOf(Objects.requireNonNull(finiteDomain, "finite domain"));
        List<OclValue> seen = new ArrayList<>();
        for (OclValue value : finiteDomain) {
            if (value == null || value.isBottom() || !declaredType.equals(value.type())) {
                throw new IllegalArgumentException(
                        "finite qualifier-domain values must be defined and have type "
                                + declaredType);
            }
            if (seen.stream().anyMatch(existing ->
                    OclEquality.equal(existing, value) == OclEquality.BoolKind.TRUE)) {
                throw new IllegalArgumentException("duplicate value in qualifier domain " + name);
            }
            seen.add(value);
        }
    }

    /** A typed declaration whose complete finite domain is not asserted. */
    public static UmlQualifier typed(String name, OclType type) {
        return new UmlQualifier(name, type, List.of(), false);
    }
}
