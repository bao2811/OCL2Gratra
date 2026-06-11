package org.uet.dse.neo4jtgg.service.impl;

import org.junit.jupiter.api.Test;
import org.neo4j.driver.Session;
import org.tzi.use.api.UseModelApi;
import org.uet.dse.neo4j.manager.Neo4jDriverManager;
import org.uet.dse.neo4j.manager.SessionManager;
import org.uet.dse.neo4j.model.FullObjectSnapshot;
import org.uet.dse.neo4j.sync.model.CoreModelPushService;
import org.uet.dse.neo4jtgg.engine.CorrRuntimeTraceHelper;
import org.uet.dse.neo4jtgg.engine.TransformationDirection;
import org.uet.dse.neo4jtgg.engine.TransformationMode;
import org.uet.dse.neo4jtgg.engine.TransformationOptions;
import org.uet.dse.neo4jtgg.engine.TransformationReport;
import org.uet.dse.neo4jtgg.model.GuardReport;
import org.uet.dse.neo4jtgg.model.ImportObjectSpec;
import org.uet.dse.neo4jtgg.model.ImportBatch;
import org.uet.dse.neo4jtgg.model.IncrementalApplyResult;
import org.uet.dse.neo4jtgg.model.IncrementalSyncProposal;
import org.uet.dse.neo4jtgg.model.IncrementalSyncStatus;
import org.uet.dse.neo4jtgg.model.TggWorkspaceContext;
import org.uet.dse.neo4jtgg.model.TggWorkspaceDefinition;
import org.uet.dse.neo4jtgg.model.WorkspaceMutationBatch;
import org.uet.dse.neo4jtgg.model.WorkspaceSide;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class Neo4jFamilies2PersonsRuntimeIT {
    private static final String ENV_NEO4J_URI = "NEO4J_URI";
    private static final String ENV_NEO4J_USER = "NEO4J_USER";
    private static final String ENV_NEO4J_PASSWORD = "NEO4J_PASSWORD";
    private static final String ENV_NEO4J_DB = "NEO4J_DB";
    private static final String ENV_NEO4J_CREATE = "NEO4J_CREATE_DB";
    private static final String ENV_NEO4J_DELETE = "NEO4J_DELETE_ON_EXIT";
    private static final Path REPO_ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void importsFamiliesAndRunsForwardOnNeo4jRuntime() throws Exception {
        assumeTrue(ensureNeo4jConnected(),
                "Neo4j is not configured. Set NEO4J_URI, NEO4J_USER, NEO4J_PASSWORD (optional NEO4J_DB).");

        try (Session session = Neo4jDriverManager.getInstance().openSession()) {
            resetNeo4j(session);
        }

        TggWorkspaceContext context = prepareFamiliesWorkspace();
        Neo4jWorkspaceRuntimeService runtimeService = Neo4jWorkspaceRuntimeService.getInstance();

        FullObjectSnapshot sourceSnapshot = runtimeService.loadSnapshot(context, WorkspaceSide.SOURCE);
        assertEquals(11, sourceSnapshot.objects.size(), "Source import should create 11 M0 objects");
        assertEquals(10, sourceSnapshot.links.size(), "Source import should create 10 M0 links");

        DefaultTggExecutionService executionService = new DefaultTggExecutionService();
        TransformationReport report = executionService.run(context, TransformationOptions.applyForward());
        assertEquals(TransformationDirection.FORWARD, report.getDirection());
        assertEquals(TransformationMode.APPLY, report.getMode());
        assertFalse(report.getCreatedObjects().isEmpty(), "Forward runtime should create target objects");
        assertFalse(report.getCreatedCorrespondences().isEmpty(), "Forward runtime should create correspondence objects");

        FullObjectSnapshot targetSnapshot = runtimeService.loadSnapshot(context, WorkspaceSide.TARGET);
        assertEquals(9, targetSnapshot.objects.size(), "Forward runtime should create 1 register + 8 target objects");
        assertEquals(8, targetSnapshot.links.size(), "Forward runtime should create 8 PersonRegistration links");
        assertEquals(1L, countObjectsByClass(targetSnapshot, "PersonRegister"));
        assertTrue(targetSnapshot.objects.containsKey("personRegister1"),
                "Forward runtime should normalize the target singleton register name to personRegister1");
        assertEquals(5L, countObjectsByClass(targetSnapshot, "Male"));
        assertEquals(3L, countObjectsByClass(targetSnapshot, "Female"));

        FullObjectSnapshot corrSnapshot = runtimeService.loadSnapshot(context, WorkspaceSide.CORRESPONDENCE);
        assertEquals(17, corrSnapshot.objects.size(), "Correspondence runtime should only contain corr nodes");
        assertEquals(1L, countObjectsByClass(corrSnapshot, "FR2PR"));
        assertEquals(2L, countObjectsByClass(corrSnapshot, "F2MP"));
        assertEquals(1L, countObjectsByClass(corrSnapshot, "M2FP"));
        assertEquals(3L, countObjectsByClass(corrSnapshot, "S2MP"));
        assertEquals(2L, countObjectsByClass(corrSnapshot, "D2FP"));
        assertEquals(5L, countObjectsByClass(corrSnapshot, "FM2MP"));
        assertEquals(3L, countObjectsByClass(corrSnapshot, "FM2FP"));
        assertEquals(17L, countObjectsByClasses(corrSnapshot, "FR2PR", "F2MP", "M2FP", "S2MP", "D2FP", "FM2MP", "FM2FP"),
                "Forward runtime should create the full correspondence set");
        assertEquals(34, corrSnapshot.links.size(), "Correspondence runtime should only contain the minimal trace links for each corr binding");
        assertTrue(corrSnapshot.links.values().stream().allMatch(link -> CorrRuntimeTraceHelper.isBindingAssociation(link.assocName)),
                "Correspondence snapshot should not contain legacy corr-domain associations");
        assertTrue(corrSnapshot.links.values().stream().anyMatch(link -> CorrRuntimeTraceHelper.isBindingAssociation(link.assocName)),
                "Correspondence snapshot should contain trace links");
        String bartS2mpCorrId = findObjectIdByNameFragment(corrSnapshot, "S2MP", "Bart");
        assertTrue(bartS2mpCorrId != null && !bartS2mpCorrId.isBlank(), "Forward runtime should create an S2MP corr for Bart");
        assertEquals("s2mp_Bart_male_Simpson_Bart", bartS2mpCorrId,
                "S2MP corr ids should be reduced to the source member id and the mapped target male id");
        assertEquals(2, traceLinkCountForCorr(corrSnapshot, bartS2mpCorrId),
                "S2MP corr objects should only trace the son and the mapped male target");
        assertEquals(Set.of("son", "mp"), traceVariablesForCorr(corrSnapshot, bartS2mpCorrId),
                "S2MP corr objects should not keep unrelated rule bindings such as fr, fm, or pr");
        assertEquals(Set.of("Bart", "male_Simpson_Bart"), traceEndpointIdsForCorr(corrSnapshot, bartS2mpCorrId),
                "S2MP corr objects should only point to the source member and the mapped target male");

        String flandersFm2mpCorrId = findObjectIdByNameFragment(corrSnapshot, "FM2MP", "Flanders");
        assertTrue(flandersFm2mpCorrId != null && !flandersFm2mpCorrId.isBlank(), "Forward runtime should create an FM2MP corr for Flanders");
        assertFalse(flandersFm2mpCorrId.toLowerCase().contains("familyregister"),
                "FM2MP corr ids should not encode the register context");
        assertFalse(flandersFm2mpCorrId.toLowerCase().contains("personregister"),
                "FM2MP corr ids should not encode the target register context");
        assertFalse(traceEndpointIdsForCorr(corrSnapshot, flandersFm2mpCorrId).stream()
                        .anyMatch(id -> id.toLowerCase().contains("register")),
                "FM2MP corr objects should not trace to FamilyRegister or PersonRegister instances");
    }

    @Test
    void rerunForwardIsIdempotentOnNeo4jRuntime() throws Exception {
        assumeTrue(ensureNeo4jConnected(),
                "Neo4j is not configured. Set NEO4J_URI, NEO4J_USER, NEO4J_PASSWORD (optional NEO4J_DB).");

        try (Session session = Neo4jDriverManager.getInstance().openSession()) {
            resetNeo4j(session);
        }

        TggWorkspaceContext context = prepareFamiliesWorkspace();
        Neo4jWorkspaceRuntimeService runtimeService = Neo4jWorkspaceRuntimeService.getInstance();
        DefaultTggExecutionService executionService = new DefaultTggExecutionService();

        TransformationReport firstRun = executionService.run(context, TransformationOptions.applyForward());
        assertFalse(firstRun.getCreatedObjects().isEmpty(), "First forward run should create target objects");
        assertFalse(firstRun.getCreatedCorrespondences().isEmpty(), "First forward run should create correspondences");

        FullObjectSnapshot firstTargetSnapshot = runtimeService.loadSnapshot(context, WorkspaceSide.TARGET);
        FullObjectSnapshot firstCorrSnapshot = runtimeService.loadSnapshot(context, WorkspaceSide.CORRESPONDENCE);

        TransformationReport secondRun = executionService.run(context, TransformationOptions.applyForward());
        assertTrue(secondRun.getCreatedObjects().isEmpty(), "Second forward run should not create duplicate target objects");
        assertTrue(secondRun.getCreatedCorrespondences().isEmpty(), "Second forward run should not create duplicate correspondences");

        FullObjectSnapshot secondTargetSnapshot = runtimeService.loadSnapshot(context, WorkspaceSide.TARGET);
        FullObjectSnapshot secondCorrSnapshot = runtimeService.loadSnapshot(context, WorkspaceSide.CORRESPONDENCE);

        assertEquals(firstTargetSnapshot.objects.size(), secondTargetSnapshot.objects.size(),
                "Second forward run should keep target object count stable");
        assertEquals(firstTargetSnapshot.links.size(), secondTargetSnapshot.links.size(),
                "Second forward run should keep target link count stable");
        assertEquals(firstCorrSnapshot.objects.size(), secondCorrSnapshot.objects.size(),
                "Second forward run should keep corr object count stable");
        assertEquals(firstCorrSnapshot.links.size(), secondCorrSnapshot.links.size(),
                "Second forward run should keep corr trace count stable");
    }

    @Test
    void incrementalForwardUpdatesAffectedTargetWithoutChangingCounts() throws Exception {
        assumeTrue(ensureNeo4jConnected(),
                "Neo4j is not configured. Set NEO4J_URI, NEO4J_USER, NEO4J_PASSWORD (optional NEO4J_DB).");

        try (Session session = Neo4jDriverManager.getInstance().openSession()) {
            resetNeo4j(session);
        }

        TggWorkspaceContext context = prepareFamiliesWorkspace();
        Neo4jWorkspaceRuntimeService runtimeService = Neo4jWorkspaceRuntimeService.getInstance();
        DefaultTggExecutionService executionService = new DefaultTggExecutionService();
        DefaultTggWorkspaceService workspaceService = DefaultTggWorkspaceService.getInstance();

        executionService.run(context, TransformationOptions.applyForward());
        workspaceService.previewIncrementalRemoteChanges(context);

        FullObjectSnapshot beforeSourceSnapshot = runtimeService.loadSnapshot(context, WorkspaceSide.SOURCE);
        FullObjectSnapshot beforeTargetSnapshot = runtimeService.loadSnapshot(context, WorkspaceSide.TARGET);
        FullObjectSnapshot beforeCorrSnapshot = runtimeService.loadSnapshot(context, WorkspaceSide.CORRESPONDENCE);
        String bartTargetId = findObjectIdByNameFragment(beforeTargetSnapshot, "Male", "_Bart");
        assertTrue(bartTargetId != null && !bartTargetId.isBlank(), "Target runtime should contain the Male mapped from Bart");
        assertEquals("Simpson, Bart", unquoteValue(beforeTargetSnapshot.objects.get(bartTargetId).primitiveValues.get("name")),
                "Forward runtime should materialize the initial target person name");

        mutateSourceMemberName(context, "Bart", "Barto");

        IncrementalSyncProposal proposal = workspaceService.previewIncrementalRemoteChanges(context);
        assertTrue(proposal != null, "Incremental runtime should detect the source-side change");
        assertEquals(TransformationDirection.FORWARD, proposal.direction());
        assertEquals(IncrementalSyncStatus.PENDING, proposal.status());
        assertFalse(proposal.mutationBatch().getUpsertObjects().isEmpty(), "Incremental runtime should prepare a target object update");
        assertEquals(1, proposal.mutationBatch().getUpsertObjects().size(),
                "Incremental runtime should merge duplicate updates for the same target object");

        FullObjectSnapshot afterTargetSnapshot = runtimeService.loadSnapshot(context, WorkspaceSide.TARGET);
        FullObjectSnapshot afterCorrSnapshot = runtimeService.loadSnapshot(context, WorkspaceSide.CORRESPONDENCE);

        assertEquals(beforeTargetSnapshot.objects.size(), afterTargetSnapshot.objects.size(),
                "Pending manual incremental proposal should not change target object count");
        assertEquals(beforeTargetSnapshot.links.size(), afterTargetSnapshot.links.size(),
                "Pending manual incremental proposal should not change target link count");
        assertEquals(beforeCorrSnapshot.objects.size(), afterCorrSnapshot.objects.size(),
                "Pending incremental proposal should not change corr object count before apply");
        assertEquals(beforeCorrSnapshot.links.size(), afterCorrSnapshot.links.size(),
                "Pending incremental proposal should not change corr trace count before apply");

        IncrementalApplyResult applyResult = workspaceService.applyPendingIncrementalProposal(context);
        assertEquals(IncrementalSyncStatus.APPLIED, applyResult.status());
        FullObjectSnapshot appliedTargetSnapshot = runtimeService.loadSnapshot(context, WorkspaceSide.TARGET);
        FullObjectSnapshot appliedCorrSnapshot = runtimeService.loadSnapshot(context, WorkspaceSide.CORRESPONDENCE);
        assertEquals("Simpson, Barto", unquoteValue(appliedTargetSnapshot.objects.get(bartTargetId).primitiveValues.get("name")),
                "Incremental forward should update only the affected target person name");
        assertEquals(beforeTargetSnapshot.objects.size(), appliedTargetSnapshot.objects.size(),
                "Incremental forward should keep target object count stable after apply");
        assertEquals(beforeCorrSnapshot.objects.size(), appliedCorrSnapshot.objects.size(),
                "Incremental forward should keep corr object count stable after apply");
    }

    @Test
    void incrementalForwardMarksManualTransformOnSourceDeletion() throws Exception {
        assumeTrue(ensureNeo4jConnected(),
                "Neo4j is not configured. Set NEO4J_URI, NEO4J_USER, NEO4J_PASSWORD (optional NEO4J_DB).");

        try (Session session = Neo4jDriverManager.getInstance().openSession()) {
            resetNeo4j(session);
        }

        TggWorkspaceContext context = prepareFamiliesWorkspace();
        Neo4jWorkspaceRuntimeService runtimeService = Neo4jWorkspaceRuntimeService.getInstance();
        DefaultTggExecutionService executionService = new DefaultTggExecutionService();
        DefaultTggWorkspaceService workspaceService = DefaultTggWorkspaceService.getInstance();

        executionService.run(context, TransformationOptions.applyForward());
        workspaceService.previewIncrementalRemoteChanges(context);

        FullObjectSnapshot beforeTargetSnapshot = runtimeService.loadSnapshot(context, WorkspaceSide.TARGET);
        FullObjectSnapshot beforeCorrSnapshot = runtimeService.loadSnapshot(context, WorkspaceSide.CORRESPONDENCE);
        String bartTargetId = findObjectIdByNameFragment(beforeTargetSnapshot, "Male", "_Bart");
        assertTrue(bartTargetId != null && !bartTargetId.isBlank(), "Target runtime should contain the Male mapped from Bart");

        mutateDeleteSourceObject(context, "Bart");

        IncrementalSyncProposal proposal = workspaceService.previewIncrementalRemoteChanges(context);
        assertTrue(proposal != null, "Incremental runtime should detect the source-side deletion");
        assertEquals(TransformationDirection.FORWARD, proposal.direction());
        assertEquals(IncrementalSyncStatus.MANUAL_TRANSFORM_REQUIRED, proposal.status());
        assertTrue(proposal.requiresManualTransform(), "Families2Persons deletion propagation should currently require manual transform");

        FullObjectSnapshot afterTargetSnapshot = runtimeService.loadSnapshot(context, WorkspaceSide.TARGET);
        FullObjectSnapshot afterCorrSnapshot = runtimeService.loadSnapshot(context, WorkspaceSide.CORRESPONDENCE);

        assertEquals(beforeTargetSnapshot.objects.size(), afterTargetSnapshot.objects.size(),
                "Pending manual deletion proposal should not change target object count");
        assertTrue(afterTargetSnapshot.objects.containsKey(bartTargetId),
                "Pending manual deletion proposal should not remove the mapped target object automatically");
        assertEquals(beforeCorrSnapshot.objects.size(), afterCorrSnapshot.objects.size(),
                "Pending manual deletion proposal should not change corr object count");
        assertTrue(afterCorrSnapshot.links.size() < beforeCorrSnapshot.links.size(),
                "Deleting the source object should remove some corr trace links even before manual transform");

        IncrementalApplyResult applyResult = workspaceService.applyPendingIncrementalProposal(context);
        assertEquals(IncrementalSyncStatus.MANUAL_TRANSFORM_REQUIRED, applyResult.status());
    }

    private TggWorkspaceContext prepareFamiliesWorkspace() throws Exception {
        TggWorkspaceContext context = new TggWorkspaceContext(null, null);
        context.setSourceFile(REPO_ROOT.resolve("rtl/examples/Families2Persons/Families.use").toFile());
        context.setTargetFile(REPO_ROOT.resolve("rtl/examples/Families2Persons/Persons.use").toFile());
        context.setTggFile(REPO_ROOT.resolve("rtl/examples/Families2Persons/F2PForward.tgg").toFile());

        TggWorkspaceDefinition definition = new Neo4jTggWorkspaceLoader().load(context, new PrintWriter(new StringWriter(), true));
        context.setWorkspaceDefinition(definition);

        pushMetamodel(definition);

        GenericXmiImportAdapter adapter = new GenericXmiImportAdapter();
        Neo4jWorkspaceRuntimeService runtimeService = Neo4jWorkspaceRuntimeService.getInstance();
        ImportBatch sourceBatch = adapter.parse(context, WorkspaceSide.SOURCE,
                REPO_ROOT.resolve("examples/families2person/src/models/Families.xmi").toFile());
        GuardReport sourceGuard = runtimeService.validateImport(context, sourceBatch);
        assertTrue(sourceGuard.getErrors().isEmpty(), sourceGuard.toDisplayText());
        runtimeService.applyImport(context, sourceBatch);
        return context;
    }

    private void mutateSourceMemberName(TggWorkspaceContext context, String sourceObjectId, String newName) {
        WorkspaceMutationBatch mutation = new WorkspaceMutationBatch(WorkspaceSide.SOURCE, context.getSourceFile());
        ImportObjectSpec spec = new ImportObjectSpec(sourceObjectId, "FamilyMember");
        spec.getAttributes().put("name", newName);
        mutation.addUpsertObject(spec);

        Neo4jWorkspaceRuntimeService runtimeService = Neo4jWorkspaceRuntimeService.getInstance();
        GuardReport report = runtimeService.validateMutation(context, mutation);
        assertTrue(report.getErrors().isEmpty(), report.toDisplayText());
        runtimeService.applyMutation(context, mutation);
    }

    private void mutateDeleteSourceObject(TggWorkspaceContext context, String sourceObjectId) {
        WorkspaceMutationBatch mutation = new WorkspaceMutationBatch(WorkspaceSide.SOURCE, context.getSourceFile());
        mutation.addDeleteObjectId(sourceObjectId);

        Neo4jWorkspaceRuntimeService runtimeService = Neo4jWorkspaceRuntimeService.getInstance();
        GuardReport report = runtimeService.validateMutation(context, mutation);
        assertTrue(report.getErrors().isEmpty(), report.toDisplayText());
        runtimeService.applyMutation(context, mutation);
    }

    private void pushMetamodel(TggWorkspaceDefinition definition) {
        new CoreModelPushService(new UseModelApi(definition.getSourceModel())).pushModelToNeo4j();
        new CoreModelPushService(new UseModelApi(definition.getTargetModel())).pushModelToNeo4j();
        new CoreModelPushService(new UseModelApi(definition.getCorrespondenceModel())).pushModelToNeo4j();
    }

    private long countObjectsByClass(FullObjectSnapshot snapshot, String className) {
        return snapshot.objects.values().stream()
                .filter(object -> className.equals(object.className))
                .count();
    }

    private long countObjectsByClasses(FullObjectSnapshot snapshot, String... classNames) {
        return snapshot.objects.values().stream()
                .filter(object -> java.util.Arrays.asList(classNames).contains(object.className))
                .count();
    }

    private String findObjectIdByAttribute(FullObjectSnapshot snapshot, String className, String attributeName, String expectedValue) {
        return snapshot.objects.values().stream()
                .filter(object -> className.equals(object.className))
                .filter(object -> expectedValue.equals(String.valueOf(object.primitiveValues.get(attributeName))))
                .map(object -> object.name)
                .findFirst()
                .orElse(null);
    }

    private String findObjectIdByNameFragment(FullObjectSnapshot snapshot, String className, String fragment) {
        return snapshot.objects.values().stream()
                .filter(object -> className.equals(object.className))
                .filter(object -> object.name.contains(fragment))
                .map(object -> object.name)
                .findFirst()
                .orElse(null);
    }

    private String unquoteValue(Object value) {
        String text = String.valueOf(value);
        if (text.length() >= 2 && text.startsWith("'") && text.endsWith("'")) {
            return text.substring(1, text.length() - 1);
        }
        return text;
    }

    private int traceLinkCountForCorr(FullObjectSnapshot snapshot, String corrObjectId) {
        return (int) snapshot.links.values().stream()
                .map(CorrRuntimeTraceHelper::parseTraceLink)
                .filter(java.util.Objects::nonNull)
                .filter(trace -> corrObjectId.equals(trace.corrObjectId()))
                .count();
    }

    private java.util.Set<String> traceVariablesForCorr(FullObjectSnapshot snapshot, String corrObjectId) {
        return snapshot.links.values().stream()
                .map(CorrRuntimeTraceHelper::parseTraceLink)
                .filter(java.util.Objects::nonNull)
                .filter(trace -> corrObjectId.equals(trace.corrObjectId()))
                .map(CorrRuntimeTraceHelper.TraceLink::variableName)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    private java.util.Set<String> traceEndpointIdsForCorr(FullObjectSnapshot snapshot, String corrObjectId) {
        return snapshot.links.values().stream()
                .map(CorrRuntimeTraceHelper::parseTraceLink)
                .filter(java.util.Objects::nonNull)
                .filter(trace -> corrObjectId.equals(trace.corrObjectId()))
                .map(CorrRuntimeTraceHelper.TraceLink::objectId)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    private boolean ensureNeo4jConnected() {
        Neo4jDriverManager manager = Neo4jDriverManager.getInstance();
        if (manager != null && manager.isConnected()) {
            return true;
        }

        String uri = readEnvOrProperty(ENV_NEO4J_URI);
        String user = readEnvOrProperty(ENV_NEO4J_USER);
        String password = readEnvOrProperty(ENV_NEO4J_PASSWORD);
        String database = readEnvOrProperty(ENV_NEO4J_DB);
        if (uri == null || user == null || password == null) {
            return false;
        }

        boolean createDb = Boolean.parseBoolean(readEnvOrProperty(ENV_NEO4J_CREATE, "false"));
        boolean deleteOnExit = Boolean.parseBoolean(readEnvOrProperty(ENV_NEO4J_DELETE, "false"));
        try {
            Neo4jDriverManager.connect(uri, user, password, database, createDb, deleteOnExit);
            SessionManager sessionManager = new SessionManager();
            sessionManager.createNewSession();
            Neo4jDriverManager.getInstance().setSessionManager(sessionManager);
            return Neo4jDriverManager.getInstance() != null && Neo4jDriverManager.getInstance().isConnected();
        } catch (Exception ex) {
            System.err.println("Neo4j connection failed: " + ex.getMessage());
            return false;
        }
    }

    private String readEnvOrProperty(String key) {
        return readEnvOrProperty(key, null);
    }

    private String readEnvOrProperty(String key, String defaultValue) {
        String env = System.getenv(key);
        if (env != null && !env.isBlank()) {
            return env;
        }
        String prop = System.getProperty(key);
        return (prop != null && !prop.isBlank()) ? prop : defaultValue;
    }

    private void resetNeo4j(Session session) {
        session.run("MATCH (n) DETACH DELETE n").consume();
    }
}
