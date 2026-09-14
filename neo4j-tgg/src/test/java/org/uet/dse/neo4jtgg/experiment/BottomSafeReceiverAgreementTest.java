package org.uet.dse.neo4jtgg.experiment;

import org.junit.jupiter.api.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.uet.dse.neo4jtgg.service.impl.DefaultOclToCypherCompiler;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Generated-query contract for PO-12 guarded entity receivers and live aliases. */
class BottomSafeReceiverAgreementTest {
    @Test
    void everyBottomCapableEntityConsumerGuardsBeforeItsPattern() {
        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model());
        List<String> invariants = List.of(
                "context Person inv BottomAttribute: (if self.age > 0 then self else null endif).age >= 0",
                "context Person inv BottomKind: (if self.age > 0 then self else null endif).oclIsKindOf(Person)",
                "context Person inv BottomCast: (if self.age > 0 then self else null endif).oclAsType(Person).age >= 0",
                "context Person inv NestedAliases: Person.allInstances()->forAll(p | "
                        + "(if p.age > self.age then p else null endif).oclIsKindOf(Person) or p = self)");

        for (String invariant : invariants) {
            InstrumentedCompilationResult result = compiler.compileInvariantInstrumented(invariant);
            String cypher = result.cypher();
            int receiverBinding = firstReceiverBinding(cypher);
            int guard = cypher.indexOf("Key IS NOT NULL", receiverBinding);
            int entityPattern = firstEntityPatternAfter(cypher, guard);
            assertTrue(receiverBinding >= 0 && guard > receiverBinding && entityPattern > guard, cypher);
            assertTrue(cypher.contains(".objectKey AS "), cypher);
            assertFalse(cypher.contains("MATCH ((CASE WHEN"), cypher);
        }
    }

    @Test
    void nestedGuardRetainsBothIteratorAndOuterSelfAliases() {
        InstrumentedCompilationResult result = new DefaultOclToCypherCompiler(model())
                .compileInvariantInstrumented("context Person inv NestedAliases: "
                        + "Person.allInstances()->forAll(p | "
                        + "(if p.age > self.age then p else null endif).oclIsKindOf(Person) or p = self)");
        String guardedPrefix = result.cypher().substring(0, result.cypher().indexOf(" MATCH (typeRecv"));
        assertTrue(guardedPrefix.contains("CASE WHEN"), result.cypher());
        assertTrue(guardedPrefix.contains("p"), result.cypher());
        assertTrue(guardedPrefix.contains("self"), result.cypher());
    }

    @Test
    void bottomSafeReceiverBranchesRoundTripThroughBothParsers() {
        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model());
        List<String> invariants = List.of(
                "context Person inv BottomAttribute: (if self.age > 0 then self else null endif).age >= 0",
                "context Person inv BottomKind: (if self.age > 0 then self else null endif).oclIsKindOf(Person)",
                "context Person inv BottomCast: (if self.age > 0 then self else null endif).oclAsType(Person).age >= 0",
                "context Person inv NestedAliases: Person.allInstances()->forAll(p | "
                        + "(if p.age > self.age then p else null endif).oclIsKindOf(Person) or p = self)");

        for (String invariant : invariants) {
            String cypher = compiler.compileInvariantInstrumented(invariant).cypher();
            String normalized = GeneratedCypherSyntaxTree.render(GeneratedCypherSyntaxTree.parse(cypher));
            assertEquals(normalized,
                    GeneratedCypherSyntaxTree.render(GeneratedCypherSyntaxTree.parse(normalized)), invariant);
            assertEquals(GeneratedCypherCanonicalTree.parse(cypher).text(),
                    GeneratedCypherCanonicalTree.parse(normalized).text(), invariant);
            assertEquals(Neo4jCypherAstBridge.parse(cypher).canonicalTree(),
                    Neo4jCypherAstBridge.parse(normalized).canonicalTree(), invariant);
            assertTrue(normalized.contains("Key IS NOT NULL"), normalized);
            assertTrue(normalized.contains("CASE WHEN"), normalized);
        }
    }

    private int firstEntityPatternAfter(String cypher, int start) {
        int attribute = cypher.indexOf("[:ObjectHasAttribute]", start);
        int type = cypher.indexOf("[:ObjectInstanceOf]", start);
        if (attribute < 0) return type;
        if (type < 0) return attribute;
        return Math.min(attribute, type);
    }

    private int firstReceiverBinding(String cypher) {
        int attribute = cypher.indexOf(" AS attrOwner");
        int type = cypher.indexOf(" AS typeRecv");
        int cast = cypher.indexOf(" AS castRecv");
        return java.util.stream.IntStream.of(attribute, type, cast)
                .filter(index -> index >= 0).min().orElse(-1);
    }

    private MModel model() {
        String specification = """
                model BottomSafeReceiver
                class Person
                attributes age : Integer
                end
                """;
        StringWriter diagnostics = new StringWriter();
        MModel model = USECompiler.compileSpecification(specification, "bottom-safe-receiver.use",
                new PrintWriter(diagnostics, true), new ModelFactory());
        assertNotNull(model, diagnostics.toString());
        return model;
    }
}
