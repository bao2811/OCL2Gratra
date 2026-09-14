package org.uet.dse.neo4jtgg.model;

import java.util.ArrayList;
import java.util.List;

public record NormalizedChangeSet(
        WorkspaceSide side,
        ChangeSourceKind sourceKind,
        ModelDelta delta,
        List<CdcChangeEvent> events,
        long detectedAtMillis) {

    public enum ChangeSourceKind {
        CDC,
        SNAPSHOT_DIFF
    }

    public NormalizedChangeSet {
        events = List.copyOf(new ArrayList<>(events != null ? events : List.of()));
    }

    public int totalEvents() {
        return events.size();
    }

    public String toDisplayText() {
        StringBuilder sb = new StringBuilder();
        sb.append("Change source: ").append(sourceKind);
        sb.append("\nSide: ").append(side.getDisplayName());
        sb.append("\nEvents: ").append(events.size());
        for (CdcChangeEvent event : events) {
            sb.append("\n- ").append(event.summary());
        }
        if (delta != null) {
            sb.append("\n\n").append(delta.toDisplayText());
        }
        return sb.toString();
    }
}
