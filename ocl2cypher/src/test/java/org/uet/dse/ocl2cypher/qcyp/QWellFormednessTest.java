package org.uet.dse.ocl2cypher.qcyp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class QWellFormednessTest {
    @Test
    void productionTranslationConstructsWellFormedQAndRejectsMalformedTrees() {
        QWellFormednessCorpus.Counts counts = QWellFormednessCorpus.run();
        assertEquals(58, counts.translatedTrees());
        assertEquals(5, counts.directValidTrees());
        assertEquals(15, counts.rejectedMalformedTrees());
    }
}
