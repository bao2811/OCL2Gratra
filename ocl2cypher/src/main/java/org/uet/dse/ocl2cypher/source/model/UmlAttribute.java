package org.uet.dse.ocl2cypher.source.model;

import java.util.Objects;
import org.uet.dse.ocl2cypher.runtime.OclType;

/**
 * A scalar attribute declaration owned by one class.
 *
 * <p>The current OCL_val profile observes scalar attributes only; a
 * multi-valued UML attribute is rejected before any graph observer is built
 * (Rule 03 {@code MM-OBS-ATTRIBUTE} / {@code MM_UNSUPPORTED_VALUE_TYPE}).
 */
public final class UmlAttribute {

    private final String key;
    private final String name;
    private final String ownerClassKey;
    private final OclType declaredType;

    public UmlAttribute(String key, String name, String ownerClassKey, OclType declaredType) {
        this.key = Objects.requireNonNull(key, "attribute key");
        this.name = Objects.requireNonNull(name, "attribute name");
        this.ownerClassKey = Objects.requireNonNull(ownerClassKey, "owner class key");
        this.declaredType = Objects.requireNonNull(declaredType, "declared type");
        if (declaredType.isCollection()) {
            throw new IllegalArgumentException(
                    "OCL_val observes scalar attributes only: " + name + " : " + declaredType);
        }
    }

    public static UmlAttribute of(String ownerClass, String name, OclType type) {
        return new UmlAttribute(ownerClass + "::" + name, name, ownerClass, type);
    }

    public String key() {
        return key;
    }

    public String name() {
        return name;
    }

    public String ownerClassKey() {
        return ownerClassKey;
    }

    public OclType declaredType() {
        return declaredType;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof UmlAttribute a && a.key.equals(key);
    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }

    @Override
    public String toString() {
        return key + " : " + declaredType;
    }
}
