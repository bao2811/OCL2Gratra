package org.uet.dse.neo4jtgg.experiment;

import org.junit.jupiter.api.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.uet.dse.neo4jtgg.ocl.OclMetamodelIndex;
import org.uet.dse.neo4jtgg.ocl.OclSemanticBinder;
import org.uet.dse.neo4jtgg.ocl.OclTypeBinding;
import org.uet.dse.neo4jtgg.ocl.ir.OclCypherPlan;
import org.uet.dse.neo4jtgg.ocl.ir.OclCypherPlanner;
import org.uet.dse.neo4jtgg.ocl.ir.OclCypherRenderer;
import org.uet.dse.neo4jtgg.ocl.ir.OclIr;
import org.uet.dse.neo4jtgg.ocl.ir.OclIrBuilder;
import org.uet.dse.neo4jtgg.ocl.ir.OclIrOptimizer;
import org.uet.dse.neo4jtgg.service.impl.DefaultOclToCypherCompiler;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OclCypherPlanFormalTreeAgreementTest {
    private final MModel model = model();
    private final DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);

    @Test
    void everyPlanConstructorHasExpectedFormalCypherTreeWitness() {
        ExpectedFormalCypherTreeVerifier.assertEveryPlanConstructorHasAFormalRule();
        List<OclCypherPlan.InvariantPlan> results = compilationCorpus();
        Set<Class<?>> covered = new LinkedHashSet<>();
        for (OclCypherPlan.InvariantPlan result : results) {
            var agreement = ExpectedFormalCypherTreeVerifier.verifyInvariant(result, model.name());
            assertFalse(agreement.normalizedGeneratedText().isBlank());
            covered.addAll(ExpectedFormalCypherTreeVerifier.constructorClasses(result.predicate()));
        }
        assertEquals(new LinkedHashSet<>(Arrays.asList(
                        OclCypherPlan.ExpressionPlan.class.getPermittedSubclasses())),
                covered, "The executable corpus must reach every sealed OclCypherPlan constructor");
    }

    @Test
    void constructorAgreementRejectsSemanticallyWrongButLexicallyParseableLowerings() {
        List<OclCypherPlan.InvariantPlan> results = compilationCorpus();
        OclCypherRenderer renderer = new OclCypherRenderer(model.name());

        OclCypherPlan.AttributeAccessPlan attribute = find(results, OclCypherPlan.AttributeAccessPlan.class);
        assertMutationRejected(renderer, attribute, text -> text.replace("attributeKey", "name"));

        OclCypherPlan.NavigationAccessPlan navigation = results.stream()
                .map(OclCypherPlan.InvariantPlan::predicate)
                .map(plan -> find(plan, OclCypherPlan.NavigationAccessPlan.class))
                .filter(java.util.Objects::nonNull)
                .filter(plan -> plan.navigation().direction()
                        == org.uet.dse.neo4jtgg.ocl.OclMetamodelIndex.NavigationDirection.OUTGOING)
                .findFirst().orElseThrow();
        assertMutationRejected(renderer, navigation,
                text -> text.replace(")-[r]->(", ")<-[r]-("));

        OclCypherPlan.CountSubqueryComparisonPlan count = find(results, OclCypherPlan.CountSubqueryComparisonPlan.class);
        assertMutationRejected(renderer, count, text -> text.replaceFirst("COUNT", "COLLECT"));

        OclCypherPlan.NotExistsSubqueryPlan notExists = find(results, OclCypherPlan.NotExistsSubqueryPlan.class);
        assertMutationRejected(renderer, notExists, text -> text.replaceFirst("NOT EXISTS", "EXISTS"));

        OclCypherPlan.MethodCallPlan allInstances = results.stream()
                .map(OclCypherPlan.InvariantPlan::predicate)
                .map(plan -> find(plan, OclCypherPlan.MethodCallPlan.class))
                .filter(java.util.Objects::nonNull)
                .filter(plan -> "allInstances".equalsIgnoreCase(plan.methodName()))
                .findFirst().orElseThrow();
        assertMutationRejected(renderer, allInstances, text -> text.replace(":UmlClass", ":Class"));
    }

    private void assertMutationRejected(OclCypherRenderer renderer,
                                        OclCypherPlan.ExpressionPlan plan,
                                        java.util.function.UnaryOperator<String> mutation) {
        OclCypherRenderer.RenderedTopLevelExpression rendered = renderer.renderTopLevelExpression(plan);
        String mutant = mutation.apply(rendered.cypher());
        assertFalse(mutant.equals(rendered.cypher()), "Mutation did not change " + plan.getClass().getSimpleName());
        assertThrows(AssertionError.class, () -> ExpectedFormalCypherTreeVerifier.verifyExpressionAgainstText(
                plan, mutant, rendered.parameters()));
    }

    private List<OclCypherPlan.InvariantPlan> compilationCorpus() {
        return namedCompilationCorpus().stream().map(NamedPlan::plan).toList();
    }

    List<NamedPlan> namedCompilationCorpus() {
        List<NamedPlan> results = new ArrayList<>();
        for (OclValFragmentCoverageTest.CoverageCase testCase : OclValFragmentCoverageTest.admittedCases())
            results.add(new NamedPlan(testCase.feature(),
                    compiler.compileInvariantInstrumented(testCase.ocl()).queryPlan()));
        results.add(new NamedPlan("experimental navigation sum", compileGeneral(
                "context Company inv Aggregate: self.employee->collect(p | p.age)->sum() >= 0")));
        results.add(new NamedPlan("experimental navigation max", compileGeneral(
                "context Company inv Maximum: self.employee->collect(p | p.score)->max() >= 0.0")));
        results.add(new NamedPlan("general navigation isUnique", compileGeneral(
                "context Company inv UniqueNames: self.employee->isUnique(p | p.name)")));
        results.add(new NamedPlan("nested shadowed iterator", compileGeneral(
                "context Person inv NestedShadow: Set{self.age, 17}->exists(x | "
                        + "Set{x}->exists(x | x = self.age))")));
        OclTypeBinding bool = OclTypeBinding.scalar("Boolean");
        results.add(new NamedPlan("manual LetPlan", new OclCypherPlan.InvariantPlan(
                "Person", "ManualLetRendererWitness",
                new OclCypherPlan.LetPlan("x",
                        new OclCypherPlan.LiteralPlan(true, bool),
                        new OclCypherPlan.VariablePlan("x", bool), bool))));
        return List.copyOf(results);
    }

    private OclCypherPlan.InvariantPlan compileGeneral(String ocl) {
        var ast = compiler.parseContextInvariants(ocl).get(0);
        OclSemanticBinder.BoundContextInvariant bound =
                new OclSemanticBinder(new OclMetamodelIndex(model)).bindContext(ast);
        OclIr.InvariantQuery va = new OclIrBuilder().buildInvariant(bound);
        OclIr.InvariantQuery optimized = new OclIrOptimizer().optimizeInvariant(va);
        return new OclCypherPlanner().planInvariant(optimized);
    }

    private static <T> T find(List<OclCypherPlan.InvariantPlan> results, Class<T> type) {
        return results.stream().map(OclCypherPlan.InvariantPlan::predicate)
                .map(plan -> find(plan, type)).filter(java.util.Objects::nonNull).findFirst().orElseThrow();
    }

    private static <T> T find(Object value, Class<T> type) {
        return find(value, type, java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>()));
    }

    private static <T> T find(Object value, Class<T> type, Set<Object> seen) {
        if (value == null || value instanceof String || value instanceof Number
                || value instanceof Boolean || value instanceof Enum<?> || !seen.add(value)) return null;
        if (type.isInstance(value)) return type.cast(value);
        if (value instanceof Collection<?> collection) {
            for (Object item : collection) {
                T found = find(item, type, seen);
                if (found != null) return found;
            }
            return null;
        }
        if (!value.getClass().isRecord()) return null;
        for (RecordComponent component : value.getClass().getRecordComponents()) {
            try {
                T found = find(component.getAccessor().invoke(value), type, seen);
                if (found != null) return found;
            } catch (ReflectiveOperationException ex) {
                throw new IllegalStateException(ex);
            }
        }
        return null;
    }

    static MModel model() {
        String specification = """
                model OclValCoverage
                class Company
                attributes name : String
                end
                class Person
                attributes
                    name : String
                    age : Integer
                    score : Real
                end
                class Employee < Person end
                class Library end
                class Book end
                association Employment between
                    Company[1] role employer
                    Person[*] role employee
                end
                association Catalog between
                    Library[1] role library qualifier (shelf : String)
                    Book[*] role book
                end
                """;
        StringWriter diagnostics = new StringWriter();
        MModel result = USECompiler.compileSpecification(specification, "oclval-formal-tree.use",
                new PrintWriter(diagnostics, true), new ModelFactory());
        assertNotNull(result, diagnostics.toString());
        return result;
    }

    record NamedPlan(String name, OclCypherPlan.InvariantPlan plan) {
    }
}
