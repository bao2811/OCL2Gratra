package org.uet.dse.neo4jtgg.experiment;

import java.util.LinkedHashSet;
import java.util.Set;

public record DifferentialResult(Set<String> referenceIds,
                                 Set<String> cypherIds,
                                 Set<String> referenceOnlyIds,
                                 Set<String> cypherOnlyIds) {
    public DifferentialResult {
        referenceIds = Set.copyOf(referenceIds);
        cypherIds = Set.copyOf(cypherIds);
        referenceOnlyIds = Set.copyOf(referenceOnlyIds);
        cypherOnlyIds = Set.copyOf(cypherOnlyIds);
    }

    public static DifferentialResult compare(Set<String> referenceIds, Set<String> cypherIds) {
        Set<String> referenceOnly = new LinkedHashSet<>(referenceIds);
        referenceOnly.removeAll(cypherIds);
        Set<String> cypherOnly = new LinkedHashSet<>(cypherIds);
        cypherOnly.removeAll(referenceIds);
        return new DifferentialResult(referenceIds, cypherIds, referenceOnly, cypherOnly);
    }

    public boolean equalIds() {
        return referenceOnlyIds.isEmpty() && cypherOnlyIds.isEmpty();
    }
}
