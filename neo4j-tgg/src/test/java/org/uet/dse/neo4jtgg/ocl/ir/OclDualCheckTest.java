package org.uet.dse.neo4jtgg.ocl.ir;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.jupiter.api.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.uet.dse.neo4j.OCLLexer;
import org.uet.dse.neo4j.OCLParser;
import org.uet.dse.neo4j.oclite.ast.ASTFile;
import org.uet.dse.neo4j.oclite.ast.ASTContext;
import org.uet.dse.neo4j.oclite.ast.ASTNode;
import org.uet.dse.neo4j.oclite.ast.ASTVisitor;
import org.uet.dse.neo4j.sync.helper.CanonicalScalarValueCodec;
import org.uet.dse.neo4jtgg.ocl.OclMetamodelIndex;
import org.uet.dse.neo4jtgg.ocl.OclSemanticBinder;
import org.uet.dse.neo4jtgg.ocl.diagnostic.OclCodedUnsupportedOperationException;

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
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OclDualCheckTest {
    @Test
    void optimizedIrMatchesJavaEvaluationForCertifiedSetExtensions() {
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

        TestObject adult = TestObject.object("p1", "Person").attribute("age", 30L);
        TestObject senior = TestObject.object("p2", "Person").attribute("age", 70L);
        TestRuntime runtime = new TestRuntime(List.of(adult, senior));

        assertDualCheck(model, runtime,
                "context Person inv ExactlyOneAgeBand: (self.age >= 18) xor (self.age >= 65)", "Person");
        assertDualCheck(model, runtime,
                "context Person inv SetLiteralDistinct: Set{1, 1, 2}->size() = 2", "Person");
        assertDualCheck(model, runtime,
                "context Person inv SetUnion: Set{1, 2}->union(Set{2, 3})->includesAll(Set{1, 2, 3})", "Person");
        assertDualCheck(model, runtime,
                "context Person inv SetIntersection: Set{1, 2}->intersection(Set{2, 3})->includes(2) and Set{1, 2}->intersection(Set{2, 3})->size() = 1", "Person");
        assertDualCheck(model, runtime,
                "context Person inv SetConversion: Set{1, 1, 2}->asSet()->size() = 2", "Person");
        assertDualCheck(model, runtime,
                "context Person inv UniqueProjection: Set{1, 2}->isUnique(x | x)", "Person");
        assertDualCheck(model, runtime,
                "context Person inv BottomSetDistinct: Set{1, null, null}->size() = 2", "Person");
        assertDualCheck(model, runtime,
                "context Person inv BottomMembership: Set{1, null}->includes(null)", "Person");
        assertDualCheck(model, runtime,
                "context Person inv BottomProjectionNotUnique: not Set{1, 2}->isUnique(x | null)", "Person");
        assertDualCheck(model, runtime,
                "context Person inv NumericSetJoin: Set{1, 2.0}->includes(1.0)", "Person");
        assertDualCheck(model, runtime,
                "context Person inv EmptySourceUnique: Set{1}->select(x | false)->isUnique(x | x)", "Person");
        assertDualCheck(model, runtime,
                "context Person inv NestedShadowing: Set{1, 2}->forAll(x | Set{2, 3}->exists(x | x >= 2))", "Person");
    }

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
    void optimizedIrMatchesJavaEvaluationForPreservationRewriteRules() {
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
        TestObject f1 = TestObject.object("f1", "Family").attribute("name", "Smith").link("children", p1, p2);
        TestObject f2 = TestObject.object("f2", "Family").attribute("name", "Empty");
        p1.link("family", f1);
        p2.link("family", f1);

        TestRuntime runtime = new TestRuntime(List.of(f1, f2, p1, p2));

        assertDualCheck(model, runtime, "context Family inv RewriteNotEmpty: self.children->notEmpty()", "Family");
        assertDualCheck(model, runtime, "context Family inv RewriteIsEmpty: self.children->isEmpty()", "Family");
        assertDualCheck(model, runtime, "context Family inv RewriteSizePositive: self.children->size() > 0", "Family");
        assertDualCheck(model, runtime, "context Family inv RewriteSizeZero: self.children->size() = 0", "Family");
        assertDualCheck(model, runtime, "context Family inv RewriteForAll: self.children->forAll(c | c.age >= 18)", "Family");
        assertDualCheck(model, runtime, "context Family inv RewriteImplies: self.name = 'Empty' implies self.children->isEmpty()", "Family");
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
        assertDualCheck(model, runtime,
                "context Company inv SeniorNonJackExists: self.employee->select(a | a.age > 35)->reject(b | b.firstName = 'Jack')->exists(c | c.firstName = 'Tom')",
                "Company");
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
        assertDualCheck(model, runtime,
                "context Family inv AddedBartPresent: self.name.split(',')->including('Bart')->includes('Bart')",
                "Family");
        assertDualCheck(model, runtime,
                "context Family inv RemovedSimpsonGone: self.name.split(',')->excluding('Simpson')->excludes('Simpson')",
                "Family");
    }

    @Test
    void optimizedIrMatchesJavaEvaluationForAppendAndPrepend() {
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

        TestObject f1 = TestObject.object("f1", "Family").attribute("name", "Simpson,Flanders");
        TestRuntime runtime = new TestRuntime(List.of(f1));

        assertDualCheck(model, runtime,
                "context Family inv AppendedBartPresent: self.name.split(',')->append('Bart')->includes('Bart')",
                "Family");
        assertDualCheck(model, runtime,
                "context Family inv PrependedBartFirst: self.name.split(',')->prepend('Bart')->first() = 'Bart'",
                "Family");
        assertDualCheck(model, runtime,
                "context Family inv OrderedUniquePrependedBartFirst: self.name.split(',')->asOrderedSet()->prepend('Bart')->first() = 'Bart'",
                "Family");
        assertDualCheck(model, runtime,
                "context Family inv OrderedUniqueAppendedBartLast: self.name.split(',')->asOrderedSet()->append('Bart')->last().isDefined()",
                "Family");
    }

    @Test
    void optimizedIrMatchesJavaEvaluationForSubSequence() {
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

        TestObject f1 = TestObject.object("f1", "Family").attribute("name", "Simpson,Flanders,Bart");
        TestRuntime runtime = new TestRuntime(List.of(f1));

        assertDualCheck(model, runtime,
                "context Family inv FirstTwoContainFlanders: self.name.split(',')->subSequence(1, 2)->includes('Flanders')",
                "Family");
        assertDualCheck(model, runtime,
                "context Family inv LastTwoStartAtFlanders: self.name.split(',')->subSequence(2, 3)->first() = 'Flanders'",
                "Family");
        assertDualCheck(model, runtime,
                "context Family inv OrderedUniqueSubSequenceStartsAtSimpson: self.name.split(',')->asOrderedSet()->subSequence(1, 2)->first() = 'Simpson'",
                "Family");
    }

    @Test
    void optimizedIrMatchesJavaEvaluationForSortedBy() {
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

        TestObject f1 = TestObject.object("f1", "Family").attribute("name", "Simpson,Flanders,Bart");
        TestRuntime runtime = new TestRuntime(List.of(f1));

        assertDualCheck(model, runtime,
                "context Family inv SortedNamesStartWithBart: self.name.split(',')->sortedBy(token | token)->first() = 'Bart'",
                "Family");
        assertDualCheck(model, runtime,
                "context Family inv SortedNamesEndWithSimpson: self.name.split(',')->sortedBy(token | token)->last() = 'Simpson'",
                "Family");
        assertDualCheck(model, runtime,
                "context Family inv SortedUniqueNamesStartWithBart: self.name.split(',')->asSet()->sortedBy(token | token)->first() = 'Bart'",
                "Family");
    }

    @Test
    void optimizedIrMatchesJavaEvaluationForOclAsType() {
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

        TestObject employee = TestObject.object("e1", "Employee").attribute("salary", 100L);
        TestRuntime runtime = new TestRuntime(List.of(employee));

        assertDualCheck(model, runtime,
                "context Employee inv SalaryVisibleAfterCast: self.oclAsType(Employee).salary = 100",
                "Employee");
        assertDualCheck(model, runtime,
                "context Employee inv WrongCastBecomesUndefined: self.oclAsType(Manager).isUndefined()",
                "Employee");
    }

    @Test
    void optimizedIrMatchesJavaEvaluationForQualifiedAssociationNavigationWithoutQualifierFilter() {
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

        TestObject b1 = TestObject.object("b1", "Book");
        TestObject b2 = TestObject.object("b2", "Book");
        TestObject library = TestObject.object("l1", "Library").link("book", b1, b2);
        b1.link("library", library);
        b2.link("library", library);
        TestRuntime runtime = new TestRuntime(List.of(library, b1, b2));

        assertDualCheck(model, runtime,
                "context Library inv HasBooks: self.book->notEmpty()",
                "Library");
        assertDualCheck(model, runtime,
                "context Book inv HasLibrary: self.library->notEmpty()",
                "Book");
    }

    @Test
    void optimizedIrMatchesJavaEvaluationForQualifiedAssociationNavigationWithLiteralQualifierFilter() {
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

        TestObject b1 = TestObject.object("b1", "Book");
        TestObject b2 = TestObject.object("b2", "Book");
        TestObject library = TestObject.object("l1", "Library")
                .qualifiedLink("book", List.of("A1"), b1)
                .qualifiedLink("book", List.of("B2"), b2);
        b1.link("library", library);
        b2.link("library", library);
        TestRuntime runtime = new TestRuntime(List.of(library, b1, b2));

        assertDualCheck(model, runtime,
                "context Library inv ShelfA1Exists: self.book['A1']->notEmpty()",
                "Library");
        assertDualCheck(model, runtime,
                "context Library inv ShelfC3Missing: self.book['C3']->isEmpty()",
                "Library");
    }

    @Test
    void optimizedIrMatchesJavaEvaluationForQualifiedAssociationNavigationWithVariableQualifierFilter() {
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

        TestObject b1 = TestObject.object("b1", "Book");
        TestObject b2 = TestObject.object("b2", "Book");
        TestObject library = TestObject.object("l1", "Library")
                .attribute("defaultShelf", "A1")
                .qualifiedLink("book", List.of("A1"), b1)
                .qualifiedLink("book", List.of("B2"), b2);
        b1.link("library", library);
        b2.link("library", library);
        TestRuntime runtime = new TestRuntime(List.of(library, b1, b2));

        assertDualCheck(model, runtime,
                "context Library inv ShelfViaVariable: let shelf = self.defaultShelf in self.book[shelf]->notEmpty()",
                "Library");
    }

    @Test
    void optimizedIrMatchesJavaEvaluationForQualifiedAssociationNavigationWithComputedQualifierFilter() {
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

        TestObject b1 = TestObject.object("b1", "Book");
        TestObject b2 = TestObject.object("b2", "Book");
        TestObject library = TestObject.object("l1", "Library")
                .attribute("defaultShelf", "A1")
                .qualifiedLink("book", List.of("A1"), b1)
                .qualifiedLink("book", List.of("B2"), b2);
        b1.link("library", library);
        b2.link("library", library);
        TestRuntime runtime = new TestRuntime(List.of(library, b1, b2));

        assertDualCheck(model, runtime,
                "context Library inv ShelfViaExpression: self.book[self.defaultShelf.concat('')]->notEmpty()",
                "Library");
    }

    @Test
    void optimizedIrMatchesJavaEvaluationForQualifiedAssociationNavigationWithEnumLiteralQualifierFilter() {
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

        TestObject b1 = TestObject.object("b1", "Book");
        TestObject b2 = TestObject.object("b2", "Book");
        TestObject library = TestObject.object("l1", "Library")
                .qualifiedLink("book", List.of("#A1"), b1)
                .qualifiedLink("book", List.of("#B2"), b2);
        b1.link("library", library);
        b2.link("library", library);
        TestRuntime runtime = new TestRuntime(List.of(library, b1, b2));

        assertDualCheck(model, runtime,
                "context Library inv ShelfViaEnum: self.book[Shelf::A1]->notEmpty()",
                "Library");
    }

    @Test
    void optimizedIrMatchesJavaEvaluationForQualifiedAssociationNavigationWithEnumAttributeQualifierFilter() {
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

        TestObject b1 = TestObject.object("b1", "Book");
        TestObject b2 = TestObject.object("b2", "Book");
        TestObject library = TestObject.object("l1", "Library")
                .attr("defaultShelf", "#A1")
                .qualifiedLink("book", List.of("#A1"), b1)
                .qualifiedLink("book", List.of("#B2"), b2);
        b1.link("library", library);
        b2.link("library", library);
        TestRuntime runtime = new TestRuntime(List.of(library, b1, b2));

        assertDualCheck(model, runtime,
                "context Library inv ShelfViaDefaultShelf: self.book[self.defaultShelf]->notEmpty()",
                "Library");
    }

    @Test
    void rejectsNonBinaryAssociationNavigationFromCertifiedFragment() {
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

        TestRuntime runtime = new TestRuntime(List.of(TestObject.object("p1", "Person")));
        org.junit.jupiter.api.Assertions.assertThrows(OclCodedUnsupportedOperationException.class,
                () -> assertDualCheck(model, runtime,
                        "context Person inv HasPets: self.pet->notEmpty()", "Person"));
    }

    @Test
    void optimizedIrMatchesJavaEvaluationForRedefiningAssociationNavigation() {
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

        TestObject startup = TestObject.object("s1", "Startup");
        TestObject employee = TestObject.object("e1", "Employee").link("startupEmployer", startup);
        startup.link("startupEmployee", employee);
        TestRuntime runtime = new TestRuntime(List.of(employee, startup));

        assertDualCheck(model, runtime,
                "context Employee inv HasStartupEmployer: self.startupEmployer->notEmpty()",
                "Employee");
        assertDualCheck(model, runtime,
                "context Startup inv HasStartupEmployees: self.startupEmployee->notEmpty()",
                "Startup");
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
    void optimizedIrMatchesJavaEvaluationForAggregates() {
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
                    Family[1] role family
                    Person[*] role children
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        TestObject p1 = TestObject.object("p1", "Person").attribute("age", 20L).attribute("score", 2.5D);
        TestObject p2 = TestObject.object("p2", "Person").attribute("age", 15L).attribute("score", 3.75D);
        TestObject f1 = TestObject.object("f1", "Family").link("children", p1, p2);
        TestObject f2 = TestObject.object("f2", "Family");
        p1.link("family", f1);
        p2.link("family", f1);

        TestRuntime runtime = new TestRuntime(List.of(f1, f2, p1, p2));

        assertDualCheck(model, runtime,
                "context Family inv TotalAgeExact: self.children->collect(c | c.age)->sum() = 35",
                "Family");
        assertDualCheck(model, runtime,
                "context Family inv MaxScoreAtLeast: if self.children->isEmpty() then self.children->collect(c | c.score)->max().isUndefined() else self.children->collect(c | c.score)->max() >= 3.75 endif",
                "Family");
        assertDualCheck(model, runtime,
                "context Family inv MinAgeUndefinedWhenEmpty: if self.children->isEmpty() then self.children->collect(c | c.age)->min().isUndefined() else self.children->collect(c | c.age)->min() >= 0 endif",
                "Family");
        assertDualCheck(model, runtime,
                "context Family inv EmptyTotalAgeIsZero: self.children->collect(c | c.age)->sum() >= 0",
                "Family");
        assertDualCheck(model, runtime,
                "context Family inv AdultScoresStayPositive: self.children->select(c | c.age >= 18)->collect(c | c.score)->sum() >= 2.5",
                "Family");
        assertDualCheck(model, runtime,
                "context Family inv AdultScoresStayPositiveRenamed: self.children->select(a | a.age >= 18)->collect(b | b.score)->sum() >= 2.5",
                "Family");
    }

    @Test
    void optimizedIrMatchesJavaEvaluationForIsUnique() {
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
                    Family[1] role family
                    Person[*] role children
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        TestObject p1 = TestObject.object("p1", "Person").attribute("name", "Bart").attribute("age", 20L);
        TestObject p2 = TestObject.object("p2", "Person").attribute("name", "Lisa").attribute("age", 15L);
        TestObject p3 = TestObject.object("p3", "Person").attribute("name", "Bart").attribute("age", 22L);
        TestObject f1 = TestObject.object("f1", "Family").link("children", p1, p2);
        TestObject f2 = TestObject.object("f2", "Family").link("children", p1, p3);
        TestObject f3 = TestObject.object("f3", "Family");
        p1.link("family", f1, f2);
        p2.link("family", f1);
        p3.link("family", f2);

        TestRuntime runtime = new TestRuntime(List.of(f1, f2, f3, p1, p2, p3));

        assertDualCheck(model, runtime,
                "context Family inv UniqueChildNames: self.children->isUnique(c | c.name)",
                "Family");
        assertDualCheck(model, runtime,
                "context Family inv UniqueChildAges: self.children->isUnique(c | c.age)",
                "Family");
        assertDualCheck(model, runtime,
                "context Family inv UniqueAdultNamesFiltered: self.children->select(c | c.age >= 18)->isUnique(c | c.name)",
                "Family");
    }

    @Test
    void optimizedIrMatchesJavaEvaluationForCollectionValuedPrimitiveAttribute() {
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

        TestObject p1 = TestObject.object("p1", "Person").attribute("aliases", List.of("Bart", "B"));
        TestObject p2 = TestObject.object("p2", "Person").attribute("aliases", List.of());
        TestObject p3 = TestObject.object("p3", "Person").attribute("aliases", List.of("Lisa"));
        TestRuntime runtime = new TestRuntime(List.of(p1, p2, p3));

        assertDualCheck(model, runtime,
                "context Person inv AliasCountNonNegative: self.aliases->count('Bart') >= 0",
                "Person");
        assertDualCheck(model, runtime,
                "context Person inv FirstAliasMaybeDefined: self.aliases->first().isDefined() or self.aliases->isEmpty()",
                "Person");
        assertDualCheck(model, runtime,
                "context Person inv AliasIncludesBartOrEmpty: self.aliases->includes('Bart') or self.aliases->isEmpty() or self.aliases->includes('Lisa')",
                "Person");
    }

    @Test
    void optimizedIrMatchesJavaEvaluationForCollectionValuedObjectReferenceAttribute() {
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

        TestObject p1 = TestObject.object("p1", "Person");
        TestObject p2 = TestObject.object("p2", "Person");
        TestObject p3 = TestObject.object("p3", "Person");
        p1.attribute("friends", List.of(p1, p2));
        p2.attribute("friends", List.of());
        p3.attribute("friends", List.of(p2));
        TestRuntime runtime = new TestRuntime(List.of(p1, p2, p3));

        assertDualCheck(model, runtime,
                "context Person inv HasSelfOrEmpty: self.friends->includes(self) or self.friends->isEmpty()",
                "Person");
        assertDualCheck(model, runtime,
                "context Person inv FriendCountNonNegative: self.friends->count(self) >= 0",
                "Person");
    }

    @Test
    void optimizedIrMatchesJavaEvaluationForNestedCollectionValuedAttributes() {
        String spec = """
                model Demo
                class Person
                attributes
                    aliases2d : Sequence(Sequence(String))
                    friendGroups : Sequence(Sequence(Person))
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        TestObject p1 = TestObject.object("p1", "Person");
        TestObject p2 = TestObject.object("p2", "Person");
        TestObject p3 = TestObject.object("p3", "Person");
        p1.attribute("aliases2d", List.of(List.of("Bart", "B"), List.of("Lisa")));
        p2.attribute("aliases2d", List.of());
        p3.attribute("aliases2d", List.of(List.of("Tom")));
        p1.attribute("friendGroups", List.of(List.of(p1, p2), List.of(p3)));
        p2.attribute("friendGroups", List.of());
        p3.attribute("friendGroups", List.of(List.of(p2)));
        TestRuntime runtime = new TestRuntime(List.of(p1, p2, p3));

        assertDualCheck(model, runtime,
                "context Person inv HasBartSomewhere: self.aliases2d->flatten()->count('Bart') >= 0",
                "Person");
        assertDualCheck(model, runtime,
                "context Person inv HasSelfSomewhereOrEmpty: self.friendGroups->flatten()->includes(self) or self.friendGroups->flatten()->isEmpty()",
                "Person");
    }

    @Test
    void optimizedIrMatchesJavaEvaluationForDeepNestedCollectionValuedAttributes() {
        String spec = """
                model Demo
                class Person
                attributes
                    aliases3d : Sequence(Sequence(Sequence(String)))
                    friendGroups3d : Sequence(Sequence(Sequence(Person)))
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        TestObject p1 = TestObject.object("p1", "Person");
        TestObject p2 = TestObject.object("p2", "Person");
        TestObject p3 = TestObject.object("p3", "Person");
        p1.attribute("aliases3d", List.of(List.of(List.of("Bart"), List.of("B")), List.of(List.of("Lisa"))));
        p2.attribute("aliases3d", List.of());
        p3.attribute("aliases3d", List.of(List.of(List.of("Tom"))));
        p1.attribute("friendGroups3d", List.of(List.of(List.of(p1), List.of(p2)), List.of(List.of(p3))));
        p2.attribute("friendGroups3d", List.of());
        p3.attribute("friendGroups3d", List.of(List.of(List.of(p2))));
        TestRuntime runtime = new TestRuntime(List.of(p1, p2, p3));

        assertDualCheck(model, runtime,
                "context Person inv HasBartSomewhere: self.aliases3d->flatten()->flatten()->count('Bart') >= 0",
                "Person");
        assertDualCheck(model, runtime,
                "context Person inv HasSelfSomewhereOrEmpty: self.friendGroups3d->flatten()->flatten()->includes(self) or self.friendGroups3d->flatten()->flatten()->isEmpty()",
                "Person");
    }

    @Test
    void optimizedIrMatchesJavaEvaluationForOrderedAndUniqueNestedCollections() {
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

        TestObject p1 = TestObject.object("p1", "Person");
        TestObject p2 = TestObject.object("p2", "Person");
        TestObject p3 = TestObject.object("p3", "Person");
        p1.attribute("aliases2d", List.of(List.of("Bart"), List.of("Bart"), List.of("Lisa")));
        p2.attribute("aliases2d", List.of());
        p3.attribute("aliases2d", List.of(List.of("Tom")));
        p1.attribute("friendGroups2d", List.of(List.of(p1, p2), List.of(p1, p2), List.of(p3)));
        p2.attribute("friendGroups2d", List.of());
        p3.attribute("friendGroups2d", List.of(List.of(p2)));
        TestRuntime runtime = new TestRuntime(List.of(p1, p2, p3));

        assertDualCheck(model, runtime,
                "context Person inv OrderedAliases: self.aliases2d->asOrderedSet()->flatten()->first().isDefined()",
                "Person");
        assertDualCheck(model, runtime,
                "context Person inv UniqueAliases: self.aliases2d->asSet()->flatten()->count('Bart') >= 0",
                "Person");
        assertDualCheck(model, runtime,
                "context Person inv UniqueFriends: self.friendGroups2d->asSet()->flatten()->count(self) >= 0",
                "Person");
    }

    @Test
    void optimizedIrMatchesJavaEvaluationForNestedCollectionSetOperations() {
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

        TestObject p1 = TestObject.object("p1", "Person").attribute("aliases2d", List.of(List.of("Bart"), List.of("Bart"), List.of("Lisa")));
        TestObject p2 = TestObject.object("p2", "Person").attribute("aliases2d", List.of());
        TestObject p3 = TestObject.object("p3", "Person").attribute("aliases2d", List.of(List.of("Tom")));
        TestRuntime runtime = new TestRuntime(List.of(p1, p2, p3));

        assertDualCheck(model, runtime,
                "context Person inv UniqueNestedAliases: self.aliases2d->flatten()->asSet()->union(self.aliases2d->flatten()->asSet())->count('Bart') >= 0",
                "Person");
        assertDualCheck(model, runtime,
                "context Person inv OrderedNestedAliases: self.aliases2d->flatten()->asOrderedSet()->intersection(self.aliases2d->flatten()->asOrderedSet())->first().isDefined() or self.aliases2d->flatten()->isEmpty()",
                "Person");
        assertDualCheck(model, runtime,
                "context Person inv NestedAliasesIncludeSelf: self.aliases2d->flatten()->includesAll(self.aliases2d->flatten())",
                "Person");
        assertDualCheck(model, runtime,
                "context Person inv NestedAliasesExcludeIntersection: self.aliases2d->flatten()->excludesAll(self.aliases2d->flatten()->intersection(self.aliases2d->flatten())) or self.aliases2d->flatten()->includesAll(self.aliases2d->flatten())",
                "Person");
    }

    @Test
    void optimizedIrMatchesJavaEvaluationForNestedObjectReferenceSetOperations() {
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

        TestObject p1 = TestObject.object("p1", "Person");
        TestObject p2 = TestObject.object("p2", "Person");
        TestObject p3 = TestObject.object("p3", "Person");
        p1.attribute("friendGroups2d", List.of(List.of(p1, p2), List.of(p1, p2), List.of(p3)));
        p2.attribute("friendGroups2d", List.of());
        p3.attribute("friendGroups2d", List.of(List.of(p2)));
        TestRuntime runtime = new TestRuntime(List.of(p1, p2, p3));

        assertDualCheck(model, runtime,
                "context Person inv UniqueNestedFriends: self.friendGroups2d->flatten()->asSet()->union(self.friendGroups2d->flatten()->asSet())->count(self) >= 0",
                "Person");
        assertDualCheck(model, runtime,
                "context Person inv OrderedNestedFriends: self.friendGroups2d->flatten()->asOrderedSet()->intersection(self.friendGroups2d->flatten()->asOrderedSet())->first().isDefined() or self.friendGroups2d->flatten()->isEmpty()",
                "Person");
        assertDualCheck(model, runtime,
                "context Person inv NestedFriendsIncludeSelf: self.friendGroups2d->flatten()->includesAll(self.friendGroups2d->flatten())",
                "Person");
        assertDualCheck(model, runtime,
                "context Person inv NestedFriendsExcludeIntersection: self.friendGroups2d->flatten()->excludesAll(self.friendGroups2d->flatten()->intersection(self.friendGroups2d->flatten())) or self.friendGroups2d->flatten()->includesAll(self.friendGroups2d->flatten())",
                "Person");
    }

    @Test
    void optimizedIrMatchesJavaEvaluationForNestedBagOperations() {
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

        TestObject p1 = TestObject.object("p1", "Person").attribute("aliases2d", List.of(List.of("Bart"), List.of("Bart"), List.of("Lisa")));
        TestObject p2 = TestObject.object("p2", "Person").attribute("aliases2d", List.of(List.of("Bart", "Bart")));
        TestObject p3 = TestObject.object("p3", "Person").attribute("aliases2d", List.of());
        TestRuntime runtime = new TestRuntime(List.of(p1, p2, p3));

        assertDualCheck(model, runtime,
                "context Person inv BagNestedAliases: self.aliases2d->flatten()->asBag()->union(self.aliases2d->flatten()->asBag())->count('Bart') >= 2",
                "Person");
        assertDualCheck(model, runtime,
                "context Person inv SharedNestedAliases: self.aliases2d->flatten()->asBag()->intersection(self.aliases2d->flatten()->asBag())->count('Bart') >= 2",
                "Person");
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
        assertTrue(ast instanceof ASTFile);
        ASTFile file = (ASTFile) ast;
        assertEquals(1, file.invariants().size());
        ASTContext context = file.invariants().get(0);

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
            if (expression instanceof OclSemanticBinder.BoundSetLiteral setLiteral) {
                List<Object> values = new ArrayList<>();
                for (OclSemanticBinder.BoundExpression element : setLiteral.elements()) {
                    Object value = evaluate(element, scope);
                    if (values.stream().noneMatch(existing ->
                            Objects.equals(normalizeNumber(existing), normalizeNumber(value)))) {
                        values.add(value);
                    }
                }
                return values;
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
                        : runtime.resolveNavigation(source, property.navigation().roleName(),
                        evaluateQualifiers(property.qualifiers(), scope));
            }
            if (expression instanceof OclSemanticBinder.BoundMethodCall methodCall) {
                Object source = evaluate(methodCall.source(), scope);
                return evaluateMethod(methodCall.ast().methodName, source, methodCall.arguments(), scope);
            }
            if (expression instanceof OclSemanticBinder.BoundCollectionOperation collectionOperation) {
                Object source = evaluate(collectionOperation.source(), scope);
                return evaluateCollectionOperation(
                        collectionOperation.ast().opName,
                        source,
                        collectionOperation.arguments(),
                        scope,
                        collectionOperation.type().isUniqueCollection());
            }
            if (expression instanceof OclSemanticBinder.BoundIterator iterator) {
                return evaluateIterator(iterator.ast().operation, evaluate(iterator.source(), scope), iterator.ast().iteratorName, iterator.body(), scope);
            }
            throw new IllegalStateException(expression.getClass().getName());
        }

        private List<Object> evaluateQualifiers(List<OclSemanticBinder.BoundExpression> qualifiers,
                                                Map<String, Object> scope) {
            List<Object> values = new ArrayList<>(qualifiers.size());
            for (OclSemanticBinder.BoundExpression qualifier : qualifiers) {
                values.add(evaluate(qualifier, scope));
            }
            return values;
        }

        private Object evaluateMethod(String name, Object source, List<OclSemanticBinder.BoundExpression> arguments, Map<String, Object> scope) {
            if ("isDefined".equalsIgnoreCase(name)) {
                return source != null;
            }
            if ("isUndefined".equalsIgnoreCase(name)) {
                return source == null;
            }
            if ("concat".equalsIgnoreCase(name)) {
                return String.valueOf(source) + String.valueOf(evaluate(arguments.get(0), scope));
            }
            if ("split".equalsIgnoreCase(name)) {
                String delimiter = String.valueOf(evaluate(arguments.get(0), scope));
                return source == null ? List.of() : List.of(String.valueOf(source).split(java.util.regex.Pattern.quote(delimiter)));
            }
            if ("oclAsType".equalsIgnoreCase(name)) {
                String targetType = arguments.get(0).type().typeName();
                return runtime.castAsType(source, targetType);
            }
            throw new IllegalStateException(name);
        }

        private Object evaluateCollectionOperation(String name,
                                                  Object source,
                                                  List<OclSemanticBinder.BoundExpression> arguments,
                                                  Map<String, Object> scope,
                                                  boolean uniqueCollection) {
            List<?> values = runtime.toList(source);
            return switch (name) {
                case "size" -> (long) values.size();
                case "count" -> runtime.toList(source).stream().filter(value ->
                        Objects.equals(normalizeNumber(value), normalizeNumber(evaluate(arguments.get(0), scope)))).count();
                case "isEmpty" -> values.isEmpty();
                case "notEmpty" -> !values.isEmpty();
                case "sum" -> sum(values);
                case "min" -> extremum(values, true);
                case "max" -> extremum(values, false);
                case "includes" -> values.stream().anyMatch(value ->
                        Objects.equals(normalizeNumber(value), normalizeNumber(evaluate(arguments.get(0), scope))));
                case "excludes" -> values.stream().noneMatch(value ->
                        Objects.equals(normalizeNumber(value), normalizeNumber(evaluate(arguments.get(0), scope))));
                case "includesAll" -> runtime.toList(evaluate(arguments.get(0), scope)).stream().allMatch(candidate ->
                        values.stream().anyMatch(value -> Objects.equals(normalizeNumber(value), normalizeNumber(candidate))));
                case "excludesAll" -> runtime.toList(evaluate(arguments.get(0), scope)).stream().noneMatch(candidate ->
                        values.stream().anyMatch(value -> Objects.equals(normalizeNumber(value), normalizeNumber(candidate))));
                case "including" -> {
                    Object candidate = evaluate(arguments.get(0), scope);
                    if (uniqueCollection) {
                        List<Object> result = new ArrayList<>(values);
                        boolean present = result.stream().anyMatch(value ->
                                Objects.equals(normalizeNumber(value), normalizeNumber(candidate)));
                        if (!present) {
                            result.add(candidate);
                        }
                        yield result;
                    }
                    List<Object> result = new ArrayList<>(values);
                    result.add(candidate);
                    yield result;
                }
                case "excluding" -> {
                    Object candidate = evaluate(arguments.get(0), scope);
                    List<Object> result = new ArrayList<>();
                    for (Object value : values) {
                        if (!Objects.equals(normalizeNumber(value), normalizeNumber(candidate))) {
                            result.add(value);
                        }
                    }
                    yield result;
                }
                case "append" -> {
                    Object candidate = evaluate(arguments.get(0), scope);
                    List<Object> result = new ArrayList<>(values);
                    result.add(candidate);
                    yield uniqueCollection ? uniquePreservingOrder(result) : result;
                }
                case "prepend" -> {
                    Object candidate = evaluate(arguments.get(0), scope);
                    List<Object> result = new ArrayList<>();
                    result.add(candidate);
                    result.addAll(values);
                    yield uniqueCollection ? uniquePreservingOrder(result) : result;
                }
                case "subSequence" -> {
                    int start = ((Number) evaluate(arguments.get(0), scope)).intValue();
                    int end = ((Number) evaluate(arguments.get(1), scope)).intValue();
                    yield subSequence(values, start, end);
                }
                case "union" -> {
                    List<Object> candidates = new ArrayList<>(runtime.toList(evaluate(arguments.get(0), scope)));
                    if (uniqueCollection) {
                        List<Object> result = new ArrayList<>(values);
                        for (Object candidate : candidates) {
                            boolean present = result.stream().anyMatch(value ->
                                    Objects.equals(normalizeNumber(value), normalizeNumber(candidate)));
                            if (!present) {
                                result.add(candidate);
                            }
                        }
                        yield result;
                    }
                    List<Object> result = new ArrayList<>(values);
                    result.addAll(candidates);
                    yield result;
                }
                case "asBag" -> new ArrayList<>(values);
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
                    if (uniqueCollection) {
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
                    yield intersectWithMultiplicity(values, candidates);
                }
                case "first" -> values.isEmpty() ? null : values.get(0);
                case "last" -> values.isEmpty() ? null : values.get(values.size() - 1);
                case "at" -> values.get(((Number) evaluate(arguments.get(0), scope)).intValue() - 1);
                case "isunique" -> {
                    List<Object> unique = new ArrayList<>();
                    for (Object candidate : values) {
                        boolean present = unique.stream().anyMatch(existing ->
                                Objects.equals(normalizeNumber(existing), normalizeNumber(candidate)));
                        if (!present) {
                            unique.add(candidate);
                        }
                    }
                    yield values.size() == unique.size();
                }
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
                case "reject" -> {
                    List<Object> result = new ArrayList<>();
                    for (Object value : values) {
                        Map<String, Object> nested = new LinkedHashMap<>(scope);
                        nested.put(iteratorName, value);
                        if (!toBooleanValue(evaluate(body, nested))) {
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
                case "sortedby" -> sortedBy(values, value -> {
                    Map<String, Object> nested = new LinkedHashMap<>(scope);
                    nested.put(iteratorName, value);
                    return evaluate(body, nested);
                });
                case "isunique" -> {
                    List<Object> projected = new ArrayList<>();
                    for (Object value : values) {
                        Map<String, Object> nested = new LinkedHashMap<>(scope);
                        nested.put(iteratorName, value);
                        projected.add(evaluate(body, nested));
                    }
                    List<Object> unique = new ArrayList<>();
                    for (Object candidate : projected) {
                        boolean present = unique.stream().anyMatch(existing ->
                                Objects.equals(normalizeNumber(existing), normalizeNumber(candidate)));
                        if (!present) {
                            unique.add(candidate);
                        }
                    }
                    yield projected.size() == unique.size();
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
            if (expression instanceof OclIr.SetLiteral setLiteral) {
                List<Object> values = new ArrayList<>();
                for (OclIr.Expression element : setLiteral.elements()) {
                    Object value = evaluate(element, scope);
                    if (values.stream().noneMatch(existing ->
                            Objects.equals(normalizeNumber(existing), normalizeNumber(value)))) {
                        values.add(value);
                    }
                }
                return values;
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
                return runtime.resolveNavigation(
                        evaluate(navigationAccess.source(), scope),
                        navigationAccess.navigation().roleName(),
                        evaluateQualifiers(navigationAccess.qualifiers(), scope));
            }
            if (expression instanceof OclIr.MethodCall methodCall) {
                return evaluateMethod(methodCall.methodName(), evaluate(methodCall.source(), scope), methodCall.arguments(), scope);
            }
            if (expression instanceof OclIr.CollectionOperation collectionOperation) {
                return evaluateCollectionOperation(
                        collectionOperation.operationName(),
                        evaluate(collectionOperation.source(), scope),
                        collectionOperation.arguments(),
                        scope,
                        collectionOperation.type().isUniqueCollection());
            }
            if (expression instanceof OclIr.IteratorOperation iteratorOperation) {
                return evaluateIterator(iteratorOperation.operationName(), evaluate(iteratorOperation.source(), scope),
                        iteratorOperation.iteratorName(), iteratorOperation.body(), scope);
            }
            if (expression instanceof OclIr.NavigationPredicateCheck predicateCheck) {
                List<?> values = runtime.toList(runtime.resolveNavigation(
                        evaluate(predicateCheck.navigation().source(), scope),
                        predicateCheck.navigation().navigation().roleName(),
                        evaluateQualifiers(predicateCheck.navigation().qualifiers(), scope)));
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
                        evaluate(countComparison.navigation().source(), scope),
                        countComparison.navigation().navigation().roleName(),
                        evaluateQualifiers(countComparison.navigation().qualifiers(), scope)));
                long count = values.stream().filter(value -> countComparison.predicate() == null
                        || toBooleanValue(evaluate(countComparison.predicate(), runtime.childScope(scope, countComparison.iteratorName(), value)))).count();
                return evaluateBinary(countComparison.operator(), count, countComparison.literal());
            }
            if (expression instanceof OclIr.NavigationAggregation aggregation) {
                List<?> values = runtime.toList(runtime.resolveNavigation(
                        evaluate(aggregation.navigation().source(), scope),
                        aggregation.navigation().navigation().roleName(),
                        evaluateQualifiers(aggregation.navigation().qualifiers(), scope)));
                List<Object> projected = new ArrayList<>();
                for (Object value : values) {
                    Map<String, Object> childScope = runtime.childScope(scope, aggregation.iteratorName(), value);
                    if (aggregation.predicate() != null && !toBooleanValue(evaluate(aggregation.predicate(), childScope))) {
                        continue;
                    }
                    projected.add(evaluate(aggregation.projection(), childScope));
                }
                return evaluateCollectionOperation(
                        aggregation.operationName(),
                        projected,
                        List.of(),
                        scope,
                        aggregation.type().isUniqueCollection());
            }
            if (expression instanceof OclIr.NavigationUniquenessCheck uniquenessCheck) {
                List<?> values = runtime.toList(runtime.resolveNavigation(
                        evaluate(uniquenessCheck.navigation().source(), scope),
                        uniquenessCheck.navigation().navigation().roleName(),
                        evaluateQualifiers(uniquenessCheck.navigation().qualifiers(), scope)));
                List<Object> projected = new ArrayList<>();
                for (Object value : values) {
                    Map<String, Object> childScope = runtime.childScope(scope, uniquenessCheck.iteratorName(), value);
                    if (uniquenessCheck.predicate() != null && !toBooleanValue(evaluate(uniquenessCheck.predicate(), childScope))) {
                        continue;
                    }
                    projected.add(evaluate(uniquenessCheck.projection(), childScope));
                }
                return evaluateCollectionOperation("isunique", projected, List.of(), scope, false);
            }
            throw new IllegalStateException(expression.getClass().getName());
        }

        private List<Object> evaluateQualifiers(List<OclIr.Expression> qualifiers,
                                                Map<String, Object> scope) {
            List<Object> values = new ArrayList<>(qualifiers.size());
            for (OclIr.Expression qualifier : qualifiers) {
                values.add(evaluate(qualifier, scope));
            }
            return values;
        }

        private Object evaluateMethod(String name, Object source, List<OclIr.Expression> arguments, Map<String, Object> scope) {
            if ("isDefined".equalsIgnoreCase(name)) {
                return source != null;
            }
            if ("isUndefined".equalsIgnoreCase(name)) {
                return source == null;
            }
            if ("concat".equalsIgnoreCase(name)) {
                return String.valueOf(source) + String.valueOf(evaluate(arguments.get(0), scope));
            }
            if ("split".equalsIgnoreCase(name)) {
                String delimiter = String.valueOf(evaluate(arguments.get(0), scope));
                return source == null ? List.of() : List.of(String.valueOf(source).split(java.util.regex.Pattern.quote(delimiter)));
            }
            if ("oclAsType".equalsIgnoreCase(name) && !arguments.isEmpty() && arguments.get(0).type().isClassReference()) {
                return runtime.castAsType(source, arguments.get(0).type().typeName());
            }
            throw new IllegalStateException(name);
        }

        private Object evaluateCollectionOperation(String name,
                                                  Object source,
                                                  List<OclIr.Expression> arguments,
                                                  Map<String, Object> scope,
                                                  boolean uniqueCollection) {
            List<?> values = runtime.toList(source);
            return switch (name) {
                case "size" -> (long) values.size();
                case "count" -> runtime.toList(source).stream().filter(value ->
                        Objects.equals(normalizeNumber(value), normalizeNumber(evaluate(arguments.get(0), scope)))).count();
                case "isEmpty" -> values.isEmpty();
                case "notEmpty" -> !values.isEmpty();
                case "sum" -> sum(values);
                case "min" -> extremum(values, true);
                case "max" -> extremum(values, false);
                case "includes" -> values.stream().anyMatch(value ->
                        Objects.equals(normalizeNumber(value), normalizeNumber(evaluate(arguments.get(0), scope))));
                case "excludes" -> values.stream().noneMatch(value ->
                        Objects.equals(normalizeNumber(value), normalizeNumber(evaluate(arguments.get(0), scope))));
                case "includesAll" -> runtime.toList(evaluate(arguments.get(0), scope)).stream().allMatch(candidate ->
                        values.stream().anyMatch(value -> Objects.equals(normalizeNumber(value), normalizeNumber(candidate))));
                case "excludesAll" -> runtime.toList(evaluate(arguments.get(0), scope)).stream().noneMatch(candidate ->
                        values.stream().anyMatch(value -> Objects.equals(normalizeNumber(value), normalizeNumber(candidate))));
                case "including" -> {
                    Object candidate = evaluate(arguments.get(0), scope);
                    if (uniqueCollection) {
                        List<Object> result = new ArrayList<>(values);
                        boolean present = result.stream().anyMatch(value ->
                                Objects.equals(normalizeNumber(value), normalizeNumber(candidate)));
                        if (!present) {
                            result.add(candidate);
                        }
                        yield result;
                    }
                    List<Object> result = new ArrayList<>(values);
                    result.add(candidate);
                    yield result;
                }
                case "excluding" -> {
                    Object candidate = evaluate(arguments.get(0), scope);
                    List<Object> result = new ArrayList<>();
                    for (Object value : values) {
                        if (!Objects.equals(normalizeNumber(value), normalizeNumber(candidate))) {
                            result.add(value);
                        }
                    }
                    yield result;
                }
                case "append" -> {
                    Object candidate = evaluate(arguments.get(0), scope);
                    List<Object> result = new ArrayList<>(values);
                    result.add(candidate);
                    yield uniqueCollection ? uniquePreservingOrder(result) : result;
                }
                case "prepend" -> {
                    Object candidate = evaluate(arguments.get(0), scope);
                    List<Object> result = new ArrayList<>();
                    result.add(candidate);
                    result.addAll(values);
                    yield uniqueCollection ? uniquePreservingOrder(result) : result;
                }
                case "subSequence" -> {
                    int start = ((Number) evaluate(arguments.get(0), scope)).intValue();
                    int end = ((Number) evaluate(arguments.get(1), scope)).intValue();
                    yield subSequence(values, start, end);
                }
                case "union" -> {
                    List<Object> candidates = new ArrayList<>(runtime.toList(evaluate(arguments.get(0), scope)));
                    if (uniqueCollection) {
                        List<Object> result = new ArrayList<>(values);
                        for (Object candidate : candidates) {
                            boolean present = result.stream().anyMatch(value ->
                                    Objects.equals(normalizeNumber(value), normalizeNumber(candidate)));
                            if (!present) {
                                result.add(candidate);
                            }
                        }
                        yield result;
                    }
                    List<Object> result = new ArrayList<>(values);
                    result.addAll(candidates);
                    yield result;
                }
                case "asBag" -> new ArrayList<>(values);
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
                    if (uniqueCollection) {
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
                    yield intersectWithMultiplicity(values, candidates);
                }
                case "first" -> values.isEmpty() ? null : values.get(0);
                case "last" -> values.isEmpty() ? null : values.get(values.size() - 1);
                case "at" -> values.get(((Number) evaluate(arguments.get(0), scope)).intValue() - 1);
                case "isunique" -> {
                    List<Object> unique = new ArrayList<>();
                    for (Object candidate : values) {
                        boolean present = unique.stream().anyMatch(existing ->
                                Objects.equals(normalizeNumber(existing), normalizeNumber(candidate)));
                        if (!present) {
                            unique.add(candidate);
                        }
                    }
                    yield values.size() == unique.size();
                }
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
                case "sortedby" -> sortedBy(values, value ->
                        evaluate(body, runtime.childScope(scope, iteratorName, value)));
                case "isunique" -> {
                    List<Object> projected = new ArrayList<>();
                    for (Object value : values) {
                        projected.add(evaluate(body, runtime.childScope(scope, iteratorName, value)));
                    }
                    List<Object> unique = new ArrayList<>();
                    for (Object candidate : projected) {
                        boolean present = unique.stream().anyMatch(existing ->
                                Objects.equals(normalizeNumber(existing), normalizeNumber(candidate)));
                        if (!present) {
                            unique.add(candidate);
                        }
                    }
                    yield projected.size() == unique.size();
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
        if ("xor".equals(operator)) {
            return toBooleanValue(left) ^ toBooleanValue(right);
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

    private static Object sum(List<?> values) {
        if (values.stream().anyMatch(value -> value instanceof Double || value instanceof Float)) {
            double total = 0D;
            for (Object value : values) {
                total += ((Number) value).doubleValue();
            }
            return total;
        }
        long total = 0L;
        for (Object value : values) {
            total += ((Number) value).longValue();
        }
        return total;
    }

    private static Object extremum(List<?> values, boolean min) {
        if (values.isEmpty()) {
            return null;
        }
        Object best = values.get(0);
        for (int index = 1; index < values.size(); index++) {
            Object candidate = values.get(index);
            double bestValue = ((Number) best).doubleValue();
            double candidateValue = ((Number) candidate).doubleValue();
            if ((min && candidateValue < bestValue) || (!min && candidateValue > bestValue)) {
                best = candidate;
            }
        }
        return best;
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

    private static List<Object> uniquePreservingOrder(List<?> values) {
        List<Object> result = new ArrayList<>();
        for (Object value : values) {
            boolean present = result.stream().anyMatch(existing ->
                    Objects.equals(normalizeNumber(existing), normalizeNumber(value)));
            if (!present) {
                result.add(value);
            }
        }
        return result;
    }

    private static List<Object> subSequence(List<?> values, int start, int end) {
        if (values.isEmpty()) {
            return List.of();
        }
        int fromIndex = Math.max(0, start - 1);
        int toIndexExclusive = Math.min(values.size(), end);
        if (fromIndex >= toIndexExclusive) {
            return List.of();
        }
        return new ArrayList<>(values.subList(fromIndex, toIndexExclusive));
    }

    private static List<Object> sortedBy(List<?> values, Function<Object, Object> keyExtractor) {
        record SortEntry(int index, Object value, Object key) {
        }
        List<SortEntry> entries = new ArrayList<>();
        for (int index = 0; index < values.size(); index++) {
            Object value = values.get(index);
            entries.add(new SortEntry(index, value, normalizeNumber(keyExtractor.apply(value))));
        }
        entries.sort((left, right) -> {
            int byKey = compareSortKeys(left.key(), right.key());
            return byKey != 0 ? byKey : Integer.compare(left.index(), right.index());
        });
        List<Object> result = new ArrayList<>();
        for (SortEntry entry : entries) {
            result.add(entry.value());
        }
        return result;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static int compareSortKeys(Object left, Object right) {
        if (left == right) {
            return 0;
        }
        if (left == null) {
            return -1;
        }
        if (right == null) {
            return 1;
        }
        if (left instanceof Comparable leftComparable
                && right instanceof Comparable
                && left.getClass().isAssignableFrom(right.getClass())) {
            return leftComparable.compareTo(right);
        }
        if (right instanceof Comparable rightComparable
                && left instanceof Comparable
                && right.getClass().isAssignableFrom(left.getClass())) {
            return -rightComparable.compareTo(left);
        }
        return String.valueOf(left).compareTo(String.valueOf(right));
    }

    private static List<Object> intersectWithMultiplicity(List<?> values, List<?> candidates) {
        List<Object> remaining = new ArrayList<>(candidates);
        List<Object> result = new ArrayList<>();
        for (Object value : values) {
            int index = indexOfNormalized(remaining, value);
            if (index >= 0) {
                result.add(value);
                remaining.remove(index);
            }
        }
        return result;
    }

    private static int indexOfNormalized(List<?> values, Object candidate) {
        for (int index = 0; index < values.size(); index++) {
            if (Objects.equals(normalizeNumber(values.get(index)), normalizeNumber(candidate))) {
                return index;
            }
        }
        return -1;
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
            return resolveNavigation(source, roleName, List.of());
        }

        private List<Object> resolveNavigation(Object source, String roleName, List<Object> qualifiers) {
            if (source instanceof Collection<?> collection) {
                List<Object> result = new ArrayList<>();
                for (Object item : collection) {
                    result.addAll(resolveNavigation(item, roleName, qualifiers));
                }
                return result;
            }
            if (source instanceof TestObject object) {
                List<Object> result = new ArrayList<>(object.links.getOrDefault(roleName, List.of()));
                if (!qualifiers.isEmpty()) {
                    for (QualifiedTargets entry : object.qualifiedLinks.getOrDefault(roleName, List.of())) {
                        if (qualifiersMatch(entry.qualifiers(), qualifiers)) {
                            result.addAll(entry.targets());
                        }
                    }
                } else {
                    for (QualifiedTargets entry : object.qualifiedLinks.getOrDefault(roleName, List.of())) {
                        result.addAll(entry.targets());
                    }
                }
                return result;
            }
            return List.of();
        }

        private boolean qualifiersMatch(List<String> storedQualifiers, List<Object> requestedQualifiers) {
            if (storedQualifiers.size() != requestedQualifiers.size()) {
                return false;
            }
            for (int i = 0; i < storedQualifiers.size(); i++) {
                Object requested = requestedQualifiers.get(i);
                if (requested == null || !Objects.equals(storedQualifiers.get(i),
                        encodeTestQualifier(requested))) {
                    return false;
                }
            }
            return true;
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

        private Object castAsType(Object source, String targetType) {
            return source instanceof TestObject object && targetType.equals(object.className) ? object : null;
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
        private final Map<String, List<QualifiedTargets>> qualifiedLinks = new LinkedHashMap<>();

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

        private TestObject attr(String name, Object value) {
            return attribute(name, value);
        }

        private TestObject link(String roleName, TestObject... targets) {
            links.put(roleName, new ArrayList<>(List.of(targets)));
            return this;
        }

        private TestObject qualifiedLink(String roleName, List<Object> qualifiers, TestObject... targets) {
            qualifiedLinks.computeIfAbsent(roleName, ignored -> new ArrayList<>())
                    .add(new QualifiedTargets(serializeQualifiers(qualifiers), new ArrayList<>(List.of(targets))));
            return this;
        }

        private List<String> serializeQualifiers(List<Object> qualifiers) {
            List<String> values = new ArrayList<>(qualifiers.size());
            for (Object qualifier : qualifiers) {
                values.add(encodeTestQualifier(qualifier));
            }
            return values;
        }
    }

    private static String encodeTestQualifier(Object value) {
        String typeName;
        if (value instanceof Byte || value instanceof Short
                || value instanceof Integer || value instanceof Long) typeName = "Integer";
        else if (value instanceof Float || value instanceof Double) typeName = "Real";
        else if (value instanceof Boolean) typeName = "Boolean";
        else if (value instanceof String text && text.startsWith("#")) typeName = "TestEnum";
        else typeName = "String";
        return CanonicalScalarValueCodec.encode(value, typeName);
    }

    private record QualifiedTargets(List<String> qualifiers, List<TestObject> targets) {
    }
}
