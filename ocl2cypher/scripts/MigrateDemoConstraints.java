import java.nio.file.*;
import java.util.*;
import org.neo4j.driver.*;

/** Explicit, demo-only schema migration. Never deletes graph data. */
public class MigrateDemoConstraints {
    record Key(String oldName, String label, String property) {
        String newName() { return oldName + "_per_model"; }
        String create() {
            return "CREATE CONSTRAINT `" + newName() + "` IF NOT EXISTS FOR (n:`" + label
                + "`) REQUIRE (n.modelKey, n.`" + property + "`) IS UNIQUE";
        }
    }
    static final List<Key> KEYS = List.of(
        new Key("canonical_class_key", "UmlClass", "classKey"),
        new Key("canonical_attribute_key", "Attribute", "attributeKey"),
        new Key("canonical_attribute_slot_key", "AttributeValue", "slotKey"),
        new Key("canonical_object_key", "Object", "objectKey"));
    static List<Map<String, Object>> constraints(Session s) {
        return s.run("SHOW CONSTRAINTS YIELD name, type, entityType, labelsOrTypes, properties RETURN *")
            .list(r -> r.asMap());
    }
    static boolean matches(Map<String, Object> c, Key k, boolean scoped) {
        return "NODE".equals(c.get("entityType"))
            && Set.of("UNIQUENESS", "NODE_PROPERTY_UNIQUENESS").contains(c.get("type"))
            && List.of(k.label()).equals(c.get("labelsOrTypes"))
            && (scoped ? List.of("modelKey", k.property()) : List.of(k.property())).equals(c.get("properties"));
    }
    public static void main(String[] args) throws Exception {
        if (!"demo".equals(System.getenv("NEO4J_DB"))) throw new IllegalStateException("Only database demo is authorized");
        boolean apply = args.length == 1 && "--apply".equals(args[0]);
        try (Driver d = GraphDatabase.driver(System.getenv("NEO4J_URI"),
                AuthTokens.basic(System.getenv("NEO4J_USER"), System.getenv("NEO4J_PASSWORD")));
             Session s = d.session(SessionConfig.forDatabase("demo"))) {
            var before = constraints(s);
            System.out.println("Database: demo; constraints: " + before);
            for (Key k : KEYS) {
                for (var c : before) {
                    String name = (String)c.get("name");
                    if ((k.oldName().equals(name) && !matches(c, k, false))
                        || (k.newName().equals(name) && !matches(c, k, true)))
                        throw new IllegalStateException("Unexpected constraint definition: " + name);
                    if (matches(c, k, false) && !k.oldName().equals(name))
                        throw new IllegalStateException("Unexpected global constraint: " + name);
                }
                long missing = s.run("MATCH (n:`" + k.label() + "`) WHERE n.modelKey IS NULL OR n.`"
                    + k.property() + "` IS NULL RETURN count(n) AS c").single().get("c").asLong();
                long duplicates = s.run("MATCH (n:`" + k.label() + "`) WITH n.modelKey AS m, n.`"
                    + k.property() + "` AS k, count(*) AS c WHERE c > 1 RETURN count(*) AS c").single().get("c").asLong();
                if (missing != 0 || duplicates != 0)
                    throw new IllegalStateException("Unsafe data for " + k.label() + ": missing=" + missing + ", duplicate groups=" + duplicates);
            }
            if (!apply) { System.out.println("Preflight PASS; no changes made."); return; }
            Path evidence = Path.of("ocl2cypher", "target", "constraint-migrations", UUID.randomUUID().toString());
            Files.createDirectories(evidence);
            Files.writeString(evidence.resolve("before.txt"), before.toString());
            // Separate committed schema statements: retain global protection until all replacements exist.
            for (Key k : KEYS) s.run(k.create()).consume();
            var created = constraints(s);
            for (Key k : KEYS)
                if (created.stream().noneMatch(c -> k.newName().equals(c.get("name")) && matches(c, k, true)))
                    throw new IllegalStateException("Replacement not verified: " + k.newName());
            for (Key k : KEYS)
                if (before.stream().anyMatch(c -> k.oldName().equals(c.get("name"))))
                    s.run("DROP CONSTRAINT `" + k.oldName() + "`").consume();
            var after = constraints(s);
            Files.writeString(evidence.resolve("after.txt"), after.toString());
            for (Key k : KEYS)
                if (after.stream().anyMatch(c -> matches(c, k, false))
                    || after.stream().noneMatch(c -> k.newName().equals(c.get("name")) && matches(c, k, true)))
                    throw new IllegalStateException("Postcondition failed: " + k.label());
            System.out.println("Migration PASS; graph data retained. Evidence: " + evidence);
        }
    }
}
