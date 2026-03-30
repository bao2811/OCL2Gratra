package org.uet.dse.neo4j.mm.object.common;

public record ObjectReference(
        String targetId,
        int index
) {}
