package org.uet.dse.neo4jtgg.experiment;

import org.junit.jupiter.api.Test;
import org.tzi.use.uml.mm.MModel;
import org.uet.dse.neo4jtgg.ocl.OclMetamodelIndex;
import org.uet.dse.neo4jtgg.ocl.OclSemanticBinder;
import org.uet.dse.neo4jtgg.ocl.OclTypeBinding;
import org.uet.dse.neo4jtgg.ocl.ir.OclCypherPlan;
import org.uet.dse.neo4jtgg.ocl.ir.OclCypherPlanner;
import org.uet.dse.neo4jtgg.ocl.ir.OclIr;
import org.uet.dse.neo4jtgg.ocl.ir.OclIrBuilder;
import org.uet.dse.neo4jtgg.ocl.ir.OclIrOptimizer;
import org.uet.dse.neo4jtgg.service.impl.DefaultOclToCypherCompiler;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Executable field-preserving Java IR-to-query-plan refinement witnesses for T4. */
class OclIrPlanPayloadRefinementTest {
    private final MModel model = OclCypherPlanFormalTreeAgreementTest.model();
    private final DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
    private final OclCypherPlanner planner = new OclCypherPlanner();

    @Test
    void allSixteenOptimizedConstructorsHaveExecutableFieldPreservingWitnesses() {
        List<Witness> witnesses = witnesses();
        Set<Class<?>> covered = new LinkedHashSet<>();
        for (Witness witness : witnesses) {
            PipelineRefinementVerifier.verifyOptimizedToPlan(witness.source(), witness.target());
            collectConstructors(witness.source(), covered,
                    Collections.newSetFromMap(new IdentityHashMap<>()));
        }
        assertEquals(new LinkedHashSet<>(Arrays.asList(
                        OclIr.OptimizedExpression.class.getPermittedSubclasses())),
                covered, "The refinement corpus must reach every production OptimizedExpression constructor");
    }

    @Test
    void payloadAndLoweringMutationsAreRejected() {
        List<Witness> witnesses = witnesses();

        Witness variableWitness = findWitness(witnesses, OclIr.Variable.class);
        OclIr.Variable variable = find(variableWitness.source(), OclIr.Variable.class,
                Collections.newSetFromMap(new IdentityHashMap<>()));
        OclCypherPlan.VariablePlan variablePlan =
                (OclCypherPlan.VariablePlan) find(variableWitness.target(), OclCypherPlan.VariablePlan.class,
                        Collections.newSetFromMap(new IdentityHashMap<>()));
        reject(variable, new OclCypherPlan.VariablePlan(variablePlan.name() + "_mut", variablePlan.type()));

        Witness letWitness = findWitness(witnesses, OclIr.Let.class);
        OclIr.Let let = find(letWitness.source(), OclIr.Let.class,
                Collections.newSetFromMap(new IdentityHashMap<>()));
        OclCypherPlan.LetPlan letPlan = (OclCypherPlan.LetPlan) find(
                letWitness.target(), OclCypherPlan.LetPlan.class,
                Collections.newSetFromMap(new IdentityHashMap<>()));
        reject(let, new OclCypherPlan.LetPlan(
                letPlan.variableName(), letPlan.value(), OclTypeBinding.scalar("String"),
                letPlan.body(), letPlan.type()));

        Witness literalWitness = findWitness(witnesses, OclIr.Literal.class);
        OclIr.Literal literal = find(literalWitness.source(), OclIr.Literal.class,
                Collections.newSetFromMap(new IdentityHashMap<>()));
        OclCypherPlan.LiteralPlan literalPlan =
                (OclCypherPlan.LiteralPlan) find(literalWitness.target(), OclCypherPlan.LiteralPlan.class,
                        Collections.newSetFromMap(new IdentityHashMap<>()));
        reject(literal, new OclCypherPlan.LiteralPlan("mutated", literalPlan.type()));

        Witness attributeWitness = findWitness(witnesses, OclIr.AttributeAccess.class);
        OclIr.AttributeAccess attribute = find(attributeWitness.source(), OclIr.AttributeAccess.class,
                Collections.newSetFromMap(new IdentityHashMap<>()));
        OclCypherPlan.AttributeAccessPlan attributePlan =
                (OclCypherPlan.AttributeAccessPlan) find(
                        attributeWitness.target(), OclCypherPlan.AttributeAccessPlan.class,
                        Collections.newSetFromMap(new IdentityHashMap<>()));
        reject(attribute, new OclCypherPlan.AttributeAccessPlan(
                attributePlan.source(), attributePlan.attributeName() + "_mut",
                attributePlan.attributeType(), attributePlan.type(), attributePlan.attribute()));

        Witness iteratorWitness = findWitness(witnesses, OclIr.IteratorOperation.class);
        OclIr.IteratorOperation iterator = find(iteratorWitness.source(), OclIr.IteratorOperation.class,
                Collections.newSetFromMap(new IdentityHashMap<>()));
        OclCypherPlan.IteratorOperationPlan iteratorPlan =
                (OclCypherPlan.IteratorOperationPlan) find(
                        iteratorWitness.target(), OclCypherPlan.IteratorOperationPlan.class,
                        Collections.newSetFromMap(new IdentityHashMap<>()));
        reject(iterator, new OclCypherPlan.IteratorOperationPlan(
                iteratorPlan.source(), iteratorPlan.sourceCollectionType(),
                iteratorPlan.operationName(), iteratorPlan.iteratorName() + "_mut",
                iteratorPlan.iteratorVariableType(),
                iteratorPlan.body(), iteratorPlan.type()));
        reject(iterator, new OclCypherPlan.IteratorOperationPlan(
                iteratorPlan.source(), OclTypeBinding.scalar("Integer"),
                iteratorPlan.operationName(), iteratorPlan.iteratorName(),
                iteratorPlan.iteratorVariableType(),
                iteratorPlan.body(), iteratorPlan.type()));
        reject(iterator, new OclCypherPlan.IteratorOperationPlan(
                iteratorPlan.source(), iteratorPlan.sourceCollectionType(),
                iteratorPlan.operationName(), iteratorPlan.iteratorName(),
                OclTypeBinding.scalar("String"), iteratorPlan.body(), iteratorPlan.type()));

        Witness predicateWitness = findWitness(witnesses, OclIr.NavigationPredicateCheck.class);
        OclIr.NavigationPredicateCheck predicate = find(
                predicateWitness.source(), OclIr.NavigationPredicateCheck.class,
                Collections.newSetFromMap(new IdentityHashMap<>()));
        OclIr.NavigationPredicateKind mutatedKind = predicate.kind() == OclIr.NavigationPredicateKind.EXISTS
                ? OclIr.NavigationPredicateKind.NOT_EXISTS : OclIr.NavigationPredicateKind.EXISTS;
        OclIr.NavigationPredicateCheck predicateMutant = new OclIr.NavigationPredicateCheck(
                predicate.navigation(), predicate.iteratorName(), predicate.predicate(), mutatedKind, predicate.type());
        reject(predicateMutant, predicateWitness.target());

        Witness countWitness = findWitness(witnesses, OclIr.NavigationCountComparison.class);
        OclIr.NavigationCountComparison count = find(
                countWitness.source(), OclIr.NavigationCountComparison.class,
                Collections.newSetFromMap(new IdentityHashMap<>()));
        OclIr.NavigationCountComparison countMutant = new OclIr.NavigationCountComparison(
                count.navigation(), count.iteratorName(), count.predicate(), "=", 0L, count.type());
        reject(countMutant, countWitness.target());

        Witness aggregationWitness = findWitness(witnesses, OclIr.NavigationAggregation.class);
        OclIr.NavigationAggregation aggregation = find(
                aggregationWitness.source(), OclIr.NavigationAggregation.class,
                Collections.newSetFromMap(new IdentityHashMap<>()));
        OclCypherPlan.NavigationAggregationPlan aggregationPlan =
                find(aggregationWitness.target(), OclCypherPlan.NavigationAggregationPlan.class,
                        Collections.newSetFromMap(new IdentityHashMap<>()));
        reject(aggregation, new OclCypherPlan.NavigationAggregationPlan(
                aggregationPlan.match(), aggregationPlan.projection(),
                aggregationPlan.operationName() + "_mut", aggregationPlan.type()));
    }

    private void reject(OclIr.Expression source, OclCypherPlan.ExpressionPlan mutant) {
        assertThrows(AssertionError.class,
                () -> PipelineRefinementVerifier.verifyOptimizedToPlan(source, mutant));
    }

    private List<Witness> witnesses() {
        List<Witness> result = new ArrayList<>();
        for (OclValFragmentCoverageTest.CoverageCase testCase : OclValFragmentCoverageTest.admittedCases()) {
            InstrumentedCompilationResult compiled = compiler.compileInvariantInstrumented(testCase.ocl());
            result.add(new Witness(compiled.normalizedValidationAlgebra().predicate(),
                    compiled.queryPlan().predicate()));
        }
        result.add(compileGeneral(
                "context Company inv Aggregate: self.employee->collect(p | p.age)->sum() >= 0"));
        result.add(compileGeneral(
                "context Company inv Maximum: self.employee->collect(p | p.score)->max() >= 0.0"));
        result.add(compileGeneral(
                "context Company inv UniqueNames: self.employee->isUnique(p | p.name)"));

        result.add(compileGeneral(
                "context Person inv RetainedLet: let measuredAge = self.age in measuredAge >= 18"));
        return List.copyOf(result);
    }

    private Witness compileGeneral(String ocl) {
        var ast = compiler.parseContextInvariants(ocl).get(0);
        OclSemanticBinder.BoundContextInvariant bound =
                new OclSemanticBinder(new OclMetamodelIndex(model)).bindContext(ast);
        OclIr.InvariantQuery va = new OclIrBuilder().buildInvariant(bound);
        OclIr.InvariantQuery optimized = new OclIrOptimizer().optimizeInvariant(va);
        return new Witness(optimized.predicate(), planner.planInvariant(optimized).predicate());
    }

    private static void collectConstructors(Object value, Set<Class<?>> constructors, Set<Object> seen) {
        if (value == null || value instanceof String || value instanceof Number
                || value instanceof Boolean || value instanceof Enum<?> || !seen.add(value)) return;
        if (value instanceof OclIr.OptimizedExpression) constructors.add(value.getClass());
        if (value instanceof Collection<?> collection) {
            collection.forEach(item -> collectConstructors(item, constructors, seen));
            return;
        }
        if (!value.getClass().isRecord()) return;
        for (RecordComponent component : value.getClass().getRecordComponents()) {
            try {
                collectConstructors(component.getAccessor().invoke(value), constructors, seen);
            } catch (ReflectiveOperationException ex) {
                throw new IllegalStateException(ex);
            }
        }
    }

    private static <T> T find(List<Witness> witnesses, Class<T> type) {
        return witnesses.stream().map(Witness::source).map(value -> find(value, type,
                        Collections.newSetFromMap(new IdentityHashMap<>())))
                .filter(java.util.Objects::nonNull).findFirst().orElseThrow();
    }

    private static Witness findWitness(List<Witness> witnesses, Class<?> type) {
        return witnesses.stream().filter(witness -> find(witness.source(), type,
                        Collections.newSetFromMap(new IdentityHashMap<>())) != null)
                .findFirst().orElseThrow();
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

    private record Witness(OclIr.Expression source, OclCypherPlan.ExpressionPlan target) {
    }
}
