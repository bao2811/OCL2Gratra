package org.uet.dse.neo4jtgg.experiment;

import org.uet.dse.neo4j.encoding.CanonicalGraphEncoding;
import org.uet.dse.neo4jtgg.ocl.ir.OclCypherPlan;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * One shared-snapshot implementation witness for the PA-COMP composition rule.
 * A certificate is issued only after exact M2/PA2--PA8, all six observation
 * equalities, snapshot stability, and PA9 renderer-accessor checks pass.
 */
public record AdapterAdequacyCertificate(
        String snapshotId,
        String modelName,
        String modelKey,
        String sourceFingerprint,
        String graphFingerprint,
        String metamodelFingerprint,
        String rendererVersion,
        Map<Observation, ObservationDiff> observationDiffs,
        RepresentationAdequacyEvaluator.Report representationReport,
        RendererAccessorReport rendererReport) {

    public AdapterAdequacyCertificate {
        requireText(snapshotId, "snapshotId");
        requireText(modelName, "modelName");
        requireText(modelKey, "modelKey");
        requireText(sourceFingerprint, "sourceFingerprint");
        requireText(graphFingerprint, "graphFingerprint");
        requireText(metamodelFingerprint, "metamodelFingerprint");
        requireText(rendererVersion, "rendererVersion");
        observationDiffs = Map.copyOf(observationDiffs);
        representationReport = Objects.requireNonNull(representationReport, "representationReport");
        rendererReport = Objects.requireNonNull(rendererReport, "rendererReport");
        if (!representationReport.passed() || !rendererReport.passed()
                || observationDiffs.size() != Observation.values().length
                || observationDiffs.values().stream().anyMatch(diff -> !diff.equal())) {
            throw new IllegalArgumentException("An inadequate snapshot cannot be certified");
        }
    }

    public static AdapterAdequacyCertificate issue(AdapterAdequacySnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        String expectedModelKey = CanonicalGraphEncoding.modelKey(snapshot.modelName());
        if (!expectedModelKey.equals(snapshot.modelKey())) {
            throw new IllegalStateException("Model key does not identify the certified model");
        }
        String sourceStart = fingerprint(snapshot.sourceAtStart());
        String sourceEnd = fingerprint(snapshot.sourceAtEnd());
        if (!sourceStart.equals(sourceEnd)) {
            throw new IllegalStateException("Source model changed while the adapter snapshot was captured");
        }
        String graphStart = fingerprint(snapshot.graphAtStart());
        String graphEnd = fingerprint(snapshot.graphAtEnd());
        if (!graphStart.equals(graphEnd)) {
            throw new IllegalStateException("Graph changed while the adapter snapshot was captured");
        }

        RepresentationAdequacyEvaluator.Report representation =
                RepresentationAdequacyEvaluator.evaluate(snapshot.sourceAtEnd(), snapshot.graphAtEnd());
        if (!representation.passed()) {
            throw new IllegalStateException("PA/exact-M2 premises failed:\n" + representation.render());
        }
        Map<Observation, ObservationDiff> diffs = compareObservations(
                snapshot.sourceAtEnd(), snapshot.graphAtEnd());
        List<Observation> failed = diffs.entrySet().stream()
                .filter(entry -> !entry.getValue().equal()).map(Map.Entry::getKey).toList();
        if (!failed.isEmpty()) {
            throw new IllegalStateException("Adapter observation equalities failed: " + failed);
        }
        RendererAccessorReport renderer = verifyRendererAccessors(
                snapshot.modelName(), snapshot.plans());
        if (!renderer.passed()) {
            throw new IllegalStateException("PA9 renderer accessor agreement failed: " + renderer.details());
        }
        return new AdapterAdequacyCertificate(
                snapshot.snapshotId(), snapshot.modelName(), snapshot.modelKey(),
                sourceEnd, graphEnd,
                digest(canonicalMetamodel(snapshot.sourceAtEnd())),
                snapshot.rendererVersion(), diffs, representation, renderer);
    }

    public static Map<Observation, ObservationDiff> compareObservations(
            RepresentationAdequacyEvaluator.Snapshot source,
            RepresentationAdequacyEvaluator.Snapshot graph) {
        Map<Observation, ObservationDiff> result = new LinkedHashMap<>();
        result.put(Observation.OBJECTS, diff(canonicalObjects(source), canonicalObjects(graph)));
        result.put(Observation.METAMODEL, diff(canonicalMetamodel(source), canonicalMetamodel(graph)));
        result.put(Observation.TYPE, diff(strings(source.typeFacts()), strings(graph.typeFacts())));
        result.put(Observation.VALUE, diff(canonicalValues(source), canonicalValues(graph)));
        result.put(Observation.NAV, diff(
                strings(RepresentationAdequacyEvaluator.navigationFacts(source.linkFacts())),
                strings(RepresentationAdequacyEvaluator.navigationFacts(graph.linkFacts()))));
        result.put(Observation.ALL_INSTANCES, diff(
                strings(source.allInstancesObservations()), strings(graph.allInstancesObservations())));
        return Map.copyOf(result);
    }

    private static RendererAccessorReport verifyRendererAccessors(
            String modelName, List<InstrumentedCompilationResult> plans) {
        Set<String> details = new TreeSet<>();
        Set<String> fingerprints = new TreeSet<>();
        if (plans.isEmpty()) details.add("no certified plan supplied");
        for (InstrumentedCompilationResult compiled : plans) {
            if (compiled == null || compiled.queryPlan() == null) {
                details.add("missing compiled query plan");
                continue;
            }
            String cypher = compiled.cypher();
            String rule = compiled.queryPlan().contextClassName() + "::"
                    + compiled.queryPlan().invariantName();
            String contextKey = CanonicalGraphEncoding.classKey(
                    modelName, compiled.queryPlan().contextClassName());
            if (!compiled.parameters().containsValue(contextKey)) {
                details.add(rule + " missing canonical context classKey parameter");
            }
            String modelKey = CanonicalGraphEncoding.modelKey(modelName);
            if (!compiled.parameters().containsValue(modelKey)) {
                details.add(rule + " missing canonical modelKey parameter");
            }
            if (!cypher.contains("MATCH (self:Object {modelKey: $")
                    || !cypher.contains("-[:ObjectInstanceOf]->(cls:UmlClass {modelKey: $")
                    || !cypher.contains("classKey: $")) {
                details.add(rule + " does not use the canonical context accessor");
            }
            if (cypher.contains("ENDS WITH") || cypher.contains(".associationName")
                    || cypher.contains(".qualifierPayload") || cypher.contains("(cls:Class")) {
                details.add(rule + " uses legacy/display-name accessor syntax");
            }
            AccessorNeeds needs = inspect(compiled.queryPlan().predicate());
            if (needs.attribute() && (!cypher.contains(":ObjectHasAttribute")
                    || !cypher.contains(".attributeKey = $"))) {
                details.add(rule + " missing exact attributeKey accessor");
            }
            if (needs.navigation() && (!cypher.contains(".associationKey = $")
                    || !cypher.contains(".sourceRole = $") || !cypher.contains(".targetRole = $"))) {
                details.add(rule + " missing exact association/role accessor");
            }
            if (needs.qualifier() && !(cypher.contains(".sourceQualifiers[")
                    || cypher.contains(".targetQualifiers["))) {
                details.add(rule + " missing direction-specific qualifier accessor");
            }
            if (needs.allInstances() && (!cypher.contains(":UmlClass {modelKey: $")
                    || !cypher.contains("classKey: $")
                    || !cypher.contains("RETURN DISTINCT"))) {
                details.add(rule + " missing canonical allInstances accessor");
            }
            fingerprints.add(digest(List.of(rule, cypher, canonicalParameters(compiled.parameters()))));
        }
        return new RendererAccessorReport(details.isEmpty(), details, fingerprints);
    }

    private static AccessorNeeds inspect(OclCypherPlan.ExpressionPlan expression) {
        AccessorNeeds needs = new AccessorNeeds(false, false, false, false);
        if (expression instanceof OclCypherPlan.AttributeAccessPlan value) {
            needs = needs.merge(new AccessorNeeds(true, false, false, false)).merge(inspect(value.source()));
        } else if (expression instanceof OclCypherPlan.NavigationAccessPlan value) {
            needs = needs.merge(new AccessorNeeds(false, true, !value.qualifiers().isEmpty(), false))
                    .merge(inspect(value.source()));
            for (var qualifier : value.qualifiers()) needs = needs.merge(inspect(qualifier));
        } else if (expression instanceof OclCypherPlan.MethodCallPlan value) {
            needs = needs.merge(inspect(value.source()));
            if ("allInstances".equalsIgnoreCase(value.methodName())) {
                needs = needs.merge(new AccessorNeeds(false, false, false, true));
            }
            for (var argument : value.arguments()) needs = needs.merge(inspect(argument));
        } else if (expression instanceof OclCypherPlan.SetLiteralPlan value) {
            for (var element : value.elements()) needs = needs.merge(inspect(element));
        } else if (expression instanceof OclCypherPlan.NotPlan value) {
            needs = needs.merge(inspect(value.expression()));
        } else if (expression instanceof OclCypherPlan.BinaryPlan value) {
            needs = needs.merge(inspect(value.left())).merge(inspect(value.right()));
        } else if (expression instanceof OclCypherPlan.IfPlan value) {
            needs = needs.merge(inspect(value.condition())).merge(inspect(value.thenBranch()))
                    .merge(inspect(value.elseBranch()));
        } else if (expression instanceof OclCypherPlan.LetPlan value) {
            needs = needs.merge(inspect(value.value())).merge(inspect(value.body()));
        } else if (expression instanceof OclCypherPlan.CollectionOperationPlan value) {
            needs = needs.merge(inspect(value.source()));
            for (var argument : value.arguments()) needs = needs.merge(inspect(argument));
        } else if (expression instanceof OclCypherPlan.IteratorOperationPlan value) {
            needs = needs.merge(inspect(value.source())).merge(inspect(value.body()));
        } else if (expression instanceof OclCypherPlan.ExistsSubqueryPlan value) {
            needs = needs.merge(inspectMatch(value.match()));
        } else if (expression instanceof OclCypherPlan.NotExistsSubqueryPlan value) {
            needs = needs.merge(inspectMatch(value.match()));
        } else if (expression instanceof OclCypherPlan.CountSubqueryComparisonPlan value) {
            needs = needs.merge(inspectMatch(value.match()));
        } else if (expression instanceof OclCypherPlan.NavigationAggregationPlan value) {
            needs = needs.merge(inspectMatch(value.match()));
            if (value.projection() != null) needs = needs.merge(inspect(value.projection()));
        } else if (expression instanceof OclCypherPlan.NavigationUniquenessPlan value) {
            needs = needs.merge(inspectMatch(value.match())).merge(inspect(value.projection()));
        }
        return needs;
    }

    private static AccessorNeeds inspectMatch(OclCypherPlan.NavigationMatchPlan match) {
        AccessorNeeds result = new AccessorNeeds(false, true,
                !match.navigation().qualifiers().isEmpty(), false).merge(inspect(match.owner()));
        if (match.predicate() != null) result = result.merge(inspect(match.predicate()));
        return result;
    }

    private static Set<String> canonicalObjects(RepresentationAdequacyEvaluator.Snapshot snapshot) {
        Set<String> result = new TreeSet<>();
        snapshot.objectObservations().forEach(value -> result.add(value.useId() + "|" + value.objectKey()));
        return Set.copyOf(result);
    }

    private static Set<String> canonicalValues(RepresentationAdequacyEvaluator.Snapshot snapshot) {
        Set<String> result = new TreeSet<>();
        snapshot.attributeObservations().forEach(value -> result.add(value.objectId() + "|"
                + value.attributeKey() + "|" + value.encodedValue() + "|" + value.slotKey()));
        return Set.copyOf(result);
    }

    private static Set<String> canonicalMetamodel(RepresentationAdequacyEvaluator.Snapshot snapshot) {
        Set<String> result = new TreeSet<>();
        snapshot.keyOwners().forEach((key, owners) -> owners.forEach(owner -> result.add(key + "=" + owner)));
        return Set.copyOf(result);
    }

    private static Set<String> strings(Collection<?> values) {
        Set<String> result = new TreeSet<>();
        values.stream().map(String::valueOf).forEach(result::add);
        return Set.copyOf(result);
    }

    private static ObservationDiff diff(Set<String> expected, Set<String> actual) {
        Set<String> missing = new TreeSet<>(expected);
        missing.removeAll(actual);
        Set<String> spurious = new TreeSet<>(actual);
        spurious.removeAll(expected);
        return new ObservationDiff(missing, spurious);
    }

    private static String fingerprint(RepresentationAdequacyEvaluator.Snapshot snapshot) {
        List<String> facts = new ArrayList<>();
        for (Observation observation : Observation.values()) {
            ObservationDiff self = compareObservations(snapshot, snapshot).get(observation);
            if (!self.equal()) throw new IllegalStateException("Snapshot is not reflexive: " + observation);
        }
        facts.addAll(canonicalObjects(snapshot));
        facts.addAll(canonicalMetamodel(snapshot));
        facts.addAll(strings(snapshot.typeFacts()));
        facts.addAll(canonicalValues(snapshot));
        facts.addAll(strings(snapshot.linkObservations()));
        facts.addAll(strings(snapshot.allInstancesObservations()));
        facts.add("duplicateSemanticLinks=" + snapshot.duplicateSemanticLinks());
        return digest(facts);
    }

    private static String canonicalParameters(Map<String, Object> parameters) {
        return parameters.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + String.valueOf(entry.getValue()))
                .reduce((left, right) -> left + ";" + right).orElse("");
    }

    private static String digest(Collection<String> values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            values.stream().sorted().forEach(value -> {
                digest.update(value.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) '\n');
            });
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name);
    }

    public enum Observation { OBJECTS, METAMODEL, TYPE, VALUE, NAV, ALL_INSTANCES }

    public record ObservationDiff(Set<String> missing, Set<String> spurious) {
        public ObservationDiff {
            missing = Set.copyOf(missing);
            spurious = Set.copyOf(spurious);
        }
        public boolean equal() { return missing.isEmpty() && spurious.isEmpty(); }
    }

    public record RendererAccessorReport(boolean passed, Set<String> details,
                                         Set<String> planFingerprints) {
        public RendererAccessorReport {
            details = Set.copyOf(details);
            planFingerprints = Set.copyOf(planFingerprints);
            if (passed != details.isEmpty()) throw new IllegalArgumentException("renderer report status");
        }
    }

    public record AdapterAdequacySnapshot(
            String snapshotId,
            String modelName,
            String modelKey,
            String rendererVersion,
            RepresentationAdequacyEvaluator.Snapshot sourceAtStart,
            RepresentationAdequacyEvaluator.Snapshot sourceAtEnd,
            RepresentationAdequacyEvaluator.Snapshot graphAtStart,
            RepresentationAdequacyEvaluator.Snapshot graphAtEnd,
            List<InstrumentedCompilationResult> plans) {
        public AdapterAdequacySnapshot {
            requireText(snapshotId, "snapshotId");
            requireText(modelName, "modelName");
            requireText(modelKey, "modelKey");
            requireText(rendererVersion, "rendererVersion");
            sourceAtStart = Objects.requireNonNull(sourceAtStart, "sourceAtStart");
            sourceAtEnd = Objects.requireNonNull(sourceAtEnd, "sourceAtEnd");
            graphAtStart = Objects.requireNonNull(graphAtStart, "graphAtStart");
            graphAtEnd = Objects.requireNonNull(graphAtEnd, "graphAtEnd");
            plans = List.copyOf(plans);
        }
    }

    private record AccessorNeeds(boolean attribute, boolean navigation,
                                 boolean qualifier, boolean allInstances) {
        private AccessorNeeds merge(AccessorNeeds other) {
            return new AccessorNeeds(attribute || other.attribute, navigation || other.navigation,
                    qualifier || other.qualifier, allInstances || other.allInstances);
        }
    }
}
