package org.uet.dse.ocl2cypher.runtime;

/**
 * Coercion kinds produced by the typed frontend. The target language has exactly
 * the three kinds admitted by research/Rule 02 and Rule 05:
 *
 * <pre>
 *   INTEGER_TO_REAL             Integer  →  Real
 *   CLASS_UPCAST                Class(D) → Class(C)  when D ⊑* C
 *   COLLECTION_ELEMENT_COERCION K(sigma) → K(sigma')  via the element mapping
 * </pre>
 *
 * There is no Set/Bag coercion; conformity of the container relies on element
 * conformity.
 */
public final class TypeConformance {

    private TypeConformance() {
    }

    public enum CoercionKind {
        INTEGER_TO_REAL,
        CLASS_UPCAST,
        COLLECTION_ELEMENT_COERCION
    }

    /**
     * OCL_val's conformance judgment conforms(tau_s, tau_t). This is the runtime
     * hook for the typing side of any lowered coercion node; a value whose type
     * already conforms evaluates to itself, otherwise the compiler would have
     * refused the source program.
     */
    public static boolean conforms(OclType source, OclType target, java.util.function.BiPredicate<String, String> subclassOf) {
        if (source.equals(target)) {
            return true;
        }
        if (source.equals(OclType.INTEGER) && target.equals(OclType.REAL)) {
            return true;
        }
        if (source.isClass() && target.isClass() && subclassOf.test(source.className(), target.className())) {
            return true;
        }
        if (source.isCollection() && target.isCollection() && source.kind() == target.kind()) {
            return conforms(source.elementType(), target.elementType(), subclassOf);
        }
        return false;
    }
}
