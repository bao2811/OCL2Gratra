package org.uet.dse.ocl2cypher.source.omg;

/** Closed operation vocabulary carried by resolved OMG-AS OperationCallExp nodes. */
public enum OclOperation {
    BOOLEAN_NOT,
    BOOLEAN_AND,
    BOOLEAN_OR,
    BOOLEAN_XOR,
    BOOLEAN_IMPLIES,
    VALUE_EQUAL,
    VALUE_NOT_EQUAL,
    LESS_THAN,
    LESS_THAN_OR_EQUAL,
    GREATER_THAN,
    GREATER_THAN_OR_EQUAL,
    NUMERIC_ADD,
    NUMERIC_SUBTRACT,
    NUMERIC_MULTIPLY,
    REAL_DIVIDE,
    INTEGER_DIVIDE,
    INTEGER_MOD,
    NUMERIC_NEGATE,
    NUMERIC_ABS,
    REAL_FLOOR,
    REAL_ROUND,
    NUMERIC_MAX,
    NUMERIC_MIN,
    COLLECTION_SIZE,
    COLLECTION_IS_EMPTY,
    COLLECTION_NOT_EMPTY,
    COLLECTION_SUM,
    COLLECTION_COUNT,
    COLLECTION_INCLUDES,
    COLLECTION_EXCLUDES,
    COLLECTION_INCLUDES_ALL,
    COLLECTION_EXCLUDES_ALL,
    SET_UNION,
    SET_INTERSECTION,
    ALL_INSTANCES,
    OCL_IS_TYPE_OF,
    OCL_IS_KIND_OF,
    OCL_AS_TYPE;

    public static OclOperation resolved(String name) {
        return valueOf(name);
    }
}
