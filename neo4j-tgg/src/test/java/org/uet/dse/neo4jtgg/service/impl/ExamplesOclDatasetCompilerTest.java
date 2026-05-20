package org.uet.dse.neo4jtgg.service.impl;

import org.junit.jupiter.api.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.uet.dse.neo4jtgg.model.CypherCompilationResult;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExamplesOclDatasetCompilerTest {
    private static final Path REPO_ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void compilesFamiliesUseSnippetsAgainstExamplesMetamodel() throws IOException {
        String familiesSpec = Files.readString(REPO_ROOT.resolve("examples/Families2Persons/Families.use"));
        String soil = Files.readString(REPO_ROOT.resolve("examples/Families2Persons/input01.soil"));
        MModel model = compileUseModel(familiesSpec, "Families.use");

        assertTrue(soil.contains("!new FamilyMember('father')"));
        assertTrue(soil.contains("!insert (fm,father) into Father"));

        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);

        CypherCompilationResult hasFamilies = compiler.compile(
                "context FamilyRegister inv HasFamilies: self.families->size() > 0");
        assertTrue(hasFamilies.isSupported(), hasFamilies.getReason());
        assertTrue(hasFamilies.getCypher().contains("EXISTS { MATCH (self)-[r]->(nav)"));

        CypherCompilationResult hasFather = compiler.compile(
                "context Family inv HasFather: self.father->notEmpty()");
        assertTrue(hasFather.isSupported(), hasFather.getReason());
        assertTrue(hasFather.getCypher().contains("EXISTS { MATCH (self)-[r]->(nav)"));

        CypherCompilationResult namedFather = compiler.compile(
                "context Family inv NamedFather: self.father.name <> ''");
        assertTrue(namedFather.isSupported(), namedFather.getReason());
        assertTrue(namedFather.getCypher().contains("ObjectHasAttribute"));

        CypherCompilationResult incomingFamily = compiler.compile(
                "context FamilyMember inv BelongsToFamily: self.familyFather->notEmpty()");
        assertTrue(incomingFamily.isSupported(), incomingFamily.getReason());
        assertTrue(incomingFamily.getCypher().contains("(self)<-[r]-(nav)"));
    }

    @Test
    void compilesSupportedSnippetsLiftedFromExamplesOclFiles() throws IOException {
        String miniCompany = readExample("examples/ocl-examples/mini_company/mini_company.ocl");
        String vehicles = readExample("examples/ocl-examples/vehicles/vehicles.ocl");

        assertTrue(miniCompany.contains("employs->exists( e:Employee | e.salary < 2000)"));
        assertTrue(vehicles.contains("self.truck->size() <> 0"));
        assertTrue(vehicles.contains("self.truck->forAll( t:Truck |"));

        String miniCompanySpec = """
                model MiniCompany
                class Employee
                attributes
                    salary : Integer
                end
                class Manager
                end
                association Employs between
                    Manager[1] role manager
                    Employee[*] role employs
                end
                """;
        DefaultOclToCypherCompiler miniCompanyCompiler = new DefaultOclToCypherCompiler(
                compileUseModel(miniCompanySpec, "MiniCompany.use"));

        CypherCompilationResult slavery = miniCompanyCompiler.compile(
                "context Manager inv slavery: self.employs->exists(e | e.salary < 2000)");
        assertTrue(slavery.isSupported(), slavery.getReason());
        assertTrue(slavery.getCypher().contains("EXISTS { MATCH (self)-[r]->(e)"));

        String vehiclesSpec = """
                model Vehicles
                class Person
                attributes
                    age : Integer
                end
                class DriversLicense
                attributes
                    licenseClass : String
                end
                class Truck
                attributes
                    tons : Real
                end
                association PersonTruck between
                    Person[1] role driver
                    Truck[*] role truck
                end
                association PersonDriversLicense between
                    Person[1] role person
                    DriversLicense[*] role driversLicense
                end
                """;
        DefaultOclToCypherCompiler vehiclesCompiler = new DefaultOclToCypherCompiler(
                compileUseModel(vehiclesSpec, "Vehicles.use"));

        CypherCompilationResult truckCount = vehiclesCompiler.compile(
                "context Person inv NumberOfDrivenTrucksNotZero: self.truck->size() <> 0");
        assertTrue(truckCount.isSupported(), truckCount.getReason());
        assertTrue(truckCount.getCypher().contains("EXISTS { MATCH (self)-[r]->(nav)"));

        CypherCompilationResult adultDrivers = vehiclesCompiler.compile(
                "context Person inv AllPersonsWithDriversLicenseAdult: self.driversLicense->notEmpty() implies self.age > 17");
        assertTrue(adultDrivers.isSupported(), adultDrivers.getReason());

        CypherCompilationResult allDriversAllowed = vehiclesCompiler.compile(
                "context Person inv AllDriversAllowedToDriveTheirTrucks: self.truck->forAll(t | t.tons > 0)");
        assertTrue(allDriversAllowed.isSupported(), allDriversAllowed.getReason());
        assertTrue(allDriversAllowed.getCypher().contains("NOT EXISTS { MATCH (self)-[r]->(t)"));
    }

    @Test
    void compilesTypedIteratorSnippetsFromExamplesWithoutNormalization() throws IOException {
        String miniCompany = readExample("examples/ocl-examples/mini_company/mini_company.ocl");
        String company = readExample("examples/ocl-examples/company/company.ocl");

        assertTrue(miniCompany.contains("employs->exists( e:Employee | e.salary < 2000)"));
        assertTrue(company.contains("self.employee->select(e:Person | e.age > 50)->notEmpty()"));
        assertTrue(company.contains("self.employee->exists( p:Person | p.firstName = 'Jack' )"));
        assertTrue(company.contains("self.employee->forAll(p:Person | p.age <= 65 )"));

        String miniCompanySpec = """
                model MiniCompany
                class Employee
                attributes
                    salary : Integer
                end
                class Manager
                end
                association Employs between
                    Manager[1] role manager
                    Employee[*] role employs
                end
                """;
        DefaultOclToCypherCompiler miniCompanyCompiler = new DefaultOclToCypherCompiler(
                compileUseModel(miniCompanySpec, "MiniCompany.use"));

        CypherCompilationResult typedExists = miniCompanyCompiler.compile(
                "context Manager inv slavery: self.employs->exists(e:Employee | e.salary < 2000)");
        assertTrue(typedExists.isSupported(), typedExists.getReason());

        String companySpec = """
                model Company
                class Company
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
                """;
        DefaultOclToCypherCompiler companyCompiler = new DefaultOclToCypherCompiler(
                compileUseModel(companySpec, "Company.use"));

        CypherCompilationResult typedSelect = companyCompiler.compile(
                "context Company inv SeniorEmployeeExists: self.employee->select(e:Person | e.age > 50)->notEmpty()");
        assertTrue(typedSelect.isSupported(), typedSelect.getReason());

        CypherCompilationResult typedExistsCompany = companyCompiler.compile(
                "context Company inv HasJack: self.employee->exists(p:Person | p.firstName = 'Jack')");
        assertTrue(typedExistsCompany.isSupported(), typedExistsCompany.getReason());

        CypherCompilationResult typedForAll = companyCompiler.compile(
                "context Company inv WorkingAgeOnly: self.employee->forAll(p:Person | p.age <= 65)");
        assertTrue(typedForAll.isSupported(), typedForAll.getReason());
    }

    @Test
    void compilesSupportedCompanySnippetsLiftedFromExamples() throws IOException {
        String company = readExample("examples/ocl-examples/company/company.ocl");

        assertTrue(company.contains("self.employer->isEmpty()"));
        assertTrue(company.contains("self.manager->size() = 1"));
        assertTrue(company.contains("self.employee->select(e:Person | e.age > 50)->notEmpty()"));
        assertTrue(company.contains("self.employee->exists( p:Person | p.firstName = 'Jack' )"));
        assertTrue(company.contains("self.employee->forAll(p:Person | p.age <= 65 )"));

        String companySpec = """
                model Company
                class Company
                attributes
                    name : String
                end
                class Person
                attributes
                    age : Integer
                    firstName : String
                    birthDate : String
                    isMarried : Boolean
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
        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(
                compileUseModel(companySpec, "Company.use"));

        CypherCompilationResult employerEmpty = compiler.compile(
                "context Person inv NoEmployer: self.employer->isEmpty()");
        assertTrue(employerEmpty.isSupported(), employerEmpty.getReason());
        assertTrue(employerEmpty.getCypher().contains("(self)<-[r]-(nav)"));

        CypherCompilationResult managerCount = compiler.compile(
                "context Company inv OneManager: self.manager->size() = 1");
        assertTrue(managerCount.isSupported(), managerCount.getReason());
        assertTrue(managerCount.getCypher().contains("COUNT { MATCH (self)-[r]->(nav)"));

        CypherCompilationResult olderEmployee = compiler.compile(
                "context Company inv SeniorEmployeeExists: self.employee->select(e | e.age > 50)->notEmpty()");
        assertTrue(olderEmployee.isSupported(), olderEmployee.getReason());
        assertTrue(olderEmployee.getCypher().contains("EXISTS { MATCH (self)-[r]->(e)"));

        CypherCompilationResult hasJack = compiler.compile(
                "context Company inv HasJack: self.employee->exists(p | p.firstName = 'Jack')");
        assertTrue(hasJack.isSupported(), hasJack.getReason());
        assertTrue(hasJack.getCypher().contains("EXISTS { MATCH (self)-[r]->(p)"));

        CypherCompilationResult retirementAge = compiler.compile(
                "context Company inv WorkingAgeOnly: self.employee->forAll(p | p.age <= 65)");
        assertTrue(retirementAge.isSupported(), retirementAge.getReason());
        assertTrue(retirementAge.getCypher().contains("NOT EXISTS { MATCH (self)-[r]->(p)"));
    }

    @Test
    void rejectsExpectedFailSnippetsFromExamples() throws IOException {
        String company = readExample("examples/ocl-examples/company/company.ocl");

        assertTrue(company.contains("self.employee.birthDate->size() > 0"));
        assertTrue(company.contains("self.employee->reject( p:Person | p.isMarried )->isEmpty()"));

        String companySpec = """
                model Company
                class Company
                end
                class Person
                attributes
                    age : Integer
                    birthDate : String
                    isMarried : Boolean
                end
                association CompanyEmployee between
                    Company[1] role employer
                    Person[*] role employee
                end
                """;
        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(
                compileUseModel(companySpec, "Company.use"));

        CypherCompilationResult implicitCollect = compiler.compile(
                "context Company inv BirthDatesPresent: self.employee.birthDate->size() > 0");
        assertFalse(implicitCollect.isSupported());

        CypherCompilationResult reject = compiler.compile(
                "context Company inv UnmarriedOnly: self.employee->reject(p | p.isMarried)->isEmpty()");
        assertFalse(reject.isSupported());
    }

    private static String readExample(String relativePath) throws IOException {
        return Files.readString(REPO_ROOT.resolve(relativePath));
    }

    private static MModel compileUseModel(String specification, String fileName) {
        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(
                specification,
                fileName,
                new PrintWriter(buffer, true),
                new ModelFactory());
        assertNotNull(model, buffer.toString());
        return model;
    }
}
