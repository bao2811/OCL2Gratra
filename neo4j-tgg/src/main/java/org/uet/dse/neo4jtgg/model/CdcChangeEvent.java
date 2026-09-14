package org.uet.dse.neo4jtgg.model;

import java.util.LinkedHashMap;
import java.util.Map;

public record CdcChangeEvent(
        long transactionId,
        long commitTimeMillis,
        WorkspaceSide side,
        EntityKind entityKind,
        Operation operation,
        String entityId,
        String entityType,
        Map<String, Object> before,
        Map<String, Object> after,
        ChangeSource source) {

    public enum EntityKind {
        OBJECT,
        LINK,
        ATTRIBUTE
    }

    public enum Operation {
        CREATE,
        UPDATE,
        DELETE
    }

    public enum ChangeSource {
        CDC,
        SNAPSHOT_DIFF
    }

    public CdcChangeEvent {
        before = Map.copyOf(new LinkedHashMap<>(before != null ? before : Map.of()));
        after = Map.copyOf(new LinkedHashMap<>(after != null ? after : Map.of()));
    }

    public String summary() {
        return source + " tx=" + transactionId + " " + side.getDisplayName() + " "
                + entityKind + " " + operation + " " + entityType + " `" + entityId + "`";
    }
}
