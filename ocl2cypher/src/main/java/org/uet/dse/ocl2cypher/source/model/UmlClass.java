package org.uet.dse.ocl2cypher.source.model;

import java.util.List;
import java.util.Objects;

/**
 * A UML class declaration inside one schema model {@code SM}.
 *
 * <p>Identity is the stable {@code umlElementKey}; the qualified name is
 * observation metadata used for diagnostics and for canonical type names. This
 * matches {@code ResolvedClass} in Typed-Core-OCL-IR.emf, where declaration
 * identity — not the surface name — decides equality.
 */
public final class UmlClass {

    private final String key;
    private final String qualifiedName;
    private final boolean isAbstract;
    private final boolean associationClass;
    private final List<String> directSuperclassKeys;

    public UmlClass(String key,
                    String qualifiedName,
                    boolean isAbstract,
                    boolean associationClass,
                    List<String> directSuperclassKeys) {
        this.key = Objects.requireNonNull(key, "class key");
        this.qualifiedName = Objects.requireNonNull(qualifiedName, "qualified name");
        this.isAbstract = isAbstract;
        this.associationClass = associationClass;
        this.directSuperclassKeys = List.copyOf(
                Objects.requireNonNull(directSuperclassKeys, "superclasses"));
    }

    public static UmlClass of(String name) {
        return new UmlClass(name, name, false, false, List.of());
    }

    public static UmlClass of(String name, String... superKeys) {
        return new UmlClass(name, name, false, false, List.of(superKeys));
    }

    public String key() {
        return key;
    }

    public String qualifiedName() {
        return qualifiedName;
    }

    public boolean isAbstract() {
        return isAbstract;
    }

    public boolean isAssociationClass() {
        return associationClass;
    }

    public List<String> directSuperclassKeys() {
        return directSuperclassKeys;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof UmlClass c && c.key.equals(key);
    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }

    @Override
    public String toString() {
        return qualifiedName;
    }
}
