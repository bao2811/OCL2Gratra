package org.uet.dse.neo4jtgg.experiment;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Immutable evidence produced by one concrete differential correctness check. */
public record ViolationOracleResult(
        String caseId,
        Set<String> contextIds,
        DifferentialResult differential,
        ViolationOracleStatus status,
        String referenceError,
        String cypherError) {

    public ViolationOracleResult {
        if (caseId == null || caseId.isBlank()) {
            throw new IllegalArgumentException("caseId must not be blank");
        }
        contextIds = immutableSet(contextIds);
        differential = Objects.requireNonNull(differential, "differential");
        status = Objects.requireNonNull(status, "status");
        referenceError = referenceError == null ? "" : referenceError;
        cypherError = cypherError == null ? "" : cypherError;
    }

    public Set<String> referenceViolationIds() {
        return differential.referenceIds();
    }

    public Set<String> cypherViolationIds() {
        return differential.cypherIds();
    }

    /** Violations present in the object-side reference but absent from Cypher. */
    public Set<String> missingIds() {
        return differential.referenceOnlyIds();
    }

    /** IDs returned by Cypher that are not violations according to the reference. */
    public Set<String> spuriousIds() {
        return differential.cypherOnlyIds();
    }

    public boolean completed() {
        return status != ViolationOracleStatus.REFERENCE_ERROR
                && status != ViolationOracleStatus.CYPHER_ERROR
                && status != ViolationOracleStatus.BOTH_ERROR;
    }

    public boolean equivalent() {
        return status == ViolationOracleStatus.EQUIVALENT;
    }

    public BenchmarkVacuityStatus vacuityStatus() {
        if (!completed()) {
            return BenchmarkVacuityStatus.NOT_COMPLETED;
        }
        if (contextIds.isEmpty()) {
            return BenchmarkVacuityStatus.EMPTY_CONTEXT;
        }
        if (referenceViolationIds().isEmpty()) {
            return BenchmarkVacuityStatus.ALL_PASS;
        }
        if (referenceViolationIds().equals(contextIds)) {
            return BenchmarkVacuityStatus.ALL_VIOLATE;
        }
        return BenchmarkVacuityStatus.NON_VACUOUS_MIXED;
    }

    /** Universe suitable for metrics, including any out-of-context IDs returned by a faulty query. */
    public Set<String> metricUniverseIds() {
        LinkedHashSet<String> result = new LinkedHashSet<>(contextIds);
        result.addAll(referenceViolationIds());
        result.addAll(cypherViolationIds());
        return immutableSet(result);
    }

    public String render() {
        return "case=" + caseId
                + " status=" + status
                + " context=" + contextIds
                + " reference=" + referenceViolationIds()
                + " cypher=" + cypherViolationIds()
                + " missing=" + missingIds()
                + " spurious=" + spuriousIds()
                + " vacuity=" + vacuityStatus()
                + (referenceError.isEmpty() ? "" : " referenceError=" + referenceError)
                + (cypherError.isEmpty() ? "" : " cypherError=" + cypherError);
    }

    private static Set<String> immutableSet(Set<String> values) {
        Objects.requireNonNull(values, "values");
        return Collections.unmodifiableSet(new LinkedHashSet<>(values));
    }
}
