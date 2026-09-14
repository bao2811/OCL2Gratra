package org.uet.dse.ocl2cypher.cypher;

import static scala.jdk.CollectionConverters.CollectionHasAsScala;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.neo4j.cypher.internal.CypherVersion;
import org.neo4j.cypher.internal.ast.Statement;
import org.neo4j.cypher.internal.ast.semantics.SemanticFeature;
import org.neo4j.cypher.internal.expressions.DynamicLabelExpression;
import org.neo4j.cypher.internal.expressions.DynamicLabelOrRelTypeExpression;
import org.neo4j.cypher.internal.expressions.DynamicRelTypeExpression;
import org.neo4j.cypher.internal.expressions.RelationshipPattern;
import org.neo4j.cypher.internal.parser.AstParserFactory;
import org.neo4j.cypher.internal.parser.AstParserFactory$;
import org.neo4j.cypher.internal.util.InputPosition;
import org.neo4j.cypher.internal.util.Neo4jCypherExceptionFactory;
import scala.Option;
import scala.Product;

/** Test-only bridge to Neo4j's actual Cypher 5 parser. */
public final class Neo4jCypherParserGate {
    private static final AstParserFactory CYPHER_5 =
            AstParserFactory$.MODULE$.apply(CypherVersion.Cypher5);
    private static final int MAX_DEPTH = 180;
    private static final int MAX_NODES = 500_000;

    private Neo4jCypherParserGate() {
    }

    /** Independent syntax acceptance only; no database access or semantic proof. */
    public static void assertParses(String text) {
        parse(text);
    }

    static Parsed parse(String text) {
        Preamble preamble = splitPreamble(text);
        List<SemanticFeature> features = List.of();
        Statement statement = CYPHER_5.apply(
                        preamble.statement(),
                        new Neo4jCypherExceptionFactory(preamble.statement(), Option.empty()),
                        Option.empty(),
                        CollectionHasAsScala(features).asScala().toSeq())
                .singleStatement();
        Projection projection = new Projection();
        String canonical = projection.render(statement, 0);
        return new Parsed(statement.getClass().getName(),
                Set.copyOf(projection.productNames), canonical, preamble.dialectHeader(),
                projection.hasUnboundedRelationship, projection.hasDynamicLabelOrType);
    }

    record Parsed(String rootClass, Set<String> productNames, String canonicalTree,
                  String dialectHeader, boolean hasUnboundedRelationship,
                  boolean hasDynamicLabelOrType) {
        boolean has(String productName) {
            return productNames.contains(productName);
        }
    }

    /**
     * Neo4j's pre-parser consumes {@code CYPHER 5} before AstParserFactory is
     * invoked.  Validate that layer explicitly and feed only the statement to
     * the real dialect parser.
     */
    private static Preamble splitPreamble(String text) {
        if (text.startsWith("CYPHER 5\r\n")) {
            return requireStatement("CYPHER 5", text.substring("CYPHER 5\r\n".length()));
        }
        if (text.startsWith("CYPHER 5\n")) {
            return requireStatement("CYPHER 5", text.substring("CYPHER 5\n".length()));
        }
        if (text.startsWith("CYPHER 5 ")) {
            return requireStatement("CYPHER 5", text.substring("CYPHER 5 ".length()));
        }
        if (text.startsWith("CYPHER")) {
            throw new IllegalArgumentException("unsupported or malformed Cypher pre-parser header");
        }
        return requireStatement(null, text);
    }

    private static Preamble requireStatement(String header, String statement) {
        if (statement.isBlank()) {
            throw new IllegalArgumentException("Cypher pre-parser header has no statement");
        }
        return new Preamble(header, statement);
    }

    private record Preamble(String dialectHeader, String statement) {
    }

    /**
     * A deterministic structural projection used only for test comparison.
     * Source positions and JVM identities are erased; constructors, field
     * order, names, literals and relationship directions remain observable.
     */
    private static final class Projection {
        private final Set<String> productNames = new LinkedHashSet<>();
        private final Set<Object> active = Collections.newSetFromMap(new IdentityHashMap<>());
        private int nodeCount;
        private boolean hasUnboundedRelationship;
        private boolean hasDynamicLabelOrType;

        private String render(Object value, int depth) {
            if (++nodeCount > MAX_NODES || depth > MAX_DEPTH) {
                throw new IllegalArgumentException("Neo4j AST projection limit exceeded");
            }
            if (value == null) return "null";
            if (value instanceof InputPosition) return "<position>";
            if (value instanceof String text) return quote(text);
            if (value instanceof Number || value instanceof Boolean || value instanceof Character) {
                return String.valueOf(value);
            }
            if (value.getClass().isEnum()) {
                return value.getClass().getSimpleName() + "." + value;
            }
            if (value instanceof scala.collection.Iterable<?> iterable) {
                List<String> items = new ArrayList<>();
                scala.collection.Iterator<?> iterator = iterable.iterator();
                while (iterator.hasNext()) items.add(render(iterator.next(), depth + 1));
                return "[" + String.join(",", items) + "]";
            }
            if (value instanceof java.lang.Iterable<?> iterable) {
                List<String> items = new ArrayList<>();
                for (Object item : iterable) items.add(render(item, depth + 1));
                return "[" + String.join(",", items) + "]";
            }
            if (value instanceof Product product) return renderProduct(product, depth);
            return "<" + value.getClass().getSimpleName() + ">";
        }

        private String renderProduct(Product product, int depth) {
            if (!active.add(product)) return "<cycle:" + product.productPrefix() + ">";
            try {
                if (product instanceof RelationshipPattern relationship
                        && relationship.length().nonEmpty() && !relationship.isBounded()) {
                    hasUnboundedRelationship = true;
                }
                if (product instanceof DynamicLabelOrRelTypeExpression
                        || product instanceof DynamicLabelExpression
                        || product instanceof DynamicRelTypeExpression) {
                    hasDynamicLabelOrType = true;
                }
                String prefix = product.productPrefix();
                productNames.add(prefix);
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

        private static String quote(String value) {
            return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
        }
    }
}
