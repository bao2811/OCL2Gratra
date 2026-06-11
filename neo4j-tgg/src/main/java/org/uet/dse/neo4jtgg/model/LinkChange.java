package org.uet.dse.neo4jtgg.model;

import java.util.List;

/**
 * Represents a single link-level change detected between two snapshots.
 */
public record LinkChange(
        String associationName,
        List<String> participants) {

    public LinkChange {
        participants = List.copyOf(participants != null ? participants : List.of());
    }

    public static LinkChange of(String associationName, List<String> participants) {
        return new LinkChange(associationName, participants);
    }

    public String identity() {
        return associationName + "_" + String.join("_", participants);
    }

    public String toDisplayText() {
        return associationName + " " + participants;
    }
}
