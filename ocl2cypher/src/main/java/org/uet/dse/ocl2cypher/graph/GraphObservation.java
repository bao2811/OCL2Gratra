package org.uet.dse.ocl2cypher.graph;

import java.util.*;
import org.uet.dse.ocl2cypher.runtime.OclType;
import org.uet.dse.ocl2cypher.runtime.OclValue;
import org.uet.dse.ocl2cypher.source.model.SchemaModel;
import org.uet.dse.ocl2cypher.source.model.UmlAttribute;
import org.uet.dse.ocl2cypher.source.model.UmlClass;

/**
 * Observer reads over a {@link GraphModel} — the only way {@code Q_CYP}
 * evaluates a query. Every accessor is model-isolated (keys carry
 * {@code modelKey}) and total on a well-formed graph: a missing slot or a
 * zero/many to-one match answers the declared typed bottom, never native null
 * and never an arbitrary row.
 */
public final class GraphObservation {

    private GraphObservation() {
    }

    /** ATTRIBUTE observer: object → slot → value node, typing-guarded by attributeKey. */
    public static OclValue attribute(GraphModel g, SchemaModel sm,
                                     String objectStableId, String attributeName) {
        String classKey = directType(g, objectStableId);
        UmlAttribute attr = nearestAttribute(sm, classKey, attributeName);
        if (attr == null) {
            throw new IllegalStateException("no attribute " + attributeName + " on " + classKey);
        }
        return attribute(g, sm, objectStableId, attr.ownerClassKey(), attributeName);
    }

    /**
     * ATTRIBUTE observer resolved by declaration identity, not by an inherited
     * surface name.  This is the production path used by Q: E/N have already
     * selected one {@code ownerClassKey::attributeName} declaration.
     */
    public static OclValue attribute(GraphModel g, SchemaModel sm,
                                     String objectStableId, String ownerClassKey,
                                     String attributeName) {
        String classKey = directType(g, objectStableId);
        UmlAttribute attr = sm.attributeByKey(ownerClassKey + "::" + attributeName);
        if (attr == null || !sm.conforms(classKey, ownerClassKey)) {
            throw new IllegalStateException("no resolved attribute " + ownerClassKey
                    + "::" + attributeName + " on " + classKey);
        }
        var catalogue = catalogue(sm);
        var attributeBinding = catalogue.attribute(attr.key());
        String objKey = GraphKey.object(g.modelKey(), objectStableId);
        List<GraphModel.Relationship> owns = g.outgoing(objKey,
                attributeBinding.slotOwnership().physicalType());
        GraphModel.Relationship hit = null;
        for (var r : owns) {
            GraphModel.Node slot = g.node(r.targetKey());
            if (attr.key().equals(slot.properties().get("attributeKey"))) {
                if (hit != null) {
                    // multiplicity corruption: duplicate storage rows are not
                    // arbitrarily selected — bottom per R-E-ATTRIBUTE
                    return new OclValue.BottomValue(attr.declaredType());
                }
                hit = r;
            }
        }
        if (hit == null) {
            return new OclValue.BottomValue(attr.declaredType());
        }
        GraphModel.Node slot = g.node(hit.targetKey());
        return GraphValueCodec.decode(attr.declaredType(), slot.properties());
    }

    /** DIRECT_TYPE observer: the unique ObjectInstanceOf target's classKey. */
    public static String directType(GraphModel g, String objectStableId) {
        String objKey = GraphKey.object(g.modelKey(), objectStableId);
        String typingType = MetamodelMapping.canonical().relationship(
                MetamodelMapping.RelationshipKind.OBJECT_TYPING).physicalType();
        String found = null;
        for (var r : g.outgoing(objKey, typingType)) {
            String current = g.node(r.targetKey()).properties().get("classKey");
            if (found != null) {
                throw new IllegalStateException("G_SOURCE_WF: object has multiple direct types "
                        + objectStableId);
            }
            found = current;
        }
        if (found != null) {
            return found;
        }
        throw new IllegalStateException("G_SOURCE_WF: object without direct type " + objectStableId);
    }

    /** CONFORMANCE observer via the Extends* closure of the direct type. */
    public static boolean conformsTo(GraphModel g, SchemaModel sm,
                                     String objectStableId, String classKey) {
        return sm.conforms(directType(g, objectStableId), classKey);
    }

    /** ALL_INSTANCES observer: nodes whose direct type conforms to C (closure). */
    public static List<String> objectsOfClass(GraphModel g, SchemaModel sm, String classKey) {
        List<String> out = new ArrayList<>();
        MetamodelMapping mapping = catalogue(sm).mapping();
        String objectRole = mapping.node(MetamodelMapping.NodeKind.OBJECT).observationRole();
        String associationClassRole = mapping.node(
                MetamodelMapping.NodeKind.ASSOCIATION_CLASS_OBJECT).observationRole();
        for (var n : g.nodes()) {
            if (!objectRole.equals(n.observationRole())
                    && !associationClassRole.equals(n.observationRole())) {
                continue;
            }
            String direct = directType(g, n.properties().get("objectKey"));
            if (sm.conforms(direct, classKey)) {
                out.add(n.properties().get("objectKey"));
            }
        }
        Collections.sort(out);
        return out;
    }

    /** NAVIGATION observer (OUTGOING by role): target objectKeys of matching links. */
    public static List<String> linkTargets(GraphModel g, SchemaModel sm,
                                           String sourceStableId, String roleName) {
        return linkTargets(g, sm, sourceStableId, roleName, false, List.of());
    }

    public static List<String> linkTargets(GraphModel g, SchemaModel sm,
                                           String sourceStableId, String roleName,
                                           boolean reverse,
                                           List<org.uet.dse.ocl2cypher.runtime.OclValue> qualifiers) {
        var navigation = sm.navigation(directType(g, sourceStableId), roleName);
        if (navigation == null || navigation.reverse() != reverse) {
            throw new IllegalStateException("no role " + roleName);
        }
        var assoc = navigation.association();
        var associationBinding = catalogue(sm).association(assoc.key());
        // The low-level observer also supports an explicit wildcard read when
        // no qualifier values are supplied.  OCL-facing frontend admission
        // still requires the declared arity for qualified navigation; this
        // wildcard is useful for graph inspection and reverse-link checks.
        if (!qualifiers.isEmpty() && qualifiers.size() != assoc.qualifierNames().size()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        String srcKey = GraphKey.object(g.modelKey(), sourceStableId);
        UmlClass associationClass = sm.clazz(assoc.key());
        if (associationClass != null && associationClass.isAssociationClass()) {
            var acBinding = associationBinding.associationClass().orElseThrow(() ->
                    new IllegalStateException("missing association-class catalogue binding "));
            String receiverEdge = navigation.reverse()
                    ? acBinding.targetParticipant().physicalType()
                    : acBinding.sourceParticipant().physicalType();
            String resultEdge = navigation.reverse()
                    ? acBinding.sourceParticipant().physicalType()
                    : acBinding.targetParticipant().physicalType();
            for (var participant : g.incoming(srcKey, receiverEdge)) {
                if (!qualifiers.isEmpty()
                        && !qualifiersMatch(participant, associationBinding, qualifiers)) {
                    continue;
                }
                for (var result : g.outgoing(participant.sourceKey(), resultEdge)) {
                    out.add(g.node(result.targetKey()).properties().get("objectKey"));
                }
            }
            Collections.sort(out);
            return navigation.toMany() && navigation.unique()
                    ? new ArrayList<>(new LinkedHashSet<>(out)) : out;
        }
        var rels = reverse
                ? g.incoming(srcKey, associationBinding.link().physicalType())
                : g.outgoing(srcKey, associationBinding.link().physicalType());
        for (var r : rels) {
            if (!assoc.key().equals(r.properties().get("associationKey"))) {
                continue;
            }
            if (!qualifiers.isEmpty()
                    && !qualifiersMatch(r, associationBinding, qualifiers)) {
                continue;
            }
            GraphModel.Node t = g.node(reverse ? r.sourceKey() : r.targetKey());
            out.add(t.properties().get("objectKey"));
        }
        Collections.sort(out);
        if (navigation.toMany() && navigation.unique() && !out.isEmpty()) {
            return new ArrayList<>(new LinkedHashSet<>(out));
        }
        return out;
    }

    /** Association-class observer: participant object to link-object identities. */
    public static List<String> associationClassObjects(GraphModel g, SchemaModel sm,
                                                       String participantStableId,
                                                       String associationClassName,
                                                       List<OclValue> qualifiers) {
        var navigation = sm.associationClassNavigation(
                directType(g, participantStableId), associationClassName);
        if (navigation == null) {
            throw new IllegalStateException("no association-class navigation "
                    + associationClassName);
        }
        if (!qualifiers.isEmpty()
                && qualifiers.size() != navigation.association().qualifierNames().size()) {
            return List.of();
        }
        var associationBinding = catalogue(sm).association(navigation.association().key());
        var acBinding = associationBinding.associationClass().orElseThrow(() ->
                new IllegalStateException("missing association-class catalogue binding"));
        String participantType = navigation.receiverIsTarget()
                ? acBinding.targetParticipant().physicalType()
                : acBinding.sourceParticipant().physicalType();
        List<String> out = new ArrayList<>();
        for (var edge : g.incoming(
                GraphKey.object(g.modelKey(), participantStableId), participantType)) {
            if (!qualifiers.isEmpty()
                    && !qualifiersMatch(edge, associationBinding, qualifiers)) {
                continue;
            }
            GraphModel.Node occurrence = g.node(edge.sourceKey());
            if (acBinding.objectNode().observationRole().equals(occurrence.observationRole())) {
                out.add(occurrence.properties().get("objectKey"));
            }
        }
        Collections.sort(out);
        return navigation.toMany() && navigation.unique()
                ? new ArrayList<>(new LinkedHashSet<>(out)) : out;
    }

    private static boolean qualifiersMatch(GraphModel.Relationship r,
                                           MetamodelTranslator.AssociationBinding association,
                                           List<org.uet.dse.ocl2cypher.runtime.OclValue> values) {
        for (int i = 0; i < values.size(); i++) {
            var binding = association.qualifiers().get(i);
            String stored = r.properties().get(binding.storageProperty());
            if (stored == null || !stored.equals(
                    qualifierPayload(binding.declaredType(), values.get(i)))) {
                return false;
            }
        }
        return true;
    }

    private static MetamodelTranslator.Catalogue catalogue(SchemaModel schema) {
        var result = MetamodelTranslator.translate(schema);
        if (result.isFailure()) {
            throw new IllegalStateException(result.primaryDiagnostic().toString());
        }
        return result.value();
    }

    private static String qualifierPayload(
            org.uet.dse.ocl2cypher.runtime.OclType declared,
            org.uet.dse.ocl2cypher.runtime.OclValue value) {
        GraphValueCodec.EncodedValue encoded = GraphValueCodec.encode(declared, value);
        if (!GraphValueCodec.DEFINED.equals(encoded.state())) {
            throw new GraphValueCodec.CodecException("G_CODEC_QUALIFIER_BOTTOM",
                    "qualifiers must be defined scalar values");
        }
        return encoded.payload();
    }

    private static UmlAttribute nearestAttribute(SchemaModel sm, String classKey, String name) {
        UmlAttribute attr = sm.attribute(classKey, name);
        if (attr != null) {
            return attr;
        }
        var klass = sm.clazz(classKey);
        if (klass != null) {
            for (String sup : klass.directSuperclassKeys()) {
                attr = nearestAttribute(sm, sup, name);
                if (attr != null) {
                    return attr;
                }
            }
        }
        return null;
    }
}
