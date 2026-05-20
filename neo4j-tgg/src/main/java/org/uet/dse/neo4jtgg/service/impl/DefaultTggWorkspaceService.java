package org.uet.dse.neo4jtgg.service.impl;

import org.tzi.use.api.UseApiException;
import org.tzi.use.api.UseModelApi;
import org.tzi.use.gui.main.MainWindow;
import org.neo4j.driver.Session;
import org.neo4j.driver.Values;
import org.uet.dse.neo4j.manager.Neo4jDriverManager;
import org.uet.dse.neo4j.model.FullObjectSnapshot;
import org.uet.dse.neo4j.sync.model.CoreModelPushService;
import org.uet.dse.neo4jtgg.engine.TransformationOptions;
import org.uet.dse.neo4jtgg.engine.TransformationReport;
import org.uet.dse.neo4jtgg.model.GuardReport;
import org.uet.dse.neo4jtgg.model.ImportBatch;
import org.uet.dse.neo4jtgg.model.ImportLinkSpec;
import org.uet.dse.neo4jtgg.model.ImportObjectSpec;
import org.uet.dse.neo4jtgg.model.TggRuleInfo;
import org.uet.dse.neo4jtgg.model.TggWorkspaceContext;
import org.uet.dse.neo4jtgg.model.TggWorkspaceDefinition;
import org.uet.dse.neo4jtgg.model.WorkspaceSide;
import org.uet.dse.neo4jtgg.service.ChangeGuardService;
import org.uet.dse.neo4jtgg.service.Neo4jChangeFeedService;
import org.uet.dse.neo4jtgg.service.OclValidationService;
import org.uet.dse.neo4jtgg.service.TggExecutionService;
import org.uet.dse.neo4jtgg.service.TggWorkspaceService;
import org.uet.dse.neo4jtgg.service.XmiImportAdapter;
import org.uet.dse.neo4jtgg.ui.EmbeddedPanelView;
import org.uet.dse.neo4jtgg.ui.RuntimeStatusPanel;
import org.uet.dse.neo4jtgg.ui.WorkspaceGraphView;

import javax.swing.JFrame;
import javax.swing.WindowConstants;
import java.io.File;
import java.io.PrintWriter;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.EnumMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.Map;

public class DefaultTggWorkspaceService implements TggWorkspaceService {
    private static final DefaultTggWorkspaceService INSTANCE = new DefaultTggWorkspaceService();

    public static DefaultTggWorkspaceService getInstance() {
        return INSTANCE;
    }

    private final XmiImportAdapter xmiImportAdapter = new Families2PersonsXmiImportAdapter();
    private final ChangeGuardService changeGuardService = new DefaultChangeGuardService();
    private final OclValidationService oclValidationService = new DefaultOclValidationService();
    private final Neo4jChangeFeedService changeFeedService = new PollingNeo4jChangeFeedService(changeGuardService);
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
        File base = new File("rtl/examples/Families2Persons");
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
        materializeCorrespondencesIfSupported(context);
        context.appendLog(WorkspaceSide.CORRESPONDENCE,
                "Registered TGG workspace `" + definition.getTransformationName() + "` for Neo4j-first runtime.");
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
        changeFeedService.applyRemoteProposal(context);
        materializeCorrespondencesIfSupported(context);
        refreshViews();
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
        return link.getAssociationName() + link.getEndpointNames().stream()
                .map(endpoint -> "_" + endpoint)
                .collect(Collectors.joining());
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
