package org.uet.dse.neo4j.repo;

import org.neo4j.driver.*;
import org.uet.dse.neo4j.repo.query.Neo4jObjectQuery;
import org.uet.dse.neo4j.sync.helper.OclSerializer;

import java.util.*;

public class Neo4jObjectRepository {

  public void upsertObjectNode(TransactionContext tx, String objName, String className) {
    tx.run(Neo4jObjectQuery.upsertObjectNode(className), Values.parameters("clsName", className, "objName", objName));
  }

  public void deleteObjectDeeply(TransactionContext tx, String objName) {
    tx.run(Neo4jObjectQuery.DELETE_OBJECT_DEEPLY, Map.of("name", objName));
  }

  public void removeAttributeValueNode(TransactionContext tx, String objName, String attrName) {
    tx.run(Neo4jObjectQuery.DELETE_ATTRIBUTE_VALUE, Map.of("vId", attributeValueId(objName, attrName)));
  }

  public void setAttributeValueNode(TransactionContext tx, String objName, String ownerClassName, String attrName, Object value, Map<String, Object> metadata) {

    String valNodeId = attributeValueId(objName, attrName);
    String attrDefId = attributeDefId(ownerClassName, attrName);

    boolean isObjRef = (boolean) metadata.get("isObjectReference");
    boolean isNested = (boolean) metadata.get("isNestedCollection");
    boolean isColl = (boolean) metadata.get("isCollection");

    Object valueForNeo4j = "Undefined";
    if (value != null && !"Undefined".equals(value)) {
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
      } else {
        valueForNeo4j = OclSerializer.serialize(value);
      }
    }

    //tx.run(Neo4jObjectQuery.DELETE_ATTRIBUTE_VALUE, Map.of("vId", valNodeId));
    tx.run("MATCH (o {use_id: $objName})-[:ObjectHasAttribute]->(val:AttributeValue {name: $valId}) " +
        "OPTIONAL MATCH (val)-[:HasNestedCollectionValue*1..5]->(n) " +
        "DETACH DELETE val, n", Map.of("objName", objName, "valId", valNodeId));

    tx.run(Neo4jObjectQuery.CREATE_ATTRIBUTE_VALUE, Values.parameters(
        "objName", objName,
        "attrDefId", attrDefId,
        "valId", valNodeId,
        "type", metadata.get("type"),
        "val", valueForNeo4j,
        "isColl", isColl,
        "collType", metadata.get("collectionType"),
        "isNested", isNested,
        "isObjRef", isObjRef
    ));

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

    tx.run(Neo4jObjectQuery.upsertBinaryLink(label), Map.of("id1", srcObj, "id2", tgtObj, "name", assocName, "sRole", sRole, "tRole", tRole));
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