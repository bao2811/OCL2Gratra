package org.uet.dse.neo4jtgg.ui;

import org.tzi.use.gui.views.View;
import org.uet.dse.neo4jtgg.model.GuardReport;
import org.uet.dse.neo4jtgg.model.ImportBatch;
import org.uet.dse.neo4jtgg.model.OclValidationResult;
import org.uet.dse.neo4jtgg.model.TggWorkspaceContext;
import org.uet.dse.neo4jtgg.model.WorkspaceSide;
import org.uet.dse.neo4jtgg.engine.TransformationReport;
import org.uet.dse.neo4jtgg.service.impl.DefaultTggWorkspaceService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;

@SuppressWarnings("serial")
public class WorkspaceGraphView extends JPanel implements View {
    private final DefaultTggWorkspaceService workspaceService;
    private final WorkspaceSide side;

    private final JTextArea txtSnapshot = new JTextArea();
    private final JTextArea txtConsole = new JTextArea();
    private final JTextArea txtOcl = new JTextArea(6, 40);
    private final DefaultListModel<String> savedOcls = new DefaultListModel<>();
    private final DefaultListModel<String> ruleNames = new DefaultListModel<>();
    private final JList<String> lstSavedOcls = new JList<>(savedOcls);
    private final JList<String> lstRules = new JList<>(ruleNames);
    private final JLabel lblSummary = new JLabel();

    public WorkspaceGraphView(DefaultTggWorkspaceService workspaceService, WorkspaceSide side) {
        this.workspaceService = workspaceService;
        this.side = side;
        setLayout(new BorderLayout(8, 8));
        setBorder(new EmptyBorder(8, 8, 8, 8));

        txtSnapshot.setEditable(false);
        txtSnapshot.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        txtConsole.setEditable(false);
        txtConsole.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        txtOcl.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        add(buildHeader(), BorderLayout.NORTH);
        add(buildWorkspaceBody(), BorderLayout.CENTER);
    }

    private JComponent buildHeader() {
        JPanel header = new JPanel(new BorderLayout(8, 8));
        JPanel titlePanel = new JPanel(new BorderLayout(4, 2));
        JLabel title = new JLabel(side.getDisplayName() + " Workspace");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        JLabel subtitle = new JLabel(sideDescription());
        subtitle.setForeground(new Color(80, 80, 80));
        titlePanel.add(title, BorderLayout.NORTH);
        titlePanel.add(subtitle, BorderLayout.CENTER);

        lblSummary.setText("Neo4j is the source of truth.");
        lblSummary.setForeground(new Color(70, 70, 70));

        header.add(titlePanel, BorderLayout.WEST);
        header.add(lblSummary, BorderLayout.CENTER);
        header.add(buildToolbar(), BorderLayout.SOUTH);
        return header;
    }

    private String sideDescription() {
        if (side == WorkspaceSide.SOURCE) {
            return "Source M0 instances imported from Families.xmi and read directly from Neo4j.";
        }
        if (side == WorkspaceSide.TARGET) {
            return "Target M0 instances produced by forward transform or imported from Persons.xmi.";
        }
        return "TGG correspondence and trace objects connecting Source and Target instances.";
    }

    private JComponent buildToolbar() {
        JPanel panel = new JPanel(new GridLayout(0, 1, 4, 4));
        panel.setBorder(BorderFactory.createTitledBorder("Actions"));

        JButton btnRefresh = new JButton("Refresh");
        JButton btnPushModel = new JButton("Push Model");
        JButton btnPushObjects = new JButton("Push Objects (disabled)");
        JButton btnPullObjects = new JButton("Refresh USE Mirror");
        JButton btnApplyRemote = new JButton("Apply Remote Delta");
        JButton btnPreviewForward = new JButton("Preview Forward");
        JButton btnTransformForward = new JButton("Transform Forward");
        JButton btnPreviewBackward = new JButton("Preview Backward");
        JButton btnTransformBackward = new JButton("Transform Backward");

        btnRefresh.addActionListener(e -> refreshContent());
        btnPushModel.addActionListener(e -> workspaceService.pushModelToNeo4j());
        btnPushObjects.addActionListener(e -> workspaceService.pushObjectsToNeo4j(true));
        btnPullObjects.addActionListener(e -> workspaceService.pullObjectsFromNeo4j(true));
        btnApplyRemote.addActionListener(e -> workspaceService.applyRemoteChanges());
        btnPreviewForward.addActionListener(e -> previewForwardTransformation());
        btnTransformForward.addActionListener(e -> runForwardTransformation());
        btnPreviewBackward.addActionListener(e -> previewBackwardTransformation());
        btnTransformBackward.addActionListener(e -> runBackwardTransformation());

        JPanel workspaceActions = buttonRow("Workspace", btnRefresh, btnPullObjects, btnApplyRemote);
        JPanel transformActions = buttonRow("Transform", btnPreviewForward, btnTransformForward, btnPreviewBackward, btnTransformBackward);
        JPanel legacyActions = buttonRow("Legacy Disabled", btnPushModel, btnPushObjects);

        if (side != WorkspaceSide.CORRESPONDENCE) {
            JButton btnImport = new JButton("Import XMI/XML");
            btnImport.addActionListener(e -> importBatch());
            btnImport.setToolTipText("Create M0 instances on Neo4j and link them to the existing imported metamodel.");
            panel.add(buttonRow("Data", btnImport));
        }

        panel.add(workspaceActions);
        panel.add(transformActions);
        panel.add(legacyActions);
        return panel;
    }

    private JPanel buttonRow(String title, JButton... buttons) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));
        row.add(new JLabel(title + ":"));
        for (JButton button : buttons) {
            row.add(button);
        }
        return row;
    }

    private JComponent buildWorkspaceBody() {
        JSplitPane vertical = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        vertical.setResizeWeight(0.72);
        vertical.setBorder(BorderFactory.createEmptyBorder());
        vertical.setTopComponent(buildCenter());
        vertical.setBottomComponent(wrapWithTitle(new JScrollPane(txtConsole), "Preview / Validation / Runtime Log"));
        vertical.setMinimumSize(new Dimension(720, 480));
        return vertical;
    }

    private JComponent buildCenter() {
        JSplitPane horizontal = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        horizontal.setResizeWeight(0.66);
        horizontal.setLeftComponent(wrapWithTitle(new JScrollPane(txtSnapshot), "Neo4j M0 Snapshot"));
        horizontal.setRightComponent(buildInspectorPanel());
        horizontal.setMinimumSize(new Dimension(720, 320));
        return horizontal;
    }

    private JComponent buildInspectorPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));

        lstSavedOcls.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        lstSavedOcls.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && lstSavedOcls.getSelectedValue() != null) {
                txtOcl.setText(lstSavedOcls.getSelectedValue());
            }
        });

        lstRules.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JPanel oclActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));
        JButton btnSave = new JButton("Save OCL");
        JButton btnLoadRule = new JButton("Insert Rule OCL");
        JButton btnRun = new JButton("Validate on Neo4j");

        btnSave.addActionListener(e -> saveCurrentOcl());
        btnLoadRule.addActionListener(e -> insertRuleOcl());
        btnRun.addActionListener(e -> validateOcl());

        oclActions.add(btnSave);
        oclActions.add(btnLoadRule);
        oclActions.add(btnRun);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Rules", wrapWithTitle(new JScrollPane(lstRules), "TGG Rules"));
        tabs.addTab("Saved OCL", wrapWithTitle(new JScrollPane(lstSavedOcls), "Saved OCL Expressions"));

        JPanel oclPanel = new JPanel(new BorderLayout(6, 6));
        oclPanel.add(wrapWithTitle(new JScrollPane(txtOcl), "OCL Editor"), BorderLayout.CENTER);
        oclPanel.add(oclActions, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        split.setResizeWeight(0.42);
        split.setTopComponent(tabs);
        split.setBottomComponent(oclPanel);
        split.setMinimumSize(new Dimension(260, 320));
        panel.add(split, BorderLayout.CENTER);
        return wrapWithTitle(panel, "Rules / OCL Inspector");
    }

    private JComponent wrapWithTitle(JComponent component, String title) {
        component.setBorder(BorderFactory.createTitledBorder(title));
        return component;
    }

    public void refreshContent() {
        TggWorkspaceContext context = workspaceService.getOrCreateContext();
        txtSnapshot.setText(workspaceService.buildSnapshotText(context, side));
        txtConsole.setText(buildConsoleText(context));
        txtConsole.setCaretPosition(0);
        lblSummary.setText(context.getRemoteChangeSummary().replace('\n', ' '));

        ruleNames.clear();
        if (context.getWorkspaceDefinition() != null) {
            context.getWorkspaceDefinition().getRules().forEach(rule -> ruleNames.addElement(rule.getName()));
        }
    }

    private String buildConsoleText(TggWorkspaceContext context) {
        StringBuilder sb = new StringBuilder();
        appendConsoleSection(sb, "Validation Result", context.getLastValidation(side));
        appendConsoleSection(sb, "Preview", context.getLastPreview(side));
        appendConsoleSection(sb, "Runtime Log", context.getLog(side));
        return sb.toString().trim();
    }

    private void appendConsoleSection(StringBuilder sb, String title, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        if (sb.length() > 0) {
            sb.append("\n\n");
        }
        sb.append("== ").append(title).append(" ==\n").append(text.trim());
    }

    public void insertOclText(String text) {
        txtOcl.setText(text);
    }

    private void saveCurrentOcl() {
        String ocl = txtOcl.getText().trim();
        if (!ocl.isBlank() && !containsSavedOcl(ocl)) {
            savedOcls.addElement(ocl);
        }
    }

    private boolean containsSavedOcl(String ocl) {
        for (int i = 0; i < savedOcls.size(); i++) {
            if (ocl.equals(savedOcls.get(i))) {
                return true;
            }
        }
        return false;
    }

    private void insertRuleOcl() {
        String selectedRule = lstRules.getSelectedValue();
        if (selectedRule == null) {
            return;
        }
        workspaceService.insertSelectedRuleText(workspaceService.getOrCreateContext(), side, selectedRule);
    }

    private void validateOcl() {
        String ocl = txtOcl.getText().trim();
        if (ocl.isBlank()) {
            return;
        }
        OclValidationResult result = workspaceService.getOclValidationService()
                .validate(workspaceService.getOrCreateContext(), side, ocl);
        workspaceService.getOrCreateContext().setLastValidation(side, result.toDisplayText());
        workspaceService.getOrCreateContext().appendLog(side, "Validated OCL on Neo4j: " + result.getSummary());
        refreshContent();
        showScrollableMessage(result.toDisplayText(), "OCL Validation Result", result.isSuccess()
                ? JOptionPane.INFORMATION_MESSAGE
                : JOptionPane.WARNING_MESSAGE);
    }

    private void importBatch() {
        JFileChooser chooser = new JFileChooser();
        File defaultFile = workspaceService.getDefaultImportFile(side);
        if (defaultFile != null) {
            chooser.setSelectedFile(defaultFile);
        }
        int result = chooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File file = chooser.getSelectedFile();
        try {
            ImportBatch batch = workspaceService.previewImport(workspaceService.getOrCreateContext(), side, file);
            GuardReport report = workspaceService.getChangeGuardService()
                    .validateImport(workspaceService.getOrCreateContext(), batch);
            refreshContent();

            if (!report.isAllowed()) {
                showScrollableMessage(report.toDisplayText(), "Import blocked", JOptionPane.WARNING_MESSAGE);
                return;
            }

            int answer = showScrollableConfirm(
                    workspaceService.getOrCreateContext().getLastPreview(side),
                    "Apply import batch?",
                    JOptionPane.INFORMATION_MESSAGE);
            if (answer == JOptionPane.YES_OPTION) {
                workspaceService.applyImport(workspaceService.getOrCreateContext(), batch);
            }
        } catch (Exception ex) {
            showScrollableMessage(ex.getMessage(), "Import Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void previewForwardTransformation() {
        try {
            TransformationReport report = workspaceService.previewForwardTransformation(workspaceService.getOrCreateContext());
            showScrollableMessage(report.toDisplayText(), "Preview Forward", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            showScrollableMessage(ex.getMessage(), "Forward Preview Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void runForwardTransformation() {
        try {
            TransformationReport report = workspaceService.previewForwardTransformation(workspaceService.getOrCreateContext());
            int answer = showScrollableConfirm(
                    report.toDisplayText(),
                    "Apply forward transformation?",
                    JOptionPane.INFORMATION_MESSAGE);
            if (answer == JOptionPane.YES_OPTION) {
                TransformationReport applied = workspaceService.runForwardTransformation(workspaceService.getOrCreateContext());
                showScrollableMessage(applied.toDisplayText(), "Forward Transformation", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception ex) {
            showScrollableMessage(ex.getMessage(), "Forward Transformation Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void previewBackwardTransformation() {
        try {
            TransformationReport report = workspaceService.previewBackwardTransformation(workspaceService.getOrCreateContext());
            showScrollableMessage(report.toDisplayText(), "Preview Backward", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            showScrollableMessage(ex.getMessage(), "Backward Preview Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void runBackwardTransformation() {
        try {
            TransformationReport report = workspaceService.previewBackwardTransformation(workspaceService.getOrCreateContext());
            int answer = showScrollableConfirm(
                    report.toDisplayText(),
                    "Apply backward transformation?",
                    JOptionPane.INFORMATION_MESSAGE);
            if (answer == JOptionPane.YES_OPTION) {
                TransformationReport applied = workspaceService.runBackwardTransformation(workspaceService.getOrCreateContext());
                showScrollableMessage(applied.toDisplayText(), "Backward Transformation", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception ex) {
            showScrollableMessage(ex.getMessage(), "Backward Transformation Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showScrollableMessage(String text, String title, int messageType) {
        JOptionPane.showMessageDialog(this, buildScrollableTextPane(text), title, messageType);
    }

    private int showScrollableConfirm(String text, String title, int messageType) {
        return JOptionPane.showConfirmDialog(this,
                buildScrollableTextPane(text),
                title,
                JOptionPane.YES_NO_OPTION,
                messageType);
    }

    private JComponent buildScrollableTextPane(String text) {
        JTextArea area = new JTextArea(text == null ? "" : text);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setCaretPosition(0);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        area.setOpaque(false);

        JScrollPane scrollPane = new JScrollPane(area,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setPreferredSize(computeDialogContentSize());
        return scrollPane;
    }

    private Dimension computeDialogContentSize() {
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int width = Math.max(420, Math.min(860, screen.width - 220));
        int height = Math.max(260, Math.min(560, screen.height - 260));
        return new Dimension(width, height);
    }

    @Override
    public void detachModel() {
        // No extra detach logic required for this lightweight view.
    }
}
