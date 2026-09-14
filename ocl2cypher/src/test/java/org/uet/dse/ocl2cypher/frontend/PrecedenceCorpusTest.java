package org.uet.dse.ocl2cypher.frontend;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.uet.dse.ocl2cypher.frontend.antlr.OCLParser;
import org.uet.dse.ocl2cypher.frontend.antlr.OCLLexer;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Precedence corpus — the 14-level chain and associativity are the negative
 * completeness criterion of §2. The expected shape there is a fully
 * parenthesized rendering of the expression parse tree, collapsing chain rules,
 * so the only observable is the binary tree. There a corpus with an open
 * admission boundary but a wrong recognition boundary would not have produced
 * an ambiguous AST that enforces a future lower-to-core.
 */
class PrecedenceCorpusTest {

    record Case(String expr, String shape) {}

    private static List<Case> load() throws Exception {
        java.nio.file.Path p = java.nio.file.Path.of("src", "test", "resources",
                "expected-precedence-trees.json");
        String raw = java.nio.file.Files.readString(p, StandardCharsets.UTF_8);
        // Minimal reader for this small fixture.
        List<Case> out = new ArrayList<>();
        String json = raw.replace("﻿", "");
        for (String obj : json.split("\\},")) {
            int ei = obj.indexOf("\"expr\"");
            int si = obj.indexOf("\"shape\"");
            if (ei == -1 || si == -1) {
                continue;
            }
            String expr = jsonString(obj, ei);
            String shape = jsonString(obj, si);
            out.add(new Case(expr, shape));
        }
        return out;
    }

    private static String jsonString(String obj, int keyIdx) {
        int colon = obj.indexOf(':', keyIdx);
        int q = obj.indexOf('"', colon);
        int end = obj.indexOf('"', q + 1);
        while (end != -1 && obj.charAt(end - 1) == '\\') {
            end = obj.indexOf('"', end + 1);
        }
        return obj.substring(q + 1, end).replace("\\\"", "\"").replace("\\\\", "\\");
    }

    @Test
    void precedenceCorpusShapesMatchG4() throws Exception {
        List<Case> cases = load();
        assertFalse(cases.isEmpty(), "expected-precedence-trees.json is empty");
        List<String> failures = new ArrayList<>();
        for (Case c : cases) {
            String actual = GrammarConformance.precedenceShape(c.expr);
            if (!c.shape.equals(actual)) {
                failures.add(c.expr + "\n  expect: " + c.shape + "\n  actual: " + actual);
            }
        }
        assertTrue(failures.isEmpty(),
                () -> "precedence-corpus failures:\n" + String.join("\n", failures));
    }
}
