package org.uet.dse.neo4jtgg.ocl;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Reads the canonical-v1 M2 observation used by semantic binding from Neo4j. */
public final class Neo4jOclMetamodelSnapshotReader {
    private final Driver driver;
    private final String database;

    public Neo4jOclMetamodelSnapshotReader(Driver driver, String database) {
        this.driver = Objects.requireNonNull(driver);
        this.database = Objects.requireNonNull(database);
    }

    public OclMetamodelSnapshot read(String modelName) {
        String modelKey = modelName.trim();
        Map<String, Object> parameters = Map.of("modelKey", modelKey);
        try (Session session = driver.session(SessionConfig.forDatabase(database))) {
            List<OclMetamodelSnapshot.ClassDef> classes = session.run(
                    "MATCH (c:UmlClass {modelKey:$modelKey})-[:InstanceOf]->(m:MetaNode) "
                            + "WHERE m.name IN ['NodeConcreteClass','NodeAbstractClass'] "
                            + "RETURN c.name AS name, m.name='NodeAbstractClass' AS abstract",
                    parameters).list(record -> new OclMetamodelSnapshot.ClassDef(
                    record.get("name").asString(), record.get("abstract").asBoolean()));
            List<OclMetamodelSnapshot.AttributeDef> attributes = session.run(
                    "MATCH (c:UmlClass {modelKey:$modelKey})-[:HasAttribute]->"
                            + "(a:Attribute {modelKey:$modelKey}) "
                            + "RETURN c.name AS owner,a.attrName AS name,a.type AS type",
                    parameters).list(record -> new OclMetamodelSnapshot.AttributeDef(
                    record.get("owner").asString(), record.get("name").asString(), record.get("type").asString()));
            List<OclMetamodelSnapshot.GeneralizationDef> generalizations = session.run(
                    "MATCH (c:UmlClass {modelKey:$modelKey})-[:Extends]->"
                            + "(p:UmlClass {modelKey:$modelKey}) "
                            + "RETURN c.name AS child,p.name AS parent",
                    parameters).list(record -> new OclMetamodelSnapshot.GeneralizationDef(
                    record.get("child").asString(), record.get("parent").asString()));
            List<OclMetamodelSnapshot.AssociationDef> associations = session.run(
                    "MATCH (s:UmlClass {modelKey:$modelKey})"
                            + "-[r:AssociateWith|Aggregates|ComposeOf]->"
                            + "(t:UmlClass {modelKey:$modelKey}) "
                            + "WHERE r.associationKey IS NOT NULL "
                            + "RETURN r.associationName AS name,r.sourceClassName AS sourceClass,"
                            + "r.sourceClassrole AS sourceRole,r.sourceMultiplicity AS sourceMultiplicity,"
                            + "r.sourceKind AS sourceKind,coalesce(r.sourceOrdered,false) AS sourceOrdered,"
                            + "coalesce(r.sourceQualifierNames,[]) AS sourceQualifierNames,"
                            + "coalesce(r.sourceQualifierTypes,[]) AS sourceQualifierTypes,"
                            + "r.targetClassName AS targetClass,r.targerClassrole AS targetRole,"
                            + "r.targetMultiplicity AS targetMultiplicity,r.targetKind AS targetKind,"
                            + "coalesce(r.targetOrdered,false) AS targetOrdered,"
                            + "coalesce(r.targetQualifierNames,[]) AS targetQualifierNames,"
                            + "coalesce(r.targetQualifierTypes,[]) AS targetQualifierTypes",
                    parameters).list(this::association);
            if (classes.isEmpty()) {
                throw new IllegalStateException("No canonical M2 classes found for model " + modelName);
            }
            return new OclMetamodelSnapshot(modelName, classes, attributes, generalizations, associations);
        }
    }

    private OclMetamodelSnapshot.AssociationDef association(Record record) {
        return new OclMetamodelSnapshot.AssociationDef(record.get("name").asString(),
                end(record, "source"), end(record, "target"));
    }

    private OclMetamodelSnapshot.EndDef end(Record record, String prefix) {
        List<String> names = record.get(prefix + "QualifierNames").asList(value -> value.asString());
        List<String> types = record.get(prefix + "QualifierTypes").asList(value -> value.asString());
        if (names.size() != types.size()) {
            throw new IllegalStateException("Qualifier metadata arity mismatch for " + record.get("name"));
        }
        List<OclMetamodelSnapshot.QualifierDef> qualifiers = new ArrayList<>();
        for (int index = 0; index < names.size(); index++) {
            qualifiers.add(new OclMetamodelSnapshot.QualifierDef(names.get(index), types.get(index)));
        }
        return new OclMetamodelSnapshot.EndDef(
                record.get(prefix + "Class").asString(), record.get(prefix + "Role").asString(),
                record.get(prefix + "Multiplicity").asString(), record.get(prefix + "Kind").asInt(),
                record.get(prefix + "Ordered").asBoolean(), qualifiers);
    }
}
