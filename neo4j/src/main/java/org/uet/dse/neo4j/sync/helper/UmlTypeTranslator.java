package org.uet.dse.neo4j.sync.helper;

import org.tzi.use.uml.ocl.type.CollectionType;
import org.tzi.use.uml.ocl.type.Type;
import org.tzi.use.uml.ocl.type.Type.VoidHandling;
import java.util.List;
import java.util.Map;

public class UmlTypeTranslator {

    public static String toTypedOclLiteral(Object value, Type type) {
        if (value == null ||
            value.toString().equalsIgnoreCase("null") ||
            value.toString().equals("Undefined")) {
            return "Undefined";
        }

        if (value.toString().equals("COLLECTION_EMPTY")) {
            return getCollectionType(type) + "{}";
        }

        if (type.isKindOfCollection(VoidHandling.EXCLUDE_VOID)) {
            if (!(value instanceof List)) {
                List<String> decoded = decodePipedStringToList(value.toString());
                if (decoded.isEmpty()) return "Undefined";
                value = decoded;
            }

            List<?> list = (List<?>) value;
            if (list.isEmpty()) return "Undefined";

            Type elemType = ((CollectionType) type).elemType();
            String header = getCollectionType(type);
            StringBuilder sb = new StringBuilder(header).append("{");
            for (int i = 0; i < list.size(); i++) {
                sb.append(toTypedOclLiteral(list.get(i), elemType));
                if (i < list.size() - 1) sb.append(", ");
            }
            return sb.append("}").toString();
        }

        String rawString = value.toString().replace("'", "").trim();

        if (type.isTypeOfBoolean()) {
            return rawString.toLowerCase().equals("true") ? "true" : "false";
        }

        if (type.isTypeOfEnum()) {
            String enumLiteral = rawString.replace("#", "");
            return type.shortName() + "::" + enumLiteral;
        }

        if (type.isKindOfClass(VoidHandling.EXCLUDE_VOID)) {
            return rawString;
        }

        if (type.isTypeOfInteger()) {
            return rawString.replaceAll("[^0-9\\-]", "");
        }

        if (type.isTypeOfReal()) {
            String s = rawString.replaceAll("[^0-9.\\-]", "");
            return s.contains(".") ? s : s + ".0";
        }

        return "'" + rawString + "'";
    }

    private static List<String> decodePipedStringToList(String raw) {
        if (raw == null || raw.trim().isEmpty() ||
            raw.equals("COLLECTION_EMPTY") ||
            raw.equals("COLLECTION_DATA") ||
            raw.equals("Undefined")) {
            return java.util.Collections.emptyList();
        }

        return java.util.Arrays.stream(raw.split("\\s*\\|\\s*"))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(java.util.stream.Collectors.toList());
    }
    public static String toOclLiteral(Object value) {
        return toTypedOclLiteral(value, org.tzi.use.uml.ocl.type.TypeFactory.mkString());
    }

    public static String toDatabaseType(Type type) {
        if (type == null || type.isTypeOfVoidType()) return "Void";

        if (type.isKindOfCollection(VoidHandling.EXCLUDE_VOID) && type instanceof CollectionType) {
            return toDatabaseType(((CollectionType) type).elemType());
        }

        if (type.isTypeOfInteger()) return "Int";
        if (type.isTypeOfReal())    return "Double";
        if (type.isTypeOfString())  return "String";
        if (type.isTypeOfBoolean()) return "Boolean";

        return type.shortName();
    }

    public static String toUmlTypeString(String dbType) {
        if (dbType == null || dbType.equalsIgnoreCase("Void")) {
            // TRẢ VỀ NULL: Trong USE API, để tạo hàm Void, tham số returnType phải truyền vào null
            return null;
        }

        switch (dbType) {
            case "Int":    return "Integer";
            case "Double": return "Real";
            case "String": return "String";
            case "Boolean": return "Boolean";
            default:       return dbType;
        }
    }


    public static String reconstructFullTypeString(Map<String, Object> props) {
        Object typeObj = props.get("type");
        if (typeObj == null) return "String";

        String umlBase = toUmlTypeString(typeObj.toString());
        if (umlBase == null) return null;

        boolean isColl = (boolean) props.getOrDefault("isCollection", false);
        if (!isColl) return umlBase;

        boolean isNested = (boolean) props.getOrDefault("isNestedCollection", false);
        String rootCollType = (String) props.getOrDefault("collectionType", "Set");

        if (isNested) {
            Object chainObj = props.get("nestedChain");
            if (chainObj instanceof List) {
                List<String> chain = (List<String>) chainObj;

                // Bắt đầu từ lõi: Integer
                String result = umlBase;

                // Duyệt ngược từ phần tử cuối cùng của xích (lớp lồng sâu nhất) về đầu
                // Ví dụ: Set(Sequence(Bag(Integer)))
                // chain sẽ là ["Sequence", "Bag"]
                // Vòng 1: Bag(Integer)
                // Vòng 2: Sequence(Bag(Integer))
                for (int i = chain.size() - 1; i >= 0; i--) {
                    String collName = chain.get(i);
                    if (collName != null && !collName.isEmpty()) {
                        result = String.format("%s(%s)", collName, result);
                    }
                }

                // Cuối cùng bọc bởi lớp ngoài cùng (rootCollType)
                return String.format("%s(%s)", rootCollType, result);
            }
        }

        return String.format("%s(%s)", rootCollType, umlBase);
    }

    public static String getCollectionType(Type type) {
        if (type == null || !type.isKindOfCollection(VoidHandling.EXCLUDE_VOID)) {
            return "None";
        }
        if (type.isKindOfOrderedSet(VoidHandling.EXCLUDE_VOID)) return "OrderedSet";
        if (type.isKindOfSet(VoidHandling.EXCLUDE_VOID)) return "Set";
        if (type.isKindOfSequence(VoidHandling.EXCLUDE_VOID)) return "Sequence";
        if (type.isKindOfBag(VoidHandling.EXCLUDE_VOID)) return "Bag";
        return "Set";
    }


    public static Type getUltimateBaseType(Type type) {
        if (type.isKindOfCollection(VoidHandling.EXCLUDE_VOID) && type instanceof CollectionType) {
            return getUltimateBaseType(((CollectionType) type).elemType());
        }
        return type;
    }

    public static int getCollectionDepth(Type type) {
        int depth = 0;
        Type current = type;
        while (current.isKindOfCollection(VoidHandling.EXCLUDE_VOID) && current instanceof CollectionType) {
            depth++;
            current = ((CollectionType) current).elemType();
        }
        return depth;
    }
}