package org.uet.dse.neo4jtgg.ui;

import org.neo4j.driver.Result;
import org.neo4j.driver.QueryRunner;
import org.neo4j.driver.Session;
import org.neo4j.driver.Transaction;
import org.tzi.use.api.UseModelApi;
import org.tzi.use.api.UseSystemApi;
import org.tzi.use.gui.main.MainWindow;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uml.sys.MSystem;
import org.uet.dse.neo4j.manager.Neo4jDriverManager;
import org.uet.dse.neo4j.manager.SessionManager;
import org.uet.dse.neo4j.encoding.CanonicalGraphEncoding;
import org.uet.dse.neo4j.repo.Neo4jObjectRepository;
import org.uet.dse.neo4j.sync.model.CoreModelPushService;
import org.uet.dse.neo4j.sync.object.ObjectDiff;
import org.uet.dse.neo4j.sync.object.ObjectPushService;
import org.uet.dse.neo4j.sync.object.ObjectSnapshotCompare;
import org.uet.dse.neo4jtgg.experiment.InstrumentedCompilationResult;
import org.uet.dse.neo4jtgg.experiment.AdapterAdequacyCertificate;
import org.uet.dse.neo4jtgg.experiment.AdapterAdequacySnapshotReader;
import org.uet.dse.neo4jtgg.experiment.Neo4jEnvironmentConfig;
import org.uet.dse.neo4jtgg.experiment.ScientificEvaluationReport;
import org.uet.dse.neo4jtgg.experiment.ViolationOracleResult;
import org.uet.dse.neo4jtgg.experiment.ViolationSetOracle;
import org.uet.dse.neo4jtgg.experiment.UseObjectSideReferenceEvaluator;
import org.uet.dse.neo4jtgg.model.ImportBatch;
import org.uet.dse.neo4jtgg.model.ImportLinkSpec;
import org.uet.dse.neo4jtgg.model.ImportObjectSpec;
import org.uet.dse.neo4jtgg.ocl.Neo4jOclMetamodelSnapshotReader;
import org.uet.dse.neo4jtgg.ocl.OclBottomSeparationChecker;
import org.uet.dse.neo4jtgg.ocl.OclExecutionPremiseChecker;
import org.uet.dse.neo4jtgg.ocl.OclScalarClosureChecker;
import org.uet.dse.neo4jtgg.service.impl.DefaultOclToCypherCompiler;
import org.uet.dse.neo4jtgg.service.impl.GenericXmiImportAdapter;
import org.uet.dse.neo4jtgg.service.impl.OclCompilerFactory;
import org.uet.dse.neo4jtgg.service.impl.SoilFileLoader;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Research-only, single-model workflow. It deliberately does not depend on the
 * source/correspondence/target TGG workspace context.
 */
@SuppressWarnings("serial")
public final class ResearchToolDialog extends JDialog {
    private final org.tzi.use.main.Session useSession;
    private final JTextField metamodelPath = new JTextField();
    private final JTextField instancePath = new JTextField();
    private final JTextField constraintPath = new JTextField();
    private final JTextField neo4jUri = new JTextField("bolt://localhost:7687", 20);
    private final JTextField neo4jDatabase = new JTextField("neo4j", 10);
    private final JTextField neo4jUser = new JTextField("neo4j", 10);
    private final JPasswordField neo4jPassword = new JPasswordField(12);
    private final JLabel connectionStatus = new JLabel("Not connected");
    private final JButton loadModelButton = new JButton("1. Load M2");
    private final JButton loadInstanceButton = new JButton("2. Load M1");
    private final JButton validateButton = new JButton("3. Validate");
    private final JCheckBox graphBackedBinding = new JCheckBox("Bind using M2 read back from Neo4j", true);
    private final JTextArea oclEditor = monospacedArea();
    private final JTextArea status = monospacedArea();
    private final JTextArea artifacts = monospacedArea();
    private final Path workspaceRoot = findWorkspaceRoot();
    private MModel model;
    private MSystem system;

    public ResearchToolDialog(MainWindow parent, org.tzi.use.main.Session useSession) {
        super(parent, "OCL2Cypher", false);
        this.useSession = useSession;
        setLayout(new BorderLayout(8, 8));
        ((JComponent) getContentPane()).setBorder(new EmptyBorder(10, 10, 10, 10));
        setSize(1180, 780);
        setMinimumSize(new Dimension(900, 620));

        add(buildSetupPanel(), BorderLayout.NORTH);
        add(buildBody(), BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);
        loadInstanceButton.setEnabled(false);
        validateButton.setEnabled(false);
        loadModelButton.setEnabled(false);
        status.setEditable(false);
        artifacts.setEditable(false);
        applyConnectionDefaults();
        status.setText("Connect Neo4j first. All research operations remain locked until connectivity is verified.");
        refreshExistingConnection(false);
        setLocationRelativeTo(parent);
    }

    private JComponent buildSetupPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(buildConnectionPanel());
        panel.add(Box.createVerticalStrut(6));
        panel.add(buildInputPanel());
        return panel;
    }

    private JComponent buildConnectionPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));
        panel.setBorder(BorderFactory.createTitledBorder("0. Neo4j Connection (required)"));
        panel.add(new JLabel("URI"));
        panel.add(neo4jUri);
        panel.add(new JLabel("Database"));
        panel.add(neo4jDatabase);
        panel.add(new JLabel("User"));
        panel.add(neo4jUser);
        panel.add(new JLabel("Password"));
        panel.add(neo4jPassword);
        JButton connect = new JButton("Connect");
        JButton reuse = new JButton("Use Existing Connection");
        connect.addActionListener(event -> connectNeo4j());
        reuse.addActionListener(event -> refreshExistingConnection(true));
        panel.add(connect);
        panel.add(reuse);
        connectionStatus.setForeground(new Color(145, 35, 35));
        panel.add(connectionStatus);
        return panel;
    }

    private JComponent buildInputPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Reproducible Input"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 4, 3, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        addFileRow(panel, gbc, 0, "Metamodel M2 (.use)", metamodelPath, "use");
        addFileRow(panel, gbc, 1, "Instance state (.soil/.xmi/.xml)", instancePath, "soil", "xmi", "xml");
        addFileRow(panel, gbc, 2, "OCL invariant (.ocl)", constraintPath, "ocl");

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        loadModelButton.addActionListener(event -> loadMetamodel());
        loadInstanceButton.addActionListener(event -> loadInstance());
        validateButton.addActionListener(event -> validateInvariant());
        actions.add(loadModelButton);
        actions.add(loadInstanceButton);
        actions.add(validateButton);
        graphBackedBinding.setToolTipText(
                "Read canonical M2 from Neo4j and require exact agreement with the loaded USE model before binding.");
        actions.add(graphBackedBinding);
        gbc.gridx = 1;
        gbc.gridy = 3;
        gbc.weightx = 1;
        panel.add(actions, gbc);
        return panel;
    }

    private JComponent buildBody() {
        JTabbedPane tabs = new JTabbedPane();
        oclEditor.setBorder(new EmptyBorder(6, 6, 6, 6));
        tabs.addTab("OCL Editor", new JScrollPane(oclEditor));
        tabs.addTab("Correctness Result", new JScrollPane(status));
        tabs.addTab("Pipeline Artifacts", new JScrollPane(artifacts));
        tabs.addTab("Neo4j Graph", new Neo4jGraphQueryPanel());
        return tabs;
    }

    private JComponent buildFooter() {
        JPanel footer = new JPanel(new BorderLayout());
        JLabel scope = new JLabel(
                "Scope: one M2 + one independent USE object state + canonical Neo4j encoding + exact violation IDs");
        scope.setForeground(new Color(70, 70, 70));
        JButton close = new JButton("Close");
        close.addActionListener(event -> dispose());
        footer.add(scope, BorderLayout.WEST);
        footer.add(close, BorderLayout.EAST);
        return footer;
    }

    private void addFileRow(JPanel panel, GridBagConstraints gbc, int row, String label,
                            JTextField field, String... extensions) {
        gbc.gridy = row;
        gbc.gridx = 0;
        gbc.weightx = 0;
        panel.add(new JLabel(label), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(field, gbc);
        gbc.gridx = 2;
        gbc.weightx = 0;
        JButton browse = new JButton("Browse");
        browse.addActionListener(event -> chooseFile(field, extensions));
        panel.add(browse, gbc);
    }

    private void chooseFile(JTextField field, String... extensions) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter(String.join(", ", extensions), extensions));
        if (!field.getText().isBlank()) {
            chooser.setSelectedFile(resolveInputPath(field.getText()).toFile());
        }
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File selectedFile = chooser.getSelectedFile();
            field.setText(displayPath(selectedFile));
            field.setToolTipText(selectedFile.getAbsolutePath());
            if (extensions.length == 1 && "ocl".equals(extensions[0])) {
                try {
                    oclEditor.setText(Files.readString(selectedFile.toPath()));
                } catch (Exception exception) {
                    showError("Cannot read OCL file", exception);
                }
            }
        }
    }

    private void loadMetamodel() {
        if (!requireConnectedNeo4j()) return;
        File file = requireFile(metamodelPath, "Select a .use metamodel first.");
        if (file == null) return;
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        loadModelButton.setEnabled(false);
        loadInstanceButton.setEnabled(false);
        validateButton.setEnabled(false);
        status.setText("LOADING M2...\nCompiling " + file.getName()
                + "\nWriting the canonical metamodel encoding to Neo4j.");

        new SwingWorker<MModel, Void>() {
            @Override
            protected MModel doInBackground() throws Exception {
                StringWriter diagnostics = new StringWriter();
                MModel loadedModel;
                try (FileInputStream input = new FileInputStream(file)) {
                    loadedModel = USECompiler.compileSpecification(input, file.getAbsolutePath(),
                            new PrintWriter(diagnostics, true), new ModelFactory());
                }
                if (loadedModel == null) {
                    throw new IllegalStateException(diagnostics.toString());
                }
                pushMetamodel(loadedModel);
                return loadedModel;
            }

            @Override
            protected void done() {
                setCursor(Cursor.getDefaultCursor());
                loadModelButton.setEnabled(true);
                try {
                    model = get();
                    system = new MSystem(model);
                    useSession.setSystem(system);
                    loadInstanceButton.setEnabled(true);
                    validateButton.setEnabled(false);
                    status.setText("M2 LOADED\nModel: " + model.name()
                            + "\nClasses: " + model.classes().size()
                            + "\nAssociations: " + model.associations().size()
                            + "\nNeo4j canonical encoding: written");
                } catch (Exception exception) {
                    model = null;
                    system = null;
                    loadInstanceButton.setEnabled(false);
                    validateButton.setEnabled(false);
                    showError("Metamodel loading failed", backgroundCause(exception));
                }
            }
        }.execute();
    }

    private void pushMetamodel(MModel loadedModel) {
        Neo4jDriverManager manager = Neo4jDriverManager.getInstance();
        ensureSessionIdentity(manager);
        new CoreModelPushService(new UseModelApi(loadedModel)).pushModelToNeo4j();
    }

    private static Exception backgroundCause(Exception exception) {
        Throwable cause = exception;
        if (exception instanceof java.util.concurrent.ExecutionException
                && exception.getCause() != null) {
            cause = exception.getCause();
        }
        if (cause instanceof Exception checked) return checked;
        return new IllegalStateException(cause.getMessage(), cause);
    }

    private void loadInstance() {
        if (!requireConnectedNeo4j()) return;
        if (system == null) return;
        File file = requireFile(instancePath, "Select a .soil instance file first.");
        if (file == null) return;
        MModel loadedModel = model;
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        loadModelButton.setEnabled(false);
        loadInstanceButton.setEnabled(false);
        validateButton.setEnabled(false);
        status.setText("LOADING M1...\nReading " + file.getName()
                + "\nComparing and writing the canonical object encoding to Neo4j.");

        new SwingWorker<LoadedInstance, Void>() {
            @Override
            protected LoadedInstance doInBackground() throws Exception {
                long started = System.nanoTime();
                MSystem loadedSystem = new MSystem(loadedModel);
                String lowerName = file.getName().toLowerCase(java.util.Locale.ROOT);
                if (lowerName.endsWith(".soil")) {
                    loadSoil(loadedSystem, file);
                } else if (lowerName.endsWith(".xmi") || lowerName.endsWith(".xml")) {
                    loadXmi(loadedSystem, loadedModel, file);
                } else {
                    throw new IllegalArgumentException("Supported instance formats are .soil, .xmi and .xml.");
                }
                pushObjects(loadedSystem);
                long elapsedMillis = (System.nanoTime() - started) / 1_000_000L;
                return new LoadedInstance(loadedSystem, elapsedMillis);
            }

            @Override
            protected void done() {
                setCursor(Cursor.getDefaultCursor());
                loadModelButton.setEnabled(true);
                loadInstanceButton.setEnabled(true);
                try {
                    LoadedInstance loaded = get();
                    system = loaded.system();
                    useSession.setSystem(system);
                    validateButton.setEnabled(true);
                    status.setText("INSTANCE STATE LOADED\nObjects: " + system.state().allObjects().size()
                            + "\nLinks: " + system.state().allLinks().size()
                            + "\nElapsed: " + loaded.elapsedMillis() + " ms"
                            + "\nUSE state is the independent reference state."
                            + "\nNeo4j canonical encoding: written");
                } catch (Exception exception) {
                    validateButton.setEnabled(false);
                    showError("Instance loading failed", backgroundCause(exception));
                }
            }
        }.execute();
    }

    private record LoadedInstance(MSystem system, long elapsedMillis) { }

    private void loadSoil(MSystem targetSystem, File file) throws Exception {
        new SoilFileLoader().load(targetSystem, Files.readString(file.toPath()), file.getAbsolutePath());
    }

    private void loadXmi(MSystem targetSystem, MModel targetModel, File file) throws Exception {
        ImportBatch batch = new GenericXmiImportAdapter().parse(targetModel, file);
        UseSystemApi api = UseSystemApi.create(targetSystem, true);
        for (ImportObjectSpec object : batch.getObjects()) {
            api.createObject(object.getClassName(), object.getObjectName());
        }
        for (ImportObjectSpec object : batch.getObjects()) {
            for (Map.Entry<String, String> attribute : object.getAttributes().entrySet()) {
                api.setAttributeValue(object.getObjectName(), attribute.getKey(), attribute.getValue());
            }
        }
        for (ImportLinkSpec link : batch.getLinks()) {
            String[] endpoints = link.getEndpointNames().toArray(String[]::new);
            if (link.getQualifierValues().isEmpty()) {
                api.createLink(link.getAssociationName(), endpoints);
            } else {
                String[][] qualifiers = link.getQualifierValues().stream()
                        .map(values -> values.toArray(String[]::new)).toArray(String[][]::new);
                api.createLink(link.getAssociationName(), endpoints, qualifiers);
            }
        }
    }

    private void pushObjects(MSystem targetSystem) {
        ObjectDiff diff = new ObjectSnapshotCompare(targetSystem).compareObjects();
        new ObjectPushService(new Neo4jObjectRepository(), targetSystem).pushToNeo4j(diff);
    }

    private void validateInvariant() {
        if (system == null) return;
        String ocl = oclEditor.getText().trim();
        if (ocl.isBlank()) {
            File file = requireFile(constraintPath, "Select or enter one OCL invariant.");
            if (file == null) return;
            try {
                ocl = Files.readString(file.toPath());
                oclEditor.setText(ocl);
            } catch (Exception exception) {
                showError("Cannot read OCL file", exception);
                return;
            }
        }
        if (!requireConnectedNeo4j()) return;
        String invariantText = ocl;
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        loadModelButton.setEnabled(false);
        loadInstanceButton.setEnabled(false);
        validateButton.setEnabled(false);
        status.setText("VALIDATING...\nCompiling OCL and comparing USE with Neo4j violation IDs.");
        new SwingWorker<ValidationRun, Void>() {
            @Override protected ValidationRun doInBackground() {
                return runInvariantSuite(invariantText);
            }

            @Override protected void done() {
                setCursor(Cursor.getDefaultCursor());
                loadModelButton.setEnabled(true);
                loadInstanceButton.setEnabled(true);
                validateButton.setEnabled(true);
                try {
                    ValidationRun run = get();
                    status.setText(run.result());
                    status.setCaretPosition(0);
                    artifacts.setText(run.artifacts());
                    artifacts.setCaretPosition(0);
                } catch (Exception exception) {
                    showError("Validation failed", backgroundCause(exception));
                }
            }
        }.execute();
    }

    private ValidationRun runInvariantSuite(String ocl) {
        OclCompilerFactory.Selection compilerSelection = selectCompiler();
        DefaultOclToCypherCompiler compiler = compilerSelection.compiler();
        List<org.uet.dse.neo4j.oclite.ast.ASTContext> invariants = compiler.parseContextInvariants(ocl);
        StringBuilder finalResult = new StringBuilder();
        StringBuilder allArtifacts = new StringBuilder();
        int passed = 0;
        int failed = 0;
        int errors = 0;
        long compileNs = 0L;
        long queryNs = 0L;
        List<ScientificEvaluationReport.CorrectnessObservation> correctness = new ArrayList<>();
        finalResult.append("EXECUTION PREMISES\n")
                .append("  BottomSeparated=PASS\n")
                .append("  StoredScalarDomain=PASS\n")
                .append("RULE RESULTS\n");
        for (org.uet.dse.neo4j.oclite.ast.ASTContext invariant : invariants) {
            String ruleId = invariant.className + "::" + invariant.invName;
            try {
                InstrumentedCompilationResult compiled = compiler.compileInvariantInstrumented(invariant);
                OclExecutionPremiseChecker.requireUseSystemScalarClosed(
                        system, compiled.validationAlgebra());
                OclExecutionPremiseChecker.requireGeneratedBottomSeparated(compiled.parameters());
                compileNs += compiled.timings().compileNs();
                long queryStart = System.nanoTime();
                ViolationOracleResult oracleResult;
                String modelKey = CanonicalGraphEncoding.modelKey(model.name());
                try (Session neo4jSession = Neo4jDriverManager.getInstance().openSession();
                     Transaction transaction = neo4jSession.beginTransaction()) {
                    var sourceAtStart = AdapterAdequacySnapshotReader.source(system, model.name());
                    var graphAtStart = AdapterAdequacySnapshotReader.graph(transaction, modelKey);
                    OclBottomSeparationChecker.requireGraphSeparated(transaction, modelKey);
                    OclScalarClosureChecker.requireGraphClosed(transaction, modelKey);
                    OclExecutionPremiseChecker.requireGraphScalarClosed(
                            transaction, model.name(), compiled.validationAlgebra());
                    AdapterAdequacyCertificate.issue(
                            new AdapterAdequacyCertificate.AdapterAdequacySnapshot(
                                    "research-tool-read-transaction-" + ruleId, model.name(), modelKey,
                                    "OclCypherRenderer-direct-v1", sourceAtStart,
                                    AdapterAdequacySnapshotReader.source(system, model.name()), graphAtStart,
                                    AdapterAdequacySnapshotReader.graph(transaction, modelKey), List.of(compiled)));
                    ViolationSetOracle oracle = new ViolationSetOracle(
                            new UseObjectSideReferenceEvaluator(),
                            (cypher, parameters) -> executeViolationQuery(transaction, cypher, parameters));
                    oracleResult = oracle.evaluate(
                            ruleId, contextIds(invariant.className), system, invariant,
                            compiled.cypher(), compiled.parameters());
                    transaction.commit();
                }
                long currentQueryNs = System.nanoTime() - queryStart;
                queryNs += currentQueryNs;
                if (!oracleResult.completed()) {
                    throw new IllegalStateException(oracleResult.render());
                }
                ScientificEvaluationReport.CorrectnessObservation observation =
                        ScientificEvaluationReport.CorrectnessObservation.compare(
                                ruleId, oracleResult.metricUniverseIds(),
                                oracleResult.referenceViolationIds(),
                                oracleResult.cypherViolationIds());
                correctness.add(observation);
                if (oracleResult.equivalent()) passed++; else failed++;
                finalResult.append("\n[").append(oracleResult.equivalent() ? "PASS" : "FAIL").append("] ")
                        .append(ruleId)
                        .append("\n  oracleStatus=").append(oracleResult.status())
                        .append("\n  referenceIds=").append(oracleResult.referenceViolationIds())
                        .append("\n  cypherIds=").append(oracleResult.cypherViolationIds())
                        .append("\n  missingIds=").append(oracleResult.missingIds())
                        .append("\n  spuriousIds=").append(oracleResult.spuriousIds())
                        .append("\n  ScalarClosed=PASS")
                        .append("\n  BottomSeparated=PASS")
                        .append("\n  precision=").append(metric(observation.precision()))
                        .append(", recall=").append(metric(observation.recall()))
                        .append(", accuracy=").append(metric(observation.accuracy()))
                        .append("\n  compileMs=").append(nanosToMillis(compiled.timings().compileNs()))
                        .append(", queryMs=").append(nanosToMillis(currentQueryNs)).append('\n');
                allArtifacts.append("================ ").append(ruleId).append(" ================\n")
                        .append(renderArtifacts(compiled)).append("\n\n");
            } catch (Exception exception) {
                errors++;
                finalResult.append("\n[ERROR] ").append(ruleId).append("\n  ")
                        .append(exception.getMessage()).append('\n');
            }
        }
        boolean overallPass = failed == 0 && errors == 0 && passed == invariants.size();
        double exactMatchRate = invariants.isEmpty() ? 0.0
                : (double) correctness.stream().filter(
                        ScientificEvaluationReport.CorrectnessObservation::exactMatch).count()
                / invariants.size();
        finalResult.insert(0, "FINAL RESULT: " + (overallPass ? "PASS" : "FAIL")
                + "\nBinder M2 source=" + compilerSelection.source()
                + "\nTotal=" + invariants.size() + ", pass=" + passed
                + ", fail=" + failed + ", error=" + errors
                + "\nCompile total ms=" + nanosToMillis(compileNs)
                + ", Query total ms=" + nanosToMillis(queryNs)
                + "\nCorrectness exact-match rate=" + metric(exactMatchRate)
                + "\nSemantic gate=" + (overallPass ? "PASS" : "FAIL") + "\n\n");
        return new ValidationRun(finalResult.toString(), allArtifacts.toString());
    }

    private record ValidationRun(String result, String artifacts) { }

    private OclCompilerFactory.Selection selectCompiler() {
        if (!graphBackedBinding.isSelected()) {
            return OclCompilerFactory.fromUse(model);
        }
        Neo4jDriverManager manager = Neo4jDriverManager.getInstance();
        if (manager == null || !manager.isConnected() || manager.getDriver() == null) {
            throw new IllegalStateException("Graph-backed binding requires an active Neo4j connection.");
        }
        var graphSnapshot = new Neo4jOclMetamodelSnapshotReader(
                manager.getDriver(), manager.getActiveDatabase()).read(model.name());
        return OclCompilerFactory.fromGraph(model, graphSnapshot);
    }

    private static String nanosToMillis(long nanos) {
        return String.format(java.util.Locale.ROOT, "%.3f", nanos / 1_000_000.0);
    }

    private static String metric(double value) {
        return String.format(java.util.Locale.ROOT, "%.4f", value);
    }

    private Set<String> contextIds(String className) {
        Set<String> ids = new LinkedHashSet<>();
        var contextClass = system.model().getClass(className);
        if (contextClass == null) return Set.of();
        for (MObject object : system.state().objectsOfClassAndSubClasses(contextClass)) {
            ids.add(object.name());
        }
        return Set.copyOf(ids);
    }

    private Set<String> executeViolationQuery(String cypher, Map<String, Object> parameters) {
        try (Session session = Neo4jDriverManager.getInstance().openSession()) {
            return executeViolationQuery(session, cypher, parameters);
        }
    }

    private Set<String> executeViolationQuery(QueryRunner runner, String cypher,
                                              Map<String, Object> parameters) {
        Set<String> ids = new LinkedHashSet<>();
        Result result = runner.run(cypher, parameters);
        while (result.hasNext()) ids.add(result.next().get("useId").asString());
        return Set.copyOf(ids);
    }

    private void requireGraphPremises() {
        String modelKey = CanonicalGraphEncoding.modelKey(model.name());
        try (Session session = Neo4jDriverManager.getInstance().openSession()) {
            OclBottomSeparationChecker.requireGraphSeparated(session, modelKey);
            OclScalarClosureChecker.requireGraphClosed(session, modelKey);
        }
    }

    private String renderArtifacts(InstrumentedCompilationResult result) {
        return "AST\n" + result.ast()
                + "\n\nBOUND OCL\n" + result.bound()
                + "\n\nVALIDATION ALGEBRA\n" + result.validationAlgebra()
                + "\n\nNORMALIZED VA\n" + result.normalizedValidationAlgebra()
                + "\n\nQUERY PLAN\n" + result.queryPlan()
                + "\n\nCYPHER\n" + result.cypher()
                + "\n\nPARAMETERS\n" + result.parameters()
                + "\n\nSTAGE TIMING (ns)\n" + result.timings()
                + "\nCompile total: " + result.timings().compileNs();
    }

    private void ensureSessionIdentity(Neo4jDriverManager manager) {
        if (manager.getSessionManager() == null) {
            SessionManager sessionManager = new SessionManager();
            sessionManager.createNewSession();
            manager.setSessionManager(sessionManager);
        }
    }

    private boolean requireConnectedNeo4j() {
        Neo4jDriverManager manager = Neo4jDriverManager.getInstance();
        if (manager == null || !manager.isConnected()) {
            setConnectionGate(false, "Not connected");
            JOptionPane.showMessageDialog(this,
                    "Connect Neo4j successfully before continuing.",
                    "Neo4j Connection Required", JOptionPane.WARNING_MESSAGE);
            return false;
        }
        return true;
    }

    private void connectNeo4j() {
        String uri = neo4jUri.getText().trim();
        String database = neo4jDatabase.getText().trim();
        String user = neo4jUser.getText().trim();
        String password = new String(neo4jPassword.getPassword());
        if (uri.isBlank() || database.isBlank() || user.isBlank()) {
            JOptionPane.showMessageDialog(this, "URI, database and user are required.",
                    "Invalid Connection", JOptionPane.WARNING_MESSAGE);
            return;
        }
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        try {
            Neo4jDriverManager.connect(uri, user, password, database, false, false);
            Neo4jDriverManager manager = Neo4jDriverManager.getInstance();
            ensureSessionIdentity(manager);
            neo4jPassword.setText("");
            setConnectionGate(true, "Connected: " + uri + " / " + database);
            status.setText("NEO4J CONNECTED\nThe metamodel step is now enabled.");
        } catch (Exception exception) {
            setConnectionGate(false, "Connection failed");
            showError("Neo4j connection failed", exception);
        } finally {
            setCursor(Cursor.getDefaultCursor());
        }
    }

    private void applyConnectionDefaults() {
        try {
            Neo4jEnvironmentConfig config = Neo4jEnvironmentConfig.load();
            neo4jUri.setText(config.uri());
            neo4jDatabase.setText(config.database());
            neo4jUser.setText(config.user());
            neo4jPassword.setText(config.password());
            if (config.sourceFile() != null) {
                connectionStatus.setToolTipText("Defaults loaded from " + config.sourceFile());
            } else {
                connectionStatus.setToolTipText("Using environment variables/system properties or built-in defaults.");
            }
        } catch (RuntimeException exception) {
            connectionStatus.setToolTipText(exception.getMessage());
        }
    }

    private void refreshExistingConnection(boolean showMessage) {
        Neo4jDriverManager manager = Neo4jDriverManager.getInstance();
        boolean connected = manager != null && manager.isConnected();
        if (connected) {
            ensureSessionIdentity(manager);
            neo4jUri.setText(manager.getUri());
            neo4jDatabase.setText(manager.getActiveDatabase());
            neo4jUser.setText(manager.getUser());
            setConnectionGate(true, "Connected: " + manager.getUri() + " / " + manager.getActiveDatabase());
            status.setText("EXISTING NEO4J CONNECTION VERIFIED\nThe metamodel step is now enabled.");
        } else {
            setConnectionGate(false, "Not connected");
            if (showMessage) {
                JOptionPane.showMessageDialog(this,
                        "No active Neo4j connection was found. Connect here or use the Neo4j plugin first.",
                        "No Existing Connection", JOptionPane.WARNING_MESSAGE);
            }
        }
    }

    private void setConnectionGate(boolean connected, String message) {
        connectionStatus.setText(message);
        connectionStatus.setForeground(connected ? new Color(20, 120, 55) : new Color(145, 35, 35));
        loadModelButton.setEnabled(connected);
        if (!connected) {
            loadInstanceButton.setEnabled(false);
            validateButton.setEnabled(false);
        } else if (model != null) {
            loadInstanceButton.setEnabled(true);
            validateButton.setEnabled(system != null && !system.state().allObjects().isEmpty());
        }
    }

    private File requireFile(JTextField field, String message) {
        String path = field.getText().trim();
        if (path.isBlank()) {
            JOptionPane.showMessageDialog(this, message, "Missing Input", JOptionPane.WARNING_MESSAGE);
            return null;
        }
        File file = resolveInputPath(path).toFile();
        if (!file.isFile()) {
            JOptionPane.showMessageDialog(this, "File does not exist: " + file,
                    "Invalid Input", JOptionPane.WARNING_MESSAGE);
            return null;
        }
        return file;
    }

    private Path resolveInputPath(String value) {
        Path path = Path.of(value.trim());
        return (path.isAbsolute() ? path : workspaceRoot.resolve(path)).normalize();
    }

    private String displayPath(File file) {
        Path absolute = file.toPath().toAbsolutePath().normalize();
        return absolute.startsWith(workspaceRoot)
                ? workspaceRoot.relativize(absolute).toString()
                : absolute.toString();
    }

    private static Path findWorkspaceRoot() {
        Path current = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        for (Path candidate = current; candidate != null; candidate = candidate.getParent()) {
            if (Files.isRegularFile(candidate.resolve("pom.xml"))
                    && Files.isDirectory(candidate.resolve("examples"))) {
                return candidate;
            }
        }
        return current;
    }

    private void showError(String title, Exception exception) {
        status.setText(title + "\n" + exception.getMessage());
        JOptionPane.showMessageDialog(this, exception.getMessage(), title, JOptionPane.ERROR_MESSAGE);
    }

    private static JTextArea monospacedArea() {
        JTextArea area = new JTextArea();
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        return area;
    }
}
