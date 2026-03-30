package org.uet.dse.neo4j.sync.object;

import org.tzi.use.uml.mm.MAssociation;
import org.tzi.use.uml.mm.MAssociationEnd;
import org.tzi.use.uml.mm.MAttribute;
import org.tzi.use.uml.ocl.value.Value;
import org.tzi.use.uml.sys.*;
import org.uet.dse.neo4j.helper.ValueMapper;
import org.uet.dse.neo4j.model.FullObjectSnapshot;
import org.uet.dse.neo4j.model.LinkState;
import org.uet.dse.neo4j.model.ObjectState;
import org.uet.dse.neo4j.sync.helper.UmlTypeTranslator;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class USEObjectSnapshotUtils {
    private MSystem system;

    public USEObjectSnapshotUtils(MSystem system) {
        this.system = system;
    }
    public FullObjectSnapshot getJavaObjectSnapshot() {
        FullObjectSnapshot snapshot = new FullObjectSnapshot();
        MSystemState state = system.state();

        for (MObject obj : state.allObjects()) {
            snapshot.objects.put(obj.name(), buildObjectState(obj, state));
        }

        for (MLink link : state.allLinks()) {
            LinkState ls = buildLinkState(link);
            snapshot.links.put(ls.getIdentity(), ls);
        }

        return snapshot;
    }

    private ObjectState buildObjectState(MObject obj, MSystemState state) {
        ObjectState os = new ObjectState();
        os.name = obj.name();
        os.className = obj.cls().name();

        for (MAttribute attr : obj.cls().allAttributes()) {
            Value useVal = obj.state(state).attributeValue(attr);
            Object mapped = ValueMapper.mapUseValue(useVal);

            Object normalizedValue = normalizeJavaValue(mapped);

            if (isObjectReferenceType(attr.type())) {

                if (normalizedValue instanceof List) {
                    os.objectReferences.put(attr.name(), (List<Object>) normalizedValue);
                } else if (normalizedValue != null) {
                    os.objectReferences.put(attr.name(), Collections.singletonList(normalizedValue.toString()));
                }
            } else {
                os.primitiveValues.put(attr.name(), normalizedValue);
            }
        }
        return os;
    }

    private Object normalizeJavaValue(Object val) {
        if (val instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) val;
            if (map.containsKey("items")) {
                List<?> items = (List<?>) map.get("items");
                return items.stream().map(this::normalizeJavaValue).collect(java.util.stream.Collectors.toList());
            }
        }
        return val;
    }

    private boolean isObjectReferenceType(org.tzi.use.uml.ocl.type.Type type) {
        if (type.isKindOfClass(org.tzi.use.uml.ocl.type.Type.VoidHandling.EXCLUDE_VOID)) return true;

        if (type.isKindOfCollection(org.tzi.use.uml.ocl.type.Type.VoidHandling.EXCLUDE_VOID)) {
            return isObjectReferenceType(((org.tzi.use.uml.ocl.type.CollectionType) type).elemType());
        }

        return false;
    }

    private LinkState buildLinkState(MLink link) {
        LinkState ls = new LinkState();
        ls.assocName = link.association().name();
        link.linkedObjects().forEach(o -> ls.participants.add(o.name()));

        ls.edgeLabel = resolveLinkEdgeLabel(link);
        if (link instanceof MLinkObject) {
            ls.linkObjectName = ((MLinkObject) link).name();
        } else if (link.linkedObjects().size() > 2) {
            ls.isTernary = true;
        }
        return ls;
    }

    private String resolveLinkEdgeLabel(MLink link) {
        if (link instanceof MLinkObject || link.linkedObjects().size() > 2) {
            return "LinkAssociateWith";
        }
        return resolveBinaryEdgeLabel(link.association());
    }

    private String resolveBinaryEdgeLabel(MAssociation assoc) {
        int maxKind = assoc.associationEnds().stream()
                .mapToInt(MAssociationEnd::aggregationKind)
                .max()
                .orElse(0);

        if (maxKind == 2) return "LinkComposeOf";
        if (maxKind == 1) return "LinkAggregates";
        return "LinkAssociateWith";
    }
}
