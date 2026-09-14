package org.uet.dse.ocl2cypher.caseStudy;

import java.io.IOException;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Corpus replay harness for the current profile. Every invariant collected
 * there already covers the type-kind, operation-kind and value-kind dimension
 * before admission into a witness.
 *
 * <p>The corpus there is more than a tag layout: a single invariant whose body
 * is a predicate-bottom must remain indistinguishable from a missing source,
 * and the short-circuit for a Boolean connective has to know where on the row
 * its alias became whole bottom.  A violation set there is therefore always
 * compared as a stable-id set via OCL equality, not as a raw row count; a
 * missing attribute or a bottom-qualified navigation has to show up as a holder
 * whose body value is ⊥, not as a dropped row that the oracle would mistake
 * for "empty".
 */
class CaseStudyReplayTest {

    private static final java.nio.file.Path CORPUS = java.nio.file.Path.of("..", "examples",
            "carrental").toAbsolutePath().normalize();

    @Test
    void requiredCorpusIsResolvedFromTheModuleDirectory() {
        assertTrue(java.nio.file.Files.isRegularFile(
                CORPUS.resolve("carrental-experiment.soil")), CORPUS.toString());
        assertTrue(java.nio.file.Files.isRegularFile(
                CORPUS.resolve("invariants-extended.ocl")), CORPUS.toString());
        assertTrue(java.nio.file.Files.isRegularFile(
                CORPUS.resolve("expected-violations-extended.csv")), CORPUS.toString());
    }

    @Test
    void replayProducesExpectedViolatedObjectIdsForBoundaryCorpus() throws IOException {
        java.nio.file.Path base = CORPUS.resolve("umlmm");
        // When this suite runs inside Maven the working directory is the
        // ocl2cypher module — so the path above is exactly
        //   <repo>/examples/carrental
        // and the replayer there relies solely on one closed catalogue; the
        // construction there relies solely on that designated witness binding.
        java.nio.file.Path soil = base.resolve("carrental-experiment.soil");
        java.nio.file.Path ocl = base.resolve("invariants-extended.ocl");
        if (!java.nio.file.Files.exists(soil) || !java.nio.file.Files.exists(ocl)) {
            // Corpus is optional on a developer machine; skip, don't fail the build.
            System.err.println("CaseStudy: corpus not found at " + base + " — skipping.");
            fail("required Car Rental corpus is missing from " + base);
        }
        var study = CaseStudyReplayer.loadCarRental(soil, ocl);
        // The counter is exactly what checkers of carrier-membership need
        // (callers may compare by OCL-aware identities rather than raw row counts).
        assertEquals(25, study.entries().size());
        assertEquals(19, study.entries().stream().filter(CaseStudyReplayer.Entry::admitted).count());
        assertEquals(6, study.entries().stream().filter(e -> !e.admitted()).count());
        for (var e : study.entries()) {
            Set<String> actual = e.actual();
            // Each study entry carries its own expected stable-id set via the
            // differential; the harness here therefore only asserts the structural
            // property every row shares: every reported violation actually occurs
            // in the snapshot projection (NoGhost).
            Set<String> ids = study.snapshot().objects().stream()
                    .map(o -> o.stableId).collect(java.util.stream.Collectors.toSet());
            assertTrue(ids.containsAll(actual),
                    "every violated id occurs in the snapshot: " + e.name() + " -> " + actual);
            if (e.admitted()) {
                assertEquals(e.expected(), actual,
                        "replayed Core violations must equal the checked-in oracle: " + e.name());
            } else {
                assertNotNull(e.rejectionCode());
                assertFalse(e.rejectionCode().isBlank());
            }
        }
    }
}
