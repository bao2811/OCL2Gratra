package org.uet.dse.neo4j.sync.helper;

import java.util.*;
import java.util.stream.Collectors;
import org.uet.dse.neo4j.sync.helper.UmlTypeTranslator;

public class OclSerializer {
    public static String serialize(Object data) {
        if (data == null || "Undefined".equals(data)) return "Undefined";

        if (data instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) data;
            String type = (String) map.getOrDefault("collectionType", "Set");
            List<Object> items = (List<Object>) map.get("items");
            
            if (items == null || items.isEmpty()) return type + "{}";
            
            String joined = items.stream()
                    .map(OclSerializer::serialize) // Đệ quy
                    .collect(Collectors.joining(", "));
            
            return String.format("%s{%s}", type, joined);
        }

        return UmlTypeTranslator.toOclLiteral(data);
    }
}