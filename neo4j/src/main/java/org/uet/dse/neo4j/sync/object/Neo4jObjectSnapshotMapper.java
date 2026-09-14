package org.uet.dse.neo4j.sync.object;

import org.neo4j.driver.Record;
import org.uet.dse.neo4j.model.LinkState;
import org.uet.dse.neo4j.model.ObjectState;
import org.uet.dse.neo4j.sync.helper.CanonicalCollectionValueCodec;
import org.uet.dse.neo4j.sync.helper.CanonicalScalarValueCodec;

import java.util.*;
import java.util.stream.Collectors;

public class Neo4jObjectSnapshotMapper {

  public static ObjectState toObjectState(Record row) {
    ObjectState os = new ObjectState();
    os.name = row.get("name").asString();
    os.className = row.get("className").asString();

    List<Object> dataList = row.get("data").asList();

    Map<String, Map<List<Integer>, Object>> attrStructureHelper = new HashMap<>();

    for (Object d : dataList) {
      Map<String, Object> item = (Map<String, Object>) d;
      String attrName = (String) item.get("attr");
      if (attrName == null) continue;

      os.attributeTypes.putIfAbsent(attrName, (String) item.get("type"));

      boolean isObjRef = Boolean.TRUE.equals(item.get("isObjRef")) || Boolean.TRUE.equals(item.get("nestIsObjRef"));
      boolean isColl = Boolean.TRUE.equals(item.get("isColl"));
      Object leafVal = item.get("leafVal");
      String refId = (String) item.get("ref");

      List<Object> rawPath = (List<Object>) item.get("pathIdx");
      List<Integer> pathIdx = (rawPath == null) ? Collections.emptyList() :
          rawPath.stream().map(v -> ((Number) v).intValue()).collect(Collectors.toList());

      attrStructureHelper.putIfAbsent(attrName, new HashMap<>());

      if (isObjRef && refId != null) {
        attrStructureHelper.get(attrName).putIfAbsent(pathIdx, new TreeMap<Integer, String>());
        int itemIdx = item.get("idx") != null ? ((Number) item.get("idx")).intValue() : 0;
        ((TreeMap<Integer, String>) attrStructureHelper.get(attrName).get(pathIdx)).put(itemIdx, refId);
      }
      else if (!isObjRef && isColl && leafVal != null && !leafVal.toString().equals("NESTED_COLLECTION")) {
        attrStructureHelper.get(attrName).put(pathIdx,
            decodePrimitiveCollection(leafVal.toString(), (String) item.get("type")));
      }
      else if (!isObjRef && !isColl && leafVal != null
          && !leafVal.toString().equals("NESTED_COLLECTION")) {
        Object decoded = decodeScalarValue(leafVal, (String) item.get("type"));
        if (decoded != null) os.primitiveValues.putIfAbsent(attrName, decoded);
      }
    }

    attrStructureHelper.forEach((attrName, pathMap) -> {
      List<Object> deepList = reconstructDeepListGeneric(pathMap);

      boolean isActuallyRef = !isPrimitiveType(os.attributeTypes.get(attrName));

      if (isActuallyRef) {
        os.objectReferences.put(attrName, deepList);
      } else {
        if (!deepList.isEmpty()) {
          os.primitiveValues.put(attrName, deepList);
        }
      }
    });

    return os;
  }

  private static List<Object> reconstructDeepListGeneric(Map<List<Integer>, Object> pathMap) {
    if (pathMap.isEmpty()) return Collections.emptyList();

    if (pathMap.size() == 1 && pathMap.containsKey(Collections.emptyList())) {
      return finalizeCollectionItem(pathMap.get(Collections.emptyList()));
    }

    TreeMap<Integer, Object> tree = new TreeMap<>();
    for (var entry : pathMap.entrySet()) {
      List<Integer> path = entry.getKey();
      if (path.isEmpty()) continue;

      int groupIdx = path.get(0);
      tree.put(groupIdx, finalizeCollectionItem(entry.getValue()));
    }
    return new ArrayList<>(tree.values());
  }

  private static List<Object> finalizeCollectionItem(Object data) {
    if (data instanceof TreeMap) {
      return new ArrayList<>(((TreeMap<?, ?>) data).values());
    } else if (data instanceof List) {
      return (List<Object>) data;
    }
    return Collections.singletonList(data);
  }

  private static boolean isPrimitiveType(String type) {
    if (type == null) return false;
    return Arrays.asList("Int", "Integer", "Double", "Real", "Boolean", "String").contains(type)
        || type.startsWith("Set(") || type.startsWith("Sequence(");
  }

  static List<Object> decodePrimitiveCollection(String raw, String typeName) {
    if (raw == null || raw.equals("Undefined") || raw.equals("COLLECTION_DATA")) {
      return new ArrayList<>();
    }
    return new ArrayList<>(CanonicalCollectionValueCodec.decodeScalarLeaves(raw, typeName));
  }

  static Object decodeScalarValue(Object raw, String typeName) {
    if (raw == null || "Undefined".equals(raw)) return null;
    if (raw instanceof String payload && payload.startsWith("v1|")) {
      if (typeName == null || typeName.isBlank()) {
        throw new IllegalArgumentException("A typed canonical scalar payload is missing val.type");
      }
      return CanonicalScalarValueCodec.decode(payload, typeName);
    }
    return raw;
  }

  static LinkState toBinaryLink(Record rec) {
    LinkState ls = new LinkState();
    ls.assocName = rec.get("assocName").asString();
    if (rec.containsKey("label") && !rec.get("label").isNull()) {
      ls.edgeLabel = rec.get("label").asString();
    }
    ls.participants = Arrays.asList(rec.get("src").asString(), rec.get("tgt").asString());
    ls.qualifierValues = List.of(
        rec.get("sourceQualifiers").asList(v -> v.isNull() ? null : v.asString()),
        rec.get("targetQualifiers").asList(v -> v.isNull() ? null : v.asString())
    );
    return ls;
  }

  static LinkState toLinkObject(Record rec) {
    LinkState ls = new LinkState();
    ls.linkObjectName = rec.get("loName").asString();
    ls.assocName = rec.get("acName").asString();
    ls.edgeLabel = "LinkAssociateWith";

    List<Map<String, Object>> parts = rec.get("parts").asList(v -> v.asMap());

    List<String> sortedParticipants = parts.stream()
        .sorted((m1, m2) -> {
          Object idx1 = m1.get("idx");
          Object idx2 = m2.get("idx");
          int i1 = (idx1 instanceof Number) ? ((Number) idx1).intValue() : 0;
          int i2 = (idx2 instanceof Number) ? ((Number) idx2).intValue() : 0;
          return Integer.compare(i1, i2);
        })
        .map(m -> (String) m.get("p"))
        .filter(Objects::nonNull)
        .collect(Collectors.toList());

    ls.participants.addAll(sortedParticipants);
    return ls;
  }

}
