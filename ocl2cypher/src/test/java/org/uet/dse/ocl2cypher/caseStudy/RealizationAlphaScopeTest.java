package org.uet.dse.ocl2cypher.caseStudy;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.uet.dse.ocl2cypher.api.*;
import org.uet.dse.ocl2cypher.core.CoreLowering;
import org.uet.dse.ocl2cypher.cypher.*;
import org.uet.dse.ocl2cypher.graph.GraphBuilder;
import org.uet.dse.ocl2cypher.qcyp.*;
import static org.junit.jupiter.api.Assertions.*;

/** Alpha-renaming witnesses, not a universal capture-avoidance proof. No DB writes. */
class RealizationAlphaScopeTest {
    @TestFactory Stream<DynamicTest> sourceNamesDoNotCaptureGeneratedAliases() throws Exception {
        Path base = Path.of("..", "examples", "carrental", "umlmm");
        var fixture = CarRentalFixture.read(base.resolve("carrentalmodel.use"),
                base.resolve("carrental-experiment.soil")).toExecutable();
        var templates = List.of(
                "context Branch inv Scope: self.employee->forAll(%s | %s.firstname <> '' and %s.lastname <> '')",
                "context Branch inv Scope: let %s = self.employee in %s->forAll(inner | inner.firstname <> '' and inner.lastname <> '')",
                "context Branch inv Scope: self.employee->forAll(%s | self.employee->forAll(%s | %s.firstname <> '' and %s.lastname <> ''))");
        List<DynamicTest> tests = new ArrayList<>();
        for (int i = 0; i < templates.size(); i++) {
            String template = templates.get(i);
            String canonical = render(template, "employeeBinder");
            String canonicalText = verify(canonical, fixture);
            for (String name : List.of("rel", "o", "t", "x", "self_0", "rel_7", "body_2", "__oclRole_3")) {
                tests.add(DynamicTest.dynamicTest("form" + i + "/" + name, () -> {
                    String source = render(template, name);
                    assertEquals(canonicalText, verify(source, fixture),
                            "alpha-renaming must not alter generated text or generated alias allocation");
                    assertEquals(verify(source, fixture), verify(source, fixture),
                            "fresh supply must restart for each realization");
                }));
            }
        }
        return tests.stream();
    }

    private static String render(String template, String name) {
        return String.format(template, name, name, name, name);
    }

    private static String verify(String source, CarRentalFixture.Executable fixture) {
        var schema = fixture.schema();
        assertEquals(Set.of("branch_bad"), Set.copyOf(CoreOracle.violationsOclEq(source, schema, fixture.snapshot())));
        var fe = FrontendCompiler.compile(source, schema);
        assertTrue(fe.isSuccess(), () -> fe.diagnostics().toString());
        var doc = fe.value().get(0);
        var core = CoreLowering.lower(schema, doc, doc.constraints.get(0));
        assertTrue(core.isSuccess(), () -> core.diagnostics().toString());
        var graph = GraphBuilder.build(schema, fixture.snapshot());
        assertTrue(graph.isSuccess(), () -> graph.diagnostics().toString());
        var q = QCypTranslator.translate(core.value());
        assertTrue(q.isSuccess(), () -> q.diagnostics().toString());
        assertEquals(Set.of("branch_bad"), Set.copyOf(QInterpreter.violations(schema,
                graph.value().graph(), core.value(), q.value())));
        var r = Realization.realize(q.value(), graph.value().graph(), CypherAst.Dialect.CYPHER_5);
        assertTrue(r.isSuccess(), () -> r.diagnostics().toString());
        String text = Serializer.cypherText(r.value());
        Neo4jCypherParserGate.assertParses(text);
        return text;
    }
}
