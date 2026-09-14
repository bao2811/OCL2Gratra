package org.uet.dse.ocl2cypher.runtime;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The {@code OCL_val} static type lattice.
 *
 * <p>Grammar fixed by {@code research/OCLscope/OCL_val-formal.md} section 3:
 * <pre>
 *   sigma ::= Boolean | Integer | Real | String | Class(C)
 *   tau   ::= sigma | Set(sigma) | Bag(sigma)
 * </pre>
 * There are no nested collections, no Sequence/OrderedSet, and no bottom
 * type: {@code bottom} is a value of a type, never a type constructor.
 *
 * <p>Identity-interned: structurally equal types are reference-identical
 * ({@code ==}). This satisfies Rule/02 §3 and proof N-5 H2.
 */
public final class OclType {

    public enum Kind {
        BOOLEAN,
        INTEGER,
        REAL,
        STRING,
        CLASS,
        SET,
        BAG
    }

    private final Kind kind;
    private final String className;
    private final OclType elementType;

    private OclType(Kind kind, String className, OclType elementType) {
        this.kind = kind;
        this.className = className;
        this.elementType = elementType;
    }

    public static final OclType BOOLEAN = new OclType(Kind.BOOLEAN, null, null);
    public static final OclType INTEGER = new OclType(Kind.INTEGER, null, null);
    public static final OclType REAL = new OclType(Kind.REAL, null, null);
    public static final OclType STRING = new OclType(Kind.STRING, null, null);

    private static final ConcurrentHashMap<String, OclType> CLASS_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<OclType, OclType> SET_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<OclType, OclType> BAG_CACHE = new ConcurrentHashMap<>();

    public static OclType clazz(String name) {
        return CLASS_CACHE.computeIfAbsent(
                Objects.requireNonNull(name, "class name"),
                n -> new OclType(Kind.CLASS, n, null));
    }

    public static OclType set(OclType element) {
        requireAtomic(element);
        return SET_CACHE.computeIfAbsent(element,
                e -> new OclType(Kind.SET, null, e));
    }

    public static OclType bag(OclType element) {
        requireAtomic(element);
        return BAG_CACHE.computeIfAbsent(element,
                e -> new OclType(Kind.BAG, null, e));
    }

    private static void requireAtomic(OclType element) {
        if (element == null || element.isCollection()) {
            throw new IllegalArgumentException(
                    "nested collections are outside OCL_val: element type must be atomic");
        }
    }

    public Kind kind() {
        return kind;
    }

    public boolean isBoolean() {
        return kind == Kind.BOOLEAN;
    }

    public boolean isInteger() {
        return kind == Kind.INTEGER;
    }

    public boolean isReal() {
        return kind == Kind.REAL;
    }

    /** {@code Integer or Real}: the OCL_val numeric join domain. */
    public boolean isNumeric() {
        return kind == Kind.INTEGER || kind == Kind.REAL;
    }

    public boolean isString() {
        return kind == Kind.STRING;
    }

    public boolean isAtomic() {
        return kind != Kind.SET && kind != Kind.BAG;
    }

    public boolean isCollection() {
        return kind == Kind.SET || kind == Kind.BAG;
    }

    public boolean isClass() {
        return kind == Kind.CLASS;
    }

    public String className() {
        if (kind != Kind.CLASS) {
            throw new IllegalStateException("not a class type: " + this);
        }
        return className;
    }

    public OclType elementType() {
        if (!isCollection()) {
            throw new IllegalStateException("not a collection type: " + this);
        }
        return elementType;
    }

    /** Least common type per OCL_val-formal section 3; null when no join exists. */
    public static OclType join(OclType a, OclType b, java.util.function.BiPredicate<String, String> subclassOf) {
        if (a.equals(b)) {
            return a;
        }
        if (a.isNumeric() && b.isNumeric()) {
            return REAL;
        }
        if (a.isClass() && b.isClass()) {
            if (subclassOf.test(a.className, b.className)) {
                return b;
            }
            if (subclassOf.test(b.className, a.className)) {
                return a;
            }
        }
        if (a.kind == b.kind && a.isCollection()) {
            OclType el = join(a.elementType, b.elementType, subclassOf);
            if (el == null) {
                return null;
            }
            return a.kind == Kind.SET ? set(el) : bag(el);
        }
        return null;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof OclType t
                && t.kind == kind
                && Objects.equals(t.className, className)
                && Objects.equals(t.elementType, elementType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, className, elementType);
    }

    @Override
    public String toString() {
        return switch (kind) {
            case BOOLEAN -> "Boolean";
            case INTEGER -> "Integer";
            case REAL -> "Real";
            case STRING -> "String";
            case CLASS -> "Class(" + className + ")";
            case SET -> "Set(" + elementType + ")";
            case BAG -> "Bag(" + elementType + ")";
        };
    }
}
