package org.uet.dse.neo4jtgg.experiment;

import org.junit.jupiter.api.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.uet.dse.neo4jtgg.service.impl.DefaultOclToCypherCompiler;
import org.uet.dse.neo4jtgg.ocl.OclBottomToken;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CompilerMutationContractTest {
    @Test
    void killsTargetedCompilerAndEncodingMutants() {
        var compiler = new DefaultOclToCypherCompiler(model());
        var booleanResult = compiler.compileInvariantInstrumented(
                "context Person inv AdultNamed: self.age >= 18 and self.name <> ''");
        var navigationResult = compiler.compileInvariantInstrumented(
                "context Company inv HasEmployee: self.employee->notEmpty()");
        var setResult = compiler.compileInvariantInstrumented(
                "context Person inv SetBottom: Set{self.age, null}->includes(null)");
        var allInstancesResult = compiler.compileInvariantInstrumented(
                "context Person inv EveryPersonVisible: Person.allInstances()->includes(self)");
        var kindOfResult = compiler.compileInvariantInstrumented(
                "context Person inv PersonKind: self.oclIsKindOf(Person)");
        var qualifiedOutgoing = compiler.compileInvariantInstrumented(
                "context Library inv ForwardShelf: self.book['HCM']->notEmpty()");
        var qualifiedIncoming = compiler.compileInvariantInstrumented(
                "context Book inv ReverseShelf: self.library['A1']->notEmpty()");

        List<Mutant> mutants = List.of(
                text("remove-distinct", booleanResult,
                        booleanResult.cypher().replace("RETURN DISTINCT", "RETURN")),
                text("and-to-or", booleanResult,
                        booleanResult.cypher().replaceFirst(" AND coalesce\\(", " OR coalesce(")),
                text("reverse-navigation", navigationResult,
                        navigationResult.cypher().replace(")-[r]->(", ")<-[r]-(")),
                text("break-result-alias", booleanResult,
                        booleanResult.cypher().replace("self.use_id AS useId", "missing.use_id AS useId")),
                new Mutant("drop-bottom-token", setResult,
                        setResult.cypher(), withoutBottom(setResult.parameters())),
                text("drop-validation-coercion", booleanResult,
                        booleanResult.cypher().replaceFirst("WHERE NOT coalesce\\(", "WHERE NOT (")),
                text("raw-set-equality", setResult,
                        setResult.cypher().replaceFirst("coalesce\\(", "(")),
                params("display-context-name", booleanResult,
                        replaceCanonicalValue(booleanResult.parameters(), "::class::Person", "Person")),
                text("schema-instanceof-for-context", booleanResult,
                        booleanResult.cypher().replaceFirst("\\[:ObjectInstanceOf\\]", "[:InstanceOf]")),
                text("attribute-suffix-lookup", booleanResult,
                        booleanResult.cypher().replace(".attributeKey = $", ".attributeKey ENDS WITH $")),
                params("wrong-attribute-key", booleanResult,
                        replaceCanonicalValue(booleanResult.parameters(), "::attribute::Person::age",
                                "MutationModel::attribute::Person::name")),
                text("association-display-property", navigationResult,
                        navigationResult.cypher().replace("r.associationKey = $", "r.associationName = $")),
                params("wrong-association-key", navigationResult,
                        replaceCanonicalValue(navigationResult.parameters(), "::association::Employment",
                                "MutationModel::association::Other")),
                text("drop-source-role", navigationResult,
                        navigationResult.cypher().replace("r.sourceRole = $", "r.wrongSourceRole = $")),
                text("drop-target-role", navigationResult,
                        navigationResult.cypher().replace("r.targetRole = $", "r.wrongTargetRole = $")),
                text("wrong-forward-qualifier-side", qualifiedOutgoing,
                        qualifiedOutgoing.cypher().replace("r.sourceQualifiers[0]", "r.targetQualifiers[0]")),
                text("wrong-reverse-qualifier-side", qualifiedIncoming,
                        qualifiedIncoming.cypher().replace("r.targetQualifiers[0]", "r.sourceQualifiers[0]")),
                text("allinstances-bag-duplicates", allInstancesResult,
                        allInstancesResult.cypher().replaceAll(
                                "RETURN DISTINCT (obj\\d+)", "RETURN $1")),
                text("allinstances-display-class", allInstancesResult,
                        allInstancesResult.cypher().replaceAll(
                                "(cls\\d+:UmlClass \\{modelKey: \\$[^,}]+, )classKey:", "$1name:")),
                text("schema-instanceof-for-kindof", kindOfResult,
                        kindOfResult.cypher().replaceFirst(
                                "\\[:ObjectInstanceOf\\](?=->\\(typeCls)", "[:InstanceOf]")));

        long killed = mutants.stream().filter(this::isKilled).count();
        double score = mutants.isEmpty() ? 1.0 : (double) killed / mutants.size();
        System.out.println("COMPILER_MUTATION_SCORE=" + killed + "/" + mutants.size() + " score=" + score);
        assertEquals(mutants.size(), killed, "Surviving mutants: "
                + mutants.stream().filter(mutant -> !isKilled(mutant)).map(Mutant::name).toList());
    }

    private boolean isKilled(Mutant mutant) {
        return assertThrows(AssertionError.class, () -> GeneratedCypherContractVerifier.verify(
                mutant.result(), mutant.cypher(), mutant.parameters()), mutant.name()) != null;
    }

    private Mutant text(String name, InstrumentedCompilationResult result, String cypher) {
        return new Mutant(name, result, cypher, result.parameters());
    }

    private Mutant params(String name, InstrumentedCompilationResult result, Map<String, Object> parameters) {
        return new Mutant(name, result, result.cypher(), parameters);
    }

    private Map<String, Object> replaceCanonicalValue(Map<String, Object> parameters,
                                                       String suffix, String replacement) {
        Map<String, Object> result = new LinkedHashMap<>(parameters);
        result.replaceAll((key, value) -> value instanceof String text && text.endsWith(suffix)
                ? replacement : value);
        return Map.copyOf(result);
    }

    private Map<String, Object> withoutBottom(Map<String, Object> parameters) {
        Map<String, Object> result = new LinkedHashMap<>(parameters);
        result.entrySet().removeIf(entry -> OclBottomToken.isToken(entry.getValue()));
        return Map.copyOf(result);
    }

    private MModel model() {
        String specification = """
                model MutationModel
                class Company end
                class Person
                attributes
                    name : String
                    age : Integer
                end
                association Employment between
                    Company[1] role employer
                    Person[*] role employee
                end
                class Library end
                class Book end
                association Catalog between
                    Library[1] role library qualifier (shelf : String)
                    Book[*] role book qualifier (code : String)
                end
                """;
        StringWriter diagnostics = new StringWriter();
        MModel model = USECompiler.compileSpecification(specification, "mutation.use",
                new PrintWriter(diagnostics, true), new ModelFactory());
        assertNotNull(model, diagnostics.toString());
        return model;
    }

    private record Mutant(String name, InstrumentedCompilationResult result,
                          String cypher, Map<String, Object> parameters) {
    }
}
