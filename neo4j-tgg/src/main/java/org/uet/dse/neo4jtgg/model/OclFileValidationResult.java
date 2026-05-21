package org.uet.dse.neo4jtgg.model;

import org.uet.dse.neo4jtgg.ocl.diagnostic.OclDiagnostic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class OclFileValidationResult {
    private final String requestScope;
    private final boolean success;
    private final String summary;
    private final List<OclRuleValidationResult> ruleResults;
    private final List<OclDiagnostic> diagnostics;
    private final long responseTimeMs;
    private final long parseTimeMs;
    private final long compileTimeMs;
    private final long executionTimeMs;
    private final long fallbackTimeMs;

    public OclFileValidationResult(boolean success, String summary, List<OclRuleValidationResult> ruleResults) {
        this("document", success, summary, ruleResults, List.of(), 0L, 0L, 0L, 0L, 0L);
    }

    public OclFileValidationResult(String requestScope,
                                   boolean success,
                                   String summary,
                                   List<OclRuleValidationResult> ruleResults,
                                   List<OclDiagnostic> diagnostics,
                                   long responseTimeMs,
                                   long parseTimeMs,
                                   long compileTimeMs,
                                   long executionTimeMs,
                                   long fallbackTimeMs) {
        this.requestScope = requestScope;
        this.success = success;
        this.summary = summary;
        this.ruleResults = List.copyOf(new ArrayList<>(ruleResults));
        this.diagnostics = List.copyOf(new ArrayList<>(diagnostics));
        this.responseTimeMs = responseTimeMs;
        this.parseTimeMs = parseTimeMs;
        this.compileTimeMs = compileTimeMs;
        this.executionTimeMs = executionTimeMs;
        this.fallbackTimeMs = fallbackTimeMs;
    }

    public String getRequestScope() {
        return requestScope;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getSummary() {
        return summary;
    }

    public List<OclRuleValidationResult> getRuleResults() {
        return Collections.unmodifiableList(ruleResults);
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

    public int getRuleCount() {
        return ruleResults.size();
    }

    public long getPassCount() {
        return ruleResults.stream().filter(OclRuleValidationResult::isSuccess).count();
    }

    public long getFailCount() {
        return ruleResults.stream()
                .filter(result -> !result.isSuccess() && !result.isSkipped())
                .count();
    }

    public long getFallbackCount() {
        return ruleResults.stream().filter(OclRuleValidationResult::isFallbackUsed).count();
    }

    public long getSkippedCount() {
        return ruleResults.stream().filter(OclRuleValidationResult::isSkipped).count();
    }

    public long getUnsupportedCount() {
        return ruleResults.stream().filter(result -> !result.isCompilerSupported()).count();
    }

    public long getDualCheckExecutedCount() {
        return ruleResults.stream().filter(OclRuleValidationResult::isDualCheckExecuted).count();
    }

    public long getDualCheckMismatchCount() {
        return ruleResults.stream().filter(OclRuleValidationResult::hasDualCheckMismatch).count();
    }

    public static OclFileValidationResult fromRuleResults(List<OclRuleValidationResult> ruleResults) {
        return fromRuleResults("document", ruleResults, List.of(), 0L, 0L, 0L, 0L, 0L);
    }

    public static OclFileValidationResult fromRuleResults(String requestScope,
                                                          List<OclRuleValidationResult> ruleResults,
                                                          List<OclDiagnostic> diagnostics,
                                                          long responseTimeMs,
                                                          long parseTimeMs,
                                                          long compileTimeMs,
                                                          long executionTimeMs,
                                                          long fallbackTimeMs) {
        long passCount = ruleResults.stream().filter(OclRuleValidationResult::isSuccess).count();
        long skippedCount = ruleResults.stream().filter(OclRuleValidationResult::isSkipped).count();
        long failCount = ruleResults.stream()
                .filter(result -> !result.isSuccess() && !result.isSkipped())
                .count();
        long fallbackCount = ruleResults.stream().filter(OclRuleValidationResult::isFallbackUsed).count();
        long unsupportedCount = ruleResults.stream().filter(result -> !result.isCompilerSupported()).count();
        long dualCheckExecutedCount = ruleResults.stream().filter(OclRuleValidationResult::isDualCheckExecuted).count();
        long dualCheckMismatchCount = ruleResults.stream().filter(OclRuleValidationResult::hasDualCheckMismatch).count();
        String summary = "Validated " + ruleResults.size()
                + " OCL element(s): pass=" + passCount
                + ", fail=" + failCount
                + ", skipped=" + skippedCount
                + ", fallback=" + fallbackCount
                + ", unsupported=" + unsupportedCount
                + (dualCheckExecutedCount > 0
                ? ", dualCheckMismatch=" + dualCheckMismatchCount + "/" + dualCheckExecutedCount
                : "")
                + ", responseTimeMs=" + responseTimeMs
                + ", parseTimeMs=" + parseTimeMs
                + ", compileTimeMs=" + compileTimeMs
                + ", executionTimeMs=" + executionTimeMs
                + ", fallbackTimeMs=" + fallbackTimeMs + ".";
        boolean success = ruleResults.stream().allMatch(result -> result.isSuccess() || result.isSkipped());
        return new OclFileValidationResult(requestScope, success, summary, ruleResults, diagnostics,
                responseTimeMs, parseTimeMs, compileTimeMs, executionTimeMs, fallbackTimeMs);
    }

    public OclFileValidationResult replaceRuleResult(OclRuleValidationResult replacement) {
        List<OclRuleValidationResult> updated = new ArrayList<>();
        boolean replaced = false;
        for (OclRuleValidationResult current : ruleResults) {
            if (!replaced
                    && current.getOwnerKind() == replacement.getOwnerKind()
                    && current.getRuleKind() == replacement.getRuleKind()
                    && java.util.Objects.equals(current.getContextClassName(), replacement.getContextClassName())
                    && java.util.Objects.equals(current.getOperationName(), replacement.getOperationName())
                    && java.util.Objects.equals(current.getAttributeName(), replacement.getAttributeName())
                    && java.util.Objects.equals(current.getRuleName(), replacement.getRuleName())) {
                updated.add(replacement);
                replaced = true;
            } else {
                updated.add(current);
            }
        }
        if (!replaced) {
            updated.add(replacement);
        }
        return fromRuleResults(requestScope, updated, diagnostics, responseTimeMs, parseTimeMs, compileTimeMs,
                executionTimeMs, fallbackTimeMs);
    }

    public String toDisplayText() {
        StringBuilder sb = new StringBuilder(summary);
        if (requestScope != null && !requestScope.isBlank()) {
            sb.append("\nRequest Scope: ").append(requestScope);
        }
        if (responseTimeMs > 0 || parseTimeMs > 0 || compileTimeMs > 0 || executionTimeMs > 0 || fallbackTimeMs > 0) {
            sb.append("\nTiming: responseTimeMs=").append(responseTimeMs)
                    .append(", parseTimeMs=").append(parseTimeMs)
                    .append(", compileTimeMs=").append(compileTimeMs)
                    .append(", executionTimeMs=").append(executionTimeMs)
                    .append(", fallbackTimeMs=").append(fallbackTimeMs);
        }
        if (getDualCheckExecutedCount() > 0) {
            sb.append("\nDual Check: mismatch=").append(getDualCheckMismatchCount())
                    .append("/").append(getDualCheckExecutedCount());
        }
        if (!diagnostics.isEmpty()) {
            sb.append("\nDiagnostics:");
            for (OclDiagnostic diagnostic : diagnostics) {
                sb.append("\n  - ").append(diagnostic.toUserMessage());
            }
        }
        for (OclRuleValidationResult ruleResult : ruleResults) {
            sb.append("\n\n- [")
                    .append(statusLabel(ruleResult))
                    .append("] ")
                    .append(formatRuleTarget(ruleResult))
                    .append(ruleResult.isSkipped() ? " (skipped)" : "")
                    .append(ruleResult.isFallbackUsed() ? " (fallback)" : "")
                    .append(ruleResult.isCompilerSupported() ? "" : " (unsupported)")
                    .append('\n')
                    .append(ruleResult.getSummary());

            if (ruleResult.getResponseTimeMs() > 0
                    || ruleResult.getParseTimeMs() > 0
                    || ruleResult.getCompileTimeMs() > 0
                    || ruleResult.getExecutionTimeMs() > 0
                    || ruleResult.getFallbackTimeMs() > 0) {
                sb.append("\nTiming: responseTimeMs=").append(ruleResult.getResponseTimeMs())
                        .append(", parseTimeMs=").append(ruleResult.getParseTimeMs())
                        .append(", compileTimeMs=").append(ruleResult.getCompileTimeMs())
                        .append(", executionTimeMs=").append(ruleResult.getExecutionTimeMs())
                        .append(", fallbackTimeMs=").append(ruleResult.getFallbackTimeMs());
            }

            if (ruleResult.getGeneratedCypher() != null && !ruleResult.getGeneratedCypher().isBlank()) {
                sb.append("\nCypher:\n").append(ruleResult.getGeneratedCypher());
            }
            if (!ruleResult.getViolations().isEmpty()) {
                sb.append("\nViolations:");
                for (var entry : ruleResult.getViolations().entrySet()) {
                    sb.append("\n  - ").append(entry.getKey()).append(": ").append(entry.getValue());
                }
            }
            if (!ruleResult.getDiagnostics().isEmpty()) {
                sb.append("\nDiagnostics:");
                for (var diagnostic : ruleResult.getDiagnostics()) {
                    sb.append("\n  - ").append(diagnostic.toUserMessage());
                }
            }
            if (!ruleResult.getRequiredInputs().isEmpty()) {
                sb.append("\nRequired Inputs: ").append(ruleResult.getRequiredInputs());
            }
            if (ruleResult.getResultLocation() != null) {
                appendResultLocation(sb, ruleResult.getResultLocation());
            }
            if (ruleResult.isDualCheckExecuted()) {
                sb.append("\nDual Check: ").append(ruleResult.getDualCheckResult().summary());
                if (!ruleResult.getDualCheckResult().compiledOnlyObjectIds().isEmpty()) {
                    sb.append("\nCompiled Only Object IDs: ").append(ruleResult.getDualCheckResult().compiledOnlyObjectIds());
                }
                if (!ruleResult.getDualCheckResult().fallbackOnlyObjectIds().isEmpty()) {
                    sb.append("\nFallback Only Object IDs: ").append(ruleResult.getDualCheckResult().fallbackOnlyObjectIds());
                }
            }
        }
        return sb.toString();
    }

    private void appendResultLocation(StringBuilder sb, OclResultLocation location) {
        boolean hasSpan = location.line() != null || location.column() != null
                || location.endLine() != null || location.endColumn() != null;
        boolean hasObjectIds = !location.objectIds().isEmpty();
        boolean hasSnippet = location.sourceSnippet() != null && !location.sourceSnippet().isBlank();
        boolean hasToken = location.tokenText() != null && !location.tokenText().isBlank();
        if (!hasSpan && !hasObjectIds && !hasSnippet && !hasToken) {
            return;
        }
        sb.append("\nResult Location:");
        if (hasSpan) {
            sb.append(" line=").append(location.line())
                    .append(", column=").append(location.column());
            if (location.endLine() != null || location.endColumn() != null) {
                sb.append(", endLine=").append(location.endLine())
                        .append(", endColumn=").append(location.endColumn());
            }
        }
        if (hasObjectIds) {
            sb.append(hasSpan ? "," : "").append(" objectIds=").append(location.objectIds());
        }
        if (hasToken) {
            sb.append("\nToken: ").append(location.tokenText());
        }
        if (hasSnippet) {
            sb.append("\nSource: ").append(location.sourceSnippet());
        }
    }

    private String statusLabel(OclRuleValidationResult ruleResult) {
        if (ruleResult.isSkipped()) {
            return "SKIP";
        }
        return ruleResult.isSuccess() ? "PASS" : "FAIL";
    }

    private String formatRuleTarget(OclRuleValidationResult ruleResult) {
        StringBuilder builder = new StringBuilder();
        if (ruleResult.getContextClassName() != null && !ruleResult.getContextClassName().isBlank()) {
            builder.append(ruleResult.getContextClassName());
        } else {
            builder.append("<expression>");
        }
        if (ruleResult.getOperationName() != null && !ruleResult.getOperationName().isBlank()) {
            builder.append("::").append(ruleResult.getOperationName());
        }
        if (ruleResult.getAttributeName() != null && !ruleResult.getAttributeName().isBlank()) {
            builder.append("::").append(ruleResult.getAttributeName());
        }
        if (ruleResult.getRuleName() != null && !ruleResult.getRuleName().isBlank()) {
            builder.append("::").append(ruleResult.getRuleName());
        }
        return builder.toString();
    }
}
