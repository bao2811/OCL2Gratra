package org.uet.dse.ocl2cypher.frontend;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * P3 acceptance: {@code OCL.g4 ~ research/OCLscope/OCL-Concrete-Grammar.ebnf}.
 *
 * <p>The EBNF there decides precedence/associativity; the ANTLR {@code OCL.g4}
 * is generated into the same concrete profile. The 14-level precedence and all
 * infix-left except right-associative {@code implies} are the negative
 * completeness criterion there — a corpus that only checks open admission but
 * whose recognition boundary is wrong would break {@code E_SM} before a single
 * Core expression is lowered.
 */
class GrammarConformanceTest {

    @Test
    void grammarAcceptsInProfileAndRejectsBeyond() throws Exception {
        Path tsv = Path.of("src", "test", "resources", "grammar-conformance.tsv");
        List<String> rows = java.nio.file.Files.readAllLines(tsv, StandardCharsets.UTF_8);
        List<String> failures = new ArrayList<>();
        for (String raw : rows) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("id") && line.contains("accept")) {
                continue;
            }
            String[] cols = raw.split("\t", -1);
            if (cols.length < 2) {
                failures.add("parse error: not enough TABs: " + raw);
                continue;
            }
            String id = cols[0].trim();
            String acceptCol = cols[1].trim();
            boolean inProfile = acceptCol.equalsIgnoreCase("YES");
            String snippet = cols.length >= 3 ? cols[2] : "";
            if (snippet.isEmpty()) {
                snippet = cols[cols.length - 1];
            }
            boolean accepted = GrammarConformance.acceptsDocument(snippet);
            if (accepted != inProfile) {
                failures.add(id + " [" + (inProfile ? "expect YES" : "expect NO") + "] -> "
                        + (accepted ? "YES" : "NO") + " for `" + snippet + "`");
            }
        }
        assertTrue(failures.isEmpty(),
                () -> "grammar-conformance.tsv failures:\n" + String.join("\n", failures));
    }
}
