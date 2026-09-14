package org.uet.dse.ocl2cypher.caseStudy;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.uet.dse.ocl2cypher.api.FrontendCompiler;
import org.uet.dse.ocl2cypher.core.CoreLowering;
import org.uet.dse.ocl2cypher.cypher.CypherAst;
import org.uet.dse.ocl2cypher.cypher.Neo4jCypherParserGate;
import org.uet.dse.ocl2cypher.cypher.Realization;
import org.uet.dse.ocl2cypher.cypher.Serializer;
import org.uet.dse.ocl2cypher.graph.GraphBuilder;
import org.uet.dse.ocl2cypher.graph.GraphModel;
import org.uet.dse.ocl2cypher.qcyp.QCypTranslator;
import org.uet.dse.ocl2cypher.source.model.SchemaModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards the scope-aware realization against reintroducing textual tree explosion. */
class GeneratedQuerySizeRegressionTest {
    private static final Path BASE = Path.of("..", "examples", "carrental", "umlmm");

    @Test
    void repeatedTaggedObservationsEvaluateEachSourceOccurrenceOnce() throws Exception {
        var fixture = CarRentalFixture.read(BASE.resolve("carrentalmodel.use"),
                BASE.resolve("carrental-experiment.soil")).toExecutable();
        GraphModel graph = GraphBuilder.build(fixture.schema(), fixture.snapshot()).value().graph();

        Map<String, Counts> expected = Map.of(
                "Car::GloballyUniqueId", new Counts(3, 2),
                "CarGroup::QualityNotReflexive", new Counts(2, 0),
                "Branch::ManagerSalaryDominates", new Counts(5, 2));
        for (var spec : CaseStudyReplayer.loadInvariants(BASE.resolve("invariants-extended.ocl"))) {
            String key = spec.context() + "::" + spec.name();
            if (!expected.containsKey(key)) continue;
            var artifact = realize(spec.source(), fixture.schema(), graph);
            String text = Serializer.cypherText(artifact);
            Counts counts = expected.get(key);
            assertEquals(counts.collectSubqueries(), occurrences(text, "COLLECT {"), key);
            assertEquals(counts.attributeSlotPaths(),
                    occurrences(text, GraphModel.OBJECT_HAS_ATTRIBUTE), key);
            assertTrue(text.length() < 20_000,
                    () -> key + " unexpectedly expanded to " + text.length() + " characters");
            Neo4jCypherParserGate.assertParses(text);
        }
    }

    @Test
    void longE2eNamespaceIsParameterDataRatherThanRepeatedQueryText() throws Exception {
        var fixture = CarRentalFixture.read(BASE.resolve("carrentalmodel.use"),
                BASE.resolve("carrental-experiment.soil")).toExecutable();
        var spec = CaseStudyReplayer.loadInvariants(BASE.resolve("invariants-extended.ocl"))
                .stream().filter(s -> s.name().equals("GloballyUniqueId")).findFirst().orElseThrow();
        GraphModel shortGraph = GraphBuilder.build(fixture.schema(), fixture.snapshot()).value().graph();

        String longKey = "ocl2cypher-e2e-carrental-00000000-0000-0000-0000-000000000000";
        SchemaModel.Builder scoped = SchemaModel.builder(longKey);
        fixture.schema().classes().forEach(scoped::clazz);
        fixture.schema().attributes().forEach(scoped::attribute);
        fixture.schema().associations().forEach(scoped::association);
        GraphModel longGraph = GraphBuilder.build(scoped.build(), fixture.snapshot()).value().graph();

        var shortArtifact = realize(spec.source(), fixture.schema(), shortGraph);
        var longArtifact = realize(spec.source(), fixture.schema(), longGraph);
        String shortText = Serializer.cypherText(shortArtifact);
        String longText = Serializer.cypherText(longArtifact);
        assertEquals(shortText, longText, "model namespace must not be duplicated as text");
        assertTrue(longText.contains("$__oclModelKey"));
        assertFalse(longText.contains(longKey));
        assertTrue(longArtifact.parameters().stream().anyMatch(p ->
                p.logicalTypeTag().equals("Physical:ModelKey")
                        && longKey.equals(p.canonicalValue())));
        Neo4jCypherParserGate.assertParses(longText);
    }

    @Test
    void everyAdmittedCarRentalInvariantProducesBoundParseableCypher() throws Exception {
        var fixture = CarRentalFixture.read(BASE.resolve("carrentalmodel.use"),
                BASE.resolve("carrental-experiment.soil")).toExecutable();
        GraphModel graph = GraphBuilder.build(fixture.schema(), fixture.snapshot()).value().graph();
        int generated = 0;
        for (var spec : CaseStudyReplayer.loadInvariants(BASE.resolve("invariants-extended.ocl"))) {
            var frontend = FrontendCompiler.compile(spec.source(), fixture.schema());
            if (frontend.isFailure()) continue;
            String key = spec.context() + "::" + spec.name();
            var artifact = realize(spec.source(), fixture.schema(), graph);
            String text = Serializer.cypherText(artifact);
            assertTrue(text.length() < 20_000,
                    () -> key + " unexpectedly expanded to " + text.length() + " characters");
            assertEquals(1, artifact.parameters().stream()
                    .filter(p -> p.logicalTypeTag().equals("Physical:ModelKey")).count(), key);
            assertFalse(text.contains(graph.modelKey()),
                    key + " must reference the model-key parameter, not copy its payload");
            Neo4jCypherParserGate.assertParses(text);
            generated++;
        }
        assertEquals(19, generated, "CarRental admitted-query inventory changed");
    }

    private static CypherAst.GeneratedArtifact realize(String source, SchemaModel schema,
                                                        GraphModel graph) {
        var frontend = FrontendCompiler.compile(source, schema);
        assertTrue(frontend.isSuccess(), () -> frontend.diagnostics().toString());
        var document = frontend.value().get(0);
        var core = CoreLowering.lower(schema, document, document.constraints.get(0));
        assertTrue(core.isSuccess(), () -> core.diagnostics().toString());
        var q = QCypTranslator.translate(core.value());
        assertTrue(q.isSuccess(), () -> q.diagnostics().toString());
        var realized = Realization.realize(q.value(), graph, CypherAst.Dialect.CYPHER_5);
        assertTrue(realized.isSuccess(), () -> realized.diagnostics().toString());
        return realized.value();
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        for (int at = 0; (at = text.indexOf(needle, at)) >= 0; at += needle.length()) count++;
        return count;
    }

    private record Counts(int collectSubqueries, int attributeSlotPaths) {}
}
