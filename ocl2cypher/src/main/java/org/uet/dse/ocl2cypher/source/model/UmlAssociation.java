package org.uet.dse.ocl2cypher.source.model;

import java.util.List;
import java.util.Objects;

/**
 * A binary UML association declaration. The canonical end order is the model
 * order established by the schema; logical direction (from → to) is the
 * observation layer's concern, not the physical relationship direction.
 */
public final class UmlAssociation {

    private final String key;
    private final String name;
    private final String sourceClassKey;
    private final String sourceRole;
    private final int sourceLower;
    private final int sourceUpper;
    private final String targetClassKey;
    private final String targetRole;
    private final int targetLower;
    private final int targetUpper;
    private final List<String> qualifierNames;
    private final List<UmlQualifier> qualifiers;
    private final boolean toMany;
    private final boolean ordered;
    private final boolean unique;

    /**
     * @param sourceUpper {@code -1} denotes unbounded {@code *}
     * @param targetUpper {@code -1} denotes unbounded {@code *}
     */
    public UmlAssociation(String key,
                          String name,
                          String sourceClassKey,
                          String sourceRole,
                          int sourceLower,
                          int sourceUpper,
                          String targetClassKey,
                          String targetRole,
                          int targetLower,
                          int targetUpper,
                          List<?> qualifierDeclarations,
                          boolean ordered,
                          boolean unique) {
        this.key = Objects.requireNonNull(key, "association key");
        this.name = Objects.requireNonNull(name, "association name");
        this.sourceClassKey = Objects.requireNonNull(sourceClassKey, "source class");
        this.sourceRole = Objects.requireNonNull(sourceRole, "source role");
        this.sourceLower = sourceLower;
        this.sourceUpper = sourceUpper;
        this.targetClassKey = Objects.requireNonNull(targetClassKey, "target class");
        this.targetRole = Objects.requireNonNull(targetRole, "target role");
        this.targetLower = targetLower;
        this.targetUpper = targetUpper;
        Objects.requireNonNull(qualifierDeclarations, "qualifiers");
        java.util.ArrayList<UmlQualifier> typedQualifiers = new java.util.ArrayList<>();
        for (Object declaration : qualifierDeclarations) {
            if (declaration instanceof UmlQualifier qualifier) {
                typedQualifiers.add(qualifier);
            } else if (declaration instanceof String qualifierName) {
                throw new IllegalArgumentException("qualifier '" + qualifierName
                        + "' requires a declared type; use UmlQualifier");
            } else {
                throw new IllegalArgumentException("unsupported qualifier declaration "
                        + declaration);
            }
        }
        this.qualifiers = List.copyOf(typedQualifiers);
        this.qualifierNames = this.qualifiers.stream().map(UmlQualifier::name).toList();
        this.ordered = ordered;
        this.unique = unique;
        this.toMany = targetUpper == -1 || targetUpper > 1;
    }

    public static UmlAssociation binary(String name,
                                        String sourceClass,
                                        String sourceRole,
                                        String targetClass,
                                        String targetRole) {
        return new UmlAssociation(name, name,
                sourceClass, sourceRole, 0, 1,
                targetClass, targetRole, 0, -1,
                List.of(), false, true);
    }

    public static UmlAssociation oneToOne(String name,
                                          String sourceClass,
                                          String sourceRole,
                                          String targetClass,
                                          String targetRole) {
        return new UmlAssociation(name, name,
                sourceClass, sourceRole, 0, 1,
                targetClass, targetRole, 0, 1,
                List.of(), false, true);
    }

    public String key() {
        return key;
    }

    public String name() {
        return name;
    }

    public String sourceClassKey() {
        return sourceClassKey;
    }

    public String sourceRole() {
        return sourceRole;
    }

    public int sourceLower() {
        return sourceLower;
    }

    public int sourceUpper() {
        return sourceUpper;
    }

    public String targetClassKey() {
        return targetClassKey;
    }

    public int targetLower() {
        return targetLower;
    }

    public int targetUpper() {
        return targetUpper;
    }

    public String targetRole() {
        return targetRole;
    }

    /** True when navigation from the SOURCE end yields a multi-valued target. */
    public boolean isToMany() {
        return toMany;
    }

    public boolean isOrdered() {
        return ordered;
    }

    public boolean isUnique() {
        return unique;
    }

    public List<String> qualifierNames() {
        return qualifierNames;
    }

    public List<UmlQualifier> qualifiers() {
        return qualifiers;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof UmlAssociation a && a.key.equals(key);
    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }

    @Override
    public String toString() {
        String mult = targetUpper == -1 ? "*" : String.valueOf(targetUpper);
        return name + " : " + sourceClassKey + "[" + sourceRole + "] --["
                + targetRole + ":" + targetClassKey + "[" + mult + "]]";
    }
}
