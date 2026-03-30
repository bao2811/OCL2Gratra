package org.uet.dse.neo4j.mm.object.link;

import java.util.List;

public final class LinkObject {

    private final String id;
    private final String associationClassName;
    private final List<String> participants;

    public LinkObject(String id, String associationClassName, List<String> participants) {
        this.id = id;
        this.associationClassName = associationClassName;
        this.participants = participants;
    }

    public String getId() {
        return id;
    }

    public String getAssociationClassName() {
        return associationClassName;
    }

    public List<String> getParticipants() {
        return participants;
    }
}
