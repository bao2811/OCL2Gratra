package org.uet.dse.neo4jtgg.model;

import java.io.File;
import java.util.EnumMap;
import java.util.Map;

import org.tzi.use.gui.main.MainWindow;
import org.tzi.use.main.Session;
import org.uet.dse.neo4j.model.FullObjectSnapshot;

public class TggWorkspaceContext {

    private final Session session;
    private final MainWindow mainWindow;
    private File sourceFile;
    private File targetFile;
    private File tggFile;
    private File correspondenceFile;
    private final Map<WorkspaceSide, StringBuilder> logs = new EnumMap<>(WorkspaceSide.class);
    private final Map<WorkspaceSide, String> lastPreview = new EnumMap<>(WorkspaceSide.class);
    private final Map<WorkspaceSide, String> lastValidation = new EnumMap<>(WorkspaceSide.class);
    private final Map<WorkspaceSide, OclFileValidationResult> lastValidationResult = new EnumMap<>(WorkspaceSide.class);
    private final Map<WorkspaceSide, String> metamodelStatus = new EnumMap<>(WorkspaceSide.class);
    private final Map<WorkspaceSide, FullObjectSnapshot> lastSnapshots = new EnumMap<>(WorkspaceSide.class);
    private String remoteChangeSummary = "No remote delta detected.";
    private IncrementalSyncProposal currentProposal;
    private IncrementalApplyResult lastApplyResult = IncrementalApplyResult.of(IncrementalSyncStatus.IDLE, "No incremental action executed yet.");
    private long lastIncrementalDetectedAt;
    private long lastIncrementalAppliedAt;
    private long lastProcessedChangeToken;
    private String conflictSummary = "No conflicts.";
    private boolean pendingManualAction;
    private TggWorkspaceDefinition workspaceDefinition;

    public TggWorkspaceContext(Session session, MainWindow mainWindow) {
        this.session = session;
        this.mainWindow = mainWindow;
        for (WorkspaceSide side : WorkspaceSide.values()) {
            logs.put(side, new StringBuilder());
            lastPreview.put(side, "");
            lastValidation.put(side, "");
            lastValidationResult.put(side, null);
            metamodelStatus.put(side, "Not checked.");
        }
    }

    public Session getSession() {
        return session;
    }

    public MainWindow getMainWindow() {
        return mainWindow;
    }

    public File getSourceFile() {
        return sourceFile;
    }

    public void setSourceFile(File sourceFile) {
        this.sourceFile = sourceFile;
    }

    public File getTargetFile() {
        return targetFile;
    }

    public void setTargetFile(File targetFile) {
        this.targetFile = targetFile;
    }

    public File getTggFile() {
        return tggFile;
    }

    public void setTggFile(File tggFile) {
        this.tggFile = tggFile;
    }

    public File getCorrespondenceFile() {
        return correspondenceFile;
    }

    public void setCorrespondenceFile(File correspondenceFile) {
        this.correspondenceFile = correspondenceFile;
    }

    public void appendLog(WorkspaceSide side, String text) {
        logs.get(side).append(text).append('\n');
    }

    public String getLog(WorkspaceSide side) {
        return logs.get(side).toString();
    }

    public String getLastPreview(WorkspaceSide side) {
        return lastPreview.get(side);
    }

    public void setLastPreview(WorkspaceSide side, String preview) {
        lastPreview.put(side, preview);
    }

    public String getLastValidation(WorkspaceSide side) {
        return lastValidation.get(side);
    }

    public void setLastValidation(WorkspaceSide side, String validation) {
        lastValidation.put(side, validation);
        lastValidationResult.put(side, null);
    }

    public OclFileValidationResult getLastValidationResult(WorkspaceSide side) {
        return lastValidationResult.get(side);
    }

    public void setLastValidationResult(WorkspaceSide side, OclFileValidationResult result) {
        lastValidationResult.put(side, result);
        lastValidation.put(side, result != null ? result.toDisplayText() : "");
    }

    public String getMetamodelStatus(WorkspaceSide side) {
        return metamodelStatus.get(side);
    }

    public void setMetamodelStatus(WorkspaceSide side, String status) {
        metamodelStatus.put(side, status);
    }

    public FullObjectSnapshot getLastSnapshot(WorkspaceSide side) {
        return lastSnapshots.get(side);
    }

    public void setLastSnapshot(WorkspaceSide side, FullObjectSnapshot snapshot) {
        lastSnapshots.put(side, snapshot);
    }

    public void clearLastSnapshots() {
        lastSnapshots.clear();
    }

    public String getRemoteChangeSummary() {
        return remoteChangeSummary;
    }

    public void setRemoteChangeSummary(String remoteChangeSummary) {
        this.remoteChangeSummary = remoteChangeSummary;
    }

    public IncrementalSyncProposal getCurrentProposal() {
        return currentProposal;
    }

    public void setCurrentProposal(IncrementalSyncProposal currentProposal) {
        this.currentProposal = currentProposal;
    }

    public IncrementalApplyResult getLastApplyResult() {
        return lastApplyResult;
    }

    public void setLastApplyResult(IncrementalApplyResult lastApplyResult) {
        this.lastApplyResult = lastApplyResult;
    }

    public long getLastIncrementalDetectedAt() {
        return lastIncrementalDetectedAt;
    }

    public void setLastIncrementalDetectedAt(long lastIncrementalDetectedAt) {
        this.lastIncrementalDetectedAt = lastIncrementalDetectedAt;
    }

    public long getLastIncrementalAppliedAt() {
        return lastIncrementalAppliedAt;
    }

    public void setLastIncrementalAppliedAt(long lastIncrementalAppliedAt) {
        this.lastIncrementalAppliedAt = lastIncrementalAppliedAt;
    }

    public long getLastProcessedChangeToken() {
        return lastProcessedChangeToken;
    }

    public void setLastProcessedChangeToken(long lastProcessedChangeToken) {
        this.lastProcessedChangeToken = lastProcessedChangeToken;
    }

    public String getConflictSummary() {
        return conflictSummary;
    }

    public void setConflictSummary(String conflictSummary) {
        this.conflictSummary = conflictSummary;
    }

    public boolean isPendingManualAction() {
        return pendingManualAction;
    }

    public void setPendingManualAction(boolean pendingManualAction) {
        this.pendingManualAction = pendingManualAction;
    }

    public TggWorkspaceDefinition getWorkspaceDefinition() {
        return workspaceDefinition;
    }

    public void setWorkspaceDefinition(TggWorkspaceDefinition workspaceDefinition) {
        this.workspaceDefinition = workspaceDefinition;
    }
}
