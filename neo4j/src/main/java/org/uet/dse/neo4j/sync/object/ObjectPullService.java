package org.uet.dse.neo4j.sync.object;

import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Value;
import org.tzi.use.api.UseSystemApi;
import org.tzi.use.uml.mm.MAttribute;
import org.tzi.use.uml.ocl.type.Type;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uml.sys.MSystem;
import org.uet.dse.neo4j.manager.Neo4jDriverManager;
import org.uet.dse.neo4j.model.FullObjectSnapshot;
import org.uet.dse.neo4j.model.LinkState;
import org.uet.dse.neo4j.model.ObjectState;
import org.uet.dse.neo4j.manager.WorkLogManager;
import org.uet.dse.neo4j.repo.query.Neo4jObjectQuery;
import org.uet.dse.neo4j.sync.helper.UmlTypeTranslator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class ObjectPullService {
  private final UseSystemApi systemApi;
  private final MSystem system;

  public ObjectPullService(UseSystemApi systemApi, MSystem system) {
    this.systemApi = systemApi;
    this.system = system;
  }

  public void pullObjectFromNeo4j(ObjectDiff diff) throws Exception {
    deleteStaleLinks(diff);
    deleteStaleObjects(diff);
    createMissingObjects(diff);
    syncAllObjectAttributes(diff);
    pullLinks();
  }

  private void deleteStaleLinks(ObjectDiff diff) {
    for (String linkId : diff.javaOnlyLinks) {
      LinkState ls = diff.javaSnapshot.links.get(linkId);
      if (ls == null)
        continue;

      try {
        systemApi.deleteLink(ls.assocName, ls.participants.toArray(new String[0]), qualifierExpressions(ls.qualifierValues));
        WorkLogManager.getInstance().log("LINK_PULL_DELETE", "Removed link: " + ls.getIdentity());
      } catch (Exception e) {
        WorkLogManager.getInstance().log("LINK_DELETE_ERROR", "Failed to remove link " + linkId + ": " + e.getMessage());
      }
    }
  }

  private void deleteStaleObjects(ObjectDiff diff) throws Exception {
    for (String objName : diff.javaOnlyObjects) {
      if (systemApi.getObject(objName) == null)
        continue;

      systemApi.deleteObject(objName);
      WorkLogManager.getInstance().log("OBJECT_PULL_DELETE", "Removed object from USE: " + objName);
    }
  }
  private void createMissingObjects(ObjectDiff diff) throws Exception {
    List<String> associationClassQueue = new ArrayList<>();

    for (String objName : diff.neo4jOnlyObjects) {
      ObjectState dbObj = diff.neo4jSnapshot.objects.get(objName);
      if (dbObj == null) continue;

      if (system.model().getClass(dbObj.className) == null) {
        WorkLogManager.getInstance().log("ERROR", "Class missing for object: " + objName);
        continue;
      }

      try {
        systemApi.createObject(dbObj.className, objName);
        WorkLogManager.getInstance().log("OBJECT_PULL_CREATE", "Created normal object: " + objName);
      } catch (Exception e) {
        associationClassQueue.add(objName);
      }
    }

    for (String objName : associationClassQueue) {
      ObjectState dbObj = diff.neo4jSnapshot.objects.get(objName);

      LinkState ls = findLinkStateForLinkObject(diff.neo4jSnapshot, objName);

      if (ls != null && ls.participants != null && !ls.participants.isEmpty()) {
        try {
          String[] participants = ls.participants.toArray(new String[0]);

          systemApi.createLinkObject(dbObj.className, objName, participants);
          WorkLogManager.getInstance().log("OBJECT_PULL_CREATE", "Created LinkObject: " + objName);
        } catch (Exception e) {
          WorkLogManager.getInstance().log("ERROR", "Failed to create LinkObject " + objName + ": " + e.getMessage());
          try {
            String[] arr = ls.participants.toArray(new String[0]);

            for (int i = 0; i < arr.length / 2; i++) {
              String temp = arr[i];
              arr[i] = arr[arr.length - 1 - i];
              arr[arr.length - 1 - i] = temp;
            }

            String[] participants = arr;
            systemApi.createLinkObject(dbObj.className, objName, participants);
          } catch (Exception exception) {
            WorkLogManager.getInstance().log("ERROR", "Failed to create LinkObject " + objName + ": " + e.getMessage());
          }
        }
      } else {
        WorkLogManager.getInstance().log("ERROR", "Cannot create LinkObject " + objName + ": Missing participant links in snapshot.");
      }
    }
  }

  private LinkState findLinkStateForLinkObject(FullObjectSnapshot snapshot, String loName) {
    return snapshot.links.values().stream()
        .filter(ls -> loName.equals(ls.linkObjectName))
        .findFirst()
        .orElse(null);
  }

  private void syncAllObjectAttributes(ObjectDiff diff) {
    for (MObject useObj : system.state().allObjects()) {
      ObjectState dbState = diff.neo4jSnapshot.objects.get(useObj.name());
      if (dbState == null)
        continue;

      syncAttributesForObject(useObj, dbState);
    }
  }

  private void syncAttributesForObject(MObject useObj, ObjectState dbState) {
    for (MAttribute attrDef : useObj.cls().allAttributes()) {
      try {
        syncAttribute(useObj, attrDef, dbState);
      } catch (Exception e) {
        WorkLogManager.getInstance().log("ATTR_ERROR", "Failed to sync " + attrDef.name() + " on " + useObj.name() + ": " + e.getMessage());
      }
    }
  }

  /**
   * resets an attribute to Undefined only when it currently holds a value — avoids a no-op write and keeps the log meaningful.
   */
  private void resetIfDefined(MObject useObj, MAttribute attrDef, String objName, String attrName) throws Exception {
    boolean isDefined = !useObj.state(system.state()).attributeValue(attrDef).isUndefined();
    if (!isDefined)
      return;

    systemApi.setAttributeValue(objName, attrName, "Undefined");
    WorkLogManager.getInstance().log("ATTR_RESET", "Reset " + attrName + " on " + objName + " to Undefined");
  }

  public void pullLinks() {
    String dbName = Neo4jDriverManager.getInstance().getActiveDatabase();
    try (Session session = Neo4jDriverManager.getInstance().getDriver().session(SessionConfig.forDatabase(dbName))) {

      pullBinaryLinks(session, systemApi);
      pullTernaryLinks(session, systemApi);
      pullLinkObjects(session, systemApi);
    }
  }

  private void pullBinaryLinks(Session session, UseSystemApi api) {
    session.run(Neo4jObjectQuery.PULL_BINARY_LINKS).forEachRemaining(rec -> {
      String assocName = rec.get("assocName").asString();
      String[] ends = { rec.get("src").asString(), rec.get("tgt").asString() };
      String[][] qualifiers = qualifierExpressions(List.of(
          rec.get("sourceQualifiers").asList(v -> v.isNull() ? null : v.asString()),
          rec.get("targetQualifiers").asList(v -> v.isNull() ? null : v.asString())
      ));
      createLinkSafely(api, assocName, ends, qualifiers);
    });
  }

  private void pullTernaryLinks(Session session, UseSystemApi api) {
    session.run(Neo4jObjectQuery.PULL_TERNARY_LINKS).forEachRemaining(rec -> {
      String assocName = rec.get("assocName").asString();
      String[] objNames = extractTernaryParticipants(rec);
      createLinkSafely(api, assocName, objNames);
    });
  }


  private String[] extractTernaryParticipants(Record rec) {
    return rec.get("participants").asList(org.neo4j.driver.Value::asMap).stream().sorted(Comparator.comparingInt(p -> ((Number) p.get("idx")).intValue())).map(p -> (String) p.get("obj")).toArray(String[]::new);
  }

  private void pullLinkObjects(Session session, UseSystemApi api) {
    session.run(Neo4jObjectQuery.PULL_LINK_OBJECTS).forEachRemaining(rec -> {
      String acName = rec.get("acName").asString();
      String loName = rec.get("loName").asString();
      String[] participants = rec.get("participants").asList(Value::asString).toArray(new String[0]);
      createLinkObjectSafely(api, acName, loName, participants);
    });
  }

  // ── Safe creation wrappers ────────────────────────────────────────────────────

  /**
   * swallow duplicatelink exceptions, which are expected when pulling into a state that already partially exists. All other failures are logged.
   */
  private void createLinkSafely(UseSystemApi api, String assocName, String[] participants) {
    createLinkSafely(api, assocName, participants, new String[0][]);
  }

  private void createLinkSafely(UseSystemApi api, String assocName, String[] participants, String[][] qualifierExpressions) {
    try {
      api.createLink(assocName, participants, qualifierExpressions);
    } catch (Exception e) {
      WorkLogManager.getInstance().log("LINK_PULL_ERROR", "Failed to create link [" + assocName + "]: " + e.getMessage());
    }
  }

  private void createLinkObjectSafely(UseSystemApi api, String acName, String loName, String[] participants) {
    try {
      api.createLinkObject(acName, loName, participants);
    } catch (Exception e) {
      WorkLogManager.getInstance().log("LINK_OBJECT_PULL_ERROR", "Failed to create link object [" + loName + "] of [" + acName + "]: " + e.getMessage());
    }
  }

  private String[][] qualifierExpressions(List<List<String>> qualifierValues) {
    if (qualifierValues == null || qualifierValues.isEmpty()) {
      return new String[0][];
    }
    String[][] expressions = new String[qualifierValues.size()][];
    for (int i = 0; i < qualifierValues.size(); i++) {
      List<String> endValues = qualifierValues.get(i);
      expressions[i] = endValues == null ? new String[0] : endValues.toArray(new String[0]);
    }
    return expressions;
  }

  private void syncAttribute(MObject useObj, MAttribute attrDef, ObjectState dbState) throws Exception {
    String objName = useObj.name();
    String attrName = attrDef.name();
    Type typeDef = attrDef.type();

    Object dbValue = null;

    boolean existsInDb = false;
    if (dbState.objectReferences.containsKey(attrName)) {
      dbValue = dbState.objectReferences.get(attrName);
      existsInDb = true;
    } else if (dbState.primitiveValues.containsKey(attrName)) {
      dbValue = dbState.primitiveValues.get(attrName);
      existsInDb = true;
    }

    if (!existsInDb || dbValue == null || "Undefined".equals(dbValue)) {
      resetIfDefined(useObj, attrDef, objName, attrName);
      return;
    }


    if (dbValue == null || "Undefined".equals(dbValue) || "COLLECTION_EMPTY".equals(dbValue)) {
      resetIfDefined(useObj, attrDef, objName, attrName);
      return;
    }

    String oclExpression = UmlTypeTranslator.toTypedOclLiteral(dbValue, typeDef);

    if ("Undefined".equals(oclExpression)) {
      resetIfDefined(useObj, attrDef, objName, attrName);
    } else {
      systemApi.setAttributeValue(objName, attrName, oclExpression);
    }
  }


}
