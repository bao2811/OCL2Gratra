package org.uet.dse.neo4j.sync.feedback;

import org.uet.dse.neo4j.sync.object.ObjectDiff;

public interface SyncFeedbackHandler {
    /** Called when the model is out of sync before the operation begins. */
    void onModelOutOfSync();

    /** Called when a diff is computed but contains no differences. */
    void onAlreadyInSync();

    /**
     * Called before executing the push. Implementations may show a confirmation
     * dialog or do nothing. Returns false to abort the operation.
     */
    boolean onPreviewAndConfirm(ObjectDiff diff);

    /** Called after a successful push. */
    void onSuccess(ObjectDiff diff);

    /** Called when the push throws. */
    void onFailure(Exception e);

    default String buildSuccessMessage(ObjectDiff diff) {
        return String.format("Sync Complete: %d objects, %d links updated.",
                diff.neo4jOnlyObjects.size(), diff.neo4jOnlyLinks.size());
    }
}
