package org.uet.dse.ocl2cypher.diagnostics;

/** Stable rule identifiers referenced by diagnostics and trace records. */
public final class RuleId {

    private RuleId() {
    }

    // Convex hull: every headmost heading [E-...], [N-...], etc. in Rule 00-07.
    public static final String E_DOC = "E-DOC";
    public static final String E_CONTEXT = "E-CONTEXT";
    public static final String E_IF = "E-IF";
    public static final String E_LET = "E-LET";
    public static final String E_VAR = "E-VAR";
    public static final String E_PROPERTY = "E-PROPERTY";
    public static final String E_TYPE_LITERAL = "E-TYPE-LITERAL";
    public static final String N_INVARIANT = "N-INVARIANT";
    public static final String N_NULL_OUTSIDE = "N-NULL-OUTSIDE";
    public static final String N_INVALID_OUTSIDE = "N-INVALID-OUTSIDE";
    public static final String N_ASSOCIATION_CLASS = "N-ASSOC-CLASS";
    public static final String N_RANGE_OUTSIDE = "N-RANGE-OUTSIDE";
    public static final String N_NESTED_COLLECTION = "N-NESTED-COLLECTION";
    public static final String N_R_BAG_LITERAL = "N-R-BAG-LITERAL";
    public static final String N_R_SET_LITERAL = "N-R-SET-LITERAL";
    public static final String T_E_ATOMIC = "T-E-ATOMIC";
    public static final String T_P_MANY = "T-P-MANY";
    public static final String T_Q_VALUE_EXPR = "T-Q-VALUE-EXPR";
    public static final String T_Q_VALUE_PLAN = "T-Q-VALUE-PLAN";
    public static final String T_Q_VIOLATIONS = "T-Q-VIOLATIONS";
    public static final String T_MODEL_BATCH = "T-MODEL-BATCH";
    public static final String T_E_MATERIALIZE_PLAN = "T-E-MATERIALIZE-PLAN";
    public static final String T_P_FROM_COLLECTION = "T-P-FROM-COLLECTION";
    public static final String T_E_BINARY = "T-E-BINARY";
    public static final String T_E_UNARY = "T-E-UNARY";
    public static final String R_E_ATTRIBUTE = "R-E-ATTRIBUTE";
    public static final String R_E_NAVIGATE_ONE = "R-E-NAVIGATE-ONE";
    public static final String R_P_NAVIGATE = "R-P-NAVIGATE";
    public static final String R_P_FILTER = "R-P-FILTER";
    public static final String R_P_COLLECT = "R-P-COLLECT";
    public static final String R_P_DISTINCT = "R-P-DISTINCT";
    public static final String R_E_MATERIALIZE_PLAN = "R-E-MATERIALIZE-PLAN";
    public static final String R_E_BOOLEAN3 = "R-E-BOOLEAN3";
    public static final String R_E_EQUALITY = "R-E-EQUALITY";
    public static final String R_E_IF = "R-E-IF";
    public static final String R_E_LET = "R-E-LET";
    public static final String R_E_COERCE = "R-E-COERCE";
    public static final String R_E_TYPE_TEST = "R-E-TYPE-TEST";
    public static final String R_E_TYPE_CAST = "R-E-TYPE-CAST";
    public static final String R_E_CONSTANT = "R-E-CONSTANT";
    public static final String R_REAL_EXACT_UNSUPPORTED = "R-REAL-EXACT-UNSUPPORTED";
    public static final String R_E_VARIABLE = "R-E-VARIABLE";
    public static final String R_E_PARAMETER = "R-E-PARAMETER";
    public static final String R_E_EXISTS3 = "R-E-EXISTS3";
    public static final String R_E_FORALL3 = "R-E-FORALL3";
    public static final String R_Q_VALUE_SCALAR = "R-Q-VALUE-SCALAR";
    public static final String R_Q_VIOLATIONS = "R-Q-VIOLATIONS";
    public static final String R_E_ASSOCIATION_CLASS_ONE = "R-E-ASSOCIATION-CLASS-ONE";
    public static final String NORMQ = "NORMQ";
    public static final String S_ARTIFACT = "S-ARTIFACT";
    public static final String S_Q = "S-Q";
}
