package org.uet.dse.neo4jtgg.service.impl;

import org.junit.jupiter.api.Test;
import org.uet.dse.neo4jtgg.model.CypherCompilationResult;
import org.uet.dse.neo4jtgg.model.OclResultLocation;
import org.uet.dse.neo4jtgg.model.OclRuleCompilationResult;
import org.uet.dse.neo4jtgg.model.OclRuleDescriptor;
import org.uet.dse.neo4jtgg.model.OclRuleKind;
import org.uet.dse.neo4jtgg.model.OclRuleOwnerKind;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OclContextBatchQueryExecutorTest {

    @Test
    void rewritesParametersAndBuildsUnionPlanPerRule() {
        OclContextBatchQueryExecutor.BatchRule first = new OclContextBatchQueryExecutor.BatchRule(
                0,
                new OclRuleDescriptor(OclRuleOwnerKind.CLASS, OclRuleKind.INV, "Person", null, null,
                        List.of(), "Adult", null, null),
                new OclRuleCompilationResult(OclRuleOwnerKind.CLASS, OclRuleKind.INV,
                        "Person", null, null, "Adult",
                        new CypherCompilationResult(true,
                                "MATCH (n:Person) WHERE n.age < $p0 RETURN n.use_id AS useId",
                                Map.of("p0", 18L), "", true),
                        3L,
                        new OclResultLocation("Person", "Adult", null, null, null, null, null, null, List.of()),
                        List.of()),
                "Adult violated");
        OclContextBatchQueryExecutor.BatchRule second = new OclContextBatchQueryExecutor.BatchRule(
                1,
                new OclRuleDescriptor(OclRuleOwnerKind.CLASS, OclRuleKind.INV, "Person", null, null,
                        List.of(), "Named", null, null),
                new OclRuleCompilationResult(OclRuleOwnerKind.CLASS, OclRuleKind.INV,
                        "Person", null, null, "Named",
                        new CypherCompilationResult(true,
                                "MATCH (n:Person) WHERE n.name = $p0 RETURN n.use_id AS useId",
                                Map.of("p0", "Bob"), "", true),
                        2L,
                        new OclResultLocation("Person", "Named", null, null, null, null, null, null, List.of()),
                        List.of()),
                "Named violated");

        OclContextBatchQueryExecutor.BatchQueryPlan plan = OclContextBatchQueryExecutor.prepare(List.of(first, second));

        assertTrue(plan.cypher().contains("UNION ALL"));
        assertTrue(plan.cypher().contains("$r0_p0"));
        assertTrue(plan.cypher().contains("$r1_p0"));
        assertEquals(18L, plan.parameters().get("r0_p0"));
        assertEquals("Bob", plan.parameters().get("r1_p0"));
        assertEquals(2, plan.aliasToRule().size());
    }
}
