package org.uet.dse.ocl2cypher.cypher;

import java.math.BigInteger;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.uet.dse.ocl2cypher.api.FrontendCompiler;
import org.uet.dse.ocl2cypher.api.ValueQueryRequest;
import org.uet.dse.ocl2cypher.core.CoreLowering;
import org.uet.dse.ocl2cypher.diagnostics.Result;
import org.uet.dse.ocl2cypher.diagnostics.Stage;
import org.uet.dse.ocl2cypher.graph.GraphBuilder;
import org.uet.dse.ocl2cypher.qcyp.QCypTranslator;
import org.uet.dse.ocl2cypher.runtime.OclType;
import org.uet.dse.ocl2cypher.runtime.OclValue;
import org.uet.dse.ocl2cypher.source.omg.OclOperation;
import org.uet.dse.ocl2cypher.source.model.CapabilityMatrix;
import org.uet.dse.ocl2cypher.source.model.SchemaModel;
import org.uet.dse.ocl2cypher.source.model.Snapshot;
import org.uet.dse.ocl2cypher.source.model.UmlAssociation;
import org.uet.dse.ocl2cypher.source.model.UmlAttribute;
import org.uet.dse.ocl2cypher.source.model.UmlClass;

/**
 * Acceptance gate for S output using Neo4j's real Cypher 5 parser.
 */
class CypherParserConformanceTest {

    private static final Set<String> WRITE_OR_UNBOUNDED_PRODUCTS = Set.of(
            "Create", "Merge", "Delete", "Remove", "SetClause", "Foreach",
            "LoadCSV", "CallClause", "UnresolvedCall", "ResolvedCall");

    @Test
    void representativeGeneratedCorpusParsesAsReadOnlyCypher5() {
        SchemaModel people = SchemaModel.builder("people")
                .clazz(UmlClass.of("Person"))
                .attribute(UmlAttribute.of("Person", "age", OclType.INTEGER))
                .build();
        Snapshot peopleData = Snapshot.builder()
                .object("alice", "Person")
                .attribute("alice", "age", new OclValue.IntegerValue(BigInteger.valueOf(20)))
                .build();
        CapabilityMatrix.entries().entrySet().stream()
                .filter(entry -> entry.getValue().admitted())
                .sorted(java.util.Map.Entry.comparingByKey())
                .forEach(entry -> {
                    var capability = entry.getValue();
                    String source = "context Person inv " + entry.getKey().name()
                            + ": " + capability.surfaceWitness();
                    assertFalse(capability.asConstructor().isBlank(), capability.testId());
                    assertFalse(capability.coreConstructor().isBlank(), capability.testId());
                    assertFalse(capability.qFamily().isBlank(), capability.testId());
                    assertFalse(capability.realizationRule().isBlank(), capability.testId());
                    assertFalse(capability.serializerSupport().isBlank(), capability.testId());
                    assertFalse(capability.oracleCoverage().isBlank(), capability.testId());
                    String rejection = expectedWitnessRejection(entry.getKey());
                    assertRealizationOutcome(compileViolationResult(source, people, peopleData),
                            rejection, source);
                    assertRealizationOutcome(compileValueResult(capability.surfaceWitness(),
                            people, peopleData), rejection, "VALUE: " + source);
                });

        SchemaModel companies = SchemaModel.builder("companies")
                .clazz(UmlClass.of("Company"))
                .clazz(UmlClass.of("Employee"))
                .attribute(UmlAttribute.of("Employee", "age", OclType.INTEGER))
                .attribute(UmlAttribute.of("Employee", "salary", OclType.INTEGER))
                .association(UmlAssociation.binary("employment", "Company", "employer",
                        "Employee", "employees"))
                .build();
        Snapshot companyData = Snapshot.builder()
                .object("acme", "Company")
                .object("e1", "Employee")
                .attribute("e1", "age", new OclValue.IntegerValue(BigInteger.valueOf(20)))
                .attribute("e1", "salary", new OclValue.IntegerValue(BigInteger.valueOf(100)))
                .link("employment", "acme", "e1")
                .build();
        List<String> navigationAndCoreCases = List.of(
                "context Company inv Staff: self.employees->size() > 0",
                "context Company inv Reject: "
                + "self.employees->reject(e | e.age < 18)->size() > 0");

        for (String source : navigationAndCoreCases) {
            assertRealizationOutcome(compileViolationResult(source, companies, companyData),
                    null, source);
        }
        String numericCollectionCarrier = "context Company inv Collected: "
                + "self.employees->collect(e | e.age)->size() > 0";
        assertRealizationOutcome(compileViolationResult(numericCollectionCarrier, companies, companyData),
                "R_NUMERIC_CAPABILITY", numericCollectionCarrier);
        // Integer decoding requires a certificate, not just toInteger().
        List<String> integerAttributeComparisons = List.of(
                "context Company inv Adults: "
                + "self.employees->select(e | e.age >= 18)"
                + "->forAll(e | e.salary > 0)",
                "context Company inv Exists: self.employees->exists(e | e.salary > 0)",
                "context Company inv LetExpr: let threshold : Integer = 18 in "
                + "self.employees->forAll(e | e.age >= threshold)");
        for (String source : integerAttributeComparisons) {
            assertRealizationOutcome(compileViolationResult(source, companies, companyData),
                    null, source);
        }
        // Keep navigation, iterators and scope in the positive parser corpus without
        // depending on uncertified numeric attributes or size operations.
        for (String source : List.of(
                "context Company inv Staff: self.employees->notEmpty()",
                "context Company inv Filter: self.employees->select(e | true)->forAll(e | true)",
                "context Company inv Reject: self.employees->reject(e | false)->notEmpty()",
                "context Company inv Exists: self.employees->exists(e | true)",
                "context Company inv Collect: self.employees->collect(e | true)->notEmpty()",
                "context Company inv Local: let flag : Boolean = true in self.employees->forAll(e | flag)",
                "context Company inv Branch: if true then true else false endif")) {
            assertReadOnlySingleQuery(compileViolation(source, companies, companyData), source);
        }
    }

    /**
     * Expectations for the exact manifest witnesses, not for all uses of an op.
     * Floor/round have Integer result: the outer Integer gate rejects first. No
     * snapshot or decimal-text heuristic supplies a range certificate.
     */
    private static String expectedWitnessRejection(OclOperation op) {
        return switch (op) {
            case REAL_DIVIDE ->
                "R-REAL-EXACT-UNSUPPORTED";
            case REAL_FLOOR, REAL_ROUND, COLLECTION_COUNT, SET_UNION, SET_INTERSECTION ->
                "R_NUMERIC_CAPABILITY";
            case BOOLEAN_NOT, BOOLEAN_AND, BOOLEAN_OR, BOOLEAN_XOR, BOOLEAN_IMPLIES, VALUE_EQUAL, VALUE_NOT_EQUAL, LESS_THAN, LESS_THAN_OR_EQUAL, GREATER_THAN, GREATER_THAN_OR_EQUAL, NUMERIC_ADD, NUMERIC_SUBTRACT, NUMERIC_MULTIPLY, NUMERIC_NEGATE, NUMERIC_ABS, NUMERIC_MIN, NUMERIC_MAX, INTEGER_DIVIDE, INTEGER_MOD, COLLECTION_SUM, COLLECTION_SIZE, COLLECTION_IS_EMPTY, COLLECTION_NOT_EMPTY, COLLECTION_INCLUDES, COLLECTION_EXCLUDES, COLLECTION_INCLUDES_ALL, COLLECTION_EXCLUDES_ALL, ALL_INSTANCES, OCL_IS_TYPE_OF, OCL_IS_KIND_OF, OCL_AS_TYPE ->
                null;
            default ->
                throw new AssertionError("Unreviewed corpus expectation: " + op);
        };
    }

    private static void assertRealizationOutcome(Result<CypherAst.GeneratedArtifact> result,
            String rejection, String source) {
        if (rejection == null) {
            assertTrue(result.isSuccess(), () -> source + ": " + result.diagnostics());
            assertReadOnlySingleQuery(Serializer.cypherText(result.value()), source);
        } else {
            assertTrue(result.isFailure(), "Expected rejection: " + source);
            assertEquals(Stage.R, result.primaryDiagnostic().stage(), source);
            assertEquals(rejection, result.primaryDiagnostic().code(), source);
        }
    }

    @Test
    void parserRejectsMalformedTextAndStructuralProjectionSeesDirectionMutation() {
        assertThrows(RuntimeException.class,
                () -> Neo4jCypherParserGate.parse("MATCH (self RETURN self AS result"));
        assertThrows(RuntimeException.class,
                () -> Neo4jCypherParserGate.parse("RETURN CASE WHEN true THEN 1 AS result"));

        var outgoing = Neo4jCypherParserGate.parse(
                "CYPHER 5 MATCH (a)-[r]->(b) RETURN b AS result");
        var incoming = Neo4jCypherParserGate.parse(
                "CYPHER 5 MATCH (a)<-[r]-(b) RETURN b AS result");
        assertNotEquals(outgoing.canonicalTree(), incoming.canonicalTree(),
                "canonical AST projection must retain relationship direction");

        var plainReturn = Neo4jCypherParserGate.parse(
                "CYPHER 5 RETURN 1 AS result");
        var distinctReturn = Neo4jCypherParserGate.parse(
                "CYPHER 5 RETURN DISTINCT 1 AS result");
        assertNotEquals(plainReturn.canonicalTree(), distinctReturn.canonicalTree(),
                "canonical AST projection must retain DISTINCT");

        assertThrows(RuntimeException.class,
                () -> Neo4jCypherParserGate.parse(
                        "CYPHER 5 RETURN any(x [1] WHERE x = 1) AS result"));
        assertThrows(IllegalArgumentException.class,
                () -> assertReadOnlySingleQuery("CYPHER 25\nRETURN 1 AS result", "bad header"));
        assertThrows(IllegalArgumentException.class,
                () -> assertReadOnlySingleQuery("CYPHER 5\n", "empty statement"));
    }

    @Test
    void targetProfileRejectsWriteProcedureDynamicAndUnboundedPathText() {
        assertProfileRejected("CYPHER 5\nCREATE (n) RETURN n AS result");
        assertProfileRejected("CYPHER 5\nCALL db.labels() YIELD label RETURN label AS result");
        assertProfileRejected("CYPHER 5\nMATCH (a)-[*]->(b) RETURN b AS result");
        assertProfileRejected("CYPHER 5\nMATCH (n:$($label)) RETURN n AS result");
    }

    private static String compileViolation(String source, SchemaModel schema, Snapshot snapshot) {
        var artifact = compileViolationResult(source, schema, snapshot);
        assertTrue(artifact.isSuccess(), () -> "R: " + artifact.diagnostics());
        return Serializer.cypherText(artifact.value());
    }

    private static Result<CypherAst.GeneratedArtifact> compileViolationResult(
            String source, SchemaModel schema, Snapshot snapshot) {
        var frontend = FrontendCompiler.compile(source, schema);
        assertTrue(frontend.isSuccess(), () -> "frontend: " + frontend.diagnostics());
        var document = frontend.value().get(0);
        var core = CoreLowering.lower(schema, document, document.constraints.get(0));
        assertTrue(core.isSuccess(), () -> "core: " + core.diagnostics());
        var graph = GraphBuilder.build(schema, snapshot);
        assertTrue(graph.isSuccess(), () -> "graph: " + graph.diagnostics());
        var query = QCypTranslator.translate(core.value());
        assertTrue(query.isSuccess(), () -> "Q: " + query.diagnostics());
        return Realization.realize(query.value(), graph.value().graph(),
                CypherAst.Dialect.CYPHER_5);
    }

    private static Result<CypherAst.GeneratedArtifact> compileValueResult(
            String expression, SchemaModel schema, Snapshot snapshot) {
        var frontend = FrontendCompiler.compileValueQuery(
                ValueQueryRequest.contextual(expression, "Person"), schema);
        assertTrue(frontend.isSuccess(), () -> "VALUE frontend: " + frontend.diagnostics());
        var core = CoreLowering.lowerValueQuery(schema, frontend.value());
        assertTrue(core.isSuccess(), () -> "VALUE core: " + core.diagnostics());
        var graph = GraphBuilder.build(schema, snapshot);
        assertTrue(graph.isSuccess(), () -> "VALUE graph: " + graph.diagnostics());
        var query = QCypTranslator.translate(core.value());
        assertTrue(query.isSuccess(), () -> "VALUE Q: " + query.diagnostics());
        return Realization.realize(query.value(), graph.value().graph(),
                CypherAst.Dialect.CYPHER_5);
    }

    private static void assertReadOnlySingleQuery(String text, String source) {
        Neo4jCypherParserGate.Parsed parsed = Neo4jCypherParserGate.parse(text);
        assertEquals("CYPHER 5", parsed.dialectHeader(), source);
        assertTrue(parsed.has("SingleQuery"), source + "\n" + parsed.canonicalTree());
        assertTrue(parsed.has("Return"), source + "\n" + parsed.canonicalTree());
        for (String forbidden : WRITE_OR_UNBOUNDED_PRODUCTS) {
            assertFalse(parsed.has(forbidden),
                    () -> source + " emitted forbidden AST product " + forbidden);
        }
        assertFalse(parsed.hasUnboundedRelationship(),
                () -> source + " emitted an unbounded variable-length relationship");
        assertFalse(parsed.hasDynamicLabelOrType(),
                () -> source + " emitted a dynamic label or relationship type");
    }

    private static void assertProfileRejected(String text) {
        assertThrows(AssertionError.class,
                () -> assertReadOnlySingleQuery(text, "negative target-profile case"));
    }
}
