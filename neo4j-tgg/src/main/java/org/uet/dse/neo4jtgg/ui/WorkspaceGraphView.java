package org.uet.dse.neo4jtgg.ui;

import org.tzi.use.gui.views.View;
import org.uet.dse.neo4jtgg.model.GuardReport;
import org.uet.dse.neo4jtgg.model.ImportBatch;
import org.uet.dse.neo4jtgg.model.IncrementalApplyResult;
import org.uet.dse.neo4jtgg.model.IncrementalSyncProposal;
import org.uet.dse.neo4jtgg.model.OclFileValidationResult;
import org.uet.dse.neo4jtgg.model.OclRuleValidationResult;
import org.uet.dse.neo4jtgg.model.TggWorkspaceContext;
import org.uet.dse.neo4jtgg.model.WorkspaceSide;
import org.uet.dse.neo4jtgg.engine.TransformationReport;
import org.uet.dse.neo4jtgg.service.impl.DefaultTggWorkspaceService;
import org.uet.dse.neo4jtgg.service.impl.OclDocumentRuleParameterValuesParser;
import org.uet.dse.neo4jtgg.service.impl.OclParameterValuesParser;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

@SuppressWarnings("serial")
public class WorkspaceGraphView extends JPanel implements View {
    private enum ValidationFilter {
        ALL("All"),
        FAILURES("Failures"),
        SKIPPED("Skipped"),
        FALLBACK("Fallback"),
        UNSUPPORTED("Unsupported");

        private final String label;

        ValidationFilter(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private sealed interface ValidationListEntry permits ValidationSummaryEntry, ValidationRuleEntry {
        String displayText();
    }

    private record ValidationSummaryEntry(String summary) implements ValidationListEntry {
        @Override
        public String displayText() {
            return summary;
        }
    }

    private record ValidationRuleEntry(OclRuleValidationResult ruleResult) implements ValidationListEntry {
        @Override
        public String displayText() {
            String target = formatRuleTarget(ruleResult);
            StringBuilder line = new StringBuilder();
            line.append(statusLabel(ruleResult)).append(' ').append(target);
            if (ruleResult.isSkipped()) {
                line.append(" [skipped]");
            }
            if (ruleResult.isFallbackUsed()) {
                line.append(" [fallback]");
            }
            if (!ruleResult.isCompilerSupported()) {
                line.append(" [unsupported]");
            }
            if (!ruleResult.getViolations().isEmpty()) {
                line.append(" violations=").append(ruleResult.getViolations().size());
            }
            return line.toString();
        }
    }

    private final DefaultTggWorkspaceService workspaceService;
    private final WorkspaceSide side;

    private final JTextArea txtSnapshot = new JTextArea();
    private final JTextArea txtConsole = new JTextArea();
    private final JTextArea txtOcl = new JTextArea(6, 40);
    private final DefaultListModel<String> savedOcls = new DefaultListModel<>();
    private final DefaultListModel<String> ruleNames = new DefaultListModel<>();
    private final DefaultListModel<ValidationListEntry> validationRules = new DefaultListModel<>();
    private final JList<String> lstSavedOcls = new JList<>(savedOcls);
    private final JList<String> lstRules = new JList<>(ruleNames);
    private final JList<ValidationListEntry> lstValidationRules = new JList<>(validationRules);
    private final JComboBox<ValidationFilter> cmbValidationFilter = new JComboBox<>(ValidationFilter.values());
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
        lstValidationRules.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                String text = value instanceof ValidationListEntry entry ? entry.displayText() : String.valueOf(value);
                Component component = super.getListCellRendererComponent(list, text, index, isSelected, cellHasFocus);
                if (!isSelected && value instanceof ValidationRuleEntry ruleEntry) {
                    if (ruleEntry.ruleResult().isSkipped()) {
                        component.setForeground(new Color(110, 80, 20));
                    } else if (!ruleEntry.ruleResult().isSuccess()) {
                        component.setForeground(new Color(140, 30, 30));
                    } else if (ruleEntry.ruleResult().isFallbackUsed()) {
                        component.setForeground(new Color(140, 90, 20));
                    }
                }
                return component;
            }
        });

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
        JButton btnPreviewRemote = new JButton("Preview Remote Delta");
        JButton btnApplyRemote = new JButton("Apply Remote Delta");
        JButton btnDiscardRemote = new JButton("Discard Proposal");
        JButton btnRefreshBaseline = new JButton("Refresh Baseline");
        JButton btnPreviewForward = new JButton("Preview Forward");
        JButton btnTransformForward = new JButton("Transform Forward");
        JButton btnPreviewBackward = new JButton("Preview Backward");
        JButton btnTransformBackward = new JButton("Transform Backward");

        btnRefresh.addActionListener(e -> refreshContent());
        btnPushModel.addActionListener(e -> workspaceService.pushModelToNeo4j());
        btnPushObjects.addActionListener(e -> workspaceService.pushObjectsToNeo4j(true));
        btnPullObjects.addActionListener(e -> workspaceService.pullObjectsFromNeo4j(true));
        btnPreviewRemote.addActionListener(e -> previewRemoteDelta());
        btnApplyRemote.addActionListener(e -> applyRemoteDelta());
        btnDiscardRemote.addActionListener(e -> discardRemoteProposal());
        btnRefreshBaseline.addActionListener(e -> refreshIncrementalBaseline());
        btnPreviewForward.addActionListener(e -> previewForwardTransformation());
        btnTransformForward.addActionListener(e -> runForwardTransformation());
        btnPreviewBackward.addActionListener(e -> previewBackwardTransformation());
        btnTransformBackward.addActionListener(e -> runBackwardTransformation());

        JPanel workspaceActions = buttonRow("Workspace", btnRefresh, btnPullObjects, btnPreviewRemote, btnApplyRemote, btnDiscardRemote, btnRefreshBaseline);
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
        lstValidationRules.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        lstValidationRules.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showSelectedValidationDetail();
            }
        });
        cmbValidationFilter.addActionListener(e -> refreshValidationList(workspaceService.getOrCreateContext()));

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
        JPanel validationPanel = new JPanel(new BorderLayout(6, 6));
        JPanel validationToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));
        validationToolbar.add(new JLabel("Show:"));
        validationToolbar.add(cmbValidationFilter);
        JButton btnRerunSelectedRule = new JButton("Re-run Selected Rule");
        btnRerunSelectedRule.addActionListener(e -> rerunSelectedValidationRule());
        validationToolbar.add(btnRerunSelectedRule);
        JButton btnCopySelectedCypher = new JButton("Copy Selected Cypher");
        btnCopySelectedCypher.addActionListener(e -> copySelectedRuleCypher());
        validationToolbar.add(btnCopySelectedCypher);
        JButton btnExportFilteredReport = new JButton("Export Filtered Report");
        btnExportFilteredReport.addActionListener(e -> exportFilteredValidationReport());
        validationToolbar.add(btnExportFilteredReport);
        validationPanel.add(validationToolbar, BorderLayout.NORTH);
        validationPanel.add(wrapWithTitle(new JScrollPane(lstValidationRules), "Latest Rule Results"), BorderLayout.CENTER);
        tabs.addTab("Validation", validationPanel);
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
        refreshValidationList(context);
        lblSummary.setText(buildHeaderSummary(context));

        ruleNames.clear();
        if (context.getWorkspaceDefinition() != null) {
            context.getWorkspaceDefinition().getRules().forEach(rule -> ruleNames.addElement(rule.getName()));
        }
    }

    private String buildConsoleText(TggWorkspaceContext context) {
        StringBuilder sb = new StringBuilder();
        IncrementalSyncProposal proposal = context.getCurrentProposal();
        if (proposal != null) {
            appendConsoleSection(sb, "Incremental Proposal", proposal.toDisplayText());
        }
        IncrementalApplyResult applyResult = context.getLastApplyResult();
        if (applyResult != null) {
            appendConsoleSection(sb, "Incremental Status", applyResult.status().getLabel() + " | " + applyResult.message());
        }
        appendConsoleSection(sb, "Validation Result", context.getLastValidation(side));
        appendConsoleSection(sb, "Preview", context.getLastPreview(side));
        appendConsoleSection(sb, "Runtime Log", context.getLog(side));
        return sb.toString().trim();
    }

    private String buildHeaderSummary(TggWorkspaceContext context) {
        String remote = context.getRemoteChangeSummary().replace('\n', ' ').trim();
        OclFileValidationResult validationResult = context.getLastValidationResult(side);
        IncrementalSyncProposal proposal = context.getCurrentProposal();
        String proposalSummary = proposal == null ? context.getLastApplyResult().status().getLabel() : proposal.summary();
        if (validationResult == null) {
            return remote + " | Incremental: " + proposalSummary;
        }
        return remote + " | Incremental: " + proposalSummary + " | Validation: rules=" + validationResult.getRuleCount()
                + ", pass=" + validationResult.getPassCount()
                + ", fail=" + validationResult.getFailCount()
                + ", skipped=" + validationResult.getSkippedCount()
                + ", fallback=" + validationResult.getFallbackCount()
                + ", unsupported=" + validationResult.getUnsupportedCount()
                + ", responseTimeMs=" + validationResult.getResponseTimeMs();
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

    private void refreshValidationList(TggWorkspaceContext context) {
        validationRules.clear();
        OclFileValidationResult validationResult = context.getLastValidationResult(side);
        if (validationResult == null) {
            return;
        }

        validationRules.addElement(new ValidationSummaryEntry(validationResult.getSummary()));
        ValidationFilter filter = (ValidationFilter) cmbValidationFilter.getSelectedItem();
        for (OclRuleValidationResult ruleResult : validationResult.getRuleResults()) {
            if (matchesFilter(ruleResult, filter)) {
                validationRules.addElement(new ValidationRuleEntry(ruleResult));
            }
        }
    }

    private void showSelectedValidationDetail() {
        Object selectedValue = lstValidationRules.getSelectedValue();
        if (!(selectedValue instanceof ValidationRuleEntry selectedEntry)) {
            txtConsole.setText(buildConsoleText(workspaceService.getOrCreateContext()));
            txtConsole.setCaretPosition(0);
            return;
        }

        OclRuleValidationResult selectedRule = selectedEntry.ruleResult();
        StringBuilder detail = new StringBuilder(buildConsoleText(workspaceService.getOrCreateContext()));
        appendConsoleSection(detail, "Selected Rule", buildValidationRuleDetail(selectedRule));
        txtConsole.setText(detail.toString().trim());
        txtConsole.setCaretPosition(0);
    }

    private boolean matchesFilter(OclRuleValidationResult ruleResult, ValidationFilter filter) {
        if (filter == null || filter == ValidationFilter.ALL) {
            return true;
        }
        return switch (filter) {
            case FAILURES -> !ruleResult.isSuccess() && !ruleResult.isSkipped();
            case SKIPPED -> ruleResult.isSkipped();
            case FALLBACK -> ruleResult.isFallbackUsed();
            case UNSUPPORTED -> !ruleResult.isCompilerSupported();
            case ALL -> true;
        };
    }

    private void rerunSelectedValidationRule() {
        Object selectedValue = lstValidationRules.getSelectedValue();
        if (!(selectedValue instanceof ValidationRuleEntry selectedEntry)) {
            return;
        }

        String ocl = txtOcl.getText().trim();
        if (ocl.isBlank()) {
            return;
        }

        OclRuleValidationResult rerunResult = workspaceService.getOclValidationService().validateRule(
                workspaceService.getOrCreateContext(),
                side,
                ocl,
                selectedEntry.ruleResult().getContextClassName(),
                selectedEntry.ruleResult().getRuleName());
        if (selectedEntry.ruleResult().getOperationName() != null && !selectedEntry.ruleResult().getOperationName().isBlank()) {
            Map<String, Object> parameterValues = promptOperationParameterValues(selectedEntry.ruleResult());
            if (parameterValues == null) {
                return;
            }
            rerunResult = workspaceService.getOclValidationService().validateOperationRule(
                    workspaceService.getOrCreateContext(),
                    side,
                    ocl,
                    selectedEntry.ruleResult().getContextClassName(),
                    selectedEntry.ruleResult().getOperationName(),
                    selectedEntry.ruleResult().getRuleName(),
                    parameterValues);
        }

        TggWorkspaceContext context = workspaceService.getOrCreateContext();
        OclFileValidationResult current = context.getLastValidationResult(side);
        OclFileValidationResult updated = current != null
                ? current.replaceRuleResult(rerunResult)
                : OclFileValidationResult.fromRuleResults(List.of(rerunResult));
        context.setLastValidationResult(side, updated);
        context.appendLog(side, "Re-ran OCL rule on Neo4j: " + formatRuleTarget(rerunResult));
        refreshContent();
        showScrollableMessage(buildValidationRuleDetail(rerunResult), "Selected Rule Validation Result",
                rerunResult.isSuccess() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
    }

    private void copySelectedRuleCypher() {
        Object selectedValue = lstValidationRules.getSelectedValue();
        if (!(selectedValue instanceof ValidationRuleEntry selectedEntry)) {
            return;
        }

        String cypher = selectedEntry.ruleResult().getGeneratedCypher();
        if (cypher == null || cypher.isBlank()) {
            showScrollableMessage("The selected rule does not have generated Cypher.", "Copy Selected Cypher",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(cypher), null);
        workspaceService.getOrCreateContext().appendLog(side, "Copied generated Cypher for selected validation rule.");
        refreshContent();
    }

    private void exportFilteredValidationReport() {
        TggWorkspaceContext context = workspaceService.getOrCreateContext();
        OclFileValidationResult current = context.getLastValidationResult(side);
        if (current == null) {
            return;
        }

        ValidationFilter filter = (ValidationFilter) cmbValidationFilter.getSelectedItem();
        List<OclRuleValidationResult> filteredRules = current.getRuleResults().stream()
                .filter(rule -> matchesFilter(rule, filter))
                .toList();
        OclFileValidationResult filtered = OclFileValidationResult.fromRuleResults(filteredRules);

        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File(side.name().toLowerCase() + "-validation-report.txt"));
        int answer = chooser.showSaveDialog(this);
        if (answer != JFileChooser.APPROVE_OPTION) {
            return;
        }

        try {
            Files.writeString(chooser.getSelectedFile().toPath(), filtered.toDisplayText());
            context.appendLog(side, "Exported filtered validation report to " + chooser.getSelectedFile().getAbsolutePath());
            refreshContent();
            showScrollableMessage("Exported validation report to:\n" + chooser.getSelectedFile().getAbsolutePath(),
                    "Export Filtered Report", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            showScrollableMessage("Failed to export validation report: " + ex.getMessage(),
                    "Export Filtered Report", JOptionPane.ERROR_MESSAGE);
        }
    }

    private String buildValidationRuleDetail(OclRuleValidationResult ruleResult) {
        StringBuilder sb = new StringBuilder();
        String target = formatRuleTarget(ruleResult);
        sb.append(statusLabel(ruleResult)).append(' ').append(target).append('\n');
        sb.append(ruleResult.getSummary());
        if (ruleResult.isSkipped()) {
            sb.append("\nMode: compiled Cypher skipped until invocation inputs are provided");
        } else if (ruleResult.isFallbackUsed()) {
            sb.append("\nMode: fallback evaluator");
        } else if (ruleResult.isCompilerSupported()) {
            sb.append("\nMode: compiled Cypher");
        } else {
            sb.append("\nMode: unsupported");
        }
        if (ruleResult.getGeneratedCypher() != null && !ruleResult.getGeneratedCypher().isBlank()) {
            sb.append("\n\nCypher:\n").append(ruleResult.getGeneratedCypher());
        }
        if (ruleResult.getResponseTimeMs() > 0
                || ruleResult.getParseTimeMs() > 0
                || ruleResult.getCompileTimeMs() > 0
                || ruleResult.getExecutionTimeMs() > 0
                || ruleResult.getFallbackTimeMs() > 0) {
            sb.append("\n\nTiming:");
            sb.append("\n- responseTimeMs=").append(ruleResult.getResponseTimeMs());
            sb.append("\n- parseTimeMs=").append(ruleResult.getParseTimeMs());
            sb.append("\n- compileTimeMs=").append(ruleResult.getCompileTimeMs());
            sb.append("\n- executionTimeMs=").append(ruleResult.getExecutionTimeMs());
            sb.append("\n- fallbackTimeMs=").append(ruleResult.getFallbackTimeMs());
        }
        if (!ruleResult.getViolations().isEmpty()) {
            sb.append("\n\nViolations:");
            for (var entry : ruleResult.getViolations().entrySet()) {
                sb.append("\n- ").append(entry.getKey()).append(": ").append(entry.getValue());
            }
        }
        if (!ruleResult.getDiagnostics().isEmpty()) {
            sb.append("\n\nDiagnostics:");
            for (var diagnostic : ruleResult.getDiagnostics()) {
                sb.append("\n- ").append(diagnostic.toUserMessage());
            }
        }
        if (!ruleResult.getRequiredInputs().isEmpty()) {
            sb.append("\n\nRequired Inputs:");
            for (String requiredInput : ruleResult.getRequiredInputs()) {
                sb.append("\n- ").append(requiredInput);
            }
        }
        if (ruleResult.getResultLocation() != null) {
            appendResultLocation(sb, ruleResult.getResultLocation());
        }
        return sb.toString();
    }

    private void appendResultLocation(StringBuilder sb, org.uet.dse.neo4jtgg.model.OclResultLocation location) {
        boolean hasSpan = location.line() != null || location.column() != null
                || location.endLine() != null || location.endColumn() != null;
        boolean hasObjectIds = !location.objectIds().isEmpty();
        boolean hasToken = location.tokenText() != null && !location.tokenText().isBlank();
        boolean hasSnippet = location.sourceSnippet() != null && !location.sourceSnippet().isBlank();
        if (!hasSpan && !hasObjectIds && !hasToken && !hasSnippet) {
            return;
        }
        sb.append("\n\nResult Location:");
        if (hasSpan) {
            sb.append("\n- line=").append(location.line())
                    .append(", column=").append(location.column())
                    .append(", endLine=").append(location.endLine())
                    .append(", endColumn=").append(location.endColumn());
        }
        if (hasObjectIds) {
            sb.append("\n- objectIds=").append(location.objectIds());
        }
        if (hasToken) {
            sb.append("\n- token=").append(location.tokenText());
        }
        if (hasSnippet) {
            sb.append("\n- source=").append(location.sourceSnippet());
        }
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
        Map<String, Map<String, Object>> documentRuleParameters = Map.of();
        if (looksLikeOperationPreDocument(ocl)) {
            documentRuleParameters = promptDocumentRuleParameterValues();
            if (documentRuleParameters == null) {
                return;
            }
        }
        OclFileValidationResult result = workspaceService.getOclValidationService()
                .validateFile(workspaceService.getOrCreateContext(), side, ocl, documentRuleParameters);
        workspaceService.getOrCreateContext().setLastValidationResult(side, result);
        workspaceService.getOrCreateContext().appendLog(side, "Validated OCL document on Neo4j: " + result.getSummary());
        refreshContent();
        showScrollableMessage(result.toDisplayText(), "OCL Validation Result", result.isSuccess()
                ? JOptionPane.INFORMATION_MESSAGE
                : JOptionPane.WARNING_MESSAGE);
    }

    private Map<String, Object> promptOperationParameterValues(OclRuleValidationResult ruleResult) {
        String target = formatRuleTarget(ruleResult);
        String input = JOptionPane.showInputDialog(this,
                "Enter operation parameter values for `" + target + "` as `name=value` pairs.\n"
                        + "Example: amount=100, note='ok', enabled=true",
                "Operation Parameters",
                JOptionPane.PLAIN_MESSAGE);
        if (input == null) {
            return null;
        }
        try {
            return OclParameterValuesParser.parse(input);
        } catch (IllegalArgumentException ex) {
            showScrollableMessage(ex.getMessage(), "Invalid Operation Parameters", JOptionPane.ERROR_MESSAGE);
            return null;
        }
    }

    private Map<String, Map<String, Object>> promptDocumentRuleParameterValues() {
        String input = JOptionPane.showInputDialog(this,
                "Optional operation inputs for document-level PRE rules.\n"
                        + "Use one rule per line as `Class::operation::rule => name=value`.\n"
                        + "Example:\n"
                        + "Family::addDaughter::UnnamedPre => name='Lisa'",
                "Document Rule Parameters",
                JOptionPane.PLAIN_MESSAGE);
        if (input == null) {
            return null;
        }
        try {
            return OclDocumentRuleParameterValuesParser.parse(input);
        } catch (IllegalArgumentException ex) {
            showScrollableMessage(ex.getMessage(), "Invalid Document Rule Parameters", JOptionPane.ERROR_MESSAGE);
            return null;
        }
    }

    private boolean looksLikeOperationPreDocument(String ocl) {
        String normalized = ocl.toLowerCase();
        return normalized.contains("context ") && normalized.contains("::") && normalized.contains("pre");
    }

    private static String formatRuleTarget(OclRuleValidationResult ruleResult) {
        StringBuilder builder = new StringBuilder();
        if (ruleResult.getContextClassName() != null && !ruleResult.getContextClassName().isBlank()) {
            builder.append(ruleResult.getContextClassName());
        }
        if (ruleResult.getOperationName() != null && !ruleResult.getOperationName().isBlank()) {
            if (builder.length() > 0) {
                builder.append("::");
            }
            builder.append(ruleResult.getOperationName());
        }
        if (ruleResult.getAttributeName() != null && !ruleResult.getAttributeName().isBlank()) {
            if (builder.length() > 0) {
                builder.append("::");
            }
            builder.append(ruleResult.getAttributeName());
        }
        if (ruleResult.getRuleName() != null && !ruleResult.getRuleName().isBlank()) {
            if (builder.length() > 0) {
                builder.append("::");
            }
            builder.append(ruleResult.getRuleName());
        }
        return builder.length() == 0 ? "<unnamed>" : builder.toString();
    }

    private static String statusLabel(OclRuleValidationResult ruleResult) {
        if (ruleResult.isSkipped()) {
            return "SKIP";
        }
        return ruleResult.isSuccess() ? "PASS" : "FAIL";
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

    private void previewRemoteDelta() {
        try {
            IncrementalSyncProposal proposal = workspaceService.previewIncrementalRemoteChanges(workspaceService.getOrCreateContext());
            refreshContent();
            if (proposal == null) {
                showScrollableMessage(workspaceService.getOrCreateContext().getLastApplyResult().message(),
                        "Preview Remote Delta", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            showScrollableMessage(proposal.toDisplayText(), "Preview Remote Delta", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            showScrollableMessage(ex.getMessage(), "Preview Remote Delta Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void applyRemoteDelta() {
        try {
            IncrementalApplyResult result = workspaceService.applyPendingIncrementalProposal(workspaceService.getOrCreateContext());
            refreshContent();
            showScrollableMessage(result.message(), "Apply Remote Delta",
                    result.status() == org.uet.dse.neo4jtgg.model.IncrementalSyncStatus.APPLIED
                            ? JOptionPane.INFORMATION_MESSAGE
                            : JOptionPane.WARNING_MESSAGE);
        } catch (Exception ex) {
            showScrollableMessage(ex.getMessage(), "Apply Remote Delta Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void discardRemoteProposal() {
        try {
            IncrementalApplyResult result = workspaceService.discardPendingIncrementalProposal(workspaceService.getOrCreateContext());
            refreshContent();
            showScrollableMessage(result.message(), "Discard Proposal", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            showScrollableMessage(ex.getMessage(), "Discard Proposal Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void refreshIncrementalBaseline() {
        try {
            IncrementalApplyResult result = workspaceService.refreshIncrementalBaseline(workspaceService.getOrCreateContext());
            refreshContent();
            showScrollableMessage(result.message(), "Refresh Baseline", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            showScrollableMessage(ex.getMessage(), "Refresh Baseline Error", JOptionPane.ERROR_MESSAGE);
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
