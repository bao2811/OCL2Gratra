package org.uet.dse.neo4jtgg.model;

import org.junit.jupiter.api.Test;
import org.uet.dse.neo4jtgg.ocl.diagnostic.OclDiagnostic;
import org.uet.dse.neo4jtgg.ocl.diagnostic.OclDiagnosticCode;
import org.uet.dse.neo4jtgg.ocl.diagnostic.OclDiagnosticPhase;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TggWorkspaceContextTest {
    @Test
    void keepsStructuredValidationResultAlongsideDisplayText() {
        TggWorkspaceContext context = new TggWorkspaceContext(null, null);
        OclFileValidationResult result = new OclFileValidationResult(true, "Validated 1 OCL element(s): pass=1, fail=0, fallback=0, unsupported=0.",
                List.of(new OclRuleValidationResult("Person", "Adult", true, true, false,
                        "SUCCESS: invariant `Adult` passed on Neo4j.", "MATCH ...", Map.of(), List.of())));

        context.setLastValidationResult(WorkspaceSide.SOURCE, result);

        assertNotNull(context.getLastValidationResult(WorkspaceSide.SOURCE));
        assertEquals(result.toDisplayText(), context.getLastValidation(WorkspaceSide.SOURCE));
    }

    @Test
    void clearsStructuredValidationResultWhenLegacyTextSetterIsUsed() {
        TggWorkspaceContext context = new TggWorkspaceContext(null, null);
        context.setLastValidationResult(WorkspaceSide.SOURCE,
                new OclFileValidationResult(true, "ok", List.of()));

        context.setLastValidation(WorkspaceSide.SOURCE, "plain text");

        assertEquals("plain text", context.getLastValidation(WorkspaceSide.SOURCE));
        assertNull(context.getLastValidationResult(WorkspaceSide.SOURCE));
    }

    @Test
    void includesDiagnosticsInDisplayText() {
        OclFileValidationResult result = new OclFileValidationResult(false, "Validated 1 OCL element(s): pass=0, fail=1, fallback=1, unsupported=1.",
                List.of(new OclRuleValidationResult("Person", "Adult", false, false, true,
                        "FAILURE: fallback evaluator found 1 violation(s).", "", Map.of("p1", "Fallback evaluation reported false"),
                        List.of(new OclDiagnostic(OclDiagnosticPhase.SEMANTIC, OclDiagnosticCode.UNKNOWN_PROPERTY,
                                "Unknown property or navigation `missing`.", 1, 10, 1, 16)))));

        String display = result.toDisplayText();

        assertTrue(display.contains("Diagnostics:"));
        assertTrue(display.contains("SEMANTIC: line 1, column 10"));
        assertTrue(display.contains("Unknown property or navigation"));
    }

    @Test
    void exposesStructuredValidationCounts() {
        OclFileValidationResult result = new OclFileValidationResult(false, "summary",
                List.of(
                        new OclRuleValidationResult("Person", "Adult", true, true, false,
                                "ok", "MATCH ...", Map.of(), List.of()),
                        new OclRuleValidationResult("Person", "Named", false, false, true,
                                "fail", "", Map.of("p1", "Fallback evaluation reported false"), List.of())
                ));

        assertEquals(2, result.getRuleCount());
        assertEquals(1, result.getPassCount());
        assertEquals(1, result.getFailCount());
        assertEquals(1, result.getFallbackCount());
        assertEquals(1, result.getUnsupportedCount());
    }

    @Test
    void separatesSkippedRuleResultsFromFailures() {
        OclFileValidationResult result = OclFileValidationResult.fromRuleResults(List.of(
                new OclRuleValidationResult(OclRuleOwnerKind.OPERATION, OclRuleKind.PRE,
                        "Family", "addDaughter", null, "UnnamedPre",
                        false, true, false, OclExecutionMode.SKIPPED,
                        "Compiled operation precondition is ready, but document-level validation skipped it because invocation parameter values were not provided.",
                        "MATCH ...", Map.of(), List.of(), 0L, 0L,
                        new OclResultLocation("Family", "UnnamedPre", null, null, null, null, null, null, List.of()),
                        List.of("parameter:name"))
        ));

        assertEquals(0, result.getPassCount());
        assertEquals(0, result.getFailCount());
        assertEquals(1, result.getSkippedCount());
        assertEquals(0, result.getUnsupportedCount());
        assertTrue(result.isSuccess());
        assertTrue(result.getSummary().contains("skipped=1"));
        assertTrue(result.toDisplayText().contains("[SKIP] Family::addDaughter::UnnamedPre"));
        assertTrue(result.toDisplayText().contains("Required Inputs: [parameter:name]"));
    }

    @Test
    void keepsRequiredInputsForOperationPostconditions() {
        OclFileValidationResult result = OclFileValidationResult.fromRuleResults(List.of(
                new OclRuleValidationResult(OclRuleOwnerKind.OPERATION, OclRuleKind.POST,
                        "BankAccount", "withdraw", null, "UnnamedPost",
                        true, true, false, OclExecutionMode.COMPILED,
                        "SUCCESS: postcondition `BankAccount::withdraw::UnnamedPost` passed on Neo4j.",
                        "MATCH ...", Map.of(), List.of(), 12L, 0L,
                        new OclResultLocation("BankAccount", "UnnamedPost", null, null, null, null, null, null, List.of()),
                        List.of("parameter:amount", "resultValue"))
        ));

        assertEquals(0, result.getSkippedCount());
        assertTrue(result.toDisplayText().contains("[PASS] BankAccount::withdraw::UnnamedPost"));
        assertTrue(result.toDisplayText().contains("Required Inputs: [parameter:amount, resultValue]"));
    }

    @Test
    void replacesRuleResultAndRecomputesSummary() {
        OclFileValidationResult result = OclFileValidationResult.fromRuleResults(List.of(
                new OclRuleValidationResult("Person", "Adult", true, true, false,
                        "ok", "MATCH ...", Map.of(), List.of()),
                new OclRuleValidationResult("Person", "Named", true, true, false,
                        "ok", "MATCH ...", Map.of(), List.of())
        ));

        OclFileValidationResult updated = result.replaceRuleResult(
                new OclRuleValidationResult("Person", "Named", false, false, true,
                        "fail", "", Map.of("p1", "Fallback evaluation reported false"), List.of()));

        assertEquals(2, updated.getRuleCount());
        assertEquals(1, updated.getPassCount());
        assertEquals(1, updated.getFailCount());
        assertEquals(1, updated.getFallbackCount());
        assertEquals(1, updated.getUnsupportedCount());
        assertTrue(updated.getSummary().contains("pass=1"));
        assertTrue(updated.getSummary().contains("fail=1"));
    }

    @Test
    void replacesOnlyMatchingOperationScopedRuleResult() {
        OclFileValidationResult result = OclFileValidationResult.fromRuleResults(List.of(
                new OclRuleValidationResult(OclRuleOwnerKind.OPERATION, OclRuleKind.PRE,
                        "BankAccount", "withdraw", null, "PositiveAmount",
                        true, true, false, OclExecutionMode.COMPILED,
                        "ok", "MATCH withdraw", Map.of(), List.of(), 1L, 0L,
                        new OclResultLocation("BankAccount", "PositiveAmount", null, null, null, null, null, null, List.of()),
                        List.of("parameter:amount")),
                new OclRuleValidationResult(OclRuleOwnerKind.OPERATION, OclRuleKind.PRE,
                        "BankAccount", "deposit", null, "PositiveAmount",
                        true, true, false, OclExecutionMode.COMPILED,
                        "ok", "MATCH deposit", Map.of(), List.of(), 1L, 0L,
                        new OclResultLocation("BankAccount", "PositiveAmount", null, null, null, null, null, null, List.of()),
                        List.of("parameter:amount"))
        ));

        OclFileValidationResult updated = result.replaceRuleResult(
                new OclRuleValidationResult(OclRuleOwnerKind.OPERATION, OclRuleKind.PRE,
                        "BankAccount", "withdraw", null, "PositiveAmount",
                        false, true, false, OclExecutionMode.ERROR,
                        "missing amount", "MATCH withdraw", Map.of(), List.of(), 0L, 0L,
                        new OclResultLocation("BankAccount", "PositiveAmount", null, null, null, null, null, null, List.of()),
                        List.of("parameter:amount")));

        assertEquals("missing amount", updated.getRuleResults().get(0).getSummary());
        assertEquals("ok", updated.getRuleResults().get(1).getSummary());
        assertEquals("deposit", updated.getRuleResults().get(1).getOperationName());
    }

    @Test
    void capturesDiagnosticsAndViolationLocationsInStructuredResponse() {
        OclDiagnostic diagnostic = new OclDiagnostic(OclDiagnosticPhase.SEMANTIC, OclDiagnosticCode.UNKNOWN_PROPERTY,
                "Unknown property or navigation `missing`.", 2, 4, 2, 10);
        OclRuleValidationResult result = new OclRuleValidationResult(OclRuleOwnerKind.CLASS, OclRuleKind.INV,
                "Person", null, null, "Adult", false, false, true,
                OclExecutionMode.UNSUPPORTED, "fail", "", Map.of("p1", "Fallback evaluation reported false"),
                List.of(diagnostic), 12L, 12L, OclResultLocation.fromDiagnostic("Person", "Adult", diagnostic),
                List.of());

        assertEquals(OclRuleOwnerKind.CLASS, result.getOwnerKind());
        assertEquals(OclRuleKind.INV, result.getRuleKind());
        assertEquals(OclExecutionMode.UNSUPPORTED, result.getExecutionMode());
        assertEquals(12L, result.getExecutionTimeMs());
        assertEquals(12L, result.getFallbackTimeMs());
        assertEquals(2, result.getResultLocation().line());
        assertEquals(4, result.getResultLocation().column());
        assertTrue(result.getResultLocation().objectIds().isEmpty());
    }

    @Test
    void keepsDocumentTimingAndDiagnostics() {
        OclDiagnostic diagnostic = new OclDiagnostic(OclDiagnosticPhase.PARSE, OclDiagnosticCode.PARSE_ERROR,
                "Failed to parse OCL input.", 1, 1, 1, 5);
        OclFileValidationResult result = OclFileValidationResult.fromRuleResults("SOURCE:document", List.of(),
                List.of(diagnostic), 30L, 5L, 7L, 9L, 11L);

        assertEquals("SOURCE:document", result.getRequestScope());
        assertEquals(30L, result.getResponseTimeMs());
        assertEquals(5L, result.getParseTimeMs());
        assertEquals(7L, result.getCompileTimeMs());
        assertEquals(9L, result.getExecutionTimeMs());
        assertEquals(11L, result.getFallbackTimeMs());
        assertEquals(1, result.getDiagnostics().size());
        assertTrue(result.getSummary().contains("parseTimeMs=5"));
        assertTrue(result.toDisplayText().contains("Request Scope: SOURCE:document"));
        assertTrue(result.toDisplayText().contains("Timing: responseTimeMs=30, parseTimeMs=5, compileTimeMs=7, executionTimeMs=9, fallbackTimeMs=11"));
        assertTrue(result.toDisplayText().contains("Failed to parse OCL input."));
    }

    @Test
    void summarizesDualCheckMismatchInStructuredResponse() {
        OclRuleValidationResult ruleResult = new OclRuleValidationResult(
                OclRuleOwnerKind.CLASS, OclRuleKind.INV,
                "Person", null, null, "Adult",
                false, true, false, OclExecutionMode.COMPILED,
                "FAILURE: invariant `Adult` violated for 1 object(s).",
                "MATCH ...", Map.of("p1", "compiled"), List.of(),
                18L, 7L,
                OclResultLocation.forViolations("Person", "Adult", List.of("p1")),
                List.of(),
                OclDualCheckResult.compare(
                        Map.of("p1", "compiled"),
                        Map.of("p2", "fallback")));
        OclFileValidationResult result = OclFileValidationResult.fromRuleResults("SOURCE:document",
                List.of(ruleResult), List.of(), 40L, 5L, 6L, 18L, 7L);

        assertEquals(1, result.getDualCheckExecutedCount());
        assertEquals(1, result.getDualCheckMismatchCount());
        assertTrue(result.getSummary().contains("dualCheckMismatch=1/1"));
        assertTrue(result.toDisplayText().contains("Dual Check: mismatch=1/1"));
        assertTrue(result.toDisplayText().contains("Dual-check mismatch: compiledOnly=[p1], fallbackOnly=[p2]."));
        assertTrue(result.toDisplayText().contains("Compiled Only Object IDs: [p1]"));
        assertTrue(result.toDisplayText().contains("Fallback Only Object IDs: [p2]"));
    }

    @Test
    void keepsStructuredOperationInvocationInputs() {
        OclOperationRuleInputs inputs = new OclOperationRuleInputs(
                "post-1",
                "pre-1",
                "ok",
                Map.of("amount", 100L));

        assertEquals("post-1", inputs.getCurrentSelfObjectId());
        assertEquals("pre-1", inputs.getPreSelfObjectId());
        assertEquals("ok", inputs.getResultValue());
        assertEquals(100L, inputs.getParameterValues().get("amount"));
    }
}
