package org.uet.dse.neo4j.sync.feedback.object;

import org.uet.dse.neo4j.sync.helper.GUIHelper;
import org.uet.dse.neo4j.sync.object.ObjectDiff;
import org.uet.dse.neo4j.sync.feedback.SyncFeedbackHandler;
import org.uet.dse.neo4j.sync.feedback.SyncPreviewProvider;

import javax.swing.*;

public class ManualSyncFeedbackHandler implements SyncFeedbackHandler {

    private final SyncPreviewProvider previewProvider;

    public ManualSyncFeedbackHandler(SyncPreviewProvider previewProvider) {
        this.previewProvider = previewProvider;
    }

    @Override
    public void onModelOutOfSync() {
        GUIHelper.handleInconsistentModel("Push Objects");
    }

    @Override
    public void onAlreadyInSync() {
        JOptionPane.showMessageDialog(
            null,
            "Object states are already in sync.",
            "No Changes",
            JOptionPane.INFORMATION_MESSAGE
        );
    }

    @Override
    public boolean onPreviewAndConfirm(ObjectDiff diff) {
        return previewProvider.showSyncPreview(diff, "Forward (Java -> DB)");
    }

    @Override
    public void onSuccess(ObjectDiff diff) {
        SwingUtilities.invokeLater(() ->
            JOptionPane.showMessageDialog(
                null,
                buildSuccessMessage(diff),
                "Success",
                JOptionPane.INFORMATION_MESSAGE
            )
        );
    }

    @Override
    public void onFailure(Exception e) {
        JOptionPane.showMessageDialog(
            null,
            "Sync failed: " + e.getMessage(),
            "Error",
            JOptionPane.ERROR_MESSAGE
        );
    }

    public String buildSuccessMessage(ObjectDiff diff) {
        return String.format(
            "<html><b>Object Sync Successful!</b><br/>" +
            "Created: %d<br/>Updated: %d<br/>Deleted from DB: %d</html>",
            diff.javaOnlyObjects.size(),
            diff.mismatchedObjects.size(),
            diff.neo4jOnlyObjects.size()
        );
    }
}