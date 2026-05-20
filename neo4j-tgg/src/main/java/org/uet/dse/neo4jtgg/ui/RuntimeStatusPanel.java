package org.uet.dse.neo4jtgg.ui;

import org.uet.dse.neo4jtgg.service.impl.DefaultTggWorkspaceService;

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

        btnRefreshStatus.addActionListener(e -> refreshStatus());
        btnRefreshMirror.addActionListener(e -> refreshMirror());

        panel.add(btnRefreshStatus);
        panel.add(btnRefreshMirror);
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
}
