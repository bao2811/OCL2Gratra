package org.uet.dse.neo4jtgg.ocl;

import org.junit.jupiter.api.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OclMetamodelIndexTest {
    @Test
    void indexesAttributesAndNavigations() {
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

        OclMetamodelIndex index = new OclMetamodelIndex(model);
        assertNotNull(index.resolveAttribute("Family", "name"));

        OclMetamodelIndex.NavigationInfo navigation = index.resolveNavigation("Family", "children");
        assertNotNull(navigation);
        assertEquals("FamilyChildren", navigation.associationName());
        assertEquals("Person", navigation.targetClassName());
        assertTrue(navigation.resultBinding().isCollection());
        assertEquals(OclTypeBinding.CollectionKind.SET, navigation.resultBinding().collectionKind());
        assertEquals("family", navigation.sourceRoleName());
        assertEquals("children", navigation.targetRoleName());
        assertEquals("*", navigation.sourceMultiplicity());
        assertEquals("*", navigation.targetMultiplicity());
        assertNotNull(navigation.sourceEnd());
        assertNotNull(navigation.targetEnd());
    }

    @Test
    void capturesSingleValuedNavigationMetadata() {
        String spec = """
                model Demo
                class Family
                end
                class Person
                end
                association FamilyParents between
                    Family[*] role family
                    Person[0..1] role father
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        OclMetamodelIndex index = new OclMetamodelIndex(model);
        OclMetamodelIndex.NavigationInfo navigation = index.resolveNavigation("Family", "father");
        assertNotNull(navigation);
        assertEquals("family", navigation.sourceRoleName());
        assertEquals("father", navigation.targetRoleName());
        assertEquals("*", navigation.sourceMultiplicity());
        assertEquals("0..1", navigation.targetMultiplicity());
        assertTrue(navigation.targetSingleValued());
    }

    @Test
    void capturesOrderedNavigationAsOrderedSet() {
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

        OclMetamodelIndex index = new OclMetamodelIndex(model);
        OclMetamodelIndex.NavigationInfo navigation = index.resolveNavigation("Invoice", "lineItem");
        assertNotNull(navigation);
        assertEquals(OclTypeBinding.CollectionKind.ORDERED_SET, navigation.resultBinding().collectionKind());
        assertTrue(navigation.targetOrdered());
        assertTrue(navigation.resultBinding().isOrderedCollection());
        assertTrue(navigation.resultBinding().isUniqueCollection());
    }

    @Test
    void resolvesSelfAssociationEndsWithDistinctDirections() {
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

        OclMetamodelIndex index = new OclMetamodelIndex(model);
        OclMetamodelIndex.NavigationInfo child = index.resolveNavigation("Person", "child");
        OclMetamodelIndex.NavigationInfo parent = index.resolveNavigation("Person", "parent");

        assertNotNull(child);
        assertNotNull(parent);
        assertEquals("Parenthood", child.associationName());
        assertEquals("Parenthood", parent.associationName());
        assertEquals("parent", child.sourceRoleName());
        assertEquals("child", child.targetRoleName());
        assertEquals("child", parent.sourceRoleName());
        assertEquals("parent", parent.targetRoleName());
        assertEquals(OclMetamodelIndex.NavigationDirection.OUTGOING, child.direction());
        assertEquals(OclMetamodelIndex.NavigationDirection.INCOMING, parent.direction());
    }

    @Test
    void distinguishesMultipleAssociationsBetweenSameClasses() {
        String spec = """
                model Demo
                class Company
                end
                class Person
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

        OclMetamodelIndex index = new OclMetamodelIndex(model);
        OclMetamodelIndex.NavigationInfo employee = index.resolveNavigation("Company", "employee");
        OclMetamodelIndex.NavigationInfo manager = index.resolveNavigation("Company", "manager");
        OclMetamodelIndex.NavigationInfo employer = index.resolveNavigation("Person", "employer");
        OclMetamodelIndex.NavigationInfo managedCompany = index.resolveNavigation("Person", "managedCompany");

        assertNotNull(employee);
        assertNotNull(manager);
        assertNotNull(employer);
        assertNotNull(managedCompany);
        assertEquals("CompanyEmployee", employee.associationName());
        assertEquals("CompanyManager", manager.associationName());
        assertEquals("CompanyEmployee", employer.associationName());
        assertEquals("CompanyManager", managedCompany.associationName());
        assertTrue(manager.targetSingleValued());
        assertEquals(OclMetamodelIndex.NavigationDirection.OUTGOING, employee.direction());
        assertEquals(OclMetamodelIndex.NavigationDirection.OUTGOING, manager.direction());
        assertEquals(OclMetamodelIndex.NavigationDirection.INCOMING, employer.direction());
        assertEquals(OclMetamodelIndex.NavigationDirection.INCOMING, managedCompany.direction());
    }

    @Test
    void resolvesInheritedAttributesAndNavigations() {
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

        OclMetamodelIndex index = new OclMetamodelIndex(model);
        assertNotNull(index.resolveAttribute("Employee", "age"));

        OclMetamodelIndex.NavigationInfo employer = index.resolveNavigation("Employee", "employer");
        assertNotNull(employer);
        assertEquals("CompanyStaff", employer.associationName());
        assertEquals("Employee", employer.sourceClassName());
        assertEquals("Company", employer.targetClassName());
        assertEquals("staff", employer.sourceRoleName());
        assertEquals("employer", employer.targetRoleName());
    }

    @Test
    void marksNAryNavigationAsNotDirectlySupportedForCypher() {
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

        OclMetamodelIndex index = new OclMetamodelIndex(model);
        OclMetamodelIndex.NavigationInfo pet = index.resolveNavigation("Person", "pet");
        assertNotNull(pet);
        assertEquals("Buy", pet.associationName());
        assertEquals(OclMetamodelIndex.NavigationDirection.UNDIRECTED, pet.direction());
        assertTrue(!pet.isBinaryAssociation());
        assertTrue(!pet.supportsDirectCypherNavigation());
    }

    @Test
    void marksQualifiedNavigationAsNotDirectlySupportedForCypher() {
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

        OclMetamodelIndex index = new OclMetamodelIndex(model);
        OclMetamodelIndex.NavigationInfo book = index.resolveNavigation("Library", "book");
        assertNotNull(book);
        assertEquals("Catalog", book.associationName());
        assertTrue(book.hasQualifiers());
        assertTrue(!book.supportsDirectCypherNavigation());
    }

    @Test
    void marksRedefiningNavigationAsNotDirectlySupportedForCypher() {
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

        OclMetamodelIndex index = new OclMetamodelIndex(model);
        OclMetamodelIndex.NavigationInfo employer = index.resolveNavigation("Employee", "startupEmployer");
        assertNotNull(employer);
        assertEquals("StartupWorksFor", employer.associationName());
        assertTrue(employer.isRedefiningAssociation());
        assertTrue(!employer.supportsDirectCypherNavigation());
    }
}
