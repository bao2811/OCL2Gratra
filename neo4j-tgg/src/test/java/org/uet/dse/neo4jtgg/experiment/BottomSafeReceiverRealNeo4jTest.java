package org.uet.dse.neo4jtgg.experiment;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Session;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.uet.dse.neo4j.encoding.CanonicalGraphEncoding;
import org.uet.dse.neo4j.manager.Neo4jDriverManager;
import org.uet.dse.neo4j.manager.SessionManager;
import org.uet.dse.neo4j.sync.helper.CanonicalScalarValueCodec;
import org.uet.dse.neo4jtgg.service.impl.DefaultOclToCypherCompiler;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Opt-in selected-runtime evidence for PO-12 receiver guards, alias
 * correlation, the C5a whole-collection-bottom boundary, and validation truth
 * for a non-null bottom element token.
 */
class BottomSafeReceiverRealNeo4jTest {
    @Test
    void bottomAndEntityArmsExecuteWithExpectedViolationSets() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("neo4j.bottom.receiver.it"),
                "Run with -Dneo4j.bottom.receiver.it=true and configured Neo4j");
        Neo4jEnvironmentConfig config = Neo4jEnvironmentConfig.load();
        String modelName = "BottomReceiver" + System.currentTimeMillis();
        String modelKey = CanonicalGraphEncoding.modelKey(modelName);
        MModel model = model(modelName);
        connect(config);
        try (Session session = Neo4jDriverManager.getInstance().openSession()) {
            seed(session, modelName, modelKey);
            DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
            assertEquals(Set.of("p0"), violations(session, compiler,
                    "context Person inv BottomAttribute: (if self.age > 0 then self else null endif).age = self.age"));
            assertEquals(Set.of("p0"), violations(session, compiler,
                    "context Person inv BottomKind: (if self.age > 0 then self else null endif).oclIsKindOf(Person)"));
            assertEquals(Set.of("p0"), violations(session, compiler,
                    "context Person inv BottomCast: (if self.age > 0 then self else null endif).oclAsType(Person).age = self.age"));
            assertEquals(Set.of("p2"), violations(session, compiler,
                    "context Person inv NestedAliases: Person.allInstances()->forAll(p | "
                            + "(if p.age > self.age then p else null endif).oclIsKindOf(Person) or p = self)"));
            assertEquals(Set.of("p0"), violations(session, compiler,
                    "context Person inv CollectionBottom: "
                            + "(if self.age > 0 then null else Set{1} endif)->isEmpty()"));
            assertEquals(Set.of(), violations(session, compiler,
                    "context Person inv BottomBooleanElement: "
                            + "Set{null, true}->exists(x | x)"));
            assertEquals(Set.of(), violations(session, compiler,
                    "context Person inv NullCollectionOperand: "
                            + "Set{1}->includesAll(if self.age >= 0 then null else Set{1} endif)"));
            assertEquals(Set.of(), violations(session, compiler,
                    "context Person inv BottomElementEquality: "
                            + "Set{null,1}->exists(x | x = null)"));
            assertEquals(Set.of("p0", "p2"), violations(session, compiler,
                    "context Person inv BottomElementOrdering: "
                            + "Set{null,1}->forAll(x | x > 0)"));
            assertEquals(Set.of(), violations(session, compiler,
                    "context Person inv BottomElementArithmetic: "
                            + "Set{null,1}->exists(x | x + 1 = 2)"));
            assertEquals(Set.of(), violations(session, compiler,
                    "context Person inv VoidEqualsEmptySet: "
                            + "null = Set{1}->select(x | false)"));
        } finally {
            Neo4jDriverManager manager = Neo4jDriverManager.getInstance();
            if (manager != null) {
                try (Session cleanup = manager.openSession()) {
                    cleanup.run("MATCH (n {modelKey:$modelKey}) DETACH DELETE n", Map.of("modelKey", modelKey)).consume();
                }
                manager.close();
            }
        }
    }

    private Set<String> violations(Session session, DefaultOclToCypherCompiler compiler, String invariant) {
        InstrumentedCompilationResult result = compiler.compileInvariantInstrumented(invariant);
        Set<String> ids = new LinkedHashSet<>();
        try {
            session.run(result.cypher(), result.parameters()).list()
                    .forEach(record -> ids.add(record.get("useId").asString()));
        } catch (RuntimeException exception) {
            throw new AssertionError(result.cypher(), exception);
        }
        return ids;
    }

    private void seed(Session session, String modelName, String modelKey) {
        String classKey = CanonicalGraphEncoding.classKey(modelName, "Person");
        String attributeKey = CanonicalGraphEncoding.attributeKey(modelName, "Person", "age");
        String p0Key = CanonicalGraphEncoding.objectKey(modelName, "p0");
        String p2Key = CanonicalGraphEncoding.objectKey(modelName, "p2");
        session.run("CREATE (c:UmlClass {modelKey:$modelKey,classKey:$classKey}) "
                        + "CREATE (p0:Object {modelKey:$modelKey,objectKey:$p0Key,use_id:'p0'})-[:ObjectInstanceOf]->(c) "
                        + "CREATE (p2:Object {modelKey:$modelKey,objectKey:$p2Key,use_id:'p2'})-[:ObjectInstanceOf]->(c) "
                        + "CREATE (a0:AttributeValue {modelKey:$modelKey,attributeKey:$attributeKey,value:$p0Age}) "
                        + "CREATE (a2:AttributeValue {modelKey:$modelKey,attributeKey:$attributeKey,value:$p2Age}) "
                        + "CREATE (p0)-[:ObjectHasAttribute]->(a0) CREATE (p2)-[:ObjectHasAttribute]->(a2)",
                Map.of("modelKey", modelKey, "classKey", classKey, "attributeKey", attributeKey,
                        "p0Key", p0Key, "p2Key", p2Key,
                        "p0Age", CanonicalScalarValueCodec.encode(0L, "Integer"),
                        "p2Age", CanonicalScalarValueCodec.encode(2L, "Integer"))).consume();
    }

    private MModel model(String modelName) {
        String specification = "model " + modelName + "\nclass Person\nattributes age : Integer\nend\n";
        StringWriter diagnostics = new StringWriter();
        MModel model = USECompiler.compileSpecification(specification, "bottom-safe-real.use",
                new PrintWriter(diagnostics, true), new ModelFactory());
        assertNotNull(model, diagnostics.toString());
        return model;
    }

    private void connect(Neo4jEnvironmentConfig config) throws Exception {
        Neo4jDriverManager.connect(config.uri(), config.user(), config.password(), config.database(), false, false);
        SessionManager identity = new SessionManager();
        identity.createNewSession();
        Neo4jDriverManager.getInstance().setSessionManager(identity);
    }
}
