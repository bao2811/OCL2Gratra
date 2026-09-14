package org.uet.dse.ocl2cypher.graph;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.uet.dse.ocl2cypher.diagnostics.Result;
import org.uet.dse.ocl2cypher.diagnostics.Stage;
import org.uet.dse.ocl2cypher.runtime.OclEquality;
import org.uet.dse.ocl2cypher.runtime.OclValue;
import org.uet.dse.ocl2cypher.source.model.SchemaModel;
import org.uet.dse.ocl2cypher.source.model.Snapshot;
import org.uet.dse.ocl2cypher.source.model.UmlAssociation;
import org.uet.dse.ocl2cypher.source.model.UmlAttribute;
import org.uet.dse.ocl2cypher.source.model.UmlQualifier;

/** Independent executable check of the finite ValidRep obligations. */
public final class ValidRepChecker {
    private ValidRepChecker() {
    }

    /** The checked one-to-one source-object to graph-node correspondence. */
    public record Witness(Map<String, String> objectCorrespondence) {
        public Witness {
            objectCorrespondence = Map.copyOf(objectCorrespondence);
        }
    }

    public static Result<Witness> check(SchemaModel sm, Snapshot sn, GraphModel g) {
        try {
            if (!sm.modelKey().equals(g.modelKey())) {
                return failure("VALIDREP_MODEL_KEY", "schema and graph modelKey differ");
            }
            for (GraphModel.Node n : g.nodes()) {
                if (!g.modelKey().equals(n.modelKey())
                        || !g.modelKey().equals(n.properties().get("modelKey"))) {
                    return failure("VALIDREP_MODEL_SCOPE", "node outside model scope: " + n.stableKey());
                }
            }
            for (GraphModel.Relationship r : g.relationships()) {
                if (!g.modelKey().equals(r.modelKey())
                        || !g.modelKey().equals(r.properties().get("modelKey"))) {
                    return failure("VALIDREP_MODEL_SCOPE",
                            "relationship outside model scope: " + r.stableKey());
                }
            }

            Set<String> sourceIds = new LinkedHashSet<>();
            Map<String, String> mu = new LinkedHashMap<>();
            for (Snapshot.ObjectDef o : sn.objects()) {
                if (!sourceIds.add(o.stableId())) {
                    return failure("VALIDREP_DUPLICATE_SOURCE_ID", "duplicate object id " + o.stableId());
                }
                if (!sm.hasClass(o.dynamicClassKey())) {
                    return failure("VALIDREP_DYNAMIC_TYPE", "unknown dynamic class " + o.dynamicClassKey());
                }
                String nodeKey = GraphKey.object(g.modelKey(), o.stableId());
                GraphModel.Node n;
                try {
                    n = g.node(nodeKey);
                } catch (RuntimeException e) {
                    return failure("VALIDREP_MISSING_OBJECT", "missing graph object " + o.stableId());
                }
                String expectedRole = sm.clazz(o.dynamicClassKey()).isAssociationClass()
                        ? "ASSOCIATION_CLASS_OBJECT" : "OBJECT";
                if (!expectedRole.equals(n.observationRole())
                        || !o.stableId().equals(n.properties().get("objectKey"))
                        || !o.stableId().equals(n.properties().get("use_id"))) {
                    return failure("VALIDREP_IDENTITY", "identity mismatch for " + o.stableId());
                }
                if (!o.dynamicClassKey().equals(GraphObservation.directType(g, o.stableId()))) {
                    return failure("VALIDREP_DYNAMIC_TYPE", "dynamic type mismatch for " + o.stableId());
                }
                mu.put(o.stableId(), nodeKey);
            }

            Set<String> graphIds = new LinkedHashSet<>();
            for (GraphModel.Node n : g.nodes()) {
                if ("OBJECT".equals(n.observationRole())
                        || "ASSOCIATION_CLASS_OBJECT".equals(n.observationRole())) {
                    String id = n.properties().get("objectKey");
                    if (id == null || !graphIds.add(id)) {
                        return failure("VALIDREP_GHOST_OBJECT", "missing or duplicate graph object identity");
                    }
                }
            }
            if (!graphIds.equals(sourceIds)) {
                return failure("VALIDREP_GHOST_OBJECT", "source and graph object sets differ");
            }

            Result<Witness> attributes = checkAttributes(sm, sn, g, mu);
            if (attributes.isFailure()) {
                return attributes;
            }
            Result<Witness> links = checkLinks(sm, sn, g, mu);
            if (links.isFailure()) {
                return links;
            }
            return Result.success(new Witness(mu));
        } catch (GraphValueCodec.CodecException e) {
            return failure(e.code(), e.getMessage());
        } catch (RuntimeException e) {
            return failure("VALIDREP_OBSERVER_FAILURE", e.getMessage());
        }
    }

    private static Result<Witness> checkAttributes(SchemaModel sm, Snapshot sn, GraphModel g,
                                                    Map<String, String> mu) {
        int expectedSlots = 0;
        for (Snapshot.ObjectDef o : sn.objects()) {
            for (Map.Entry<String, OclValue> slot : sn.attributeSlots(o.stableId()).entrySet()) {
                UmlAttribute declared = nearestAttribute(sm, o.dynamicClassKey(), slot.getKey());
                if (declared == null) {
                    return failure("VALIDREP_UNKNOWN_SLOT", "unknown slot " + o.stableId()
                            + "." + slot.getKey());
                }
                if (!slot.getValue().type().equals(declared.declaredType())) {
                    return failure("VALIDREP_SLOT_TYPE", "slot type mismatch for "
                            + o.stableId() + "." + slot.getKey());
                }
                expectedSlots++;
            }
            for (UmlAttribute attr : applicableAttributes(sm, o.dynamicClassKey())) {
                OclValue expected = sn.attributeSlot(o.stableId(), attr.name())
                        .orElseGet(() -> new OclValue.BottomValue(attr.declaredType()));
                OclValue observed = GraphObservation.attribute(g, sm, o.stableId(),
                        attr.ownerClassKey(), attr.name());
                if (OclEquality.equal(expected, observed) != OclEquality.BoolKind.TRUE) {
                    return failure("VALIDREP_ATTRIBUTE", "attribute observation differs for "
                            + o.stableId() + "." + attr.name());
                }
            }
        }
        long actualSlots = g.nodes().stream()
                .filter(n -> "ATTRIBUTE_VALUE".equals(n.observationRole())).count();
        if (actualSlots != expectedSlots) {
            return failure("VALIDREP_GHOST_SLOT", "source and graph slot counts differ");
        }
        return Result.success(new Witness(mu));
    }

    private static Result<Witness> checkLinks(SchemaModel sm, Snapshot sn, GraphModel g,
                                               Map<String, String> mu) {
        Map<LinkSignature, Integer> expected = new HashMap<>();
        for (Snapshot.LinkDef link : sn.links()) {
            UmlAssociation assoc = resolveAssociation(sm, link.associationName);
            if (assoc == null) {
                return failure("VALIDREP_UNKNOWN_ASSOCIATION", "unknown association "
                        + link.associationName);
            }
            if (!sn.hasObject(link.sourceStableId) || !sn.hasObject(link.targetStableId)) {
                return failure("VALIDREP_DANGLING_LINK", "link endpoint is absent");
            }
            if (!sm.conforms(sn.object(link.sourceStableId).dynamicClassKey(), assoc.sourceClassKey())
                    || !sm.conforms(sn.object(link.targetStableId).dynamicClassKey(), assoc.targetClassKey())) {
                return failure("VALIDREP_LINK_TYPE", "link endpoints do not conform to " + assoc.name());
            }
            if (link.qualifiers.size() != assoc.qualifierNames().size()) {
                return failure("VALIDREP_QUALIFIER_ARITY", "qualifier arity differs for " + assoc.name());
            }
            List<String> qualifiers = new ArrayList<>();
            for (int i = 0; i < link.qualifiers.size(); i++) {
                if (!assoc.qualifierNames().get(i).equals(link.qualifiers.get(i).name())) {
                    return failure("VALIDREP_QUALIFIER_NAME", "qualifier name differs for " + assoc.name());
                }
                Object rawQualifier = link.qualifiers.get(i).value();
                UmlQualifier declaration = assoc.qualifiers().get(i);
                if (!(rawQualifier instanceof OclValue qualifierValue)
                        || qualifierValue.isBottom()
                        || !declaration.declaredType().equals(qualifierValue.type())) {
                    return failure("VALIDREP_QUALIFIER_TYPE", "qualifier type differs for "
                            + assoc.name() + "." + declaration.name());
                }
                if (declaration.finiteDomainComplete()
                        && declaration.finiteDomain().stream().noneMatch(domainValue ->
                                OclEquality.equal(domainValue, qualifierValue)
                                        == OclEquality.BoolKind.TRUE)) {
                    return failure("VALIDREP_QUALIFIER_DOMAIN", "qualifier value is outside "
                            + "the declared finite domain for " + assoc.name() + "."
                            + declaration.name());
                }
                GraphValueCodec.validateObjectReference(
                        declaration.declaredType(), qualifierValue, sm, sn);
                qualifiers.add(qualifierPayload(
                        declaration.declaredType(), qualifierValue));
            }
            increment(expected, new LinkSignature(assoc.key(),
                    GraphKey.object(g.modelKey(), link.sourceStableId),
                    GraphKey.object(g.modelKey(), link.targetStableId), qualifiers));
        }

        Map<String, UmlAssociation> associationsByKey = new HashMap<>();
        for (UmlAssociation association : sm.associations()) {
            associationsByKey.put(association.key(), association);
        }
        Map<LinkSignature, Integer> actual = new HashMap<>();
        for (GraphModel.Relationship r : g.relationships()) {
            if (!GraphModel.LINK_ASSOCIATE_WITH.equals(r.physicalType())) {
                continue;
            }
            UmlAssociation assoc = associationsByKey.get(r.properties().get("associationKey"));
            if (assoc == null
                    || !assoc.name().equals(r.properties().get("associationName"))
                    || !assoc.sourceRole().equals(r.properties().get("sourceRole"))
                    || !assoc.targetRole().equals(r.properties().get("targetRole"))) {
                return failure("VALIDREP_LINK_METADATA", "link metadata differs: " + r.stableKey());
            }
            List<String> qualifiers = new ArrayList<>();
            for (int i = 0; i < assoc.qualifierNames().size(); i++) {
                String value = r.properties().get("qualifier::" + i);
                if (value == null) {
                    return failure("VALIDREP_QUALIFIER_VALUE", "missing qualifier on " + r.stableKey());
                }
                qualifiers.add(value);
            }
            increment(actual, new LinkSignature(assoc.key(), r.sourceKey(), r.targetKey(), qualifiers));
        }
        for (Snapshot.LinkDef link : sn.links()) {
            if (link.associationClassObjectStableId == null) continue;
            UmlAssociation assoc = resolveAssociation(sm, link.associationName);
            String occurrenceKey = GraphKey.object(
                    g.modelKey(), link.associationClassObjectStableId);
            List<GraphModel.Relationship> sourceEdges = g.outgoing(occurrenceKey,
                    GraphModel.associationClassSourceParticipant(assoc.key()));
            List<GraphModel.Relationship> targetEdges = g.outgoing(occurrenceKey,
                    GraphModel.associationClassTargetParticipant(assoc.key()));
            if (sourceEdges.size() != 1 || targetEdges.size() != 1
                    || !GraphKey.object(g.modelKey(), link.sourceStableId)
                            .equals(sourceEdges.get(0).targetKey())
                    || !GraphKey.object(g.modelKey(), link.targetStableId)
                            .equals(targetEdges.get(0).targetKey())) {
                return failure("VALIDREP_ASSOCIATION_CLASS",
                        "association-class participants differ for "
                                + link.associationClassObjectStableId);
            }
            List<String> qualifiers = new ArrayList<>();
            for (int i = 0; i < assoc.qualifierNames().size(); i++) {
                String value = sourceEdges.get(0).properties().get("qualifier::" + i);
                if (value == null || !value.equals(
                        targetEdges.get(0).properties().get("qualifier::" + i))) {
                    return failure("VALIDREP_ASSOCIATION_CLASS",
                            "association-class qualifier differs for "
                                    + link.associationClassObjectStableId);
                }
                qualifiers.add(value);
            }
            increment(actual, new LinkSignature(assoc.key(),
                    sourceEdges.get(0).targetKey(), targetEdges.get(0).targetKey(), qualifiers));
        }
        if (!expected.equals(actual)) {
            return failure("VALIDREP_NAVIGATION", "source and graph link multisets differ");
        }
        Result<Witness> multiplicity = checkMultiplicity(sm, sn);
        if (multiplicity.isFailure()) {
            return multiplicity;
        }
        return Result.success(new Witness(mu));
    }

    /** Check UML end bounds on the observed link occurrence multiset. */
    private static Result<Witness> checkMultiplicity(SchemaModel sm, Snapshot sn) {
        for (UmlAssociation assoc : sm.associations()) {
            Map<Group, Integer> outgoing = new HashMap<>();
            Map<Group, Integer> incoming = new HashMap<>();
            Map<Group, Set<String>> uniqueTargets = new HashMap<>();
            for (Snapshot.LinkDef link : sn.links()) {
                UmlAssociation actual = resolveAssociation(sm, link.associationName);
                if (actual == null || !actual.key().equals(assoc.key())) {
                    continue;
                }
                List<String> qualifiers = qualifierPayloads(assoc, link);
                Group out = new Group(link.sourceStableId, qualifiers);
                Group in = new Group(link.targetStableId, qualifiers);
                increment(outgoing, out);
                increment(incoming, in);
                if (assoc.isUnique()
                        && !uniqueTargets.computeIfAbsent(out, k -> new LinkedHashSet<>())
                                .add(link.targetStableId)) {
                    return failure("VALIDREP_SET_DUPLICATE",
                            "duplicate target occurrence in unique association " + assoc.name());
                }
            }

            // For unqualified ends, zero-count groups are observable and lower
            // bounds must be checked for every conforming endpoint.  For a
            // qualified end, the finite snapshot only exposes groups whose
            // qualifier key occurs; bounds are checked per such key.
            for (Snapshot.ObjectDef source : sn.objects()) {
                if (!sm.conforms(source.dynamicClassKey(), assoc.sourceClassKey())) continue;
                if (assoc.qualifierNames().isEmpty()) {
                    Result<Witness> r = bound(outgoing.getOrDefault(
                            new Group(source.stableId(), List.of()), 0),
                            assoc.targetLower(), assoc.targetUpper(), assoc.name(), "target");
                    if (r.isFailure()) return r;
                }
            }
            for (Snapshot.ObjectDef target : sn.objects()) {
                if (!sm.conforms(target.dynamicClassKey(), assoc.targetClassKey())) continue;
                if (assoc.qualifierNames().isEmpty()) {
                    Result<Witness> r = bound(incoming.getOrDefault(
                            new Group(target.stableId(), List.of()), 0),
                            assoc.sourceLower(), assoc.sourceUpper(), assoc.name(), "source");
                    if (r.isFailure()) return r;
                }
            }
            if (!assoc.qualifierNames().isEmpty()) {
                boolean finiteDomainKnown = assoc.qualifiers().stream()
                        .allMatch(UmlQualifier::finiteDomainComplete);
                if (!finiteDomainKnown
                        && (assoc.targetLower() > 0 || assoc.sourceLower() > 0)) {
                    return failure("VALIDREP_QUALIFIER_DOMAIN_REQUIRED",
                            "cannot check positive lower multiplicity of qualified association "
                                    + assoc.name() + " without complete finite qualifier domains");
                }
                for (Map.Entry<Group, Integer> e : outgoing.entrySet()) {
                    Result<Witness> r = bound(e.getValue(), assoc.targetLower(),
                            assoc.targetUpper(), assoc.name(), "target/qualifier");
                    if (r.isFailure()) return r;
                }
                for (Map.Entry<Group, Integer> e : incoming.entrySet()) {
                    Result<Witness> r = bound(e.getValue(), assoc.sourceLower(),
                            assoc.sourceUpper(), assoc.name(), "source/qualifier");
                    if (r.isFailure()) return r;
                }
                if (finiteDomainKnown) {
                    List<List<String>> qualifierKeys = qualifierKeys(assoc.qualifiers());
                    // An explicitly complete empty domain denotes that no
                    // qualifier key exists.  Therefore every endpoint has
                    // cardinality zero; a positive lower bound is invalid
                    // even though there are no concrete keys to enumerate.
                    if (qualifierKeys.isEmpty()
                            && (assoc.targetLower() > 0 || assoc.sourceLower() > 0)) {
                        return failure("VALIDREP_MULTIPLICITY",
                                "positive lower multiplicity on " + assoc.name()
                                        + " is impossible with an empty qualifier domain");
                    }
                    for (Snapshot.ObjectDef source : sn.objects()) {
                        if (!sm.conforms(source.dynamicClassKey(), assoc.sourceClassKey())) continue;
                        for (List<String> key : qualifierKeys) {
                            Result<Witness> r = bound(outgoing.getOrDefault(
                                            new Group(source.stableId(), key), 0),
                                    assoc.targetLower(), assoc.targetUpper(), assoc.name(),
                                    "target/qualifier");
                            if (r.isFailure()) return r;
                        }
                    }
                    for (Snapshot.ObjectDef target : sn.objects()) {
                        if (!sm.conforms(target.dynamicClassKey(), assoc.targetClassKey())) continue;
                        for (List<String> key : qualifierKeys) {
                            Result<Witness> r = bound(incoming.getOrDefault(
                                            new Group(target.stableId(), key), 0),
                                    assoc.sourceLower(), assoc.sourceUpper(), assoc.name(),
                                    "source/qualifier");
                            if (r.isFailure()) return r;
                        }
                    }
                }
            }
        }
        return Result.success(new Witness(Map.of()));
    }

    private static List<List<String>> qualifierKeys(List<UmlQualifier> declarations) {
        List<List<String>> keys = new ArrayList<>();
        keys.add(List.of());
        for (UmlQualifier declaration : declarations) {
            List<List<String>> expanded = new ArrayList<>();
            for (List<String> prefix : keys) {
                for (OclValue value : declaration.finiteDomain()) {
                    List<String> key = new ArrayList<>(prefix);
                    key.add(qualifierPayload(declaration.declaredType(), value));
                    expanded.add(List.copyOf(key));
                }
            }
            keys = expanded;
        }
        return List.copyOf(keys);
    }

    private static Result<Witness> bound(int count, int lower, int upper,
                                         String association, String end) {
        if (count < lower || (upper != -1 && count > upper)) {
            return failure("VALIDREP_MULTIPLICITY",
                    association + " " + end + " multiplicity " + count
                            + " outside [" + lower + "," + (upper == -1 ? "*" : upper) + "]");
        }
        return Result.success(new Witness(Map.of()));
    }

    private record Group(String objectId, List<String> qualifiers) {
        private Group {
            qualifiers = List.copyOf(qualifiers);
        }
    }

    private record LinkSignature(String associationKey, String sourceKey, String targetKey,
                                 List<String> qualifiers) {
        private LinkSignature {
            qualifiers = List.copyOf(qualifiers);
        }
    }

    private static <T> void increment(Map<T, Integer> counts, T key) {
        counts.merge(key, 1, Integer::sum);
    }

    private static UmlAssociation resolveAssociation(SchemaModel sm, String nameOrRole) {
        UmlAssociation a = sm.associationByName(nameOrRole);
        return a != null ? a : sm.associationByRole(nameOrRole);
    }

    private static Collection<UmlAttribute> applicableAttributes(SchemaModel sm, String classKey) {
        List<UmlAttribute> attributes = new ArrayList<>();
        for (UmlAttribute attr : sm.attributes()) {
            if (sm.conforms(classKey, attr.ownerClassKey())) {
                attributes.add(attr);
            }
        }
        return attributes;
    }

    private static UmlAttribute nearestAttribute(SchemaModel sm, String classKey, String name) {
        UmlAttribute direct = sm.attribute(classKey, name);
        if (direct != null) {
            return direct;
        }
        if (sm.clazz(classKey) != null) {
            for (String parent : sm.clazz(classKey).directSuperclassKeys()) {
                UmlAttribute inherited = nearestAttribute(sm, parent, name);
                if (inherited != null) {
                    return inherited;
                }
            }
        }
        return null;
    }

    private static List<String> qualifierPayloads(UmlAssociation association,
                                                   Snapshot.LinkDef link) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < link.qualifiers.size(); i++) {
            Object raw = link.qualifiers.get(i).value();
            if (!(raw instanceof OclValue value)) {
                throw new GraphValueCodec.CodecException("G_CODEC_CARRIER",
                        "qualifier value is not an OCL scalar");
            }
            result.add(qualifierPayload(
                    association.qualifiers().get(i).declaredType(), value));
        }
        return List.copyOf(result);
    }

    private static String qualifierPayload(org.uet.dse.ocl2cypher.runtime.OclType declared,
                                           OclValue value) {
        GraphValueCodec.EncodedValue encoded = GraphValueCodec.encode(declared, value);
        if (!GraphValueCodec.DEFINED.equals(encoded.state())) {
            throw new GraphValueCodec.CodecException("G_CODEC_QUALIFIER_BOTTOM",
                    "qualifiers must be defined scalar values");
        }
        return encoded.payload();
    }

    private static Result<Witness> failure(String code, String message) {
        return Result.failure(Stage.F_G, code, message == null ? code : message);
    }
}
