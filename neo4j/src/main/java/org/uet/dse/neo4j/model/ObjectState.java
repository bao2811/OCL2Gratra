package org.uet.dse.neo4j.model;

import java.util.*;

public class ObjectState {
    public String name;
    public String className;

    public Map<String, Object> primitiveValues = new HashMap<>();

    public Map<String, String> attributeTypes = new HashMap<>();

    public Map<String, List<Object>> objectReferences = new HashMap<>();

    private Map<String, Object> rawData = new HashMap<>();

    public Map<String, Object> getRawData() {
        return this.rawData;
    }

    public void setRawData(Map<String, Object> rawData) {
        this.rawData = rawData;
    }

    public void setRawValue(String attrName, Object value) {
        this.rawData.put(attrName, value);
    }

    public Object getRawValue(String attrName) {
        return this.rawData.get(attrName);
    }

    public boolean isSameAs(ObjectState other) {
        if (other == null) return false;
        if (!Objects.equals(this.className, other.className)) return false;

        // So sánh Primitive (đã bao gồm List nguyên thủy)
        if (!deepCompareMaps(this.primitiveValues, other.primitiveValues)) return false;

        // So sánh Reference (Map<String, List<String>>)
        if (!deepCompareMaps(this.objectReferences, other.objectReferences)) return false;

        return true;
    }

    public boolean deepCompareMaps(Map<String, ?> map1, Map<String, ?> map2) {
        Set<String> allKeys = new HashSet<>(map1.keySet());
        allKeys.addAll(map2.keySet());

        for (String key : allKeys) {
            if (!isEqualValue(map1.get(key), map2.get(key))) return false;
        }
        return true;
    }

    public boolean isEqualValue(Object v1, Object v2) {
        if (v1 == null && v2 == null) return true;
        if (v1 == null || v2 == null) return false;

        // 1. So sánh List (Collection)
        if (v1 instanceof List && v2 instanceof List) {
            List<?> list1 = (List<?>) v1;
            List<?> list2 = (List<?>) v2;
            if (list1.size() != list2.size()) return false;
            for (int i = 0; i < list1.size(); i++) {
                if (!isEqualValue(list1.get(i), list2.get(i))) return false;
            }
            return true;
        }

        // 2. So sánh Số (Double vs Long/Integer)
        if (v1 instanceof Number && v2 instanceof Number) {
            return Double.compare(((Number) v1).doubleValue(), ((Number) v2).doubleValue()) == 0;
        }

        // 3. So sánh các kiểu khác qua String (Enum, Boolean, String)
        String s1 = v1.toString().trim();
        String s2 = v2.toString().trim();
        return s1.equalsIgnoreCase(s2);
    }





    private boolean comparePrimitiveMaps(Map<String, Object> map1, Map<String, Object> map2) {
        if (map1.size() != map2.size()) return false;
        for (String key : map1.keySet()) {
            Object v1 = map1.get(key);
            Object v2 = map2.get(key);
            // So sánh toString để tránh lệch kiểu dữ liệu Long/Integer từ Neo4j
            String s1 = (v1 == null) ? "null" : v1.toString();
            String s2 = (v2 == null) ? "null" : v2.toString();
            if (!s1.equals(s2)) return false;
        }
        return true;
    }
}