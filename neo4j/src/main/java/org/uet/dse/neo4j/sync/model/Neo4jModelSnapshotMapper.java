package org.uet.dse.neo4j.sync.model;

import org.neo4j.driver.Record;
import org.neo4j.driver.Value;
import org.uet.dse.neo4j.model.AssociationState;
import org.uet.dse.neo4j.model.ClassState;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

public class Neo4jModelSnapshotMapper {

    private Neo4jModelSnapshotMapper() {}

    static ClassState toClassState(Record row) {
        ClassState cls = new ClassState();
        cls.setName(row.get("className").asString());
        cls.setType(row.get("classType").asString());
        cls.setMetaNodeName(row.get("metaName").asString());

        if ("NodeEnumeration".equals(cls.getMetaNodeName())) {
            populateEnumLiterals(cls, row.get("enumValues"));
        }
        if ("NodeAssociationClass".equals(cls.getMetaNodeName())) {
            populateAssociationClassEnds(cls, row.get("acSrc"), row.get("acTgt"));
        }

        populateAttributes(cls, row.get("attrs"));
        populateInvariants(cls, row.get("invariants"));
        populateOperations(cls, row.get("ops"));

        return cls;
    }


    private static void populateEnumLiterals(ClassState cls, Value enumVals) {
        if (!enumVals.isNull()) {
            cls.setEnumLiterals(enumVals.asList(Value::asString));
        }
    }

    private static void populateAssociationClassEnds(ClassState cls, Value src, Value tgt) {
        if (!src.get("name").isNull()) {
            cls.setAcSource(
                    src.get("name").asString(),
                    src.get("role").asString(),
                    src.get("mult").asString()
            );
        }
        if (!tgt.get("name").isNull()) {
            cls.setAcTarget(
                    tgt.get("name").asString(),
                    tgt.get("role").asString(),
                    tgt.get("mult").asString()
            );
        }
    }

    private static void populateAttributes(ClassState cls, Value attrsVal) {
        if (attrsVal.isNull()) return;

        for (Value aVal : attrsVal.asList(v -> v)) {
            if (aVal.get("name").isNull()) continue;          // OPTIONAL MATCH miss
            String attrName = aVal.get("name").asString();
            cls.getAttributes().put(attrName, buildAttributeProps(attrName, aVal));
        }
    }

    private static Map<String, Object> buildAttributeProps(String attrName, Value aVal) {
        Map<String, Object> props = new HashMap<>();
        props.put("attrName",            attrName);
        props.put("name",                attrName);
        props.put("type",                resolveType(aVal.get("type").asString("String"), aVal.get("ref")));
        props.put("index",               aVal.get("idx").asInt(0));
        props.put("isCollection",        aVal.get("sCollection").asBoolean(false));
        props.put("collectionType",      aVal.get("collectionType").asString("Set"));
        props.put("isNestedCollection",  aVal.get("isNested").asBoolean(false));
        props.put("nestedChain",         resolveNestedChain(aVal.get("chain")));
        return props;
    }

    private static List<String> resolveNestedChain(Value chainVal) {
        return chainVal.isNull() ? Collections.emptyList() : chainVal.asList(Value::asString);
    }

    private static void populateInvariants(ClassState cls, Value invsVal) {
        if (invsVal.isNull()) return;

        for (Value invVal : invsVal.asList(v -> v)) {
            if (invVal.get("expr").isNull()) continue;
            Map<String, Object> invMap = new HashMap<>(invVal.asMap());
            invMap.put("exist", invVal.get("exist").asBoolean(false));
            cls.addInvariant(invMap);
        }
    }

    private static void populateOperations(ClassState cls, Value opsVal) {
        if (opsVal.isNull()) return;

        for (Value oVal : opsVal.asList(v -> v)) {
            if (oVal.get("name").isNull()) continue;
            String opName = oVal.get("name").asString();

            cls.getOperations().put(opName, buildOperationProps(oVal));
            populateParams(cls, opName, oVal.get("params"));
            populateConditions(cls, opName, oVal.get("pre"),  cls::addPreCondition);
            populateConditions(cls, opName, oVal.get("post"), cls::addPostCondition);
        }
    }

    private static Map<String, Object> buildOperationProps(Value oVal) {
        Map<String, Object> props = new HashMap<>();
        props.put("returnType", resolveType(oVal.get("ret").asString(), oVal.get("retRef")));
        props.put("body",       oVal.get("body").asString(null));
        props.put("isQuery",    oVal.get("isQuery").asBoolean(true));
        return props;
    }

    private static void populateParams(ClassState cls, String opName, Value paramsVal) {
        if (paramsVal.isNull()) return;

        for (Value pVal : paramsVal.asList(v -> v)) {
            if (pVal.get("pName").isNull()) continue;
            cls.addOperationParam(
                    opName,
                    pVal.get("pName").asString(),
                    resolveType(pVal.get("pType").asString(), pVal.get("pRef")),
                    pVal.get("pOrder").asInt(0)
            );
        }
    }

    private static void populateConditions(
            ClassState cls,
            String opName,
            Value conditionsVal,
            BiConsumer<String, Map<String, Object>> adder) {

        if (conditionsVal.isNull()) return;

        for (Value cVal : conditionsVal.asList(v -> v)) {
            if (!cVal.get("expr").isNull()) {
                adder.accept(opName, new HashMap<>(cVal.asMap()));
            }
        }
    }

    static AssociationState toBinaryAssociation(Record rec) {
        AssociationState as = new AssociationState();
        as.name    = rec.get("name").asString();
        as.type    = rec.get("type").asString();
        as.srcName = rec.get("src").asString();
        as.srcRole = rec.get("sRole").asString("");
        as.srcMult = rec.get("sMult").asString("1");
        as.tgtName = rec.get("tgt").asString();
        as.tgtRole = rec.get("tRole").asString("");
        as.tgtMult = rec.get("tMult").asString("*");
        return as;
    }

    static Map<String, Object> toTernaryAssociation(Record rec) {
        Map<String, Object> data = new HashMap<>();
        data.put("name",         rec.get("assocName").asString());
        data.put("participants", rec.get("participants").asList());
        return data;
    }

    /**
     * When the stored type is "Object", the real type name lives in a separate
     * reference node. Falls back to the raw type string otherwise.
     */
    private static String resolveType(String rawType, Value refNode) {
        return "Object".equals(rawType) ? refNode.asString("Object") : rawType;
    }
}
