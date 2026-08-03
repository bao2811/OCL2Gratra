package org.uet.dse.neo4jtgg.experiment;

/** Exhaustive outcome of a concrete two-sided violation-set comparison. */
public enum ViolationOracleStatus {
    EQUIVALENT,
    MISSING_ONLY,
    SPURIOUS_ONLY,
    INCOMPARABLE,
    REFERENCE_ERROR,
    CYPHER_ERROR,
    BOTH_ERROR
}
