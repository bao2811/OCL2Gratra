package org.uet.dse.neo4j.repo.mapper;

import org.neo4j.driver.Record;
import org.neo4j.driver.Value;
import org.neo4j.driver.types.Node;
import org.uet.dse.neo4j.mm.core.common.NCollectionType;
import org.uet.dse.neo4j.mm.core.common.NType;
import org.uet.dse.neo4j.mm.object.node.AttributeInstance;
import org.uet.dse.neo4j.mm.object.node.DomainObject;

import java.util.ArrayList;
import java.util.List;

public class DomainObjectMapper {

    public DomainObject map(List<Record> records) {

        Node objectNode = records.get(0).get("n").asNode();

        String id = objectNode.get("use_id").asString();
        String className = extractClassName(objectNode);

        List<AttributeInstance> attributes = new ArrayList<>();

        for (Record record : records) {

            if (record.get("m").isNull()) continue;

            Node attrNode = record.get("m").asNode();

            attributes.add(mapAttribute(attrNode));
        }

        return DomainObject.builder()
                .id(id)
                .className(className)
                .attributes(attributes)
                .build();
    }

    private AttributeInstance mapAttribute(Node node) {

        String name = node.get("name").asString();
        String typeStr = node.get("type").asString("");
        boolean isCollection = node.get("isCollection").asBoolean(false);
        boolean isNested = node.get("isNestedCollection").asBoolean(false);
        String collectionType = node.get("collectionType").asString("");
        Value raw = node.get("value");

        String value = null;

        if (raw != null && !raw.isNull()) {

            switch (raw.type().name()) {

                case "STRING" -> value = raw.asString();

                case "INTEGER" -> value = String.valueOf(raw.asLong());

                case "FLOAT" -> value = String.valueOf(raw.asDouble());

                case "BOOLEAN" -> value = String.valueOf(raw.asBoolean());

                default -> value = raw.toString();
            }
        }


        return new AttributeInstance(
                name,
                NType.fromString(typeStr),
                isCollection,
                isNested,
                NCollectionType.fromString(collectionType),
                value,
                List.of()
        );
    }

    private String extractClassName(Node node) {
        return node.labels().iterator().hasNext()
                ? node.labels().iterator().next()
                : "Unknown";
    }
}
