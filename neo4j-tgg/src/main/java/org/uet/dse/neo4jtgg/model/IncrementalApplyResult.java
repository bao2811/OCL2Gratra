package org.uet.dse.neo4jtgg.model;

public record IncrementalApplyResult(
        IncrementalSyncStatus status,
        String message,
        long timestampMillis) {

    public static IncrementalApplyResult of(IncrementalSyncStatus status, String message) {
        return new IncrementalApplyResult(status, message, System.currentTimeMillis());
    }
}
