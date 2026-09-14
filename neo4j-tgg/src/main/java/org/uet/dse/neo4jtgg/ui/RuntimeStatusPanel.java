package org.uet.dse.neo4jtgg.ui;

import org.uet.dse.neo4jtgg.service.impl.DefaultTggWorkspaceService;
import org.uet.dse.neo4jtgg.model.IncrementalApplyResult;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;

@SuppressWarnings("serial")
public class RuntimeStatusPanel extends JPanel {
    private final DefaultTggWorkspaceService workspaceService;
    private final JTextArea txtStatus = new JTextArea();

    public RuntimeStatusPanel(DefaultTggWorkspaceService workspaceService) {
        super(new BorderLayout(8, 8));
        this.workspaceService = workspaceService;

        txtStatus.setEditable(false);
        txtStatus.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        add(buildToolbar(), BorderLayout.NORTH);
        add(new JScrollPane(txtStatus), BorderLayout.CENTER);
        refreshStatus();
    }

    private JComponent buildToolbar() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnRefreshStatus = new JButton("Refresh Status");
        JButton btnRefreshMirror = new JButton("Refresh USE Mirror");
        JButton btnPreviewDelta = new JButton("Preview Remote Delta");
        JButton btnApplyDelta = new JButton("Apply Remote Delta");
        JButton btnDiscardDelta = new JButton("Discard Proposal");
        JButton btnRefreshBaseline = new JButton("Refresh Baseline");

        btnRefreshStatus.addActionListener(e -> refreshStatus());
        btnRefreshMirror.addActionListener(e -> refreshMirror());
        btnPreviewDelta.addActionListener(e -> previewRemoteDelta());
        btnApplyDelta.addActionListener(e -> applyRemoteDelta());
        btnDiscardDelta.addActionListener(e -> discardProposal());
        btnRefreshBaseline.addActionListener(e -> refreshBaseline());

        panel.add(btnRefreshStatus);
        panel.add(btnRefreshMirror);
        panel.add(btnPreviewDelta);
        panel.add(btnApplyDelta);
        panel.add(btnDiscardDelta);
        panel.add(btnRefreshBaseline);
        return panel;
    }

    private void refreshMirror() {
        try {
            workspaceService.refreshUseMirror(true);
            refreshStatus();
        } catch (RuntimeException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Mirror Refresh Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public void refreshStatus() {
        txtStatus.setText(workspaceService.buildRuntimeStatusText());
        txtStatus.setCaretPosition(0);
    }

    private void previewRemoteDelta() {
        try {
            workspaceService.previewIncrementalRemoteChanges(workspaceService.getOrCreateContext());
            refreshStatus();
        } catch (RuntimeException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Preview Remote Delta Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void applyRemoteDelta() {
        try {
            IncrementalApplyResult result = workspaceService.applyPendingIncrementalProposal(workspaceService.getOrCreateContext());
            refreshStatus();
            JOptionPane.showMessageDialog(this, result.message(), "Apply Remote Delta",
                    result.status() == org.uet.dse.neo4jtgg.model.IncrementalSyncStatus.APPLIED
                            ? JOptionPane.INFORMATION_MESSAGE
                            : JOptionPane.WARNING_MESSAGE);
        } catch (RuntimeException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Apply Remote Delta Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void discardProposal() {
        try {
            IncrementalApplyResult result = workspaceService.discardPendingIncrementalProposal(workspaceService.getOrCreateContext());
            refreshStatus();
            JOptionPane.showMessageDialog(this, result.message(), "Discard Proposal", JOptionPane.INFORMATION_MESSAGE);
        } catch (RuntimeException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Discard Proposal Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void refreshBaseline() {
        try {
            IncrementalApplyResult result = workspaceService.refreshIncrementalBaseline(workspaceService.getOrCreateContext());
            refreshStatus();
            JOptionPane.showMessageDialog(this, result.message(), "Refresh Baseline", JOptionPane.INFORMATION_MESSAGE);
        } catch (RuntimeException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Refresh Baseline Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
