package org.uet.dse.neo4jtgg.experiment;

import org.junit.jupiter.api.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.uet.dse.neo4jtgg.ocl.OclSemanticBinder;
import org.uet.dse.neo4jtgg.ocl.OclTypeBinding;
import org.uet.dse.neo4jtgg.service.impl.DefaultOclToCypherCompiler;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Constructor-by-constructor executable evidence for PA9. */
class RendererAccessorAgreementTest {
    @Test
    void everyPa1ThroughPa8AccessorFamilySatisfiesTheGeneratedQueryContract() {
        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model());
        List<String> invariants = List.of(
                "context Person inv ContextAndId: self.age >= 0",
                "context Employee inv InheritedAttribute: self.age >= 0",
                "context Company inv ForwardNavigation: self.employee->notEmpty()",
                "context Employee inv ReverseNavigation: self.employer->notEmpty()",
                "context Library inv QualifiedForward: self.book['HCM']->notEmpty()",
                "context Book inv QualifiedReverse: self.library['A1']->notEmpty()",
                "context Person inv AllInstances: Person.allInstances()->includes(self)",
                "context Employee inv KindOf: self.oclIsKindOf(Person)",
                "context Employee inv Cast: self.oclAsType(Person).age >= 0");

        for (String invariant : invariants) {
            InstrumentedCompilationResult result = compiler.compileInvariantInstrumented(invariant);
            GeneratedCypherContractVerifier.verify(result, result.cypher(), result.parameters());
            if (invariant.contains("QualifiedReverse")) {
                OclSemanticBinder.BoundCollectionOperation operation =
                        (OclSemanticBinder.BoundCollectionOperation) result.bound().expression();
                OclSemanticBinder.BoundProperty navigation =
                        (OclSemanticBinder.BoundProperty) operation.source();
                assertTrue(navigation.navigation().targetSingleValued());
                assertTrue(navigation.navigation().resultBinding().isCollection());
                assertEquals(OclTypeBinding.CollectionKind.SET, navigation.type().collectionKind());
                assertEquals(navigation.type(), operation.sourceCollectionType());
            }
        }
    }

    private MModel model() {
        String specification = """
                model RendererAccessorAgreement
                class Person
                attributes age : Integer
                end
                class Employee < Person end
                class Company end
                class Library end
                class Book end
                association Employment between
                    Company[1] role employer
                    Employee[*] role employee
                end
                association Catalog between
                    Library[1] role library qualifier (shelf : String)
                    Book[*] role book qualifier (code : String)
                end
                """;
        StringWriter diagnostics = new StringWriter();
        MModel model = USECompiler.compileSpecification(specification, "renderer-accessor-agreement.use",
                new PrintWriter(diagnostics, true), new ModelFactory());
        assertNotNull(model, diagnostics.toString());
        return model;
    }
}
