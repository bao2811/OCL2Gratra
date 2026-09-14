package org.uet.dse.neo4jtgg.engine;

/**
 * Represents a conflict detected during incremental synchronization.
 */
public record SyncConflict(
        String objectId,
        String attributeName,
        Object sourceValue,
        Object targetValue,
        String ruleName,
        ConflictResolution suggestedResolution) {

    public enum ConflictResolution {
        KEEP_SOURCE,
        KEEP_TARGET,
        MANUAL
    }

    public String toDisplayText() {
        return "CONFLICT on `" + objectId + "." + attributeName
                + "`: source=" + sourceValue + " vs target=" + targetValue
                + " (rule: " + ruleName + ", suggestion: " + suggestedResolution + ")";
    }
}
