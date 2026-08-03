package org.uet.dse.neo4jtgg.experiment;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepresentationAdequacyEvaluatorTest {
    @Test
    void acceptsR1ThroughR7AcrossIndependentFixtures() {
        List<RepresentationAdequacyEvaluator.Snapshot> fixtures = List.of(
                canonicalSnapshot(),
                attributeOnlySnapshot(),
                emptySnapshot());

        fixtures.forEach(snapshot -> assertTrue(
                RepresentationAdequacyEvaluator.evaluate(snapshot, snapshot).passed(),
                () -> RepresentationAdequacyEvaluator.evaluate(snapshot, snapshot).render()));
    }

    @Test
    void acceptsExtensionallyEqualSnapshotsWithInheritanceAndBidirectionalQualifiers() {
        var source = canonicalSnapshot();
        var graph = canonicalSnapshot();

        var report = RepresentationAdequacyEvaluator.evaluate(source, graph);

        assertTrue(report.passed(), report.render());
        assertEquals(16, report.obligations().size());
        assertTrue(RepresentationAdequacyEvaluator.navigationFacts(source.linkFacts()).stream()
                .anyMatch(fact -> fact.direction() == RepresentationAdequacyEvaluator.Direction.FORWARD
                        && fact.qualifiers().equals(List.of("'HCM'"))));
        assertTrue(RepresentationAdequacyEvaluator.navigationFacts(source.linkFacts()).stream()
                .anyMatch(fact -> fact.direction() == RepresentationAdequacyEvaluator.Direction.REVERSE
                        && fact.qualifiers().equals(List.of("'A1'"))));
    }

    @Test
    void reportsMissingAndSpuriousFactsSeparately() {
        var source = canonicalSnapshot();
        var graph = new RepresentationAdequacyEvaluator.Snapshot(
                Map.of("library", 1, "book", 1, "ghost", 1),
                Set.of(new RepresentationAdequacyEvaluator.TypeFact("library", "m::class::Library")),
                Set.of(new RepresentationAdequacyEvaluator.AttributeFact(
                        "library", "m::attribute::Library::name", "'WRONG'")),
                Set.of(), source.keyOwners(), 0);

        var report = RepresentationAdequacyEvaluator.evaluate(source, graph);

        assertFalse(report.passed());
        var objects = obligation(report, "R2");
        assertTrue(objects.spurious().contains("ghost"), report.render());
        var attributes = obligation(report, "R4");
        assertFalse(attributes.missing().isEmpty(), report.render());
        assertFalse(attributes.spurious().isEmpty(), report.render());
        assertFalse(obligation(report, "R5").missing().isEmpty(), report.render());
    }

    @Test
    void detectsDuplicateObjectNodesAndKeyCollisions() {
        var source = canonicalSnapshot();
        Map<String, Integer> counts = new LinkedHashMap<>(source.objectNodeCounts());
        counts.put("book", 2);
        Map<String, Set<String>> owners = new LinkedHashMap<>(source.keyOwners());
        owners.put("m::class::Library", Set.of("Library", "OtherLibrary"));
        var graph = new RepresentationAdequacyEvaluator.Snapshot(counts, source.typeFacts(),
                source.attributeFacts(), source.linkFacts(), owners, 1);

        var report = RepresentationAdequacyEvaluator.evaluate(source, graph);

        assertFalse(obligation(report, "R1").passed(), report.render());
        assertFalse(obligation(report, "KEY").passed(), report.render());
        assertEquals(1, report.duplicateSemanticLinks());
    }

    @Test
    void pa2DetectsMissingSpuriousAndWrongObjectIdentityMappings() {
        var source = objectIdentitySnapshot(List.of(
                observation("source:library", "library", "m::object::library"),
                observation("source:book", "book", "m::object::book")));
        var graph = objectIdentitySnapshot(List.of(
                observation("neo4j:library", "library", "m::object::WRONG"),
                observation("neo4j:ghost", "ghost", "m::object::ghost")));

        var result = obligation(RepresentationAdequacyEvaluator.evaluate(source, graph), "PA2");

        assertFalse(result.passed());
        assertTrue(result.missing().stream().anyMatch(value -> value.contains("book")));
        assertTrue(result.spurious().stream().anyMatch(value -> value.contains("ghost")));
        assertTrue(result.missing().stream().anyMatch(value -> value.contains("m::object::library")));
    }

    @Test
    void pa3RejectsMissingAndNonInjectiveUseIdsAndObjectKeys() {
        var source = objectIdentitySnapshot(List.of(
                observation("source:a", "a", "m::object::a"),
                observation("source:b", "b", "m::object::b")));
        var graph = objectIdentitySnapshot(List.of(
                observation("neo4j:1", "a", "m::object::a"),
                observation("neo4j:2", "a", "m::object::other"),
                observation("neo4j:3", "c", "m::object::a"),
                observation("neo4j:4", null, "m::object::missing-id"),
                observation("neo4j:5", "missing-key", null)));

        var result = obligation(RepresentationAdequacyEvaluator.evaluate(source, graph), "PA3");

        assertFalse(result.passed());
        assertTrue(result.details().contains("neo4j:4 missing use_id"), result.details().toString());
        assertTrue(result.details().contains("neo4j:5 missing objectKey"), result.details().toString());
        assertTrue(result.details().contains("use_id=a nodes=2"), result.details().toString());
        assertTrue(result.details().contains("objectKey=m::object::a nodes=2"), result.details().toString());
    }

    @Test
    void pa2AndPa3AcceptStableBijectionIndependentlyOfPhysicalNodeTokens() {
        var source = objectIdentitySnapshot(List.of(
                observation("source:a", "a", "m::object::a"),
                observation("source:b", "b", "m::object::b")));
        var graph = objectIdentitySnapshot(List.of(
                observation("4:abc", "a", "m::object::a"),
                observation("4:def", "b", "m::object::b")));

        var report = RepresentationAdequacyEvaluator.evaluate(source, graph);

        assertTrue(obligation(report, "PA2").passed(), report.render());
        assertTrue(obligation(report, "PA3").passed(), report.render());
    }

    @Test
    void pa4AcceptsCompleteRuntimeAndInheritedMemberships() {
        var report = RepresentationAdequacyEvaluator.evaluate(canonicalSnapshot(), canonicalSnapshot());

        var result = obligation(report, "PA4");
        assertTrue(result.passed(), report.render());
        assertTrue(canonicalSnapshot().typeFacts().contains(
                new RepresentationAdequacyEvaluator.TypeFact("book", "m::class::Publication")));
    }

    @Test
    void pa4RejectsMissingAndSpuriousTypeMembershipsIndependently() {
        var source = canonicalSnapshot();
        Set<RepresentationAdequacyEvaluator.TypeFact> mutated = new java.util.LinkedHashSet<>(source.typeFacts());
        mutated.remove(new RepresentationAdequacyEvaluator.TypeFact("book", "m::class::Publication"));
        mutated.add(new RepresentationAdequacyEvaluator.TypeFact("book", "m::class::Library"));
        var graph = snapshot(source, source.objectNodeCounts(), Set.copyOf(mutated),
                source.attributeFacts(), source.linkFacts());

        var result = obligation(RepresentationAdequacyEvaluator.evaluate(source, graph), "PA4");

        assertFalse(result.passed());
        assertTrue(result.missing().stream().anyMatch(value -> value.contains("Publication")));
        assertTrue(result.spurious().stream().anyMatch(value -> value.contains("Library")));
    }

    @Test
    void pa8UsesIndependentAllInstancesObservationsAndCatchesAccessorDrift() {
        var source = canonicalSnapshot();
        Set<RepresentationAdequacyEvaluator.AllInstancesFact> mutatedAllInstances =
                new java.util.LinkedHashSet<>(source.allInstancesObservations());
        mutatedAllInstances.remove(new RepresentationAdequacyEvaluator.AllInstancesFact(
                "m::class::Publication", "book"));
        mutatedAllInstances.add(new RepresentationAdequacyEvaluator.AllInstancesFact(
                "m::class::Publication", "library"));
        var graph = new RepresentationAdequacyEvaluator.Snapshot(
                source.objectNodeCounts(), source.typeFacts(), source.attributeFacts(), source.linkFacts(),
                source.keyOwners(), source.duplicateSemanticLinks(), source.objectObservations(),
                source.attributeObservations(), source.linkObservations(), Set.copyOf(mutatedAllInstances));

        var report = RepresentationAdequacyEvaluator.evaluate(source, graph);
        var result = obligation(report, "PA8");

        assertTrue(obligation(report, "PA4").passed(), report.render());
        assertFalse(result.passed());
        assertTrue(result.missing().stream().anyMatch(value -> value.contains("book")));
        assertTrue(result.spurious().stream().anyMatch(value -> value.contains("library")));
    }

    @Test
    void pa5AcceptsOneCanonicalSlotWithTheExpectedValuePerObjectAttributePair() {
        var report = RepresentationAdequacyEvaluator.evaluate(canonicalSnapshot(), canonicalSnapshot());
        assertTrue(obligation(report, "PA5").passed(), report.render());
    }

    @Test
    void pa5RejectsWrongValueAndDuplicateSemanticSlot() {
        var source = canonicalSnapshot();
        var expected = source.attributeObservations().get(0);
        List<RepresentationAdequacyEvaluator.AttributeObservation> observations = List.of(
                new RepresentationAdequacyEvaluator.AttributeObservation(
                        "neo4j:primary", expected.objectId(), expected.attributeKey(), "'WRONG'", expected.slotKey()),
                new RepresentationAdequacyEvaluator.AttributeObservation(
                        "neo4j:duplicate", expected.objectId(), expected.attributeKey(), "'OTHER'", null));
        var graph = new RepresentationAdequacyEvaluator.Snapshot(
                source.objectNodeCounts(), source.typeFacts(),
                Set.of(new RepresentationAdequacyEvaluator.AttributeFact(
                        expected.objectId(), expected.attributeKey(), "'WRONG'")),
                source.linkFacts(), source.keyOwners(), source.duplicateSemanticLinks(),
                source.objectObservations(), observations);

        var result = obligation(RepresentationAdequacyEvaluator.evaluate(source, graph), "PA5");
        assertFalse(result.passed());
        assertFalse(result.missing().isEmpty());
        assertFalse(result.spurious().isEmpty());
        assertTrue(result.details().contains("neo4j:duplicate missing slotKey"), result.details().toString());
        assertTrue(result.details().stream().anyMatch(value -> value.startsWith("semanticSlot=")
                && value.endsWith("nodes=2")), result.details().toString());
    }

    @Test
    void pa5RejectsMissingAndSpuriousAttributeSlots() {
        var source = canonicalSnapshot();
        var graph = new RepresentationAdequacyEvaluator.Snapshot(
                source.objectNodeCounts(), source.typeFacts(), Set.of(
                new RepresentationAdequacyEvaluator.AttributeFact(
                        "book", "m::attribute::Book::ghost", "'x'")),
                source.linkFacts(), source.keyOwners(), source.duplicateSemanticLinks());

        var result = obligation(RepresentationAdequacyEvaluator.evaluate(source, graph), "PA5");
        assertFalse(result.passed());
        assertFalse(result.missing().isEmpty());
        assertFalse(result.spurious().isEmpty());
    }

    @Test
    void pa6RejectsMissingSpuriousAndDuplicatePhysicalLinkWitnesses() {
        var source = canonicalSnapshot();
        var expected = source.linkObservations().get(0);
        List<RepresentationAdequacyEvaluator.LinkObservation> observations = List.of(
                expected,
                new RepresentationAdequacyEvaluator.LinkObservation(
                        "neo4j:duplicate", expected.linkKey(), expected.associationKey(),
                        expected.sourceId(), expected.targetId(), expected.sourceRole(), expected.targetRole(),
                        expected.sourceQualifiers(), expected.targetQualifiers()),
                new RepresentationAdequacyEvaluator.LinkObservation(
                        "neo4j:spurious", "spurious-link-key", expected.associationKey(),
                        expected.sourceId(), "ghost", expected.sourceRole(), expected.targetRole(),
                        expected.sourceQualifiers(), expected.targetQualifiers()));
        var graph = snapshotWithLinks(source, observations);

        var result = obligation(RepresentationAdequacyEvaluator.evaluate(source, graph), "PA6");
        assertFalse(result.passed());
        assertFalse(result.spurious().isEmpty(), result.toString());
        assertTrue(result.details().stream().anyMatch(value -> value.contains("relationships=2")),
                result.details().toString());
    }

    @Test
    void pa7RejectsWrongDirectionOrderArityAndMissingQualifierProperty() {
        var source = canonicalSnapshot();
        var expected = source.linkObservations().get(0);
        var graph = snapshotWithLinks(source, List.of(
                new RepresentationAdequacyEvaluator.LinkObservation(
                        "neo4j:wrong-qualifiers", expected.linkKey(), expected.associationKey(),
                        expected.sourceId(), expected.targetId(), expected.sourceRole(), expected.targetRole(),
                        List.of("'extra'", "'HCM'"), null)));

        var result = obligation(RepresentationAdequacyEvaluator.evaluate(source, graph), "PA7");
        assertFalse(result.passed());
        assertFalse(result.missing().isEmpty(), result.toString());
        assertFalse(result.spurious().isEmpty(), result.toString());
        assertTrue(result.details().contains("neo4j:wrong-qualifiers missing targetQualifiers"),
                result.details().toString());
    }

    @Test
    void detectsDedicatedMutationsForEveryR1ThroughR7Obligation() {
        var source = canonicalSnapshot();
        List<TargetedMutation> mutations = List.of(
                new TargetedMutation("R1", snapshot(source,
                        Map.of("library", 1, "book", 2), source.typeFacts(), source.attributeFacts(), source.linkFacts())),
                new TargetedMutation("R2", snapshot(source,
                        Map.of("library", 1, "book", 1, "ghost", 1), source.typeFacts(), source.attributeFacts(), source.linkFacts())),
                new TargetedMutation("R3", snapshot(source, source.objectNodeCounts(),
                        withoutFirst(source.typeFacts()), source.attributeFacts(), source.linkFacts())),
                new TargetedMutation("PA4", snapshot(source, source.objectNodeCounts(),
                        withoutFirst(source.typeFacts()), source.attributeFacts(), source.linkFacts())),
                new TargetedMutation("R4", snapshot(source, source.objectNodeCounts(), source.typeFacts(),
                        Set.of(new RepresentationAdequacyEvaluator.AttributeFact(
                                "library", "m::attribute::Library::name", "'Changed'")), source.linkFacts())),
                new TargetedMutation("PA5", snapshot(source, source.objectNodeCounts(), source.typeFacts(),
                        Set.of(new RepresentationAdequacyEvaluator.AttributeFact(
                                "library", "m::attribute::Library::name", "'Changed'")), source.linkFacts())),
                new TargetedMutation("R5", snapshot(source, source.objectNodeCounts(), source.typeFacts(),
                        source.attributeFacts(), Set.of())),
                new TargetedMutation("PA6", snapshot(source, source.objectNodeCounts(), source.typeFacts(),
                        source.attributeFacts(), Set.of())),
                new TargetedMutation("R6", snapshot(source, source.objectNodeCounts(), source.typeFacts(),
                        source.attributeFacts(), Set.of())),
                new TargetedMutation("PA7", snapshot(source, source.objectNodeCounts(), source.typeFacts(),
                        source.attributeFacts(), Set.of())),
                new TargetedMutation("R7", snapshot(source, source.objectNodeCounts(),
                        withoutFirst(source.typeFacts()), source.attributeFacts(), source.linkFacts())),
                new TargetedMutation("PA8", new RepresentationAdequacyEvaluator.Snapshot(
                        source.objectNodeCounts(), source.typeFacts(), source.attributeFacts(), source.linkFacts(),
                        source.keyOwners(), source.duplicateSemanticLinks(), source.objectObservations(),
                        source.attributeObservations(), source.linkObservations(), Set.of())));

        for (TargetedMutation mutation : mutations) {
            var report = RepresentationAdequacyEvaluator.evaluate(source, mutation.graph());
            assertFalse(obligation(report, mutation.obligation()).passed(),
                    mutation.obligation() + " mutation survived:\n" + report.render());
        }
    }

    @Test
    void summarizesStructuralAndRuntimeMetrics() {
        var latencies = new GraphEvaluationMetrics.LatencyCollector();
        latencies.add("attribute", 10);
        latencies.add("attribute", 30);
        latencies.add("attribute", 20);
        var metrics = new GraphEvaluationMetrics(10, 2, 1, 1,
                18, 8, 40, 200, 2, 1_000_000_000L, latencies.summarize());

        assertEquals(1.5, metrics.nodeExpansion());
        assertEquals(2.0, metrics.relationshipExpansion());
        assertEquals(2.0, metrics.encodingObjectsPerSecond());
        assertEquals(20, metrics.primitiveLatencies().get("attribute").medianNs());
        assertTrue(metrics.toCsvRow("small", "instance-of").contains("\"small\""));
    }

    @Test
    void exportsPerObligationCsvWithBothDifferenceDirections() {
        var report = RepresentationAdequacyEvaluator.evaluate(
                canonicalSnapshot(),
                new RepresentationAdequacyEvaluator.Snapshot(
                        Map.of("library", 1, "book", 1, "ghost", 1),
                        Set.of(), Set.of(), Set.of(), Map.of(), 0));

        String csv = report.toCsv("mutation", "canonical-v1");

        assertTrue(csv.startsWith("dataset,profile,obligation,passed,missingFacts,spuriousFacts,details"));
        assertTrue(csv.contains("\"R3\",false,"));
        assertTrue(report.missingFacts() > 0);
        assertTrue(report.spuriousFacts() > 0);
    }

    private RepresentationAdequacyEvaluator.ObligationResult obligation(
            RepresentationAdequacyEvaluator.Report report, String code) {
        return report.obligations().stream().filter(result -> result.code().equals(code)).findFirst().orElseThrow();
    }

    private RepresentationAdequacyEvaluator.Snapshot canonicalSnapshot() {
        String libraryClass = "m::class::Library";
        String bookClass = "m::class::Book";
        String publicationClass = "m::class::Publication";
        String attribute = "m::attribute::Library::name";
        String association = "m::association::Catalog";
        return new RepresentationAdequacyEvaluator.Snapshot(
                Map.of("library", 1, "book", 1),
                Set.of(
                        new RepresentationAdequacyEvaluator.TypeFact("library", libraryClass),
                        new RepresentationAdequacyEvaluator.TypeFact("book", bookClass),
                        new RepresentationAdequacyEvaluator.TypeFact("book", publicationClass)),
                Set.of(new RepresentationAdequacyEvaluator.AttributeFact("library", attribute, "'Central'")),
                Set.of(new RepresentationAdequacyEvaluator.LinkFact(association, "library", "book",
                        "library", "book", List.of("'HCM'"), List.of("'A1'"))),
                Map.of(
                        libraryClass, Set.of("Library"),
                        bookClass, Set.of("Book"),
                        publicationClass, Set.of("Publication"),
                        attribute, Set.of("Library.name"),
                        association, Set.of("Catalog")),
                0);
    }

    private RepresentationAdequacyEvaluator.Snapshot attributeOnlySnapshot() {
        String classKey = "a::class::Account";
        String attributeKey = "a::attribute::Account::balance";
        return new RepresentationAdequacyEvaluator.Snapshot(
                Map.of("account-1", 1, "account-2", 1),
                Set.of(
                        new RepresentationAdequacyEvaluator.TypeFact("account-1", classKey),
                        new RepresentationAdequacyEvaluator.TypeFact("account-2", classKey)),
                Set.of(
                        new RepresentationAdequacyEvaluator.AttributeFact("account-1", attributeKey, "0"),
                        new RepresentationAdequacyEvaluator.AttributeFact("account-2", attributeKey, "10")),
                Set.of(),
                Map.of(classKey, Set.of("Account"), attributeKey, Set.of("Account.balance")),
                0);
    }

    private RepresentationAdequacyEvaluator.Snapshot emptySnapshot() {
        return new RepresentationAdequacyEvaluator.Snapshot(
                Map.of(), Set.of(), Set.of(), Set.of(), Map.of(), 0);
    }

    private RepresentationAdequacyEvaluator.Snapshot objectIdentitySnapshot(
            List<RepresentationAdequacyEvaluator.ObjectObservation> observations) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        observations.stream().map(RepresentationAdequacyEvaluator.ObjectObservation::useId)
                .filter(java.util.Objects::nonNull).filter(id -> !id.isBlank())
                .forEach(id -> counts.merge(id, 1, Integer::sum));
        return new RepresentationAdequacyEvaluator.Snapshot(
                counts, Set.of(), Set.of(), Set.of(), Map.of(), 0, observations);
    }

    private RepresentationAdequacyEvaluator.ObjectObservation observation(
            String token, String useId, String objectKey) {
        return new RepresentationAdequacyEvaluator.ObjectObservation(token, useId, objectKey);
    }

    private RepresentationAdequacyEvaluator.Snapshot snapshot(
            RepresentationAdequacyEvaluator.Snapshot base,
            Map<String, Integer> objects,
            Set<RepresentationAdequacyEvaluator.TypeFact> types,
            Set<RepresentationAdequacyEvaluator.AttributeFact> attributes,
            Set<RepresentationAdequacyEvaluator.LinkFact> links) {
        return new RepresentationAdequacyEvaluator.Snapshot(
                objects, types, attributes, links, base.keyOwners(), base.duplicateSemanticLinks());
    }

    private <T> Set<T> withoutFirst(Set<T> values) {
        var result = new java.util.LinkedHashSet<>(values);
        result.remove(result.iterator().next());
        return Set.copyOf(result);
    }

    private RepresentationAdequacyEvaluator.Snapshot snapshotWithLinks(
            RepresentationAdequacyEvaluator.Snapshot base,
            List<RepresentationAdequacyEvaluator.LinkObservation> links) {
        return new RepresentationAdequacyEvaluator.Snapshot(
                base.objectNodeCounts(), base.typeFacts(), base.attributeFacts(), base.linkFacts(),
                base.keyOwners(), base.duplicateSemanticLinks(), base.objectObservations(),
                base.attributeObservations(), links);
    }

    private record TargetedMutation(String obligation, RepresentationAdequacyEvaluator.Snapshot graph) {
    }
}
