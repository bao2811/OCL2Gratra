package org.uet.dse.neo4jtgg.service.impl;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.PrintWriter;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.swing.JFrame;
import javax.swing.WindowConstants;

import org.neo4j.driver.Session;
import org.neo4j.driver.Values;
import org.tzi.use.api.UseApiException;
import org.tzi.use.api.UseModelApi;
import org.tzi.use.gui.main.MainWindow;
import org.uet.dse.neo4j.manager.Neo4jDriverManager;
import org.uet.dse.neo4j.model.FullObjectSnapshot;
import org.uet.dse.neo4j.sync.model.CoreModelPushService;
import org.uet.dse.neo4jtgg.engine.IncrementalSyncEngine;
import org.uet.dse.neo4jtgg.engine.TransformationDirection;
import org.uet.dse.neo4jtgg.engine.TransformationMode;
import org.uet.dse.neo4jtgg.engine.TransformationOptions;
import org.uet.dse.neo4jtgg.engine.TransformationReport;
import org.uet.dse.neo4jtgg.model.GuardReport;
import org.uet.dse.neo4jtgg.model.ImpactAnalysisResult;
import org.uet.dse.neo4jtgg.model.ImportBatch;
import org.uet.dse.neo4jtgg.model.ImportLinkSpec;
import org.uet.dse.neo4jtgg.model.ImportObjectSpec;
import org.uet.dse.neo4jtgg.model.IncrementalApplyResult;
import org.uet.dse.neo4jtgg.model.IncrementalSyncProposal;
import org.uet.dse.neo4jtgg.model.IncrementalSyncStatus;
import org.uet.dse.neo4jtgg.model.ModelDelta;
import org.uet.dse.neo4jtgg.model.NormalizedChangeSet;
import org.uet.dse.neo4jtgg.model.TggRuleInfo;
import org.uet.dse.neo4jtgg.model.TggWorkspaceContext;
import org.uet.dse.neo4jtgg.model.TggWorkspaceDefinition;
import org.uet.dse.neo4jtgg.model.WorkspaceSide;
import org.uet.dse.neo4jtgg.model.WorkspaceMutationBatch;
import org.uet.dse.neo4jtgg.service.ChangeGuardService;
import org.uet.dse.neo4jtgg.service.IncrementalImpactAnalysisService;
import org.uet.dse.neo4jtgg.service.Neo4jChangeFeedService;
import org.uet.dse.neo4jtgg.service.OclValidationService;
import org.uet.dse.neo4jtgg.service.TggExecutionService;
import org.uet.dse.neo4jtgg.service.TggWorkspaceService;
import org.uet.dse.neo4jtgg.service.WorkspaceChangeDetector;
import org.uet.dse.neo4jtgg.service.XmiImportAdapter;
import org.uet.dse.neo4jtgg.ui.EmbeddedPanelView;
import org.uet.dse.neo4jtgg.ui.RuntimeStatusPanel;
import org.uet.dse.neo4jtgg.ui.WorkspaceGraphView;

public class DefaultTggWorkspaceService implements TggWorkspaceService {

    private static final DefaultTggWorkspaceService INSTANCE = new DefaultTggWorkspaceService();

    public static DefaultTggWorkspaceService getInstance() {
        return INSTANCE;
    }

    private final XmiImportAdapter xmiImportAdapter = new GenericXmiImportAdapter();
    private final ChangeGuardService changeGuardService = new DefaultChangeGuardService();
    private final OclValidationService oclValidationService = new DefaultOclValidationService();
    private final Neo4jChangeFeedService changeFeedService = new PollingNeo4jChangeFeedService(changeGuardService);
    private final WorkspaceChangeDetector changeDetector = new DefaultWorkspaceChangeDetector();
    private final IncrementalImpactAnalysisService impactAnalysisService = new DefaultIncrementalImpactAnalysisService();
    private final Neo4jTggWorkspaceLoader workspaceLoader = new Neo4jTggWorkspaceLoader();
    private final Neo4jMetamodelStateService metamodelStateService = new Neo4jMetamodelStateService();
    private final Neo4jWorkspaceRuntimeService runtimeService = Neo4jWorkspaceRuntimeService.getInstance();
    private final RuleDrivenCorrMaterializer corrMaterializer = new RuleDrivenCorrMaterializer();
    private final Neo4jUseMirrorService mirrorService = new Neo4jUseMirrorService();
    private final TggExecutionService executionService = new DefaultTggExecutionService();
    private final Map<WorkspaceSide, WorkspaceGraphView> openViews = new EnumMap<>(WorkspaceSide.class);
    private final Map<WorkspaceSide, JFrame> openViewWindows = new EnumMap<>(WorkspaceSide.class);
    private TggWorkspaceContext context;
    private JFrame runtimeWindow;
    private RuntimeStatusPanel runtimeStatusPanel;

    private DefaultTggWorkspaceService() {
    }

    @Override
    public TggWorkspaceContext getOrCreateContext() {
        if (context == null) {
            throw new IllegalStateException("Context not initialized yet.");
        }
        return context;
    }

    @Override
    public TggWorkspaceContext createDefaultContext(TggWorkspaceContext context) {
        this.context = context;
        File base = new File("examples/families2person/metamodels");
        context.setSourceFile(new File(base, "Families.use"));
        context.setTargetFile(new File(base, "Persons.use"));
        context.setTggFile(new File(base, "F2PForward.tgg"));
        return context;
    }

    @Override
    public void loadWorkspace(TggWorkspaceContext context) throws Exception {
        this.context = context;
        MainWindow mainWindow = context.getMainWindow();
        PrintWriter logWriter = mainWindow.logWriter();
        ensureNeo4jConnected();
        suppressLegacySyncRuntime(context);
        TggWorkspaceDefinition definition = workspaceLoader.load(context, logWriter);
        context.setWorkspaceDefinition(definition);

        context.appendLog(WorkspaceSide.SOURCE, "Loaded source model from " + context.getSourceFile().getAbsolutePath());
        context.appendLog(WorkspaceSide.CORRESPONDENCE, "Loaded TGG rules from " + context.getTggFile().getAbsolutePath());
        context.appendLog(WorkspaceSide.TARGET, "Loaded target model from " + context.getTargetFile().getAbsolutePath());
        context.appendLog(WorkspaceSide.CORRESPONDENCE,
                "Disabled legacy Neo4j real-time USE sync for this Neo4j-first TGG workspace.");
        if (context.getCorrespondenceFile() != null) {
            context.appendLog(WorkspaceSide.CORRESPONDENCE,
                    "Correspondence snapshot placeholder registered: " + context.getCorrespondenceFile().getAbsolutePath());
        }

        syncMetamodelIfMissing(context, WorkspaceSide.SOURCE, definition.getSourceModel(), definition.getClassNames(WorkspaceSide.SOURCE));
        syncMetamodelIfMissing(context, WorkspaceSide.CORRESPONDENCE, definition.getCorrespondenceModel(), definition.getClassNames(WorkspaceSide.CORRESPONDENCE));
        syncMetamodelIfMissing(context, WorkspaceSide.TARGET, definition.getTargetModel(), definition.getClassNames(WorkspaceSide.TARGET));
        metamodelStateService.connectTggMetamodelLayers(definition);
        context.appendLog(WorkspaceSide.CORRESPONDENCE,
                "Restored metamodel-layer TGG connections on Neo4j between correspondence classes and source/target classes.");
        materializeCorrespondencesIfSupported(context);
        context.appendLog(WorkspaceSide.CORRESPONDENCE,
                "Registered TGG workspace `" + definition.getTransformationName() + "` for Neo4j-first runtime.");
        initializeSnapshotBaseline(context);
        refreshUseMirror(false);
    }

    @Override
    public void openGraphViews(TggWorkspaceContext context) {
        this.context = context;
        disposeOpenWindows();
        openViews.clear();
        openViewWindows.clear();

        Point baseLocation = context.getMainWindow().getLocationOnScreen();
        for (WorkspaceSide side : WorkspaceSide.values()) {
            WorkspaceGraphView view = new WorkspaceGraphView(this, side);
            openViews.put(side, view);
            JFrame frame = createExternalWindow(side.getDisplayName() + " Graph", view);
            frame.setSize(1120, 760);
            frame.setLocation(new Point(baseLocation.x + (side.ordinal() * 40), baseLocation.y + (side.ordinal() * 40)));
            frame.setVisible(true);
            openViewWindows.put(side, frame);
        }

        runtimeStatusPanel = new RuntimeStatusPanel(this);
        EmbeddedPanelView syncView = new EmbeddedPanelView(runtimeStatusPanel);
        runtimeWindow = createExternalWindow("Neo4j TGG Runtime", syncView);
        runtimeWindow.setSize(640, 280);
        runtimeWindow.setLocation(new Point(baseLocation.x + 140, baseLocation.y + 140));
        runtimeWindow.setVisible(true);

        changeFeedService.start(context, this::refreshViews);
        refreshViews();
    }

    @Override
    public String buildSnapshotText(TggWorkspaceContext context, WorkspaceSide side) {
        return runtimeService.renderSnapshot(context, side);
    }

    @Override
    public ImportBatch previewImport(TggWorkspaceContext context, WorkspaceSide side, File sourceFile) throws Exception {
        ensureMetamodelReadyForImport(context, side);
        ImportBatch batch = xmiImportAdapter.parse(context, side, sourceFile);
        GuardReport report = changeGuardService.validateImport(context, batch);

        StringBuilder preview = new StringBuilder();
        preview.append("Import file: ").append(sourceFile.getAbsolutePath()).append('\n');
        preview.append("Import phase: M0 instance import into existing Neo4j metamodel\n");
        preview.append("Metamodel link: imported objects will be attached to existing M1 classes/associations on Neo4j\n\n");
        preview.append("Objects to create/update: ").append(batch.getObjects().size()).append('\n');
        for (ImportObjectSpec objectSpec : batch.getObjects()) {
            preview.append("- ").append(objectSpec.getObjectName()).append(" : ").append(objectSpec.getClassName())
                    .append(" ").append(objectSpec.getAttributes()).append('\n');
        }
        preview.append("Links to create: ").append(batch.getLinks().size()).append('\n');
        for (ImportLinkSpec linkSpec : batch.getLinks()) {
            preview.append("- ").append(linkSpec.getAssociationName()).append(" ")
                    .append(linkSpec.getEndpointNames()).append('\n');
        }
        preview.append('\n').append(report.toDisplayText());
        context.setLastPreview(side, preview.toString());
        context.appendLog(side, "Prepared XMI preview for " + sourceFile.getName());
        return batch;
    }

    @Override
    public void applyImport(TggWorkspaceContext context, ImportBatch batch) throws Exception {
        ensureMetamodelReadyForImport(context, batch.getSide());
        GuardReport report = changeGuardService.validateImport(context, batch);
        if (!report.isAllowed()) {
            throw new IllegalStateException(report.toDisplayText());
        }
        runtimeService.applyImport(context, batch);
        context.appendLog(batch.getSide(), "Applied import batch from " + batch.getSourceFile().getName());
        context.appendLog(batch.getSide(), "Import batch was written directly to Neo4j as M0 instances linked to the existing M1 metamodel.");
        verifyImportVisibleInNeo4j(context, batch);
        context.appendLog(WorkspaceSide.CORRESPONDENCE,
                "Skipped automatic correspondence materialization after import to preserve imported M0 instances.");
        refreshUseMirror(false);
        refreshViews();
        scheduleDelayedImportVerification(context, batch);
    }

    @Override
    public void refreshViews() {
        for (WorkspaceGraphView view : openViews.values()) {
            view.refreshContent();
        }
        if (runtimeStatusPanel != null) {
            runtimeStatusPanel.refreshStatus();
        }
    }

    @Override
    public void insertSelectedRuleText(TggWorkspaceContext context, WorkspaceSide side, String ruleName) {
        TggWorkspaceDefinition definition = context.getWorkspaceDefinition();
        if (definition == null) {
            return;
        }
        TggRuleInfo rule = definition.getRuleByName(ruleName);
        if (rule == null) {
            return;
        }
        String constraints = rule.getSideConstraints(side);
        context.setLastValidation(side, constraints);
        if (openViews.containsKey(side)) {
            openViews.get(side).insertOclText(constraints);
        }
    }

    @Override
    public TransformationReport previewForwardTransformation(TggWorkspaceContext context) {
        return handleTransformationReport(context, executionService.preview(context, TransformationOptions.previewForward()));
    }

    @Override
    public TransformationReport runForwardTransformation(TggWorkspaceContext context) {
        return handleTransformationReport(context, executionService.run(context, TransformationOptions.applyForward()));
    }

    @Override
    public TransformationReport previewBackwardTransformation(TggWorkspaceContext context) {
        return handleTransformationReport(context, executionService.preview(context, TransformationOptions.previewBackward()));
    }

    @Override
    public TransformationReport runBackwardTransformation(TggWorkspaceContext context) {
        return handleTransformationReport(context, executionService.run(context, TransformationOptions.applyBackward()));
    }

    public OclValidationService getOclValidationService() {
        return oclValidationService;
    }

    public ChangeGuardService getChangeGuardService() {
        return changeGuardService;
    }

    public File getDefaultImportFile(WorkspaceSide side) {
        File modelsDir = new File("examples/families2person/src/models");
        if (side == WorkspaceSide.SOURCE) {
            return new File(modelsDir, "Families.xmi");
        }
        if (side == WorkspaceSide.TARGET) {
            return new File(modelsDir, "Persons.xmi");
        }
        return modelsDir;
    }

    public void applyRemoteChanges() {
        previewIncrementalRemoteChanges(context);
    }

    @Override
    public IncrementalSyncProposal previewIncrementalRemoteChanges(TggWorkspaceContext context) {
        ensureNeo4jConnected();
        if (context == null) {
            throw new IllegalStateException("Workspace context is not initialized yet.");
        }
        this.context = context;

        FullObjectSnapshot currentSource = runtimeService.loadSnapshot(context, WorkspaceSide.SOURCE);
        FullObjectSnapshot currentTarget = runtimeService.loadSnapshot(context, WorkspaceSide.TARGET);
        FullObjectSnapshot corrSnapshot = runtimeService.loadSnapshot(context, WorkspaceSide.CORRESPONDENCE);

        FullObjectSnapshot previousSource = context.getLastSnapshot(WorkspaceSide.SOURCE);
        FullObjectSnapshot previousTarget = context.getLastSnapshot(WorkspaceSide.TARGET);

        if (previousSource == null || previousTarget == null) {
            updateSnapshotBaseline(context, currentSource, currentTarget, corrSnapshot);
            context.appendLog(WorkspaceSide.CORRESPONDENCE,
                    "Incremental baseline initialized. No sync applied on first run.");
            context.setCurrentProposal(null);
            context.setLastApplyResult(IncrementalApplyResult.of(
                    IncrementalSyncStatus.BASELINE_REFRESHED,
                    "Initialized incremental baseline from current Neo4j snapshots."));
            refreshViews();
            return null;
        }

        NormalizedChangeSet sourceChanges = changeDetector.detectSourceChanges(context, previousSource, currentSource);
        NormalizedChangeSet targetChanges = changeDetector.detectTargetChanges(context, previousTarget, currentTarget);
        ModelDelta sourceDelta = sourceChanges.delta();
        ModelDelta targetDelta = targetChanges.delta();

        if (sourceDelta.isEmpty() && targetDelta.isEmpty()) {
            context.appendLog(WorkspaceSide.CORRESPONDENCE, "Incremental sync: no changes detected.");
            context.setCurrentProposal(null);
            context.setPendingManualAction(false);
            context.setConflictSummary("No conflicts.");
            context.setLastApplyResult(IncrementalApplyResult.of(
                    IncrementalSyncStatus.IDLE,
                    "No incremental changes detected."));
            refreshViews();
            return null;
        }

        if (!sourceDelta.isEmpty() && !targetDelta.isEmpty()) {
            IncrementalSyncProposal proposal = new IncrementalSyncProposal(
                    TransformationDirection.FORWARD,
                    WorkspaceSide.CORRESPONDENCE,
                    sourceChanges,
                    sourceDelta,
                    new ImpactAnalysisResult(TransformationDirection.FORWARD, WorkspaceSide.CORRESPONDENCE,
                            Set.of(), Set.of(), Set.of(), Set.of(), Set.of(),
                            java.util.Map.of(), java.util.Map.of(), null,
                            List.of("Both source and target changed since the last baseline."), true, true),
                    new WorkspaceMutationBatch(WorkspaceSide.CORRESPONDENCE, context.getTggFile()),
                    List.of(),
                    List.of("Both source and target changed since the last baseline. Run a manual transform or discard the proposal."),
                    true,
                    true,
                    System.currentTimeMillis());
            rememberProposal(context, proposal, "Bi-directional incremental conflict detected.");
            refreshViews();
            return proposal;
        }

        IncrementalSyncProposal proposal;
        if (!sourceDelta.isEmpty()) {
            proposal = buildIncrementalProposal(context, sourceChanges, currentSource, currentTarget, corrSnapshot,
                    TransformationDirection.FORWARD);
        } else {
            proposal = buildIncrementalProposal(context, targetChanges, currentSource, currentTarget, corrSnapshot,
                    TransformationDirection.BACKWARD);
        }
        rememberProposal(context, proposal, proposal != null ? proposal.summary() : "No incremental proposal created.");
        refreshViews();
        return proposal;
    }

    @Override
    public IncrementalApplyResult applyPendingIncrementalProposal(TggWorkspaceContext context) {
        ensureNeo4jConnected();
        IncrementalSyncProposal proposal = context.getCurrentProposal();
        if (proposal == null) {
            IncrementalApplyResult result = IncrementalApplyResult.of(IncrementalSyncStatus.IDLE, "No pending incremental proposal.");
            context.setLastApplyResult(result);
            refreshViews();
            return result;
        }
        if (proposal.blocked()) {
            IncrementalApplyResult result = IncrementalApplyResult.of(IncrementalSyncStatus.BLOCKED,
                    "Incremental proposal is blocked because both sides changed.");
            context.setLastApplyResult(result);
            refreshViews();
            return result;
        }
        if (proposal.requiresManualTransform()) {
            IncrementalApplyResult result = IncrementalApplyResult.of(IncrementalSyncStatus.MANUAL_TRANSFORM_REQUIRED,
                    "Incremental proposal requires a manual forward/backward transform.");
            context.setLastApplyResult(result);
            refreshViews();
            return result;
        }
        GuardReport report = runtimeService.validateMutation(context, proposal.mutationBatch());
        if (!report.isAllowed()) {
            IncrementalApplyResult result = IncrementalApplyResult.of(IncrementalSyncStatus.FAILED,
                    "Incremental proposal failed validation: " + report.toDisplayText());
            context.setLastApplyResult(result);
            context.appendLog(WorkspaceSide.CORRESPONDENCE, result.message());
            refreshViews();
            return result;
        }

        runtimeService.applyMutation(context, proposal.mutationBatch());
        int corrCreated = corrMaterializer.materialize(context);
        refreshUseMirror(false);
        refreshIncrementalBaseline(context);
        context.setCurrentProposal(null);
        context.setLastIncrementalAppliedAt(System.currentTimeMillis());
        context.setPendingManualAction(false);
        context.setConflictSummary("No conflicts.");
        IncrementalApplyResult result = IncrementalApplyResult.of(IncrementalSyncStatus.APPLIED,
                "Applied incremental proposal and materialized " + corrCreated + " correspondence object(s).");
        context.setLastApplyResult(result);
        context.appendLog(proposal.mutationBatch().getReceiverSide(), result.message());
        refreshViews();
        return result;
    }

    @Override
    public IncrementalApplyResult discardPendingIncrementalProposal(TggWorkspaceContext context) {
        context.setCurrentProposal(null);
        context.setPendingManualAction(false);
        IncrementalApplyResult result = refreshIncrementalBaseline(context);
        IncrementalApplyResult discarded = IncrementalApplyResult.of(IncrementalSyncStatus.DISCARDED,
                "Discarded pending incremental proposal and refreshed baseline.");
        context.setLastApplyResult(discarded);
        refreshViews();
        return discarded;
    }

    @Override
    public IncrementalApplyResult refreshIncrementalBaseline(TggWorkspaceContext context) {
        ensureNeo4jConnected();
        FullObjectSnapshot source = runtimeService.loadSnapshot(context, WorkspaceSide.SOURCE);
        FullObjectSnapshot target = runtimeService.loadSnapshot(context, WorkspaceSide.TARGET);
        FullObjectSnapshot corr = runtimeService.loadSnapshot(context, WorkspaceSide.CORRESPONDENCE);
        updateSnapshotBaseline(context, source, target, corr);
        context.setCurrentProposal(null);
        context.setConflictSummary("No conflicts.");
        context.setPendingManualAction(false);
        IncrementalApplyResult result = IncrementalApplyResult.of(IncrementalSyncStatus.BASELINE_REFRESHED,
                "Refreshed incremental baseline from current Neo4j snapshots.");
        context.setLastApplyResult(result);
        refreshViews();
        return result;
    }

    public void pushModelToNeo4j() {
        ensureNeo4jConnected();
        context.appendLog(WorkspaceSide.CORRESPONDENCE,
                "Push Model is disabled in the Neo4j-first TGG runtime. "
                + "Run Workspace already imports or reuses M1, and pushing M1 again can remove imported M0 instances.");
        refreshViews();
    }

    public void pushObjectsToNeo4j(boolean manual) {
        ensureNeo4jConnected();
        context.appendLog(WorkspaceSide.SOURCE,
                "USE push is disabled in Neo4j-first runtime. Write objects through Neo4j-backed import/actions instead.");
        refreshViews();
    }

    public void pullObjectsFromNeo4j(boolean manual) {
        refreshUseMirror(manual);
    }

    public void refreshUseMirror(boolean manual) {
        ensureNeo4jConnected();
        if (context == null || context.getSession() == null) {
            if (context != null) {
                context.appendLog(WorkspaceSide.CORRESPONDENCE,
                        "Skipped USE mirror refresh because no USE session is attached to this Neo4j-only runtime.");
            }
            refreshViews();
            return;
        }
        try {
            mirrorService.refreshMirror(context);
        } catch (UseApiException exception) {
            throw new IllegalStateException("Failed to rebuild USE mirror: " + exception.getMessage(), exception);
        }
        refreshViews();
    }

    public String buildRuntimeStatusText() {
        StringBuilder sb = new StringBuilder();
        sb.append("Neo4j TGG Runtime\n\n");
        sb.append("Neo4j is the source of truth.\n");
        sb.append("USE is a reflected mirror rebuilt from Neo4j snapshots on demand.\n\n");

        if (context == null) {
            sb.append("Workspace: not initialized.");
            return sb.toString();
        }

        sb.append("Workspace files\n");
        sb.append("- Source: ").append(context.getSourceFile() != null ? context.getSourceFile().getAbsolutePath() : "<unset>").append('\n');
        sb.append("- Target: ").append(context.getTargetFile() != null ? context.getTargetFile().getAbsolutePath() : "<unset>").append('\n');
        sb.append("- TGG: ").append(context.getTggFile() != null ? context.getTggFile().getAbsolutePath() : "<unset>").append('\n');
        sb.append("- Corr snapshot: ").append(context.getCorrespondenceFile() != null ? context.getCorrespondenceFile().getAbsolutePath() : "<none>").append('\n');
        sb.append('\n');

        Neo4jDriverManager driverManager = Neo4jDriverManager.getInstance();
        sb.append("Neo4j connection\n");
        sb.append("- URI: ").append(driverManager != null ? driverManager.getUri() : "<not connected>").append('\n');
        sb.append("- Database: ").append(driverManager != null ? driverManager.getActiveDatabase() : "<not connected>").append('\n');
        sb.append('\n');

        sb.append("Metamodel status\n");
        for (WorkspaceSide side : WorkspaceSide.values()) {
            sb.append("- ").append(side.getDisplayName()).append(": ").append(context.getMetamodelStatus(side)).append('\n');
        }
        sb.append('\n');

        sb.append("Runtime notes\n");
        sb.append("- Run Workspace imports or reuses only M2/M1 metamodel data on Neo4j.\n");
        sb.append("- XMI/XML import creates M0 instances and links them to the existing metamodel.\n");
        sb.append("- M0 object nodes are stored as (:ClassName {use_id: ...})-[:ObjectInstanceOf]->(:Class), not as direct children of ManageModel.\n");
        sb.append("- Import batches write directly to Neo4j.\n");
        sb.append("- Graph views read source/corr/target snapshots directly from Neo4j.\n");
        sb.append("- 'Refresh USE Mirror' rebuilds the USE session from the current Neo4j snapshot.\n");
        sb.append("- 'Push Objects' from USE is intentionally disabled in Neo4j-first runtime.\n");
        sb.append('\n');
        sb.append("Incremental status\n");
        sb.append("- Last result: ").append(context.getLastApplyResult().status().getLabel())
                .append(" | ").append(context.getLastApplyResult().message()).append('\n');
        sb.append("- Last change token: ").append(context.getLastProcessedChangeToken()).append('\n');
        sb.append("- Conflict summary: ").append(context.getConflictSummary()).append('\n');
        sb.append("- Manual action pending: ").append(context.isPendingManualAction()).append('\n');
        if (context.getCurrentProposal() != null) {
            sb.append("- Pending proposal: ").append(context.getCurrentProposal().summary()).append('\n');
            if (context.getCurrentProposal().changeSet() != null) {
                sb.append("- Change source: ").append(context.getCurrentProposal().changeSet().sourceKind()).append('\n');
                sb.append("- Detected events: ").append(context.getCurrentProposal().changeSet().totalEvents()).append('\n');
            }
            if (context.getCurrentProposal().impactAnalysis() != null) {
                sb.append("- Affected rules: ").append(context.getCurrentProposal().impactAnalysis().affectedRuleNames()).append('\n');
            }
        }
        sb.append('\n');
        sb.append("Remote change feed\n");
        sb.append(context.getRemoteChangeSummary());
        return sb.toString();
    }

    private void ensureNeo4jConnected() {
        Neo4jDriverManager driverManager = Neo4jDriverManager.getInstance();
        if (driverManager == null || !driverManager.isConnected()) {
            throw new IllegalStateException(
                    "Neo4j connection is required. Open 'Plugins -> Neo4j Plugin phase 2 -> Open connection' before running the TGG workspace.");
        }
    }

    private void initializeSnapshotBaseline(TggWorkspaceContext context) {
        try {
            FullObjectSnapshot source = runtimeService.loadSnapshot(context, WorkspaceSide.SOURCE);
            FullObjectSnapshot target = runtimeService.loadSnapshot(context, WorkspaceSide.TARGET);
            FullObjectSnapshot corr = runtimeService.loadSnapshot(context, WorkspaceSide.CORRESPONDENCE);
            updateSnapshotBaseline(context, source, target, corr);
            context.appendLog(WorkspaceSide.CORRESPONDENCE, "Initialized incremental snapshot baseline.");
            context.setLastApplyResult(IncrementalApplyResult.of(
                    IncrementalSyncStatus.BASELINE_REFRESHED,
                    "Initialized incremental baseline."));
        } catch (Exception exception) {
            context.appendLog(WorkspaceSide.CORRESPONDENCE,
                    "Failed to initialize incremental baseline: " + exception.getMessage());
        }
    }

    private void updateSnapshotBaseline(TggWorkspaceContext context,
            FullObjectSnapshot source,
            FullObjectSnapshot target,
            FullObjectSnapshot corr) {
        context.setLastSnapshot(WorkspaceSide.SOURCE, source);
        context.setLastSnapshot(WorkspaceSide.TARGET, target);
        context.setLastSnapshot(WorkspaceSide.CORRESPONDENCE, corr);
    }

    private IncrementalSyncProposal buildIncrementalProposal(TggWorkspaceContext context,
            NormalizedChangeSet changeSet,
            FullObjectSnapshot sourceSnapshot,
            FullObjectSnapshot targetSnapshot,
            FullObjectSnapshot corrSnapshot,
            TransformationDirection direction) {
        ModelDelta delta = changeSet.delta();
        ImpactAnalysisResult impact = impactAnalysisService.analyze(context, changeSet, sourceSnapshot, targetSnapshot, corrSnapshot);
        TransformationReport report = new TransformationReport(direction, TransformationMode.PREVIEW);
        report.addInfo("Incremental delta detected on " + delta.side().getDisplayName() + ".");
        report.addInfo(changeSet.toDisplayText());
        report.addInfo(impact.toDisplayText());

        IncrementalSyncEngine engine = new IncrementalSyncEngine(report);
        WorkspaceMutationBatch batch = direction == TransformationDirection.FORWARD
                ? engine.syncForward(context, delta, sourceSnapshot, targetSnapshot, corrSnapshot, impact)
                : engine.syncBackward(context, delta, sourceSnapshot, targetSnapshot, corrSnapshot, impact);
        return new IncrementalSyncProposal(
                direction,
                delta.side(),
                changeSet,
                delta,
                impact,
                batch,
                engine.getConflicts(),
                mergeWarnings(impact.warnings(), engine.getWarnings()),
                impact.requiresManualTransform() || engine.requiresManualTransform(),
                impact.blocked(),
                System.currentTimeMillis());
    }

    private List<String> mergeWarnings(List<String> first, List<String> second) {
        java.util.LinkedHashSet<String> merged = new java.util.LinkedHashSet<>();
        if (first != null) {
            merged.addAll(first);
        }
        if (second != null) {
            merged.addAll(second);
        }
        return List.copyOf(merged);
    }

    private void rememberProposal(TggWorkspaceContext context, IncrementalSyncProposal proposal, String logMessage) {
        context.setCurrentProposal(proposal);
        context.setLastIncrementalDetectedAt(System.currentTimeMillis());
        context.setPendingManualAction(proposal != null && (proposal.requiresManualTransform() || proposal.blocked()));
        context.setConflictSummary(proposal == null || proposal.conflicts().isEmpty()
                ? "No conflicts."
                : proposal.conflicts().size() + " conflict(s) detected.");
        context.setLastApplyResult(IncrementalApplyResult.of(
                proposal == null ? IncrementalSyncStatus.IDLE : proposal.status(),
                logMessage));
        context.setRemoteChangeSummary(proposal == null ? "No remote delta detected." : proposal.summary());
        if (proposal != null) {
            context.setLastPreview(proposal.mutationBatch().getReceiverSide(), proposal.toDisplayText());
        }
        context.appendLog(WorkspaceSide.CORRESPONDENCE, logMessage);
    }

    private void suppressLegacySyncRuntime(TggWorkspaceContext context) {
        String reason = "Legacy USE<->Neo4j sync is disabled while a Neo4j-first TGG workspace is active.";
        try {
            Class<?> guard = Class.forName("org.uet.dse.neo4j.sync.LegacySyncGuard");
            guard.getMethod("disable", String.class).invoke(null, reason);
            context.appendLog(WorkspaceSide.CORRESPONDENCE, reason);
        } catch (ReflectiveOperationException | LinkageError exception) {
            context.appendLog(WorkspaceSide.CORRESPONDENCE,
                    "Legacy sync guard is not available in the loaded neo4j plugin jar: " + exception.getMessage());
        }

        try {
            Class<?> realTimeService = Class.forName("org.uet.dse.neo4j.realtime.Neo4jRealTimeService");
            realTimeService.getMethod("stopIfRunning").invoke(null);
            context.appendLog(WorkspaceSide.CORRESPONDENCE, "Stopped legacy Neo4j real-time sync service.");
        } catch (ReflectiveOperationException | LinkageError exception) {
            context.appendLog(WorkspaceSide.CORRESPONDENCE,
                    "Legacy real-time service could not be stopped through the loaded neo4j plugin jar: " + exception.getMessage());
        }

        try {
            Class<?> syncConfig = Class.forName("org.uet.dse.neo4j.config.SyncConfig");
            Object config = syncConfig.getMethod("getInstance").invoke(null);
            syncConfig.getField("autoPushModelOnImport").setBoolean(config, false);
            syncConfig.getField("autoPushObjectOnChange").setBoolean(config, false);
            syncConfig.getField("autoPullModelOnChange").setBoolean(config, false);
            syncConfig.getField("autoPullObjectOnChange").setBoolean(config, false);
            context.appendLog(WorkspaceSide.CORRESPONDENCE,
                    "Disabled legacy auto push/pull sync flags for this USE process.");
        } catch (ReflectiveOperationException | LinkageError exception) {
            context.appendLog(WorkspaceSide.CORRESPONDENCE,
                    "Legacy sync config could not be updated: " + exception.getMessage());
        }
    }

    private TggWorkspaceDefinition requireWorkspaceDefinition() {
        TggWorkspaceDefinition definition = context != null ? context.getWorkspaceDefinition() : null;
        if (definition == null) {
            throw new IllegalStateException("Workspace metadata is not loaded yet.");
        }
        return definition;
    }

    private void syncMetamodelIfMissing(TggWorkspaceContext context, WorkspaceSide side,
            org.tzi.use.uml.mm.MModel model, java.util.Set<String> expectedClasses) {
        if (model == null) {
            context.setMetamodelStatus(side, "No model available.");
            return;
        }
        boolean alreadyImported = metamodelStateService.hasImportedMetamodel(model.name(), expectedClasses);
        if (alreadyImported) {
            String status = "Reused existing metamodel from Neo4j.";
            context.setMetamodelStatus(side, status);
            context.appendLog(side, status + " Model `" + model.name() + "` was already present.");
            return;
        }

        new CoreModelPushService(new UseModelApi(model)).pushModelToNeo4j();
        String status = "Imported metamodel to Neo4j.";
        context.setMetamodelStatus(side, status);
        context.appendLog(side, status + " Model `" + model.name() + "` was missing and has been created.");
    }

    private void materializeCorrespondencesIfSupported(TggWorkspaceContext context) {
        int created = corrMaterializer.materialize(context);
        if (created > 0) {
            context.appendLog(WorkspaceSide.CORRESPONDENCE,
                    "Materialized correspondence runtime on Neo4j with the rule-driven engine.");
        }
    }

    private TransformationReport handleTransformationReport(TggWorkspaceContext context, TransformationReport report) {
        context.setLastValidation(WorkspaceSide.CORRESPONDENCE, report.toDisplayText());
        context.appendLog(WorkspaceSide.CORRESPONDENCE,
                "Transformation " + report.getMode() + " " + report.getDirection() + " completed.");
        refreshViews();
        return report;
    }

    private void ensureMetamodelReadyForImport(TggWorkspaceContext context, WorkspaceSide side) {
        ensureNeo4jConnected();
        TggWorkspaceDefinition definition = context.getWorkspaceDefinition();
        if (definition == null || definition.getModel(side) == null) {
            throw new IllegalStateException(
                    "Workspace metadata for " + side.getDisplayName() + " is not loaded yet. Run Workspace first to import or reuse the metamodel on Neo4j.");
        }

        org.tzi.use.uml.mm.MModel model = definition.getModel(side);
        Set<String> expectedClasses = definition.getClassNames(side);
        boolean metamodelExists = metamodelStateService.hasImportedMetamodel(model.name(), expectedClasses);
        if (!metamodelExists) {
            context.appendLog(side,
                    "Metamodel `" + model.name() + "` was missing from " + describeNeo4jConnection()
                    + ". Re-importing M1 before XMI import.");
            syncMetamodelIfMissing(context, side, model, expectedClasses);
        } else {
            context.setMetamodelStatus(side, "Reused existing metamodel from Neo4j.");
        }
    }

    private void verifyImportVisibleInNeo4j(TggWorkspaceContext context, ImportBatch batch) {
        FullObjectSnapshot snapshot = runtimeService.loadSnapshot(context, batch.getSide());
        Set<String> expectedObjects = batch.getObjects().stream()
                .map(ImportObjectSpec::getObjectName)
                .collect(Collectors.toSet());
        Set<String> expectedLinks = batch.getLinks().stream()
                .map(this::buildLinkIdentity)
                .collect(Collectors.toSet());

        long visibleObjects = expectedObjects.stream().filter(snapshot.objects::containsKey).count();
        long visibleLinks = expectedLinks.stream().filter(snapshot.links::containsKey).count();
        context.appendLog(batch.getSide(),
                "Neo4j verification after import on " + describeNeo4jConnection()
                + ": objects " + visibleObjects + "/" + expectedObjects.size()
                + ", links " + visibleLinks + "/" + expectedLinks.size() + ".");
        RawImportVisibility rawVisibility = verifyRawImportVisibleInNeo4j(expectedObjects);
        context.appendLog(batch.getSide(),
                "Raw Neo4j M0 visibility: use_id nodes " + rawVisibility.visibleObjectCount()
                + "/" + expectedObjects.size()
                + (rawVisibility.sampleObjectIds().isEmpty() ? "" : ", sample " + rawVisibility.sampleObjectIds())
                + ".");

        if (rawVisibility.visibleObjectCount() < expectedObjects.size()) {
            Set<String> missing = expectedObjects.stream()
                    .filter(name -> !rawVisibility.visibleObjectIds().contains(name))
                    .collect(Collectors.toSet());
            throw new IllegalStateException("Import write finished, but raw Neo4j query cannot see "
                    + missing.size() + " object node(s) by use_id: " + missing
                    + ". Active connection is " + describeNeo4jConnection() + ".");
        }

        if (visibleObjects < expectedObjects.size()) {
            Set<String> missing = expectedObjects.stream()
                    .filter(name -> !snapshot.objects.containsKey(name))
                    .collect(Collectors.toSet());
            throw new IllegalStateException("Import write finished, but Neo4j snapshot cannot see "
                    + missing.size() + " object(s): " + missing
                    + ". Check that the metamodel belongs to the active Neo4j database and was created with the current schema.");
        }
    }

    private RawImportVisibility verifyRawImportVisibleInNeo4j(Set<String> expectedObjects) {
        if (expectedObjects.isEmpty()) {
            return new RawImportVisibility(Set.of(), List.of());
        }
        try (Session session = Neo4jDriverManager.getInstance().openSession()) {
            List<String> visibleIds = session.run(
                    "MATCH (o) WHERE o.use_id IN $objectIds RETURN o.use_id AS id ORDER BY id",
                    Values.parameters("objectIds", expectedObjects))
                    .list(record -> record.get("id").asString());
            return new RawImportVisibility(Set.copyOf(visibleIds), visibleIds.stream().limit(10).toList());
        }
    }

    private void scheduleDelayedImportVerification(TggWorkspaceContext context, ImportBatch batch) {
        Set<String> expectedObjects = batch.getObjects().stream()
                .map(ImportObjectSpec::getObjectName)
                .collect(Collectors.toSet());
        if (expectedObjects.isEmpty()) {
            return;
        }

        javax.swing.Timer timer = new javax.swing.Timer(5000, event -> {
            RawImportVisibility visibility = verifyRawImportVisibleInNeo4j(expectedObjects);
            context.appendLog(batch.getSide(),
                    "Delayed raw Neo4j M0 visibility after 5s: use_id nodes "
                    + visibility.visibleObjectCount() + "/" + expectedObjects.size()
                    + (visibility.sampleObjectIds().isEmpty() ? "" : ", sample " + visibility.sampleObjectIds())
                    + ".");
            if (visibility.visibleObjectCount() < expectedObjects.size()) {
                Set<String> missing = expectedObjects.stream()
                        .filter(name -> !visibility.visibleObjectIds().contains(name))
                        .collect(Collectors.toSet());
                context.appendLog(batch.getSide(),
                        "M0 objects disappeared or were not committed for " + missing.size()
                        + " object(s): " + missing
                        + ". Check legacy sync actions, manual cleanup queries, or another USE instance connected to "
                        + describeNeo4jConnection() + ".");
            }
            refreshViews();
        });
        timer.setRepeats(false);
        timer.start();
    }

    private String buildLinkIdentity(ImportLinkSpec link) {
        return link.getIdentity();
    }

    private String describeNeo4jConnection() {
        Neo4jDriverManager driverManager = Neo4jDriverManager.getInstance();
        if (driverManager == null) {
            return "Neo4j <not connected>";
        }
        return driverManager.getUri() + " database `" + driverManager.getActiveDatabase() + "`";
    }

    private record RawImportVisibility(Set<String> visibleObjectIds, List<String> sampleObjectIds) {

        int visibleObjectCount() {
            return visibleObjectIds.size();
        }
    }

    private JFrame createExternalWindow(String title, java.awt.Component content) {
        JFrame frame = new JFrame(title);
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        frame.add(content, BorderLayout.CENTER);
        frame.setMinimumSize(new Dimension(480, 240));
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (content instanceof org.tzi.use.gui.views.View view) {
                    view.detachModel();
                }
            }
        });
        return frame;
    }

    private void disposeOpenWindows() {
        for (JFrame frame : openViewWindows.values()) {
            if (frame != null) {
                frame.dispose();
            }
        }
        if (runtimeWindow != null) {
            runtimeWindow.dispose();
            runtimeWindow = null;
        }
    }

}
