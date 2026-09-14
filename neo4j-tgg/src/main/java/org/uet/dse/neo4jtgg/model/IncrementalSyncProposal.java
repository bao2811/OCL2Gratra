package org.uet.dse.neo4jtgg.model;

import org.uet.dse.neo4jtgg.engine.SyncConflict;
import org.uet.dse.neo4jtgg.engine.TransformationDirection;

import java.util.ArrayList;
import java.util.List;

public record IncrementalSyncProposal(
        TransformationDirection direction,
        WorkspaceSide drivingSide,
        NormalizedChangeSet changeSet,
        ModelDelta delta,
        ImpactAnalysisResult impactAnalysis,
        WorkspaceMutationBatch mutationBatch,
        List<SyncConflict> conflicts,
        List<String> warnings,
        boolean requiresManualTransform,
        boolean blocked,
        long createdAtMillis) {

    public IncrementalSyncProposal {
        conflicts = List.copyOf(new ArrayList<>(conflicts != null ? conflicts : List.of()));
        warnings = List.copyOf(new ArrayList<>(warnings != null ? warnings : List.of()));
    }

    public IncrementalSyncStatus status() {
        if (blocked) {
            return IncrementalSyncStatus.BLOCKED;
        }
        if (requiresManualTransform) {
            return IncrementalSyncStatus.MANUAL_TRANSFORM_REQUIRED;
        }
        return IncrementalSyncStatus.PENDING;
    }

    public String summary() {
        StringBuilder sb = new StringBuilder();
        sb.append("Incremental proposal [").append(status().getLabel()).append("] ");
        sb.append(direction).append(" from ").append(drivingSide.getDisplayName());
        sb.append(" | changes=").append(delta != null ? delta.totalChanges() : 0);
        sb.append(", conflicts=").append(conflicts.size());
        if (mutationBatch != null) {
            sb.append(", upserts=").append(mutationBatch.getUpsertObjects().size() + mutationBatch.getUpsertLinks().size());
            sb.append(", deletes=").append(mutationBatch.getDeleteObjectIds().size() + mutationBatch.getDeleteLinks().size());
        }
        return sb.toString();
    }

    public String toDisplayText() {
        StringBuilder sb = new StringBuilder(summary());
        if (delta != null) {
            sb.append("\n\n").append(delta.toDisplayText());
        }
        if (changeSet != null) {
            sb.append("\n\nDetected Changes\n").append(changeSet.toDisplayText());
        }
        if (impactAnalysis != null) {
            sb.append("\n\nAffected Region\n").append(impactAnalysis.toDisplayText());
        }
        if (mutationBatch != null) {
            sb.append("\n\nMutation batch\n").append(mutationBatch.toDisplayText());
        }
        if (!conflicts.isEmpty()) {
            sb.append("\n\nConflicts");
            for (SyncConflict conflict : conflicts) {
                sb.append("\n- ").append(conflict.toDisplayText());
            }
        }
        if (!warnings.isEmpty()) {
            sb.append("\n\nWarnings");
            for (String warning : warnings) {
                sb.append("\n- ").append(warning);
            }
        }
        return sb.toString();
    }
}
