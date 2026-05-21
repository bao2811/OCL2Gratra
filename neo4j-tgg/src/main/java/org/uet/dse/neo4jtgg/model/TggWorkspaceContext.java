package org.uet.dse.neo4jtgg.model;

import org.tzi.use.gui.main.MainWindow;
import org.tzi.use.main.Session;

import java.io.File;
import java.util.EnumMap;
import java.util.Map;

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
    private String remoteChangeSummary = "No remote delta detected.";
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

    public String getRemoteChangeSummary() {
        return remoteChangeSummary;
    }

    public void setRemoteChangeSummary(String remoteChangeSummary) {
        this.remoteChangeSummary = remoteChangeSummary;
    }

    public TggWorkspaceDefinition getWorkspaceDefinition() {
        return workspaceDefinition;
    }

    public void setWorkspaceDefinition(TggWorkspaceDefinition workspaceDefinition) {
        this.workspaceDefinition = workspaceDefinition;
    }
}
