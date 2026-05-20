package org.uet.dse.neo4jtgg.ocl.ir;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.jupiter.api.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.uet.dse.neo4j.OCLLexer;
import org.uet.dse.neo4j.OCLParser;
import org.uet.dse.neo4j.oclite.ast.ASTContext;
import org.uet.dse.neo4j.oclite.ast.ASTNode;
import org.uet.dse.neo4j.oclite.ast.ASTVisitor;
import org.uet.dse.neo4jtgg.ocl.OclMetamodelIndex;
import org.uet.dse.neo4jtgg.ocl.OclSemanticBinder;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OclDualCheckTest {
    @Test
    void optimizedIrMatchesJavaEvaluationForNavigationSubset() {
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
                    Family[1] role family
                    Person[*] role children
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        TestObject p1 = TestObject.object("p1", "Person").attribute("age", 20L);
        TestObject p2 = TestObject.object("p2", "Person").attribute("age", 15L);
        TestObject p3 = TestObject.object("p3", "Person").attribute("age", 30L);
        TestObject f1 = TestObject.object("f1", "Family").attribute("name", "Smith").link("children", p1, p2);
        TestObject f2 = TestObject.object("f2", "Family").attribute("name", "Jones").link("children", p3);
        TestObject f3 = TestObject.object("f3", "Family").attribute("name", "Empty");
        p1.link("family", f1);
        p2.link("family", f1);
        p3.link("family", f2);

        TestRuntime runtime = new TestRuntime(List.of(f1, f2, f3, p1, p2, p3));

        assertDualCheck(model, runtime, "context Family inv HasChildren: self.children->notEmpty()", "Family");
        assertDualCheck(model, runtime, "context Family inv AdultChildren: self.children->forall(c | c.age >= 18)", "Family");
        assertDualCheck(model, runtime, "context Family inv TwoAdults: self.children->select(c | c.age >= 18)->size() >= 2", "Family");
        assertDualCheck(model, runtime, "context Person inv HasFamily: self.family->notEmpty()", "Person");
    }

    @Test
    void optimizedIrMatchesJavaEvaluationForCompanyStyleExamples() {
        String spec = """
                model Company
                class Company
                attributes
                    name : String
                end
                class Person
                attributes
                    age : Integer
                    firstName : String
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
        MModel model = USECompiler.compileSpecification(spec, "company.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        TestObject jack = TestObject.object("p1", "Person").attribute("age", 55L).attribute("firstName", "Jack");
        TestObject mary = TestObject.object("p2", "Person").attribute("age", 40L).attribute("firstName", "Mary");
        TestObject tom = TestObject.object("p3", "Person").attribute("age", 70L).attribute("firstName", "Tom");
        TestObject acme = TestObject.object("c1", "Company").attribute("name", "Acme")
                .link("employee", jack, mary)
                .link("manager", mary);
        TestObject beta = TestObject.object("c2", "Company").attribute("name", "Beta")
                .link("employee", tom)
                .link("manager", tom);
        jack.link("employer", acme);
        mary.link("employer", acme).link("managedCompany", acme);
        tom.link("employer", beta).link("managedCompany", beta);

        TestRuntime runtime = new TestRuntime(List.of(acme, beta, jack, mary, tom));

        assertDualCheck(model, runtime,
                "context Company inv SeniorEmployeeExists: self.employee->select(e | e.age > 50)->notEmpty()",
                "Company");
        assertDualCheck(model, runtime,
                "context Company inv HasJack: self.employee->exists(p | p.firstName = 'Jack')",
                "Company");
        assertDualCheck(model, runtime,
                "context Company inv WorkingAgeOnly: self.employee->forAll(p | p.age <= 65)",
                "Company");
        assertDualCheck(model, runtime,
                "context Person inv NoEmployer: self.employer->isEmpty()",
                "Person");
    }

    @Test
    void optimizedIrMatchesJavaEvaluationForIncludesAndExcludes() {
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
                    Family[1] role family
                    Person[*] role children
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        TestObject p1 = TestObject.object("p1", "Person").attribute("age", 20L);
        TestObject p2 = TestObject.object("p2", "Person").attribute("age", 15L);
        TestObject f1 = TestObject.object("f1", "Family").attribute("name", "Simpson,Flanders").link("children", p1, p2);
        p1.link("family", f1);
        p2.link("family", f1);

        TestRuntime runtime = new TestRuntime(List.of(f1, p1, p2));

        assertDualCheck(model, runtime,
                "context Family inv NameIncludesSimpson: self.name.split(',')->includes('Simpson')",
                "Family");
        assertDualCheck(model, runtime,
                "context Family inv SplitIncludesFirst: self.name.split(',')->includes(self.name.split(',')->at(1))",
                "Family");
        assertDualCheck(model, runtime,
                "context Family inv ChildrenExcludeNull: self.children->excludes(null)",
                "Family");
        assertDualCheck(model, runtime,
                "context Family inv NameIncludesAllSelf: self.name.split(',')->includesAll(self.name.split(','))",
                "Family");
        assertDualCheck(model, runtime,
                "context Family inv NameExcludesSemicolonSplit: self.name.split(',')->excludesAll(self.name.split(';'))",
                "Family");
    }

    @Test
    void optimizedIrMatchesJavaEvaluationForAnyAndOne() {
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
                    Family[1] role family
                    Person[*] role children
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        TestObject p1 = TestObject.object("p1", "Person").attribute("age", 20L).attribute("name", "Bart");
        TestObject p2 = TestObject.object("p2", "Person").attribute("age", 15L).attribute("name", "Lisa");
        TestObject p3 = TestObject.object("p3", "Person").attribute("age", 22L).attribute("name", "Maggie");
        TestObject f1 = TestObject.object("f1", "Family").link("children", p1, p2);
        TestObject f2 = TestObject.object("f2", "Family").link("children", p2, p3);
        p1.link("family", f1);
        p2.link("family", f1, f2);
        p3.link("family", f2);

        TestRuntime runtime = new TestRuntime(List.of(f1, f2, p1, p2, p3));

        assertDualCheck(model, runtime,
                "context Family inv HasNamedAdult: self.children->any(c | c.age >= 18).name.isDefined()",
                "Family");
        assertDualCheck(model, runtime,
                "context Family inv ExactlyOneMinor: self.children->one(c | c.age < 18)",
                "Family");
    }

    @Test
    void optimizedIrMatchesJavaEvaluationForFirstAndLast() {
        String spec = """
                model Demo
                class Family
                attributes
                    name : String
                end
                class Person
                attributes
                    age : Integer
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

        TestObject p1 = TestObject.object("p1", "Person").attribute("age", 20L).attribute("name", "Bart");
        TestObject p2 = TestObject.object("p2", "Person").attribute("age", 15L).attribute("name", "Lisa");
        TestObject f1 = TestObject.object("f1", "Family").attribute("name", "Simpson,Flanders").link("children", p1, p2);
        p1.link("family", f1);
        p2.link("family", f1);

        TestRuntime runtime = new TestRuntime(List.of(f1, p1, p2));

        assertDualCheck(model, runtime,
                "context Family inv FirstSplitDefined: self.name.split(',')->first().isDefined()",
                "Family");
        assertDualCheck(model, runtime,
                "context Family inv LastSplitDefined: self.name.split(',')->last().isDefined()",
                "Family");
    }

    @Test
    void optimizedIrMatchesJavaEvaluationForOrderedNavigationFirstAndLast() {
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

        TestObject l1 = TestObject.object("l1", "LineItem").attribute("price", 10L);
        TestObject l2 = TestObject.object("l2", "LineItem").attribute("price", 20L);
        TestObject invoice = TestObject.object("i1", "Invoice").link("lineItem", l1, l2);
        l1.link("invoice", invoice);
        l2.link("invoice", invoice);

        TestRuntime runtime = new TestRuntime(List.of(invoice, l1, l2));

        assertDualCheck(model, runtime,
                "context Invoice inv FirstLineItemPriceDefined: self.lineItem->first().price.isDefined()",
                "Invoice");
        assertDualCheck(model, runtime,
                "context Invoice inv LastLineItemPriceDefined: self.lineItem->last().price.isDefined()",
                "Invoice");
    }

    @Test
    void optimizedIrMatchesJavaEvaluationForUnionAndIntersection() {
        String spec = """
                model Demo
                class Family
                attributes
                    name : String
                end
                class Person
                attributes
                    age : Integer
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

        TestObject p1 = TestObject.object("p1", "Person").attribute("age", 20L).attribute("name", "Bart");
        TestObject p2 = TestObject.object("p2", "Person").attribute("age", 15L).attribute("name", "Lisa");
        TestObject p3 = TestObject.object("p3", "Person").attribute("age", 22L).attribute("name", "Maggie");
        TestObject f1 = TestObject.object("f1", "Family").attribute("name", "Bart,Lisa").link("children", p1, p2, p3);
        p1.link("family", f1);
        p2.link("family", f1);
        p3.link("family", f1);

        TestRuntime runtime = new TestRuntime(List.of(f1, p1, p2, p3));

        assertDualCheck(model, runtime,
                "context Family inv UnionNamesContainsBart: self.name.split(',')->union(self.name.split(';'))->includes('Bart')",
                "Family");
        assertDualCheck(model, runtime,
                "context Family inv AdultIntersectionNotEmpty: self.children->intersection(self.children->select(c | c.age >= 18))->notEmpty()",
                "Family");
    }

    @Test
    void optimizedIrMatchesJavaEvaluationForOrderedSetUnionAndIntersection() {
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

        TestObject l1 = TestObject.object("l1", "LineItem").attribute("price", 10L);
        TestObject l2 = TestObject.object("l2", "LineItem").attribute("price", 20L);
        TestObject invoice = TestObject.object("i1", "Invoice").link("lineItem", l1, l2);
        l1.link("invoice", invoice);
        l2.link("invoice", invoice);

        TestRuntime runtime = new TestRuntime(List.of(invoice, l1, l2));

        assertDualCheck(model, runtime,
                "context Invoice inv OrderedUnionFirstDefined: self.lineItem->union(self.lineItem)->first().price.isDefined()",
                "Invoice");
        assertDualCheck(model, runtime,
                "context Invoice inv OrderedIntersectionLastDefined: self.lineItem->intersection(self.lineItem)->last().price.isDefined()",
                "Invoice");
    }

    @Test
    void optimizedIrMatchesJavaEvaluationForCount() {
        String spec = """
                model Demo
                class Family
                attributes
                    name : String
                end
                class Person
                attributes
                    age : Integer
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

        TestObject p1 = TestObject.object("p1", "Person").attribute("age", 20L).attribute("name", "Bart");
        TestObject p2 = TestObject.object("p2", "Person").attribute("age", 15L).attribute("name", "Lisa");
        TestObject p3 = TestObject.object("p3", "Person").attribute("age", 22L).attribute("name", "Bart");
        TestObject f1 = TestObject.object("f1", "Family").attribute("name", "Bart,Bart,Lisa").link("children", p1, p2, p3);
        p1.link("family", f1);
        p2.link("family", f1);
        p3.link("family", f1);

        TestRuntime runtime = new TestRuntime(List.of(f1, p1, p2, p3));

        assertDualCheck(model, runtime,
                "context Family inv NameCountBart: self.name.split(',')->count('Bart') = 2",
                "Family");
        assertDualCheck(model, runtime,
                "context Family inv ChildCountOfAnyAdult: self.children->count(self.children->any(c | c.age >= 0)) >= 1",
                "Family");
    }

    @Test
    void optimizedIrMatchesJavaEvaluationForAsSet() {
        String spec = """
                model Demo
                class Family
                attributes
                    name : String
                end
                class Person
                attributes
                    age : Integer
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

        TestObject p1 = TestObject.object("p1", "Person").attribute("age", 20L).attribute("name", "Bart");
        TestObject p2 = TestObject.object("p2", "Person").attribute("age", 15L).attribute("name", "Lisa");
        TestObject f1 = TestObject.object("f1", "Family").attribute("name", "Bart,Bart,Lisa").link("children", p1, p2, p1);
        p1.link("family", f1);
        p2.link("family", f1);

        TestRuntime runtime = new TestRuntime(List.of(f1, p1, p2));

        assertDualCheck(model, runtime,
                "context Family inv SplitAsSetShrinks: self.name.split(',')->asSet()->size() = 2",
                "Family");
    }

    @Test
    void optimizedIrMatchesJavaEvaluationForAsOrderedSet() {
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

        TestObject family = TestObject.object("f1", "Family").attribute("name", "Bart,Bart,Lisa");
        TestRuntime runtime = new TestRuntime(List.of(family));

        assertDualCheck(model, runtime,
                "context Family inv OrderedUniqueFirstDefined: self.name.split(',')->asOrderedSet()->first().isDefined()",
                "Family");
        assertDualCheck(model, runtime,
                "context Family inv OrderedUniqueLastDefined: self.name.split(',')->asOrderedSet()->last().isDefined()",
                "Family");
    }

    @Test
    void optimizedIrMatchesJavaEvaluationForNullLet() {
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

        TestObject adult = TestObject.object("p1", "Person").attribute("name", "Bart").attribute("age", 20L);
        TestObject minor = TestObject.object("p2", "Person").attribute("name", "Lisa").attribute("age", 15L);
        TestRuntime runtime = new TestRuntime(List.of(adult, minor));

        assertDualCheck(model, runtime,
                "context Person inv NullLetStaysUsable: let fallback = null in fallback.isUndefined()",
                "Person");
    }

    @Test
    void optimizedIrMatchesJavaEvaluationForIfExpression() {
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

        TestObject adult = TestObject.object("p1", "Person").attribute("age", 20L).attribute("name", "Bart");
        TestObject child = TestObject.object("p2", "Person").attribute("age", 12L).attribute("name", "Lisa");
        TestRuntime runtime = new TestRuntime(List.of(adult, child));

        assertDualCheck(model, runtime,
                "context Person inv AdultKeepsName: if self.age >= 18 then self.name else 'minor' endif.isDefined()",
                "Person");
        assertDualCheck(model, runtime,
                "context Person inv MinorBranchUsed: if self.age >= 18 then self.name else 'minor' endif <> ''",
                "Person");
    }

    @Test
    void optimizedIrMatchesJavaEvaluationForIfExpressionWithVoidBranch() {
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

        TestObject adult = TestObject.object("p1", "Person").attribute("age", 20L).attribute("name", "Bart");
        TestObject child = TestObject.object("p2", "Person").attribute("age", 12L).attribute("name", "Lisa");
        TestRuntime runtime = new TestRuntime(List.of(adult, child));

        assertDualCheck(model, runtime,
                "context Person inv NullThenBranchTracksAdult: (if self.age >= 18 then null else self.name endif).isUndefined() = (self.age >= 18)",
                "Person");
        assertDualCheck(model, runtime,
                "context Person inv NullElseBranchTracksMinor: (if self.age >= 18 then self.name else null endif).isUndefined() = (self.age < 18)",
                "Person");
    }

    @Test
    void optimizedIrMatchesJavaEvaluationForLetExpression() {
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

        TestObject adult = TestObject.object("p1", "Person").attribute("age", 20L).attribute("name", "Bart");
        TestObject child = TestObject.object("p2", "Person").attribute("age", 12L).attribute("name", "Lisa");
        TestRuntime runtime = new TestRuntime(List.of(adult, child));

        assertDualCheck(model, runtime,
                "context Person inv AdultByLet: let threshold = 18 in self.age >= threshold",
                "Person");
        assertDualCheck(model, runtime,
                "context Person inv ScopedLetInIf: let fallback = 'minor' in if self.age >= 18 then self.name else fallback endif <> ''",
                "Person");
        assertDualCheck(model, runtime,
                "context Person inv NestedLetThreshold: let threshold = 18 in let bonus = threshold + 1 in self.age >= bonus",
                "Person");
        assertDualCheck(model, runtime,
                "context Person inv ShadowedLet: let limit = 18 in let limit = limit + 1 in self.age >= limit",
                "Person");
    }

    @Test
    void optimizedIrMatchesJavaEvaluationForFlatten() {
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
                    Family[1] role family
                    Person[*] role children
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        TestObject p1 = TestObject.object("p1", "Person").attribute("nicknames", "B,Bar");
        TestObject p2 = TestObject.object("p2", "Person").attribute("nicknames", "L");
        TestObject f1 = TestObject.object("f1", "Family").attribute("aliases", "Bart;Lisa;Bart").link("children", p1, p2);
        p1.link("family", f1);
        p2.link("family", f1);

        TestRuntime runtime = new TestRuntime(List.of(f1, p1, p2));

        assertDualCheck(model, runtime,
                "context Family inv FlattenedAliasCount: self.aliases.split(';')->collect(a | a.split(','))->flatten()->count('Bart') = 2",
                "Family");
        assertDualCheck(model, runtime,
                "context Family inv FlattenedFamilyIncludesSelf: self.children->collect(c | c.family)->flatten()->includes(self)",
                "Family");
    }

    @Test
    void optimizedIrMatchesJavaEvaluationForOrderedSetFlatten() {
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

        TestObject family = TestObject.object("f1", "Family").attribute("aliases", "Bart;Bart,Lisa");
        TestRuntime runtime = new TestRuntime(List.of(family));

        assertDualCheck(model, runtime,
                "context Family inv FlatOrderedAliases: self.aliases.split(';')->collect(a | a.split(','))->asOrderedSet()->flatten()->first().isDefined()",
                "Family");
        assertDualCheck(model, runtime,
                "context Family inv FlatOrderedUniqueCount: self.aliases.split(';')->collect(a | a.split(','))->asOrderedSet()->flatten()->count('Bart') = 1",
                "Family");
    }

    @Test
    void optimizedIrMatchesJavaEvaluationForSelfAssociationAndEmptyEdges() {
        String spec = """
                model Demo
                class Person
                attributes
                    age : Integer
                end
                association Parenthood between
                    Person[*] role parent
                    Person[*] role child
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        TestObject alice = TestObject.object("p1", "Person").attribute("age", 45L);
        TestObject bob = TestObject.object("p2", "Person").attribute("age", 18L);
        TestObject carol = TestObject.object("p3", "Person").attribute("age", 12L);
        TestObject dave = TestObject.object("p4", "Person").attribute("age", 33L);
        alice.link("child", bob, carol);
        bob.link("parent", alice);
        carol.link("parent", alice);

        TestRuntime runtime = new TestRuntime(List.of(alice, bob, carol, dave));

        assertDualCheck(model, runtime, "context Person inv AdultChildrenOnly: self.child->forAll(c | c.age >= 18)", "Person");
        assertDualCheck(model, runtime, "context Person inv HasParent: self.parent->notEmpty()", "Person");
        assertDualCheck(model, runtime, "context Person inv AnyMinorDefined: self.child->any(c | c.age < 18).isDefined()", "Person");
    }

    @Test
    void optimizedIrMatchesJavaEvaluationForInheritedNavigationAndAttributes() {
        String spec = """
                model Demo
                class Person
                attributes
                    age : Integer
                end
                class Employee < Person
                end
                class Company
                attributes
                    name : String
                end
                association CompanyStaff between
                    Company[*] role employer
                    Person[*] role staff
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        TestObject alice = TestObject.object("p1", "Employee").attribute("age", 22L);
        TestObject bob = TestObject.object("p2", "Employee").attribute("age", 16L);
        TestObject acme = TestObject.object("c1", "Company").attribute("name", "Acme").link("staff", alice, bob);
        alice.link("employer", acme);
        bob.link("employer", acme);

        TestRuntime runtime = new TestRuntime(List.of(acme, alice, bob));

        assertDualCheck(model, runtime, "context Employee inv AdultWorkersHaveEmployer: self.age >= 18 implies self.employer->notEmpty()", "Employee");
        assertDualCheck(model, runtime, "context Company inv StaffAdultsOnly: self.staff->forAll(s | s.age >= 18)", "Company");
    }

    private void assertDualCheck(MModel model, TestRuntime runtime, String ocl, String contextClass) {
        ASTNode ast = new ASTVisitor().visit(new OCLParser(new CommonTokenStream(new OCLLexer(CharStreams.fromString(ocl)))).oclFile());
        assertTrue(ast instanceof ASTContext);
        ASTContext context = (ASTContext) ast;

        OclSemanticBinder binder = new OclSemanticBinder(new OclMetamodelIndex(model));
        OclSemanticBinder.BoundContextInvariant boundInvariant = binder.bindContext(context);
        OclIr.InvariantQuery optimized = new OclIrOptimizer().optimizeInvariant(new OclIrBuilder().buildInvariant(boundInvariant));

        BoundEvaluator boundEvaluator = new BoundEvaluator(runtime);
        IrEvaluator irEvaluator = new IrEvaluator(runtime);

        Set<String> javaViolations = new LinkedHashSet<>();
        Set<String> compiledViolations = new LinkedHashSet<>();
        for (TestObject self : runtime.allInstances(contextClass)) {
            if (!toBoolean(boundEvaluator.evaluate(boundInvariant.expression(), runtime.scopeOf("self", self)))) {
                javaViolations.add(self.id);
            }
            if (!toBoolean(irEvaluator.evaluate(optimized.predicate(), runtime.scopeOf("self", self)))) {
                compiledViolations.add(self.id);
            }
        }

        assertEquals(javaViolations, compiledViolations, ocl);
    }

    private boolean toBoolean(Object value) {
        return value instanceof Boolean bool && bool;
    }

    private static final class BoundEvaluator {
        private final TestRuntime runtime;

        private BoundEvaluator(TestRuntime runtime) {
            this.runtime = runtime;
        }

        private Object evaluate(OclSemanticBinder.BoundExpression expression, Map<String, Object> scope) {
            if (expression instanceof OclSemanticBinder.BoundVariable variable) {
                return scope.get(variable.ast().name);
            }
            if (expression instanceof OclSemanticBinder.BoundLiteral literal) {
                return literal.value();
            }
            if (expression instanceof OclSemanticBinder.BoundNot not) {
                return !toBooleanValue(evaluate(not.expression(), scope));
            }
            if (expression instanceof OclSemanticBinder.BoundIf ifExpression) {
                return toBooleanValue(evaluate(ifExpression.condition(), scope))
                        ? evaluate(ifExpression.thenBranch(), scope)
                        : evaluate(ifExpression.elseBranch(), scope);
            }
            if (expression instanceof OclSemanticBinder.BoundLet letExpression) {
                Map<String, Object> nested = new LinkedHashMap<>(scope);
                nested.put(letExpression.ast().variableName, evaluate(letExpression.value(), scope));
                return evaluate(letExpression.body(), nested);
            }
            if (expression instanceof OclSemanticBinder.BoundBinary binary) {
                return evaluateBinary(binary.ast().op, evaluate(binary.left(), scope), evaluate(binary.right(), scope));
            }
            if (expression instanceof OclSemanticBinder.BoundProperty property) {
                Object source = evaluate(property.source(), scope);
                return property.isAttribute()
                        ? runtime.resolveAttribute(source, property.ast().name)
                        : runtime.resolveNavigation(source, property.navigation().roleName());
            }
            if (expression instanceof OclSemanticBinder.BoundMethodCall methodCall) {
                Object source = evaluate(methodCall.source(), scope);
                return evaluateMethod(methodCall.ast().methodName, source, methodCall.arguments(), scope);
            }
            if (expression instanceof OclSemanticBinder.BoundCollectionOperation collectionOperation) {
                Object source = evaluate(collectionOperation.source(), scope);
                return evaluateCollectionOperation(collectionOperation.ast().opName, source, collectionOperation.arguments(), scope);
            }
            if (expression instanceof OclSemanticBinder.BoundIterator iterator) {
                return evaluateIterator(iterator.ast().operation, evaluate(iterator.source(), scope), iterator.ast().iteratorName, iterator.body(), scope);
            }
            throw new IllegalStateException(expression.getClass().getName());
        }

        private Object evaluateMethod(String name, Object source, List<OclSemanticBinder.BoundExpression> arguments, Map<String, Object> scope) {
            if ("isDefined".equalsIgnoreCase(name)) {
                return source != null;
            }
            if ("isUndefined".equalsIgnoreCase(name)) {
                return source == null;
            }
            if ("split".equalsIgnoreCase(name)) {
                String delimiter = String.valueOf(evaluate(arguments.get(0), scope));
                return source == null ? List.of() : List.of(String.valueOf(source).split(java.util.regex.Pattern.quote(delimiter)));
            }
            throw new IllegalStateException(name);
        }

        private Object evaluateCollectionOperation(String name, Object source, List<OclSemanticBinder.BoundExpression> arguments, Map<String, Object> scope) {
            List<?> values = runtime.toList(source);
            return switch (name) {
                case "size" -> (long) values.size();
                case "count" -> runtime.toList(source).stream().filter(value ->
                        Objects.equals(normalizeNumber(value), normalizeNumber(evaluate(arguments.get(0), scope)))).count();
                case "isEmpty" -> values.isEmpty();
                case "notEmpty" -> !values.isEmpty();
                case "includes" -> values.stream().anyMatch(value ->
                        Objects.equals(normalizeNumber(value), normalizeNumber(evaluate(arguments.get(0), scope))));
                case "excludes" -> values.stream().noneMatch(value ->
                        Objects.equals(normalizeNumber(value), normalizeNumber(evaluate(arguments.get(0), scope))));
                case "includesAll" -> runtime.toList(evaluate(arguments.get(0), scope)).stream().allMatch(candidate ->
                        values.stream().anyMatch(value -> Objects.equals(normalizeNumber(value), normalizeNumber(candidate))));
                case "excludesAll" -> runtime.toList(evaluate(arguments.get(0), scope)).stream().noneMatch(candidate ->
                        values.stream().anyMatch(value -> Objects.equals(normalizeNumber(value), normalizeNumber(candidate))));
                case "union" -> {
                    List<Object> result = new ArrayList<>(values);
                    for (Object candidate : runtime.toList(evaluate(arguments.get(0), scope))) {
                        boolean present = result.stream().anyMatch(value ->
                                Objects.equals(normalizeNumber(value), normalizeNumber(candidate)));
                        if (!present) {
                            result.add(candidate);
                        }
                    }
                    yield result;
                }
                case "asSet" -> {
                    List<Object> result = new ArrayList<>();
                    for (Object value : values) {
                        boolean present = result.stream().anyMatch(existing ->
                                Objects.equals(normalizeNumber(existing), normalizeNumber(value)));
                        if (!present) {
                            result.add(value);
                        }
                    }
                    yield result;
                }
                case "asOrderedSet" -> {
                    List<Object> result = new ArrayList<>();
                    for (Object value : values) {
                        boolean present = result.stream().anyMatch(existing ->
                                Objects.equals(normalizeNumber(existing), normalizeNumber(value)));
                        if (!present) {
                            result.add(value);
                        }
                    }
                    yield result;
                }
                case "flatten" -> flatten(values);
                case "intersection" -> {
                    List<?> candidates = runtime.toList(evaluate(arguments.get(0), scope));
                    List<Object> result = new ArrayList<>();
                    for (Object value : values) {
                        boolean present = candidates.stream().anyMatch(candidate ->
                                Objects.equals(normalizeNumber(value), normalizeNumber(candidate)));
                        if (present) {
                            result.add(value);
                        }
                    }
                    yield result;
                }
                case "first" -> values.isEmpty() ? null : values.get(0);
                case "last" -> values.isEmpty() ? null : values.get(values.size() - 1);
                case "at" -> values.get(((Number) evaluate(arguments.get(0), scope)).intValue() - 1);
                default -> throw new IllegalStateException(name);
            };
        }

        private Object evaluateIterator(String operation, Object source, String iteratorName,
                                        OclSemanticBinder.BoundExpression body, Map<String, Object> scope) {
            List<?> values = runtime.toList(source);
            return switch (operation.toLowerCase()) {
                case "select" -> {
                    List<Object> result = new ArrayList<>();
                    for (Object value : values) {
                        Map<String, Object> nested = new LinkedHashMap<>(scope);
                        nested.put(iteratorName, value);
                        if (toBooleanValue(evaluate(body, nested))) {
                            result.add(value);
                        }
                    }
                    yield result;
                }
                case "exists" -> {
                    boolean found = false;
                    for (Object value : values) {
                        Map<String, Object> nested = new LinkedHashMap<>(scope);
                        nested.put(iteratorName, value);
                        if (toBooleanValue(evaluate(body, nested))) {
                            found = true;
                            break;
                        }
                    }
                    yield found;
                }
                case "one" -> {
                    long count = 0;
                    for (Object value : values) {
                        Map<String, Object> nested = new LinkedHashMap<>(scope);
                        nested.put(iteratorName, value);
                        if (toBooleanValue(evaluate(body, nested))) {
                            count++;
                        }
                    }
                    yield count == 1;
                }
                case "any" -> {
                    Object found = null;
                    for (Object value : values) {
                        Map<String, Object> nested = new LinkedHashMap<>(scope);
                        nested.put(iteratorName, value);
                        if (toBooleanValue(evaluate(body, nested))) {
                            found = value;
                            break;
                        }
                    }
                    yield found;
                }
                case "forall" -> {
                    boolean ok = true;
                    for (Object value : values) {
                        Map<String, Object> nested = new LinkedHashMap<>(scope);
                        nested.put(iteratorName, value);
                        if (!toBooleanValue(evaluate(body, nested))) {
                            ok = false;
                            break;
                        }
                    }
                    yield ok;
                }
                case "collect" -> {
                    List<Object> result = new ArrayList<>();
                    for (Object value : values) {
                        Map<String, Object> nested = new LinkedHashMap<>(scope);
                        nested.put(iteratorName, value);
                        result.add(evaluate(body, nested));
                    }
                    yield result;
                }
                default -> throw new IllegalStateException(operation);
            };
        }
    }

    private static final class IrEvaluator {
        private final TestRuntime runtime;

        private IrEvaluator(TestRuntime runtime) {
            this.runtime = runtime;
        }

        private Object evaluate(OclIr.Expression expression, Map<String, Object> scope) {
            if (expression instanceof OclIr.Variable variable) {
                return scope.get(variable.name());
            }
            if (expression instanceof OclIr.Literal literal) {
                return literal.value();
            }
            if (expression instanceof OclIr.Not not) {
                return !toBooleanValue(evaluate(not.expression(), scope));
            }
            if (expression instanceof OclIr.If ifExpression) {
                return toBooleanValue(evaluate(ifExpression.condition(), scope))
                        ? evaluate(ifExpression.thenBranch(), scope)
                        : evaluate(ifExpression.elseBranch(), scope);
            }
            if (expression instanceof OclIr.Let letExpression) {
                Map<String, Object> nested = new LinkedHashMap<>(scope);
                nested.put(letExpression.variableName(), evaluate(letExpression.value(), scope));
                return evaluate(letExpression.body(), nested);
            }
            if (expression instanceof OclIr.Binary binary) {
                return evaluateBinary(binary.operator(), evaluate(binary.left(), scope), evaluate(binary.right(), scope));
            }
            if (expression instanceof OclIr.AttributeAccess attributeAccess) {
                return runtime.resolveAttribute(evaluate(attributeAccess.source(), scope), attributeAccess.attributeName());
            }
            if (expression instanceof OclIr.NavigationAccess navigationAccess) {
                return runtime.resolveNavigation(evaluate(navigationAccess.source(), scope), navigationAccess.navigation().roleName());
            }
            if (expression instanceof OclIr.MethodCall methodCall) {
                return evaluateMethod(methodCall.methodName(), evaluate(methodCall.source(), scope), methodCall.arguments(), scope);
            }
            if (expression instanceof OclIr.CollectionOperation collectionOperation) {
                return evaluateCollectionOperation(collectionOperation.operationName(),
                        evaluate(collectionOperation.source(), scope), collectionOperation.arguments(), scope);
            }
            if (expression instanceof OclIr.IteratorOperation iteratorOperation) {
                return evaluateIterator(iteratorOperation.operationName(), evaluate(iteratorOperation.source(), scope),
                        iteratorOperation.iteratorName(), iteratorOperation.body(), scope);
            }
            if (expression instanceof OclIr.NavigationPredicateCheck predicateCheck) {
                List<?> values = runtime.toList(runtime.resolveNavigation(
                        evaluate(predicateCheck.navigation().source(), scope), predicateCheck.navigation().navigation().roleName()));
                return switch (predicateCheck.kind()) {
                    case EXISTS -> values.stream().anyMatch(value -> predicateCheck.predicate() == null
                            || toBooleanValue(evaluate(predicateCheck.predicate(), runtime.childScope(scope, predicateCheck.iteratorName(), value))));
                    case NOT_EXISTS -> values.stream().noneMatch(value -> predicateCheck.predicate() == null
                            || toBooleanValue(evaluate(predicateCheck.predicate(), runtime.childScope(scope, predicateCheck.iteratorName(), value))));
                    case FORALL -> values.stream().allMatch(value ->
                            toBooleanValue(evaluate(predicateCheck.predicate(), runtime.childScope(scope, predicateCheck.iteratorName(), value))));
                };
            }
            if (expression instanceof OclIr.NavigationCountComparison countComparison) {
                List<?> values = runtime.toList(runtime.resolveNavigation(
                        evaluate(countComparison.navigation().source(), scope), countComparison.navigation().navigation().roleName()));
                long count = values.stream().filter(value -> countComparison.predicate() == null
                        || toBooleanValue(evaluate(countComparison.predicate(), runtime.childScope(scope, countComparison.iteratorName(), value)))).count();
                return evaluateBinary(countComparison.operator(), count, countComparison.literal());
            }
            throw new IllegalStateException(expression.getClass().getName());
        }

        private Object evaluateMethod(String name, Object source, List<OclIr.Expression> arguments, Map<String, Object> scope) {
            if ("isDefined".equalsIgnoreCase(name)) {
                return source != null;
            }
            if ("isUndefined".equalsIgnoreCase(name)) {
                return source == null;
            }
            if ("split".equalsIgnoreCase(name)) {
                String delimiter = String.valueOf(evaluate(arguments.get(0), scope));
                return source == null ? List.of() : List.of(String.valueOf(source).split(java.util.regex.Pattern.quote(delimiter)));
            }
            throw new IllegalStateException(name);
        }

        private Object evaluateCollectionOperation(String name, Object source, List<OclIr.Expression> arguments, Map<String, Object> scope) {
            List<?> values = runtime.toList(source);
            return switch (name) {
                case "size" -> (long) values.size();
                case "count" -> runtime.toList(source).stream().filter(value ->
                        Objects.equals(normalizeNumber(value), normalizeNumber(evaluate(arguments.get(0), scope)))).count();
                case "isEmpty" -> values.isEmpty();
                case "notEmpty" -> !values.isEmpty();
                case "includes" -> values.stream().anyMatch(value ->
                        Objects.equals(normalizeNumber(value), normalizeNumber(evaluate(arguments.get(0), scope))));
                case "excludes" -> values.stream().noneMatch(value ->
                        Objects.equals(normalizeNumber(value), normalizeNumber(evaluate(arguments.get(0), scope))));
                case "includesAll" -> runtime.toList(evaluate(arguments.get(0), scope)).stream().allMatch(candidate ->
                        values.stream().anyMatch(value -> Objects.equals(normalizeNumber(value), normalizeNumber(candidate))));
                case "excludesAll" -> runtime.toList(evaluate(arguments.get(0), scope)).stream().noneMatch(candidate ->
                        values.stream().anyMatch(value -> Objects.equals(normalizeNumber(value), normalizeNumber(candidate))));
                case "union" -> {
                    List<Object> result = new ArrayList<>(values);
                    for (Object candidate : runtime.toList(evaluate(arguments.get(0), scope))) {
                        boolean present = result.stream().anyMatch(value ->
                                Objects.equals(normalizeNumber(value), normalizeNumber(candidate)));
                        if (!present) {
                            result.add(candidate);
                        }
                    }
                    yield result;
                }
                case "asSet" -> {
                    List<Object> result = new ArrayList<>();
                    for (Object value : values) {
                        boolean present = result.stream().anyMatch(existing ->
                                Objects.equals(normalizeNumber(existing), normalizeNumber(value)));
                        if (!present) {
                            result.add(value);
                        }
                    }
                    yield result;
                }
                case "asOrderedSet" -> {
                    List<Object> result = new ArrayList<>();
                    for (Object value : values) {
                        boolean present = result.stream().anyMatch(existing ->
                                Objects.equals(normalizeNumber(existing), normalizeNumber(value)));
                        if (!present) {
                            result.add(value);
                        }
                    }
                    yield result;
                }
                case "flatten" -> flatten(values);
                case "intersection" -> {
                    List<?> candidates = runtime.toList(evaluate(arguments.get(0), scope));
                    List<Object> result = new ArrayList<>();
                    for (Object value : values) {
                        boolean present = candidates.stream().anyMatch(candidate ->
                                Objects.equals(normalizeNumber(value), normalizeNumber(candidate)));
                        if (present) {
                            result.add(value);
                        }
                    }
                    yield result;
                }
                case "first" -> values.isEmpty() ? null : values.get(0);
                case "last" -> values.isEmpty() ? null : values.get(values.size() - 1);
                case "at" -> values.get(((Number) evaluate(arguments.get(0), scope)).intValue() - 1);
                default -> throw new IllegalStateException(name);
            };
        }

        private Object evaluateIterator(String operation, Object source, String iteratorName, OclIr.Expression body,
                                        Map<String, Object> scope) {
            List<?> values = runtime.toList(source);
            return switch (operation.toLowerCase()) {
                case "select" -> {
                    List<Object> result = new ArrayList<>();
                    for (Object value : values) {
                        if (toBooleanValue(evaluate(body, runtime.childScope(scope, iteratorName, value)))) {
                            result.add(value);
                        }
                    }
                    yield result;
                }
                case "exists" -> values.stream().anyMatch(value ->
                        toBooleanValue(evaluate(body, runtime.childScope(scope, iteratorName, value))));
                case "one" -> values.stream().filter(value ->
                        toBooleanValue(evaluate(body, runtime.childScope(scope, iteratorName, value)))).count() == 1;
                case "any" -> values.stream().filter(value ->
                        toBooleanValue(evaluate(body, runtime.childScope(scope, iteratorName, value)))).findFirst().orElse(null);
                case "forall" -> values.stream().allMatch(value ->
                        toBooleanValue(evaluate(body, runtime.childScope(scope, iteratorName, value))));
                case "collect" -> {
                    List<Object> result = new ArrayList<>();
                    for (Object value : values) {
                        result.add(evaluate(body, runtime.childScope(scope, iteratorName, value)));
                    }
                    yield result;
                }
                default -> throw new IllegalStateException(operation);
            };
        }
    }

    private static Object evaluateBinary(String operator, Object left, Object right) {
        if ("=".equals(operator)) {
            return Objects.equals(normalizeNumber(left), normalizeNumber(right));
        }
        if ("<>".equals(operator)) {
            return !Objects.equals(normalizeNumber(left), normalizeNumber(right));
        }
        if ("and".equals(operator)) {
            return toBooleanValue(left) && toBooleanValue(right);
        }
        if ("or".equals(operator)) {
            return toBooleanValue(left) || toBooleanValue(right);
        }
        if ("implies".equals(operator)) {
            return !toBooleanValue(left) || toBooleanValue(right);
        }
        if (">".equals(operator) || "<".equals(operator) || ">=".equals(operator) || "<=".equals(operator)) {
            double l = ((Number) left).doubleValue();
            double r = ((Number) right).doubleValue();
            return switch (operator) {
                case ">" -> l > r;
                case "<" -> l < r;
                case ">=" -> l >= r;
                case "<=" -> l <= r;
                default -> false;
            };
        }
        if ("+".equals(operator) || "-".equals(operator) || "*".equals(operator) || "/".equals(operator)) {
            double l = ((Number) left).doubleValue();
            double r = ((Number) right).doubleValue();
            return switch (operator) {
                case "+" -> l + r;
                case "-" -> l - r;
                case "*" -> l * r;
                case "/" -> l / r;
                default -> 0D;
            };
        }
        throw new IllegalStateException(operator);
    }

    private static boolean toBooleanValue(Object value) {
        return value instanceof Boolean bool && bool;
    }

    private static Object normalizeNumber(Object value) {
        return value instanceof Number number ? number.doubleValue() : value;
    }

    private static List<Object> flatten(Collection<?> collection) {
        List<Object> result = new ArrayList<>();
        for (Object item : collection) {
            if (item instanceof Collection<?> nested) {
                result.addAll(flatten(nested));
            } else if (item != null) {
                result.add(item);
            }
        }
        return result;
    }

    private static final class TestRuntime {
        private final Map<String, List<TestObject>> byClass = new LinkedHashMap<>();

        private TestRuntime(List<TestObject> objects) {
            for (TestObject object : objects) {
                byClass.computeIfAbsent(object.className, ignored -> new ArrayList<>()).add(object);
            }
        }

        private List<TestObject> allInstances(String className) {
            return byClass.getOrDefault(className, List.of());
        }

        private Object resolveAttribute(Object source, String attributeName) {
            if (source instanceof Collection<?> collection) {
                List<Object> values = new ArrayList<>();
                for (Object value : collection) {
                    Object resolved = resolveAttribute(value, attributeName);
                    if (resolved != null) {
                        values.add(resolved);
                    }
                }
                return values.size() == 1 ? values.get(0) : values;
            }
            return source instanceof TestObject object ? object.attributes.get(attributeName) : null;
        }

        private List<Object> resolveNavigation(Object source, String roleName) {
            if (source instanceof Collection<?> collection) {
                List<Object> result = new ArrayList<>();
                for (Object item : collection) {
                    result.addAll(resolveNavigation(item, roleName));
                }
                return result;
            }
            if (source instanceof TestObject object) {
                return new ArrayList<>(object.links.getOrDefault(roleName, List.of()));
            }
            return List.of();
        }

        private List<?> toList(Object source) {
            if (source == null) {
                return List.of();
            }
            if (source instanceof List<?> list) {
                return list;
            }
            if (source instanceof Collection<?> collection) {
                return new ArrayList<>(collection);
            }
            return List.of(source);
        }

        private Map<String, Object> scopeOf(String name, Object value) {
            Map<String, Object> scope = new LinkedHashMap<>();
            scope.put(name, value);
            return scope;
        }

        private Map<String, Object> childScope(Map<String, Object> scope, String name, Object value) {
            Map<String, Object> child = new LinkedHashMap<>(scope);
            child.put(name, value);
            return child;
        }
    }

    private static final class TestObject {
        private final String id;
        private final String className;
        private final Map<String, Object> attributes = new LinkedHashMap<>();
        private final Map<String, List<TestObject>> links = new LinkedHashMap<>();

        private TestObject(String id, String className) {
            this.id = id;
            this.className = className;
        }

        private static TestObject object(String id, String className) {
            return new TestObject(id, className);
        }

        private TestObject attribute(String name, Object value) {
            attributes.put(name, value);
            return this;
        }

        private TestObject link(String roleName, TestObject... targets) {
            links.put(roleName, new ArrayList<>(List.of(targets)));
            return this;
        }
    }
}
