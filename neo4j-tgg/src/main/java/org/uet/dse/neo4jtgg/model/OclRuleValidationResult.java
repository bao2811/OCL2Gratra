package org.uet.dse.neo4jtgg.model;

import org.uet.dse.neo4jtgg.ocl.diagnostic.OclDiagnostic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OclRuleValidationResult {
    private final OclRuleOwnerKind ownerKind;
    private final OclRuleKind ruleKind;
    private final String contextClassName;
    private final String operationName;
    private final String attributeName;
    private final String ruleName;
    private final boolean success;
    private final boolean compilerSupported;
    private final boolean fallbackUsed;
    private final OclExecutionMode executionMode;
    private final String summary;
    private final String generatedCypher;
    private final Map<String, String> violations;
    private final List<OclDiagnostic> diagnostics;
    private final long responseTimeMs;
    private final long parseTimeMs;
    private final long compileTimeMs;
    private final long executionTimeMs;
    private final long fallbackTimeMs;
    private final OclResultLocation resultLocation;
    private final List<String> requiredInputs;
    private final OclDualCheckResult dualCheckResult;

    public OclRuleValidationResult(String contextClassName,
                                   String ruleName,
                                   boolean success,
                                   boolean compilerSupported,
                                   boolean fallbackUsed,
                                   String summary,
                                   String generatedCypher,
                                   Map<String, String> violations,
                                   List<OclDiagnostic> diagnostics) {
        this(OclRuleOwnerKind.CLASS, OclRuleKind.INV, contextClassName, null, null, ruleName,
                success, compilerSupported, fallbackUsed,
                inferExecutionMode(success, compilerSupported, fallbackUsed),
                summary, generatedCypher, violations, diagnostics, 0L, 0L, 0L, 0L, 0L,
                inferResultLocation(contextClassName, ruleName, violations, diagnostics), List.of(),
                OclDualCheckResult.disabled());
    }

    public OclRuleValidationResult(String contextClassName,
                                   String ruleName,
                                   boolean success,
                                   boolean compilerSupported,
                                   boolean fallbackUsed,
                                   OclExecutionMode executionMode,
                                   String summary,
                                   String generatedCypher,
                                   Map<String, String> violations,
                                   List<OclDiagnostic> diagnostics,
                                   long responseTimeMs,
                                   long parseTimeMs,
                                   long compileTimeMs,
                                   long executionTimeMs,
                                   long fallbackTimeMs,
                                   OclResultLocation resultLocation) {
        this(OclRuleOwnerKind.CLASS, OclRuleKind.INV, contextClassName, null, null, ruleName,
                success, compilerSupported, fallbackUsed, executionMode, summary, generatedCypher, violations,
                diagnostics, responseTimeMs, parseTimeMs, compileTimeMs, executionTimeMs, fallbackTimeMs,
                resultLocation, List.of(), OclDualCheckResult.disabled());
    }

    public OclRuleValidationResult(String contextClassName,
                                   String ruleName,
                                   boolean success,
                                   boolean compilerSupported,
                                   boolean fallbackUsed,
                                   OclExecutionMode executionMode,
                                   String summary,
                                   String generatedCypher,
                                   Map<String, String> violations,
                                   List<OclDiagnostic> diagnostics,
                                   long executionTimeMs,
                                   long fallbackTimeMs,
                                   OclResultLocation resultLocation) {
        this(contextClassName, ruleName, success, compilerSupported, fallbackUsed, executionMode, summary,
                generatedCypher, violations, diagnostics, 0L, 0L, 0L, executionTimeMs, fallbackTimeMs, resultLocation);
    }

    public OclRuleValidationResult(OclRuleOwnerKind ownerKind,
                                   OclRuleKind ruleKind,
                                   String contextClassName,
                                   String operationName,
                                   String attributeName,
                                   String ruleName,
                                   boolean success,
                                   boolean compilerSupported,
                                   boolean fallbackUsed,
                                   OclExecutionMode executionMode,
                                   String summary,
                                   String generatedCypher,
                                   Map<String, String> violations,
                                   List<OclDiagnostic> diagnostics,
                                   long responseTimeMs,
                                   long parseTimeMs,
                                   long compileTimeMs,
                                   long executionTimeMs,
                                   long fallbackTimeMs,
                                   OclResultLocation resultLocation,
                                   List<String> requiredInputs) {
        this(ownerKind, ruleKind, contextClassName, operationName, attributeName, ruleName,
                success, compilerSupported, fallbackUsed, executionMode, summary, generatedCypher, violations,
                diagnostics, responseTimeMs, parseTimeMs, compileTimeMs, executionTimeMs, fallbackTimeMs,
                resultLocation, requiredInputs, OclDualCheckResult.disabled());
    }

    public OclRuleValidationResult(OclRuleOwnerKind ownerKind,
                                   OclRuleKind ruleKind,
                                   String contextClassName,
                                   String operationName,
                                   String attributeName,
                                   String ruleName,
                                   boolean success,
                                   boolean compilerSupported,
                                   boolean fallbackUsed,
                                   OclExecutionMode executionMode,
                                   String summary,
                                   String generatedCypher,
                                   Map<String, String> violations,
                                   List<OclDiagnostic> diagnostics,
                                   long responseTimeMs,
                                   long parseTimeMs,
                                   long compileTimeMs,
                                   long executionTimeMs,
                                   long fallbackTimeMs,
                                   OclResultLocation resultLocation,
                                   List<String> requiredInputs,
                                   OclDualCheckResult dualCheckResult) {
        this.ownerKind = ownerKind;
        this.ruleKind = ruleKind;
        this.contextClassName = contextClassName;
        this.operationName = operationName;
        this.attributeName = attributeName;
        this.ruleName = ruleName;
        this.success = success;
        this.compilerSupported = compilerSupported;
        this.fallbackUsed = fallbackUsed;
        this.executionMode = executionMode;
        this.summary = summary;
        this.generatedCypher = generatedCypher;
        this.violations = new LinkedHashMap<>(violations);
        this.diagnostics = List.copyOf(new ArrayList<>(diagnostics));
        this.responseTimeMs = responseTimeMs;
        this.parseTimeMs = parseTimeMs;
        this.compileTimeMs = compileTimeMs;
        this.executionTimeMs = executionTimeMs;
        this.fallbackTimeMs = fallbackTimeMs;
        this.resultLocation = resultLocation;
        this.requiredInputs = List.copyOf(new ArrayList<>(requiredInputs != null ? requiredInputs : List.of()));
        this.dualCheckResult = dualCheckResult != null ? dualCheckResult : OclDualCheckResult.disabled();
    }

    public OclRuleValidationResult(OclRuleOwnerKind ownerKind,
                                   OclRuleKind ruleKind,
                                   String contextClassName,
                                   String operationName,
                                   String attributeName,
                                   String ruleName,
                                   boolean success,
                                   boolean compilerSupported,
                                   boolean fallbackUsed,
                                   OclExecutionMode executionMode,
                                   String summary,
                                   String generatedCypher,
                                   Map<String, String> violations,
                                   List<OclDiagnostic> diagnostics,
                                   long executionTimeMs,
                                   long fallbackTimeMs,
                                   OclResultLocation resultLocation,
                                   List<String> requiredInputs) {
        this(ownerKind, ruleKind, contextClassName, operationName, attributeName, ruleName,
                success, compilerSupported, fallbackUsed, executionMode, summary, generatedCypher, violations,
                diagnostics, 0L, 0L, 0L, executionTimeMs, fallbackTimeMs, resultLocation, requiredInputs,
                OclDualCheckResult.disabled());
    }

    public OclRuleValidationResult(OclRuleOwnerKind ownerKind,
                                   OclRuleKind ruleKind,
                                   String contextClassName,
                                   String operationName,
                                   String attributeName,
                                   String ruleName,
                                   boolean success,
                                   boolean compilerSupported,
                                   boolean fallbackUsed,
                                   OclExecutionMode executionMode,
                                   String summary,
                                   String generatedCypher,
                                   Map<String, String> violations,
                                   List<OclDiagnostic> diagnostics,
                                   long executionTimeMs,
                                   long fallbackTimeMs,
                                   OclResultLocation resultLocation,
                                   List<String> requiredInputs,
                                   OclDualCheckResult dualCheckResult) {
        this(ownerKind, ruleKind, contextClassName, operationName, attributeName, ruleName,
                success, compilerSupported, fallbackUsed, executionMode, summary, generatedCypher, violations,
                diagnostics, 0L, 0L, 0L, executionTimeMs, fallbackTimeMs, resultLocation, requiredInputs,
                dualCheckResult);
    }

    public OclRuleOwnerKind getOwnerKind() {
        return ownerKind;
    }

    public OclRuleKind getRuleKind() {
        return ruleKind;
    }

    public String getContextClassName() {
        return contextClassName;
    }

    public String getOperationName() {
        return operationName;
    }

    public String getAttributeName() {
        return attributeName;
    }

    public String getRuleName() {
        return ruleName;
    }

    public boolean isSuccess() {
        return success;
    }

    public boolean isCompilerSupported() {
        return compilerSupported;
    }

    public boolean isFallbackUsed() {
        return fallbackUsed;
    }

    public boolean isSkipped() {
        return executionMode == OclExecutionMode.SKIPPED;
    }

    public OclExecutionMode getExecutionMode() {
        return executionMode;
    }

    public String getSummary() {
        return summary;
    }

    public String getGeneratedCypher() {
        return generatedCypher;
    }

    public Map<String, String> getViolations() {
        return Collections.unmodifiableMap(violations);
    }

    public List<OclDiagnostic> getDiagnostics() {
        return Collections.unmodifiableList(diagnostics);
    }

    public long getResponseTimeMs() {
        return responseTimeMs;
    }

    public long getParseTimeMs() {
        return parseTimeMs;
    }

    public long getCompileTimeMs() {
        return compileTimeMs;
    }

    public long getExecutionTimeMs() {
        return executionTimeMs;
    }

    public long getFallbackTimeMs() {
        return fallbackTimeMs;
    }

    public OclResultLocation getResultLocation() {
        return resultLocation;
    }

    public List<String> getRequiredInputs() {
        return Collections.unmodifiableList(requiredInputs);
    }

    public OclDualCheckResult getDualCheckResult() {
        return dualCheckResult;
    }

    public boolean isDualCheckExecuted() {
        return dualCheckResult.executed();
    }

    public boolean hasDualCheckMismatch() {
        return dualCheckResult.executed() && !dualCheckResult.matched();
    }

    public OclRuleValidationResult withTiming(long responseTimeMs, long parseTimeMs, long compileTimeMs) {
        return new OclRuleValidationResult(ownerKind, ruleKind, contextClassName, operationName, attributeName, ruleName,
                success, compilerSupported, fallbackUsed, executionMode, summary, generatedCypher, violations, diagnostics,
                responseTimeMs, parseTimeMs, compileTimeMs, executionTimeMs, fallbackTimeMs, resultLocation, requiredInputs,
                dualCheckResult);
    }

    public OclRuleValidationResult withDualCheck(OclDualCheckResult dualCheckResult,
                                                 long additionalExecutionTimeMs,
                                                 long additionalFallbackTimeMs) {
        return new OclRuleValidationResult(ownerKind, ruleKind, contextClassName, operationName, attributeName, ruleName,
                success, compilerSupported, fallbackUsed, executionMode, summary, generatedCypher, violations, diagnostics,
                responseTimeMs, parseTimeMs, compileTimeMs,
                executionTimeMs + Math.max(0L, additionalExecutionTimeMs),
                fallbackTimeMs + Math.max(0L, additionalFallbackTimeMs),
                resultLocation, requiredInputs, dualCheckResult);
    }

    private static OclExecutionMode inferExecutionMode(boolean success, boolean compilerSupported, boolean fallbackUsed) {
        if (fallbackUsed) {
            return compilerSupported ? OclExecutionMode.FALLBACK : OclExecutionMode.UNSUPPORTED;
        }
        if (!success && !compilerSupported) {
            return OclExecutionMode.ERROR;
        }
        return OclExecutionMode.COMPILED;
    }

    private static OclResultLocation inferResultLocation(String contextClassName,
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
}
