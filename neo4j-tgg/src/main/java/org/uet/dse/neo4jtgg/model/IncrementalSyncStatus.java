package org.uet.dse.neo4jtgg.model;

public enum IncrementalSyncStatus {
    IDLE("idle"),
    PENDING("pending"),
    APPLIED("applied"),
    DISCARDED("discarded"),
    BLOCKED("blocked"),
    MANUAL_TRANSFORM_REQUIRED("manual-transform-required"),
    BASELINE_REFRESHED("baseline-refreshed"),
    FAILED("failed");

    private final String label;

    IncrementalSyncStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
