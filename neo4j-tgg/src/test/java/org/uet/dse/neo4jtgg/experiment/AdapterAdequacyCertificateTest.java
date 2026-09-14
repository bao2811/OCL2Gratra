package org.uet.dse.neo4jtgg.experiment;

import org.junit.jupiter.api.Test;
import org.tzi.use.api.UseSystemApi;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.ocl.value.StringValue;
import org.tzi.use.uml.ocl.value.Value;
import org.tzi.use.uml.sys.MObject;
import org.uet.dse.neo4j.encoding.CanonicalGraphEncoding;
import org.uet.dse.neo4jtgg.service.impl.DefaultOclToCypherCompiler;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdapterAdequacyCertificateTest {
    @Test
    void issuesOneCertificateForAllSharedSnapshotPremises() throws Exception {
        Fixture fixture = fixture();
        AdapterAdequacyCertificate certificate = AdapterAdequacyCertificate.issue(
                snapshot(fixture, fixture.source(), fixture.source(), fixture.plan()));

        assertEquals(6, certificate.observationDiffs().size());
        assertTrue(certificate.observationDiffs().values().stream()
                .allMatch(AdapterAdequacyCertificate.ObservationDiff::equal));
        assertTrue(certificate.representationReport().passed());
        assertTrue(certificate.rendererReport().passed());
        assertEquals(1, certificate.rendererReport().planFingerprints().size());
    }

    @Test
    void rejectsEveryObservationMutationAndSnapshotMixing() throws Exception {
        Fixture fixture = fixture();
        var source = fixture.source();
        for (AdapterAdequacyCertificate.Observation observation
                : AdapterAdequacyCertificate.Observation.values()) {
            var mutated = mutate(source, observation);
            assertThrows(IllegalStateException.class,
                    () -> AdapterAdequacyCertificate.issue(
                            snapshot(fixture, mutated, mutated, fixture.plan())), observation.name());
        }

        var changed = mutate(source, AdapterAdequacyCertificate.Observation.TYPE);
        assertThrows(IllegalStateException.class, () -> AdapterAdequacyCertificate.issue(
                new AdapterAdequacyCertificate.AdapterAdequacySnapshot(
                        "mixed-source", fixture.model().name(),
                        CanonicalGraphEncoding.modelKey(fixture.model().name()), "direct-renderer-v1",
                        source, changed, source, source, List.of(fixture.plan()))));
        assertThrows(IllegalStateException.class, () -> AdapterAdequacyCertificate.issue(
                new AdapterAdequacyCertificate.AdapterAdequacySnapshot(
                        "mixed-graph", fixture.model().name(),
                        CanonicalGraphEncoding.modelKey(fixture.model().name()), "direct-renderer-v1",
                        source, source, source, changed, List.of(fixture.plan()))));
    }

    @Test
    void rejectsRendererAccessorDrift() throws Exception {
        Fixture fixture = fixture();
        InstrumentedCompilationResult plan = fixture.plan();
        InstrumentedCompilationResult legacy = new InstrumentedCompilationResult(
                plan.ast(), plan.bound(), plan.validationAlgebra(), plan.normalizedValidationAlgebra(),
                plan.queryPlan(), plan.cypher().replace(".attributeKey = $", ".name = $"),
                plan.parameters(), plan.timings());
        assertThrows(IllegalStateException.class, () -> AdapterAdequacyCertificate.issue(
                snapshot(fixture, fixture.source(), fixture.source(), legacy)));
    }

    @Test
    void graphSnapshotUsesRendererLabelsAndScansCanonicalKeysAcrossWrongModelTags() throws Exception {
        Path workspace = workspace();
        String reader = Files.readString(workspace.resolve(
                "neo4j-tgg/src/main/java/org/uet/dse/neo4jtgg/experiment/AdapterAdequacySnapshotReader.java"));
        assertTrue(reader.contains("(c:UmlClass)"), "type observation must require :UmlClass");
        assertTrue(reader.contains("c.classKey STARTS WITH $classKeyPrefix"),
                "allInstances observation must see every current canonical class key");
        assertTrue(reader.contains("MATCH (n:UmlClass) WHERE n.classKey STARTS WITH $classKeyPrefix"),
                "class-key ownership must not hide a cloned key behind a foreign modelKey");
        assertTrue(reader.contains("r.associationKey STARTS WITH $associationKeyPrefix"),
                "association observation must not hide a cross-model relationship");
    }

    @Test
    void independentSourceOracleCanonicalizesRealSignedZero() throws Exception {
        String modelName = "SignedZeroOracle";
        String specification = """
                model SignedZeroOracle
                class Sample
                attributes
                    reading : Real
                end
                """;
        StringWriter diagnostics = new StringWriter();
        MModel model = USECompiler.compileSpecification(specification, modelName + ".use",
                new PrintWriter(diagnostics, true), new ModelFactory());
        assertTrue(model != null, diagnostics.toString());
        UseSystemApi api = UseSystemApi.create(model, false);
        api.createObject("Sample", "sample");
        api.setAttributeValue("sample", "reading", "-0.0");

        RepresentationAdequacyEvaluator.Snapshot snapshot =
                AdapterAdequacySnapshotReader.source(api.getSystem(), modelName);

        assertTrue(snapshot.attributeFacts().stream()
                .anyMatch(fact -> "v1|R|0.0".equals(fact.encodedValue())),
                snapshot.attributeFacts().toString());
        assertTrue(snapshot.attributeFacts().stream()
                .noneMatch(fact -> fact.encodedValue().contains("-0.0")),
                snapshot.attributeFacts().toString());
    }

    private static Path workspace() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve(
                    "verification/contract/proof-contract-registry.json"))) return current;
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate workspace root");
    }

    private AdapterAdequacyCertificate.AdapterAdequacySnapshot snapshot(
            Fixture fixture, RepresentationAdequacyEvaluator.Snapshot graphStart,
            RepresentationAdequacyEvaluator.Snapshot graphEnd, InstrumentedCompilationResult plan) {
        return new AdapterAdequacyCertificate.AdapterAdequacySnapshot(
                "unit-shared-snapshot", fixture.model().name(),
                CanonicalGraphEncoding.modelKey(fixture.model().name()), "direct-renderer-v1",
                fixture.source(), fixture.source(), graphStart, graphEnd, List.of(plan));
    }

    private Fixture fixture() throws Exception {
        String modelName = "AdapterCertificateFixture";
        String specification = """
                model %s
                class Library
                attributes
                    name : String
                end
                class Book
                attributes
                    title : String
                end
                association Catalog between
                    Library[1] role library qualifier (shelf : String)
                    Book[*] role book
                end
                """.formatted(modelName);
        StringWriter diagnostics = new StringWriter();
        MModel model = USECompiler.compileSpecification(specification, modelName + ".use",
                new PrintWriter(diagnostics, true), new ModelFactory());
        assertTrue(model != null, diagnostics.toString());
        UseSystemApi api = UseSystemApi.create(model, false);
        api.createObject("Library", "library");
        api.createObject("Book", "book");
        api.setAttributeValue("library", "name", "'Central'");
        api.setAttributeValue("book", "title", "'A'");
        api.createLinkEx(model.getAssociation("Catalog"),
                new MObject[]{api.getObject("library"), api.getObject("book")},
                new Value[][]{{new StringValue("A1")}, new Value[0]});
        RepresentationAdequacyEvaluator.Snapshot source =
                RepresentationEvaluationRealNeo4jTest.sourceSnapshot(api.getSystem(), modelName);
        InstrumentedCompilationResult plan = new DefaultOclToCypherCompiler(model)
                .compileInvariantInstrumented("context Library inv AdapterProbe: "
                        + "self.name <> '' and self.book['A1']->notEmpty() "
                        + "and Book.allInstances()->notEmpty()");
        return new Fixture(model, source, plan);
    }

    private RepresentationAdequacyEvaluator.Snapshot mutate(
            RepresentationAdequacyEvaluator.Snapshot source,
            AdapterAdequacyCertificate.Observation observation) {
        Map<String, Integer> objects = new LinkedHashMap<>(source.objectNodeCounts());
        Set<RepresentationAdequacyEvaluator.TypeFact> types = new LinkedHashSet<>(source.typeFacts());
        Set<RepresentationAdequacyEvaluator.AttributeFact> attributes =
                new LinkedHashSet<>(source.attributeFacts());
        Set<RepresentationAdequacyEvaluator.LinkFact> links = new LinkedHashSet<>(source.linkFacts());
        Map<String, Set<String>> keys = new LinkedHashMap<>(source.keyOwners());
        var objectObservations = new java.util.ArrayList<>(source.objectObservations());
        var attributeObservations = new java.util.ArrayList<>(source.attributeObservations());
        var linkObservations = new java.util.ArrayList<>(source.linkObservations());
        Set<RepresentationAdequacyEvaluator.AllInstancesFact> allInstances =
                new LinkedHashSet<>(source.allInstancesObservations());
        switch (observation) {
            case OBJECTS -> {
                objects.remove("book");
                objectObservations.removeIf(value -> "book".equals(value.useId()));
            }
            case METAMODEL -> keys.remove(keys.keySet().iterator().next());
            case TYPE -> types.remove(types.iterator().next());
            case VALUE -> {
                var removed = attributes.iterator().next();
                attributes.remove(removed);
                attributeObservations.removeIf(value -> value.objectId().equals(removed.objectId())
                        && value.attributeKey().equals(removed.attributeKey()));
            }
            case NAV -> {
                links.clear();
                linkObservations.clear();
            }
            case ALL_INSTANCES -> allInstances.remove(allInstances.iterator().next());
        }
        return new RepresentationAdequacyEvaluator.Snapshot(objects, types, attributes, links, keys,
                source.duplicateSemanticLinks(), objectObservations, attributeObservations,
                linkObservations, allInstances);
    }

    private record Fixture(MModel model, RepresentationAdequacyEvaluator.Snapshot source,
                           InstrumentedCompilationResult plan) {
    }
}
