package org.uet.dse.neo4jtgg.experiment;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Extensional R1--R7 comparison used to connect a concrete graph adapter to
 * the canonical object--graph representation contract.
 *
 * <p>The evaluator deliberately knows nothing about Cypher rendering. Source
 * and graph adapters must independently construct snapshots of the observations
 * visible to validation. This prevents query-shape tests from being mistaken
 * for representation evidence.</p>
 */
public final class RepresentationAdequacyEvaluator {
    private RepresentationAdequacyEvaluator() {
    }

    public static Report evaluate(Snapshot source, Snapshot graph) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(graph, "graph");

        List<ObligationResult> results = new ArrayList<>();
        results.add(r1(graph));
        results.add(compare("R2", "object-node exactness", source.objectIds(), graph.objectIds()));
        results.add(pa2(source, graph));
        results.add(pa3(source, graph));
        results.add(compare("R3", "type preservation", source.typeFacts(), graph.typeFacts()));
        results.add(compare("PA4", "prototype type exactness", source.typeFacts(), graph.typeFacts()));
        results.add(compare("R4", "attribute preservation", source.attributeFacts(), graph.attributeFacts()));
        results.add(pa5(source, graph));
        results.add(compare("R5", "association preservation", source.linkFacts(), graph.linkFacts()));
        results.add(pa6(source, graph));
        results.add(compare("R6", "navigation preservation",
                navigationFacts(source.linkFacts()), navigationFacts(graph.linkFacts())));
        results.add(pa7(source, graph));
        results.add(compare("R7", "allInstances preservation",
                allInstancesFacts(source.typeFacts()), allInstancesFacts(graph.typeFacts())));
        results.add(compare("PA8", "prototype allInstances accessor agreement",
                source.allInstancesObservations(), graph.allInstancesObservations()));
        results.add(compare("M2", "metamodel key declarations",
                keyFacts(source.keyOwners()), keyFacts(graph.keyOwners())));
        results.add(keyUniqueness(graph));
        return new Report(List.copyOf(results), graph.duplicateSemanticLinks());
    }

    private static ObligationResult r1(Snapshot graph) {
        Set<String> duplicates = new TreeSet<>();
        graph.objectNodeCounts().forEach((id, count) -> {
            if (count == null || count != 1) duplicates.add(id + " count=" + count);
        });
        return new ObligationResult("R1", "object injectivity", duplicates.isEmpty(),
                Set.of(), Set.of(), duplicates);
    }

    private static ObligationResult pa2(Snapshot source, Snapshot graph) {
        Set<String> missing = stringifyDifference(identityFacts(source), identityFacts(graph));
        Set<String> spurious = stringifyDifference(identityFacts(graph), identityFacts(source));
        Set<String> details = new TreeSet<>(malformedObjectObservations(graph));
        duplicateFacts(graph.objectObservations(), details, true);
        return new ObligationResult("PA2", "prototype object exactness",
                missing.isEmpty() && spurious.isEmpty() && details.isEmpty(), missing, spurious, details);
    }

    private static ObligationResult pa3(Snapshot source, Snapshot graph) {
        Set<String> missing = stringifyDifference(identityFacts(source), identityFacts(graph));
        Set<String> spurious = stringifyDifference(identityFacts(graph), identityFacts(source));
        Set<String> details = new TreeSet<>(malformedObjectObservations(graph));
        duplicateFacts(graph.objectObservations(), details, false);
        return new ObligationResult("PA3", "use_id existence, stability, and injectivity",
                missing.isEmpty() && spurious.isEmpty() && details.isEmpty(), missing, spurious, details);
    }

    private static Set<ObjectIdentityFact> identityFacts(Snapshot snapshot) {
        Set<ObjectIdentityFact> identities = new LinkedHashSet<>();
        for (ObjectObservation observation : snapshot.objectObservations()) {
            if (admitted(observation.useId()) && admitted(observation.objectKey())) {
                identities.add(new ObjectIdentityFact(observation.useId(), observation.objectKey()));
            }
        }
        return Set.copyOf(identities);
    }

    private static Set<String> malformedObjectObservations(Snapshot snapshot) {
        Set<String> violations = new TreeSet<>();
        for (ObjectObservation observation : snapshot.objectObservations()) {
            if (!admitted(observation.useId())) {
                violations.add(observation.nodeToken() + " missing use_id");
            }
            if (!admitted(observation.objectKey())) {
                violations.add(observation.nodeToken() + " missing objectKey");
            }
        }
        return violations;
    }

    private static void duplicateFacts(List<ObjectObservation> observations, Set<String> violations,
                                       boolean completeIdentity) {
        Map<String, Integer> useIds = new LinkedHashMap<>();
        Map<String, Integer> objectKeys = new LinkedHashMap<>();
        Map<ObjectIdentityFact, Integer> identities = new LinkedHashMap<>();
        for (ObjectObservation observation : observations) {
            if (admitted(observation.useId())) useIds.merge(observation.useId(), 1, Integer::sum);
            if (admitted(observation.objectKey())) objectKeys.merge(observation.objectKey(), 1, Integer::sum);
            if (admitted(observation.useId()) && admitted(observation.objectKey())) {
                identities.merge(new ObjectIdentityFact(observation.useId(), observation.objectKey()), 1, Integer::sum);
            }
        }
        useIds.forEach((id, count) -> {
            if (count != 1) violations.add("use_id=" + id + " nodes=" + count);
        });
        objectKeys.forEach((key, count) -> {
            if (count != 1) violations.add("objectKey=" + key + " nodes=" + count);
        });
        if (completeIdentity) identities.forEach((identity, count) -> {
            if (count != 1) violations.add(identity + " nodes=" + count);
        });
    }

    private static boolean admitted(String value) {
        return value != null && !value.isBlank();
    }

    private static ObligationResult pa5(Snapshot source, Snapshot graph) {
        Set<String> missing = stringifyDifference(attributeSlotFacts(source), attributeSlotFacts(graph));
        Set<String> spurious = stringifyDifference(attributeSlotFacts(graph), attributeSlotFacts(source));
        Set<String> details = new TreeSet<>();
        Map<String, Integer> semanticSlots = new LinkedHashMap<>();
        Map<String, Integer> physicalSlots = new LinkedHashMap<>();
        for (AttributeObservation observation : graph.attributeObservations()) {
            if (!admitted(observation.objectId())) details.add(observation.nodeToken() + " missing owner use_id");
            if (!admitted(observation.attributeKey())) details.add(observation.nodeToken() + " missing attributeKey");
            if (!admitted(observation.slotKey())) details.add(observation.nodeToken() + " missing slotKey");
            if (observation.encodedValue() == null) details.add(observation.nodeToken() + " missing encoded value");
            if (admitted(observation.objectId()) && admitted(observation.attributeKey())) {
                semanticSlots.merge(observation.objectId() + "|" + observation.attributeKey(), 1, Integer::sum);
            }
            if (admitted(observation.slotKey())) physicalSlots.merge(observation.slotKey(), 1, Integer::sum);
        }
        semanticSlots.forEach((slot, count) -> {
            if (count != 1) details.add("semanticSlot=" + slot + " nodes=" + count);
        });
        physicalSlots.forEach((slot, count) -> {
            if (count != 1) details.add("slotKey=" + slot + " nodes=" + count);
        });
        return new ObligationResult("PA5", "prototype attribute slot/value exactness",
                missing.isEmpty() && spurious.isEmpty() && details.isEmpty(), missing, spurious, details);
    }

    private static Set<AttributeSlotFact> attributeSlotFacts(Snapshot snapshot) {
        Set<AttributeSlotFact> facts = new LinkedHashSet<>();
        for (AttributeObservation observation : snapshot.attributeObservations()) {
            if (admitted(observation.objectId()) && admitted(observation.attributeKey())
                    && admitted(observation.slotKey()) && observation.encodedValue() != null) {
                facts.add(new AttributeSlotFact(observation.objectId(), observation.attributeKey(),
                        observation.encodedValue(), observation.slotKey()));
            }
        }
        return Set.copyOf(facts);
    }

    private static ObligationResult pa6(Snapshot source, Snapshot graph) {
        Set<String> missing = stringifyDifference(linkSlotFacts(source), linkSlotFacts(graph));
        Set<String> spurious = stringifyDifference(linkSlotFacts(graph), linkSlotFacts(source));
        Set<String> details = new TreeSet<>();
        Map<String, Integer> semanticKeys = new LinkedHashMap<>();
        for (LinkObservation observation : graph.linkObservations()) {
            if (!admitted(observation.linkKey())) details.add(observation.relationshipToken() + " missing linkKey");
            if (!admitted(observation.associationKey())) details.add(observation.relationshipToken() + " missing associationKey");
            if (!admitted(observation.sourceId())) details.add(observation.relationshipToken() + " missing source use_id");
            if (!admitted(observation.targetId())) details.add(observation.relationshipToken() + " missing target use_id");
            if (!admitted(observation.sourceRole())) details.add(observation.relationshipToken() + " missing sourceRole");
            if (!admitted(observation.targetRole())) details.add(observation.relationshipToken() + " missing targetRole");
            if (admitted(observation.linkKey())) semanticKeys.merge(observation.linkKey(), 1, Integer::sum);
        }
        semanticKeys.forEach((key, count) -> {
            if (count != 1) details.add("linkKey=" + key + " relationships=" + count);
        });
        return new ObligationResult("PA6", "prototype binary association-link exactness",
                missing.isEmpty() && spurious.isEmpty() && details.isEmpty(), missing, spurious, details);
    }

    private static Set<LinkSlotFact> linkSlotFacts(Snapshot snapshot) {
        Set<LinkSlotFact> facts = new LinkedHashSet<>();
        for (LinkObservation observation : snapshot.linkObservations()) {
            if (admitted(observation.linkKey()) && admitted(observation.associationKey())
                    && admitted(observation.sourceId()) && admitted(observation.targetId())
                    && admitted(observation.sourceRole()) && admitted(observation.targetRole())) {
                facts.add(new LinkSlotFact(observation.linkKey(), observation.associationKey(),
                        observation.sourceId(), observation.targetId(),
                        observation.sourceRole(), observation.targetRole()));
            }
        }
        return Set.copyOf(facts);
    }

    private static ObligationResult pa7(Snapshot source, Snapshot graph) {
        Set<String> missing = stringifyDifference(qualifierFacts(source), qualifierFacts(graph));
        Set<String> spurious = stringifyDifference(qualifierFacts(graph), qualifierFacts(source));
        Set<String> details = new TreeSet<>();
        for (LinkObservation observation : graph.linkObservations()) {
            qualifierViolations(observation.relationshipToken(), "sourceQualifiers",
                    observation.sourceQualifiers(), details);
            qualifierViolations(observation.relationshipToken(), "targetQualifiers",
                    observation.targetQualifiers(), details);
        }
        return new ObligationResult("PA7", "prototype directional qualifier agreement",
                missing.isEmpty() && spurious.isEmpty() && details.isEmpty(), missing, spurious, details);
    }

    private static Set<QualifierFact> qualifierFacts(Snapshot snapshot) {
        Set<QualifierFact> facts = new LinkedHashSet<>();
        for (LinkObservation observation : snapshot.linkObservations()) {
            if (!admitted(observation.linkKey())) continue;
            if (validQualifierList(observation.sourceQualifiers())) {
                facts.add(new QualifierFact(observation.linkKey(), Direction.FORWARD,
                        observation.sourceQualifiers()));
            }
            if (validQualifierList(observation.targetQualifiers())) {
                facts.add(new QualifierFact(observation.linkKey(), Direction.REVERSE,
                        observation.targetQualifiers()));
            }
        }
        return Set.copyOf(facts);
    }

    private static boolean validQualifierList(List<String> qualifiers) {
        return qualifiers != null && qualifiers.stream().allMatch(Objects::nonNull);
    }

    private static void qualifierViolations(String token, String property, List<String> qualifiers,
                                            Set<String> details) {
        if (qualifiers == null) {
            details.add(token + " missing " + property);
            return;
        }
        for (int index = 0; index < qualifiers.size(); index++) {
            if (qualifiers.get(index) == null) details.add(token + " null " + property + "[" + index + "]");
        }
    }

    private static ObligationResult keyUniqueness(Snapshot graph) {
        Set<String> violations = new TreeSet<>();
        graph.keyOwners().forEach((key, owners) -> {
            if (key == null || key.isBlank()) {
                violations.add("blank key owned by " + owners);
            } else if (owners == null || owners.size() != 1) {
                violations.add(key + " owners=" + owners);
            }
        });
        return new ObligationResult("KEY", "canonical key uniqueness", violations.isEmpty(),
                Set.of(), Set.of(), violations);
    }

    private static <T> ObligationResult compare(String code, String name,
                                                 Collection<T> expected, Collection<T> actual) {
        Set<String> missing = stringifyDifference(expected, actual);
        Set<String> spurious = stringifyDifference(actual, expected);
        return new ObligationResult(code, name, missing.isEmpty() && spurious.isEmpty(),
                missing, spurious, Set.of());
    }

    private static <T> Set<String> stringifyDifference(Collection<T> left, Collection<T> right) {
        Set<T> difference = new LinkedHashSet<>(left);
        difference.removeAll(new LinkedHashSet<>(right));
        Set<String> result = new TreeSet<>();
        difference.stream().map(String::valueOf).forEach(result::add);
        return Set.copyOf(result);
    }

    public static Set<NavigationFact> navigationFacts(Collection<LinkFact> links) {
        Set<NavigationFact> facts = new LinkedHashSet<>();
        for (LinkFact link : links) {
            facts.add(new NavigationFact(link.associationKey(), link.sourceId(), link.sourceRole(),
                    Direction.FORWARD, link.sourceQualifiers(), link.targetId()));
            facts.add(new NavigationFact(link.associationKey(), link.targetId(), link.targetRole(),
                    Direction.REVERSE, link.targetQualifiers(), link.sourceId()));
        }
        return Set.copyOf(facts);
    }

    public static Set<AllInstancesFact> allInstancesFacts(Collection<TypeFact> types) {
        Set<AllInstancesFact> facts = new LinkedHashSet<>();
        for (TypeFact type : types) facts.add(new AllInstancesFact(type.classKey(), type.objectId()));
        return Set.copyOf(facts);
    }

    private static Set<String> keyFacts(Map<String, Set<String>> keys) {
        Set<String> facts = new TreeSet<>();
        keys.forEach((key, owners) -> owners.forEach(owner -> facts.add(key + "=" + owner)));
        return Set.copyOf(facts);
    }

    public record Snapshot(
            Map<String, Integer> objectNodeCounts,
            Set<TypeFact> typeFacts,
            Set<AttributeFact> attributeFacts,
            Set<LinkFact> linkFacts,
            Map<String, Set<String>> keyOwners,
            long duplicateSemanticLinks,
            List<ObjectObservation> objectObservations,
            List<AttributeObservation> attributeObservations,
            List<LinkObservation> linkObservations,
            Set<AllInstancesFact> allInstancesObservations) {

        public Snapshot(Map<String, Integer> objectNodeCounts,
                        Set<TypeFact> typeFacts,
                        Set<AttributeFact> attributeFacts,
                        Set<LinkFact> linkFacts,
                        Map<String, Set<String>> keyOwners,
                        long duplicateSemanticLinks) {
            this(objectNodeCounts, typeFacts, attributeFacts, linkFacts, keyOwners,
                    duplicateSemanticLinks, observationsFromCounts(objectNodeCounts),
                    observationsFromAttributeFacts(attributeFacts), observationsFromLinkFacts(linkFacts),
                    allInstancesFacts(typeFacts));
        }

        public Snapshot(Map<String, Integer> objectNodeCounts,
                        Set<TypeFact> typeFacts,
                        Set<AttributeFact> attributeFacts,
                        Set<LinkFact> linkFacts,
                        Map<String, Set<String>> keyOwners,
                        long duplicateSemanticLinks,
                        List<ObjectObservation> objectObservations) {
            this(objectNodeCounts, typeFacts, attributeFacts, linkFacts, keyOwners,
                    duplicateSemanticLinks, objectObservations,
                    observationsFromAttributeFacts(attributeFacts), observationsFromLinkFacts(linkFacts),
                    allInstancesFacts(typeFacts));
        }

        public Snapshot(Map<String, Integer> objectNodeCounts,
                        Set<TypeFact> typeFacts,
                        Set<AttributeFact> attributeFacts,
                        Set<LinkFact> linkFacts,
                        Map<String, Set<String>> keyOwners,
                        long duplicateSemanticLinks,
                        List<ObjectObservation> objectObservations,
                        List<AttributeObservation> attributeObservations) {
            this(objectNodeCounts, typeFacts, attributeFacts, linkFacts, keyOwners,
                    duplicateSemanticLinks, objectObservations, attributeObservations,
                    observationsFromLinkFacts(linkFacts), allInstancesFacts(typeFacts));
        }

        public Snapshot(Map<String, Integer> objectNodeCounts,
                        Set<TypeFact> typeFacts,
                        Set<AttributeFact> attributeFacts,
                        Set<LinkFact> linkFacts,
                        Map<String, Set<String>> keyOwners,
                        long duplicateSemanticLinks,
                        List<ObjectObservation> objectObservations,
                        List<AttributeObservation> attributeObservations,
                        List<LinkObservation> linkObservations) {
            this(objectNodeCounts, typeFacts, attributeFacts, linkFacts, keyOwners,
                    duplicateSemanticLinks, objectObservations, attributeObservations,
                    linkObservations, allInstancesFacts(typeFacts));
        }

        public Snapshot {
            objectNodeCounts = immutableCounts(objectNodeCounts);
            typeFacts = Set.copyOf(typeFacts);
            attributeFacts = Set.copyOf(attributeFacts);
            linkFacts = Set.copyOf(linkFacts);
            keyOwners = immutableOwners(keyOwners);
            objectObservations = List.copyOf(objectObservations);
            attributeObservations = List.copyOf(attributeObservations);
            linkObservations = List.copyOf(linkObservations);
            allInstancesObservations = Set.copyOf(allInstancesObservations);
            if (duplicateSemanticLinks < 0) throw new IllegalArgumentException("duplicateSemanticLinks");
        }

        public Set<String> objectIds() {
            return Set.copyOf(objectNodeCounts.keySet());
        }

        private static Map<String, Integer> immutableCounts(Map<String, Integer> source) {
            Map<String, Integer> result = new LinkedHashMap<>();
            source.forEach((key, value) -> result.put(Objects.requireNonNull(key), Objects.requireNonNull(value)));
            return Map.copyOf(result);
        }

        private static Map<String, Set<String>> immutableOwners(Map<String, Set<String>> source) {
            Map<String, Set<String>> result = new LinkedHashMap<>();
            source.forEach((key, value) -> result.put(key, value == null ? Set.of() : Set.copyOf(value)));
            return Map.copyOf(result);
        }

        private static List<ObjectObservation> observationsFromCounts(Map<String, Integer> counts) {
            List<ObjectObservation> observations = new ArrayList<>();
            counts.forEach((id, count) -> {
                if (count == null || count < 0) throw new IllegalArgumentException("object count for " + id);
                for (int index = 0; index < count; index++) {
                    observations.add(new ObjectObservation("fixture:" + id + ":" + index, id, id));
                }
            });
            return List.copyOf(observations);
        }

        private static List<AttributeObservation> observationsFromAttributeFacts(Set<AttributeFact> facts) {
            return facts.stream().map(fact -> new AttributeObservation(
                    "fixture:" + fact.objectId() + ":" + fact.attributeKey(),
                    fact.objectId(), fact.attributeKey(), fact.encodedValue(),
                    fact.objectId() + "::slot::" + fact.attributeKey())).toList();
        }

        private static List<LinkObservation> observationsFromLinkFacts(Set<LinkFact> facts) {
            return facts.stream().map(fact -> new LinkObservation(
                    "fixture:" + fact,
                    "fixture::link::" + fact,
                    fact.associationKey(), fact.sourceId(), fact.targetId(),
                    fact.sourceRole(), fact.targetRole(),
                    fact.sourceQualifiers(), fact.targetQualifiers())).toList();
        }
    }

    public record ObjectObservation(String nodeToken, String useId, String objectKey) {
        public ObjectObservation { Objects.requireNonNull(nodeToken, "nodeToken"); }
    }

    public record ObjectIdentityFact(String useId, String objectKey) {
        public ObjectIdentityFact {
            Objects.requireNonNull(useId, "useId");
            Objects.requireNonNull(objectKey, "objectKey");
        }
    }

    public record AttributeObservation(String nodeToken, String objectId, String attributeKey,
                                       String encodedValue, String slotKey) {
        public AttributeObservation { Objects.requireNonNull(nodeToken, "nodeToken"); }
    }

    public record AttributeSlotFact(String objectId, String attributeKey,
                                    String encodedValue, String slotKey) {
        public AttributeSlotFact {
            Objects.requireNonNull(objectId); Objects.requireNonNull(attributeKey);
            Objects.requireNonNull(encodedValue); Objects.requireNonNull(slotKey);
        }
    }

    public record TypeFact(String objectId, String classKey) {
        public TypeFact { Objects.requireNonNull(objectId); Objects.requireNonNull(classKey); }
    }

    public record AttributeFact(String objectId, String attributeKey, String encodedValue) {
        public AttributeFact {
            Objects.requireNonNull(objectId); Objects.requireNonNull(attributeKey);
            Objects.requireNonNull(encodedValue);
        }
    }

    public record LinkFact(String associationKey, String sourceId, String targetId,
                           String sourceRole, String targetRole,
                           List<String> sourceQualifiers, List<String> targetQualifiers) {
        public LinkFact {
            Objects.requireNonNull(associationKey); Objects.requireNonNull(sourceId);
            Objects.requireNonNull(targetId); Objects.requireNonNull(sourceRole);
            Objects.requireNonNull(targetRole);
            sourceQualifiers = List.copyOf(sourceQualifiers);
            targetQualifiers = List.copyOf(targetQualifiers);
        }
    }

    public record LinkObservation(String relationshipToken, String linkKey,
                                  String associationKey, String sourceId, String targetId,
                                  String sourceRole, String targetRole,
                                  List<String> sourceQualifiers, List<String> targetQualifiers) {
        public LinkObservation { Objects.requireNonNull(relationshipToken, "relationshipToken"); }
    }

    public record LinkSlotFact(String linkKey, String associationKey,
                               String sourceId, String targetId,
                               String sourceRole, String targetRole) {
        public LinkSlotFact {
            Objects.requireNonNull(linkKey); Objects.requireNonNull(associationKey);
            Objects.requireNonNull(sourceId); Objects.requireNonNull(targetId);
            Objects.requireNonNull(sourceRole); Objects.requireNonNull(targetRole);
        }
    }

    public record QualifierFact(String linkKey, Direction direction, List<String> qualifiers) {
        public QualifierFact {
            Objects.requireNonNull(linkKey); Objects.requireNonNull(direction);
            qualifiers = List.copyOf(qualifiers);
        }
    }

    public enum Direction { FORWARD, REVERSE }

    public record NavigationFact(String associationKey, String receiverId, String role,
                                 Direction direction, List<String> qualifiers, String targetId) {
        public NavigationFact {
            Objects.requireNonNull(associationKey); Objects.requireNonNull(receiverId);
            Objects.requireNonNull(role); Objects.requireNonNull(direction);
            qualifiers = List.copyOf(qualifiers); Objects.requireNonNull(targetId);
        }
    }

    public record AllInstancesFact(String classKey, String objectId) {
        public AllInstancesFact { Objects.requireNonNull(classKey); Objects.requireNonNull(objectId); }
    }

    public record ObligationResult(String code, String name, boolean passed,
                                   Set<String> missing, Set<String> spurious, Set<String> details) {
        public ObligationResult {
            missing = Set.copyOf(missing); spurious = Set.copyOf(spurious); details = Set.copyOf(details);
        }
    }

    public record Report(List<ObligationResult> obligations, long duplicateSemanticLinks) {
        public Report { obligations = List.copyOf(obligations); }

        public boolean passed() {
            return obligations.stream().allMatch(ObligationResult::passed);
        }

        public long missingFacts() {
            return obligations.stream().mapToLong(result -> result.missing().size()).sum();
        }

        public long spuriousFacts() {
            return obligations.stream().mapToLong(result -> result.spurious().size()).sum();
        }

        public long failedObligations() {
            return obligations.stream().filter(result -> !result.passed()).count();
        }

        /** Machine-readable summary for the evaluation tables and benchmark artifacts. */
        public String toCsv(String dataset, String profile) {
            StringBuilder csv = new StringBuilder();
            csv.append("dataset,profile,obligation,passed,missingFacts,spuriousFacts,details")
                    .append(System.lineSeparator());
            for (ObligationResult obligation : obligations) {
                csv.append(csv(dataset)).append(',')
                        .append(csv(profile)).append(',')
                        .append(csv(obligation.code())).append(',')
                        .append(obligation.passed()).append(',')
                        .append(obligation.missing().size()).append(',')
                        .append(obligation.spurious().size()).append(',')
                        .append(obligation.details().size())
                        .append(System.lineSeparator());
            }
            return csv.toString();
        }

        public String render() {
            StringBuilder result = new StringBuilder();
            for (ObligationResult obligation : obligations) {
                result.append(obligation.code()).append('=')
                        .append(obligation.passed() ? "PASS" : "FAIL")
                        .append(" name=").append(obligation.name());
                if (!obligation.missing().isEmpty()) result.append(" missing=").append(obligation.missing());
                if (!obligation.spurious().isEmpty()) result.append(" spurious=").append(obligation.spurious());
                if (!obligation.details().isEmpty()) result.append(" details=").append(obligation.details());
                result.append(System.lineSeparator());
            }
            result.append("duplicateSemanticLinks=").append(duplicateSemanticLinks);
            return result.toString();
        }

        private static String csv(String value) {
            String escaped = Objects.requireNonNull(value).replace("\"", "\"\"");
            return "\"" + escaped + "\"";
        }
    }
}
