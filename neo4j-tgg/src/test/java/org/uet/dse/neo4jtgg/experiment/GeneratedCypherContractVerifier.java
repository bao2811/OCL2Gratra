package org.uet.dse.neo4jtgg.experiment;

import org.uet.dse.neo4jtgg.ocl.OclMetamodelIndex;
import org.uet.dse.neo4jtgg.ocl.OclBottomToken;
import org.uet.dse.neo4jtgg.ocl.ir.OclCypherPlan;

import java.lang.reflect.RecordComponent;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Executable safety contract checked independently from the Cypher renderer. */
final class GeneratedCypherContractVerifier {
    private static final Pattern PARAMETER = Pattern.compile("\\$([A-Za-z_][A-Za-z0-9_]*)");

    private GeneratedCypherContractVerifier() {
    }

    static void verify(InstrumentedCompilationResult result, String cypher, Map<String, Object> parameters) {
        GeneratedCypherSyntaxTree.parse(cypher);
        assertTrue(cypher.contains("WHERE NOT coalesce("), "Missing validation-false wrapper");
        assertTrue(cypher.contains("RETURN DISTINCT self.use_id AS useId"), "Missing set-valued violation projection");
        assertTrue(cypher.contains("[:ObjectInstanceOf]"),
                "OCL object conformance must use ObjectInstanceOf");
        assertFalse(cypher.contains("[:InstanceOf]"),
                "Schema InstanceOf must not be used as OCL object conformance");
        assertFalse(cypher.matches("(?s).*<[A-Za-z_][A-Za-z0-9_]*>.*"), "Unexpanded placeholder");

        OclCypherPlan.InvariantPlan invariant = result.queryPlan();
        assertTrue(Pattern.compile("(?s)MATCH \\(self:Object \\{modelKey: \\$[^}]+}\\)"
                        + "-\\[:ObjectInstanceOf]->\\(cls:UmlClass "
                        + "\\{modelKey: \\$[^,}]+, classKey: \\$[^}]+}\\)")
                        .matcher(cypher).find(),
                "Context must use model-scoped Object/UmlClass nodes and exact classKey lookup");
        assertTrue(hasCanonicalParameter(parameters, "::class::" + invariant.contextClassName()),
                "Missing canonical context classKey parameter for " + invariant.contextClassName());

        Set<String> referenced = new HashSet<>();
        Matcher matcher = PARAMETER.matcher(cypher);
        while (matcher.find()) referenced.add(matcher.group(1));
        assertEquals(parameters.keySet(), referenced, "Parameter environment differs from rendered references");

        String plan = ResearchArtifactFingerprint.of(result.queryPlan());
        if (plan.contains("operator=String(and)")) {
            assertTrue(cypher.contains(" AND coalesce("), "AND semantics lost");
        }
        if (plan.contains("SetLiteralPlan") || plan.contains("operationName=String(union)")
                || plan.contains("operationName=String(intersection)")
                || plan.contains("operationName=String(asSet)")) {
            boolean hasBottom = parameters.values().stream().anyMatch(OclBottomToken::isToken);
            assertTrue(hasBottom, "Finite-set plan has no separated bottom token");
            assertTrue(cypher.contains("coalesce("), "Set equality/deduplication is not null-safe");
        }
        for (OclCypherPlan.AttributeAccessPlan access : collectPlans(
                result.queryPlan(), OclCypherPlan.AttributeAccessPlan.class)) {
            String owner = access.attribute().owner().name();
            assertTrue(cypher.contains("[:ObjectHasAttribute]"), "Attribute slot relationship missing");
            assertTrue(cypher.contains(".attributeKey = $"), "Exact attributeKey predicate missing");
            assertFalse(cypher.contains("ENDS WITH"), "Suffix attribute matching reintroduced");
            assertFalse(cypher.contains("val.name = $"), "Display name used as attribute identity");
            assertTrue(hasCanonicalParameter(parameters,
                            "::attribute::" + owner + "::" + access.attributeName()),
                    "Missing canonical attributeKey parameter for " + owner + "." + access.attributeName());
        }
        for (OclCypherPlan.NavigationAccessPlan access : collectNavigationAccesses(result.queryPlan())) {
            OclMetamodelIndex.NavigationInfo navigation = access.navigation();
            assertTrue(cypher.contains("r.associationKey = $"), "Navigation association key missing");
            assertTrue(cypher.contains("r.sourceRole = $"), "Navigation source role missing");
            assertTrue(cypher.contains("r.targetRole = $"), "Navigation target role missing");
            assertFalse(cypher.contains("r.associationName"), "Legacy associationName lookup reintroduced");
            assertFalse(cypher.contains("r.qualifierPayload"), "Direction-free qualifier payload reintroduced");
            assertFalse(cypher.contains("r.name = $"), "Display name used as association identity");
            assertTrue(hasCanonicalParameter(parameters,
                            "::association::" + navigation.associationName()),
                    "Missing canonical associationKey parameter for " + navigation.associationName());
            assertTrue(parameters.containsValue(navigation.sourceRoleName()),
                    "Missing exact source-role parameter " + navigation.sourceRoleName());
            assertTrue(parameters.containsValue(navigation.targetRoleName()),
                    "Missing exact target-role parameter " + navigation.targetRoleName());
            switch (navigation.direction()) {
                case OUTGOING -> assertTrue(cypher.contains(")-[r]->("), "Outgoing navigation was reversed");
                case INCOMING -> assertTrue(cypher.contains(")<-[r]-("), "Incoming navigation was reversed");
                case UNDIRECTED -> assertTrue(cypher.contains(")-[r]-("), "Undirected navigation was changed");
            }
            if (!access.qualifiers().isEmpty()) {
                String expectedQualifierProperty = switch (navigation.direction()) {
                    case OUTGOING, UNDIRECTED -> "r.sourceQualifiers[0]";
                    case INCOMING -> "r.targetQualifiers[0]";
                };
                assertTrue(cypher.contains(expectedQualifierProperty),
                        "Qualified navigation uses the wrong direction-specific payload: " + expectedQualifierProperty);
            }
        }
        for (OclCypherPlan.MethodCallPlan call : collectPlans(
                result.queryPlan(), OclCypherPlan.MethodCallPlan.class)) {
            if ("allInstances".equalsIgnoreCase(call.methodName())) {
                assertTrue(hasCanonicalParameter(parameters, "::class::" + call.source().type().typeName()),
                        "Missing canonical allInstances classKey parameter");
                assertTrue(Pattern.compile("(?s)COLLECT \\{ MATCH \\(obj\\d+:Object "
                                + "\\{modelKey: \\$[^}]+}\\)-\\[:ObjectInstanceOf\\]->"
                                + "\\(cls\\d+:UmlClass \\{modelKey: \\$[^,}]+, classKey: \\$[^}]+}\\) "
                                + "RETURN DISTINCT obj\\d+ \\}")
                                .matcher(cypher).find(),
                        "allInstances must use model-scoped canonical Object/UmlClass lookup with local DISTINCT");
            }
            if ("oclIsKindOf".equalsIgnoreCase(call.methodName())
                    || "oclAsType".equalsIgnoreCase(call.methodName())) {
                assertTrue(Pattern.compile("(?s)(?:EXISTS \\{|COLLECT \\{) WITH .*? AS "
                                + "(?:typeRecv|castRecv)\\d+Key WHERE (?:typeRecv|castRecv)\\d+Key IS NOT NULL "
                                + "MATCH \\((?:typeRecv|castRecv)\\d+:Object \\{modelKey: \\$[^,}]+, "
                                + "objectKey: (?:typeRecv|castRecv)\\d+Key}\\)"
                                + "-\\[:ObjectInstanceOf\\]->"
                                + "\\((?:typeCls|castCls)\\d+:UmlClass \\{modelKey: \\$[^,}]+, "
                                + "classKey: \\$[^}]+}\\)")
                                .matcher(cypher).find(),
                        "Type accessor must guard and model-scope canonical receiver/class identity");
                assertTrue(hasCanonicalParameter(parameters,
                                "::class::" + call.arguments().get(0).type().typeName()),
                        "Missing canonical type-operation classKey parameter");
            }
        }
    }

    private static boolean hasCanonicalParameter(Map<String, Object> parameters, String suffix) {
        return parameters.values().stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .anyMatch(value -> value.endsWith(suffix) && value.length() > suffix.length());
    }

    private static Set<OclCypherPlan.NavigationAccessPlan> collectNavigationAccesses(Object root) {
        return collectPlans(root, OclCypherPlan.NavigationAccessPlan.class);
    }

    private static <T> Set<T> collectPlans(Object root, Class<T> type) {
        Set<T> result = new HashSet<>();
        collect(root, type, result, java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>()));
        return result;
    }

    private static <T> void collect(Object value, Class<T> type, Set<T> result, Set<Object> seen) {
        if (value == null || value instanceof String || value instanceof Number
                || value instanceof Boolean || value instanceof Enum<?> || !seen.add(value)) return;
        if (type.isInstance(value)) result.add(type.cast(value));
        if (value instanceof Collection<?> collection) {
            collection.forEach(item -> collect(item, type, result, seen));
            return;
        }
        if (!value.getClass().isRecord()) return;
        for (RecordComponent component : value.getClass().getRecordComponents()) {
            try {
                collect(component.getAccessor().invoke(value), type, result, seen);
            } catch (ReflectiveOperationException ex) {
                throw new IllegalStateException(ex);
            }
        }
    }
}
