package org.uet.dse.neo4j.sync.feedback.object;

import org.uet.dse.neo4j.manager.WorkLogManager;
import org.uet.dse.neo4j.sync.object.ObjectDiff;
import org.uet.dse.neo4j.sync.feedback.SyncFeedbackHandler;

public class AutoSyncFeedbackHandler implements SyncFeedbackHandler {

    @Override
    public void onModelOutOfSync() {
        System.out.println("Real-time sync skipped: Model version mismatch.");
    }

    @Override
    public void onAlreadyInSync() {
        // Background sync finding no diff is expected — not worth logging.
    }

    @Override
    public boolean onPreviewAndConfirm(ObjectDiff diff) {
        return true; // Auto-sync never requires confirmation.
    }

    @Override
    public void onSuccess(ObjectDiff diff) {
        System.out.printf("Real-time: Successfully pushed %d new objects.%n",
            diff.javaOnlyObjects.size());
    }

    @Override
    public void onFailure(Exception e) {
        System.err.println("Auto-sync error: " + e.getMessage());
        WorkLogManager.getInstance().log("REALTIME_ERROR", e.getMessage());
    }
}
