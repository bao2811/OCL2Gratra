package org.uet.dse.neo4j.sync.feedback.object;

import org.uet.dse.neo4j.manager.WorkLogManager;
import org.uet.dse.neo4j.sync.object.ObjectDiff;
import org.uet.dse.neo4j.sync.feedback.SyncFeedbackHandler;

public class AutoPullFeedbackHandler implements SyncFeedbackHandler {

    @Override
    public void onModelOutOfSync() {
        System.out.println("Real-time pull skipped: Model version mismatch.");
    }

    @Override
    public void onAlreadyInSync() {

    }

    @Override
    public boolean onPreviewAndConfirm(ObjectDiff diff) {
        return true;
    }

    @Override
    public void onSuccess(ObjectDiff diff) {
        WorkLogManager.getInstance().log("OBJECT_PULL_SUCCESS", buildSuccessMessage(diff));
    }

    @Override
    public void onFailure(Exception e) {
        WorkLogManager.getInstance().log("OBJECT_PULL_ERROR", e.getMessage());
    }
}
