package org.uet.dse.neo4jtgg.experiment;

import org.junit.jupiter.api.Test;
import org.uet.dse.neo4jtgg.ocl.ir.OclCypherPlan;
import org.uet.dse.neo4jtgg.ocl.ir.OclCypherRenderer;

import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Executable constructor-to-production-method/formal-rule/evidence contract for PO-14. */
class OclCypherPlanConstructorEvidenceMatrixTest {
    private static final List<String> HEADER = List.of(
            "constructor", "ir_constructor", "factory_method", "planner_method",
            "renderer_method", "formal_rule", "evidence_test", "evidence_method");
    private static final String EVIDENCE_METHOD =
            "everySealedPlanConstructorHasFactoryPlannerRendererFormalRuleAndRoundTripEvidence";

    @Test
    void everySealedPlanConstructorHasFactoryPlannerRendererFormalRuleAndRoundTripEvidence() throws Exception {
        Path workspace = workspace();
        List<Row> rows = rows(workspace.resolve(
                "verification/coverage/cypher_plan_constructor_matrix.csv"));

        List<String> permitted = Arrays.stream(OclCypherPlan.ExpressionPlan.class.getPermittedSubclasses())
                .map(Class::getSimpleName).toList();
        assertEquals(permitted, rows.stream().map(Row::constructor).toList());
        assertEquals(rows.size(), new LinkedHashSet<>(rows.stream().map(Row::constructor).toList()).size());

        String ir = Files.readString(workspace.resolve(
                "neo4j-tgg/src/main/java/org/uet/dse/neo4jtgg/ocl/ir/OclIr.java"));
        String factory = Files.readString(workspace.resolve(
                "neo4j-tgg/src/main/java/org/uet/dse/neo4jtgg/ocl/ir/OclCypherQueryModel.java"));
        String planner = Files.readString(workspace.resolve(
                "neo4j-tgg/src/main/java/org/uet/dse/neo4jtgg/ocl/ir/OclCypherPlanner.java"));
        String renderer = Files.readString(workspace.resolve(
                "neo4j-tgg/src/main/java/org/uet/dse/neo4jtgg/ocl/ir/OclCypherRenderer.java"));
        String formal = Files.readString(workspace.resolve(
                "neo4j-tgg/src/test/java/org/uet/dse/neo4jtgg/experiment/ExpectedFormalCypherTreeVerifier.java"));

        Map<Class<?>, OclCypherPlan.ExpressionPlan> witnesses = constructorWitnesses();
        assertEquals(new LinkedHashSet<>(Arrays.asList(
                OclCypherPlan.ExpressionPlan.class.getPermittedSubclasses())), witnesses.keySet());

        OclCypherRenderer cypherRenderer = new OclCypherRenderer("OclValCoverage");
        for (Row row : rows) {
            Class<?> constructor = Arrays.stream(OclCypherPlan.ExpressionPlan.class.getPermittedSubclasses())
                    .filter(type -> type.getSimpleName().equals(row.constructor())).findFirst().orElseThrow();
            assertDeclaration(ir, "record", row.irConstructor());
            assertMethod(factory, row.factoryMethod());
            assertMethod(planner, row.plannerMethod());
            assertMethod(renderer, row.rendererMethod());
            assertMethod(formal, row.formalRule());
            assertEquals(getClass().getSimpleName(), row.evidenceTest());
            assertEquals(EVIDENCE_METHOD, row.evidenceMethod());
            getClass().getDeclaredMethod(row.evidenceMethod());

            OclCypherPlan.ExpressionPlan plan = witnesses.get(constructor);
            OclCypherRenderer.RenderedTopLevelExpression rendered =
                    cypherRenderer.renderTopLevelExpression(plan);
            String normalized = GeneratedCypherSyntaxTree.render(
                    GeneratedCypherSyntaxTree.parse(rendered.cypher()));
            assertEquals(normalized, GeneratedCypherSyntaxTree.render(
                    GeneratedCypherSyntaxTree.parse(normalized)), row.constructor());
            assertEquals(GeneratedCypherCanonicalTree.parse(rendered.cypher()).text(),
                    GeneratedCypherCanonicalTree.parse(normalized).text(), row.constructor());
            assertEquals(Neo4jCypherAstBridge.parse(rendered.cypher()).canonicalTree(),
                    Neo4jCypherAstBridge.parse(normalized).canonicalTree(), row.constructor());
            ExpectedFormalCypherTreeVerifier.verifyExpressionAgainstText(
                    plan, rendered.cypher(), rendered.parameters());
        }
    }

    private static void assertDeclaration(String source, String kind, String name) {
        assertTrue(Pattern.compile("\\b" + Pattern.quote(kind) + "\\s+" + Pattern.quote(name) + "\\b")
                .matcher(source).find(), kind + " " + name);
    }

    private static void assertMethod(String source, String name) {
        assertTrue(Pattern.compile("\\b" + Pattern.quote(name) + "\\s*\\(")
                .matcher(source).find(), "method " + name);
    }

    private static List<Row> rows(Path path) throws Exception {
        List<String> lines = Files.readAllLines(path).stream().filter(line -> !line.isBlank()).toList();
        assertFalse(lines.isEmpty());
        assertEquals(HEADER, List.of(lines.get(0).split(",", -1)));
        List<Row> result = new ArrayList<>();
        for (String line : lines.subList(1, lines.size())) {
            String[] value = line.split(",", -1);
            assertEquals(HEADER.size(), value.length, line);
            result.add(new Row(value[0], value[1], value[2], value[3], value[4], value[5], value[6], value[7]));
        }
        return List.copyOf(result);
    }

    private static Map<Class<?>, OclCypherPlan.ExpressionPlan> constructorWitnesses() {
        Map<Class<?>, OclCypherPlan.ExpressionPlan> result = new LinkedHashMap<>();
        OclCypherPlanFormalTreeAgreementTest corpus = new OclCypherPlanFormalTreeAgreementTest();
        for (OclCypherPlanFormalTreeAgreementTest.NamedPlan item : corpus.namedCompilationCorpus())
            collect(item.plan().predicate(), result, Collections.newSetFromMap(new IdentityHashMap<>()));
        return Collections.unmodifiableMap(result);
    }

    private static void collect(Object value, Map<Class<?>, OclCypherPlan.ExpressionPlan> target,
                                Set<Object> seen) {
        if (value == null || value instanceof String || value instanceof Number
                || value instanceof Boolean || value instanceof Enum<?> || !seen.add(value)) return;
        if (value instanceof OclCypherPlan.ExpressionPlan plan) target.putIfAbsent(plan.getClass(), plan);
        if (value instanceof Collection<?> collection) {
            collection.forEach(item -> collect(item, target, seen));
            return;
        }
        if (!value.getClass().isRecord()) return;
        for (RecordComponent component : value.getClass().getRecordComponents()) {
            try {
                collect(component.getAccessor().invoke(value), target, seen);
            } catch (ReflectiveOperationException ex) {
                throw new IllegalStateException(ex);
            }
        }
    }

    private static Path workspace() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve(
                    "verification/contract/proof-contract-registry.json"))) return current;
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate workspace root");
    }

    private record Row(String constructor, String irConstructor, String factoryMethod,
                       String plannerMethod, String rendererMethod, String formalRule,
                       String evidenceTest, String evidenceMethod) {
    }
}
