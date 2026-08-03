package org.uet.dse.neo4jtgg.experiment;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.tzi.use.api.UseModelApi;
import org.tzi.use.api.UseSystemApi;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.uml.mm.MAssociationEnd;
import org.tzi.use.uml.mm.MAttribute;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.ocl.value.StringValue;
import org.tzi.use.uml.ocl.value.Value;
import org.tzi.use.uml.sys.MLink;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uml.sys.MSystem;
import org.uet.dse.neo4j.encoding.CanonicalGraphEncoding;
import org.uet.dse.neo4j.helper.ValueMapper;
import org.uet.dse.neo4j.manager.Neo4jDriverManager;
import org.uet.dse.neo4j.manager.SessionManager;
import org.uet.dse.neo4j.repo.Neo4jObjectRepository;
import org.uet.dse.neo4j.sync.helper.QualifierValueCodec;
import org.uet.dse.neo4j.sync.model.CoreModelPushService;
import org.uet.dse.neo4j.sync.object.ObjectDiff;
import org.uet.dse.neo4j.sync.object.ObjectPushService;
import org.uet.dse.neo4j.sync.object.ObjectSnapshotCompare;
import org.uet.dse.neo4jtgg.service.impl.DefaultOclToCypherCompiler;
import org.uet.dse.neo4jtgg.ocl.Neo4jOclMetamodelSnapshotReader;
import org.uet.dse.neo4jtgg.ocl.OclMetamodelSnapshot;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Opt-in graph-level evaluation for Theorem 0 plus portable E2/E3 metrics.
 * The test uses a dedicated model key and never clears unrelated data.
 */
class RepresentationEvaluationRealNeo4jTest {
    private static final int DEFAULT_SAMPLES = 7;

    @Test
    void evaluatesR1ThroughR7AndDetectsInjectedCounterexamples() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("neo4j.representation.it"),
                "Run with -Dneo4j.representation.it=true");
        String modelName = "RepresentationEval_" + System.currentTimeMillis();
        String modelKey = CanonicalGraphEncoding.modelKey(modelName);
        connect(Neo4jEnvironmentConfig.load());
        try {
            MModel model = compileModel(modelName);
            UseSystemApi api = seedUseModel(model);
            long encodingStart = System.nanoTime();
            new CoreModelPushService(new UseModelApi(model)).pushModelToNeo4j();
            ObjectDiff diff = new ObjectSnapshotCompare(api.getSystem()).compareObjects();
            new ObjectPushService(new Neo4jObjectRepository(), api.getSystem()).pushToNeo4j(diff);
            long encodingNs = System.nanoTime() - encodingStart;

            verifyAllInstancesSetSemanticsUnderDuplicateMembership(model, modelKey);

            RepresentationAdequacyEvaluator.Snapshot source = sourceSnapshot(api.getSystem(), modelName);
            RepresentationAdequacyEvaluator.Snapshot graph = graphSnapshot(modelKey);
            RepresentationAdequacyEvaluator.Report baseline =
                    RepresentationAdequacyEvaluator.evaluate(source, graph);
            System.out.println("REPRESENTATION_BASELINE\n" + baseline.render());
            System.out.println("REPRESENTATION_CSV\n" + baseline.toCsv(
                    "qualified-inheritance-small", CanonicalGraphEncoding.PROFILE_ID));
            assertTrue(baseline.passed(), baseline.render());

            OclMetamodelSnapshot useM2 = OclMetamodelSnapshot.fromUse(model);
            OclMetamodelSnapshot graphM2 = new Neo4jOclMetamodelSnapshotReader(
                    Neo4jDriverManager.getInstance().getDriver(),
                    Neo4jDriverManager.getInstance().getActiveDatabase()).read(modelName);
            assertEquals(useM2, graphM2, "Graph-backed M2 view differs from USE MModel");
            String binderProbe = "context Library inv GraphM2Binder: self.book['HCM']->notEmpty()";
            var useCompilation = new DefaultOclToCypherCompiler(model).compileInvariantInstrumented(binderProbe);
            var graphCompilation = new DefaultOclToCypherCompiler(graphM2).compileInvariantInstrumented(binderProbe);
            assertEquals(useCompilation.cypher(), graphCompilation.cypher());
            assertEquals(useCompilation.parameters(), graphCompilation.parameters());

            GraphEvaluationMetrics metrics = collectMetrics(modelKey, encodingNs, model);
            System.out.println(GraphEvaluationMetrics.csvHeader());
            System.out.println(metrics.toCsvRow("qualified-inheritance-small", CanonicalGraphEncoding.PROFILE_ID));
            metrics.primitiveLatencies().forEach((name, latency) ->
                    System.out.println("REPRESENTATION_LATENCY=" + name
                            + " samples=" + latency.samples()
                            + " medianNs=" + latency.medianNs()
                            + " p95Ns=" + latency.p95Ns()));

            injectCounterexamples(modelKey);
            RepresentationAdequacyEvaluator.Report mutated =
                    RepresentationAdequacyEvaluator.evaluate(source, graphSnapshot(modelKey));
            System.out.println("REPRESENTATION_MUTATED\n" + mutated.render());
            System.out.println("REPRESENTATION_MUTATED_CSV\n" + mutated.toCsv(
                    "qualified-inheritance-small-mutated", CanonicalGraphEncoding.PROFILE_ID));
            assertFalse(mutated.passed(), "Injected faults must be detected");
            assertFalse(obligation(mutated, "R1").details().isEmpty(), mutated.render());
            assertFalse(obligation(mutated, "R2").spurious().isEmpty(), mutated.render());
            assertFalse(obligation(mutated, "R3").missing().isEmpty(), mutated.render());
            assertFalse(obligation(mutated, "PA4").missing().isEmpty(), mutated.render());
            assertFalse(obligation(mutated, "PA4").spurious().isEmpty(), mutated.render());
            assertFalse(obligation(mutated, "R4").missing().isEmpty(), mutated.render());
            assertFalse(obligation(mutated, "R4").spurious().isEmpty(), mutated.render());
            assertFalse(obligation(mutated, "PA5").missing().isEmpty(), mutated.render());
            assertFalse(obligation(mutated, "PA5").spurious().isEmpty(), mutated.render());
            assertFalse(obligation(mutated, "PA5").details().isEmpty(), mutated.render());
            assertFalse(obligation(mutated, "R5").missing().isEmpty(), mutated.render());
            assertFalse(obligation(mutated, "R5").spurious().isEmpty(), mutated.render());
            assertFalse(obligation(mutated, "PA6").missing().isEmpty(), mutated.render());
            assertFalse(obligation(mutated, "PA6").spurious().isEmpty(), mutated.render());
            assertFalse(obligation(mutated, "PA6").details().isEmpty(), mutated.render());
            assertFalse(obligation(mutated, "R6").missing().isEmpty(), mutated.render());
            assertFalse(obligation(mutated, "PA7").missing().isEmpty(), mutated.render());
            assertFalse(obligation(mutated, "PA7").spurious().isEmpty(), mutated.render());
            assertFalse(obligation(mutated, "R7").missing().isEmpty(), mutated.render());
            assertFalse(obligation(mutated, "PA8").missing().isEmpty(), mutated.render());
            assertFalse(obligation(mutated, "PA8").spurious().isEmpty(), mutated.render());
            assertFalse(obligation(mutated, "KEY").details().isEmpty(), mutated.render());
        } finally {
            cleanup(modelName, modelKey);
            Neo4jDriverManager manager = Neo4jDriverManager.getInstance();
            if (manager != null) manager.close();
        }
    }

    private UseSystemApi seedUseModel(MModel model) throws Exception {
        UseSystemApi api = UseSystemApi.create(model, false);
        api.createObject("Library", "central");
        api.createObject("Book", "book_a");
        api.createObject("Book", "book_b");
        api.setAttributeValue("central", "name", "'Central Library'");
        api.setAttributeValue("book_a", "title", "'A'");
        api.setAttributeValue("book_b", "title", "'B'");
        api.createLinkEx(model.getAssociation("Catalog"),
                new MObject[]{api.getObject("central"), api.getObject("book_a")},
                new Value[][]{{new StringValue("HCM")}, {new StringValue("A1")}});
        api.createLinkEx(model.getAssociation("Catalog"),
                new MObject[]{api.getObject("central"), api.getObject("book_b")},
                new Value[][]{{new StringValue("HCM")}, {new StringValue("B2")}});
        return api;
    }

    static RepresentationAdequacyEvaluator.Snapshot sourceSnapshot(MSystem system, String modelName) {
        Map<String, Integer> objects = new LinkedHashMap<>();
        List<RepresentationAdequacyEvaluator.ObjectObservation> objectObservations = new ArrayList<>();
        Set<RepresentationAdequacyEvaluator.TypeFact> types = new LinkedHashSet<>();
        Set<RepresentationAdequacyEvaluator.AttributeFact> attributes = new LinkedHashSet<>();
        List<RepresentationAdequacyEvaluator.AttributeObservation> attributeObservations = new ArrayList<>();
        Set<RepresentationAdequacyEvaluator.LinkFact> links = new LinkedHashSet<>();
        List<RepresentationAdequacyEvaluator.LinkObservation> linkObservations = new ArrayList<>();
        Map<String, Set<String>> keys = sourceKeys(system.model(), modelName);

        var state = system.state();
        for (MObject object : state.allObjects()) {
            objects.put(object.name(), 1);
            objectObservations.add(new RepresentationAdequacyEvaluator.ObjectObservation(
                    "source:" + object.name(), object.name(),
                    CanonicalGraphEncoding.objectKey(modelName, object.name())));
            types.add(new RepresentationAdequacyEvaluator.TypeFact(object.name(),
                    CanonicalGraphEncoding.classKey(modelName, object.cls().name())));
            object.cls().allParents().forEach(parent -> types.add(
                    new RepresentationAdequacyEvaluator.TypeFact(object.name(),
                            CanonicalGraphEncoding.classKey(modelName, parent.name()))));
            for (MAttribute attribute : object.cls().allAttributes()) {
                Object mapped = ValueMapper.mapUseValue(object.state(state).attributeValue(attribute));
                String attributeKey = CanonicalGraphEncoding.attributeKey(
                        modelName, attribute.owner().name(), attribute.name());
                String payload = expectedStoredPayload(mapped);
                attributes.add(new RepresentationAdequacyEvaluator.AttributeFact(
                        object.name(), attributeKey, payload));
                attributeObservations.add(new RepresentationAdequacyEvaluator.AttributeObservation(
                        "source:" + object.name() + ":" + attributeKey,
                        object.name(), attributeKey, payload,
                        CanonicalGraphEncoding.attributeSlotKey(
                                modelName, object.name(), attribute.owner().name(), attribute.name())));
            }
        }
        for (MLink link : state.allLinks()) {
            if (link.linkedObjects().size() != 2) continue;
            MAssociationEnd sourceEnd = link.association().associationEnds().get(0);
            MAssociationEnd targetEnd = link.association().associationEnds().get(1);
            List<List<String>> qualifiers = new ArrayList<>();
            link.getQualifier().forEach(values -> qualifiers.add(QualifierValueCodec.encodeQualifierValues(values)));
            while (qualifiers.size() < 2) qualifiers.add(List.of());
            String associationKey = CanonicalGraphEncoding.associationKey(modelName, link.association().name());
            String sourceId = link.linkedObjects().get(0).name();
            String targetId = link.linkedObjects().get(1).name();
            links.add(new RepresentationAdequacyEvaluator.LinkFact(
                    associationKey, sourceId, targetId, sourceEnd.name(), targetEnd.name(),
                    qualifiers.get(0), qualifiers.get(1)));
            linkObservations.add(new RepresentationAdequacyEvaluator.LinkObservation(
                    "source:" + link,
                    CanonicalGraphEncoding.binaryLinkKey(modelName, link.association().name(),
                            sourceId, targetId, qualifiers.get(0), qualifiers.get(1)),
                    associationKey, sourceId, targetId, sourceEnd.name(), targetEnd.name(),
                    qualifiers.get(0), qualifiers.get(1)));
        }
        return new RepresentationAdequacyEvaluator.Snapshot(
                objects, types, attributes, links, keys, 0, objectObservations,
                attributeObservations, linkObservations);
    }

    /** Independent oracle for the canonical AttributeValue.value payload. */
    private static String expectedStoredPayload(Object value) {
        if (value == null || "Undefined".equals(value)) return "Undefined";
        if (value instanceof Map<?, ?> collection) {
            Object rawItems = collection.get("items");
            List<?> items = rawItems instanceof List<?> list ? list : List.of();
            if (items.isEmpty()) return "COLLECTION_EMPTY";
            return items.stream()
                    .map(item -> item == null ? "null" : item.toString())
                    .collect(java.util.stream.Collectors.joining(" | "));
        }
        return value.toString();
    }

    private static Map<String, Set<String>> sourceKeys(MModel model, String modelName) {
        Map<String, Set<String>> keys = new LinkedHashMap<>();
        model.classes().forEach(cls -> {
            keys.put(CanonicalGraphEncoding.classKey(modelName, cls.name()), Set.of(cls.name()));
            cls.attributes().forEach(attribute -> keys.put(
                    CanonicalGraphEncoding.attributeKey(modelName, cls.name(), attribute.name()),
                    Set.of(cls.name() + "_" + attribute.name())));
        });
        model.associations().forEach(association -> keys.put(
                CanonicalGraphEncoding.associationKey(modelName, association.name()), Set.of(association.name())));
        return Map.copyOf(keys);
    }

    static RepresentationAdequacyEvaluator.Snapshot graphSnapshot(String modelKey) {
        Map<String, Integer> objects = new LinkedHashMap<>();
        List<RepresentationAdequacyEvaluator.ObjectObservation> objectObservations = new ArrayList<>();
        Set<RepresentationAdequacyEvaluator.TypeFact> types = new LinkedHashSet<>();
        Set<RepresentationAdequacyEvaluator.AttributeFact> attributes = new LinkedHashSet<>();
        List<RepresentationAdequacyEvaluator.AttributeObservation> attributeObservations = new ArrayList<>();
        Set<RepresentationAdequacyEvaluator.LinkFact> links = new LinkedHashSet<>();
        List<RepresentationAdequacyEvaluator.LinkObservation> linkObservations = new ArrayList<>();
        Set<RepresentationAdequacyEvaluator.AllInstancesFact> allInstances = new LinkedHashSet<>();
        Map<String, Set<String>> keys = new LinkedHashMap<>();
        long linkRows = 0;
        try (Session session = Neo4jDriverManager.getInstance().openSession()) {
            session.run("MATCH (o:Object {modelKey:$modelKey}) "
                            + "RETURN elementId(o) AS token, o.use_id AS id, o.objectKey AS objectKey",
                    Map.of("modelKey", modelKey)).forEachRemaining(record -> {
                String id = record.get("id").isNull() ? null : record.get("id").asString();
                String objectKey = record.get("objectKey").isNull()
                        ? null : record.get("objectKey").asString();
                objectObservations.add(new RepresentationAdequacyEvaluator.ObjectObservation(
                        record.get("token").asString(), id, objectKey));
                if (id != null && !id.isBlank()) objects.merge(id, 1, Integer::sum);
            });
            session.run("MATCH (o:Object {modelKey:$modelKey})-[:ObjectInstanceOf]->(c) "
                            + "RETURN o.use_id AS id, c.classKey AS classKey",
                    Map.of("modelKey", modelKey)).forEachRemaining(record -> types.add(
                    new RepresentationAdequacyEvaluator.TypeFact(record.get("id").asString(),
                             record.get("classKey").asString())));
            session.run("MATCH (o)-[:ObjectInstanceOf]->(c {modelKey:$modelKey}) "
                            + "WHERE c.classKey IS NOT NULL "
                            + "RETURN DISTINCT c.classKey AS classKey, o.use_id AS id",
                    Map.of("modelKey", modelKey)).forEachRemaining(record -> allInstances.add(
                    new RepresentationAdequacyEvaluator.AllInstancesFact(
                            record.get("classKey").asString(), record.get("id").asString())));
            session.run("MATCH (o:Object {modelKey:$modelKey})-[:ObjectHasAttribute]->(v:AttributeValue) "
                            + "RETURN elementId(v) AS token, o.use_id AS id, v.attributeKey AS attributeKey, "
                            + "v.value AS value, v.slotKey AS slotKey",
                    Map.of("modelKey", modelKey)).forEachRemaining(record -> {
                String id = record.get("id").isNull() ? null : record.get("id").asString();
                String attributeKey = record.get("attributeKey").isNull()
                        ? null : record.get("attributeKey").asString();
                String value = record.get("value").isNull() ? null : record.get("value").asString();
                String slotKey = record.get("slotKey").isNull() ? null : record.get("slotKey").asString();
                attributeObservations.add(new RepresentationAdequacyEvaluator.AttributeObservation(
                        record.get("token").asString(), id, attributeKey, value, slotKey));
                if (id != null && attributeKey != null && value != null) {
                    attributes.add(new RepresentationAdequacyEvaluator.AttributeFact(id, attributeKey, value));
                }
            });
            Result linkResult = session.run("MATCH (a:Object {modelKey:$modelKey})-[r]->"
                            + "(b:Object {modelKey:$modelKey}) "
                            + "WHERE type(r) STARTS WITH 'Link' "
                            + "RETURN elementId(r) AS token,r.linkKey AS linkKey,"
                            + "r.associationKey AS associationKey, a.use_id AS source, b.use_id AS target, "
                            + "r.sourceRole AS sourceRole, r.targetRole AS targetRole, "
                            + "r.sourceQualifiers AS sourceQualifiers,r.targetQualifiers AS targetQualifiers",
                    Map.of("modelKey", modelKey));
            while (linkResult.hasNext()) {
                var record = linkResult.next();
                linkRows++;
                String associationKey = record.get("associationKey").isNull()
                        ? null : record.get("associationKey").asString();
                String source = record.get("source").isNull() ? null : record.get("source").asString();
                String target = record.get("target").isNull() ? null : record.get("target").asString();
                String sourceRole = record.get("sourceRole").isNull()
                        ? null : record.get("sourceRole").asString();
                String targetRole = record.get("targetRole").isNull()
                        ? null : record.get("targetRole").asString();
                List<String> sourceQualifiers = record.get("sourceQualifiers").isNull()
                        ? null : record.get("sourceQualifiers").asList(v -> v.isNull() ? null : v.asString());
                List<String> targetQualifiers = record.get("targetQualifiers").isNull()
                        ? null : record.get("targetQualifiers").asList(v -> v.isNull() ? null : v.asString());
                linkObservations.add(new RepresentationAdequacyEvaluator.LinkObservation(
                        record.get("token").asString(),
                        record.get("linkKey").isNull() ? null : record.get("linkKey").asString(),
                        associationKey, source, target, sourceRole, targetRole,
                        sourceQualifiers, targetQualifiers));
                if (associationKey != null && source != null && target != null
                        && sourceRole != null && targetRole != null
                        && sourceQualifiers != null && targetQualifiers != null) {
                    links.add(new RepresentationAdequacyEvaluator.LinkFact(
                            associationKey, source, target, sourceRole, targetRole,
                            sourceQualifiers, targetQualifiers));
                }
            }
            collectKeyOwners(session, "MATCH (n {modelKey:$modelKey}) WHERE n.classKey IS NOT NULL "
                    + "RETURN n.classKey AS key, n.name AS owner", modelKey, keys);
            collectKeyOwners(session, "MATCH (n:Attribute {modelKey:$modelKey}) WHERE n.attributeKey IS NOT NULL "
                    + "RETURN n.attributeKey AS key, n.name AS owner", modelKey, keys);
            collectKeyOwners(session, "MATCH (a {modelKey:$modelKey})-[r]->(b {modelKey:$modelKey}) "
                    + "WHERE r.modelKey=$modelKey "
                    + "AND r.associationKey IS NOT NULL AND NOT type(r) STARTS WITH 'Link' "
                    + "RETURN r.associationKey AS key, r.associationName AS owner", modelKey, keys);
        }
        return new RepresentationAdequacyEvaluator.Snapshot(objects, types, attributes, links, keys,
                linkRows - links.size(), objectObservations, attributeObservations, linkObservations,
                allInstances);
    }

    private void verifyAllInstancesSetSemanticsUnderDuplicateMembership(MModel model, String modelKey) {
        String publicationKey = CanonicalGraphEncoding.classKey(model.name(), "Publication");
        try (Session session = Neo4jDriverManager.getInstance().openSession()) {
            session.run("MATCH (o:Object {modelKey:$modelKey,use_id:'book_a'}),"
                            + "(c {classKey:$publicationKey}) "
                            + "CREATE (o)-[:ObjectInstanceOf]->(c)",
                    Map.of("modelKey", modelKey, "publicationKey", publicationKey)).consume();

            var compiled = new DefaultOclToCypherCompiler(model)
                    .compile("Publication.allInstances()->size()");
            assertTrue(compiled.isSupported(), compiled.getReason());
            assertTrue(compiled.getCypher().contains("RETURN DISTINCT"), compiled.getCypher());
            long size = session.run(compiled.getCypher(), compiled.getParameters())
                    .single().get("value").asLong();
            assertEquals(2L, size, "duplicate ObjectInstanceOf edges must not duplicate allInstances");
        }
    }

    private static void collectKeyOwners(Session session, String query, String modelKey,
                                         Map<String, Set<String>> target) {
        session.run(query, Map.of("modelKey", modelKey)).forEachRemaining(record -> {
            String key = record.get("key").asString();
            String owner = record.get("owner").asString();
            target.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(owner);
        });
    }

    private GraphEvaluationMetrics collectMetrics(String modelKey, long encodingNs, MModel model) {
        int samples = Integer.getInteger("neo4j.representation.samples", DEFAULT_SAMPLES);
        GraphEvaluationMetrics.LatencyCollector latency = new GraphEvaluationMetrics.LatencyCollector();
        Map<String, String> observations = Map.of(
                "object", "MATCH (o:Object {modelKey:$modelKey, use_id:'book_a'}) RETURN o",
                "type", "MATCH (:Object {modelKey:$modelKey, use_id:'book_a'})-[:ObjectInstanceOf]->(c) RETURN c.classKey",
                "attribute", "MATCH (:Object {modelKey:$modelKey, use_id:'book_a'})-[:ObjectHasAttribute]->(v) RETURN v.value",
                "navigation-forward", "MATCH (:Object {modelKey:$modelKey, use_id:'central'})-[r]->(b) WHERE type(r) STARTS WITH 'Link' AND r.sourceQualifiers=[\"'HCM'\"] RETURN b.use_id",
                "navigation-reverse", "MATCH (:Object {modelKey:$modelKey, use_id:'book_a'})<-[r]-(l) WHERE type(r) STARTS WITH 'Link' AND r.targetQualifiers=[\"'A1'\"] RETURN l.use_id",
                "allInstances", "MATCH (o:Object {modelKey:$modelKey})-[:ObjectInstanceOf]->(c {classKey:$publicationKey}) RETURN o.use_id");
        Map<String, Object> parameters = Map.of("modelKey", modelKey,
                "publicationKey", CanonicalGraphEncoding.classKey(model.name(), "Publication"));
        try (Session session = Neo4jDriverManager.getInstance().openSession()) {
            for (Map.Entry<String, String> observation : observations.entrySet()) {
                session.run(observation.getValue(), parameters).list();
                for (int sample = 0; sample < samples; sample++) {
                    long start = System.nanoTime();
                    session.run(observation.getValue(), parameters).list();
                    latency.add(observation.getKey(), System.nanoTime() - start);
                }
            }
            DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
            long compileStart = System.nanoTime();
            var compiled = compiler.compile("context Library inv HasHCM: self.book['HCM']->notEmpty()");
            latency.add("validation-compile", System.nanoTime() - compileStart);
            assertTrue(compiled.isSupported(), compiled.getReason());
            session.run(compiled.getCypher(), compiled.getParameters()).list();
            for (int sample = 0; sample < samples; sample++) {
                long start = System.nanoTime();
                session.run(compiled.getCypher(), compiled.getParameters()).list();
                latency.add("validation-query", System.nanoTime() - start);
            }

            long nodes = scalar(session, "MATCH (n {modelKey:$modelKey}) RETURN count(n) AS value", parameters);
            long relationships = scalar(session, "MATCH (a {modelKey:$modelKey})-[r]->(b {modelKey:$modelKey}) RETURN count(r) AS value", parameters);
            long nodeProperties = scalar(session, "MATCH (n {modelKey:$modelKey}) RETURN coalesce(sum(size(keys(n))),0) AS value", parameters);
            long relationshipProperties = scalar(session, "MATCH (a {modelKey:$modelKey})-[r]->(b {modelKey:$modelKey}) RETURN coalesce(sum(size(keys(r))),0) AS value", parameters);
            long nodePayload = payloadSize(session,
                    "MATCH (n {modelKey:$modelKey}) RETURN properties(n) AS properties", parameters);
            long relationshipPayload = payloadSize(session,
                    "MATCH (a {modelKey:$modelKey})-[r]->(b {modelKey:$modelKey}) "
                            + "RETURN properties(r) AS properties", parameters);
            long maxTypeDegree = scalar(session, "MATCH (o:Object {modelKey:$modelKey})-[:ObjectInstanceOf]->(c) WITH c,count(o) AS degree RETURN coalesce(max(degree),0) AS value", parameters);
            long metamodelElements = model.classes().size()
                    + model.classes().stream().mapToLong(cls -> cls.attributes().size()).sum()
                    + model.associations().size()
                    + model.classes().stream().mapToLong(cls -> cls.parents().size()).sum();
            return new GraphEvaluationMetrics(metamodelElements, 3, 3, 2, nodes, relationships,
                    nodeProperties + relationshipProperties,
                    nodePayload + relationshipPayload,
                    maxTypeDegree, encodingNs, latency.summarize());
        }
    }

    private long scalar(Session session, String query, Map<String, Object> parameters) {
        return session.run(query, parameters).single().get("value").asLong();
    }

    private long payloadSize(Session session, String query, Map<String, Object> parameters) {
        return session.run(query, parameters).list().stream()
                .mapToLong(record -> record.get("properties").asMap().entrySet().stream()
                        .mapToLong(entry -> entry.getKey().length() + String.valueOf(entry.getValue()).length())
                        .sum())
                .sum();
    }

    private void injectCounterexamples(String modelKey) {
        try (Session session = Neo4jDriverManager.getInstance().openSession()) {
            session.run("MATCH (o:Object {modelKey:$modelKey,use_id:'book_a'}) "
                            + "CREATE (:Object {modelKey:$modelKey,use_id:o.use_id,"
                            + "objectKey:o.objectKey+'::injected-duplicate-id'})",
                    Map.of("modelKey", modelKey)).consume();
            session.run("CREATE (:Object {modelKey:$modelKey,objectKey:$missingIdKey})",
                    Map.of("modelKey", modelKey,
                            "missingIdKey", modelKey + "::object::injected-missing-id")).consume();
            session.run("MATCH (:Object {modelKey:$modelKey,use_id:'book_a'})-[r:ObjectInstanceOf]->"
                            + "({classKey:$publicationKey}) DELETE r",
                    Map.of("modelKey", modelKey,
                            "publicationKey", CanonicalGraphEncoding.classKey(modelKey, "Publication"))).consume();
            session.run("MATCH (o:Object {modelKey:$modelKey,use_id:'book_b'}),"
                            + "(c:UmlClass {classKey:$libraryKey}) "
                            + "CREATE (o)-[:ObjectInstanceOf]->(c)",
                    Map.of("modelKey", modelKey,
                            "libraryKey", CanonicalGraphEncoding.classKey(modelKey, "Library"))).consume();
            session.run("MATCH (:Object {modelKey:$modelKey,use_id:'book_a'})"
                            + "-[:ObjectHasAttribute]->(v:AttributeValue) "
                            + "WHERE v.attributeKey=$attributeKey SET v.value=\"'MUTATED'\"",
                    Map.of("modelKey", modelKey,
                            "attributeKey", CanonicalGraphEncoding.attributeKey(modelKey, "Publication", "title"))).consume();
            session.run("MATCH (o:Object {modelKey:$modelKey,use_id:'book_a'}) "
                            + "CREATE (o)-[:ObjectHasAttribute]->(:AttributeValue {modelKey:$modelKey,"
                            + "attributeKey:$attributeKey,value:\"'DUPLICATE'\"})",
                    Map.of("modelKey", modelKey,
                            "attributeKey", CanonicalGraphEncoding.attributeKey(
                                    modelKey, "Publication", "title"))).consume();
            session.run("CREATE (:Object {modelKey:$modelKey,use_id:'ghost',objectKey:$ghostKey})",
                    Map.of("modelKey", modelKey,
                            "ghostKey", CanonicalGraphEncoding.objectKey(modelKey, "ghost"))).consume();
            session.run("CREATE ({modelKey:$modelKey,classKey:$libraryKey,name:'CollidingLibrary'})",
                    Map.of("modelKey", modelKey,
                            "libraryKey", CanonicalGraphEncoding.classKey(modelKey, "Library"))).consume();
            session.run("MATCH (a:Object {modelKey:$modelKey,use_id:'central'})-[r]->"
                            + "(b:Object {modelKey:$modelKey,use_id:'book_b'}) "
                            + "WHERE type(r) STARTS WITH 'Link' DELETE r",
                    Map.of("modelKey", modelKey)).consume();
            session.run("MATCH (a:Object {modelKey:$modelKey,use_id:'central'}),"
                            + "(b:Object {modelKey:$modelKey,use_id:'book_a'}) "
                            + "CREATE (a)-[:LinkAssociateWith {modelKey:$modelKey,linkKey:'injected-spurious-link',"
                            + "associationKey:$associationKey,"
                            + "name:'Catalog',sourceRole:'library',targetRole:'book',"
                            + "sourceQualifiers:[\"'SPURIOUS'\"],targetQualifiers:[\"'A1'\"]}]->(b)",
                    Map.of("modelKey", modelKey,
                            "associationKey", CanonicalGraphEncoding.associationKey(modelKey, "Catalog"))).consume();
            session.run("MATCH (a:Object {modelKey:$modelKey,use_id:'central'})-[r]->"
                            + "(b:Object {modelKey:$modelKey,use_id:'book_a'}) "
                            + "WHERE type(r) STARTS WITH 'Link' "
                            + "CREATE (a)-[copy:LinkAssociateWith]->(b) SET copy=properties(r)",
                    Map.of("modelKey", modelKey)).consume();
        }
    }

    private RepresentationAdequacyEvaluator.ObligationResult obligation(
            RepresentationAdequacyEvaluator.Report report, String code) {
        return report.obligations().stream().filter(result -> result.code().equals(code)).findFirst().orElseThrow();
    }

    private MModel compileModel(String modelName) {
        StringWriter diagnostics = new StringWriter();
        MModel model = USECompiler.compileSpecification(MODEL.formatted(modelName), modelName + ".use",
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

    private void cleanup(String modelName, String modelKey) {
        Neo4jDriverManager manager = Neo4jDriverManager.getInstance();
        if (manager == null || !manager.isConnected()) return;
        try (Session session = manager.openSession()) {
            session.run("MATCH (n {modelKey:$modelKey}) DETACH DELETE n", Map.of("modelKey", modelKey)).consume();
            session.run("MATCH (m:ManageModel {name:$modelName}) DETACH DELETE m",
                    Map.of("modelName", modelName)).consume();
        }
    }

    private static final String MODEL = """
            model %s
            class Publication
            attributes
                title : String
            end
            class Book < Publication
            end
            class Library
            attributes
                name : String
            end
            association Catalog between
                Library[1] role library qualifier (city : String)
                Book[*] role book qualifier (shelf : String)
            end
            """;
}
