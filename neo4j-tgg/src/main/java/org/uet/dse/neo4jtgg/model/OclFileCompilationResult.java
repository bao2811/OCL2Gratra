package org.uet.dse.neo4jtgg.model;

import org.uet.dse.neo4jtgg.ocl.diagnostic.OclDiagnostic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class OclFileCompilationResult {
    private final String requestScope;
    private final List<OclRuleCompilationResult> ruleResults;
    private final List<OclDiagnostic> documentDiagnostics;
    private final int freeExpressionCount;
    private final long responseTimeMs;
    private final long parseTimeMs;
    private final long compileTimeMs;

    public OclFileCompilationResult(List<OclRuleCompilationResult> ruleResults,
                                    List<OclDiagnostic> documentDiagnostics,
                                    int freeExpressionCount) {
        this("document", ruleResults, documentDiagnostics, freeExpressionCount, 0L, 0L, 0L);
    }

    public OclFileCompilationResult(String requestScope,
                                    List<OclRuleCompilationResult> ruleResults,
                                    List<OclDiagnostic> documentDiagnostics,
                                    int freeExpressionCount,
                                    long responseTimeMs,
                                    long parseTimeMs,
                                    long compileTimeMs) {
        this.requestScope = requestScope;
        this.ruleResults = List.copyOf(new ArrayList<>(ruleResults));
        this.documentDiagnostics = List.copyOf(new ArrayList<>(documentDiagnostics));
        this.freeExpressionCount = freeExpressionCount;
        this.responseTimeMs = responseTimeMs;
        this.parseTimeMs = parseTimeMs;
        this.compileTimeMs = compileTimeMs;
    }

    public String getRequestScope() {
        return requestScope;
    }

    public List<OclRuleCompilationResult> getRuleResults() {
        return Collections.unmodifiableList(ruleResults);
    }

    public List<OclDiagnostic> getDocumentDiagnostics() {
        return Collections.unmodifiableList(documentDiagnostics);
    }

    public int getFreeExpressionCount() {
        return freeExpressionCount;
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

    public int getRuleCount() {
        return ruleResults.size();
    }

    public long getSupportedCount() {
        return ruleResults.stream().filter(OclRuleCompilationResult::isSupported).count();
    }

    public long getUnsupportedCount() {
        return ruleResults.stream().filter(result -> !result.isSupported()).count();
    }

    public String getSummary() {
        return "Compiled " + getRuleCount()
                + " OCL rule(s): supported=" + getSupportedCount()
                + ", unsupported=" + getUnsupportedCount()
                + ", freeExpressions=" + freeExpressionCount
                + ", responseTimeMs=" + responseTimeMs
                + ", parseTimeMs=" + parseTimeMs
                + ", compileTimeMs=" + compileTimeMs + ".";
    }

    public String toDisplayText() {
        StringBuilder sb = new StringBuilder(getSummary());
        if (requestScope != null && !requestScope.isBlank()) {
            sb.append("\nRequest Scope: ").append(requestScope);
        }
        if (!documentDiagnostics.isEmpty()) {
            sb.append("\nDiagnostics:");
            for (OclDiagnostic diagnostic : documentDiagnostics) {
                sb.append("\n  - ").append(diagnostic.toUserMessage());
            }
        }
        for (OclRuleCompilationResult ruleResult : ruleResults) {
            sb.append("\n\n- [")
                    .append(ruleResult.isSupported() ? "SUPPORTED" : "UNSUPPORTED")
                    .append("] ")
                    .append(formatRuleTarget(ruleResult));
            if (ruleResult.getCompilationTimeMs() > 0) {
                sb.append("\nCompilation Time: ").append(ruleResult.getCompilationTimeMs()).append(" ms");
            }
            if (ruleResult.getCypher() != null && !ruleResult.getCypher().isBlank()) {
                sb.append("\nCypher:\n").append(ruleResult.getCypher());
            }
            if (!ruleResult.getParameters().isEmpty()) {
                sb.append("\nParameters: ").append(ruleResult.getParameters());
            }
            if (!ruleResult.getRequiredInputs().isEmpty()) {
                sb.append("\nRequired Inputs: ").append(ruleResult.getRequiredInputs());
            }
            if (!ruleResult.getDiagnostics().isEmpty()) {
                sb.append("\nDiagnostics:");
                for (OclDiagnostic diagnostic : ruleResult.getDiagnostics()) {
                    sb.append("\n  - ").append(diagnostic.toUserMessage());
                }
            }
            appendResultLocation(sb, ruleResult.getResultLocation());
        }
        return sb.toString();
    }

    private String formatRuleTarget(OclRuleCompilationResult ruleResult) {
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
        if (ruleResult.getInvariantName() != null && !ruleResult.getInvariantName().isBlank()) {
            builder.append("::").append(ruleResult.getInvariantName());
        }
        return builder.toString();
    }

    private void appendResultLocation(StringBuilder sb, OclResultLocation location) {
        if (location == null) {
            return;
        }
        boolean hasSpan = location.line() != null || location.column() != null
                || location.endLine() != null || location.endColumn() != null;
        boolean hasToken = location.tokenText() != null && !location.tokenText().isBlank();
        boolean hasSnippet = location.sourceSnippet() != null && !location.sourceSnippet().isBlank();
        if (!hasSpan && !hasToken && !hasSnippet) {
            return;
        }
        sb.append("\nResult Location:");
        if (hasSpan) {
            sb.append(" line=").append(location.line())
                    .append(", column=").append(location.column())
                    .append(", endLine=").append(location.endLine())
                    .append(", endColumn=").append(location.endColumn());
        }
        if (hasToken) {
            sb.append("\nToken: ").append(location.tokenText());
        }
        if (hasSnippet) {
            sb.append("\nSource: ").append(location.sourceSnippet());
        }
    }
}
