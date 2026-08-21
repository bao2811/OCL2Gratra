package org.uet.dse.neo4j.repo;

import org.neo4j.driver.*;
import org.uet.dse.neo4j.repo.query.Neo4jObjectQuery;
import org.uet.dse.neo4j.encoding.CanonicalGraphEncoding;
import org.uet.dse.neo4j.sync.helper.CanonicalScalarValueCodec;
import org.uet.dse.neo4j.sync.helper.UmlTypeTranslator;

import java.util.*;

public class Neo4jObjectRepository {

  public boolean checkClassesExist(TransactionContext tx, String modelName,
                                   Collection<String> classNames) {
    if (classNames.isEmpty()) return true;
    List<String> classKeys = classNames.stream()
        .map(name -> CanonicalGraphEncoding.classKey(modelName, name)).distinct().toList();
    long count = tx.run(
        "MATCH (cls:UmlClass {modelKey:$modelName}) WHERE cls.classKey IN $classKeys "
            + "RETURN count(DISTINCT cls.classKey) AS count",
        Map.of("modelName", modelName, "classKeys", classKeys)).single().get("count").asLong();
    return count == classKeys.size();
  }

  public void upsertObjectNodesBatch(TransactionContext tx, String modelName, String className,
                                     List<Map<String, Object>> rows) {
    if (rows.isEmpty()) return;
    tx.run(Neo4jObjectQuery.upsertObjectNodesBatchInModel(className),
        Map.of("modelName", modelName, "rows", rows));
  }

  public void setScalarAttributeValuesBatch(TransactionContext tx, String modelName,
                                            List<Map<String, Object>> rows) {
    if (rows.isEmpty()) return;
    tx.run(Neo4jObjectQuery.UPSERT_SCALAR_ATTRIBUTE_VALUES_BATCH,
        Map.of("modelName", modelName, "rows", rows));
  }

  public void deleteLinksBatch(TransactionContext tx, String modelName, Collection<String> identities) {
    if (identities.isEmpty()) return;
    List<String> keys = identities.stream().map(id -> modelName + "::" + id).toList();
    tx.run("MATCH (:Object {modelKey:$modelName})"
            + "-[r:LinkAssociateWith|LinkAggregates|LinkComposeOf]->"
            + "(:Object {modelKey:$modelName}) WHERE r.linkKey IN $keys DELETE r",
        Map.of("modelName", modelName, "keys", keys));
  }

  public void deleteObjectsDeeplyBatch(TransactionContext tx, String modelName,
                                        Collection<String> objectNames) {
    if (objectNames.isEmpty()) return;
    List<String> objectKeys = objectNames.stream()
        .map(name -> CanonicalGraphEncoding.objectKey(modelName, name)).toList();
    tx.run("MATCH (o:Object {modelKey:$modelName}) WHERE o.objectKey IN $objectKeys "
            + "OPTIONAL MATCH (o)-[:ObjectHasAttribute]->(val:AttributeValue) "
            + "OPTIONAL MATCH (val)-[:HasNestedCollectionValue*0..5]->(nested) "
            + "WITH collect(DISTINCT o)+collect(DISTINCT val)+collect(DISTINCT nested) AS nodes "
            + "UNWIND nodes AS node WITH DISTINCT node WHERE node IS NOT NULL DETACH DELETE node",
        Map.of("modelName", modelName, "objectKeys", objectKeys));
  }

  public void upsertBinaryLinksBatch(TransactionContext tx, String modelName, String label,
                                     List<Map<String, Object>> rows) {
    if (rows.isEmpty()) return;
    tx.run(Neo4jObjectQuery.upsertBinaryLinksBatchInModel(label),
        Map.of("modelName", modelName, "rows", rows));
  }

  public void upsertObjectNode(TransactionContext tx, String objName, String className) {
    tx.run(Neo4jObjectQuery.upsertObjectNode(className), Values.parameters("clsName", className, "objName", objName));
  }

  public void upsertObjectNode(TransactionContext tx, String modelName, String objName, String className) {
    upsertObjectNode(tx, modelName, objName, className, List.of(className));
  }

  public void upsertObjectNode(TransactionContext tx, String modelName, String objName, String className,
                               Collection<String> conformingClassNames) {
    List<String> classKeys = conformingClassNames.stream()
        .map(name -> CanonicalGraphEncoding.classKey(modelName, name))
        .distinct()
        .toList();
    tx.run(Neo4jObjectQuery.upsertObjectNodeInModel(className),
        Values.parameters(
            "modelName", modelName,
            "objName", objName,
            "objectKey", CanonicalGraphEncoding.objectKey(modelName, objName),
            "runtimeClassKey", CanonicalGraphEncoding.classKey(modelName, className),
            "classKeys", classKeys));
  }

  public void deleteObjectDeeply(TransactionContext tx, String objName) {
    tx.run(Neo4jObjectQuery.DELETE_OBJECT_DEEPLY, Map.of("name", objName));
  }

  public void removeAttributeValueNode(TransactionContext tx, String objName, String attrName) {
    tx.run(Neo4jObjectQuery.DELETE_ATTRIBUTE_VALUE, Map.of("vId", attributeValueId(objName, attrName)));
  }

  public void setAttributeValueNode(TransactionContext tx, String objName, String ownerClassName, String attrName, Object value, Map<String, Object> metadata) {
    setAttributeValueNode(tx, null, objName, ownerClassName, attrName, value, metadata);
  }

  public void setAttributeValueNode(TransactionContext tx, String modelName, String objName, String ownerClassName, String attrName, Object value, Map<String, Object> metadata) {

    String valNodeId = attributeValueId(objName, attrName);
    String attrDefId = attributeDefId(ownerClassName, attrName);

    boolean isObjRef = (boolean) metadata.get("isObjectReference");
    boolean isNested = (boolean) metadata.get("isNestedCollection");
    boolean isColl = (boolean) metadata.get("isCollection");

    Object valueForNeo4j = "Undefined";
    if (!isNested && !isColl && !isObjRef) {
      String databaseType = Objects.toString(metadata.get("type"), null);
      String oclType = UmlTypeTranslator.toUmlTypeString(databaseType);
      valueForNeo4j = CanonicalScalarValueCodec.encode(value, oclType);
    } else if (value != null) {
      if (isNested) {
        valueForNeo4j = "NESTED_COLLECTION";
      } else if (isColl && value instanceof Map) {
        Map<String, Object> collMap = (Map<String, Object>) value;
        List<Object> items = (List<Object>) collMap.get("items");

        if (items == null || items.isEmpty()) {
          valueForNeo4j = "COLLECTION_EMPTY";
        } else if (isObjRef) {
          valueForNeo4j = "COLLECTION_DATA";
        } else {
          // "1 | 2 | 3"
          valueForNeo4j = items.stream()
              .map(i -> i == null ? "null" : i.toString())
              .collect(java.util.stream.Collectors.joining(" | "));
        }
      } else if (isObjRef) {
        valueForNeo4j = "Object";
      }
    } else {
      valueForNeo4j = "Undefined";
    }

    //tx.run(Neo4jObjectQuery.DELETE_ATTRIBUTE_VALUE, Map.of("vId", valNodeId));
    String deleteOldValue = modelName == null
        ? "MATCH (o {use_id:$objName})-[:ObjectHasAttribute]->(val:AttributeValue {name:$valId}) "
        : "MATCH (o:Object {modelKey:$modelName,use_id:$objName})-[:ObjectHasAttribute]->"
            + "(val:AttributeValue {modelKey:$modelName,name:$valId}) ";
    deleteOldValue += "OPTIONAL MATCH (val)-[:HasNestedCollectionValue*1..5]->(n) "
        + "DETACH DELETE val, n";
    Map<String, Object> deleteParameters = new HashMap<>();
    deleteParameters.put("objName", objName);
    deleteParameters.put("valId", valNodeId);
    if (modelName != null) deleteParameters.put("modelName", modelName);
    tx.run(deleteOldValue, deleteParameters);

    String createAttributeQuery = modelName == null
        ? Neo4jObjectQuery.CREATE_ATTRIBUTE_VALUE
        : Neo4jObjectQuery.CREATE_ATTRIBUTE_VALUE_IN_MODEL;
    Map<String, Object> parameters = new HashMap<>();
    if (modelName != null) {
      parameters.put("modelName", modelName);
      parameters.put("attributeKey", CanonicalGraphEncoding.attributeKey(modelName, ownerClassName, attrName));
      parameters.put("slotKey", CanonicalGraphEncoding.attributeSlotKey(
          modelName, objName, ownerClassName, attrName));
    }
    parameters.put("objName", objName);
    parameters.put("attrDefId", attrDefId);
    parameters.put("valId", valNodeId);
    parameters.put("type", metadata.get("type"));
    parameters.put("val", valueForNeo4j);
    parameters.put("isColl", isColl);
    parameters.put("collType", metadata.get("collectionType"));
    parameters.put("isNested", isNested);
    parameters.put("isObjRef", isObjRef);
    tx.run(createAttributeQuery, parameters);

    if (value != null) {
      if (isColl && isObjRef && value instanceof Map && !isNested) {
        List<Object> items = (List<Object>) ((Map) value).get("items");
        if (items != null) {
          for (int i = 0; i < items.size(); i++) {
            Object item = items.get(i);
            if (item != null) {
              tx.run(Neo4jObjectQuery.CREATE_REFERENCE_VALUE_EDGE, Values.parameters(
                  "valNodeId", valNodeId,
                  "targetId", item.toString(),
                  "idx", i
              ));
            }
          }
        }
      }

      if (value != null && !"Undefined".equals(value)) {
        if (isNested && value instanceof Map) {
          pushNestedStructure(tx, valNodeId, (Map<String, Object>) value, isObjRef);
        } else if (isColl && isObjRef && value instanceof Map) {
          createObjectReferences(tx, valNodeId, ((Map) value).get("items"));
        } else if (isObjRef && !isColl) {
          createObjectReferences(tx, valNodeId, value);
        }
      }

//      if (isNested && value instanceof Map) {
//        pushNestedStructure(tx, valNodeId, (Map<String, Object>) value, isObjRef);
//      }
//
//      else if (isObjRef && !isColl && !"Undefined".equals(value)) {
//        createObjectReferences(tx, valNodeId, value);
//      }


    }
  }

  private void pushNestedStructureold(TransactionContext tx, String parentId, Map<String, Object> collMap, boolean isObjRef) {
    List<Object> items = (List<Object>) collMap.get("items");
    if (items == null) return;

    for (int i = 0; i < items.size(); i++) {
      Object item = items.get(i);

      if (item instanceof Map) {
        Map<String, Object> subColl = (Map<String, Object>) item;
        String childId = "nested_" + UUID.randomUUID();

        // Ghi isObjRef vào từng node NestedCollectionValue
        tx.run(Neo4jObjectQuery.CREATE_NESTED_COLLECTION_NODE,
            Values.parameters(
                "nId", childId,
                "collType", subColl.get("collectionType"),
                "isObjRef", isObjRef // TRUYỀN GIÁ TRỊ TỪ METADATA XUỐNG
            ));

        tx.run(Neo4jObjectQuery.CONNECT_NESTED_NODE,
            Values.parameters("parentId", parentId, "childId", childId, "idx", i));

        pushNestedStructure(tx, childId, subColl, isObjRef);
      }
      else if (item != null) {
        if (isObjRef) {
          tx.run(Neo4jObjectQuery.CONNECT_NESTED_TO_OBJECT,
              Values.parameters("nId", parentId, "targetId", item.toString(), "idx", i));
        } else {
          tx.run(Neo4jObjectQuery.SET_PRIMITIVE_VALUE_ON_NODE,
              Map.of("nId", parentId, "val", item.toString()));
        }
      }
    }
  }

  private void pushNestedStructure(TransactionContext tx, String parentId, Map<String, Object> collMap, boolean isObjRef) {
    List<Object> items = (List<Object>) collMap.get("items");
    if (items == null) return;

    // Danh sách để gom các giá trị nguyên thủy ở cấp độ hiện tại
    List<Object> primitiveLeafItems = new ArrayList<>();

    for (int i = 0; i < items.size(); i++) {
      Object item = items.get(i);

      if (item instanceof Map) {
        // --- TRƯỜNG HỢP: MẢNG CON (Lồng tiếp) ---
        Map<String, Object> subColl = (Map<String, Object>) item;
        String childId = "nested_" + UUID.randomUUID();

        tx.run(Neo4jObjectQuery.CREATE_NESTED_COLLECTION_NODE,
            Values.parameters("nId", childId, "collType", subColl.get("collectionType"), "isObjRef", isObjRef));

        tx.run(Neo4jObjectQuery.CONNECT_NESTED_NODE,
            Values.parameters("parentId", parentId, "childId", childId, "idx", i));

        // Đệ quy đào sâu
        pushNestedStructure(tx, childId, subColl, isObjRef);
      }
      else if (item != null) {
        // --- TRƯỜNG HỢP: PHẦN TỬ CUỐI (LÁ) ---
        if (isObjRef) {
          // Nếu là tham chiếu đối tượng: Nối cạnh (Giữ nguyên logic cũ vì cạnh không bị đè)
          tx.run(Neo4jObjectQuery.CONNECT_NESTED_TO_OBJECT,
              Values.parameters("nId", parentId, "targetId", item.toString(), "idx", i));
        } else {
          // Nếu là nguyên thủy: Thêm vào danh sách chờ để gộp chuỗi
          primitiveLeafItems.add(item);
        }
      }
    }

    // SAU VÒNG LẶP: Nếu có giá trị nguyên thủy, gộp chúng lại và SET một lần duy nhất
    if (!primitiveLeafItems.isEmpty()) {
      String joinedValues = primitiveLeafItems.stream()
          .map(Object::toString)
          .collect(java.util.stream.Collectors.joining(" | "));

      // Ghi chuỗi "1 | 2 | 3" vào thuộc tính value của node hiện tại (parentId)
      tx.run(Neo4jObjectQuery.SET_PRIMITIVE_VALUE_ON_NODE,
          Values.parameters("nId", parentId, "val", joinedValues));
    }
  }
  public boolean checkClassExistsInDb(TransactionContext tx, String className) {
    return tx.run(Neo4jObjectQuery.CHECK_CLASS_EXISTS, Map.of("name", className)).hasNext();
  }

  public void upsertBinaryLink(TransactionContext tx, String srcObj, String tgtObj, String assocName, String label, String sRole, String tRole) {
    upsertBinaryLink(tx, srcObj, tgtObj, assocName, label, sRole, tRole, List.of(), List.of());
  }

  public void upsertBinaryLink(TransactionContext tx, String srcObj, String tgtObj, String assocName, String label,
                               String sRole, String tRole, List<String> sourceQualifiers, List<String> targetQualifiers) {

    tx.run(Neo4jObjectQuery.upsertBinaryLink(label), Map.of(
        "id1", srcObj,
        "id2", tgtObj,
        "name", assocName,
        "sRole", sRole,
        "tRole", tRole,
        "sQualifiers", sourceQualifiers != null ? sourceQualifiers : List.of(),
        "tQualifiers", targetQualifiers != null ? targetQualifiers : List.of()));
  }

  public void upsertBinaryLink(TransactionContext tx, String modelName, String srcObj, String tgtObj,
                               String assocName, String label, String sRole, String tRole,
                               List<String> sourceQualifiers, List<String> targetQualifiers) {
    tx.run(Neo4jObjectQuery.upsertBinaryLinkInModel(label), Map.of(
        "modelName", modelName,
        "objectKey1", CanonicalGraphEncoding.objectKey(modelName, srcObj),
        "objectKey2", CanonicalGraphEncoding.objectKey(modelName, tgtObj),
        "associationKey", CanonicalGraphEncoding.associationKey(modelName, assocName),
        "linkKey", CanonicalGraphEncoding.binaryLinkKey(modelName, assocName, srcObj, tgtObj,
            sourceQualifiers != null ? sourceQualifiers : List.of(),
            targetQualifiers != null ? targetQualifiers : List.of()),
        "name", assocName,
        "sRole", sRole,
        "tRole", tRole,
        "sQualifiers", sourceQualifiers != null ? sourceQualifiers : List.of(),
        "tQualifiers", targetQualifiers != null ? targetQualifiers : List.of()));
  }

  public void upsertTernaryLink(TransactionContext tx, String assocName, List<Map<String, Object>> participants) {

    String hubId = assocName + "_" + System.currentTimeMillis();
    tx.run(Neo4jObjectQuery.createTernaryHub(assocName), Map.of("id", hubId, "name", assocName));

    for (Map<String, Object> p : participants) {
      tx.run(Neo4jObjectQuery.upsertTernarySpoke((String) p.get("label")), Map.of("objId", p.get("objName"), "hubId", hubId, "role", p.get("role"), "idx", p.get("index")));
    }
  }

  public void upsertLinkObject(TransactionContext tx, String loName, String loClassName, List<Map<String, Object>> participants) {

    upsertObjectNode(tx, loName, loClassName);

    for (Map<String, Object> p : participants) {
      tx.run(Neo4jObjectQuery.upsertLinkObjectSpoke((String) p.get("label")), Map.of("loId", loName, "pId", p.get("objName"), "role", p.get("role")));
    }
  }

  private void createObjectReferences(TransactionContext tx, String valNodeId, Object value) {
    if (value instanceof List) {
      List<String> targets = (List<String>) value;
      for (int i = 0; i < targets.size(); i++) {
        tx.run(Neo4jObjectQuery.CREATE_OBJECT_REFERENCE, Map.of("vId", valNodeId, "tId", targets.get(i), "idx", i));
      }
    } else {
      tx.run(Neo4jObjectQuery.CREATE_OBJECT_REFERENCE, Map.of("vId", valNodeId, "tId", value.toString(), "idx", 0));
    }
  }

  private boolean isObjectReference(Map<String, Object> metadata, Object value) {
    return "Object".equals(metadata.get("type")) && !"Undefined".equals(value);
  }

  private static String attributeValueId(String objName, String attrName) {
    return objName + "_" + attrName;
  }

  private static String attributeDefId(String ownerClassName, String attrName) {
    return ownerClassName + "_" + attrName;
  }

}
