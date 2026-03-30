package org.uet.dse.neo4j.sync.helper;

import org.uet.dse.neo4j.sync.object.ObjectDiff;

import javax.swing.*;
import java.awt.*;

public class GUIHelper {
    public static void handleInconsistentModel(String action) {
        JOptionPane.showMessageDialog(null,
                "<html><b>" + action + " Failed!</b><br/>" +
                        "Your local Model version does not match Database.<br/>" +
                        "<i>Reason: Model is the Source of Truth for Objects.</i><br/>" +
                        "Please perform a <b>Model Sync</b> first.</html>",
                "Version Conflict", JOptionPane.WARNING_MESSAGE);
    }

    public static boolean showSyncPreview(ObjectDiff diff, String title) {
        String report = "Are u sure?";//title.contains("Forward") ? diff.generateForwardReport() : diff.generateBackwardReport();
        JTextArea textArea = new JTextArea(report);
        textArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        textArea.setEditable(false);
        int res = JOptionPane.showConfirmDialog(null, new JScrollPane(textArea),
                "Sync Preview: " + "on sync", JOptionPane.OK_CANCEL_OPTION);
        return res == JOptionPane.OK_OPTION;
    }
}
