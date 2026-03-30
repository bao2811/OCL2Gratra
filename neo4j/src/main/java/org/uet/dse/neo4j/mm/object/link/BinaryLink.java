package org.uet.dse.neo4j.mm.object.link;

public final class BinaryLink {

    private final String associationName;
    private final String sourceId;
    private final String targetId;
    private final String sourceRole;
    private final String targetRole;

    public BinaryLink(
            String associationName,
            String sourceId,
            String targetId,
            String sourceRole,
            String targetRole
    ) {
        this.associationName = associationName;
        this.sourceId = sourceId;
        this.targetId = targetId;
        this.sourceRole = sourceRole;
        this.targetRole = targetRole;
    }

    public String getAssociationName() {
        return associationName;
    }

    public String getSourceId() {
        return sourceId;
    }

    public String getTargetId() {
        return targetId;
    }

    public String getSourceRole() {
        return sourceRole;
    }

    public String getTargetRole() {
        return targetRole;
    }
}
