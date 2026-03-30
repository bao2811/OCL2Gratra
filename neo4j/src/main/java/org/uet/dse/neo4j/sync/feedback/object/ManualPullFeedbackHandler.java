package org.uet.dse.neo4j.sync.feedback.object;

import org.uet.dse.neo4j.sync.helper.GUIHelper;
import org.uet.dse.neo4j.sync.object.ObjectDiff;
import org.uet.dse.neo4j.sync.feedback.SyncFeedbackHandler;

import javax.swing.*;

public class ManualPullFeedbackHandler implements SyncFeedbackHandler {

    @Override
    public void onModelOutOfSync() {
        GUIHelper.handleInconsistentModel("Pull Objects");
    }

    @Override
    public void onAlreadyInSync() {
        JOptionPane.showMessageDialog(null, "Your local state matches the Database.",
                "No Changes", JOptionPane.INFORMATION_MESSAGE);
    }

    @Override
    public boolean onPreviewAndConfirm(ObjectDiff diff) {
        return GUIHelper.showSyncPreview(diff, "Pull Objects (DB -> Java)");
    }

    @Override
    public void onSuccess(ObjectDiff diff) {
        JOptionPane.showMessageDialog(null, buildSuccessMessage(diff));
    }

    @Override
    public void onFailure(Exception e) {
        JOptionPane.showMessageDialog(null,
                "Pull failed: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
    }
}