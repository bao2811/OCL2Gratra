package org.uet.dse.neo4jtgg.experiment;

import java.util.Collection;
import java.util.LinkedHashSet;

public final class StableIdDifferentialChecker {
    public DifferentialResult compare(Collection<String> referenceIds, Collection<String> cypherIds) {
        return DifferentialResult.compare(new LinkedHashSet<>(referenceIds), new LinkedHashSet<>(cypherIds));
    }
}
