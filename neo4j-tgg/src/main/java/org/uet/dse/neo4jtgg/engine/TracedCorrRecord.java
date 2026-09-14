package org.uet.dse.neo4jtgg.engine;

/**
 * Unified correspondence record used across all TGG engines.
 * Replaces the duplicated inner {@code CorrRecord} classes that previously existed
 * in ForwardRuleApplicationEngine, BackwardRuleApplicationEngine, and RuleDrivenCorrMaterializer.
 * <p>
 * Includes trace metadata (rule name, direction, timestamps) to support
 * auditing, orphan cleanup, and resync operations.
 */
public record TracedCorrRecord(
        String objectId,
        String corrClassName,
        String sourceObjectId,
        String sourceClassName,
        String targetObjectId,
        String targetClassName,
        String appliedRuleName,
        TransformationDirection direction,
        long createdAt,
        long lastVerifiedAt
) {
    /**
     * Create a TracedCorrRecord with current timestamp for both createdAt and lastVerifiedAt.
     */
    public static TracedCorrRecord create(String objectId, String corrClassName,
                                           String sourceObjectId, String sourceClassName,
                                           String targetObjectId, String targetClassName,
                                           String appliedRuleName, TransformationDirection direction) {
        long now = System.currentTimeMillis();
        return new TracedCorrRecord(objectId, corrClassName,
                sourceObjectId, sourceClassName,
                targetObjectId, targetClassName,
                appliedRuleName, direction, now, now);
    }

    /**
     * Return a copy with updated lastVerifiedAt timestamp.
     */
    public TracedCorrRecord withVerifiedNow() {
        return new TracedCorrRecord(objectId, corrClassName,
                sourceObjectId, sourceClassName,
                targetObjectId, targetClassName,
                appliedRuleName, direction, createdAt, System.currentTimeMillis());
    }

    /**
     * Build a deterministic corr key for deduplication.
     */
    public String corrKey() {
        return appliedRuleName + "_" + sourceObjectId + "_" + targetObjectId;
    }
}
