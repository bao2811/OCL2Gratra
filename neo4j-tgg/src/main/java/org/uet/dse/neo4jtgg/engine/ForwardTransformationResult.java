package org.uet.dse.neo4jtgg.engine;

import org.uet.dse.neo4jtgg.model.ImportBatch;

import java.util.List;
import java.util.Map;

public record ForwardTransformationResult(
        ImportBatch targetBatch,
        List<CorrCreation> corrCreations
) {
    public List<TracedCorrRecord> correspondenceRecords() {
        return corrCreations.stream()
                .map(CorrCreation::record)
                .toList();
    }

    public boolean hasCorrespondences() {
        return !corrCreations.isEmpty();
    }

    public record CorrCreation(
            TracedCorrRecord record,
            Map<String, String> sourceBindings,
            Map<String, String> targetBindings
    ) {
    }
}
