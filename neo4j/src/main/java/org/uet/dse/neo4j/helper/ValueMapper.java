package org.uet.dse.neo4j.helper;

import org.tzi.use.uml.ocl.value.*;
import java.util.*;

public class ValueMapper {

    public static Object mapUseValue(Value value) {
        if (value == null || value.isUndefined()) return null;


        if (value instanceof org.tzi.use.uml.ocl.value.EnumValue) {
            String literal = ((org.tzi.use.uml.ocl.value.EnumValue) value).value();
            return "#" + literal;
        }

        if (value instanceof CollectionValue) {
            CollectionValue cv = (CollectionValue) value;
            Map<String, Object> collectionNode = new HashMap<>();

            String kind = "Set";
            if (cv.isSet()) kind = "Set";
            else if (cv.isSequence()) kind = "Sequence";
            else if (cv.isBag()) kind = "Bag";
            else if (cv.isOrderedSet()) kind = "OrderedSet";

            collectionNode.put("collectionType", kind);

            List<Object> items = new ArrayList<>();
            for (Value v : cv.collection()) {
                items.add(mapUseValue(v));
            }
            collectionNode.put("items", items);

            return collectionNode;
        }

        if (value instanceof ObjectValue) {
            return ((ObjectValue) value).value().name();
        }

        if (value instanceof IntegerValue) return ((IntegerValue) value).value();
        if (value instanceof RealValue) return ((RealValue) value).value();
        if (value instanceof BooleanValue) return ((BooleanValue) value).value();
        if (value instanceof StringValue) return ((StringValue) value).value();

        return value.toString();
    }
}