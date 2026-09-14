package org.uet.dse.neo4jtgg.service.impl;

import org.junit.jupiter.api.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.uet.dse.neo4j.encoding.CanonicalGraphEncoding;
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
    void compilesCertifiedXorAndSetExtensionsDeterministically() {
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

        String ocl = """
                context Person inv Extended:
                    ((self.age >= 18) xor (self.age >= 65)) or
                    (Set{1, 1, 2}->union(Set{2, 3})->intersection(Set{1, 2, 3})
                        ->asSet()->isUnique(x | x))
                """;
        CypherCompilationResult first = compiler.compile(ocl);
        CypherCompilationResult second = compiler.compile(ocl);

        assertTrue(first.isSupported(), first.getReason());
        assertEquals(first.getCypher(), second.getCypher());
        assertEquals(first.getParameters(), second.getParameters());
        assertTrue(first.getCypher().contains("reduce("));
        assertTrue(first.getCypher().contains("size("));
        assertTrue(first.getParameters().values().stream()
                .anyMatch(value -> value instanceof Map<?, ?> map
                        && Boolean.TRUE.equals(map.get("__oclBottom"))));
        assertTrue(first.getCypher().contains("coalesce("));
    }

    @Test
    void rendersBottomSafeTypedSetEqualityForDeduplicationAndMembership() {
        String spec = """
                model Demo
                class Person
                end
                """;
        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());
        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);

        CypherCompilationResult result = compiler.compile("""
                context Person inv BottomSetSemantics:
                  Set{1, null, null}->size() = 2 and
                  Set{1, null}->includes(null) and
                  Set{1, 2} = Set{2, 1} and
                  not Set{1, 2}->isUnique(x | null)
                """);

        assertTrue(result.isSupported(), result.getReason());
        assertTrue(result.getCypher().contains("coalesce(null, $"), result.getCypher());
        assertTrue(result.getCypher().contains("__oclBottom")
                || result.getParameters().values().stream().anyMatch(value -> value instanceof Map<?, ?> map
                && Boolean.TRUE.equals(map.get("__oclBottom"))), result.getCypher());
        assertTrue(result.getCypher().contains(", false)"), result.getCypher());
        assertTrue(result.getCypher().contains("all(setLeft"), result.getCypher());
        assertTrue(result.getCypher().contains("any(setRight"), result.getCypher());
    }

    @Test
    void rejectsUninferableOrNestedSetLiteralsDuringBinding() {
        String spec = """
                model Demo
                class Person
                end
                """;
        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());
        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);

        CypherCompilationResult empty = compiler.compile("context Person inv Empty: Set{}->isEmpty()");
        CypherCompilationResult nested = compiler.compile("context Person inv Nested: Set{Set{1}}->notEmpty()");

        assertFalse(empty.isSupported());
        assertFalse(nested.isSupported());
        assertEquals(OclDiagnosticCode.INVALID_COLLECTION_ARGUMENT, empty.getDiagnostics().get(0).code());
        assertEquals(OclDiagnosticCode.INVALID_COLLECTION_ARGUMENT, nested.getDiagnostics().get(0).code());
    }

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
        assertEquals(0, result.getDocumentDiagnostics().size());
        assertTrue(result.getRuleResults().get(1).isSupported());
        assertTrue(result.getRuleResults().get(1).getCompilation().getCypher().contains("RETURN"));
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
        assertTrue(result.getCypher().contains("MATCH (self:Object {modelKey:"));
        assertTrue(result.getCypher().contains("(cls:UmlClass {modelKey:"));
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
    void compilesCollectionValuedPropertyProjectionByNormalizingThroughFlatten() {
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
        CypherCompilationResult result = compiler.compile(
                "context Family inv ChildFamiliesIncludeSelf: self.children.family->includes(self)");

        assertTrue(result.isSupported(), result.getReason());
        assertTrue(result.getCypher().contains("reduce("));
        assertTrue(result.getCypher().contains("IN ["));
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
        assertFalse(result.getCypher().contains(" ELSE false "));
    }

    @Test
    void compilesNullIfConditionAsBottom() {
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

        assertTrue(result.isSupported(), result.getReason());
        assertTrue(result.getCypher().contains("THEN null WHEN"), result.getCypher());
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
    void keepsReusedLetAsSingleExpressionBindingInCypher() {
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
        assertTrue(result.getCypher().contains("head([_let"));
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
        assertTrue(result.getCypher().contains("NOT EXISTS {")
                && result.getCypher().contains(")-[r]->(c:Object {modelKey:"));
        assertTrue(result.getCypher().contains("r.associationKey = $"));
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
        assertTrue(result.getCypher().contains("EXISTS {")
                && result.getCypher().contains(")-[r]->(c:Object {modelKey:"));
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
        assertTrue(result.getCypher().contains("EXISTS {")
                && result.getCypher().contains(")-[r]->(nav:Object {modelKey:"));
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
        assertTrue(result.getCypher().contains("EXISTS {")
                && result.getCypher().contains(")-[r]->(c:Object {modelKey:"));
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
        assertTrue(result.getCypher().contains("EXISTS {")
                && result.getCypher().contains(")-[r]->(nav:Object {modelKey:"));
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
        assertTrue(result.getCypher().contains("NOT EXISTS {")
                && result.getCypher().contains(")-[r]->(nav:Object {modelKey:"));
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
        assertTrue(result.getCypher().contains("size(COLLECT {")
                && result.getCypher().contains(")-[r]->(c:Object {modelKey:"));
        assertTrue(result.getCypher().contains("ObjectHasAttribute"));
    }

    @Test
    void compilesSelectedNavigationExistsAndOneViaSubqueryPlans() {
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
        CypherCompilationResult exists = compiler.compile(
                "context Family inv AdultBart: self.children->select(c | c.age >= 18)->exists(c | c.name = 'Bart')");
        CypherCompilationResult one = compiler.compile(
                "context Family inv ExactlyOneAdult: self.children->one(c | c.age >= 18)");

        assertTrue(exists.isSupported(), exists.getReason());
        assertTrue(exists.getCypher().contains("EXISTS {")
                && exists.getCypher().contains(")-[r]->(c:Object {modelKey:"));
        assertTrue(exists.getCypher().contains("AND coalesce("));
        assertTrue(one.isSupported(), one.getReason());
        assertTrue(one.getCypher().contains("size(COLLECT {")
                && one.getCypher().contains(")-[r]->(c:Object {modelKey:"));
        assertTrue(one.getCypher().contains("= $"));
    }

    @Test
    void compilesRejectedNavigationChecksViaSubqueryPlans() {
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
        CypherCompilationResult empty = compiler.compile(
                "context Family inv AdultOnly: self.children->reject(c | c.age >= 18)->isEmpty()");
        CypherCompilationResult one = compiler.compile(
                "context Family inv OneMinorNamedLisa: self.children->reject(c | c.age >= 18)->one(c | c.name = 'Lisa')");

        assertTrue(empty.isSupported(), empty.getReason());
        assertTrue(empty.getCypher().contains("NOT EXISTS {")
                && empty.getCypher().contains(")-[r]->(c:Object {modelKey:"));
        assertTrue(empty.getCypher().contains("NOT coalesce("));
        assertTrue(one.isSupported(), one.getReason());
        assertTrue(one.getCypher().contains("size(COLLECT {")
                && one.getCypher().contains(")-[r]->(c:Object {modelKey:"));
        assertTrue(one.getCypher().contains("NOT coalesce("));
    }

    @Test
    void compilesMultiLevelSelectRejectChainViaSingleExistsSubquery() {
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
        CypherCompilationResult result = compiler.compile(
                "context Family inv ChainedFilters: self.children->select(c | c.age >= 18)->reject(c | c.name = 'Bart')->exists(c | c.name = 'Lisa')");

        assertTrue(result.isSupported(), result.getReason());
        assertTrue(result.getCypher().contains("EXISTS {")
                && result.getCypher().contains(")-[r]->(c:Object {modelKey:"));
        assertTrue(result.getCypher().contains("NOT coalesce("));
        assertTrue(result.getCypher().contains("AND coalesce("));
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
        assertTrue(result.getCypher().contains(")<-[r]-(nav:Object {modelKey:"));
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
        assertTrue(child.getCypher().contains(")-[r]->(nav:Object {modelKey:"));
        assertTrue(parent.getCypher().contains(")<-[r]-(nav:Object {modelKey:"));
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
        String employeeKey = CanonicalGraphEncoding.associationKey("Demo", "CompanyEmployee");
        String managerKey = CanonicalGraphEncoding.associationKey("Demo", "CompanyManager");
        assertTrue(employees.getParameters().containsValue(employeeKey));
        assertFalse(employees.getParameters().containsValue(managerKey));
        assertTrue(manager.getParameters().containsValue(managerKey));
        assertFalse(manager.getParameters().containsValue(employeeKey));
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
        assertTrue(result.getCypher().contains(")<-[r]-(nav:Object {modelKey:"));
        assertTrue(result.getParameters().containsValue(
                CanonicalGraphEncoding.associationKey("Demo", "CompanyStaff")));
        assertTrue(result.getParameters().containsValue(
                CanonicalGraphEncoding.classKey("Demo", "Employee")));
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
    void compilesCollectionIncludingAndExcluding() {
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
        CypherCompilationResult including = compiler.compile(
                "context Family inv AddedBartPresent: self.name.split(',')->including('Bart')->includes('Bart')");
        CypherCompilationResult excluding = compiler.compile(
                "context Family inv RemovedBartGone: self.name.split(',')->excluding('Bart')->excludes('Bart')");
        CypherCompilationResult uniqueIncluding = compiler.compile(
                "context Family inv UniqueAddedBartPresent: self.name.split(',')->asSet()->including('Bart')->includes('Bart')");

        assertTrue(including.isSupported(), including.getReason());
        assertTrue(including.getCypher().contains(" + ["));
        assertTrue(excluding.isSupported(), excluding.getReason());
        assertTrue(excluding.getCypher().contains("WHERE NOT coalesce("));
        assertTrue(uniqueIncluding.isSupported(), uniqueIncluding.getReason());
        assertTrue(uniqueIncluding.getCypher().contains("CASE WHEN any(existing"));
    }

    @Test
    void compilesCollectionAppendAndPrepend() {
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
        CypherCompilationResult append = compiler.compile(
                "context Family inv AppendedBartPresent: self.name.split(',')->append('Bart')->includes('Bart')");
        CypherCompilationResult prepend = compiler.compile(
                "context Family inv PrependedBartFirst: self.name.split(',')->prepend('Bart')->first() = 'Bart'");
        CypherCompilationResult uniquePrepend = compiler.compile(
                "context Family inv UniquePrependedBartFirst: self.name.split(',')->asOrderedSet()->prepend('Bart')->first() = 'Bart'");

        assertTrue(append.isSupported(), append.getReason());
        assertTrue(append.getCypher().contains(" + ["));
        assertTrue(prepend.isSupported(), prepend.getReason());
        assertTrue(prepend.getCypher().contains("(["));
        assertTrue(prepend.getCypher().contains("] + "));
        assertTrue(uniquePrepend.isSupported(), uniquePrepend.getReason());
        assertTrue(uniquePrepend.getCypher().contains("CASE WHEN any(existing"));
    }

    @Test
    void compilesCollectionSubSequence() {
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
        CypherCompilationResult sequenceSubSequence = compiler.compile(
                "context Family inv MiddleNameDefined: self.name.split(',')->subSequence(1, 2)->last().isDefined()");
        CypherCompilationResult orderedSetSubSequence = compiler.compile(
                "context Family inv FirstUniqueMiddleNameDefined: self.name.split(',')->asOrderedSet()->subSequence(1, 2)->first().isDefined()");

        assertTrue(sequenceSubSequence.isSupported(), sequenceSubSequence.getReason());
        assertTrue(sequenceSubSequence.getCypher().contains("[("));
        assertTrue(sequenceSubSequence.getCypher().contains("..("));
        assertTrue(orderedSetSubSequence.isSupported(), orderedSetSubSequence.getReason());
        assertTrue(orderedSetSubSequence.getCypher().contains("CASE WHEN any(existing"));
        assertTrue(orderedSetSubSequence.getCypher().contains("[("));
    }

    @Test
    void compilesIteratorSortedBy() {
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
        CypherCompilationResult sortedSequence = compiler.compile(
                "context Family inv SortedNamesBeginDefined: self.name.split(',')->sortedBy(token | token)->first().isDefined()");
        CypherCompilationResult sortedUnique = compiler.compile(
                "context Family inv SortedUniqueNamesBeginDefined: self.name.split(',')->asSet()->sortedBy(token | token)->first().isDefined()");

        assertTrue(sortedSequence.isSupported(), sortedSequence.getReason());
        assertTrue(sortedSequence.getCypher().contains("COLLECT { UNWIND range("));
        assertTrue(sortedSequence.getCypher().contains("ORDER BY"));

        assertTrue(sortedUnique.isSupported(), sortedUnique.getReason());
        assertTrue(sortedUnique.getCypher().contains("CASE WHEN any(existing"));
        assertTrue(sortedUnique.getCypher().contains("ORDER BY"));
    }

    @Test
    void compilesMethodOclAsType() {
        String spec = """
                model Demo
                class Person
                end
                class Employee < Person
                attributes
                    salary : Integer
                end
                class Manager < Person
                attributes
                    level : Integer
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        CypherCompilationResult castSuccess = compiler.compile(
                "context Employee inv SalaryVisibleAfterCast: self.oclAsType(Employee).salary >= 0");
        CypherCompilationResult castMismatch = compiler.compile(
                "context Employee inv WrongCastBecomesUndefined: self.oclAsType(Manager).isUndefined()");

        assertTrue(castSuccess.isSupported(), castSuccess.getReason());
        assertTrue(castSuccess.getCypher().contains("COLLECT { WITH self.objectKey AS castRecv"));
        assertTrue(castSuccess.getCypher().contains("Key IS NOT NULL MATCH (castRecv"));
        assertTrue(castSuccess.getCypher().contains("ObjectInstanceOf"));
        assertTrue(castSuccess.getCypher().contains("(castCls"), castSuccess.getCypher());

        assertTrue(castMismatch.isSupported(), castMismatch.getReason());
        assertTrue(castMismatch.getCypher().contains("COLLECT { WITH self.objectKey AS castRecv"));
        assertTrue(castMismatch.getCypher().contains("Key IS NOT NULL MATCH (castRecv"));
        assertTrue(castMismatch.getCypher().contains("(castCls"), castMismatch.getCypher());
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
        assertTrue((one.getCypher().contains("size(COLLECT {")
                && one.getCypher().contains(")-[r]->(c:Object {modelKey:"))
                || one.getCypher().contains("single("));
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
        assertTrue(bagIntersection.getCypher().contains("remaining"));
        assertTrue(bagIntersection.getCypher().contains("CASE WHEN any(existing"));
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
    void compilesCollectionAggregates() {
        String spec = """
                model Demo
                class Family
                end
                class Person
                attributes
                    age : Integer
                    score : Real
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
        CypherCompilationResult sum = compiler.compile(
                "context Family inv TotalAgePositive: self.children->collect(c | c.age)->sum() >= 0");
        CypherCompilationResult min = compiler.compile(
                "context Family inv MinAgeUndefinedWhenEmpty: self.children->collect(c | c.age)->min().isUndefined()");
        CypherCompilationResult max = compiler.compile(
                "context Family inv MaxScorePositive: self.children->collect(c | c.score)->max() >= 0.0");

        assertTrue(sum.isSupported(), sum.getReason());
        assertTrue(sum.getCypher().contains("reduce("));
        assertTrue(sum.getCypher().contains("= 0"));
        assertTrue(min.isSupported(), min.getReason());
        assertTrue(min.getCypher().contains("best"));
        assertTrue(min.getCypher().contains(" IS NULL"));
        assertTrue(max.isSupported(), max.getReason());
        assertTrue(max.getCypher().contains("reduce("));
        assertTrue(max.getCypher().contains(">"));
    }

    @Test
    void compilesOptimizedNavigationAggregates() {
        String spec = """
                model Demo
                class Family
                end
                class Person
                attributes
                    age : Integer
                    score : Real
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
        CypherCompilationResult sum = compiler.compile(
                "context Family inv TotalAgePositive: self.children->collect(c | c.age)->sum() >= 0");
        CypherCompilationResult max = compiler.compile(
                "context Family inv AdultScoreMax: self.children->select(c | c.age >= 18)->collect(c | c.score)->max() >= 0.0");

        assertTrue(sum.isSupported(), sum.getReason());
        assertTrue(sum.getCypher().contains(")-[r]->(c:Object {modelKey:"));
        assertTrue(sum.getCypher().contains("| acc"));
        assertTrue(sum.getCypher().contains("+ item"));

        assertTrue(max.isSupported(), max.getReason());
        assertTrue(max.getCypher().contains(")-[r]->(c:Object {modelKey:"));
        assertTrue(max.getCypher().contains("coalesce("));
        assertTrue(max.getCypher().contains("best"));
    }

    @Test
    void compilesOptimizedNavigationChainsWithDifferentIteratorNames() {
        String spec = """
                model Demo
                class Family
                end
                class Person
                attributes
                    age : Integer
                    name : String
                    score : Real
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
        CypherCompilationResult exists = compiler.compile(
                "context Family inv ChainedFilters: self.children->select(a | a.age >= 18)->reject(b | b.name = 'Bart')->exists(c | c.name = 'Lisa')");
        CypherCompilationResult sum = compiler.compile(
                "context Family inv AdultScoreSum: self.children->select(a | a.age >= 18)->collect(b | b.score)->sum() >= 0.0");

        assertTrue(exists.isSupported(), exists.getReason());
        assertTrue(exists.getCypher().contains("EXISTS {")
                && exists.getCypher().contains(")-[r]->(c:Object {modelKey:"));
        assertTrue(exists.getCypher().contains("AND coalesce("));

        assertTrue(sum.isSupported(), sum.getReason());
        assertTrue(sum.getCypher().contains(")-[r]->(b:Object {modelKey:"));
        assertTrue(sum.getCypher().contains("reduce("));
        assertTrue(sum.getCypher().contains("coalesce("));
    }

    @Test
    void compilesOptimizedNavigationIsUnique() {
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
        CypherCompilationResult result = compiler.compile(
                "context Family inv UniqueAdultNames: self.children->select(c | c.age >= 18)->isUnique(c | c.name)");

        assertTrue(result.isSupported(), result.getReason());
        assertTrue(result.getCypher().contains("size(reduce("));
        assertTrue(result.getCypher().contains("reduce("));
        assertTrue(result.getCypher().contains("any(existing"));
        assertTrue(result.getCypher().contains("coalesce("));
    }

    @Test
    void compilesIsUniqueIterator() {
        String spec = """
                model Demo
                class Family
                end
                class Person
                attributes
                    name : String
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
        CypherCompilationResult uniqueNames = compiler.compile(
                "context Family inv UniqueChildNames: self.children->isUnique(c | c.name)");
        CypherCompilationResult uniqueAges = compiler.compile(
                "context Family inv UniqueChildAges: self.children->isUnique(c | c.age)");

        assertTrue(uniqueNames.isSupported(), uniqueNames.getReason());
        assertTrue(uniqueNames.getCypher().contains("size(reduce("));
        assertTrue(uniqueNames.getCypher().contains("CASE WHEN any(existing"));
        assertTrue(uniqueAges.isSupported(), uniqueAges.getReason());
        assertTrue(uniqueAges.getCypher().contains(" = size("));
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
    void compilesFlattenNotEmptyViaNestedExistsSubquery() {
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
        CypherCompilationResult result = compiler.compile(
                "context Family inv FlattenedFamiliesExist: self.children->collect(c | c.family)->flatten()->notEmpty()");

        assertTrue(result.isSupported(), result.getReason());
        assertTrue(result.getCypher().contains("EXISTS {")
                && result.getCypher().contains(")-[r]->(c:Object {modelKey:"));
        assertTrue(result.getCypher().contains("size("));
    }

    @Test
    void compilesFlattenIsEmptyViaNestedNotExistsSubquery() {
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
        CypherCompilationResult result = compiler.compile(
                "context Family inv FlattenedFamiliesEmpty: self.children->collect(c | c.family)->flatten()->isEmpty()");

        assertTrue(result.isSupported(), result.getReason());
        assertTrue(result.getCypher().contains("NOT EXISTS {")
                && result.getCypher().contains(")-[r]->(c:Object {modelKey:"));
        assertTrue(result.getCypher().contains("size("));
    }

    @Test
    void admitsCanonicalNAryNavigationOnlyInGeneralProductionProfile() {
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

        assertTrue(result.isSupported(), result.getReason());
        assertTrue(result.getCypher().contains(":LinkHub"), result.getCypher());
        assertTrue(result.getCypher().contains(".linkKey"), result.getCypher());
        assertTrue(result.getCypher().contains(".role"), result.getCypher());

        RuntimeException certifiedFailure = assertThrows(RuntimeException.class,
                () -> compiler.compileInvariantInstrumented(
                        "context Person inv HasPets: self.pet->notEmpty()"));
        assertTrue(certifiedFailure.getMessage().contains("binary UML associations"),
                certifiedFailure.getMessage());
    }

    @Test
    void compilesNavigationOverQualifiedAssociationWithoutQualifierFiltering() {
        String spec = """
                model Demo
                class Library
                end
                class Book
                end
                association Catalog between
                    Library[1] role library qualifier (shelf : String)
                    Book[*] role book
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        CypherCompilationResult result = compiler.compile(
                "context Library inv HasBooks: self.book->notEmpty()");

        assertTrue(result.isSupported(), result.getReason());
        assertTrue(result.getCypher().contains("EXISTS {")
                && result.getCypher().contains(")-[r]->("));
    }

    @Test
    void compilesNavigationOverQualifiedAssociationWithVariableQualifierFilter() {
        String spec = """
                model Demo
                class Library
                attributes
                    defaultShelf : String
                end
                class Book
                end
                association Catalog between
                    Library[1] role library qualifier (shelf : String)
                    Book[*] role book
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        CypherCompilationResult result = compiler.compile(
                "context Library inv ShelfLookup: let shelf = self.defaultShelf in self.book[shelf]->notEmpty()");

        assertTrue(result.isSupported(), result.getReason());
        assertTrue(result.getCypher().contains("sourceQualifiers[0]"));
    }

    @Test
    void compilesNavigationOverQualifiedAssociationWithComputedQualifierFilter() {
        String spec = """
                model Demo
                class Library
                attributes
                    defaultShelf : String
                end
                class Book
                end
                association Catalog between
                    Library[1] role library qualifier (shelf : String)
                    Book[*] role book
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        CypherCompilationResult result = compiler.compile(
                "context Library inv ShelfLookup: self.book[self.defaultShelf.concat('')]->notEmpty()");

        assertTrue(result.isSupported(), result.getReason());
        assertTrue(result.getCypher().contains("sourceQualifiers[0]"));
        assertTrue(result.getCypher().contains("replace(toString"));
    }

    @Test
    void compilesNavigationOverQualifiedAssociationWithEnumLiteralQualifierFilter() {
        String spec = """
                model Demo
                enum Shelf { A1, B2 }
                class Library
                end
                class Book
                end
                association Catalog between
                    Library[1] role library qualifier (shelf : Shelf)
                    Book[*] role book
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        CypherCompilationResult result = compiler.compile(
                "context Library inv ShelfLookup: self.book[Shelf::A1]->notEmpty()");

        assertTrue(result.isSupported(), result.getReason());
        assertTrue(result.getCypher().contains("sourceQualifiers[0]"));
    }

    @Test
    void compilesNavigationOverQualifiedAssociationWithEnumAttributeQualifierFilter() {
        String spec = """
                model Demo
                enum Shelf { A1, B2 }
                class Library
                attributes
                    defaultShelf : Shelf
                end
                class Book
                end
                association Catalog between
                    Library[1] role library qualifier (shelf : Shelf)
                    Book[*] role book
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        CypherCompilationResult result = compiler.compile(
                "context Library inv ShelfLookup: self.book[self.defaultShelf]->notEmpty()");

        assertTrue(result.isSupported(), result.getReason());
        assertTrue(result.getCypher().contains("sourceQualifiers[0]"));
        assertTrue(result.getCypher().contains("replace(toString"));
    }

    @Test
    void compilesNavigationOverQualifiedAssociationWithLiteralQualifierFilter() {
        String spec = """
                model Demo
                class Library
                end
                class Book
                end
                association Catalog between
                    Library[1] role library qualifier (shelf : String)
                    Book[*] role book
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        CypherCompilationResult result = compiler.compile(
                "context Library inv ShelfLookup: self.book['A1']->notEmpty()");

        assertTrue(result.isSupported(), result.getReason());
        assertTrue(result.getCypher().contains("sourceQualifiers[0]"));
    }

    @Test
    void compilesNavigationOverRedefiningAssociation() {
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

        assertTrue(result.isSupported(), result.getReason());
        assertTrue(result.getCypher().contains("EXISTS {")
                && result.getCypher().contains(")-[r]->("));
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
    void compilesCollectionValuedPrimitiveAttribute() {
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
        CypherCompilationResult result = compiler.compile("context Person inv HasAliases: self.aliases->count('Bart') >= 1 and self.aliases->first().isDefined()");

        assertTrue(result.isSupported(), result.getReason());
        assertTrue(result.getCypher().contains("split("));
        assertTrue(result.getCypher().contains("size(["));
        assertTrue(result.getCypher().contains("head("));
    }

    @Test
    void compilesCollectionValuedObjectReferenceAttribute() {
        String spec = """
                model Demo
                class Person
                attributes
                    friends : Sequence(Person)
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        CypherCompilationResult result = compiler.compile(
                "context Person inv HasSelfOrEmpty: self.friends->includes(self) or self.friends->isEmpty()");

        assertTrue(result.isSupported(), result.getReason());
        assertTrue(result.getCypher().contains("objectReference"));
        assertTrue(result.getCypher().contains("HasReferenceValue"));
        assertTrue(result.getCypher().contains("ORDER BY r.index"));
        assertTrue(result.getCypher().contains("any("));
    }

    @Test
    void compilesNestedCollectionValuedPrimitiveAttribute() {
        String spec = """
                model Demo
                class Person
                attributes
                    aliases2d : Sequence(Sequence(String))
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        CypherCompilationResult result = compiler.compile(
                "context Person inv HasBartSomewhere: self.aliases2d->flatten()->count('Bart') >= 1");

        assertTrue(result.isSupported(), result.getReason());
        assertTrue(result.getCypher().contains("HasNestedCollectionValue"));
        assertTrue(result.getCypher().contains("split("));
        assertTrue(result.getCypher().contains("reduce("));
    }

    @Test
    void compilesNestedCollectionValuedObjectReferenceAttribute() {
        String spec = """
                model Demo
                class Person
                attributes
                    friendGroups : Sequence(Sequence(Person))
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        CypherCompilationResult result = compiler.compile(
                "context Person inv HasSelfSomewhere: self.friendGroups->flatten()->includes(self) or self.friendGroups->flatten()->isEmpty()");

        assertTrue(result.isSupported(), result.getReason());
        assertTrue(result.getCypher().contains("HasNestedCollectionValue"));
        assertTrue(result.getCypher().contains("HasReferenceValue"));
        assertTrue(result.getCypher().contains("ORDER BY r.index"));
    }

    @Test
    void compilesDeepNestedCollectionValuedPrimitiveAttribute() {
        String spec = """
                model Demo
                class Person
                attributes
                    aliases3d : Sequence(Sequence(Sequence(String)))
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        CypherCompilationResult result = compiler.compile(
                "context Person inv HasBartSomewhere: self.aliases3d->flatten()->flatten()->count('Bart') >= 1");

        assertTrue(result.isSupported(), result.getReason());
        assertTrue(result.getCypher().contains("HasNestedCollectionValue"));
        assertTrue(result.getCypher().contains("COLLECT { MATCH (nestedAttr"));
        assertTrue(result.getCypher().contains("reduce("));
    }

    @Test
    void compilesDeepNestedCollectionValuedObjectReferenceAttribute() {
        String spec = """
                model Demo
                class Person
                attributes
                    friendGroups3d : Sequence(Sequence(Sequence(Person)))
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        CypherCompilationResult result = compiler.compile(
                "context Person inv HasSelfSomewhere: self.friendGroups3d->flatten()->flatten()->includes(self) or self.friendGroups3d->flatten()->flatten()->isEmpty()");

        assertTrue(result.isSupported(), result.getReason());
        assertTrue(result.getCypher().contains("HasNestedCollectionValue"));
        assertTrue(result.getCypher().contains("HasReferenceValue"));
        assertTrue(result.getCypher().contains("COLLECT { MATCH (nestedAttr"));
        assertTrue(result.getCypher().contains("ORDER BY r.index"));
    }

    @Test
    void compilesOrderedAndUniqueNestedCollectionCombinations() {
        String spec = """
                model Demo
                class Person
                attributes
                    aliases2d : Sequence(Sequence(String))
                    friendGroups2d : Sequence(Sequence(Person))
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        CypherCompilationResult orderedPrimitive = compiler.compile(
                "context Person inv OrderedAliases: self.aliases2d->asOrderedSet()->flatten()->first().isDefined()");
        CypherCompilationResult uniquePrimitive = compiler.compile(
                "context Person inv UniqueAliases: self.aliases2d->asSet()->flatten()->count('Bart') >= 1");
        CypherCompilationResult uniqueRefs = compiler.compile(
                "context Person inv UniqueFriends: self.friendGroups2d->asSet()->flatten()->count(self) >= 0");

        assertTrue(orderedPrimitive.isSupported(), orderedPrimitive.getReason());
        assertTrue(orderedPrimitive.getCypher().contains("HasNestedCollectionValue"));
        assertTrue(orderedPrimitive.getCypher().contains("head("));

        assertTrue(uniquePrimitive.isSupported(), uniquePrimitive.getReason());
        assertTrue(uniquePrimitive.getCypher().contains("any(existing"));
        assertTrue(uniquePrimitive.getCypher().contains("reduce("));

        assertTrue(uniqueRefs.isSupported(), uniqueRefs.getReason());
        assertTrue(uniqueRefs.getCypher().contains("HasReferenceValue"));
        assertTrue(uniqueRefs.getCypher().contains("any(existing"));
    }

    @Test
    void compilesNestedCollectionSetOperationsAndContainmentChecks() {
        String spec = """
                model Demo
                class Person
                attributes
                    aliases2d : Sequence(Sequence(String))
                    friendGroups2d : Sequence(Sequence(Person))
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        CypherCompilationResult uniqueUnion = compiler.compile(
                "context Person inv UniqueNestedAliases: self.aliases2d->flatten()->asSet()->union(self.aliases2d->flatten()->asSet())->count('Bart') >= 0");
        CypherCompilationResult orderedIntersection = compiler.compile(
                "context Person inv OrderedNestedAliases: self.aliases2d->flatten()->asOrderedSet()->intersection(self.aliases2d->flatten()->asOrderedSet())->first().isDefined()");
        CypherCompilationResult includesAll = compiler.compile(
                "context Person inv NestedAliasesIncludeSelf: self.aliases2d->flatten()->includesAll(self.aliases2d->flatten())");
        CypherCompilationResult excludesAll = compiler.compile(
                "context Person inv NestedAliasesExcludeIntersection: self.aliases2d->flatten()->excludesAll(self.aliases2d->flatten()->intersection(self.aliases2d->flatten())) or self.aliases2d->flatten()->includesAll(self.aliases2d->flatten())");

        assertTrue(uniqueUnion.isSupported(), uniqueUnion.getReason());
        assertTrue(uniqueUnion.getCypher().contains("CASE WHEN any(existing"));

        assertTrue(orderedIntersection.isSupported(), orderedIntersection.getReason());
        assertTrue(orderedIntersection.getCypher().contains("CASE WHEN any(existing"));
        assertTrue(orderedIntersection.getCypher().contains("head("));

        assertTrue(includesAll.isSupported(), includesAll.getReason());
        assertTrue(includesAll.getCypher().contains("all("));
        assertTrue(includesAll.getCypher().contains("any("));

        assertTrue(excludesAll.isSupported(), excludesAll.getReason());
        assertTrue(excludesAll.getCypher().contains("none("));
    }

    @Test
    void compilesNestedObjectReferenceSetOperationsAndContainmentChecks() {
        String spec = """
                model Demo
                class Person
                attributes
                    friendGroups2d : Sequence(Sequence(Person))
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        CypherCompilationResult uniqueUnion = compiler.compile(
                "context Person inv UniqueNestedFriends: self.friendGroups2d->flatten()->asSet()->union(self.friendGroups2d->flatten()->asSet())->count(self) >= 0");
        CypherCompilationResult orderedIntersection = compiler.compile(
                "context Person inv OrderedNestedFriends: self.friendGroups2d->flatten()->asOrderedSet()->intersection(self.friendGroups2d->flatten()->asOrderedSet())->first().isDefined() or self.friendGroups2d->flatten()->isEmpty()");
        CypherCompilationResult includesAll = compiler.compile(
                "context Person inv NestedFriendsIncludeSelf: self.friendGroups2d->flatten()->includesAll(self.friendGroups2d->flatten())");
        CypherCompilationResult excludesAll = compiler.compile(
                "context Person inv NestedFriendsExcludeIntersection: self.friendGroups2d->flatten()->excludesAll(self.friendGroups2d->flatten()->intersection(self.friendGroups2d->flatten())) or self.friendGroups2d->flatten()->includesAll(self.friendGroups2d->flatten())");

        assertTrue(uniqueUnion.isSupported(), uniqueUnion.getReason());
        assertTrue(uniqueUnion.getCypher().contains("HasReferenceValue"));
        assertTrue(uniqueUnion.getCypher().contains("CASE WHEN any(existing"));

        assertTrue(orderedIntersection.isSupported(), orderedIntersection.getReason());
        assertTrue(orderedIntersection.getCypher().contains("HasReferenceValue"));
        assertTrue(orderedIntersection.getCypher().contains("head("));

        assertTrue(includesAll.isSupported(), includesAll.getReason());
        assertTrue(includesAll.getCypher().contains("all("));
        assertTrue(includesAll.getCypher().contains("any("));

        assertTrue(excludesAll.isSupported(), excludesAll.getReason());
        assertTrue(excludesAll.getCypher().contains("none("));
    }

    @Test
    void compilesNestedBagOperationsWithoutImplicitDeduplication() {
        String spec = """
                model Demo
                class Person
                attributes
                    aliases2d : Sequence(Sequence(String))
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        CypherCompilationResult bagUnion = compiler.compile(
                "context Person inv BagNestedAliases: self.aliases2d->flatten()->asBag()->union(self.aliases2d->flatten()->asBag())->count('Bart') >= 2");
        CypherCompilationResult bagIntersection = compiler.compile(
                "context Person inv SharedNestedAliases: self.aliases2d->flatten()->asBag()->intersection(self.aliases2d->flatten()->asBag())->count('Bart') >= 2");

        assertTrue(bagUnion.isSupported(), bagUnion.getReason());
        assertTrue(bagUnion.getCypher().contains(" + "));
        assertFalse(bagUnion.getCypher().contains("CASE WHEN any(existing"));

        assertTrue(bagIntersection.isSupported(), bagIntersection.getReason());
        assertTrue(bagIntersection.getCypher().contains("reduce("));
        assertTrue(bagIntersection.getCypher().contains("remaining"));
        assertTrue(bagIntersection.getCypher().contains("CASE WHEN any(existing"));
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
        assertTrue(result.getDiagnostics().get(1).message().contains("select/reject/collect/exists/forall/one/any/isUnique/sortedBy"));
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
        assertEquals("SEMANTIC", result.getDiagnostic().phase().name());
        assertEquals(OclDiagnosticCode.INVALID_COLLECTION_ARGUMENT, result.getDiagnostic().code());
        assertEquals(2, result.getDiagnostics().size());
        assertTrue(result.getDiagnostics().get(1).message().contains("expects one collection argument"));
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
