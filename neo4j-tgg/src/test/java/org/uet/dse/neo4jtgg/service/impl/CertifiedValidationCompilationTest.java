package org.uet.dse.neo4jtgg.service.impl;

import org.junit.jupiter.api.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.uet.dse.neo4jtgg.ocl.diagnostic.OclDiagnosticCode;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression contract for the validation service's certified context path. */
class CertifiedValidationCompilationTest {
    @Test
    void certifiedFilePathAcceptsOclValAndRejectsGeneralOnlyContextInvariant() {
        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model());

        var admittedAst = OclDocumentParser.parse(
                "context Person inv Adult: self.age >= 18");
        var admitted = compiler.compileFileWithCertifiedContextInvariants(admittedAst);
        assertEquals(1, admitted.getRuleResults().size());
        assertTrue(admitted.getRuleResults().get(0).isSupported(),
                admitted.getRuleResults().get(0).getReason());

        String generalOnly =
                "context Company inv Experimental: self.employee->one(p | p.age >= 18)";
        var generalAst = OclDocumentParser.parse(generalOnly);
        assertTrue(compiler.compileFile(generalAst).getRuleResults().get(0).isSupported(),
                "The explicitly general compiler remains available");

        var certified = compiler.compileFileWithCertifiedContextInvariants(generalAst);
        assertFalse(certified.getRuleResults().get(0).isSupported());
        assertTrue(certified.getRuleResults().get(0).getDiagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.code()
                        == OclDiagnosticCode.OCL_VAL_EXCLUDED_CONSTRUCT));
    }

    @Test
    void validationServicePinsBothDocumentAndSingleRulePathsToCertifiedContexts() throws IOException {
        String source = Files.readString(moduleRoot().resolve(
                "src/main/java/org/uet/dse/neo4jtgg/service/impl/DefaultOclValidationService.java"));
        assertTrue(source.contains("compileFileWithCertifiedContextInvariants(astFile)"));
        assertFalse(source.contains("compiler.compileFile(astFile)"),
                "Validation service must not silently return to the general context binder");
        assertTrue(source.contains("requireCertifiedContextPremises("));
        assertTrue(source.contains("requireGraphScalarClosed("));
    }

    @Test
    void researchToolPinsCertifiedCompilationAndBothExecutionPremiseFamilies() throws IOException {
        String source = Files.readString(moduleRoot().resolve(
                "src/main/java/org/uet/dse/neo4jtgg/ui/ResearchToolDialog.java"));
        assertTrue(source.contains("compileInvariantInstrumented(invariant)"));
        assertTrue(source.contains("requireGraphPremises()"));
        assertTrue(source.contains("requireUseSystemScalarClosed("));
        assertTrue(source.contains("requireGeneratedBottomSeparated("));
    }

    private MModel model() {
        String specification = """
                model CertifiedValidation
                class Company
                end
                class Person
                attributes
                    age : Integer
                end
                association Employment between
                    Company[1] role employer
                    Person[*] role employee
                end
                """;
        StringWriter diagnostics = new StringWriter();
        MModel result = USECompiler.compileSpecification(
                specification, "certified-validation.use",
                new PrintWriter(diagnostics, true), new ModelFactory());
        assertNotNull(result, diagnostics.toString());
        return result;
    }

    private Path moduleRoot() {
        Path working = Path.of(System.getProperty("user.dir"));
        for (Path candidate : new Path[]{working, working.resolve("neo4j-tgg")}) {
            if (Files.isDirectory(candidate.resolve("src/main/java/org/uet/dse/neo4jtgg"))) {
                return candidate.normalize();
            }
        }
        throw new IllegalStateException("Cannot locate neo4j-tgg module from " + working);
    }
}
