package org.uet.dse.neo4j.sync.model;

import org.neo4j.driver.Session;
import org.tzi.use.api.UseModelApi;
import org.tzi.use.graph.DirectedGraph;
import org.tzi.use.uml.mm.*;
import org.uet.dse.neo4j.manager.Neo4jDriverManager;
import org.uet.dse.neo4j.manager.WorkLogManager;
import org.uet.dse.neo4j.repo.Neo4jModelRepository;
import org.uet.dse.neo4j.sync.model.simpleDomain.KusakabeModelDiff;

import java.util.Iterator;
import java.util.List;

public class IncrementalModelPushService extends CoreModelPushService implements ModelPushService {
  private final UseModelApi modelApi;
  private final WorkLogManager logger = WorkLogManager.getInstance();

  private final Neo4jModelRepository repo;

  public IncrementalModelPushService(UseModelApi modelApi) {
    super(modelApi);
    this.modelApi = modelApi;
    this.repo = new Neo4jModelRepository();
    this.repo.initializeMetamodel(modelApi.getModel().name());
  }

  @Override
  public void pushModelToNeo4j(ModelDiff modelDiff, MModel inputModel) {
    KusakabeModelDiff kusakabeModelDiff = (KusakabeModelDiff) modelDiff;
    MModel allUseModel;
    if (inputModel == null) {
      allUseModel = modelApi.getModel();
    } else {
      allUseModel = inputModel;
    }

    String currentUser = Neo4jDriverManager.getInstance().getSessionManager().getUserDisplayName();

    logger.log("PUSH_START", "User " + currentUser + " initiated sync for model: " + allUseModel.name());

    try (Session session = Neo4jDriverManager.getInstance().openSession()) {
      repo.initializeMetamodel(inputModel.name());

      this.syncCoreEntities(session, allUseModel, kusakabeModelDiff);
      this.syncInheritance(session, allUseModel, kusakabeModelDiff);
      this.syncStructuralDetails(session, allUseModel, kusakabeModelDiff);
      this.syncAssociations(session, allUseModel, kusakabeModelDiff);

      updateModelVersion(session, currentUser);
      logger.log("PUSH_SUCCESS", "Model " + allUseModel.name() + " is now synchronized.");

    } catch (Exception e) {
      logger.log("PUSH_ERROR", "Failed to push model: " + e.getMessage());
      throw e;
    }

  }

  public void syncCoreEntities(Session session, MModel model, KusakabeModelDiff diff) {
    for (org.tzi.use.uml.ocl.type.EnumType e : model.enumTypes()) {
      if (isContaining(e.name(), diff.classDiff.isolated)) {
        repo.upsertEnumeration(e.name(), e.getLiterals(), model.name());
        logger.log("PUSH_ENUM", "Pushed enumeration: " + e.name());
      }
    }

    // Sync Classes (Concrete, Abstract, AssociationClass)
    for (MClass cls : model.classes()) {

      if (isContaining(cls.name(), diff.classDiff.isolated)) {
        createClassNode(session, cls);
      }
    }
  }

  //may meet bug, added rules to avoid conflict
  public void syncInheritance(Session session, MModel model, KusakabeModelDiff kusakabeModelDiff) {
    DirectedGraph<MClassifier, MGeneralization> genGraph = model.generalizationGraph();
    Iterator<MGeneralization> edgeIterator = genGraph.edgeIterator();

    while (edgeIterator.hasNext()) {
      MGeneralization gen = edgeIterator.next();
      String parent = gen.parent().name();
      String child = gen.child().name();

      boolean isParentNew = isContaining(parent, kusakabeModelDiff.classDiff.isolated);
      boolean isChildNew = isContaining(child, kusakabeModelDiff.classDiff.isolated);
      if ((isChildNew && isParentNew)
          || (isChildNew && !isParentNew)
      ) {
        createExtendsEdge(session, gen.child(), gen.parent());
      }
    }
  }

  public void syncStructuralDetails(Session session, MModel model, KusakabeModelDiff kusakabeModelDiff) {
    for (MClass cls : model.classes()) {
      if (isContaining(cls.name(), kusakabeModelDiff.classDiff.isolated)) {
        mapAttributes(session, cls);
        mapOperations(session, cls);
        mapInvariants(session, cls);
        mapOperationConstraintsForAllOperationsInClass(session, cls);
      }
    }
  }

  public void syncAssociations(Session session, MModel allUseModel, KusakabeModelDiff kusakabeModelDiff) {
    for (MAssociation assoc : allUseModel.associations()) {
      if (isContaining(assoc.name(), kusakabeModelDiff.associationDiff.isolated)) {
        if (assoc.associationEnds().size() > 2) {
          repo.upsertTernaryAssociation(assoc, modelApi.getModel().name());
        } else if (assoc instanceof MAssociationClass) {
          repo.createAssociationClassStructure((MAssociationClass) assoc, modelApi.getModel().name());
        } else {
          repo.createAssociationEdge(assoc);
        }
      }
    }
  }

  public boolean isContaining(String value, List<String> collection) {
    return collection.stream()
        .anyMatch(ei -> ei.equals(value));
  }

}
