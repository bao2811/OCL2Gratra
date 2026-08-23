package org.uet.dse.neo4jtgg.service.impl;

import org.neo4j.driver.Session;
import org.tzi.use.uml.mm.MAssociation;
import org.tzi.use.uml.mm.MAssociationClass;
import org.tzi.use.uml.mm.MAssociationEnd;
import org.tzi.use.uml.mm.MAttribute;
import org.tzi.use.uml.mm.MClass;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.ocl.type.CollectionType;
import org.tzi.use.uml.ocl.type.Type;
import org.uet.dse.neo4j.manager.Neo4jDriverManager;
import org.uet.dse.neo4j.model.FullObjectSnapshot;
import org.uet.dse.neo4j.model.LinkState;
import org.uet.dse.neo4j.model.ObjectState;
import org.uet.dse.neo4j.repo.Neo4jObjectRepository;
import org.uet.dse.neo4j.sync.helper.UmlTypeTranslator;
import org.uet.dse.neo4j.sync.object.Neo4jObjectSnapshotUtils;
import org.uet.dse.neo4j.sync.object.ObjectPushService;
import org.uet.dse.neo4jtgg.engine.CorrRuntimeTraceHelper;
import org.uet.dse.neo4jtgg.engine.ForwardTransformationResult;
import org.uet.dse.neo4jtgg.model.GuardReport;
import org.uet.dse.neo4jtgg.model.ImportBatch;
import org.uet.dse.neo4jtgg.model.ImportLinkSpec;
import org.uet.dse.neo4jtgg.model.ImportObjectSpec;
import org.uet.dse.neo4jtgg.model.TggRuleInfo;
import org.uet.dse.neo4jtgg.model.TggWorkspaceContext;
import org.uet.dse.neo4jtgg.model.TggWorkspaceDefinition;
import org.uet.dse.neo4jtgg.model.WorkspaceSide;
import org.uet.dse.neo4jtgg.model.WorkspaceMutationBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class Neo4jWorkspaceRuntimeService {
    private static final Neo4jWorkspaceRuntimeService INSTANCE = new Neo4jWorkspaceRuntimeService();

    public static Neo4jWorkspaceRuntimeService getInstance() {
        return INSTANCE;
    }

    private final Neo4jObjectSnapshotUtils snapshotUtils = new Neo4jObjectSnapshotUtils();
    private final Neo4jObjectRepository objectRepository = new Neo4jObjectRepository();

    private Neo4jWorkspaceRuntimeService() {
    }

    public FullObjectSnapshot loadSnapshot(TggWorkspaceContext context, WorkspaceSide side) {
        MModel model = requireSideModel(context, side);
        return snapshotUtils.getNeo4jObjectSnapshot(model.name());
    }

    public String renderSnapshot(TggWorkspaceContext context, WorkspaceSide side) {
        TggWorkspaceDefinition definition = context.getWorkspaceDefinition();
        if (definition == null) {
            return "No active Neo4j TGG workspace loaded.";
        }

        MModel model = requireSideModel(context, side);
        FullObjectSnapshot snapshot = loadSnapshot(context, side);

        StringBuilder sb = new StringBuilder();
        sb.append("Transformation: ").append(definition.getTransformationName()).append('\n');
        sb.append("Runtime: Neo4j-first workspace\n");
        sb.append("Model file: ");
        if (side == WorkspaceSide.SOURCE) {
            sb.append(context.getSourceFile().getAbsolutePath());
        } else if (side == WorkspaceSide.CORRESPONDENCE) {
            sb.append(context.getTggFile().getAbsolutePath());
        } else if (side == WorkspaceSide.TARGET) {
            sb.append(context.getTargetFile().getAbsolutePath());
        } else {
            sb.append("<unknown>");
        }
        sb.append('\n');
        sb.append("Neo4j model: ").append(model.name()).append('\n');
        sb.append("Metamodel: ").append(context.getMetamodelStatus(side)).append("\n\n");
        sb.append("Import flow: Run Workspace handles M2/M1; Import XMI/XML creates M0 instances linked to M1.\n\n");

        sb.append(side.getDisplayName()).append(" classes\n");
        for (String className : definition.getClassNames(side)) {
            sb.append("- ").append(className).append('\n');
        }

        sb.append("\nObjects on Neo4j (").append(snapshot.objects.size()).append(")\n");
        snapshot.objects.values().stream()
                .sorted((left, right) -> left.name.compareTo(right.name))
                .forEach(state -> sb.append("- ")
                        .append(state.name)
                        .append(" : ")
                        .append(state.className)
                        .append(" attrs=")
                        .append(formatAttributes(state))
                        .append('\n'));
        if (snapshot.objects.isEmpty()) {
            sb.append("- No M0 instances on Neo4j yet. Import XMI/XML to create objects for this metamodel.\n");
        }

        sb.append("\nLinks on Neo4j (").append(snapshot.links.size()).append(")\n");
        snapshot.links.values().stream()
                .sorted((left, right) -> left.getIdentity().compareTo(right.getIdentity()))
                .forEach(link -> sb.append("- ")
                        .append(link.assocName)
                        .append(" ")
                        .append(link.participants)
                        .append(link.linkObjectName != null ? " [linkObject=" + link.linkObjectName + "]" : "")
                        .append('\n'));
        if (snapshot.links.isEmpty()) {
            sb.append("- No M0 links on Neo4j yet.\n");
        }

        sb.append("\nRule fragments\n");
        for (TggRuleInfo rule : definition.getRules()) {
            String fragment = rule.getSideConstraints(side);
            if (!fragment.isBlank()) {
                sb.append("- ").append(rule.getName()).append('\n');
            }
        }

        sb.append("\nNeo4j status\n");
        sb.append("Connected: ")
                .append(Neo4jDriverManager.getInstance() != null && Neo4jDriverManager.getInstance().isConnected())
                .append('\n');
        sb.append("\nRemote change feed\n").append(context.getRemoteChangeSummary()).append('\n');
        return sb.toString().trim();
    }

    public GuardReport validateImport(TggWorkspaceContext context, ImportBatch batch) {
        GuardReport report = new GuardReport();
        MModel model = requireSideModel(context, batch.getSide());
        FullObjectSnapshot snapshot = loadSnapshot(context, batch.getSide());
        Set<String> seenNames = new HashSet<>();

        for (ImportObjectSpec spec : batch.getObjects()) {
            if (!seenNames.add(spec.getObjectName())) {
                report.addError("Duplicate object inside import batch: " + spec.getObjectName());
            }

            MClass modelClass = model.getClass(spec.getClassName());
            if (modelClass == null) {
                report.addError("Class `" + spec.getClassName() + "` is not defined in model `" + model.name() + "`.");
                continue;
            }

            ObjectState existing = snapshot.objects.get(spec.getObjectName());
            if (existing != null && !existing.className.equals(spec.getClassName())) {
                report.addError("Object `" + spec.getObjectName() + "` already exists on Neo4j with class `" +
                        existing.className + "`.");
            } else if (existing != null) {
                report.addWarning("Object `" + spec.getObjectName() + "` already exists on Neo4j and will be updated.");
            }

            for (String attributeName : spec.getAttributes().keySet()) {
                if (modelClass.attribute(attributeName, true) == null) {
                    report.addError("Attribute `" + attributeName + "` is not defined on class `" + spec.getClassName() + "`.");
                }
            }
        }

        for (ImportLinkSpec link : batch.getLinks()) {
            MAssociation association = model.getAssociation(link.getAssociationName());
            if (association == null) {
                report.addError("Association `" + link.getAssociationName() + "` is not defined in model `" + model.name() + "`.");
                continue;
            }

            for (String endpoint : link.getEndpointNames()) {
                boolean presentInBatch = batch.getObjects().stream().anyMatch(obj -> obj.getObjectName().equals(endpoint));
                if (!presentInBatch && !snapshot.objects.containsKey(endpoint)) {
                    report.addError("Link `" + link.getAssociationName() + "` references missing Neo4j object `" + endpoint + "`.");
                }
            }

            String identity = buildLinkIdentity(link);
            if (snapshot.links.containsKey(identity)) {
                report.addWarning("Link `" + identity + "` already exists on Neo4j and will be reused.");
            }
        }

        return report;
    }

    public GuardReport validateRemotePull(TggWorkspaceContext context) {
        GuardReport report = new GuardReport();
        report.addWarning("Neo4j is the source of truth. Refresh USE Mirror will rebuild the USE session from the current Neo4j snapshot.");
        return report;
    }

    public void applyImport(TggWorkspaceContext context, ImportBatch batch) {
        MModel model = requireSideModel(context, batch.getSide());
        try (Session session = Neo4jDriverManager.getInstance().openSession()) {
            session.executeWrite(tx -> {
                for (ImportObjectSpec spec : batch.getObjects()) {
                    MClass cls = model.getClass(spec.getClassName());
                    objectRepository.upsertObjectNode(tx, model.name(), spec.getObjectName(), spec.getClassName());

                    for (Map.Entry<String, String> attr : spec.getAttributes().entrySet()) {
                        MAttribute attribute = cls.attribute(attr.getKey(), true);
                        Map<String, Object> metadata = buildAttributeMetadata(attribute);
                        Object mappedValue = parseImportValue(attribute.type(), attr.getValue());
                        objectRepository.setAttributeValueNode(tx, model.name(), spec.getObjectName(),
                                cls.name(), attribute.name(), mappedValue, metadata);
                    }
                }

                for (ImportLinkSpec linkSpec : batch.getLinks()) {
                    upsertLink(tx, model, linkSpec);
                }
                return null;
            });
        }

        ObjectPushService.updateObjectVersionOnServer();
    }

    public void applyForwardTransformationResult(TggWorkspaceContext context, ForwardTransformationResult result) {
        applyImport(context, result.targetBatch());
        if (!result.hasCorrespondences()) {
            return;
        }

        try (Session session = Neo4jDriverManager.getInstance().openSession()) {
            session.executeWrite(tx -> {
                for (ForwardTransformationResult.CorrCreation creation : result.corrCreations()) {
                    upsertCorrCreation(tx, creation);
                }
                return null;
            });
        }

        ObjectPushService.updateObjectVersionOnServer();
    }

    public GuardReport validateMutation(TggWorkspaceContext context, WorkspaceMutationBatch batch) {
        GuardReport report = new GuardReport();
        if (batch == null || batch.isEmpty()) {
            report.addWarning("Mutation batch is empty.");
            return report;
        }

        MModel model = requireSideModel(context, batch.getReceiverSide());
        FullObjectSnapshot snapshot = loadSnapshot(context, batch.getReceiverSide());

        for (ImportObjectSpec spec : batch.getUpsertObjects()) {
            MClass modelClass = model.getClass(spec.getClassName());
            if (modelClass == null) {
                report.addError("Class `" + spec.getClassName() + "` is not defined in model `" + model.name() + "`.");
                continue;
            }
            for (String attributeName : spec.getAttributes().keySet()) {
                if (modelClass.attribute(attributeName, true) == null) {
                    report.addError("Attribute `" + attributeName + "` is not defined on class `" + spec.getClassName() + "`.");
                }
            }
        }

        for (ImportLinkSpec link : batch.getUpsertLinks()) {
            MAssociation association = model.getAssociation(link.getAssociationName());
            if (association == null) {
                report.addError("Association `" + link.getAssociationName() + "` is not defined in model `" + model.name() + "`.");
                continue;
            }
            for (String endpoint : link.getEndpointNames()) {
                boolean presentInBatch = batch.getUpsertObjects().stream().anyMatch(obj -> obj.getObjectName().equals(endpoint));
                if (!presentInBatch && !snapshot.objects.containsKey(endpoint)) {
                    report.addError("Link `" + link.getAssociationName() + "` references missing Neo4j object `" + endpoint + "`.");
                }
            }
        }

        return report;
    }

    public void applyMutation(TggWorkspaceContext context, WorkspaceMutationBatch batch) {
        if (batch == null || batch.isEmpty()) {
            return;
        }

        MModel model = requireSideModel(context, batch.getReceiverSide());
        try (Session session = Neo4jDriverManager.getInstance().openSession()) {
            session.executeWrite(tx -> {
                for (ImportLinkSpec linkSpec : batch.getDeleteLinks()) {
                    deleteLink(tx, linkSpec);
                }
                for (String objectId : batch.getDeleteObjectIds()) {
                    objectRepository.deleteObjectDeeply(tx, objectId);
                }
                for (ImportObjectSpec spec : batch.getUpsertObjects()) {
                    MClass cls = model.getClass(spec.getClassName());
                    objectRepository.upsertObjectNode(tx, model.name(), spec.getObjectName(), spec.getClassName());
                    for (Map.Entry<String, String> attr : spec.getAttributes().entrySet()) {
                        MAttribute attribute = cls.attribute(attr.getKey(), true);
                        Map<String, Object> metadata = buildAttributeMetadata(attribute);
                        Object mappedValue = parseImportValue(attribute.type(), attr.getValue());
                        objectRepository.setAttributeValueNode(tx, model.name(), spec.getObjectName(),
                                cls.name(), attribute.name(), mappedValue, metadata);
                    }
                }
                for (ImportLinkSpec linkSpec : batch.getUpsertLinks()) {
                    upsertLink(tx, model, linkSpec);
                }
                return null;
            });
        }

        ObjectPushService.updateObjectVersionOnServer();
    }

    private String formatAttributes(ObjectState state) {
        Map<String, Object> combined = new TreeMap<>();
        combined.putAll(state.primitiveValues);
        combined.putAll(state.objectReferences);
        return combined.toString();
    }

    private MModel requireSideModel(TggWorkspaceContext context, WorkspaceSide side) {
        TggWorkspaceDefinition definition = context.getWorkspaceDefinition();
        if (definition == null) {
            throw new IllegalStateException("Workspace metadata is not loaded yet.");
        }
        MModel model = definition.getModel(side);
        if (model == null) {
            throw new IllegalStateException("Neo4j object runtime is not available for side `" + side + "`.");
        }
        return model;
    }

    private String buildLinkIdentity(ImportLinkSpec link) {
        return link.getIdentity();
    }

    private Map<String, Object> buildAttributeMetadata(MAttribute attribute) {
        Type type = attribute.type();
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("type", UmlTypeTranslator.toDatabaseType(type));
        metadata.put("isCollection", type.isKindOfCollection(Type.VoidHandling.EXCLUDE_VOID));
        metadata.put("collectionType", UmlTypeTranslator.getCollectionType(type));
        metadata.put("isNestedCollection", UmlTypeTranslator.getCollectionDepth(type) > 1);
        metadata.put("isObjectReference", isObjectReferenceType(type));
        return metadata;
    }

    private Object parseImportValue(Type type, String rawValue) {
        if (rawValue == null || rawValue.isBlank() || "Undefined".equalsIgnoreCase(rawValue)) {
            return "Undefined";
        }

        if (type.isKindOfCollection(Type.VoidHandling.EXCLUDE_VOID) && type instanceof CollectionType collectionType) {
            String normalized = rawValue.trim();
            if (normalized.startsWith("[") && normalized.endsWith("]")) {
                normalized = normalized.substring(1, normalized.length() - 1);
            }
            List<Object> items = new ArrayList<>();
            if (!normalized.isBlank()) {
                for (String item : normalized.split("\\s*,\\s*")) {
                    items.add(parseImportValue(collectionType.elemType(), item));
                }
            }
            Map<String, Object> collection = new HashMap<>();
            collection.put("collectionType", UmlTypeTranslator.getCollectionType(type));
            collection.put("items", items);
            return collection;
        }

        if (type.isTypeOfInteger()) {
            return Long.parseLong(rawValue.trim());
        }
        if (type.isTypeOfReal()) {
            return Double.parseDouble(rawValue.trim());
        }
        if (type.isTypeOfBoolean()) {
            return Boolean.parseBoolean(rawValue.trim());
        }
        if (type.isTypeOfEnum()) {
            return rawValue.trim().replace(type.shortName() + "::", "");
        }
        if (type.isKindOfClass(Type.VoidHandling.EXCLUDE_VOID)) {
            return rawValue.trim();
        }
        return rawValue;
    }

    private boolean isObjectReferenceType(Type type) {
        if (type.isKindOfClass(Type.VoidHandling.EXCLUDE_VOID)) {
            return true;
        }
        if (type.isKindOfCollection(Type.VoidHandling.EXCLUDE_VOID) && type instanceof CollectionType collectionType) {
            return isObjectReferenceType(collectionType.elemType());
        }
        return false;
    }

    private void upsertCorrCreation(org.neo4j.driver.TransactionContext tx,
                                    ForwardTransformationResult.CorrCreation creation) {
        var record = creation.record();
        objectRepository.upsertObjectNode(tx, record.objectId(), record.corrClassName());

        for (Map.Entry<String, String> binding : creation.sourceBindings().entrySet()) {
            objectRepository.upsertBinaryLink(tx,
                    record.objectId(),
                    binding.getValue(),
                    CorrRuntimeTraceHelper.bindingAssociationName(record.appliedRuleName(), WorkspaceSide.SOURCE, binding.getKey()),
                    "LinkAssociateWith",
                    "corr",
                    binding.getKey());
        }
        for (Map.Entry<String, String> binding : creation.targetBindings().entrySet()) {
            objectRepository.upsertBinaryLink(tx,
                    record.objectId(),
                    binding.getValue(),
                    CorrRuntimeTraceHelper.bindingAssociationName(record.appliedRuleName(), WorkspaceSide.TARGET, binding.getKey()),
                    "LinkAssociateWith",
                    "corr",
                    binding.getKey());
        }
    }

    private void upsertLink(org.neo4j.driver.TransactionContext tx, MModel model, ImportLinkSpec linkSpec) {
        MAssociation association = model.getAssociation(linkSpec.getAssociationName());
        List<String> endpoints = linkSpec.getEndpointNames();

        if (association instanceof MAssociationClass associationClass && endpoints.size() > 1) {
            String linkObjectName = associationClass.name() + "_" + String.join("_", endpoints);
            List<Map<String, Object>> participants = new ArrayList<>();
            for (int i = 0; i < endpoints.size(); i++) {
                Map<String, Object> participant = new HashMap<>();
                participant.put("objName", endpoints.get(i));
                participant.put("role", associationClass.associationEnds().get(i).name());
                participant.put("label", "LinkAssociateWith");
                participant.put("index", i);
                participants.add(participant);
            }
            objectRepository.upsertLinkObject(
                    tx, model.name(), linkObjectName, associationClass.name(), participants);
            return;
        }

        if (endpoints.size() == 2) {
            MAssociationEnd sourceEnd = association.associationEnds().get(0);
            MAssociationEnd targetEnd = association.associationEnds().get(1);
            String label = resolveBinaryLinkLabel(sourceEnd, targetEnd);
            objectRepository.upsertBinaryLink(tx,
                    model.name(),
                    endpoints.get(0),
                    endpoints.get(1),
                    association.name(),
                    label,
                    sourceEnd.name(),
                    targetEnd.name(),
                    qualifierValuesForEnd(linkSpec, 0),
                    qualifierValuesForEnd(linkSpec, 1));
            return;
        }

        List<Map<String, Object>> participants = new ArrayList<>();
        for (int i = 0; i < endpoints.size(); i++) {
            MAssociationEnd end = association.associationEnds().get(i);
            Map<String, Object> participant = new HashMap<>();
            participant.put("objName", endpoints.get(i));
            participant.put("role", end.name());
            participant.put("index", i);
            participant.put("label", "LinkAssociateWith");
            participants.add(participant);
        }
        objectRepository.upsertTernaryLink(tx, model.name(), association.name(), participants);
    }

    private String resolveBinaryLinkLabel(MAssociationEnd sourceEnd, MAssociationEnd targetEnd) {
        if (targetEnd.aggregationKind() == 2 || sourceEnd.aggregationKind() == 2) {
            return "LinkComposeOf";
        }
        if (targetEnd.aggregationKind() == 1 || sourceEnd.aggregationKind() == 1) {
            return "LinkAggregates";
        }
        return "LinkAssociateWith";
    }

    private void deleteLink(org.neo4j.driver.TransactionContext tx, ImportLinkSpec linkSpec) {
        List<String> endpoints = linkSpec.getEndpointNames();
        if (endpoints.size() == 2) {
            Map<String, Object> params = new HashMap<>();
            params.put("left", endpoints.get(0));
            params.put("right", endpoints.get(1));
            params.put("assocName", linkSpec.getAssociationName());
            params.put("sourceQualifiers", qualifierValuesForEnd(linkSpec, 0));
            params.put("targetQualifiers", qualifierValuesForEnd(linkSpec, 1));
            tx.run("""
                    MATCH (a {use_id: $left})-[r]->(b {use_id: $right})
                    WHERE r.name = $assocName OR r.associationName = $assocName
                      AND coalesce(r.sourceQualifiers, []) = $sourceQualifiers
                      AND coalesce(r.targetQualifiers, []) = $targetQualifiers
                    DELETE r
                    """, params);
            tx.run("""
                    MATCH (a {use_id: $right})-[r]->(b {use_id: $left})
                    WHERE r.name = $assocName OR r.associationName = $assocName
                      AND coalesce(r.sourceQualifiers, []) = $targetQualifiers
                      AND coalesce(r.targetQualifiers, []) = $sourceQualifiers
                    DELETE r
                    """, params);
            return;
        }

        tx.run("""
                MATCH (hub)
                WHERE hub.use_id STARTS WITH $hubIdPrefix
                OPTIONAL MATCH (hub)-[r]-()
                DELETE r
                WITH hub
                DETACH DELETE hub
                """, Map.of("hubIdPrefix", linkSpec.getAssociationName() + "_"));
    }

    private String lowerFirst(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return Character.toLowerCase(value.charAt(0)) + value.substring(1);
    }

    private List<String> qualifierValuesForEnd(ImportLinkSpec linkSpec, int endIndex) {
        if (linkSpec.getQualifierValues().size() <= endIndex) {
            return List.of();
        }
        List<String> values = linkSpec.getQualifierValues().get(endIndex);
        return values != null ? values : List.of();
    }
}
