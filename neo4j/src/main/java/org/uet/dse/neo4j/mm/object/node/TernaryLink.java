package org.uet.dse.neo4j.mm.object.node;

import java.util.List;

public final class TernaryLink {

    private final String associationName;
    private final List<Participant> participants;

    public TernaryLink(String associationName, List<Participant> participants) {
        this.associationName = associationName;
        this.participants = participants;
    }

    public String getAssociationName() {
        return associationName;
    }

    public List<Participant> getParticipants() {
        return participants;
    }

    public record Participant(String objectId, int index) {}
}
