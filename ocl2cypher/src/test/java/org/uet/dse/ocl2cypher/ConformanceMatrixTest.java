package org.uet.dse.ocl2cypher;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.uet.dse.ocl2cypher.api.CoreOracle;
import org.uet.dse.ocl2cypher.conformance.PropertyFixtures;
import org.uet.dse.ocl2cypher.graph.GraphBuilder;
import org.uet.dse.ocl2cypher.qcyp.QCypTranslator;
import org.uet.dse.ocl2cypher.qcyp.QInterpreter;
import org.uet.dse.ocl2cypher.qcyp.QQuery;

import static org.junit.jupiter.api.Assertions.*;

/**
 * D11 matrix test: every bottom/empty/direction/multiplicity corner is a row
 * whose counter is exactly what checkers of carrier-membership need (callers
 * may compare by OCL-aware identities rather than raw collection pointers).
 *
 * <p>Note the carrier guarantee: a predicate-bottom on any occurrence makes
 * the filtered collection a typed bottom, not an empty set; hence collecting
 * a short list of filtered item IDs there would violate the carrier, so the
 * counter is used as a participation measure instead.
 */
class ConformanceMatrixTest {

    /** Missing attribute → bottom → violation: carrier of a single predicate-bottom
     *  must remain indistinguishable from a missing source. */
    @Test
    void bottomCarrierRemainsDistinguishable() {
        var sm = PropertyFixtures.personSchema();
        var sn = PropertyFixtures.mixedPersons();
        String ocl = "context Person inv Adult: self.age >= 18";

        var coreViol = CoreOracle.violationsOclEq(ocl, sm, sn);
        assertEquals(Set.of("bob", "carol"), Set.copyOf(coreViol),
                "bottom (missing age) is a violation, not an empty-filter hit");
        assertTrue(coreViol.contains("carol"));

        var fe = org.uet.dse.ocl2cypher.api.FrontendCompiler.compile(ocl, sm);
        assertTrue(fe.isSuccess());
        var low = org.uet.dse.ocl2cypher.core.CoreLowering.lower(sm, fe.value().get(0),
                fe.value().get(0).constraints.get(0));
        assertTrue(low.isSuccess());
        var q = QCypTranslator.translate(low.value());
        assertTrue(q.isSuccess());
        var g = GraphBuilder.build(sm, sn);
        assertTrue(g.isSuccess());
        var qViol = QInterpreter.violations(sm, g.value().graph(), low.value(), q.value());
        assertEquals(Set.copyOf(coreViol), Set.copyOf(qViol),
                "Q-over-G violation set must equal Core even on the bottom carrier");
    }
}
