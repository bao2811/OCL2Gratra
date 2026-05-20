package org.uet.dse.neo4jtgg.model;

public enum WorkspaceSide {
    SOURCE("Source"),
    CORRESPONDENCE("Correspondence"),
    TARGET("Target");

    private final String displayName;

    WorkspaceSide(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
