package org.uet.dse.neo4jtgg.model;

import org.junit.jupiter.api.Test;
import org.uet.dse.neo4jtgg.ocl.diagnostic.OclDiagnostic;
import org.uet.dse.neo4jtgg.ocl.diagnostic.OclDiagnosticCode;
import org.uet.dse.neo4jtgg.ocl.diagnostic.OclDiagnosticPhase;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OclFileCompilationResultTest {
    @Test
    void summarizesCompileResponseWithTimingAndCounts() {
        OclFileCompilationResult result = new OclFileCompilationResult(
                "SOURCE:compile",
                List.of(
                        new OclRuleCompilationResult(
                                OclRuleOwnerKind.CLASS,
                                OclRuleKind.INV,
                                "Person",
                                null,
                                null,
                                "Adult",
                                new CypherCompilationResult(true, "MATCH ...", Map.of("p0", 18L), "", true),
                                7L,
                                new OclResultLocation("Person", "Adult", null, null, null, null, null, null, List.of()),
                                List.of()),
                        new OclRuleCompilationResult(
                                OclRuleOwnerKind.OPERATION,
                                OclRuleKind.PRE,
                                "BankAccount",
                                "withdraw",
                                null,
                                "PositiveAmount",
                                new CypherCompilationResult(false, "", Map.of(), "unsupported", false,
                                        List.of(new OclDiagnostic(OclDiagnosticPhase.SEMANTIC, OclDiagnosticCode.UNSUPPORTED_RULE_KIND,
                                                "Unsupported rule.", 2, 1, 2, 3))),
                                3L,
                                new OclResultLocation("BankAccount", "PositiveAmount", 2, 1, 2, 3, "pre", "pre:", List.of()),
                                List.of("parameter:amount"))
                ),
                List.of(new OclDiagnostic(OclDiagnosticPhase.PARSE, OclDiagnosticCode.PARSE_ERROR,
                        "Failed to parse OCL input.", 1, 1, 1, 5)),
                1,
                25L,
                5L,
                17L);

        assertEquals(2, result.getRuleCount());
        assertEquals(1, result.getSupportedCount());
        assertEquals(1, result.getUnsupportedCount());
        assertTrue(result.getSummary().contains("supported=1"));
        assertTrue(result.getSummary().contains("freeExpressions=1"));
        assertTrue(result.getSummary().contains("responseTimeMs=25"));
    }

    @Test
    void rendersCompilationDisplayTextWithDiagnosticsAndLocation() {
        OclFileCompilationResult result = new OclFileCompilationResult(
                "SOURCE:compile",
                List.of(
                        new OclRuleCompilationResult(
                                OclRuleOwnerKind.OPERATION,
                                OclRuleKind.PRE,
                                "BankAccount",
                                "withdraw",
                                null,
                                "PositiveAmount",
                                new CypherCompilationResult(false, "", Map.of(), "unsupported", false,
                                        List.of(new OclDiagnostic(OclDiagnosticPhase.SEMANTIC, OclDiagnosticCode.UNSUPPORTED_RULE_KIND,
                                                "Unsupported rule.", 2, 1, 2, 3))),
                                3L,
                                new OclResultLocation("BankAccount", "PositiveAmount", 2, 1, 2, 3, "pre", "pre:", List.of()),
                                List.of("parameter:amount"))
                ),
                List.of(new OclDiagnostic(OclDiagnosticPhase.PARSE, OclDiagnosticCode.PARSE_ERROR,
                        "Failed to parse OCL input.", 1, 1, 1, 5)),
                0,
                11L,
                4L,
                6L);

        String display = result.toDisplayText();

        assertTrue(display.contains("Request Scope: SOURCE:compile"));
        assertTrue(display.contains("[UNSUPPORTED] BankAccount::withdraw::PositiveAmount"));
        assertTrue(display.contains("Required Inputs: [parameter:amount]"));
        assertTrue(display.contains("Result Location: line=2, column=1, endLine=2, endColumn=3"));
        assertTrue(display.contains("Token: pre"));
        assertTrue(display.contains("Source: pre:"));
        assertTrue(display.contains("Failed to parse OCL input."));
    }
}
