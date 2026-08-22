package org.uet.dse.neo4jtgg.experiment;

import org.junit.jupiter.api.Test;
import org.tzi.use.uml.mm.MModel;
import org.uet.dse.neo4jtgg.ocl.ir.RawCypherParser;
import org.uet.dse.neo4jtgg.ocl.ir.RawCypherRenderer;
import org.uet.dse.neo4jtgg.service.impl.DefaultOclToCypherCompiler;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Seeded property generation over the certified parser-to-typed-AST pipeline. */
class OclGeneratedPropertyTest {
    @Test
    void generatedCertifiedPropertiesCompileDeterministicallyThroughTypedRawAst() {
        long seed = Long.getLong("ocl.property.seed", OclPropertyCaseGenerator.DEFAULT_SEED);
        int count = Integer.getInteger("ocl.property.static.cases",
                OclPropertyCaseGenerator.DEFAULT_STATIC_CASES);
        MModel model = OclVal47NonVacuityFixture.compileModel("GeneratedProperties");
        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        RawCypherParser parser = new RawCypherParser();
        RawCypherRenderer renderer = new RawCypherRenderer();
        Set<String> contexts = new LinkedHashSet<>();

        for (OclPropertyCaseGenerator.PropertyCase property
                : OclPropertyCaseGenerator.generate(seed, count)) {
            var first = compiler.compileInvariantInstrumented(property.invariant());
            var second = compiler.compileInvariantInstrumented(property.invariant());
            assertNotNull(first.queryPlan(), property::replay);
            assertEquals(first.cypher(), second.cypher(), property::replay);
            assertEquals(first.parameters(), second.parameters(), property::replay);
            assertEquals(first.cypher(), renderer.render(parser.parse(first.cypher())), property::replay);
            contexts.add(property.context());
        }
        assertEquals(Set.of("Person", "Company"), contexts);
        System.out.println("OCL_GENERATED_PROPERTY_STATIC=PASS seed=" + seed + " cases=" + count);
    }
}
