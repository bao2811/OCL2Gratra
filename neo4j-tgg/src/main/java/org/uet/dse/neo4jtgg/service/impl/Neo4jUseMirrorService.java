package org.uet.dse.neo4jtgg.service.impl;

import org.tzi.use.api.UseApiException;
import org.tzi.use.api.UseModelApi;
import org.tzi.use.api.UseSystemApi;
import org.tzi.use.main.Session;
import org.tzi.use.uml.mm.MAssociation;
import org.tzi.use.uml.mm.MAssociationClass;
import org.tzi.use.uml.mm.MAssociationEnd;
import org.tzi.use.uml.mm.MAttribute;
import org.tzi.use.uml.mm.MClass;
import org.tzi.use.uml.mm.MInvalidModelException;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.sys.MSystem;
import org.uet.dse.neo4j.model.FullObjectSnapshot;
import org.uet.dse.neo4j.model.LinkState;
import org.uet.dse.neo4j.model.ObjectState;
import org.uet.dse.neo4j.sync.helper.UmlTypeTranslator;
import org.uet.dse.neo4jtgg.model.TggWorkspaceContext;
import org.uet.dse.neo4jtgg.model.TggWorkspaceDefinition;
import org.uet.dse.neo4jtgg.model.WorkspaceSide;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Neo4jUseMirrorService {
    private final Neo4jWorkspaceRuntimeService runtimeService = Neo4jWorkspaceRuntimeService.getInstance();

    public void refreshMirror(TggWorkspaceContext context) throws UseApiException {
        TggWorkspaceDefinition definition = context.getWorkspaceDefinition();
        if (definition == null) {
            throw new IllegalStateException("Workspace metadata is not loaded yet.");
        }

        MModel mirrorModel = buildMirrorModel(definition);
        MSystem mirrorSystem = new MSystem(mirrorModel);
        UseSystemApi api = UseSystemApi.create(mirrorSystem, true);

        Map<WorkspaceSide, FullObjectSnapshot> snapshots = new LinkedHashMap<>();
        snapshots.put(WorkspaceSide.SOURCE, runtimeService.loadSnapshot(context, WorkspaceSide.SOURCE));
        snapshots.put(WorkspaceSide.CORRESPONDENCE, runtimeService.loadSnapshot(context, WorkspaceSide.CORRESPONDENCE));
        snapshots.put(WorkspaceSide.TARGET, runtimeService.loadSnapshot(context, WorkspaceSide.TARGET));

        // Diagnostic: log snapshot sizes per side
        for (Map.Entry<WorkspaceSide, FullObjectSnapshot> entry : snapshots.entrySet()) {
            FullObjectSnapshot snap = entry.getValue();
            context.appendLog(entry.getKey(),
                    "Mirror snapshot [" + entry.getKey().getDisplayName() + "]: "
                    + snap.objects.size() + " objects, " + snap.links.size() + " links.");
            if (!snap.objects.isEmpty()) {
                snap.objects.values().stream().limit(5).forEach(obj ->
                        context.appendLog(entry.getKey(),
                                "  -> " + obj.name + " : " + obj.className));
            }
        }

        // Diagnostic: log mirror model classes and associations
        context.appendLog(WorkspaceSide.SOURCE,
                "Mirror model classes: " + mirrorModel.classes().stream()
                        .map(c -> c.name()).collect(java.util.stream.Collectors.joining(", ")));
        context.appendLog(WorkspaceSide.SOURCE,
                "Mirror model associations: " + mirrorModel.associations().stream()
                        .map(a -> a.name()).collect(java.util.stream.Collectors.joining(", ")));

        createMirrorObjects(api, mirrorModel, snapshots.values());
        createMirrorLinks(api, mirrorModel, snapshots.values());
        setMirrorAttributes(api, mirrorModel, snapshots.values());

        // Diagnostic: log how many objects exist in the final mirror system
        context.appendLog(WorkspaceSide.SOURCE,
                "Mirror system objects after refresh: " + mirrorSystem.state().allObjects().size());

        Session session = context.getSession();
        session.setSystem(mirrorSystem);
        context.appendLog(WorkspaceSide.SOURCE, "Refreshed USE mirror from Neo4j snapshot.");
    }

    private MModel buildMirrorModel(TggWorkspaceDefinition definition) {
        ModelFactory modelFactory = new ModelFactory();
        MModel mirrorModel = modelFactory.createModel(definition.getTransformationName() + "_Mirror");
        UseModelApi api = new UseModelApi(mirrorModel);

        List<MModel> models = List.of(
                definition.getSourceModel(),
                definition.getCorrespondenceModel(),
                definition.getTargetModel()
        );

        try {
            for (MModel model : models) {
                copyClasses(api, model);
            }
            for (MModel model : models) {
                copyGeneralizations(api, model);
            }
            for (MModel model : models) {
                copyAssociations(api, mirrorModel, model);
            }
        } catch (UseApiException | MInvalidModelException exception) {
            throw new IllegalStateException("Failed to build USE mirror model: " + exception.getMessage(), exception);
        }
        return mirrorModel;
    }

    private void copyClasses(UseModelApi api, MModel model) throws UseApiException {
        for (MClass cls : model.classes()) {
            if (api.getClass(cls.name()) == null) {
                api.createClass(cls.name(), cls.isAbstract());
            }
            for (MAttribute attribute : cls.attributes()) {
                if (api.getClass(cls.name()).attribute(attribute.name(), false) == null) {
                    api.createAttribute(cls.name(), attribute.name(), attribute.type().toString());
                }
            }
        }
    }

    private void copyGeneralizations(UseModelApi api, MModel model) throws UseApiException {
        for (MClass cls : model.classes()) {
            for (MClass parent : cls.parents()) {
                if (api.getClass(cls.name()) != null
                        && api.getClass(parent.name()) != null
                        && !api.getClass(cls.name()).parents().contains(api.getClass(parent.name()))) {
                    api.createGeneralization(cls.name(), parent.name());
                }
            }
        }
    }

    private void copyAssociations(UseModelApi api, MModel mirrorModel, MModel sourceModel) throws UseApiException, MInvalidModelException {
        for (MAssociation association : sourceModel.associations()) {
            if (mirrorModel.getAssociation(association.name()) != null) {
                continue;
            }

            if (association instanceof MAssociationClass) {
                MAssociationEnd left = association.associationEnds().get(0);
                MAssociationEnd right = association.associationEnds().get(1);
                api.createAssociationClass(
                        association.name(),
                        false,
                        left.cls().name(),
                        left.name(),
                        left.multiplicity().toString(),
                        left.aggregationKind(),
                        right.cls().name(),
                        right.name(),
                        right.multiplicity().toString(),
                        right.aggregationKind()
                );
                continue;
            }

            int numEnds = association.associationEnds().size();
            String[] classNames = new String[numEnds];
            String[] roleNames = new String[numEnds];
            String[] multiplicities = new String[numEnds];
            int[] aggregationKinds = new int[numEnds];
            boolean[] ordered = new boolean[numEnds];
            for (int i = 0; i < numEnds; i++) {
                MAssociationEnd end = association.associationEnds().get(i);
                classNames[i] = end.cls().name();
                roleNames[i] = end.name();
                multiplicities[i] = end.multiplicity().toString();
                aggregationKinds[i] = end.aggregationKind();
                ordered[i] = end.isOrdered();
            }

            api.createAssociation(
                    association.name(),
                    classNames,
                    roleNames,
                    multiplicities,
                    aggregationKinds,
                    ordered,
                    new String[0][][]
            );
        }
    }

    private void createMirrorObjects(UseSystemApi api, MModel mirrorModel, Collection<FullObjectSnapshot> snapshots) throws UseApiException {
        Set<String> createdObjects = new LinkedHashSet<>();
        Set<String> deferredLinkObjects = new LinkedHashSet<>();

        for (FullObjectSnapshot snapshot : snapshots) {
            for (ObjectState objectState : snapshot.objects.values()) {
                if (!createdObjects.add(objectState.name)) {
                    continue;
                }
                if (mirrorModel.getAssociationClass(objectState.className) != null) {
                    deferredLinkObjects.add(objectState.name);
                    continue;
                }
                if (mirrorModel.getClass(objectState.className) != null) {
                    api.createObject(objectState.className, objectState.name);
                }
            }
        }
    }

    private void createMirrorLinks(UseSystemApi api, MModel mirrorModel, Collection<FullObjectSnapshot> snapshots) throws UseApiException {
        Set<String> createdLinks = new LinkedHashSet<>();
        List<LinkState> linkObjects = new ArrayList<>();

        for (FullObjectSnapshot snapshot : snapshots) {
            for (LinkState linkState : snapshot.links.values()) {
                if (linkState.linkObjectName != null) {
                    linkObjects.add(linkState);
                    continue;
                }
                if (!createdLinks.add(linkState.getIdentity())) {
                    continue;
                }
                if (mirrorModel.getAssociation(linkState.assocName) != null) {
                    api.createLink(linkState.assocName, linkState.participants.toArray(new String[0]));
                }
            }
        }

        for (LinkState linkState : linkObjects) {
            if (!createdLinks.add(linkState.getIdentity())) {
                continue;
            }
            if (mirrorModel.getAssociationClass(linkState.assocName) != null) {
                api.createLinkObject(linkState.assocName, linkState.linkObjectName,
                        linkState.participants.toArray(new String[0]));
            }
        }
    }

    private void setMirrorAttributes(UseSystemApi api, MModel mirrorModel, Collection<FullObjectSnapshot> snapshots) throws UseApiException {
        Set<String> touchedObjects = new LinkedHashSet<>();
        for (FullObjectSnapshot snapshot : snapshots) {
            for (ObjectState objectState : snapshot.objects.values()) {
                if (!touchedObjects.add(objectState.name)) {
                    continue;
                }
                MClass cls = mirrorModel.getClass(objectState.className);
                if (cls == null) {
                    continue;
                }
                for (MAttribute attribute : cls.allAttributes()) {
                    Object value = objectState.objectReferences.containsKey(attribute.name())
                            ? objectState.objectReferences.get(attribute.name())
                            : objectState.primitiveValues.get(attribute.name());
                    if (value == null) {
                        continue;
                    }
                    api.setAttributeValue(objectState.name, attribute.name(),
                            UmlTypeTranslator.toTypedOclLiteral(value, attribute.type()));
                }
            }
        }
    }
}
