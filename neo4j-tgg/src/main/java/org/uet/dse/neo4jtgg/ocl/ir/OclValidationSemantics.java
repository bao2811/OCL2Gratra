package org.uet.dse.neo4jtgg.ocl.ir;

/**
 * Validation truth policy used by generated invariant queries.
 *
 * <p>This class captures the validation semantics used in the preservation
 * theorem: an invariant is satisfied only when its predicate evaluates to
 * {@code true}; {@code false}, {@code null}/undefined, and invalid-like values
 * are treated as violations. Cypher represents undefined predicate values as
 * {@code null}, so the generated violation predicate is:</p>
 *
 * <pre>
 * NOT coalesce(predicate, false)
 * </pre>
 */
public final class OclValidationSemantics {
    private OclValidationSemantics() {
    }

    /**
     * Coerces a Cypher predicate to validation truth. Only true remains true;
     * null/undefined becomes false.
     */
    public static String validationTruth(String predicateCypher) {
        return "coalesce(" + predicateCypher + ", false)";
    }

    /**
     * Validation-oriented Boolean negation. Undefined/null is first coerced to
     * false, matching the reference evaluator used by dual-check tests.
     */
    public static String not(String predicateCypher) {
        return "(NOT " + validationTruth(predicateCypher) + ")";
    }

    /**
     * Validation-oriented conjunction. Only true is true; undefined/null is
     * treated as false before applying the Boolean connective.
     */
    public static String and(String leftCypher, String rightCypher) {
        return "(" + validationTruth(leftCypher) + " AND " + validationTruth(rightCypher) + ")";
    }

    /**
     * Validation-oriented disjunction. Only true is true; undefined/null is
     * treated as false before applying the Boolean connective.
     */
    public static String or(String leftCypher, String rightCypher) {
        return "(" + validationTruth(leftCypher) + " OR " + validationTruth(rightCypher) + ")";
    }

    /** Exclusive disjunction after applying validation truth to both operands. */
    public static String xor(String leftCypher, String rightCypher) {
        String left = validationTruth(leftCypher);
        String right = validationTruth(rightCypher);
        return "((" + left + " AND NOT " + right + ") OR (NOT " + left + " AND " + right + "))";
    }

    /**
     * Validation-oriented implication: false/undefined antecedents make the
     * implication true, consistent with {@code !bool(a) || bool(b)}.
     */
    public static String implies(String leftCypher, String rightCypher) {
        return "(" + not(leftCypher) + " OR " + validationTruth(rightCypher) + ")";
    }

    /**
     * Produces the Cypher condition used by invariant validation queries:
     * return the object when the predicate is false or undefined.
     */
    public static String violationPredicate(String predicateCypher) {
        return "NOT " + validationTruth(predicateCypher);
    }
}
