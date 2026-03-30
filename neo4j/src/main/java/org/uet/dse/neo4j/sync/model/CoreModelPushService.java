package org.uet.dse.neo4j.sync.model;

import org.neo4j.driver.Session;
import org.neo4j.driver.Values;
import org.tzi.use.api.UseModelApi;
import org.tzi.use.graph.DirectedGraph;
import org.tzi.use.uml.mm.*;
import org.tzi.use.uml.ocl.type.CollectionType;
import org.tzi.use.uml.ocl.type.Type;
import org.uet.dse.neo4j.helper.TypeMapper;
import org.uet.dse.neo4j.manager.Neo4jDriverManager;
import org.uet.dse.neo4j.mm.core.common.NCollectionType;
import org.uet.dse.neo4j.mm.core.common.NType;
import org.uet.dse.neo4j.mm.core.node.*;
import org.uet.dse.neo4j.manager.WorkLogManager;
import org.uet.dse.neo4j.query.builder.CypherQueryBuilder;
import org.uet.dse.neo4j.repo.Neo4jModelRepository;
import org.uet.dse.neo4j.sync.helper.UmlTypeTranslator;
import org.uet.dse.neo4j.sync.object.ObjectSyncHelper;

import java.util.Iterator;
import java.util.Map;

public class CoreModelPushService {
    private final UseModelApi modelApi;
    private final WorkLogManager logger = WorkLogManager.getInstance();

    private final Neo4jModelRepository repo;

    public CoreModelPushService(UseModelApi modelApi) {
        this.modelApi = modelApi;
        this.repo = new Neo4jModelRepository();
        // Khởi tạo metamodel trước khi push
        this.repo.initializeMetamodel(modelApi.getModel().name());
    }

    public void pushModelToNeo4j() {
        MModel model = modelApi.getModel();
        String currentUser = Neo4jDriverManager.getInstance().getSessionManager().getUserDisplayName();

        logger.log("PUSH_START", "User " + currentUser + " initiated sync for model: " + model.name());

        try (Session session = Neo4jDriverManager.getInstance().openSession()) {
            repo.initializeMetamodel(model.name());

            syncCoreEntities(session, model);
            syncInheritance(session, model);
            syncStructuralDetails(session, model);
            syncAssociations(model);

            updateModelVersion(session, currentUser);
            logger.log("PUSH_SUCCESS", "Model " + model.name() + " is now synchronized.");

        } catch (Exception e) {
            logger.log("PUSH_ERROR", "Failed to push model: " + e.getMessage());
            throw e;
        }
    }

    public void syncCoreEntities(Session session, MModel model) {
        for (org.tzi.use.uml.ocl.type.EnumType e : model.enumTypes()) {
            repo.upsertEnumeration(e.name(), e.getLiterals(), model.name());
            logger.log("PUSH_ENUM", "Pushed enumeration: " + e.name());
        }

        for (MClass cls : model.classes()) {
            createClassNode(session, cls);
        }
    }

    public void syncInheritance(Session session, MModel model) {
        DirectedGraph<MClassifier, MGeneralization> genGraph = model.generalizationGraph();
        Iterator<MGeneralization> edgeIterator = genGraph.edgeIterator();

        while (edgeIterator.hasNext()) {
            MGeneralization gen = edgeIterator.next();
            createExtendsEdge(session, gen.child(), gen.parent());
        }
    }

    public void syncStructuralDetails(Session session, MModel model) {
        for (MClass cls : model.classes()) {
            mapAttributes(session, cls);
            mapOperations(session, cls);
            mapInvariants(session, cls);
            mapOperationConstraintsForAllOperationsInClass(session, cls);
        }
    }

    public void syncAssociations(MModel model) {
        for (MAssociation assoc : model.associations()) {
            if (assoc.associationEnds().size() > 2) {
                repo.upsertTernaryAssociation(assoc, model.name());
            }
            else if (assoc instanceof MAssociationClass) {
                repo.createAssociationClassStructure((MAssociationClass) assoc, model.name());
            } else {
                repo.createAssociationEdge(assoc);
            }
        }
    }


    public void updateModelVersion(org.neo4j.driver.Session session, String user) {
        org.tzi.use.uml.mm.MModel model = modelApi.getModel();
        String modelName = model.name();

        long modelHash = ObjectSyncHelper.calculateModelHash(model);
        long ts = System.currentTimeMillis();

        String cypher = "MERGE (v:ModelVersion {id: 'CURRENT'}) " +
                "SET v.timestamp = timestamp(), v.user = $user, " +
                "    v.modelHash = $hash, v.modelName = $name";
        session.run(cypher, Values.parameters("user", user, "hash", modelHash, "name", modelName));

        org.uet.dse.neo4j.sync.ChangeTracker.updateLocalTimestamp(ts);

        logger.log("VERSION_UPDATE", "Model version updated. Hash: " + modelHash);
    }
    /**
     * refactored
     */
    public void mapOperations(Session session, MClass cls) {
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
    public void createExtendsEdge(Session session, MClassifier child, MClassifier parent) {
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
    public void createClassNode(Session session, MClass cls) {
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
    public void mapAttributes(Session session, MClass cls) {
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
    public void pushNestedCollectionNodes(Session session, String parentId, Type currentType) {
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
    public void mapInvariants(Session session, MClass cls) {
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
    public void mapOperationConstraintsForOneOperation(Session session, MOperation op, String opId) {
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
    public void mapOperationConstraintsForAllOperationsInClass(Session session, MClass cls) {
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
