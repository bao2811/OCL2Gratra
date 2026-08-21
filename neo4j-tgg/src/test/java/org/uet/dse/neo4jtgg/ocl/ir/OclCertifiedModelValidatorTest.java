package org.uet.dse.neo4jtgg.ocl.ir;

import org.junit.jupiter.api.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.uet.dse.neo4jtgg.ocl.OclTypeBinding;
import org.uet.dse.neo4jtgg.service.impl.DefaultOclToCypherCompiler;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OclCertifiedModelValidatorTest {
    private static final OclTypeBinding BOOLEAN = OclTypeBinding.scalar("Boolean");
    private static final OclTypeBinding INTEGER = OclTypeBinding.scalar("Integer");

    @Test
    void certifiedPipelineProducesWfOvaAndCqmArtifacts() {
        var result = new DefaultOclToCypherCompiler(model()).compileInvariantInstrumented(
                "context Person inv Adult: self.age >= 18");

        assertTrue(OclCertifiedModelValidator.wfOva(result.validationAlgebra()).valid());
        assertTrue(OclCertifiedModelValidator.certifiedOva(result.validationAlgebra()).valid());
        assertTrue(OclCertifiedModelValidator.wfOva(result.normalizedValidationAlgebra()).valid());
        assertTrue(OclCertifiedModelValidator.certifiedOva(result.normalizedValidationAlgebra()).valid());
        assertTrue(OclCertifiedModelValidator.wfCqm(result.queryPlan()).valid());
        assertTrue(OclCertifiedModelValidator.certifiedCqm(result.queryPlan()).valid());
        assertEquals(OclCypherPlan.ViolationPolicy.NOT_VALIDATION_TRUE,
                result.queryPlan().violationPolicy());
    }

    @Test
    void certifiedOvaRejectsOrderedAndBagCollectionKinds() {
        for (OclTypeBinding.CollectionKind kind : List.of(
                OclTypeBinding.CollectionKind.BAG,
                OclTypeBinding.CollectionKind.SEQUENCE,
                OclTypeBinding.CollectionKind.ORDERED_SET)) {
            OclIr.InvariantQuery query = new OclIr.InvariantQuery(
                    "Person", "BadCollection",
                    new OclIr.CollectionOperation(
                            new OclIr.SetLiteral(List.of(new OclIr.Literal(1, INTEGER)),
                                    OclTypeBinding.scalarCollection("Integer", kind)),
                            OclTypeBinding.scalarCollection("Integer", kind),
                            "notEmpty", List.of(), BOOLEAN));

            var report = OclCertifiedModelValidator.certifiedOva(query);
            assertFalse(report.valid(), kind.toString());
            assertTrue(report.violations().stream()
                    .anyMatch(v -> v.code().equals("CERT_OVA_COLLECTION")), report.toString());
        }
    }

    @Test
    void certifiedOvaAndCqmRejectGeneralOnlyAnyIterator() {
        OclTypeBinding setOfInteger = OclTypeBinding.scalarCollection(
                "Integer", OclTypeBinding.CollectionKind.SET);
        OclIr.InvariantQuery ova = new OclIr.InvariantQuery(
                "Person", "AnyIsGeneralOnly",
                new OclIr.IteratorOperation(
                        new OclIr.SetLiteral(List.of(new OclIr.Literal(1, INTEGER)), setOfInteger),
                        setOfInteger, "any", "x", INTEGER,
                        new OclIr.Literal(true, BOOLEAN), BOOLEAN));
        OclCypherPlan.InvariantPlan cqm = new OclCypherPlan.InvariantPlan(
                "Person", "AnyIsGeneralOnly",
                new OclCypherPlan.IteratorOperationPlan(
                        new OclCypherPlan.SetLiteralPlan(
                                List.of(new OclCypherPlan.LiteralPlan(1, INTEGER)), setOfInteger),
                        setOfInteger, "any", "x", INTEGER,
                        new OclCypherPlan.LiteralPlan(true, BOOLEAN), BOOLEAN));

        assertTrue(OclCertifiedModelValidator.certifiedOva(ova).violations().stream()
                .anyMatch(v -> v.code().equals("CERT_OVA_ITERATOR")));
        assertTrue(OclCertifiedModelValidator.certifiedCqm(cqm).violations().stream()
                .anyMatch(v -> v.code().equals("CERT_CQM_ITERATOR")));
    }

    @Test
    void wfOvaRejectsUnboundVariablesAndNonBooleanInvariantRoots() {
        OclIr.InvariantQuery query = new OclIr.InvariantQuery(
                "Person", "Unbound", new OclIr.Variable("ghost", INTEGER));

        var report = OclCertifiedModelValidator.wfOva(query);
        assertFalse(report.valid());
        assertTrue(report.violations().stream().anyMatch(v -> v.code().equals("WF_OVA_SCOPE")));
        assertTrue(report.violations().stream().anyMatch(v -> v.code().equals("WF_OVA_BOOLEAN_ROOT")));
    }

    @Test
    void validationPolicyTreatsEveryNonTrueValueAsViolation() {
        assertEquals("coalesce((p) = true, false)", OclValidationSemantics.validationTruth("p"));
        assertEquals("NOT coalesce((p) = true, false)", OclValidationSemantics.violationPredicate("p"));

        OclCypherPlan.InvariantPlan plan = new OclCypherPlan.InvariantPlan(
                "Person", "Policy", new OclCypherPlan.LiteralPlan(null, BOOLEAN));
        assertEquals(OclCypherPlan.ViolationPolicy.NOT_VALIDATION_TRUE, plan.violationPolicy());
        String query = new OclCypherRenderer("Demo").renderInvariant(plan).cypher();
        assertTrue(query.contains("WHERE NOT coalesce((null) = true, false)"), query);
    }

    private MModel model() {
        String specification = """
                model CertifiedValidator
                class Person
                attributes
                    age : Integer
                end
                """;
        StringWriter diagnostics = new StringWriter();
        MModel result = USECompiler.compileSpecification(
                specification, "certified-validator.use",
                new PrintWriter(diagnostics, true), new ModelFactory());
        assertNotNull(result, diagnostics.toString());
        return result;
    }
}
