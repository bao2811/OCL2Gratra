package org.uet.dse.neo4j.sync.model;

import org.neo4j.driver.QueryRunner;
import org.neo4j.driver.Values;
import org.tzi.use.api.UseModelApi;
import org.tzi.use.graph.DirectedGraph;
import org.tzi.use.uml.mm.*;
import org.tzi.use.uml.ocl.type.CollectionType;
import org.tzi.use.uml.ocl.type.Type;
import org.uet.dse.neo4j.helper.TypeMapper;
import org.uet.dse.neo4j.encoding.CanonicalGraphSchema;
import org.uet.dse.neo4j.manager.Neo4jDriverManager;
import org.uet.dse.neo4j.mm.core.common.NCollectionType;
import org.uet.dse.neo4j.mm.core.common.NType;
import org.uet.dse.neo4j.mm.core.node.*;
import org.uet.dse.neo4j.manager.WorkLogManager;
import org.uet.dse.neo4j.query.builder.CypherQueryBuilder;
import org.uet.dse.neo4j.repo.Neo4jModelRepository;
import org.uet.dse.neo4j.sync.helper.UmlTypeTranslator;
import org.uet.dse.neo4j.sync.object.ObjectSyncHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CoreModelPushService {
    private final UseModelApi modelApi;
    private final WorkLogManager logger = WorkLogManager.getInstance();

    private final Neo4jModelRepository repo;

    public CoreModelPushService(UseModelApi modelApi) {
        this.modelApi = modelApi;
        this.repo = new Neo4jModelRepository();
    }

    public void pushModelToNeo4j() {
        MModel model = modelApi.getModel();
        String currentUser = Neo4jDriverManager.getInstance().getSessionManager().getUserDisplayName();

        logger.log("PUSH_START", "User " + currentUser + " initiated sync for model: " + model.name());

        try {
            long modelHash = ObjectSyncHelper.calculateModelHash(model);
            long phaseStartedAt = System.nanoTime();
            CanonicalGraphSchema.ensureInstalled(
                    Neo4jDriverManager.getInstance().getDriver(),
                    Neo4jDriverManager.getInstance().getActiveDatabase());
            long schemaNs = System.nanoTime() - phaseStartedAt;
            phaseStartedAt = System.nanoTime();
            if (repo.isModelCurrent(model.name(), modelHash)) {
                long currentCheckNs = System.nanoTime() - phaseStartedAt;
                logger.log("PUSH_SKIP", "M2 is unchanged; skipped graph writes for " + model.name()
                        + "; schemaMs=" + millis(schemaNs) + "; currentCheckMs=" + millis(currentCheckNs));
                return;
            }
            long currentCheckNs = System.nanoTime() - phaseStartedAt;
            long[] phases = new long[8];
            long transactionStartedAt = System.nanoTime();
            repo.withSharedSession(session -> {
                long startedAt = System.nanoTime();
                repo.initializeMetamodel(model.name());
                phases[0] = System.nanoTime() - startedAt;
                startedAt = System.nanoTime();
                syncCoreEntities(session, model);
                phases[1] = System.nanoTime() - startedAt;
                startedAt = System.nanoTime();
                syncInheritance(session, model);
                phases[2] = System.nanoTime() - startedAt;
                startedAt = System.nanoTime();
                syncStructuralDetails(session, model);
                phases[3] = System.nanoTime() - startedAt;
                startedAt = System.nanoTime();
                syncAssociations(model);
                phases[4] = System.nanoTime() - startedAt;
                startedAt = System.nanoTime();
                updateModelVersion(session, currentUser);
                phases[5] = System.nanoTime() - startedAt;
            });
            phases[6] = System.nanoTime() - transactionStartedAt;
            logger.log("PUSH_PROFILE", "model=" + model.name()
                    + "; schemaMs=" + millis(schemaNs)
                    + "; currentCheckMs=" + millis(currentCheckNs)
                    + "; metaMs=" + millis(phases[0])
                    + "; classesMs=" + millis(phases[1])
                    + "; inheritanceMs=" + millis(phases[2])
                    + "; structureMs=" + millis(phases[3])
                    + "; associationsMs=" + millis(phases[4])
                    + "; versionMs=" + millis(phases[5])
                    + "; transactionMs=" + millis(phases[6]));
            logger.log("PUSH_SUCCESS", "Model " + model.name() + " is now synchronized.");

        } catch (Exception e) {
            logger.log("PUSH_ERROR", "Failed to push model: " + e.getMessage());
            throw e;
        }
    }

    public void syncCoreEntities(QueryRunner session, MModel model) {
        for (org.tzi.use.uml.ocl.type.EnumType e : model.enumTypes()) {
            repo.upsertEnumeration(e.name(), e.getLiterals(), model.name());
            logger.log("PUSH_ENUM", "Pushed enumeration: " + e.name());
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (MClass cls : model.classes()) rows.add(classRow(cls, model.name()));
        repo.upsertClassesBatch(session, model.name(), rows);
    }

    private static String millis(long nanoseconds) {
        return String.format(java.util.Locale.ROOT, "%.3f", nanoseconds / 1_000_000.0);
    }

    private Map<String, Object> classRow(MClass cls, String modelName) {
        AbstractClassNode node = cls instanceof MAssociationClass
                ? new NodeAssociationClass()
                : cls.isAbstract() ? new NodeAbstractClass() : new NodeConcreteClass();
        node.setName(cls.name());
        node.setHasParent(!cls.parents().isEmpty());
        node.setHasChildren(!cls.children().isEmpty());
        Map<String, Object> props = new HashMap<>(node.toPropertyMap());
        String classKey = org.uet.dse.neo4j.encoding.CanonicalGraphEncoding.classKey(modelName, cls.name());
        props.put("modelKey", org.uet.dse.neo4j.encoding.CanonicalGraphEncoding.modelKey(modelName));
        props.put("classKey", classKey);
        props.put("canonicalKey", modelName + "::" + node.getMetaName() + "::" + cls.name());
        return Map.of("metaName", node.getMetaName(), "classKey", classKey, "props", props);
    }

    public void syncInheritance(QueryRunner session, MModel model) {
        DirectedGraph<MClassifier, MGeneralization> genGraph = model.generalizationGraph();
        Iterator<MGeneralization> edgeIterator = genGraph.edgeIterator();
        List<Map<String, Object>> rows = new ArrayList<>();
        while (edgeIterator.hasNext()) {
            MGeneralization gen = edgeIterator.next();
            rows.add(Map.of(
                    "childKey", org.uet.dse.neo4j.encoding.CanonicalGraphEncoding.classKey(
                            model.name(), gen.child().name()),
                    "parentKey", org.uet.dse.neo4j.encoding.CanonicalGraphEncoding.classKey(
                            model.name(), gen.parent().name()),
                    "childName", gen.child().name(),
                    "parentName", gen.parent().name()));
        }
        repo.upsertInheritanceBatch(session, model.name(), rows);
    }

    public void syncStructuralDetails(QueryRunner session, MModel model) {
        syncAttributesBatch(session, model);
        for (MClass cls : model.classes()) {
            mapOperations(session, cls);
            mapInvariants(session, cls);
            mapOperationConstraintsForAllOperationsInClass(session, cls);
        }
    }

    private void syncAttributesBatch(QueryRunner session, MModel model) {
        List<Map<String, Object>> rows = new ArrayList<>();
        List<Map<String, Object>> referenceRows = new ArrayList<>();
        for (MClass cls : model.classes()) {
            int index = 0;
            for (MAttribute attr : cls.attributes()) {
                Type fullType = attr.type();
                Type baseType = UmlTypeTranslator.getUltimateBaseType(fullType);
                NodeAttribute node = new NodeAttribute();
                node.setName(cls.name() + "_" + attr.name());
                node.setAttrName(attr.name());
                node.setIndex(index++);
                node.setNestedCollection(UmlTypeTranslator.getCollectionDepth(fullType) > 1);
                node.setCollection(fullType.isKindOfCollection(Type.VoidHandling.EXCLUDE_VOID));
                node.setType(UmlTypeTranslator.toDatabaseType(baseType));
                node.setDerived(attr.isDerived());
                try {
                    node.setCollectionType(node.isCollection()
                            ? NCollectionType.valueOf(UmlTypeTranslator.getCollectionType(fullType))
                            : NCollectionType.None);
                } catch (IllegalArgumentException exception) {
                    node.setCollectionType(NCollectionType.None);
                }
                Map<String, Object> props = new HashMap<>(node.toPropertyMap());
                props.put("modelKey", org.uet.dse.neo4j.encoding.CanonicalGraphEncoding.modelKey(model.name()));
                props.put("canonicalKey", model.name() + "::" + node.getMetaName() + "::" + node.getName());
                String attributeKey = org.uet.dse.neo4j.encoding.CanonicalGraphEncoding.attributeKey(
                        model.name(), cls.name(), attr.name());
                props.put("attributeKey", attributeKey);
                Map<String, Object> typeInfo = TypeMapper.parseType(baseType);
                String referenceName = (String) typeInfo.getOrDefault("ref", "");
                rows.add(Map.of(
                        "ownerKey", org.uet.dse.neo4j.encoding.CanonicalGraphEncoding.classKey(
                                model.name(), cls.name()),
                        "attributeKey", attributeKey,
                        "props", props));
                if (!referenceName.isBlank()) {
                    referenceRows.add(Map.of(
                            "attributeKey", attributeKey,
                            "referenceKey", org.uet.dse.neo4j.encoding.CanonicalGraphEncoding.classKey(
                                    model.name(), referenceName)));
                }
            }
        }
        repo.upsertAttributesBatch(session, model.name(), rows);
        repo.upsertAttributeReferencesBatch(session, model.name(), referenceRows);
    }

    public void syncAssociations(MModel model) {
        List<Map<String, Object>> binaryRows = new ArrayList<>();
        for (MAssociation assoc : model.associations()) {
            if (assoc.associationEnds().size() > 2) {
                repo.upsertTernaryAssociation(assoc, model.name());
            }
            else if (assoc instanceof MAssociationClass) {
                repo.createAssociationClassStructure((MAssociationClass) assoc, model.name());
            } else {
                binaryRows.add(binaryAssociationRow(assoc, model.name()));
            }
        }
        QueryRunner runner = repo.currentRunner();
        repo.removeObsoleteBinaryAssociationTypes(runner, model.name(), binaryRows);
        binaryRows.stream().collect(Collectors.groupingBy(row -> (String) row.get("edgeLabel")))
                .forEach((label, rows) -> repo.upsertBinaryAssociationsBatch(runner, label, rows));
    }

    private Map<String, Object> binaryAssociationRow(MAssociation association, String modelName) {
        MAssociationEnd source = association.associationEnds().get(0);
        MAssociationEnd target = association.associationEnds().get(1);
        String edgeLabel = source.aggregationKind() == 2 || target.aggregationKind() == 2
                ? "ComposeOf" : source.aggregationKind() == 1 || target.aggregationKind() == 1
                ? "Aggregates" : "AssociateWith";
        Map<String, Object> props = new HashMap<>();
        props.put("associationName", association.name());
        props.put("sourceClassName", source.cls().name());
        props.put("sourceClassrole", source.name());
        props.put("sourceMultiplicity", source.multiplicity().toString());
        props.put("sourceKind", source.aggregationKind());
        props.put("sourceOrdered", source.isOrdered());
        props.put("sourceQualifierNames", source.getQualifiers().stream().map(q -> q.name()).toList());
        props.put("sourceQualifierTypes", source.getQualifiers().stream().map(q -> q.type().toString()).toList());
        props.put("targetClassName", target.cls().name());
        props.put("targerClassrole", target.name());
        props.put("targetMultiplicity", target.multiplicity().toString());
        props.put("targetKind", target.aggregationKind());
        props.put("targetOrdered", target.isOrdered());
        props.put("targetQualifierNames", target.getQualifiers().stream().map(q -> q.name()).toList());
        props.put("targetQualifierTypes", target.getQualifiers().stream().map(q -> q.type().toString()).toList());
        return Map.of(
                "modelName", modelName,
                "edgeLabel", edgeLabel,
                "associationKey", org.uet.dse.neo4j.encoding.CanonicalGraphEncoding.associationKey(
                        modelName, association.name()),
                "sourceClassKey", org.uet.dse.neo4j.encoding.CanonicalGraphEncoding.classKey(
                        modelName, source.cls().name()),
                "targetClassKey", org.uet.dse.neo4j.encoding.CanonicalGraphEncoding.classKey(
                        modelName, target.cls().name()),
                "props", props);
    }


    public void updateModelVersion(QueryRunner session, String user) {
        org.tzi.use.uml.mm.MModel model = modelApi.getModel();
        String modelName = model.name();

        long modelHash = ObjectSyncHelper.calculateModelHash(model);
        long ts = System.currentTimeMillis();

        String cypher = "MERGE (v:ModelVersion {id: 'CURRENT'}) " +
                "SET v.timestamp = timestamp(), v.user = $user, " +
                "    v.modelHash = $hash, v.modelName = $name";
        session.run(cypher, Values.parameters("user", user, "hash", modelHash, "name", modelName));
        session.run("MATCH (m:ManageModel {name:$name}) "
                        + "SET m.modelHash=$hash, m.encodingVersion=$version",
                Values.parameters("name", modelName, "hash", modelHash,
                        "version", CanonicalGraphSchema.VERSION));

        org.uet.dse.neo4j.sync.ChangeTracker.updateLocalTimestamp(ts);

        logger.log("VERSION_UPDATE", "Model version updated. Hash: " + modelHash);
    }
    /**
     * refactored
     */
    public void mapOperations(QueryRunner session, MClass cls) {
        for (MOperation op : cls.operations()) {
            if (!op.cls().equals(cls)) continue;

            String opId = cls.name() + "_" + op.name();
            Map<String, Object> rInfo = TypeMapper.parseTypeUSE2Neo4j(op.resultType());

            NodeOperation opNode = new NodeOperation();
            opNode.setName(opId);
            opNode.setOpName(op.name());
            String rTypeStr = (String) rInfo.get("type");
            opNode.setReturnType(NType.fromString(rTypeStr));
            opNode.setReturnCollection((Boolean) rInfo.get("isCollection"));
            opNode.setAbstract(cls.isAbstract());

            String body = null;
            boolean isQuery = true;
            if (op.hasExpression()) {
                body = op.expression().toString();
                isQuery = true;
            } else if (op.hasStatement()) {
                body = op.getStatement().toString();
                isQuery = false;
            }
            opNode.setBody(body);
            opNode.setQuery(isQuery);

            repo.createInstanceNode(opNode, "Operation", modelApi.getModel().name());
            repo.createStructuralEdge(cls.name(), opNode.getName(), "HasOperation");

            TypeMapper.handleTypeReference(repo, opNode.getName(), op.resultType(), true);

            int order = 1;
            for (org.tzi.use.uml.ocl.expr.VarDecl param : op.paramList()) {
                Map<String, Object> pInfo = TypeMapper.parseTypeUSE2Neo4j(param.type());
                String paramId = opId + "_p_" + param.name();

                NodeParam paramNode = new NodeParam();
                paramNode.setName(paramId);
                paramNode.setpName(param.name());
                paramNode.setpOrder(order++);
                paramNode.setCollection((Boolean) pInfo.get("isCollection"));
                String pTypeStr = (String) pInfo.get("type");
                paramNode.setType(NType.fromString(pTypeStr));

                if ((Boolean) pInfo.get("isCollection")) {
                    String pColTypeStr = (String) pInfo.get("collType");
                    paramNode.setCollectionType(NCollectionType.valueOf(pColTypeStr));
                } else {
                    paramNode.setCollectionType(NCollectionType.None);
                }

                repo.createInstanceNode(paramNode, "Param", modelApi.getModel().name());
                repo.createStructuralEdge(opNode.getName(), paramNode.getName(), "HasParam");

                TypeMapper.handleTypeReference(repo, paramNode.getName(), param.type(), false);
            }

            mapOperationConstraintsForOneOperation(session, op, opNode.getName());
        }
    }

    /**
     * refactored
     * @param session
     * @param child
     * @param parent
     */
    public void createExtendsEdge(QueryRunner session, MClassifier child, MClassifier parent) {
        String cypher = CypherQueryBuilder.buildExtendsEdgeQuery();

        session.run(cypher, Values.parameters(
                "srcName", child.name(),
                "tgtName", parent.name()
        ));
    }


    /**
     * refactored
     * @param session
     * @param cls
     */
    public void createClassNode(QueryRunner session, MClass cls) {
        AbstractClassNode classNode;

        if (cls instanceof MAssociationClass) {
            classNode = new NodeAssociationClass();
        } else if (cls.isAbstract()) {
            classNode = new NodeAbstractClass();
        } else {
            classNode = new NodeConcreteClass();
        }
        classNode.setName(cls.name());
        classNode.setHasParent(!cls.parents().isEmpty());
        classNode.setHasChildren(!cls.children().isEmpty());

        repo.createInstanceNode(classNode, cls.name(), modelApi.getModel().name());
    }

    /**
     * refactored
     * @param session
     * @param cls
     */
    public void mapAttributes(QueryRunner session, MClass cls) {
        int index = 0;
        for (MAttribute attr : cls.attributes()) {
            Type fullType = attr.type();
            String instanceId = cls.name() + "_" + attr.name();

            Type ultimateBaseType = UmlTypeTranslator.getUltimateBaseType(fullType);
            int depth = UmlTypeTranslator.getCollectionDepth(fullType);
            boolean isCollection = depth > 0;
            boolean isNested = depth > 1;

            NodeAttribute nodeAttr = new NodeAttribute();
            nodeAttr.setName(instanceId);
            nodeAttr.setAttrName(attr.name());
            nodeAttr.setIndex(index++);
            nodeAttr.setNestedCollection(isNested);
            nodeAttr.setCollection(isCollection);
            nodeAttr.setType(UmlTypeTranslator.toDatabaseType(ultimateBaseType));
            nodeAttr.setDerived(attr.isDerived());
            if (isCollection) {
                String colTypeStr = UmlTypeTranslator.getCollectionType(fullType);
                try {
                    nodeAttr.setCollectionType(NCollectionType.valueOf(colTypeStr));
                } catch (IllegalArgumentException e) {
                    nodeAttr.setCollectionType(NCollectionType.None);
                }
            } else {
                nodeAttr.setCollectionType(NCollectionType.None);
            }


            repo.createInstanceNode(nodeAttr, "Attribute", modelApi.getModel().name());
            repo.createStructuralEdge(cls.name(), nodeAttr.getName(), "HasAttribute");

            TypeMapper.handleTypeReference(repo, nodeAttr.getName(), ultimateBaseType, false);

            if (isNested) {
                pushNestedCollectionNodes(session, nodeAttr.getName(), ((CollectionType) fullType).elemType());
            }
        }
    }

    /**
     * refactored
     * create node nested collection
     */
    public void pushNestedCollectionNodes(QueryRunner session, String parentId, Type currentType) {
        if (!currentType.isKindOfCollection(Type.VoidHandling.EXCLUDE_VOID)) return;
        Type innerType = ((CollectionType) currentType).elemType();
        boolean stillNested = innerType.isKindOfCollection(Type.VoidHandling.EXCLUDE_VOID);
        String uuidName = "nested_" + java.util.UUID.randomUUID().toString();

        NodeNestedCollection nestedNode = new NodeNestedCollection();
        nestedNode.setName(uuidName);
        nestedNode.setCollectionName(UmlTypeTranslator.getCollectionType(currentType));
        nestedNode.setNestedCollection(stillNested);

        repo.createInstanceNode(nestedNode, "NestedCollection", modelApi.getModel().name());

        repo.createStructuralEdge(parentId, nestedNode.getName(), "HasNestedCollection");

        if (stillNested) {
            pushNestedCollectionNodes(session, nestedNode.getName(), innerType);
        }
    }



    /**
     * refactored
     * class invariant
     */
    public void mapInvariants(QueryRunner session, MClass cls) {
        int index = 0;

        for (MClassInvariant inv : modelApi.getModel().classInvariants(cls)) {
            String displayName = (inv.name() == null || inv.name().isEmpty()) ? "unnamed" : inv.name();
            String invInstanceId = cls.name() + "_inv_" + index;

            NodeClassInvariant invNode = new NodeClassInvariant();
            invNode.setName(invInstanceId);
            invNode.setInvName(displayName);
            invNode.setExpression(inv.bodyExpression().toString());
            invNode.setExistential(inv.isExistential());
            invNode.setIndex(index);

            repo.createInstanceNode(invNode, "ClassInvariant", modelApi.getModel().name());
            repo.createStructuralEdge(cls.name(), invNode.getName(), "HasInvariant");

            index++;
        }
    }

    /**
     * refactored
     * pre/post
     */
    public void mapOperationConstraintsForOneOperation(QueryRunner session, MOperation op, String opId) {
        int preIdx = 0;
        for (MPrePostCondition pre : op.preConditions()) {
            NodePreCondition preNode = new NodePreCondition();

            preNode.setName(opId + "_pre_" + preIdx);
            preNode.setCondName(pre.name());
            preNode.setExpression(pre.expression().toString());
            preNode.setIndex(preIdx++);

            repo.createInstanceNode(preNode, "PreCondition", modelApi.getModel().name());
            repo.createStructuralEdge(opId, preNode.getName(), "HasPreCondition");
        }

        int postIdx = 0;
        for (MPrePostCondition post : op.postConditions()) {
            NodePostCondition postNode = new NodePostCondition();

            postNode.setName(opId + "_post_" + postIdx);
            postNode.setCondName(post.name());
            postNode.setExpression(post.expression().toString());
            postNode.setIndex(postIdx++);

            repo.createInstanceNode(postNode, "PostCondition", modelApi.getModel().name());
            repo.createStructuralEdge(opId, postNode.getName(), "HasPostCondition");
        }
    }

    /**
     * checked
     */
    public void mapOperationConstraintsForAllOperationsInClass(QueryRunner session, MClass cls) {
        for (MOperation op : cls.operations()) {
            if (op.cls().equals(cls)) {
                String opId = cls.name() + "_" + op.name();
                mapOperationConstraintsForOneOperation(session, op, opId);
            }
        }
    }

    public void pushSingleClass(MClass mClass) {

    }

    public void pullSingleClass(String className) throws Exception {

    }
}
