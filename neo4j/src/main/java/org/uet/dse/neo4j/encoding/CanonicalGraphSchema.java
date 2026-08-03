package org.uet.dse.neo4j.encoding;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;

import java.util.List;

/** Installs the canonical lookup schema once per database and encoding version. */
public final class CanonicalGraphSchema {
    public static final String VERSION = "canonical-v7.1-binary-link-exactness";

    private static final List<String> SCHEMA_STATEMENTS = List.of(
            "CREATE CONSTRAINT canonical_model_name IF NOT EXISTS "
                    + "FOR (n:ManageModel) REQUIRE n.name IS UNIQUE",
            "CREATE CONSTRAINT canonical_object_key IF NOT EXISTS "
                    + "FOR (n:Object) REQUIRE n.objectKey IS UNIQUE",
            "CREATE INDEX canonical_object_model IF NOT EXISTS "
                    + "FOR (n:Object) ON (n.modelKey)",
            "CREATE INDEX canonical_attribute_key IF NOT EXISTS "
                    + "FOR (n:Attribute) ON (n.attributeKey)",
            "CREATE INDEX canonical_class_key IF NOT EXISTS "
                    + "FOR (n:UmlClass) ON (n.classKey)",
            "CREATE INDEX canonical_attribute_value_key IF NOT EXISTS "
                    + "FOR (n:AttributeValue) ON (n.attributeKey)",
            "CREATE CONSTRAINT canonical_attribute_slot_key IF NOT EXISTS "
                    + "FOR (n:AttributeValue) REQUIRE n.slotKey IS UNIQUE",
            "CREATE INDEX canonical_attribute_value_model IF NOT EXISTS "
                    + "FOR (n:AttributeValue) ON (n.modelKey)",
            "CREATE INDEX canonical_meta_uid IF NOT EXISTS "
                    + "FOR (n:MetaNode) ON (n.uid)",
            "CREATE INDEX canonical_association_identity IF NOT EXISTS "
                    + "FOR ()-[r:AssociateWith]-() ON (r.associationKey)",
            "CREATE INDEX canonical_aggregation_identity IF NOT EXISTS "
                    + "FOR ()-[r:Aggregates]-() ON (r.associationKey)",
            "CREATE INDEX canonical_composition_identity IF NOT EXISTS "
                    + "FOR ()-[r:ComposeOf]-() ON (r.associationKey)",
            "CREATE INDEX canonical_link_association IF NOT EXISTS "
                    + "FOR ()-[r:LinkAssociateWith]-() ON (r.associationKey)",
            "CREATE INDEX canonical_link_identity IF NOT EXISTS "
                    + "FOR ()-[r:LinkAssociateWith]-() ON (r.linkKey)",
            "CREATE INDEX canonical_aggregate_association IF NOT EXISTS "
                    + "FOR ()-[r:LinkAggregates]-() ON (r.associationKey)",
            "CREATE INDEX canonical_aggregate_link_identity IF NOT EXISTS "
                    + "FOR ()-[r:LinkAggregates]-() ON (r.linkKey)",
            "CREATE INDEX canonical_composition_association IF NOT EXISTS "
                    + "FOR ()-[r:LinkComposeOf]-() ON (r.associationKey)",
            "CREATE INDEX canonical_composition_link_identity IF NOT EXISTS "
                    + "FOR ()-[r:LinkComposeOf]-() ON (r.linkKey)"
    );

    private CanonicalGraphSchema() { }

    /** Immutable schema contract exposed for conformance auditing. */
    public static List<String> statements() {
        return SCHEMA_STATEMENTS;
    }

    public static void ensureInstalled(Driver driver, String database) {
        try (Session session = driver.session(SessionConfig.forDatabase(database))) {
            boolean current = session.run(
                    "MATCH (s:CanonicalEncodingSchema {id:'CURRENT', version:$version}) RETURN s",
                    java.util.Map.of("version", VERSION)).hasNext();
            if (current) return;

            replaceObjectKeyIndexesWithConstraint(session);
            migrateAttributeSlotKeys(session);
            migrateBinaryLinkKeys(session);
            assertWellFormedBinaryLinks(session);
            assertUniqueBinaryLinkKeys(session);
            for (String statement : SCHEMA_STATEMENTS) {
                session.run(statement).consume();
            }
            // One-time compatibility migration. Keeping this out of every M2
            // synchronization avoids a database-wide scan on each import.
            session.run("MATCH (legacy) WHERE legacy.classKey IS NOT NULL "
                    + "SET legacy:UmlClass").consume();
            session.run("MERGE (s:CanonicalEncodingSchema {id:'CURRENT'}) "
                            + "SET s.version=$version, s.installedAt=timestamp()",
                    java.util.Map.of("version", VERSION)).consume();
        }
    }

    private static void replaceObjectKeyIndexesWithConstraint(Session session) {
        List<String> indexes = session.run(
                        "SHOW INDEXES YIELD name, labelsOrTypes, properties, owningConstraint "
                                + "WHERE labelsOrTypes = ['Object'] AND properties = ['objectKey'] "
                                + "AND owningConstraint IS NULL RETURN name")
                .list(record -> record.get("name").asString());
        for (String index : indexes) {
            session.run("DROP INDEX `" + index.replace("`", "``") + "` IF EXISTS").consume();
        }
    }

    private static void migrateAttributeSlotKeys(Session session) {
        session.run("MATCH (o:Object)-[:ObjectHasAttribute]->(v:AttributeValue) "
                + "WHERE o.objectKey IS NOT NULL AND v.attributeKey IS NOT NULL "
                + "SET v.slotKey=o.objectKey+'::slot::'+v.attributeKey").consume();
    }

    private static void migrateBinaryLinkKeys(Session session) {
        session.run("MATCH (a:Object)-[r:LinkAssociateWith|LinkAggregates|LinkComposeOf]->(b:Object) "
                + "WHERE r.modelKey IS NOT NULL AND r.name IS NOT NULL "
                + "AND a.use_id IS NOT NULL AND b.use_id IS NOT NULL "
                + "WITH a,b,r,coalesce(r.sourceQualifiers,[]) AS sq,coalesce(r.targetQualifiers,[]) AS tq "
                + "SET r.sourceQualifiers=sq,r.targetQualifiers=tq,r.linkKey=r.modelKey "
                + "+'::link::association='+toString(size(r.name))+':'+r.name "
                + "+'::source='+toString(size(a.use_id))+':'+a.use_id "
                + "+'::target='+toString(size(b.use_id))+':'+b.use_id "
                + "+'::sourceQualifiers='+toString(size(sq))+':' "
                + "+reduce(encoded='',q IN sq|encoded+toString(size(q))+':'+q) "
                + "+'::targetQualifiers='+toString(size(tq))+':' "
                + "+reduce(encoded='',q IN tq|encoded+toString(size(q))+':'+q)").consume();
    }

    private static void assertUniqueBinaryLinkKeys(Session session) {
        boolean duplicate = session.run(
                "MATCH (:Object)-[r:LinkAssociateWith|LinkAggregates|LinkComposeOf]->(:Object) "
                        + "WHERE r.linkKey IS NOT NULL WITH r.linkKey AS key,count(r) AS copies "
                        + "WHERE copies > 1 RETURN key LIMIT 1").hasNext();
        if (duplicate) {
            throw new IllegalStateException(
                    "Canonical binary-link migration found duplicate semantic linkKey values");
        }
    }

    private static void assertWellFormedBinaryLinks(Session session) {
        boolean malformed = session.run(
                "MATCH (a:Object)-[r:LinkAssociateWith|LinkAggregates|LinkComposeOf]->(b:Object) "
                        + "WHERE r.modelKey IS NOT NULL AND (r.linkKey IS NULL "
                        + "OR r.associationKey IS NULL OR a.use_id IS NULL OR b.use_id IS NULL "
                        + "OR r.sourceRole IS NULL OR r.targetRole IS NULL "
                        + "OR r.sourceQualifiers IS NULL OR r.targetQualifiers IS NULL "
                        + "OR any(q IN r.sourceQualifiers WHERE q IS NULL) "
                        + "OR any(q IN r.targetQualifiers WHERE q IS NULL)) RETURN r LIMIT 1").hasNext();
        if (malformed) {
            throw new IllegalStateException(
                    "Canonical binary-link migration found a malformed model-scoped relationship");
        }
    }
}
