package org.uet.dse.ocl2cypher.caseStudy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CaseStudyInvariantReaderTest {
    @Test void corpusHeadersMatchEveryExpectedInvariantKey() throws Exception {
        Path base = Path.of("..", "examples", "carrental");
        var specs = CaseStudyReplayer.loadInvariants(base.resolve("invariants-extended.ocl"));
        var expected = Files.readAllLines(base.resolve("expected-violations-extended.csv"))
                .stream().skip(1).filter(s -> !s.isBlank()).map(s -> s.split(",", -1)[0]).toList();
        var keys = specs.stream().map(s -> s.context() + "::" + s.name()).toList();
        assertFalse(expected.isEmpty());
        assertEquals(expected, keys);
        for (var spec : specs) {
            assertTrue(spec.source().startsWith("context " + spec.context() + " inv " + spec.name() + ": "));
            assertFalse(spec.body().isBlank());
        }
        // Header matching is not expected violation-ID agreement or admission proof.
    }
}
