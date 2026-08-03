package org.uet.dse.neo4jtgg.experiment;

/** Safety classification of the object-side reference result for one benchmark case. */
public enum BenchmarkVacuityStatus {
    EMPTY_CONTEXT,
    ALL_PASS,
    ALL_VIOLATE,
    NON_VACUOUS_MIXED,
    NOT_COMPLETED
}
