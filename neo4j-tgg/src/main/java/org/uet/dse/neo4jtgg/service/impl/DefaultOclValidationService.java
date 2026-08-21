package org.uet.dse.neo4jtgg.service.impl;

import org.neo4j.driver.Record;
import org.neo4j.driver.QueryRunner;
import org.neo4j.driver.Session;
import org.neo4j.driver.Transaction;
import org.neo4j.driver.Value;
import org.neo4j.driver.types.Node;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.sys.MSystem;
import org.uet.dse.neo4j.manager.Neo4jDriverManager;
import org.uet.dse.neo4j.oclite.Neo4jRepository;
import org.uet.dse.neo4j.oclite.ast.ASTContext;
import org.uet.dse.neo4j.oclite.ast.ASTBinary;
import org.uet.dse.neo4j.oclite.ast.ASTCollectionOp;
import org.uet.dse.neo4j.oclite.ast.ASTExpression;
import org.uet.dse.neo4j.oclite.ast.ASTFile;
import org.uet.dse.neo4j.oclite.ast.ASTIf;
import org.uet.dse.neo4j.oclite.ast.ASTIterator;
import org.uet.dse.neo4j.oclite.ast.ASTLet;
import org.uet.dse.neo4j.oclite.ast.ASTMethodCall;
import org.uet.dse.neo4j.oclite.ast.ASTNode;
import org.uet.dse.neo4j.oclite.ast.ASTNot;
import org.uet.dse.neo4j.oclite.ast.ASTProperty;
import org.uet.dse.neo4j.oclite.ast.ASTVar;
import org.uet.dse.neo4j.oclite.expr.ExecutionContext;
import org.uet.dse.neo4j.oclite.expr.ExpressionBinder;
import org.uet.dse.neo4j.oclite.expr.ExpressionNode;
import org.uet.dse.neo4jtgg.model.CypherCompilationResult;
import org.uet.dse.neo4jtgg.model.OclDualCheckResult;
import org.uet.dse.neo4jtgg.model.OclExecutionMode;
import org.uet.dse.neo4jtgg.model.OclFileCompilationResult;
import org.uet.dse.neo4jtgg.model.OclFileValidationResult;
import org.uet.dse.neo4jtgg.model.OclOperationRuleInputs;
import org.uet.dse.neo4jtgg.model.OclResultLocation;
import org.uet.dse.neo4jtgg.model.OclRuleDescriptor;
import org.uet.dse.neo4jtgg.model.OclRuleKind;
import org.uet.dse.neo4jtgg.model.OclRuleCompilationResult;
import org.uet.dse.neo4jtgg.model.OclRuleOwnerKind;
import org.uet.dse.neo4jtgg.model.OclRuleValidationResult;
import org.uet.dse.neo4jtgg.model.OclValidationResult;
import org.uet.dse.neo4jtgg.model.TggWorkspaceContext;
import org.uet.dse.neo4jtgg.model.WorkspaceSide;
import org.uet.dse.neo4jtgg.ocl.diagnostic.OclCompilationException;
import org.uet.dse.neo4jtgg.ocl.diagnostic.OclDiagnostic;
import org.uet.dse.neo4jtgg.ocl.OclBottomSeparationChecker;
import org.uet.dse.neo4jtgg.ocl.OclBottomToken;
import org.uet.dse.neo4jtgg.ocl.OclExecutionPremiseChecker;
import org.uet.dse.neo4jtgg.ocl.OclScalarClosureChecker;
import org.uet.dse.neo4j.encoding.CanonicalGraphEncoding;
import org.uet.dse.neo4jtgg.experiment.AdapterAdequacyCertificate;
import org.uet.dse.neo4jtgg.experiment.AdapterAdequacySnapshotReader;
import org.uet.dse.neo4jtgg.experiment.InstrumentedCompilationResult;
import org.uet.dse.neo4jtgg.service.OclValidationService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DefaultOclValidationService implements OclValidationService {

    @Override
    public OclValidationResult validate(TggWorkspaceContext context, WorkspaceSide side, String oclExpression) {
        OclFileValidationResult fileResult = validateFile(context, side, oclExpression);
        if (fileResult.getRuleResults().size() == 1) {
            OclRuleValidationResult ruleResult = fileResult.getRuleResults().get(0);
            return new OclValidationResult(ruleResult.isSuccess(),
                    ruleResult.getSummary(),
                    ruleResult.getGeneratedCypher(),
                    ruleResult.isFallbackUsed(),
                    ruleResult.getViolations());
        }
        return new OclValidationResult(fileResult.isSuccess(),
                fileResult.getSummary(),
                "",
                false,
                Map.of(),
                fileResult.toDisplayText());
    }

    @Override
    public OclFileValidationResult validateFile(TggWorkspaceContext context, WorkspaceSide side, String oclText) {
        return validateFile(context, side, oclText, Map.of(), false);
    }

    @Override
    public OclFileValidationResult validateFile(TggWorkspaceContext context,
                                                WorkspaceSide side,
                                                String oclText,
                                                Map<String, Map<String, Object>> ruleParameterValues) {
        return validateFile(context, side, oclText, ruleParameterValues, false);
    }

    @Override
    public OclFileValidationResult validateFile(TggWorkspaceContext context,
                                                WorkspaceSide side,
                                                String oclText,
                                                Map<String, Map<String, Object>> ruleParameterValues,
                                                boolean dualCheck) {
        long responseStartedAt = System.nanoTime();
        String requestScope = side.name() + ":document";
        if (Neo4jDriverManager.getInstance() == null || !Neo4jDriverManager.getInstance().isConnected()) {
            return new OclFileValidationResult(requestScope, false, "Neo4j is not connected.", List.of(), List.of(),
                    elapsedMillis(responseStartedAt), 0L, 0L, 0L, 0L);
        }

        MModel model = context.getWorkspaceDefinition() != null ? context.getWorkspaceDefinition().getModel(side) : null;
        if (model == null) {
            return new OclFileValidationResult(requestScope, false,
                    "No Neo4j-backed model metadata is available for " + side.getDisplayName() + ".",
                    List.of(), List.of(), elapsedMillis(responseStartedAt), 0L, 0L, 0L, 0L);
        }

        try {
            long parseStartedAt = System.nanoTime();
            ASTFile astFile = OclDocumentParser.parse(model, oclText);
            long parseTimeMs = elapsedMillis(parseStartedAt);

            DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
            long compileStartedAt = System.nanoTime();
            OclFileCompilationResult compilationResult =
                    compiler.compileFileWithCertifiedContextInvariants(astFile);
            long compileTimeMs = elapsedMillis(compileStartedAt);

            List<OclRuleValidationResult> ruleResults = new ArrayList<>();
            long executionTimeMs = 0L;
            long fallbackTimeMs = 0L;
            List<OclRuleDescriptor> rules = OclRuleExtractor.extractRules(astFile);
            List<OclRuleCompilationResult> compiledRules = compilationResult.getRuleResults();
            Map<Integer, OclRuleValidationResult> batchedContextResults = new LinkedHashMap<>();
            Map<Integer, Long> batchedContextExecutionTimes = new LinkedHashMap<>();
            Map<String, List<OclContextBatchQueryExecutor.BatchRule>> batchRulesByClass =
                    collectBatchedContextRules(rules, compiledRules);
            int compiledIndex = 0;
            try (Session sharedSession = Neo4jDriverManager.getInstance().openSession();
                 Transaction transaction = sharedSession.beginTransaction()) {
                String modelKey = CanonicalGraphEncoding.modelKey(model.name());
                MSystem sourceSystem = requireSourceSystem(context, model);
                var sourceAtStart = AdapterAdequacySnapshotReader.source(sourceSystem, model.name());
                var graphAtStart = AdapterAdequacySnapshotReader.graph(transaction, modelKey);
                OclBottomSeparationChecker.requireGraphSeparated(
                        transaction, modelKey);
                OclScalarClosureChecker.requireGraphClosed(
                        transaction, modelKey);
                List<InstrumentedCompilationResult> certifiedPlans = requireCertifiedContextPremises(
                        transaction, model.name(), compiler, rules, compiledRules);
                if (!certifiedPlans.isEmpty()) {
                    AdapterAdequacyCertificate.issue(new AdapterAdequacyCertificate.AdapterAdequacySnapshot(
                            "validation-service-read-transaction", model.name(), modelKey,
                            "OclCypherRenderer-direct-v1", sourceAtStart,
                            AdapterAdequacySnapshotReader.source(sourceSystem, model.name()), graphAtStart,
                            AdapterAdequacySnapshotReader.graph(transaction, modelKey), certifiedPlans));
                }
                for (List<OclContextBatchQueryExecutor.BatchRule> batchRules : batchRulesByClass.values()) {
                    OclContextBatchQueryExecutor.BatchExecutionResult batchExecutionResult =
                            OclContextBatchQueryExecutor.execute(transaction, batchRules);
                    executionTimeMs += batchExecutionResult.executionTimeMs();
                    for (OclContextBatchQueryExecutor.BatchRule batchRule : batchRules) {
                        Map<String, String> violations = batchExecutionResult.violationsByRuleIndex()
                                .getOrDefault(batchRule.ruleIndex(), Map.of());
                        OclRuleValidationResult batchResult = compiledValidationFromViolations(
                                batchRule.rule(),
                                batchRule.compiledRule().getCompilation(),
                                violations,
                                "invariant",
                                batchExecutionResult.executionTimeMs());
                        batchedContextResults.put(batchRule.ruleIndex(), batchResult);
                        batchedContextExecutionTimes.put(batchRule.ruleIndex(), batchExecutionResult.executionTimeMs());
                    }
                }
                for (int ruleIndex = 0; ruleIndex < rules.size(); ruleIndex++) {
                    OclRuleDescriptor rule = rules.get(ruleIndex);
                    Map<String, Object> documentRuleParameters = lookupRuleParameters(rule, ruleParameterValues);
                    if (rule.ast() instanceof ASTContext astContext) {
                        OclRuleCompilationResult compiledRule = compiledRules.get(compiledIndex++);
                        CypherCompilationResult compilation = compiledRule.getCompilation();
                        if (compilation.isSupported()) {
                            OclRuleValidationResult ruleResult = batchedContextResults.get(ruleIndex);
                            if (ruleResult == null) {
                                ruleResult = validateContextWithCypher(
                                        transaction, rule, astContext, compilation);
                            }
                            long batchExecutionTime = batchedContextExecutionTimes.getOrDefault(ruleIndex, 0L);
                            if (dualCheck) {
                                ruleResult = attachDualCheck(model, rule, astContext, compilation, ruleResult);
                            }
                            ruleResult = withRuleTiming(ruleResult, 0L, compiledRule.getCompilationTimeMs(), 0L);
                            ruleResults.add(ruleResult);
                            if (dualCheck) {
                                executionTimeMs += Math.max(0L, ruleResult.getExecutionTimeMs() - batchExecutionTime);
                                fallbackTimeMs += ruleResult.getFallbackTimeMs();
                            }
                        } else {
                            OclRuleValidationResult ruleResult = withRuleTiming(
                                    fallbackContextEvaluation(model, rule, astContext,
                                            compilation.getCypher(), false, compilation.getDiagnostics()),
                                    0L, compiledRule.getCompilationTimeMs(), 0L);
                            ruleResults.add(ruleResult);
                            executionTimeMs += ruleResult.getExecutionTimeMs();
                            fallbackTimeMs += ruleResult.getFallbackTimeMs();
                        }
                        continue;
                    }

                    if (rule.ownerKind() == OclRuleOwnerKind.OPERATION
                            && (rule.ruleKind() == OclRuleKind.PRE || rule.ruleKind() == OclRuleKind.POST)) {
                        OclRuleCompilationResult compiledRule = compiledRules.get(compiledIndex++);
                        CypherCompilationResult compilation = compiledRule.getCompilation();
                        if (compilation.isSupported()) {
                            List<String> missingParameters = findMissingParameters(rule, compiledRule, documentRuleParameters);
                            if (!missingParameters.isEmpty()) {
                                OclRuleValidationResult ruleResult = withRuleTiming(
                                        missingOperationParameters(rule, compiledRule, missingParameters),
                                        0L, compiledRule.getCompilationTimeMs(), 0L);
                                ruleResults.add(ruleResult);
                                continue;
                            }
                            if (rule.ruleKind() == OclRuleKind.PRE && documentRuleParameters.isEmpty()) {
                                OclRuleValidationResult ruleResult = withRuleTiming(
                                        skippedOperationPrecondition(rule, compiledRule),
                                        0L, compiledRule.getCompilationTimeMs(), 0L);
                                ruleResults.add(ruleResult);
                                continue;
                            }
                            String violationMessage = rule.ruleKind() == OclRuleKind.POST
                                    ? "Postcondition `" + displayRuleName(rule) + "` violated"
                                    : "Precondition `" + displayRuleName(rule) + "` violated";
                            String ruleLabel = rule.ruleKind() == OclRuleKind.POST ? "postcondition" : "precondition";
                            OclRuleValidationResult ruleResult =
                                    validateCompiledRule(transaction, rule, compilation, documentRuleParameters,
                                            violationMessage, ruleLabel);
                            if (dualCheck) {
                                ruleResult = attachDualCheckOperation(model, rule, compilation, documentRuleParameters, ruleResult);
                            }
                            ruleResult = withRuleTiming(ruleResult, 0L, compiledRule.getCompilationTimeMs(), 0L);
                            ruleResults.add(ruleResult);
                            executionTimeMs += ruleResult.getExecutionTimeMs();
                            fallbackTimeMs += ruleResult.getFallbackTimeMs();
                        } else {
                            OclRuleValidationResult ruleResult = withRuleTiming(
                                    unsupportedRuleValidation(rule, compiledRule),
                                    0L, compiledRule.getCompilationTimeMs(), 0L);
                            ruleResults.add(ruleResult);
                        }
                        continue;
                    }

                    if (rule.expression() != null && rule.ownerKind() == OclRuleOwnerKind.CLASS && rule.className() == null) {
                        OclRuleCompilationResult compiledRule = compiledRules.get(compiledIndex++);
                        CypherCompilationResult compilation = compiledRule.getCompilation();
                        OclRuleValidationResult ruleResult;
                        if (compilation.isSupported()) {
                            ruleResult = validateCompiledExpression(transaction, rule, compilation);
                            ruleResult = withRuleTiming(ruleResult, 0L, compiledRule.getCompilationTimeMs(), 0L);
                        } else {
                            ruleResult = withRuleTiming(
                                    fallbackExpressionEvaluation(model, rule, compilation.getCypher(),
                                            compilation.getDiagnostics()),
                                    0L, compiledRule.getCompilationTimeMs(), 0L);
                        }
                        ruleResults.add(ruleResult);
                        executionTimeMs += ruleResult.getExecutionTimeMs();
                        fallbackTimeMs += ruleResult.getFallbackTimeMs();
                        continue;
                    }

                    OclRuleCompilationResult compiledRule = compiledRules.get(compiledIndex++);
                    OclRuleValidationResult ruleResult = withRuleTiming(
                            unsupportedRuleValidation(rule, compiledRule),
                            0L, compiledRule.getCompilationTimeMs(), 0L);
                    ruleResults.add(ruleResult);
                    executionTimeMs += ruleResult.getExecutionTimeMs();
                }
                transaction.commit();
            }

            return OclFileValidationResult.fromRuleResults(requestScope, ruleResults,
                    compilationResult.getDocumentDiagnostics(),
                    elapsedMillis(responseStartedAt), parseTimeMs, compileTimeMs, executionTimeMs, fallbackTimeMs);
        } catch (OclCompilationException ex) {
            List<OclDiagnostic> diagnostics = List.of(ex.toDiagnostic());
            return new OclFileValidationResult(requestScope, false, "Validation failed: " + ex.getMessage(),
                    List.of(), diagnostics, elapsedMillis(responseStartedAt), 0L, 0L, 0L, 0L);
        } catch (Exception ex) {
            return new OclFileValidationResult(requestScope, false, "Validation failed: " + ex.getMessage(),
                    List.of(), List.of(), elapsedMillis(responseStartedAt), 0L, 0L, 0L, 0L);
        }
    }

    @Override
    public OclRuleValidationResult validateRule(TggWorkspaceContext context, WorkspaceSide side, String oclText,
                                                String contextClassName, String ruleName) {
        return validateRule(context, side, oclText, contextClassName, ruleName, Map.of());
    }

    @Override
    public OclRuleValidationResult validateRule(TggWorkspaceContext context, WorkspaceSide side, String oclText,
                                                String contextClassName, String ruleName,
                                                Map<String, Object> parameterValues) {
        return validateRule(context, side, oclText, contextClassName, ruleName, parameterValues, false);
    }

    @Override
    public OclRuleValidationResult validateRule(TggWorkspaceContext context, WorkspaceSide side, String oclText,
                                                String contextClassName, String ruleName,
                                                Map<String, Object> parameterValues,
                                                boolean dualCheck) {
        return validateRuleInternal(context, side, oclText, contextClassName, null, ruleName, parameterValues,
                false, dualCheck);
    }

    @Override
    public OclRuleValidationResult validateOperationRule(TggWorkspaceContext context, WorkspaceSide side, String oclText,
                                                         String contextClassName, String operationName, String ruleName,
                                                         Map<String, Object> parameterValues) {
        return validateOperationRule(context, side, oclText, contextClassName, operationName, ruleName,
                new OclOperationRuleInputs(parameterValues), false);
    }

    @Override
    public OclRuleValidationResult validateOperationRule(TggWorkspaceContext context,
                                                         WorkspaceSide side,
                                                         String oclText,
                                                         String contextClassName,
                                                         String operationName,
                                                         String ruleName,
                                                         OclOperationRuleInputs inputs) {
        return validateOperationRule(context, side, oclText, contextClassName, operationName, ruleName, inputs, false);
    }

    @Override
    public OclRuleValidationResult validateOperationRule(TggWorkspaceContext context,
                                                         WorkspaceSide side,
                                                         String oclText,
                                                         String contextClassName,
                                                         String operationName,
                                                         String ruleName,
                                                         OclOperationRuleInputs inputs,
                                                         boolean dualCheck) {
        Map<String, Object> parameterValues = toRuntimeParameters(inputs);
        return validateRuleInternal(context, side, oclText, contextClassName, operationName, ruleName,
                parameterValues, true, dualCheck);
    }

    private OclRuleValidationResult validateRuleInternal(TggWorkspaceContext context,
                                                         WorkspaceSide side,
                                                         String oclText,
                                                         String contextClassName,
                                                         String operationName,
                                                         String ruleName,
                                                         Map<String, Object> parameterValues,
                                                         boolean operationScoped,
                                                         boolean dualCheck) {
        if (Neo4jDriverManager.getInstance() == null || !Neo4jDriverManager.getInstance().isConnected()) {
            return validationFailure(contextClassName, operationName, ruleName, "Neo4j is not connected.");
        }

        MModel model = context.getWorkspaceDefinition() != null ? context.getWorkspaceDefinition().getModel(side) : null;
        if (model == null) {
            return validationFailure(contextClassName, operationName, ruleName,
                    "No Neo4j-backed model metadata is available for " + side.getDisplayName() + ".");
        }

        try {
            try (Session premiseSession = Neo4jDriverManager.getInstance().openSession()) {
                OclBottomSeparationChecker.requireGraphSeparated(
                        premiseSession, CanonicalGraphEncoding.modelKey(model.name()));
                OclScalarClosureChecker.requireGraphClosed(
                        premiseSession, CanonicalGraphEncoding.modelKey(model.name()));
            }
            long parseStartedAt = System.nanoTime();
            ASTFile astFile = OclDocumentParser.parse(model, oclText);
            long parseTimeMs = elapsedMillis(parseStartedAt);
            DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
            long compileStartedAt = System.nanoTime();
            OclFileCompilationResult compilationResult =
                    compiler.compileFileWithCertifiedContextInvariants(astFile);
            long compileTimeMs = elapsedMillis(compileStartedAt);
            long responseStartedAt = System.nanoTime();

            List<OclRuleDescriptor> rules = OclRuleExtractor.extractRules(astFile);
            List<OclRuleCompilationResult> compiledRules = compilationResult.getRuleResults();
            int compiledIndex = 0;
            for (OclRuleDescriptor rule : rules) {
                OclRuleCompilationResult compiledRule = compiledRules.get(compiledIndex++);
                CypherCompilationResult compilation = compiledRule.getCompilation();
                if (!(rule.ast() instanceof ASTContext astContext)) {
                    if (rule.ownerKind() == OclRuleOwnerKind.OPERATION
                            && (rule.ruleKind() == OclRuleKind.PRE || rule.ruleKind() == OclRuleKind.POST)
                            && matchesRuleTarget(rule, contextClassName, operationName, ruleName, operationScoped)) {
                        if (!compilation.isSupported()) {
                            return withRuleTiming(unsupportedRuleValidation(rule, compiledRule), parseTimeMs,
                                    compiledRule.getCompilationTimeMs(), responseStartedAt);
                        }
                        List<String> missingParameters = findMissingParameters(rule, compiledRule, parameterValues);
                        if (!missingParameters.isEmpty()) {
                            return withRuleTiming(missingOperationParameters(rule, compiledRule, missingParameters),
                                    parseTimeMs, compiledRule.getCompilationTimeMs(), responseStartedAt);
                        }
                        String violationMessage = rule.ruleKind() == OclRuleKind.POST
                                ? "Postcondition `" + displayRuleName(rule) + "` violated"
                                : "Precondition `" + displayRuleName(rule) + "` violated";
                        String ruleLabel = rule.ruleKind() == OclRuleKind.POST ? "postcondition" : "precondition";
                        OclRuleValidationResult ruleResult = validateCompiledRule(rule, compilation, parameterValues,
                                violationMessage, ruleLabel);
                        if (dualCheck) {
                            ruleResult = attachDualCheckOperation(model, rule, compilation, parameterValues, ruleResult);
                        }
                        return withRuleTiming(ruleResult,
                                parseTimeMs, compiledRule.getCompilationTimeMs(), responseStartedAt);
                    }
                    if (matchesRuleTarget(rule, contextClassName, operationName, ruleName, operationScoped)) {
                        return withRuleTiming(unsupportedRuleValidation(rule, compiledRule), parseTimeMs,
                                compiledRule.getCompilationTimeMs(), responseStartedAt);
                    }
                    continue;
                }
                if (!matchesRuleTarget(rule, contextClassName, operationName, ruleName, operationScoped)) {
                    continue;
                }
                if (compilation.isSupported()) {
                    OclRuleValidationResult ruleResult;
                    String modelKey = CanonicalGraphEncoding.modelKey(model.name());
                    MSystem sourceSystem = requireSourceSystem(context, model);
                    try (Session neo4jSession = Neo4jDriverManager.getInstance().openSession();
                         Transaction transaction = neo4jSession.beginTransaction()) {
                        var sourceAtStart = AdapterAdequacySnapshotReader.source(sourceSystem, model.name());
                        var graphAtStart = AdapterAdequacySnapshotReader.graph(transaction, modelKey);
                        OclBottomSeparationChecker.requireGraphSeparated(transaction, modelKey);
                        OclScalarClosureChecker.requireGraphClosed(transaction, modelKey);
                        InstrumentedCompilationResult certified = requireCertifiedContextPremise(
                                transaction, model.name(), compiler, astContext, compiledRule);
                        AdapterAdequacyCertificate.issue(
                                new AdapterAdequacyCertificate.AdapterAdequacySnapshot(
                                        "validation-rule-read-transaction", model.name(), modelKey,
                                        "OclCypherRenderer-direct-v1", sourceAtStart,
                                        AdapterAdequacySnapshotReader.source(sourceSystem, model.name()),
                                        graphAtStart, AdapterAdequacySnapshotReader.graph(transaction, modelKey),
                                        List.of(certified)));
                        ruleResult = validateContextWithCypher(
                                transaction, rule, astContext, compilation);
                        transaction.commit();
                    }
                    if (dualCheck) {
                        ruleResult = attachDualCheck(model, rule, astContext, compilation, ruleResult);
                    }
                    return withRuleTiming(ruleResult, parseTimeMs,
                            compiledRule.getCompilationTimeMs(), responseStartedAt);
                }
                return withRuleTiming(fallbackContextEvaluation(model, rule, astContext, compilation.getCypher(), false,
                                compilation.getDiagnostics()),
                        parseTimeMs, compiledRule.getCompilationTimeMs(), responseStartedAt);
            }
            return withRuleTiming(validationFailure(contextClassName, operationName, ruleName,
                            "Rule `" + formatRuleTarget(contextClassName, operationName, ruleName)
                                    + "` was not found in the current OCL document."),
                    parseTimeMs, compileTimeMs, responseStartedAt);
        } catch (OclCompilationException ex) {
            return validationFailure(contextClassName, operationName, ruleName, "Validation failed: " + ex.getMessage(),
                    List.of(ex.toDiagnostic()));
        } catch (Exception ex) {
            return validationFailure(contextClassName, operationName, ruleName, "Validation failed: " + ex.getMessage());
        }
    }

    private OclRuleValidationResult validateContextWithCypher(OclRuleDescriptor rule,
                                                              ASTContext astContext,
                                                              CypherCompilationResult compilation) {
        try (Session neo4jSession = Neo4jDriverManager.getInstance().openSession()) {
            return validateContextWithCypher(neo4jSession, rule, astContext, compilation);
        }
    }

    private OclRuleValidationResult validateContextWithCypher(QueryRunner neo4jSession,
                                                              OclRuleDescriptor rule,
                                                              ASTContext astContext,
                                                              CypherCompilationResult compilation) {
        return validateCompiledRule(neo4jSession, rule, compilation, Map.of(),
                "Invariant `" + astContext.invName + "` violated", "invariant");
    }

    private OclRuleValidationResult validateCompiledRule(OclRuleDescriptor rule,
                                                         CypherCompilationResult compilation,
                                                         Map<String, Object> runtimeParameters,
                                                         String violationMessage,
                                                         String ruleLabel) {
        try (Session neo4jSession = Neo4jDriverManager.getInstance().openSession()) {
            return validateCompiledRule(neo4jSession, rule, compilation, runtimeParameters, violationMessage, ruleLabel);
        }
    }

    private OclRuleValidationResult validateCompiledRule(QueryRunner neo4jSession,
                                                         OclRuleDescriptor rule,
                                                         CypherCompilationResult compilation,
                                                         Map<String, Object> runtimeParameters,
                                                         String violationMessage,
                                                         String ruleLabel) {
        Map<String, String> violations = new LinkedHashMap<>();
        long executionStartedAt = System.nanoTime();
        List<Record> records = neo4jSession.run(compilation.getCypher(),
                mergeQueryParameters(compilation.getParameters(), runtimeParameters)).list();
        for (Record record : records) {
            String useId = record.get("useId").asString();
            violations.put(useId, violationMessage);
        }

        String summary = violations.isEmpty()
                ? "SUCCESS: " + ruleLabel + " `" + displayRuleName(rule) + "` passed on Neo4j."
                : "FAILURE: " + ruleLabel + " `" + displayRuleName(rule) + "` violated for " + violations.size() + " object(s).";
        return new OclRuleValidationResult(rule.ownerKind(), rule.ruleKind(), rule.className(),
                rule.operationName(), rule.attributeName(), rule.ruleName(), violations.isEmpty(),
                true, false, OclExecutionMode.COMPILED, summary, compilation.getCypher(), violations,
                compilation.getDiagnostics(), 0L, 0L, 0L, elapsedMillis(executionStartedAt), 0L,
                inferResultLocation(rule.className(), rule.ruleName(), violations, compilation.getDiagnostics()),
                inferRequiredInputs(rule));
    }

    private List<InstrumentedCompilationResult> requireCertifiedContextPremises(
            QueryRunner session,
            String modelName,
            DefaultOclToCypherCompiler compiler,
            List<OclRuleDescriptor> rules,
            List<OclRuleCompilationResult> compiledRules) {
        if (rules.size() != compiledRules.size()) {
            throw new IllegalStateException("Certified premise gate cannot align parsed and compiled rules");
        }
        List<InstrumentedCompilationResult> certifiedPlans = new ArrayList<>();
        for (int index = 0; index < rules.size(); index++) {
            OclRuleDescriptor rule = rules.get(index);
            if (rule.ast() instanceof ASTContext contextInvariant) {
                InstrumentedCompilationResult certified = requireCertifiedContextPremise(
                        session, modelName, compiler, contextInvariant, compiledRules.get(index));
                if (certified != null) certifiedPlans.add(certified);
            }
        }
        return List.copyOf(certifiedPlans);
    }

    private InstrumentedCompilationResult requireCertifiedContextPremise(
            QueryRunner session,
            String modelName,
            DefaultOclToCypherCompiler compiler,
            ASTContext contextInvariant,
            OclRuleCompilationResult compiledRule) {
        if (!compiledRule.isSupported()) {
            return null;
        }
        var certified = compiler.compileInvariantInstrumented(contextInvariant);
        CypherCompilationResult compilation = compiledRule.getCompilation();
        if (!certified.cypher().equals(compilation.getCypher())
                || !certified.parameters().equals(compilation.getParameters())) {
            throw new IllegalStateException(
                    "Certified context compilation drifted between file and instrumented entry points");
        }
        OclExecutionPremiseChecker.requireGeneratedBottomSeparated(certified.parameters());
        OclExecutionPremiseChecker.requireGraphScalarClosed(
                session, modelName, certified.validationAlgebra());
        return certified;
    }

    private MSystem requireSourceSystem(TggWorkspaceContext context, MModel model) {
        if (context == null || context.getSession() == null || !context.getSession().hasSystem()) {
            throw new IllegalStateException(
                    "AdapterAdequate requires the native USE source state used to encode the graph");
        }
        MSystem system = context.getSession().system();
        if (!model.name().equals(system.model().name())) {
            throw new IllegalStateException("AdapterAdequate source/model mismatch: expected "
                    + model.name() + " but USE session contains " + system.model().name());
        }
        return system;
    }

    private OclRuleValidationResult validateCompiledExpression(QueryRunner neo4jSession,
                                                               OclRuleDescriptor rule,
                                                               CypherCompilationResult compilation) {
        long executionStartedAt = System.nanoTime();
        OclBottomToken.requireWellFormedGeneratedParameters(compilation.getParameters());
        List<Record> records = neo4jSession.run(compilation.getCypher(), compilation.getParameters()).list();
        Object result = null;
        if (!records.isEmpty() && records.get(0).containsKey("value")) {
            Value value = records.get(0).get("value");
            result = value.isNull() ? null : value.asObject();
        }
        boolean success = result instanceof Boolean ok ? ok : result != null;
        String summary = "Evaluation result: " + result;
        return new OclRuleValidationResult(rule.ownerKind(), rule.ruleKind(), rule.className(),
                rule.operationName(), rule.attributeName(), rule.ruleName(), success, true, false,
                OclExecutionMode.COMPILED, summary, compilation.getCypher(), Map.of(),
                compilation.getDiagnostics(), 0L, 0L, 0L, elapsedMillis(executionStartedAt), 0L,
                new OclResultLocation(rule.className(), rule.ruleName(), null, null, null, null, null, null, List.of()),
                inferRequiredInputs(rule));
    }

    private OclRuleValidationResult fallbackContextEvaluation(MModel model,
                                                              OclRuleDescriptor rule,
                                                              ASTContext astContext,
                                                              String generatedCypher,
                                                              boolean compilerSupported,
                                                              List<OclDiagnostic> diagnostics) {
        String modelName = model.name();
        ExpressionBinder binder = new ExpressionBinder(modelName, Map.of("self", astContext.className));
        Map<String, String> violations = new LinkedHashMap<>();
        long executionStartedAt = System.nanoTime();
        ExpressionNode expressionNode = binder.bind(astContext.expression);
        Neo4jRepository repository = new Neo4jRepository(modelName);
        for (Node node : repository.findAllInstancesOfClass(astContext.className)) {
            ExecutionContext executionContext = new ExecutionContext(modelName);
            executionContext.pushScope("self", node);
            executionContext.setVariable("self", node);
            Object result = expressionNode.evaluate(executionContext);
            if (!(result instanceof Boolean ok) || !ok) {
                String useId = node.containsKey("use_id") ? node.get("use_id").asString() : String.valueOf(node.id());
                violations.put(useId, "Fallback evaluation reported false");
            }
        }

        String summary = violations.isEmpty()
                ? "SUCCESS: fallback evaluator passed on Neo4j."
                : "FAILURE: fallback evaluator found " + violations.size() + " violation(s).";
        long fallbackTimeMs = elapsedMillis(executionStartedAt);
        return new OclRuleValidationResult(rule.ownerKind(), rule.ruleKind(), rule.className(),
                rule.operationName(), rule.attributeName(), rule.ruleName(), violations.isEmpty(), compilerSupported, true,
                compilerSupported ? OclExecutionMode.FALLBACK : OclExecutionMode.UNSUPPORTED,
                summary, generatedCypher, violations, diagnostics, 0L, 0L, 0L, fallbackTimeMs, fallbackTimeMs,
                inferResultLocation(rule.className(), rule.ruleName(), violations, diagnostics),
                inferRequiredInputs(rule));
    }

    private OclRuleValidationResult fallbackExpressionEvaluation(MModel model,
                                                                 OclRuleDescriptor rule,
                                                                 String generatedCypher,
                                                                 List<OclDiagnostic> diagnostics) {
        String modelName = model.name();
        ExpressionBinder binder = new ExpressionBinder(modelName);
        long executionStartedAt = System.nanoTime();
        ExpressionNode expressionNode = binder.bind(rule.ast());
        Object result = expressionNode.evaluate(new ExecutionContext(modelName));
        String summary = "Evaluation result: " + result;
        boolean success = result instanceof Boolean ok ? ok : result != null;
        long fallbackTimeMs = elapsedMillis(executionStartedAt);
        return new OclRuleValidationResult(rule.ownerKind(), rule.ruleKind(), rule.className(),
                rule.operationName(), rule.attributeName(), rule.ruleName(), success, false, true,
                OclExecutionMode.UNSUPPORTED, summary, generatedCypher, Map.of(), diagnostics,
                0L, 0L, 0L, fallbackTimeMs, fallbackTimeMs,
                new OclResultLocation(rule.className(), rule.ruleName(), null, null, null, null, null, null, List.of()),
                inferRequiredInputs(rule));
    }

    private OclRuleValidationResult attachDualCheck(MModel model,
                                                    OclRuleDescriptor rule,
                                                    ASTContext astContext,
                                                    CypherCompilationResult compilation,
                                                    OclRuleValidationResult compiledResult) {
        try {
            OclRuleValidationResult fallbackResult = fallbackContextEvaluation(model, rule, astContext,
                    compilation.getCypher(), true, compilation.getDiagnostics());
            OclDualCheckResult dualCheckResult = OclDualCheckResult.compare(
                    compiledResult.getViolations(),
                    fallbackResult.getViolations());
            return compiledResult.withDualCheck(dualCheckResult,
                    fallbackResult.getExecutionTimeMs(),
                    fallbackResult.getFallbackTimeMs());
        } catch (RuntimeException ex) {
            return compiledResult.withDualCheck(OclDualCheckResult.error(ex.getMessage()), 0L, 0L);
        }
    }

    private OclRuleValidationResult attachDualCheckOperation(MModel model,
                                                             OclRuleDescriptor rule,
                                                             CypherCompilationResult compilation,
                                                             Map<String, Object> runtimeParameters,
                                                             OclRuleValidationResult compiledResult) {
        try {
            OclRuleValidationResult fallbackResult = fallbackOperationEvaluation(model, rule, compilation.getCypher(),
                    true, compilation.getDiagnostics(), runtimeParameters);
            OclDualCheckResult dualCheckResult = OclDualCheckResult.compare(
                    compiledResult.getViolations(),
                    fallbackResult.getViolations());
            return compiledResult.withDualCheck(dualCheckResult,
                    fallbackResult.getExecutionTimeMs(),
                    fallbackResult.getFallbackTimeMs());
        } catch (RuntimeException ex) {
            return compiledResult.withDualCheck(OclDualCheckResult.error(ex.getMessage()), 0L, 0L);
        }
    }

    private OclRuleValidationResult fallbackOperationEvaluation(MModel model,
                                                                OclRuleDescriptor rule,
                                                                String generatedCypher,
                                                                boolean compilerSupported,
                                                                List<OclDiagnostic> diagnostics,
                                                                Map<String, Object> runtimeParameters) {
        String modelName = model.name();
        List<String> localVariables = new ArrayList<>(runtimeParameters != null ? runtimeParameters.keySet() : List.of());
        ExpressionBinder binder = new ExpressionBinder(modelName, localVariables, Map.of("self", rule.className()));
        Map<String, String> violations = new LinkedHashMap<>();
        long executionStartedAt = System.nanoTime();
        ExpressionNode expressionNode = binder.bind(rule.expression());
        Neo4jRepository repository = new Neo4jRepository(modelName);
        for (Node node : repository.findAllInstancesOfClass(rule.className())) {
            ExecutionContext executionContext = new ExecutionContext(modelName);
            executionContext.pushScope("self", node);
            executionContext.setVariable("self", node);
            if (runtimeParameters != null) {
                for (Map.Entry<String, Object> entry : runtimeParameters.entrySet()) {
                    executionContext.setVariable(entry.getKey(), entry.getValue());
                }
            }
            Object result = expressionNode.evaluate(executionContext);
            if (!(result instanceof Boolean ok) || !ok) {
                String useId = node.containsKey("use_id") ? node.get("use_id").asString() : String.valueOf(node.id());
                violations.put(useId, "Fallback evaluation reported false");
            }
        }

        String ruleLabel = rule.ruleKind() == OclRuleKind.POST ? "postcondition" : "precondition";
        String summary = violations.isEmpty()
                ? "SUCCESS: fallback " + ruleLabel + " evaluator passed on Neo4j."
                : "FAILURE: fallback " + ruleLabel + " evaluator found " + violations.size() + " violation(s).";
        long fallbackTimeMs = elapsedMillis(executionStartedAt);
        return new OclRuleValidationResult(rule.ownerKind(), rule.ruleKind(), rule.className(),
                rule.operationName(), rule.attributeName(), rule.ruleName(), violations.isEmpty(), compilerSupported, true,
                compilerSupported ? OclExecutionMode.FALLBACK : OclExecutionMode.UNSUPPORTED,
                summary, generatedCypher, violations, diagnostics, 0L, 0L, 0L, fallbackTimeMs, fallbackTimeMs,
                inferResultLocation(rule.className(), rule.ruleName(), violations, diagnostics),
                inferRequiredInputs(rule));
    }

    private Map<String, List<OclContextBatchQueryExecutor.BatchRule>> collectBatchedContextRules(List<OclRuleDescriptor> rules,
                                                                                                  List<OclRuleCompilationResult> compiledRules) {
        Map<String, List<OclContextBatchQueryExecutor.BatchRule>> batchRulesByClass = new LinkedHashMap<>();
        int compiledIndex = 0;
        for (int ruleIndex = 0; ruleIndex < rules.size(); ruleIndex++) {
            OclRuleDescriptor rule = rules.get(ruleIndex);
            if (rule.ast() instanceof ASTContext astContext) {
                OclRuleCompilationResult compiledRule = compiledRules.get(compiledIndex++);
                if (compiledRule.isSupported()) {
                    batchRulesByClass
                            .computeIfAbsent(astContext.className, ignored -> new ArrayList<>())
                            .add(new OclContextBatchQueryExecutor.BatchRule(
                                    ruleIndex,
                                    rule,
                                    compiledRule,
                                    "Invariant `" + astContext.invName + "` violated"));
                }
                continue;
            }
            if (rule.ownerKind() == OclRuleOwnerKind.OPERATION
                    && (rule.ruleKind() == OclRuleKind.PRE || rule.ruleKind() == OclRuleKind.POST
                    || rule.ruleKind() == OclRuleKind.BODY)) {
                compiledIndex++;
                continue;
            }
            if (rule.ownerKind() == OclRuleOwnerKind.ATTRIBUTE
                    && (rule.ruleKind() == OclRuleKind.INIT || rule.ruleKind() == OclRuleKind.DERIVE)) {
                compiledIndex++;
            }
        }
        return batchRulesByClass;
    }

    private OclRuleValidationResult compiledValidationFromViolations(OclRuleDescriptor rule,
                                                                     CypherCompilationResult compilation,
                                                                     Map<String, String> violations,
                                                                     String ruleLabel,
                                                                     long executionTimeMs) {
        String summary = violations.isEmpty()
                ? "SUCCESS: " + ruleLabel + " `" + displayRuleName(rule) + "` passed on Neo4j."
                : "FAILURE: " + ruleLabel + " `" + displayRuleName(rule) + "` violated for " + violations.size() + " object(s).";
        return new OclRuleValidationResult(rule.ownerKind(), rule.ruleKind(), rule.className(),
                rule.operationName(), rule.attributeName(), rule.ruleName(), violations.isEmpty(),
                true, false, OclExecutionMode.COMPILED, summary, compilation.getCypher(), violations,
                compilation.getDiagnostics(), 0L, 0L, 0L, executionTimeMs, 0L,
                inferResultLocation(rule.className(), rule.ruleName(), violations, compilation.getDiagnostics()),
                inferRequiredInputs(rule));
    }

    private OclRuleValidationResult validationFailure(String contextClassName,
                                                      String operationName,
                                                      String ruleName,
                                                      String summary) {
        return validationFailure(contextClassName, operationName, ruleName, summary, List.of());
    }

    private OclRuleValidationResult unsupportedRuleValidation(OclRuleDescriptor rule, OclRuleCompilationResult compiledRule) {
        boolean compilerSupported = compiledRule.isSupported();
        String summary;
        OclExecutionMode executionMode;
        if (rule.ownerKind() == OclRuleOwnerKind.OPERATION && rule.ruleKind() == OclRuleKind.PRE && compilerSupported) {
            summary = "Compiled operation precondition is ready, but document-level validation skipped it because invocation parameter values were not provided.";
            executionMode = OclExecutionMode.SKIPPED;
        } else {
            summary = "Unsupported OCL rule kind `" + rule.ruleKind().name().toLowerCase()
                    + "` for current validation path. Only `Class -> inv` is executable right now.";
            executionMode = OclExecutionMode.UNSUPPORTED;
        }
        return new OclRuleValidationResult(rule.ownerKind(), rule.ruleKind(), rule.className(),
                rule.operationName(), rule.attributeName(), rule.ruleName(),
                false, compilerSupported, false, executionMode, summary,
                compilerSupported ? compiledRule.getCypher() : "",
                Map.of(), compiledRule.getDiagnostics(), 0L, 0L, compiledRule.getCompilationTimeMs(), 0L, 0L,
                inferResultLocation(rule.className(), rule.ruleName(), Map.of(), compiledRule.getDiagnostics()),
                inferRequiredInputs(rule));
    }

    private OclRuleValidationResult skippedOperationPrecondition(OclRuleDescriptor rule,
                                                                 OclRuleCompilationResult compiledRule) {
        return new OclRuleValidationResult(rule.ownerKind(), rule.ruleKind(), rule.className(),
                rule.operationName(), rule.attributeName(), rule.ruleName(),
                false, true, false, OclExecutionMode.SKIPPED,
                "Compiled operation precondition is ready, but document-level validation skipped it because invocation parameter values were not provided.",
                compiledRule.getCypher(), Map.of(), compiledRule.getDiagnostics(), 0L, 0L, compiledRule.getCompilationTimeMs(), 0L, 0L,
                inferResultLocation(rule.className(), rule.ruleName(), Map.of(), compiledRule.getDiagnostics()),
                inferRequiredInputs(rule));
    }

    private OclRuleValidationResult missingOperationParameters(OclRuleDescriptor rule,
                                                               OclRuleCompilationResult compiledRule,
                                                               List<String> missingParameters) {
        CypherCompilationResult compilation = compiledRule.getCompilation();
        String ruleLabel = rule.ruleKind() == OclRuleKind.POST ? "postcondition" : "precondition";
        String summary = "Missing invocation parameter values for operation " + ruleLabel + " `"
                + displayRuleName(rule) + "`: " + String.join(", ", missingParameters) + ".";
        return new OclRuleValidationResult(rule.ownerKind(), rule.ruleKind(), rule.className(),
                rule.operationName(), rule.attributeName(), rule.ruleName(),
                false, true, false, OclExecutionMode.ERROR, summary, compilation.getCypher(),
                Map.of(), compilation.getDiagnostics(), 0L, 0L, compiledRule.getCompilationTimeMs(), 0L, 0L,
                inferResultLocation(rule.className(), rule.ruleName(), Map.of(), compilation.getDiagnostics()),
                inferRequiredInputs(rule));
    }

    private List<String> findMissingParameters(OclRuleDescriptor rule,
                                               OclRuleCompilationResult compiledRule,
                                               Map<String, Object> parameterValues) {
        List<String> missing = new ArrayList<>();
        Map<String, Object> safeParameters = parameterValues != null ? parameterValues : Map.of();
        for (String parameterName : rule.parameterNames()) {
            if (!safeParameters.containsKey(parameterName)) {
                missing.add(parameterName);
            }
        }
        if (compiledRule.getRequiredInputs().contains("resultValue") && !safeParameters.containsKey("result")) {
            missing.add("result");
        }
        return missing;
    }

    private Map<String, Object> toRuntimeParameters(OclOperationRuleInputs inputs) {
        if (inputs == null) {
            return Map.of();
        }
        Map<String, Object> runtimeParameters = new LinkedHashMap<>(inputs.getParameterValues());
        if (inputs.getResultValue() != null) {
            runtimeParameters.put("result", inputs.getResultValue());
        }
        return Map.copyOf(runtimeParameters);
    }

    private Map<String, Object> mergeQueryParameters(Map<String, Object> compilationParameters,
                                                     Map<String, Object> runtimeParameters) {
        OclBottomToken.requireWellFormedGeneratedParameters(compilationParameters);
        OclBottomToken.requireNoExternalToken(runtimeParameters);
        if (runtimeParameters != null) {
            OclScalarClosureChecker.requireValuesClosed(runtimeParameters.values(), "runtime parameter");
        }
        Map<String, Object> merged = new LinkedHashMap<>();
        if (compilationParameters != null) {
            merged.putAll(compilationParameters);
        }
        if (runtimeParameters != null) {
            merged.putAll(runtimeParameters);
        }
        return merged;
    }

    private Map<String, Object> lookupRuleParameters(OclRuleDescriptor rule,
                                                     Map<String, Map<String, Object>> ruleParameterValues) {
        if (ruleParameterValues == null || ruleParameterValues.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> direct = ruleParameterValues.get(rule.qualifiedName());
        if (direct != null) {
            return direct;
        }
        String ruleName = displayRuleName(rule);
        Map<String, Object> fallback = ruleParameterValues.get(ruleName);
        return fallback != null ? fallback : Map.of();
    }

    private String displayRuleName(OclRuleDescriptor rule) {
        StringBuilder builder = new StringBuilder();
        if (rule.className() != null && !rule.className().isBlank()) {
            builder.append(rule.className());
        }
        if (rule.operationName() != null && !rule.operationName().isBlank()) {
            if (builder.length() > 0) {
                builder.append("::");
            }
            builder.append(rule.operationName());
        }
        if (rule.attributeName() != null && !rule.attributeName().isBlank()) {
            if (builder.length() > 0) {
                builder.append("::");
            }
            builder.append(rule.attributeName());
        }
        if (rule.ruleName() != null && !rule.ruleName().isBlank()) {
            if (builder.length() > 0) {
                builder.append("::");
            }
            builder.append(rule.ruleName());
        }
        return builder.length() == 0 ? "<unnamed>" : builder.toString();
    }

    private OclRuleValidationResult validationFailure(String contextClassName,
                                                      String operationName,
                                                      String ruleName,
                                                      String summary,
                                                      List<OclDiagnostic> diagnostics) {
        OclRuleOwnerKind ownerKind = operationName != null && !operationName.isBlank()
                ? OclRuleOwnerKind.OPERATION
                : OclRuleOwnerKind.CLASS;
        OclRuleKind ruleKind = operationName != null && !operationName.isBlank()
                ? OclRuleKind.PRE
                : OclRuleKind.INV;
        return new OclRuleValidationResult(ownerKind, ruleKind, contextClassName, operationName, null,
                ruleName, false, false, false, OclExecutionMode.ERROR, summary, "", Map.of(), diagnostics,
                0L, 0L, 0L, 0L, 0L,
                inferResultLocation(contextClassName, ruleName, Map.of(), diagnostics), List.of());
    }

    private OclRuleValidationResult withRuleTiming(OclRuleValidationResult result,
                                                   long parseTimeMs,
                                                   long compileTimeMs,
                                                   long responseStartedAt) {
        if (result == null) {
            return null;
        }
        long responseTimeMs = responseStartedAt > 0L ? elapsedMillis(responseStartedAt) : 0L;
        return result.withTiming(responseTimeMs, parseTimeMs, compileTimeMs);
    }

    private boolean matchesRuleTarget(OclRuleDescriptor rule,
                                      String contextClassName,
                                      String operationName,
                                      String ruleName,
                                      boolean operationScoped) {
        if (!java.util.Objects.equals(rule.className(), contextClassName)) {
            return false;
        }
        if (!java.util.Objects.equals(rule.ruleName(), ruleName)) {
            return false;
        }
        if (operationScoped) {
            return java.util.Objects.equals(rule.operationName(), operationName);
        }
        return true;
    }

    private String formatRuleTarget(String contextClassName, String operationName, String ruleName) {
        StringBuilder builder = new StringBuilder();
        if (contextClassName != null && !contextClassName.isBlank()) {
            builder.append(contextClassName);
        }
        if (operationName != null && !operationName.isBlank()) {
            if (builder.length() > 0) {
                builder.append("::");
            }
            builder.append(operationName);
        }
        if (ruleName != null && !ruleName.isBlank()) {
            if (builder.length() > 0) {
                builder.append("::");
            }
            builder.append(ruleName);
        }
        return builder.length() == 0 ? "<unnamed>" : builder.toString();
    }

    private OclResultLocation inferResultLocation(String contextClassName,
                                                  String ruleName,
                                                  Map<String, String> violations,
                                                  List<OclDiagnostic> diagnostics) {
        if (diagnostics != null && !diagnostics.isEmpty()) {
            return OclResultLocation.fromDiagnostic(contextClassName, ruleName, diagnostics.get(0));
        }
        if (violations != null && !violations.isEmpty()) {
            return OclResultLocation.forViolations(contextClassName, ruleName, new ArrayList<>(violations.keySet()));
        }
        return new OclResultLocation(contextClassName, ruleName, null, null, null, null, null, null, List.of());
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }

    private List<String> inferRequiredInputs(OclRuleDescriptor rule) {
        List<String> requiredInputs = new ArrayList<>();
        switch (rule.ruleKind()) {
            case PRE -> {
                for (String parameterName : rule.parameterNames()) {
                    requiredInputs.add("parameter:" + parameterName);
                }
            }
            case POST -> {
                for (String parameterName : rule.parameterNames()) {
                    requiredInputs.add("parameter:" + parameterName);
                }
                if (referencesVariable(rule.expression(), "result")) {
                    requiredInputs.add("resultValue");
                }
            }
            case BODY -> {
                requiredInputs.add("self");
                for (String parameterName : rule.parameterNames()) {
                    requiredInputs.add("parameter:" + parameterName);
                }
            }
            case INIT, DERIVE -> requiredInputs.add("self");
            case INV -> {
            }
        }
        return List.copyOf(requiredInputs);
    }

    private boolean referencesVariable(ASTExpression expression, String variableName) {
        if (expression == null || variableName == null || variableName.isBlank()) {
            return false;
        }
        if (expression instanceof ASTVar variable) {
            return variableName.equals(variable.name);
        }
        if (expression instanceof ASTBinary binary) {
            return referencesVariable(binary.left, variableName)
                    || referencesVariable(binary.right, variableName);
        }
        if (expression instanceof ASTProperty property) {
            if (referencesVariable(property.source, variableName)) {
                return true;
            }
            for (ASTExpression qualifier : property.qualifiers) {
                if (referencesVariable(qualifier, variableName)) {
                    return true;
                }
            }
            return false;
        }
        if (expression instanceof ASTMethodCall methodCall) {
            if (referencesVariable(methodCall.source, variableName)) {
                return true;
            }
            for (ASTExpression argument : methodCall.args) {
                if (referencesVariable(argument, variableName)) {
                    return true;
                }
            }
            return false;
        }
        if (expression instanceof ASTCollectionOp collectionOp) {
            if (referencesVariable(collectionOp.source, variableName)) {
                return true;
            }
            for (ASTExpression argument : collectionOp.args) {
                if (referencesVariable(argument, variableName)) {
                    return true;
                }
            }
            return false;
        }
        if (expression instanceof ASTIterator iterator) {
            if (referencesVariable(iterator.source, variableName)) {
                return true;
            }
            if (variableName.equals(iterator.iteratorName)) {
                return false;
            }
            return referencesVariable(iterator.body, variableName);
        }
        if (expression instanceof ASTIf ifExpression) {
            return referencesVariable(ifExpression.condition, variableName)
                    || referencesVariable(ifExpression.thenBranch, variableName)
                    || referencesVariable(ifExpression.elseBranch, variableName);
        }
        if (expression instanceof ASTLet letExpression) {
            if (referencesVariable(letExpression.value, variableName)) {
                return true;
            }
            if (variableName.equals(letExpression.variableName)) {
                return false;
            }
            return referencesVariable(letExpression.body, variableName);
        }
        if (expression instanceof ASTNot not) {
            return referencesVariable(not.expression, variableName);
        }
        return false;
    }
}
