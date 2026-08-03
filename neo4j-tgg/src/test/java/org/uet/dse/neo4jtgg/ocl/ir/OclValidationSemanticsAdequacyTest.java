package org.uet.dse.neo4jtgg.ocl.ir;

import org.junit.jupiter.api.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.uet.dse.neo4jtgg.model.CypherCompilationResult;
import org.uet.dse.neo4jtgg.service.impl.DefaultOclToCypherCompiler;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OclValidationSemanticsAdequacyTest {
    @Test
    void missingAttributeValueMakesIsDefinedPredicateNonTrue() {
        CypherCompilationResult result = compiler().compile(
                "context Family inv NameDefined: self.name.isDefined()");

        assertTrue(result.isSupported(), result.getReason());
        assertTrue(result.getCypher().contains("CASE WHEN head(["), result.getCypher());
        assertTrue(result.getCypher().contains("IS NULL"), result.getCypher());
        assertTrue(result.getCypher().contains("THEN null"), result.getCypher());
        assertTrue(result.getCypher().contains("IS NOT NULL"), result.getCypher());
        assertTrue(result.getCypher().contains("WHERE NOT coalesce("), result.getCypher());
    }

    @Test
    void missingAttributeValueCanSatisfyIsUndefinedPredicate() {
        CypherCompilationResult result = compiler().compile(
                "context Family inv NameUndefined: self.name.isUndefined()");

        assertTrue(result.isSupported(), result.getReason());
        assertTrue(result.getCypher().contains("CASE WHEN head(["), result.getCypher());
        assertTrue(result.getCypher().contains("THEN null"), result.getCypher());
        assertTrue(result.getCypher().contains("IS NULL"), result.getCypher());
        assertTrue(result.getCypher().contains("WHERE NOT coalesce("), result.getCypher());
    }

    @Test
    void missingOptionalNavigationIsRepresentedAsEmptyNavigationSet() {
        CypherCompilationResult result = compiler().compile(
                "context Family inv FatherOptional: self.father->isEmpty()");

        assertTrue(result.isSupported(), result.getReason());
        assertTrue(result.getCypher().contains("NOT EXISTS { MATCH (self)-[r]->(nav)"), result.getCypher());
        assertTrue(result.getCypher().contains("WHERE NOT coalesce(NOT EXISTS"), result.getCypher());
    }

    @Test
    void undefinedAntecedentInImpliesUsesValidationBooleanCoercion() {
        CypherCompilationResult result = compiler().compile(
                "context Family inv SimpsonHasFather: self.name = 'Simpson' implies self.father->notEmpty()");

        assertTrue(result.isSupported(), result.getReason());
        assertTrue(result.getCypher().contains("NOT coalesce("), result.getCypher());
        assertTrue(result.getCypher().contains(" OR coalesce("), result.getCypher());
        assertTrue(result.getCypher().contains("EXISTS { MATCH (self)-[r]->(nav)"), result.getCypher());
    }

    @Test
    void falseLiteralPredicateIsRenderedAsViolationCondition() {
        OclCypherPlan.InvariantPlan plan = new OclCypherPlan.InvariantPlan(
                "Family",
                "FalsePredicate",
                new OclCypherPlan.LiteralPlan(Boolean.FALSE,
                        org.uet.dse.neo4jtgg.ocl.OclTypeBinding.scalar("Boolean")));

        OclCypherRenderer.RenderedInvariant rendered = new OclCypherRenderer().renderInvariant(plan);

        assertTrue(rendered.cypher().contains("WHERE NOT coalesce($"), rendered.cypher());
        assertTrue(rendered.parameters().containsValue(Boolean.FALSE), rendered.parameters().toString());
    }

    private DefaultOclToCypherCompiler compiler() {
        return new DefaultOclToCypherCompiler(model());
    }

    private MModel model() {
        String spec = """
                model Demo
                class Family
                attributes
                    name : String
                end
                class Person
                attributes
                    name : String
                end
                association FamilyFather between
                    Family[*] role family
                    Person[0..1] role father
                end
                """;
        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());
        return model;
    }
}
