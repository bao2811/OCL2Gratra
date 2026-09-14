package org.uet.dse.neo4jtgg.experiment;

import org.tzi.use.api.UseSystemApi;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.ocl.value.StringValue;
import org.tzi.use.uml.ocl.value.Value;
import org.tzi.use.uml.sys.MObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Shared valid-model fixture and independently reviewed expectations for OCL_val-47. */
final class OclVal47NonVacuityFixture {
    private static final String MANIFEST =
            "/org/uet/dse/neo4jtgg/experiment/oclval47-nonvacuity.tsv";

    private OclVal47NonVacuityFixture() {
    }

    static MModel compileModel(String modelName) {
        String specification = """
                model %s
                class Company
                attributes
                    name : String
                end
                class Person
                attributes
                    name : String
                    age : Integer
                end
                class Employee < Person
                end
                class Library
                end
                class Book
                end
                association Employment between
                    Company[1] role employer
                    Person[*] role employee
                end
                association Catalog between
                    Library[1] role library qualifier (shelf : String)
                    Book[*] role book
                end
                """.formatted(modelName);
        StringWriter diagnostics = new StringWriter();
        MModel model = USECompiler.compileSpecification(specification, modelName + ".use",
                new PrintWriter(diagnostics, true), new ModelFactory());
        assertNotNull(model, diagnostics.toString());
        return model;
    }

    static UseSystemApi seed(MModel model) throws Exception {
        UseSystemApi api = UseSystemApi.create(model, false);
        for (String company : List.of("companyGood", "companyEmpty", "companyNegative", "companyMisc")) {
            api.createObject("Company", company);
            api.setAttributeValue(company, "name", "'" + company + "'");
        }
        createPerson(api, "person17", "Person", "Person", 17);
        createPerson(api, "employee30", "Employee", "Employee", 30);
        createPerson(api, "person18", "Person", "Adult", 18);
        createPerson(api, "personNeg", "Person", "Negative", -1);
        createPerson(api, "personBlank", "Person", "", 10);
        createPerson(api, "personOld", "Person", "Old", 201);
        createPerson(api, "personZero", "Person", "Zero", 0);

        link(api, "companyGood", "person17");
        link(api, "companyGood", "employee30");
        link(api, "companyGood", "person18");
        link(api, "companyNegative", "personNeg");
        link(api, "companyMisc", "personBlank");
        link(api, "companyMisc", "personOld");
        link(api, "companyMisc", "personZero");

        api.createObject("Library", "libraryWith");
        api.createObject("Library", "libraryEmpty");
        api.createObject("Book", "book");
        api.createLinkEx(model.getAssociation("Catalog"),
                new MObject[]{api.getObject("libraryWith"), api.getObject("book")},
                new Value[][]{new Value[]{new StringValue("A1")}, new Value[0]});
        return api;
    }

    static Set<String> contextIds(UseSystemApi api, String className) {
        Set<String> ids = new LinkedHashSet<>();
        var contextClass = api.getSystem().model().getClass(className);
        assertNotNull(contextClass, "Unknown context class " + className);
        for (MObject object : api.getSystem().state().objectsOfClassAndSubClasses(contextClass)) {
            ids.add(object.name());
        }
        return Set.copyOf(ids);
    }

    static Map<String, ExpectedCase> expectations() throws Exception {
        Map<String, ExpectedCase> result = new LinkedHashMap<>();
        var input = OclVal47NonVacuityFixture.class.getResourceAsStream(MANIFEST);
        assertNotNull(input, MANIFEST);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (!"id\tfeature\tcontext\tobligation\texpectedClass\texpectedViolations\tsemanticBasis"
                    .equals(header)) throw new IllegalStateException("Unexpected non-vacuity manifest header");
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] fields = line.split("\\t", -1);
                if (fields.length != 7) throw new IllegalStateException("Malformed non-vacuity row: " + line);
                Set<String> violations = fields[5].isBlank()
                        ? Set.of() : Set.of(fields[5].split(";"));
                ExpectedCase expected = new ExpectedCase(fields[0], fields[1], fields[2],
                        Obligation.valueOf(fields[3]), BenchmarkVacuityStatus.valueOf(fields[4]),
                        violations, fields[6]);
                if (result.put(expected.id(), expected) != null) {
                    throw new IllegalStateException("Duplicate non-vacuity case: " + expected.id());
                }
            }
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(result));
    }

    static String expressionSource(String invariantSource) {
        int separator = invariantSource.indexOf(':');
        if (separator < 0 || separator == invariantSource.length() - 1) {
            throw new IllegalArgumentException("Invariant has no predicate: " + invariantSource);
        }
        return invariantSource.substring(separator + 1).trim();
    }

    private static void createPerson(UseSystemApi api, String id, String className,
                                     String name, int age) throws Exception {
        api.createObject(className, id);
        api.setAttributeValue(id, "name", "'" + name + "'");
        api.setAttributeValue(id, "age", Integer.toString(age));
    }

    private static void link(UseSystemApi api, String company, String person) throws Exception {
        api.createLink("Employment", company, person);
    }

    enum Obligation {
        PROFILE_TAUTOLOGY,
        MIXED_REQUIRED
    }

    record ExpectedCase(String id, String feature, String context, Obligation obligation,
                        BenchmarkVacuityStatus expectedClass, Set<String> expectedViolations,
                        String semanticBasis) {
        ExpectedCase {
            expectedViolations = Set.copyOf(expectedViolations);
            if (semanticBasis == null || semanticBasis.isBlank()) {
                throw new IllegalArgumentException("semanticBasis must not be blank for " + id);
            }
        }
    }
}
