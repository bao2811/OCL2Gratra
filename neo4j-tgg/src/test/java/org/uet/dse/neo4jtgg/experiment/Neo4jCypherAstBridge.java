package org.uet.dse.neo4jtgg.experiment;

import org.neo4j.cypher.internal.CypherVersion;
import org.neo4j.cypher.internal.ast.Statement;
import org.neo4j.cypher.internal.ast.semantics.SemanticFeature;
import org.neo4j.cypher.internal.parser.AstParserFactory;
import org.neo4j.cypher.internal.parser.AstParserFactory$;
import org.neo4j.cypher.internal.util.InputPosition;
import org.neo4j.cypher.internal.util.Neo4jCypherExceptionFactory;
import scala.Option;
import scala.Product;
import scala.collection.Iterator;
import scala.collection.immutable.Seq;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static scala.jdk.CollectionConverters.CollectionHasAsScala;

/**
 * Test-only bridge to Neo4j's real Cypher 5 parser and internal AST.
 *
 * <p>The production compiler deliberately remains
 * {@code OclCypherPlan -> OclCypherRenderer -> Cypher text}.  This bridge is
 * only an independent acceptance/agreement oracle for the generated text.
 * Its projection ignores source positions and object identities, but preserves
 * AST constructor order, field names, schema/property/function names,
 * parameters, literals, directions, and aliases.</p>
 */
final class Neo4jCypherAstBridge {
    private static final AstParserFactory CYPHER_5 =
            AstParserFactory$.MODULE$.apply(CypherVersion.Cypher5);
    private static final int MAX_DEPTH = 180;
    private static final int MAX_NODES = 100_000;

    private Neo4jCypherAstBridge() {
    }

    static Observation parse(String cypher) {
        List<SemanticFeature> semanticFeatures = List.of();
        Statement statement = CYPHER_5.apply(
                cypher,
                new Neo4jCypherExceptionFactory(cypher, Option.empty()),
                Option.empty(),
                CollectionHasAsScala(semanticFeatures).asScala().toSeq())
                .singleStatement();
        Projection projection = new Projection();
        String tree = projection.render(statement, 0);
        return new Observation(statement.getClass().getName(), Set.copyOf(projection.productNames),
                Set.copyOf(projection.typeNames), Map.copyOf(projection.productCounts),
                tree, sha256(tree));
    }

    static String parserArtifact() {
        try {
            return Path.of(AstParserFactory$.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                    .getFileName().toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot identify loaded Neo4j parser artifact", ex);
        }
    }

    record Observation(String rootClass, Set<String> productNames, Set<String> typeNames,
                       Map<String, Integer> productCounts, String canonicalTree, String sha256) {
        boolean hasAnyType(String... names) {
            for (String name : names) if (typeNames.contains(name)) return true;
            return false;
        }

        int count(String productName) {
            return productCounts.getOrDefault(productName, 0);
        }
    }

    private static final class Projection {
        private final Set<String> productNames = new LinkedHashSet<>();
        private final Set<String> typeNames = new LinkedHashSet<>();
        private final Map<String, Integer> productCounts = new LinkedHashMap<>();
        private final Set<Object> active = Collections.newSetFromMap(new IdentityHashMap<>());
        private int nodes;

        private String render(Object value, int depth) {
            if (++nodes > MAX_NODES) throw new IllegalArgumentException("Neo4j AST exceeds projection node limit");
            if (depth > MAX_DEPTH) throw new IllegalArgumentException("Neo4j AST exceeds projection depth limit");
            if (value == null) return "null";
            if (value instanceof InputPosition) return "<position>";
            if (value instanceof String text) return quote(text);
            if (value instanceof Number || value instanceof Boolean || value instanceof Character)
                return String.valueOf(value);
            if (value.getClass().isEnum()) return value.getClass().getSimpleName() + "." + value;
            if (value instanceof scala.collection.Iterable<?> iterable)
                return renderScalaIterable(iterable, depth);
            if (value instanceof java.lang.Iterable<?> iterable)
                return renderJavaIterable(iterable, depth);
            if (value instanceof Product product) return renderProduct(product, depth);
            registerTypes(value.getClass());
            return "<" + value.getClass().getSimpleName() + ">";
        }

        private String renderScalaIterable(scala.collection.Iterable<?> values, int depth) {
            List<String> items = new ArrayList<>();
            Iterator<?> iterator = values.iterator();
            while (iterator.hasNext()) items.add(render(iterator.next(), depth + 1));
            return "[" + String.join(",", items) + "]";
        }

        private String renderJavaIterable(java.lang.Iterable<?> values, int depth) {
            List<String> items = new ArrayList<>();
            for (Object item : values) items.add(render(item, depth + 1));
            return "[" + String.join(",", items) + "]";
        }

        private String renderProduct(Product product, int depth) {
            if (!active.add(product)) return "<cycle:" + product.productPrefix() + ">";
            try {
                String prefix = product.productPrefix();
                productNames.add(prefix);
                productCounts.merge(prefix, 1, Integer::sum);
                registerTypes(product.getClass());
                List<String> fields = new ArrayList<>(product.productArity());
                for (int index = 0; index < product.productArity(); index++) {
                    fields.add(product.productElementName(index) + "="
                            + render(product.productElement(index), depth + 1));
                }
                return prefix + "(" + String.join(",", fields) + ")";
            } finally {
                active.remove(product);
            }
        }

        private void registerTypes(Class<?> type) {
            if (type == null || type == Object.class) return;
            if (!typeNames.add(type.getSimpleName())) return;
            registerTypes(type.getSuperclass());
            for (Class<?> contract : type.getInterfaces()) registerTypes(contract);
        }
    }

    private static String quote(String value) {
        StringBuilder result = new StringBuilder(value.length() + 2).append('"');
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            switch (current) {
                case '\\' -> result.append("\\\\");
                case '"' -> result.append("\\\"");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> result.append(current);
            }
        }
        return result.append('"').toString();
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) result.append(String.format("%02x", item));
            return result.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
