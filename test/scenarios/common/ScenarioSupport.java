package scenarios.common;

import org.neo4j.driver.Session;
import org.tzi.use.api.UseModelApi;
import org.tzi.use.api.UseSystemApi;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.uml.mm.MClass;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.uet.dse.neo4j.manager.Neo4jDriverManager;
import org.uet.dse.neo4j.manager.SessionManager;
import org.uet.dse.neo4j.sync.model.CoreModelPushService;
import org.uet.dse.neo4j.sync.object.ObjectDiff;
import org.uet.dse.neo4j.sync.object.ObjectSyncCoordinator;
import org.uet.dse.neo4jtgg.model.OclFileValidationResult;
import org.uet.dse.neo4jtgg.model.TggWorkspaceContext;
import org.uet.dse.neo4jtgg.model.TggWorkspaceDefinition;
import org.uet.dse.neo4jtgg.model.WorkspaceSide;
import org.uet.dse.neo4jtgg.service.impl.DefaultOclValidationService;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class ScenarioSupport {
    private ScenarioSupport() {
    }

    public static void ensureNeo4jConnected() throws Exception {
        Neo4jDriverManager manager = Neo4jDriverManager.getInstance();
        if (manager != null && manager.isConnected()) {
            return;
        }

        DotEnvNeo4jConfig config = DotEnvNeo4jConfig.loadDefault();
        Neo4jDriverManager.connect(
                config.requireUri(),
                config.requireUser(),
                config.requirePassword(),
                config.database(),
                config.createDatabase(),
                config.deleteOnExit());

        SessionManager sessionManager = new SessionManager();
        sessionManager.createNewSession();
        Neo4jDriverManager.getInstance().setSessionManager(sessionManager);
    }

    public static void resetDatabase() {
        try (Session session = Neo4jDriverManager.getInstance().openSession()) {
            session.run("MATCH (n) DETACH DELETE n").consume();
        }
    }

    public static MModel compileModel(Path modelPath) throws Exception {
        String spec = Files.readString(modelPath);
        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(
                spec,
                modelPath.getFileName().toString(),
                new PrintWriter(buffer, true),
                new ModelFactory());
        if (model == null) {
            throw new IllegalStateException("Failed to compile model " + modelPath + ":\n" + buffer);
        }
        return model;
    }

    public static UseSystemApi createSystemApi(MModel model) {
        return UseSystemApi.create(model, false);
    }

    public static void pushModelAndObjects(MModel model, UseSystemApi systemApi) {
        CoreModelPushService modelPushService = new CoreModelPushService(new UseModelApi(model));
        modelPushService.pushModelToNeo4j();

        ObjectSyncCoordinator objectSyncCoordinator = new ObjectSyncCoordinator(systemApi.getSystem());
        ObjectDiff diff = objectSyncCoordinator.compareObjects();
        objectSyncCoordinator.pushToNeo4j(diff);
    }

    public static OclFileValidationResult validateQueries(MModel model, Path queriesPath) throws Exception {
        String oclText = Files.readString(queriesPath);
        TggWorkspaceContext context = new TggWorkspaceContext(null, null);
        context.setWorkspaceDefinition(new TggWorkspaceDefinition(
                model.name(),
                model,
                null,
                null,
                List.of(),
                classNames(model),
                Set.of(),
                Set.of()));

        return new DefaultOclValidationService().validateFile(
                context,
                WorkspaceSide.SOURCE,
                oclText,
                Map.of(),
                true);
    }

    public static void assertValidationPassed(OclFileValidationResult result) {
        if (!result.isSuccess()) {
            throw new IllegalStateException("OCL validation failed:\n" + result.toDisplayText());
        }
    }

    private static Set<String> classNames(MModel model) {
        return model.classes().stream().map(MClass::name).collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }
}
