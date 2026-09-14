package org.uet.dse.ocl2cypher.cypher;

import org.junit.jupiter.api.Test;

class NumericCapabilityGateTest {
    @Test
    void acceptsOnlyCertifiedIntegerTrees() {
        NumericCapabilityGateCheck.main(new String[0]);
    }
}
