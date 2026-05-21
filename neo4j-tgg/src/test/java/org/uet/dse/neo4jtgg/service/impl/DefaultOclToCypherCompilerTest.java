package org.uet.dse.neo4jtgg.service.impl;

import org.junit.jupiter.api.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.uet.dse.neo4jtgg.model.CypherCompilationResult;
import org.uet.dse.neo4jtgg.model.OclFileCompilationResult;
import org.uet.dse.neo4jtgg.model.OclRuleKind;
import org.uet.dse.neo4jtgg.model.OclRuleOwnerKind;
import org.uet.dse.neo4jtgg.ocl.diagnostic.OclDiagnosticCode;
import org.uet.dse.neo4jtgg.ocl.diagnostic.OclCodedUnsupportedOperationException;
import org.uet.dse.neo4jtgg.ocl.OclMetamodelIndex;
import org.uet.dse.neo4jtgg.ocl.OclSemanticBinder;
import org.uet.dse.neo4jtgg.ocl.ir.OclCypherPlan;
import org.uet.dse.neo4jtgg.ocl.ir.OclCypherPlanner;
import org.uet.dse.neo4jtgg.ocl.ir.OclCypherRenderer;
import org.uet.dse.neo4jtgg.ocl.ir.OclIrBuilder;
import org.uet.dse.neo4jtgg.ocl.ir.OclIrOptimizer;

import java.io.PrintWriter;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DefaultOclToCypherCompilerTest {
    @Test
    void compilesMultipleInvariantsFromOneOclDocument() {
        String spec = """
                model Demo
                class Person
                attributes
                    name : String
                    age : Integer
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        OclFileCompilationResult result = compiler.compileFile("""
                context Person inv Adult: self.age >= 18
                context Person inv Named: self.name <> ''
                """);

        assertEquals(2, result.getRuleResults().size());
        assertTrue(result.getRuleResults().get(0).isSupported(), result.getRuleResults().get(0).getReason());
        assertTrue(result.getRuleResults().get(1).isSupported(), result.getRuleResults().get(1).getReason());
        assertEquals(OclRuleOwnerKind.CLASS, result.getRuleResults().get(0).getOwnerKind());
        assertEquals(OclRuleKind.INV, result.getRuleResults().get(0).getRuleKind());
        assertEquals("Adult", result.getRuleResults().get(0).getInvariantName());
        assertEquals("Named", result.getRuleResults().get(1).getInvariantName());
    }

    @Test
    void reportsDocumentDiagnosticForFreeTopLevelExpressionInFileMode() {
        String spec = """
                model Demo
                class Person
                attributes
                    age : Integer
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        OclFileCompilationResult result = compiler.compileFile("""
                context Person inv Adult: self.age >= 18
                1 = 1
                """);

        assertEquals(2, result.getRuleResults().size());
        assertEquals(1, result.getFreeExpressionCount());
        assertEquals(1, result.getDocumentDiagnostics().size());
        assertEquals(OclDiagnosticCode.UNSUPPORTED_AST_NODE, result.getDocumentDiagnostics().get(0).code());
        assertFalse(result.getRuleResults().get(1).isSupported());
        assertNull(result.getRuleResults().get(1).getContextClassName());
    }

    @Test
    void capturesCompileResponseTimingForDocumentMode() {
        String spec = """
                model Demo
                class Person
                attributes
                    age : Integer
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        OclFileCompilationResult result = compiler.compileFile("""
                context Person inv Adult: self.age >= 18
                context Person inv Senior: self.age >= 65
                """);

        assertEquals("document", result.getRequestScope());
        assertTrue(result.getResponseTimeMs() >= 0);
        assertTrue(result.getParseTimeMs() >= 0);
        assertTrue(result.getCompileTimeMs() >= 0);
        assertEquals(2, result.getRuleResults().size());
        assertTrue(result.getRuleResults().get(0).getCompilationTimeMs() >= 0);
        assertNotNull(result.getRuleResults().get(0).getResultLocation());
    }

    @Test
    void keepsParseDiagnosticLocationInCompileFileResponse() {
        String spec = """
                model Demo
                class Person
                attributes
                    age : Integer
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        OclFileCompilationResult result = compiler.compileFile(
                "context Person inv Broken: self.age >=");

        assertFalse(result.getDocumentDiagnostics().isEmpty());
        assertEquals(OclDiagnosticCode.PARSE_ERROR, result.getDocumentDiagnostics().get(0).code());
        assertTrue(result.getParseTimeMs() >= 0);
    }

    @Test
    void compilesOperationPreconditionAsParameterizedRuleResult() {
        String spec = """
                model Demo
                class BankAccount
                operations
                    withdraw(amount : Real)
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        OclFileCompilationResult result = compiler.compileFile("""
                context BankAccount::withdraw(amount: Real)
                pre:
                    amount > 0
                """);

        assertEquals(1, result.getRuleResults().size());
        assertEquals(OclRuleOwnerKind.OPERATION, result.getRuleResults().get(0).getOwnerKind());
        assertEquals(OclRuleKind.PRE, result.getRuleResults().get(0).getRuleKind());
        assertEquals("BankAccount", result.getRuleResults().get(0).getContextClassName());
        assertEquals("withdraw", result.getRuleResults().get(0).getOperationName());
        assertTrue(result.getRuleResults().get(0).isSupported(), result.getRuleResults().get(0).getReason());
        assertTrue(result.getRuleResults().get(0).getCypher().contains("$amount"));
        assertEquals(List.of("parameter:amount"), result.getRuleResults().get(0).getRequiredInputs());
    }

    @Test
    void compilesSimplePostconditionAndExposesRequiredInputs() {
        String spec = """
                model Demo
                class BankAccount
                operations
                    withdraw(amount : Real) : Real
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        OclFileCompilationResult result = compiler.compileFile("""
                context BankAccount::withdraw(amount: Real)
                post:
                    result > 0 and amount > 0
                """);

        assertEquals(1, result.getRuleResults().size());
        assertEquals(OclRuleKind.POST, result.getRuleResults().get(0).getRuleKind());
        assertTrue(result.getRuleResults().get(0).isSupported(), result.getRuleResults().get(0).getReason());
        assertEquals(List.of("parameter:amount", "resultValue"),
                result.getRuleResults().get(0).getRequiredInputs());
        assertTrue(result.getRuleResults().get(0).getCypher().contains("$result"));
        assertTrue(result.getRuleResults().get(0).getCypher().contains("$amount"));
    }

    @Test
    void compilesSimpleContextInvariant() {
        String spec = """
                model Demo
                class Person
                attributes
                    name : String
                    age : Integer
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        CypherCompilationResult result = compiler.compile("context Person inv Adult: self.age >= 18 and self.name <> ''");

        assertTrue(result.isSupported(), result.getReason());
        assertTrue(result.getCypher().contains("MATCH (self)-[:ObjectInstanceOf]->(cls"));
        assertTrue(result.getCypher().contains("self.use_id AS useId"));
        assertFalse(result.getParameters().isEmpty());
    }

    @Test
    void compilesIfExpression() {
        String spec = """
                model Demo
                class Person
                attributes
                    age : Integer
                    name : String
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        CypherCompilationResult result = compiler.compile(
                "context Person inv AdultNamed: if self.age >= 18 then self.name else 'minor' endif <> ''");

        assertTrue(result.isSupported(), result.getReason());
        assertTrue(result.getCypher().contains("CASE WHEN"));
        assertTrue(result.getCypher().contains("THEN"));
        assertTrue(result.getCypher().contains("ELSE"));
    }

    @Test
    void compilesIfExpressionWithVoidBranch() {
        String spec = """
                model Demo
                class Person
                attributes
                    age : Integer
                    name : String
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        CypherCompilationResult result = compiler.compile(
                "context Person inv NullableAdultLabel: (if self.age >= 18 then null else self.name endif).isUndefined()");

        assertTrue(result.isSupported(), result.getReason());
        assertTrue(result.getCypher().contains("CASE WHEN"));
    }

    @Test
    void compilesMethodCallOnIfWithoutParentheses() {
        String spec = """
                model Demo
                class Person
                attributes
                    age : Integer
                    name : String
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        CypherCompilationResult result = compiler.compile(
                "context Person inv IfCallNoParens: if self.age >= 18 then self.name else 'minor' endif.isDefined()");

        assertTrue(result.isSupported(), result.getReason());
        assertTrue(result.getCypher().contains("CASE WHEN"));
    }

    @Test
    void foldsLiteralIfBeforeRendering() {
        String spec = """
                model Demo
                class Person
                attributes
                    age : Integer
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        CypherCompilationResult result = compiler.compile(
                "context Person inv AdultLiteralIf: if true then self.age >= 18 else false endif");

        assertTrue(result.isSupported(), result.getReason());
        assertFalse(result.getCypher().contains("CASE WHEN"));
    }

    @Test
    void rejectsNullIfConditionAtSemanticPhase() {
        String spec = """
                model Demo
                class Person
                attributes
                    name : String
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        CypherCompilationResult result = compiler.compile(
                "context Person inv NullIf: if null then self.name = 'adult' else true endif");

        assertFalse(result.isSupported());
        assertEquals("SEMANTIC", result.getDiagnostic().phase().name());
        assertEquals(OclDiagnosticCode.INVALID_IF_CONDITION, result.getDiagnostic().code());
    }

    @Test
    void compilesLetExpression() {
        String spec = """
                model Demo
                class Person
                attributes
                    age : Integer
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        CypherCompilationResult result = compiler.compile(
                "context Person inv AdultByLet: let threshold = 18 in self.age >= threshold");

        assertTrue(result.isSupported(), result.getReason());
        assertFalse(result.getCypher().contains("threshold"));
        assertTrue(result.getParameters().containsValue(18L));
    }

    @Test
    void compilesNullLetExpression() {
        String spec = """
                model Demo
                class Person
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        CypherCompilationResult result = compiler.compile(
                "context Person inv NullLet: let fallback = null in fallback.isUndefined()");

        assertTrue(result.isSupported(), result.getReason());
        assertFalse(result.getCypher().contains("fallback"));
    }

    @Test
    void compilesNestedLetExpression() {
        String spec = """
                model Demo
                class Person
                attributes
                    age : Integer
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        CypherCompilationResult result = compiler.compile(
                "context Person inv NestedLet: let threshold = 18 in let bonus = threshold + 1 in self.age >= bonus");

        assertTrue(result.isSupported(), result.getReason());
        assertFalse(result.getCypher().contains("threshold"));
        assertFalse(result.getCypher().contains("bonus"));
        assertTrue(result.getParameters().containsValue(19L));
        assertFalse(result.getParameters().containsValue(18L));
        assertFalse(result.getParameters().containsValue(1L));
    }

    @Test
    void foldsNestedLetArithmeticBeforeRendering() {
        String spec = """
                model Demo
                class Person
                attributes
                    age : Integer
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        CypherCompilationResult result = compiler.compile(
                "context Person inv NestedLet: let threshold = 18 in let bonus = threshold + 1 in self.age >= bonus");

        assertTrue(result.isSupported(), result.getReason());
        assertTrue(result.getParameters().containsValue(19L));
        assertFalse(result.getParameters().containsValue(18L));
        assertFalse(result.getParameters().containsValue(1L));
    }

    @Test
    void keepsReusedLetAsSingleReduceBindingInCypher() {
        String spec = """
                model Demo
                class Person
                attributes
                    age : Integer
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        CypherCompilationResult result = compiler.compile(
                "context Person inv ReusedLet: let threshold = self.age + 1 in threshold >= 18 and threshold <= 65");

        assertTrue(result.isSupported(), result.getReason());
        assertTrue(result.getCypher().contains("reduce("));
        assertFalse(result.getCypher().contains("threshold"));
    }

    @Test
    void compilesIteratorOverNavigation() {
        String spec = """
                model Demo
                class Family
                end
                class Person
                attributes
                    age : Integer
                end
                association FamilyChildren between
                    Family[*] role family
                    Person[*] role children
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        CypherCompilationResult result = compiler.compile("context Family inv AdultChildren: self.children->forall(c | c.age >= 18)");

        assertTrue(result.isSupported(), result.getReason());
        assertTrue(result.getCypher().contains("NOT EXISTS { MATCH (self)-[r]->(c)"));
        assertTrue(result.getCypher().contains("r.name = $"));
    }

    @Test
    void compilesExistsOverNavigationAsExistsSubquery() {
        String spec = """
                model Demo
                class Family
                end
                class Person
                attributes
                    age : Integer
                end
                association FamilyChildren between
                    Family[*] role family
                    Person[*] role children
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        CypherCompilationResult result = compiler.compile("context Family inv HasAdultChild: self.children->exists(c | c.age >= 18)");

        assertTrue(result.isSupported(), result.getReason());
        assertTrue(result.getCypher().contains("EXISTS { MATCH (self)-[r]->(c)"));
        assertTrue(result.getCypher().contains("c.age") || result.getCypher().contains("ObjectHasAttribute"));
    }

    @Test
    void compilesNavigationNotEmptyAsExistsSubquery() {
        String spec = """
                model Demo
                class Family
                end
                class Person
                end
                association FamilyChildren between
                    Family[1] role family
                    Person[*] role children
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        CypherCompilationResult result = compiler.compile("context Family inv HasChildren: self.children->notEmpty()");

        assertTrue(result.isSupported(), result.getReason());
        assertTrue(result.getCypher().contains("EXISTS { MATCH (self)-[r]->(nav)"));
    }

    @Test
    void compilesCollectionPropertyProjectionShorthand() {
        String spec = """
                model Demo
                class Family
                end
                class Person
                attributes
                    name : String
                end
                association FamilyChildren between
                    Family[1] role family
                    Person[*] role children
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        CypherCompilationResult result = compiler.compile("context Family inv HasBart: self.children.name->includes('Bart')");

        assertTrue(result.isSupported(), result.getReason());
        assertTrue(result.getCypher().contains(" IN ["));
        assertTrue(result.getCypher().contains("ObjectHasAttribute"));
        assertTrue(result.getCypher().contains("WHERE "));
    }

    @Test
    void compilesSelectedNavigationNotEmptyAsExistsSubquery() {
        String spec = """
                model Demo
                class Family
                end
                class Person
                attributes
                    age : Integer
                end
                association FamilyChildren between
                    Family[*] role family
                    Person[*] role children
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        CypherCompilationResult result = compiler.compile(
                "context Family inv HasAdultChild: self.children->select(c | c.age >= 18)->notEmpty()");

        assertTrue(result.isSupported(), result.getReason());
        assertTrue(result.getCypher().contains("EXISTS { MATCH (self)-[r]->(c)"));
        assertTrue(result.getCypher().contains("ObjectHasAttribute"));
    }

    @Test
    void compilesNavigationSizeGreaterThanZeroAsExistsSubquery() {
        String spec = """
                model Demo
                class Family
                end
                class Person
                end
                association FamilyChildren between
                    Family[1] role family
                    Person[*] role children
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        CypherCompilationResult result = compiler.compile("context Family inv HasChildren: self.children->size() > 0");

        assertTrue(result.isSupported(), result.getReason());
        assertTrue(result.getCypher().contains("EXISTS { MATCH (self)-[r]->(nav)"));
    }

    @Test
    void compilesNavigationSizeEqualsZeroAsNotExistsSubquery() {
        String spec = """
                model Demo
                class Family
                end
                class Person
                end
                association FamilyChildren between
                    Family[*] role family
                    Person[*] role children
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        CypherCompilationResult result = compiler.compile("context Family inv NoChildren: self.children->size() = 0");

        assertTrue(result.isSupported(), result.getReason());
        assertTrue(result.getCypher().contains("NOT EXISTS { MATCH (self)-[r]->(nav)"));
    }

    @Test
    void compilesSelectedNavigationSizeComparisonUsingCountSubquery() {
        String spec = """
                model Demo
                class Family
                end
                class Person
                attributes
                    age : Integer
                end
                association FamilyChildren between
                    Family[*] role family
                    Person[*] role children
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        CypherCompilationResult result = compiler.compile(
                "context Family inv TwoAdults: self.children->select(c | c.age >= 18)->size() >= 2");

        assertTrue(result.isSupported(), result.getReason());
        assertTrue(result.getCypher().contains("COUNT { MATCH (self)-[r]->(c)"));
        assertTrue(result.getCypher().contains("ObjectHasAttribute"));
    }

    @Test
    void compilesReverseNavigationUsingIncomingDirection() {
        String spec = """
                model Demo
                class Family
                attributes
                    name : String
                end
                class Person
                end
                association FamilyChildren between
                    Family[*] role family
                    Person[*] role children
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        CypherCompilationResult result = compiler.compile("context Person inv HasFamily: self.family->notEmpty()");

        assertTrue(result.isSupported(), result.getReason());
        assertTrue(result.getCypher().contains("(self)<-[r]-(nav)"));
        assertTrue(result.getCypher().contains("r.sourceRole = $"));
        assertTrue(result.getCypher().contains("r.targetRole = $"));
    }

    @Test
    void compilesSelfAssociationRolesWithDistinctDirections() {
        String spec = """
                model Demo
                class Person
                end
                association Parenthood between
                    Person[*] role parent
                    Person[*] role child
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        CypherCompilationResult child = compiler.compile("context Person inv HasChild: self.child->notEmpty()");
        CypherCompilationResult parent = compiler.compile("context Person inv HasParent: self.parent->notEmpty()");

        assertTrue(child.isSupported(), child.getReason());
        assertTrue(parent.isSupported(), parent.getReason());
        assertTrue(child.getCypher().contains("(self)-[r]->(nav)"));
        assertTrue(parent.getCypher().contains("(self)<-[r]-(nav)"));
        assertTrue(child.getParameters().containsValue("parent"));
        assertTrue(child.getParameters().containsValue("child"));
        assertTrue(parent.getParameters().containsValue("parent"));
        assertTrue(parent.getParameters().containsValue("child"));
    }

    @Test
    void compilesDifferentAssociationsBetweenSameClassesWithoutMixingMetadata() {
        String spec = """
                model Demo
                class Company
                end
                class Person
                attributes
                    age : Integer
                end
                association CompanyEmployee between
                    Company[*] role employer
                    Person[*] role employee
                end
                association CompanyManager between
                    Company[*] role managedCompany
                    Person[1] role manager
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        CypherCompilationResult employees = compiler.compile(
                "context Company inv HasAdultEmployee: self.employee->exists(e | e.age >= 18)");
        CypherCompilationResult manager = compiler.compile(
                "context Company inv HasManager: self.manager->notEmpty()");

        assertTrue(employees.isSupported(), employees.getReason());
        assertTrue(manager.isSupported(), manager.getReason());
        assertTrue(employees.getParameters().containsValue("CompanyEmployee"));
        assertFalse(employees.getParameters().containsValue("CompanyManager"));
        assertTrue(manager.getParameters().containsValue("CompanyManager"));
        assertFalse(manager.getParameters().containsValue("CompanyEmployee"));
    }

    @Test
    void compilesInheritedAttributeAndNavigationFromSubclassContext() {
        String spec = """
                model Demo
                class Person
                attributes
                    age : Integer
                end
                class Employee < Person
                end
                class Company
                end
                association CompanyStaff between
                    Company[*] role employer
                    Person[*] role staff
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        CypherCompilationResult result = compiler.compile(
                "context Employee inv AdultWithEmployer: self.age >= 18 and self.employer->notEmpty()");

        assertTrue(result.isSupported(), result.getReason());
        assertTrue(result.getCypher().contains("ObjectHasAttribute"));
        assertTrue(result.getCypher().contains("(self)<-[r]-(nav)"));
        assertTrue(result.getParameters().containsValue("CompanyStaff"));
        assertTrue(result.getParameters().containsValue("Employee"));
    }

    @Test
    void compilesCollectionIncludesAndExcludes() {
        String spec = """
                model Demo
                class Family
                attributes
                    name : String
                end
                class Person
                end
                association FamilyChildren between
                    Family[*] role family
                    Person[*] role children
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        CypherCompilationResult includes = compiler.compile(
                "context Family inv HasComma: self.name.split(',')->includes('Simpson')");
        CypherCompilationResult excludes = compiler.compile(
                "context Family inv SplitExcludesFirst: self.name.split(',')->excludes(self.name.split(',')->at(1))");

        assertTrue(includes.isSupported(), includes.getReason());
        assertTrue(includes.getCypher().contains("any("));
        assertTrue(excludes.isSupported(), excludes.getReason());
        assertTrue(excludes.getCypher().contains("none("));
    }

    @Test
    void compilesCollectionIncludesAllAndExcludesAll() {
        String spec = """
                model Demo
                class Family
                attributes
                    name : String
                end
                class Person
                end
                association FamilyChildren between
                    Family[*] role family
                    Person[*] role children
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        CypherCompilationResult includesAll = compiler.compile(
                "context Family inv HasBothNames: self.name.split(',')->includesAll(self.name.split(','))");
        CypherCompilationResult excludesAll = compiler.compile(
                "context Family inv NoSemicolonNames: self.name.split(',')->excludesAll(self.name.split(';'))");

        assertTrue(includesAll.isSupported(), includesAll.getReason());
        assertTrue(includesAll.getCypher().contains("all("));
        assertTrue(includesAll.getCypher().contains("any("));
        assertTrue(excludesAll.isSupported(), excludesAll.getReason());
        assertTrue(excludesAll.getCypher().contains("none("));
        assertTrue(excludesAll.getCypher().contains("any("));
    }

    @Test
    void compilesIteratorAnyAndOne() {
        String spec = """
                model Demo
                class Family
                end
                class Person
                attributes
                    age : Integer
                    name : String
                end
                association FamilyChildren between
                    Family[*] role family
                    Person[*] role children
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        CypherCompilationResult any = compiler.compile(
                "context Family inv HasNamedChild: self.children->any(c | c.name = 'Bart').isDefined()");
        CypherCompilationResult one = compiler.compile(
                "context Family inv ExactlyOneAdult: self.children->one(c | c.age >= 18)");

        assertTrue(any.isSupported(), any.getReason());
        assertTrue(any.getCypher().contains("head(["));
        assertTrue(one.isSupported(), one.getReason());
        assertTrue(one.getCypher().contains("single("));
    }

    @Test
    void compilesCollectionFirstAndLast() {
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
                association FamilyChildren between
                    Family[*] role family
                    Person[*] role children
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        CypherCompilationResult first = compiler.compile(
                "context Family inv FirstNameDefined: self.name.split(',')->first().isDefined()");
        CypherCompilationResult last = compiler.compile(
                "context Family inv LastNameDefined: self.name.split(',')->last().isDefined()");

        assertTrue(first.isSupported(), first.getReason());
        assertTrue(first.getCypher().contains("head("));
        assertTrue(last.isSupported(), last.getReason());
        assertTrue(last.getCypher().contains("[size("));
    }

    @Test
    void compilesPositionalAccessOnOrderedNavigationCollections() {
        String spec = """
                model Demo
                class Invoice
                end
                class LineItem
                attributes
                    price : Integer
                end
                association InvoiceLineItems between
                    Invoice[1] role invoice
                    LineItem[*] role lineItem ordered
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        CypherCompilationResult first = compiler.compile(
                "context Invoice inv FirstLineItemPriceDefined: self.lineItem->first().price.isDefined()");
        CypherCompilationResult last = compiler.compile(
                "context Invoice inv LastLineItemPriceDefined: self.lineItem->last().price.isDefined()");

        assertTrue(first.isSupported(), first.getReason());
        assertTrue(last.isSupported(), last.getReason());
        assertTrue(first.getCypher().contains("head(["));
        assertTrue(last.getCypher().contains("[size("));
    }

    @Test
    void compilesCollectionUnionAndIntersection() {
        String spec = """
                model Demo
                class Family
                attributes
                    name : String
                end
                class Person
                attributes
                    age : Integer
                end
                association FamilyChildren between
                    Family[*] role family
                    Person[*] role children
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        CypherCompilationResult union = compiler.compile(
                "context Family inv UnionNamesHasBart: self.name.split(',')->union(self.name.split(';'))->includes('Bart')");
        CypherCompilationResult uniqueUnion = compiler.compile(
                "context Family inv UniqueUnionHasBart: self.name.split(',')->asSet()->union(self.name.split(';')->asSet())->includes('Bart')");
        CypherCompilationResult intersection = compiler.compile(
                "context Family inv AdultIntersectionNotEmpty: self.children->intersection(self.children->select(c | c.age >= 18))->notEmpty()");
        CypherCompilationResult bagIntersection = compiler.compile(
                "context Family inv SharedAgesNotEmpty: self.children->collect(c | c.age)->intersection(self.children->collect(c | c.age))->notEmpty()");

        assertTrue(union.isSupported(), union.getReason());
        assertTrue(union.getCypher().contains(" + "));
        assertFalse(union.getCypher().contains("CASE WHEN any(existing"));
        assertTrue(uniqueUnion.isSupported(), uniqueUnion.getReason());
        assertTrue(uniqueUnion.getCypher().contains("CASE WHEN any(existing"));
        assertTrue(intersection.isSupported(), intersection.getReason());
        assertTrue(intersection.getCypher().contains(" IN "));
        assertTrue(intersection.getCypher().contains("any("));
        assertTrue(intersection.getCypher().contains("CASE WHEN any(existing"));
        assertTrue(bagIntersection.isSupported(), bagIntersection.getReason());
        assertTrue(bagIntersection.getCypher().contains("any("));
        assertFalse(bagIntersection.getCypher().contains("CASE WHEN any(existing"));
    }

    @Test
    void compilesOrderedSetUnionAndIntersectionWithPositionalAccess() {
        String spec = """
                model Demo
                class Invoice
                end
                class LineItem
                attributes
                    price : Integer
                end
                association InvoiceLineItems between
                    Invoice[1] role invoice
                    LineItem[*] role lineItem ordered
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        CypherCompilationResult union = compiler.compile(
                "context Invoice inv OrderedUnionFirstDefined: self.lineItem->union(self.lineItem)->first().price.isDefined()");
        CypherCompilationResult intersection = compiler.compile(
                "context Invoice inv OrderedIntersectionLastDefined: self.lineItem->intersection(self.lineItem)->last().price.isDefined()");

        assertTrue(union.isSupported(), union.getReason());
        assertTrue(intersection.isSupported(), intersection.getReason());
        assertTrue(union.getCypher().contains("CASE WHEN any(existing"));
        assertTrue(union.getCypher().contains("head("));
        assertTrue(intersection.getCypher().contains("CASE WHEN any(existing"));
        assertTrue(intersection.getCypher().contains("[size("));
    }

    @Test
    void compilesCollectionCount() {
        String spec = """
                model Demo
                class Family
                attributes
                    name : String
                end
                class Person
                attributes
                    age : Integer
                end
                association FamilyChildren between
                    Family[*] role family
                    Person[*] role children
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        CypherCompilationResult scalarCount = compiler.compile(
                "context Family inv TwoBarts: self.name.split(',')->count('Bart') = 2");
        CypherCompilationResult nodeCount = compiler.compile(
                "context Family inv AnyAdultCounted: self.children->count(self.children->any(c | c.age >= 18)) >= 1");

        assertTrue(scalarCount.isSupported(), scalarCount.getReason());
        assertTrue(scalarCount.getCypher().contains("size(["));
        assertTrue(nodeCount.isSupported(), nodeCount.getReason());
        assertTrue(nodeCount.getCypher().contains("size(["));
    }

    @Test
    void rejectsPositionalAccessOnUnorderedCollections() {
        String spec = """
                model Demo
                class Family
                end
                class Person
                attributes
                    age : Integer
                    name : String
                end
                association FamilyChildren between
                    Family[*] role family
                    Person[*] role children
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        CypherCompilationResult last = compiler.compile(
                "context Family inv LastChildNamed: self.children->last().name.isDefined()");
        CypherCompilationResult at = compiler.compile(
                "context Family inv FirstChildExists: self.children->at(1).isDefined()");

        assertFalse(last.isSupported());
        assertFalse(at.isSupported());
        assertNotNull(last.getDiagnostic());
        assertNotNull(at.getDiagnostic());
        assertEquals("SEMANTIC", last.getDiagnostic().phase().name());
        assertEquals("SEMANTIC", at.getDiagnostic().phase().name());
        assertEquals(OclDiagnosticCode.UNORDERED_POSITIONAL_ACCESS, last.getDiagnostic().code());
        assertEquals(OclDiagnosticCode.UNORDERED_POSITIONAL_ACCESS, at.getDiagnostic().code());
        assertEquals(2, last.getDiagnostics().size());
        assertEquals(2, at.getDiagnostics().size());
        assertTrue(last.getDiagnostics().get(1).message().contains("Hint:"));
        assertTrue(at.getDiagnostics().get(1).message().contains("Hint:"));
        assertTrue(last.getReason().contains("ordered collections"));
        assertTrue(at.getReason().contains("ordered collections"));
    }

    @Test
    void compilesCollectionAsSet() {
        String spec = """
                model Demo
                class Family
                attributes
                    name : String
                end
                class Person
                end
                association FamilyChildren between
                    Family[*] role family
                    Person[*] role children
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        CypherCompilationResult scalarSet = compiler.compile(
                "context Family inv UniqueSplitNames: self.name.split(',')->asSet()->size() >= 1");
        CypherCompilationResult nodeSet = compiler.compile(
                "context Family inv UniqueChildren: self.children->asSet()->notEmpty()");

        assertTrue(scalarSet.isSupported(), scalarSet.getReason());
        assertTrue(scalarSet.getCypher().contains("reduce("));
        assertTrue(nodeSet.isSupported(), nodeSet.getReason());
        assertTrue(nodeSet.getCypher().contains("reduce("));
    }

    @Test
    void compilesCollectionAsOrderedSet() {
        String spec = """
                model Demo
                class Family
                attributes
                    name : String
                end
                """; 

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        CypherCompilationResult orderedSet = compiler.compile(
                "context Family inv OrderedUniqueFirstDefined: self.name.split(',')->asOrderedSet()->first().isDefined()");

        assertTrue(orderedSet.isSupported(), orderedSet.getReason());
        assertTrue(orderedSet.getCypher().contains("reduce("));
        assertTrue(orderedSet.getCypher().contains("head("));
    }

    @Test
    void compilesCollectionFlatten() {
        String spec = """
                model Demo
                class Family
                attributes
                    aliases : String
                end
                class Person
                attributes
                    nicknames : String
                end
                association FamilyChildren between
                    Family[*] role family
                    Person[*] role children
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        CypherCompilationResult scalarFlatten = compiler.compile(
                "context Family inv FlattenedAliases: self.aliases.split(';')->collect(a | a.split(','))->flatten()->count('Bart') >= 1");
        CypherCompilationResult uniqueFlatten = compiler.compile(
                "context Family inv UniqueFlattenedAliases: self.aliases.split(';')->collect(a | a.split(','))->asSet()->flatten()->count('Bart') >= 1");
        CypherCompilationResult nodeFlatten = compiler.compile(
                "context Family inv FlattenedFamilies: self.children->collect(c | c.family)->flatten()->includes(self)");

        assertTrue(scalarFlatten.isSupported(), scalarFlatten.getReason());
        assertTrue(scalarFlatten.getCypher().contains("reduce("));
        assertFalse(scalarFlatten.getCypher().contains("CASE WHEN any(existing"));
        assertTrue(uniqueFlatten.isSupported(), uniqueFlatten.getReason());
        assertTrue(uniqueFlatten.getCypher().contains("CASE WHEN any(existing"));
        assertTrue(nodeFlatten.isSupported(), nodeFlatten.getReason());
        assertTrue(nodeFlatten.getCypher().contains("reduce("));
    }

    @Test
    void compilesOrderedSetFlattenWithPositionalAccess() {
        String spec = """
                model Demo
                class Family
                attributes
                    aliases : String
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        CypherCompilationResult orderedFlatten = compiler.compile(
                "context Family inv FlatOrderedAliases: self.aliases.split(';')->collect(a | a.split(','))->asOrderedSet()->flatten()->first().isDefined()");

        assertTrue(orderedFlatten.isSupported(), orderedFlatten.getReason());
        assertTrue(orderedFlatten.getCypher().contains("reduce("));
        assertTrue(orderedFlatten.getCypher().contains("CASE WHEN any(existing"));
        assertTrue(orderedFlatten.getCypher().contains("head("));
    }

    @Test
    void rejectsNavigationOverNAryAssociation() {
        String spec = """
                model Demo
                class Person
                end
                class Company
                end
                class Animal
                end
                association Buy between
                    Person[0..1] role buyer
                    Company[0..1] role seller
                    Animal[*] role pet
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        CypherCompilationResult result = compiler.compile(
                "context Person inv HasPets: self.pet->notEmpty()");

        assertFalse(result.isSupported());
        assertTrue(result.getReason().startsWith("SEMANTIC:"));
        assertNotNull(result.getDiagnostic());
        assertEquals(2, result.getDiagnostics().size());
        assertEquals("SEMANTIC", result.getDiagnostic().phase().name());
        assertEquals(OclDiagnosticCode.NON_BINARY_ASSOCIATION_UNSUPPORTED, result.getDiagnostic().code());
        assertTrue(result.getReason().contains("non-binary associations"));
        assertTrue(result.getDiagnostics().get(1).message().contains("Hint:"));
    }

    @Test
    void rejectsNavigationOverQualifiedAssociation() {
        String spec = """
                model Demo
                class Library
                end
                class Book
                end
                association Catalog between
                    Library[1] role library
                    Book[*] role book qualifier (shelf : String)
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        CypherCompilationResult result = compiler.compile(
                "context Library inv HasBooks: self.book->notEmpty()");

        assertFalse(result.isSupported());
        assertNotNull(result.getDiagnostic());
        assertEquals(2, result.getDiagnostics().size());
        assertEquals("SEMANTIC", result.getDiagnostic().phase().name());
        assertEquals(OclDiagnosticCode.QUALIFIED_ASSOCIATION_UNSUPPORTED, result.getDiagnostic().code());
        assertTrue(result.getReason().contains("qualified associations"));
        assertTrue(result.getDiagnostics().get(1).message().contains("Hint:"));
    }

    @Test
    void rejectsNavigationOverRedefiningAssociation() {
        String spec = """
                model Demo
                class Person
                end
                class Employee < Person
                end
                class Company
                end
                class Startup < Company
                end
                association WorksFor between
                    Person[*] role employee
                    Company[0..1] role employer
                end
                association StartupWorksFor between
                    Employee[*] role startupEmployee redefines employee
                    Startup[0..1] role startupEmployer redefines employer
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        CypherCompilationResult result = compiler.compile(
                "context Employee inv HasStartupEmployer: self.startupEmployer->notEmpty()");

        assertFalse(result.isSupported());
        assertNotNull(result.getDiagnostic());
        assertEquals(2, result.getDiagnostics().size());
        assertEquals("SEMANTIC", result.getDiagnostic().phase().name());
        assertEquals(OclDiagnosticCode.REDEFINING_ASSOCIATION_UNSUPPORTED, result.getDiagnostic().code());
        assertTrue(result.getReason().contains("redefining associations"));
        assertTrue(result.getDiagnostics().get(1).message().contains("Hint:"));
    }

    @Test
    void rejectsUnknownAttributeDuringBinding() {
        String spec = """
                model Demo
                class Person
                attributes
                    age : Integer
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        CypherCompilationResult result = compiler.compile("context Person inv Bad: self.missing = 1");

        assertFalse(result.isSupported());
        assertTrue(result.getReason().startsWith("SEMANTIC:"));
        assertNotNull(result.getDiagnostic());
        assertEquals(2, result.getDiagnostics().size());
        assertEquals("SEMANTIC", result.getDiagnostic().phase().name());
        assertEquals(OclDiagnosticCode.UNKNOWN_PROPERTY, result.getDiagnostic().code());
        assertTrue(result.getReason().contains("Unknown property or navigation"));
        assertTrue(result.getDiagnostics().get(1).message().contains("Hint:"));
    }

    @Test
    void rejectsUnknownContextClassWithStructuredDiagnostic() {
        String spec = """
                model Demo
                class Person
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        CypherCompilationResult result = compiler.compile("context Missing inv Bad: true");

        assertFalse(result.isSupported());
        assertEquals("SEMANTIC", result.getDiagnostic().phase().name());
        assertEquals(OclDiagnosticCode.UNKNOWN_CONTEXT_CLASS, result.getDiagnostic().code());
        assertEquals(2, result.getDiagnostics().size());
        assertTrue(result.getDiagnostics().get(1).message().contains("context class name"));
    }

    @Test
    void rejectsUnsupportedFreeVariableWithStructuredDiagnostic() {
        String spec = """
                model Demo
                class Person
                attributes
                    age : Integer
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        CypherCompilationResult result = compiler.compile("context Person inv BadVar: threshold > self.age");

        assertFalse(result.isSupported());
        assertEquals("SEMANTIC", result.getDiagnostic().phase().name());
        assertEquals(OclDiagnosticCode.UNSUPPORTED_FREE_VARIABLE, result.getDiagnostic().code());
        assertEquals(2, result.getDiagnostics().size());
        assertTrue(result.getDiagnostics().get(1).message().contains("bind this variable through `let`"));
    }

    @Test
    void rejectsIfConditionThatIsNotBoolean() {
        String spec = """
                model Demo
                class Person
                attributes
                    age : Integer
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        CypherCompilationResult result = compiler.compile(
                "context Person inv BadIf: if self.age then true else false endif");

        assertFalse(result.isSupported());
        assertEquals("SEMANTIC", result.getDiagnostic().phase().name());
        assertEquals(OclDiagnosticCode.INVALID_IF_CONDITION, result.getDiagnostic().code());
        assertEquals(2, result.getDiagnostics().size());
        assertTrue(result.getDiagnostics().get(1).message().contains("condition of `if` must be Boolean"));
    }

    @Test
    void rejectsIteratorOnScalarSourceWithStructuredDiagnostic() {
        String spec = """
                model Demo
                class Person
                attributes
                    age : Integer
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        CypherCompilationResult result = compiler.compile(
                "context Person inv BadIterator: self.age->exists(x | x > 0)");

        assertFalse(result.isSupported());
        assertEquals("SEMANTIC", result.getDiagnostic().phase().name());
        assertEquals(OclDiagnosticCode.INVALID_ITERATOR_SOURCE, result.getDiagnostic().code());
        assertEquals(2, result.getDiagnostics().size());
        assertTrue(result.getDiagnostics().get(1).message().contains("iterators require a collection source"));
    }

    @Test
    void rejectsInvalidMethodReceiverForAllInstances() {
        String spec = """
                model Demo
                class Person
                attributes
                    age : Integer
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        CypherCompilationResult result = compiler.compile(
                "context Person inv BadReceiver: self.age.allInstances()->notEmpty()");

        assertFalse(result.isSupported());
        assertEquals("SEMANTIC", result.getDiagnostic().phase().name());
        assertEquals(OclDiagnosticCode.INVALID_METHOD_RECEIVER, result.getDiagnostic().code());
        assertEquals(2, result.getDiagnostics().size());
        assertTrue(result.getDiagnostics().get(1).message().contains("class name"));
    }

    @Test
    void rejectsInvalidCollectionArgumentForUnion() {
        String spec = """
                model Demo
                class Family
                attributes
                    name : String
                end
                class Person
                end
                association FamilyChildren between
                    Family[*] role family
                    Person[*] role children
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        CypherCompilationResult result = compiler.compile(
                "context Family inv BadUnion: self.children->union(self.name.split(',')->first())->notEmpty()");

        assertFalse(result.isSupported());
        assertEquals("SEMANTIC", result.getDiagnostic().phase().name());
        assertEquals(OclDiagnosticCode.INVALID_COLLECTION_ARGUMENT, result.getDiagnostic().code());
        assertEquals(2, result.getDiagnostics().size());
        assertTrue(result.getDiagnostics().get(1).message().contains("one collection argument"));
    }

    @Test
    void rejectsInvalidCollectionSourceForFirst() {
        String spec = """
                model Demo
                class Person
                attributes
                    age : Integer
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        CypherCompilationResult result = compiler.compile("context Person inv BadFirst: self.age->first() = 1");

        assertFalse(result.isSupported());
        assertEquals("SEMANTIC", result.getDiagnostic().phase().name());
        assertEquals(OclDiagnosticCode.INVALID_COLLECTION_SOURCE, result.getDiagnostic().code());
        assertEquals(2, result.getDiagnostics().size());
        assertTrue(result.getDiagnostics().get(1).message().contains("requires a collection source"));
    }

    @Test
    void rejectsIteratorTypeMismatchWithStructuredDiagnostic() {
        String spec = """
                model Demo
                class Family
                attributes
                    name : String
                end
                class Person
                end
                association FamilyChildren between
                    Family[*] role family
                    Person[*] role children
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        CypherCompilationResult result = compiler.compile(
                "context Family inv BadIteratorType: self.children->exists(c:String | true)");

        assertFalse(result.isSupported());
        assertEquals("SEMANTIC", result.getDiagnostic().phase().name());
        assertEquals(OclDiagnosticCode.ITERATOR_TYPE_MISMATCH, result.getDiagnostic().code());
        assertEquals(2, result.getDiagnostics().size());
        assertTrue(result.getDiagnostics().get(1).message().contains("iterator type annotation"));
    }

    @Test
    void rejectsIfBranchesWithIncompatibleTypes() {
        String spec = """
                model Demo
                class Person
                attributes
                    age : Integer
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        CypherCompilationResult result = compiler.compile(
                "context Person inv BadIfTypes: if self.age >= 18 then self.age else 'minor' endif = self.age");

        assertFalse(result.isSupported());
        assertEquals("SEMANTIC", result.getDiagnostic().phase().name());
        assertEquals(OclDiagnosticCode.INCOMPATIBLE_IF_BRANCH_TYPES, result.getDiagnostic().code());
        assertEquals(2, result.getDiagnostics().size());
        assertTrue(result.getDiagnostics().get(1).message().contains("both `if` branches"));
    }

    @Test
    void rejectsCollectionValuedAttributeDuringBinding() {
        String spec = """
                model Demo
                class Person
                attributes
                    aliases : Sequence(String)
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        CypherCompilationResult result = compiler.compile("context Person inv HasAliases: self.aliases->notEmpty()");

        assertFalse(result.isSupported());
        assertNotNull(result.getDiagnostic());
        assertEquals(2, result.getDiagnostics().size());
        assertEquals("SEMANTIC", result.getDiagnostic().phase().name());
        assertEquals(OclDiagnosticCode.COLLECTION_VALUED_ATTRIBUTE_UNSUPPORTED, result.getDiagnostic().code());
        assertTrue(result.getReason().contains("Collection-valued attributes"));
        assertTrue(result.getDiagnostics().get(1).message().contains("Hint:"));
    }

    @Test
    void reportsParseDiagnosticsForMalformedInput() {
        String spec = """
                model Demo
                class Person
                attributes
                    age : Integer
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        CypherCompilationResult result = compiler.compile("context Person inv Bad: self.age >");

        assertFalse(result.isSupported());
        assertNotNull(result.getDiagnostic());
        assertEquals(2, result.getDiagnostics().size());
        assertEquals("PARSE", result.getDiagnostic().phase().name());
        assertNotNull(result.getDiagnostic().line());
        assertNotNull(result.getDiagnostic().column());
        assertNotNull(result.getDiagnostic().endLine());
        assertNotNull(result.getDiagnostic().endColumn());
        assertNotNull(result.getDiagnostic().tokenText());
        assertNotNull(result.getDiagnostic().sourceSnippet());
        assertTrue(result.getDiagnostic().sourceSnippet().contains("self.age >"));
        assertEquals(result.getDiagnostic(), result.getDiagnostics().get(0));
        assertEquals(OclDiagnosticCode.PARSE_ERROR, result.getDiagnostic().code());
        assertEquals(result.getDiagnostic().tokenText(), result.getDiagnostics().get(1).tokenText());
        assertEquals(result.getDiagnostic().sourceSnippet(), result.getDiagnostics().get(1).sourceSnippet());
        assertTrue(result.getDiagnostics().get(1).message().contains("Hint:"));
        assertTrue(result.getDiagnostic().endColumn() >= result.getDiagnostic().column());
        assertTrue(result.getReason().startsWith("PARSE:"));
    }

    @Test
    void doesNotWriteMalformedInputDiagnosticsToSystemErr() {
        String spec = """
                model Demo
                class Person
                attributes
                    age : Integer
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        PrintStream originalErr = System.err;
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();
        try {
            System.setErr(new PrintStream(errBuffer, true, StandardCharsets.UTF_8));
            CypherCompilationResult result = compiler.compile("context Person inv Bad: self.age >");

            assertFalse(result.isSupported());
        } finally {
            System.setErr(originalErr);
        }

        assertEquals("", errBuffer.toString(StandardCharsets.UTF_8));
    }

    @Test
    void reportsSpecificParseHintForMethodCallOnIfWithoutParentheses() {
        String spec = """
                model Demo
                class Person
                attributes
                    age : Integer
                    name : String
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        CypherCompilationResult result = compiler.compile(
                "context Person inv BadIfCall: if self.age >= 18 then self.name else 'minor' endif.isDefined(");

        assertFalse(result.isSupported());
        assertEquals("PARSE", result.getDiagnostic().phase().name());
        assertEquals(OclDiagnosticCode.PARSE_ERROR, result.getDiagnostic().code());
        assertTrue(result.getDiagnostics().get(1).message().contains("incomplete expressions")
                || result.getDiagnostics().get(1).message().contains("wrap `if ... then ... else ... endif` in parentheses"));
    }

    @Test
    void reportsSemanticDiagnosticsFromCodedBinderExceptionWithoutMessageMatching() {
        String spec = """
                model Demo
                class Person
                attributes
                    age : Integer
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        OclMetamodelIndex index = new OclMetamodelIndex(model);
        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(
                index,
                new OclSemanticBinder(index) {
                    @Override
                    public BoundContextInvariant bindContext(org.uet.dse.neo4j.oclite.ast.ASTContext context) {
                        throw new OclCodedUnsupportedOperationException(
                                OclDiagnosticCode.UNSUPPORTED_METHOD_CALL,
                                "Synthetic semantic gap for custom method.");
                    }
                },
                new OclIrBuilder(),
                new OclIrOptimizer(),
                new OclCypherPlanner(),
                new OclCypherRenderer());

        CypherCompilationResult result = compiler.compile("context Person inv Adult: self.age >= 18");

        assertFalse(result.isSupported());
        assertEquals("SEMANTIC", result.getDiagnostic().phase().name());
        assertEquals(OclDiagnosticCode.UNSUPPORTED_METHOD_CALL, result.getDiagnostic().code());
        assertEquals(2, result.getDiagnostics().size());
        assertTrue(result.getReason().contains("Synthetic semantic gap"));
        assertTrue(result.getDiagnostics().get(1).message().contains("supported semantic subset"));
    }

    @Test
    void reportsIrDiagnosticsFromCodedIrBuilderException() {
        String spec = """
                model Demo
                class Person
                attributes
                    age : Integer
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        OclMetamodelIndex index = new OclMetamodelIndex(model);
        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(
                index,
                new OclSemanticBinder(index),
                new OclIrBuilder() {
                    @Override
                    public org.uet.dse.neo4jtgg.ocl.ir.OclIr.InvariantQuery buildInvariant(OclSemanticBinder.BoundContextInvariant invariant) {
                        throw new OclCodedUnsupportedOperationException(
                                OclDiagnosticCode.UNSUPPORTED_BOUND_EXPRESSION,
                                "Synthetic IR lowering gap.");
                    }
                },
                new OclIrOptimizer(),
                new OclCypherPlanner(),
                new OclCypherRenderer());

        CypherCompilationResult result = compiler.compile("context Person inv Adult: self.age >= 18");

        assertFalse(result.isSupported());
        assertEquals("IR", result.getDiagnostic().phase().name());
        assertEquals(OclDiagnosticCode.UNSUPPORTED_BOUND_EXPRESSION, result.getDiagnostic().code());
        assertEquals(2, result.getDiagnostics().size());
        assertTrue(result.getDiagnostics().get(1).message().contains("IR lowering"));
    }

    @Test
    void reportsPlanningDiagnosticsWithHint() {
        String spec = """
                model Demo
                class Person
                attributes
                    age : Integer
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        OclMetamodelIndex index = new OclMetamodelIndex(model);
        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(
                index,
                new OclSemanticBinder(index),
                new OclIrBuilder(),
                new OclIrOptimizer(),
                new OclCypherPlanner() {
                    @Override
                    public OclCypherPlan.InvariantPlan planInvariant(org.uet.dse.neo4jtgg.ocl.ir.OclIr.InvariantQuery invariantQuery) {
                        throw new IllegalStateException("Synthetic planning failure for test.");
                    }
                },
                new OclCypherRenderer());

        CypherCompilationResult result = compiler.compile("context Person inv Adult: self.age >= 18");

        assertFalse(result.isSupported());
        assertNotNull(result.getDiagnostic());
        assertEquals("PLANNING", result.getDiagnostic().phase().name());
        assertEquals(2, result.getDiagnostics().size());
        assertTrue(result.getReason().contains("Synthetic planning failure"));
        assertTrue(result.getDiagnostics().get(1).message().contains("Hint:"));
    }

    @Test
    void reportsPlanningDiagnosticsWithSpecificCountHint() {
        String spec = """
                model Demo
                class Person
                attributes
                    age : Integer
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        OclMetamodelIndex index = new OclMetamodelIndex(model);
        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(
                index,
                new OclSemanticBinder(index),
                new OclIrBuilder(),
                new OclIrOptimizer(),
                new OclCypherPlanner() {
                    @Override
                    public OclCypherPlan.InvariantPlan planInvariant(org.uet.dse.neo4jtgg.ocl.ir.OclIr.InvariantQuery invariantQuery) {
                        throw new OclCodedUnsupportedOperationException(
                                OclDiagnosticCode.UNSUPPORTED_COUNT_OPERATOR,
                                "Synthetic planner mismatch for count predicate.");
                    }
                },
                new OclCypherRenderer());

        CypherCompilationResult result = compiler.compile("context Person inv Adult: self.age >= 18");

        assertFalse(result.isSupported());
        assertEquals("PLANNING", result.getDiagnostic().phase().name());
        assertEquals(OclDiagnosticCode.UNSUPPORTED_COUNT_OPERATOR, result.getDiagnostic().code());
        assertEquals(2, result.getDiagnostics().size());
        assertTrue(result.getDiagnostics().get(1).message().contains("supported count predicate"));
    }

    @Test
    void reportsRenderingDiagnosticsWithHint() {
        String spec = """
                model Demo
                class Person
                attributes
                    age : Integer
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        OclMetamodelIndex index = new OclMetamodelIndex(model);
        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(
                index,
                new OclSemanticBinder(index),
                new OclIrBuilder(),
                new OclIrOptimizer(),
                new OclCypherPlanner(),
                new OclCypherRenderer() {
                    @Override
                    public RenderedInvariant renderInvariant(OclCypherPlan.InvariantPlan invariantPlan) {
                        throw new UnsupportedOperationException("Synthetic rendering failure for test.");
                    }
                });

        CypherCompilationResult result = compiler.compile("context Person inv Adult: self.age >= 18");

        assertFalse(result.isSupported());
        assertNotNull(result.getDiagnostic());
        assertEquals("RENDERING", result.getDiagnostic().phase().name());
        assertEquals(2, result.getDiagnostics().size());
        assertTrue(result.getReason().contains("Synthetic rendering failure"));
        assertTrue(result.getDiagnostics().get(1).message().contains("Hint:"));
    }

    @Test
    void reportsRenderingDiagnosticsWithSpecificCollectionHint() {
        String spec = """
                model Demo
                class Person
                attributes
                    age : Integer
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        OclMetamodelIndex index = new OclMetamodelIndex(model);
        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(
                index,
                new OclSemanticBinder(index),
                new OclIrBuilder(),
                new OclIrOptimizer(),
                new OclCypherPlanner(),
                new OclCypherRenderer() {
                    @Override
                    public RenderedInvariant renderInvariant(OclCypherPlan.InvariantPlan invariantPlan) {
                        throw new OclCodedUnsupportedOperationException(
                                OclDiagnosticCode.UNSUPPORTED_COLLECTION_OPERATION,
                                "Synthetic renderer collection gap.");
                    }
                });

        CypherCompilationResult result = compiler.compile("context Person inv Adult: self.age >= 18");

        assertFalse(result.isSupported());
        assertEquals("RENDERING", result.getDiagnostic().phase().name());
        assertEquals(OclDiagnosticCode.UNSUPPORTED_COLLECTION_OPERATION, result.getDiagnostic().code());
        assertEquals(2, result.getDiagnostics().size());
        assertTrue(result.getDiagnostics().get(1).message().contains("supported subset"));
    }

    @Test
    void reportsRenderingDiagnosticsWithSpecificIteratorHint() {
        String spec = """
                model Demo
                class Person
                attributes
                    age : Integer
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        OclMetamodelIndex index = new OclMetamodelIndex(model);
        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(
                index,
                new OclSemanticBinder(index),
                new OclIrBuilder(),
                new OclIrOptimizer(),
                new OclCypherPlanner(),
                new OclCypherRenderer() {
                    @Override
                    public RenderedInvariant renderInvariant(OclCypherPlan.InvariantPlan invariantPlan) {
                        throw new UnsupportedOperationException("Unsupported iterator: sortedBy");
                    }
                });

        CypherCompilationResult result = compiler.compile("context Person inv Adult: self.age >= 18");

        assertFalse(result.isSupported());
        assertEquals("RENDERING", result.getDiagnostic().phase().name());
        assertEquals(2, result.getDiagnostics().size());
        assertTrue(result.getDiagnostics().get(1).message().contains("select/collect/exists/forall/one/any"));
    }

    @Test
    void reportsRenderingDiagnosticsWithSpecificOperatorHint() {
        String spec = """
                model Demo
                class Person
                attributes
                    age : Integer
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        OclMetamodelIndex index = new OclMetamodelIndex(model);
        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(
                index,
                new OclSemanticBinder(index),
                new OclIrBuilder(),
                new OclIrOptimizer(),
                new OclCypherPlanner(),
                new OclCypherRenderer() {
                    @Override
                    public RenderedInvariant renderInvariant(OclCypherPlan.InvariantPlan invariantPlan) {
                        throw new UnsupportedOperationException("Unsupported operator: xor");
                    }
                });

        CypherCompilationResult result = compiler.compile("context Person inv Adult: self.age >= 18");

        assertFalse(result.isSupported());
        assertEquals("RENDERING", result.getDiagnostic().phase().name());
        assertEquals(2, result.getDiagnostics().size());
        assertTrue(result.getDiagnostics().get(1).message().contains("supported boolean, comparison, or arithmetic operator"));
    }

    @Test
    void reportsRenderingDiagnosticsWithSpecificMethodCallHint() {
        String spec = """
                model Demo
                class Person
                attributes
                    age : Integer
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        OclMetamodelIndex index = new OclMetamodelIndex(model);
        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(
                index,
                new OclSemanticBinder(index),
                new OclIrBuilder(),
                new OclIrOptimizer(),
                new OclCypherPlanner(),
                new OclCypherRenderer() {
                    @Override
                    public RenderedInvariant renderInvariant(OclCypherPlan.InvariantPlan invariantPlan) {
                        throw new UnsupportedOperationException("Unsupported method call: sortedBy");
                    }
                });

        CypherCompilationResult result = compiler.compile("context Person inv Adult: self.age >= 18");

        assertFalse(result.isSupported());
        assertEquals("RENDERING", result.getDiagnostic().phase().name());
        assertEquals(OclDiagnosticCode.UNSUPPORTED_METHOD_CALL, result.getDiagnostic().code());
        assertEquals(2, result.getDiagnostics().size());
        assertTrue(result.getDiagnostics().get(1).message().contains("split()/isDefined()/isUndefined()"));
    }

    @Test
    void reportsRenderingDiagnosticsForInvalidMethodArgumentArity() {
        String spec = """
                model Demo
                class Person
                attributes
                    name : String
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        CypherCompilationResult result = compiler.compile(
                "context Person inv BadSplit: self.name.split()->notEmpty()");

        assertFalse(result.isSupported());
        assertEquals("RENDERING", result.getDiagnostic().phase().name());
        assertEquals(OclDiagnosticCode.INVALID_METHOD_ARGUMENT, result.getDiagnostic().code());
        assertEquals(2, result.getDiagnostics().size());
        assertTrue(result.getDiagnostics().get(1).message().contains("invalid argument shape"));
    }

    @Test
    void reportsRenderingDiagnosticsForInvalidCollectionArgumentArity() {
        String spec = """
                model Demo
                class Person
                attributes
                    name : String
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        CypherCompilationResult result = compiler.compile(
                "context Person inv BadCount: self.name.split(',')->count() = 0");

        assertFalse(result.isSupported());
        assertEquals("RENDERING", result.getDiagnostic().phase().name());
        assertEquals(OclDiagnosticCode.INVALID_COLLECTION_ARGUMENT, result.getDiagnostic().code());
        assertEquals(2, result.getDiagnostics().size());
        assertTrue(result.getDiagnostics().get(1).message().contains("invalid argument shape"));
    }

    @Test
    void reportsPlanningDiagnosticsWithSpecificPlanSourceHint() {
        String spec = """
                model Demo
                class Person
                attributes
                    age : Integer
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        OclMetamodelIndex index = new OclMetamodelIndex(model);
        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(
                index,
                new OclSemanticBinder(index),
                new OclIrBuilder(),
                new OclIrOptimizer(),
                new OclCypherPlanner() {
                    @Override
                    public OclCypherPlan.InvariantPlan planInvariant(org.uet.dse.neo4jtgg.ocl.ir.OclIr.InvariantQuery invariantQuery) {
                        throw new IllegalStateException("Unsupported plan source: SyntheticPlan");
                    }
                },
                new OclCypherRenderer());

        CypherCompilationResult result = compiler.compile("context Person inv Adult: self.age >= 18");

        assertFalse(result.isSupported());
        assertEquals("PLANNING", result.getDiagnostic().phase().name());
        assertEquals(2, result.getDiagnostics().size());
        assertTrue(result.getDiagnostics().get(1).message().contains("dedicated plan node"));
    }
}
