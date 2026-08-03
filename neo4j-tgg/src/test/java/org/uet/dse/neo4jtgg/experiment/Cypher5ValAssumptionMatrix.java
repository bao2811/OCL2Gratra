package org.uet.dse.neo4jtgg.experiment;

import org.neo4j.driver.Record;
import org.uet.dse.neo4jtgg.ocl.OclBottomToken;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** One executable, selected-runtime probe for each trusted CY1--CY9 assumption. */
final class Cypher5ValAssumptionMatrix {
    static final String NEO4J_KERNEL = "2026.06.0";
    static final String CYPHER_COMPONENT = "5";
    static final String EDITION = "enterprise";
    static final String DATABASE = "demo";
    static final String JAVA_DRIVER_DEPENDENCY = "5.21.0";
    static final String ENCODING_PROFILE = "canonical-v1";

    static final String FIXTURE_SETUP = """
            CYPHER 5
            CREATE (a:Cypher5ValProbe {
              probeRun:$probeRun, probeKey:'a', objectKey:$objectKey, scalar:$integerValue,
              realValue:$realValue, textValue:$textValue, booleanValue:$booleanValue
            })
            CREATE (b:Cypher5ValProbe {probeRun:$probeRun, probeKey:'b'})
            CREATE (a)-[:LinkProbe {
              associationKey:$associationKey, sourceRole:'source', targetRole:'target',
              sourceQualifiers:$sourceQualifiers, targetQualifiers:$targetQualifiers
            }]->(b)
            CREATE (a)-[:LinkProbe {
              associationKey:$associationKey, sourceRole:'source', targetRole:'target',
              sourceQualifiers:$sourceQualifiers, targetQualifiers:$targetQualifiers
            }]->(b)
            """;

    private Cypher5ValAssumptionMatrix() {
    }

    static List<Probe> probes() {
        return List.of(
                new Probe("CY1", "Representation/lookup",
                        "scalar/object-id parameter/property lookup, missing property, and non-null bottom representation",
                        "integerOk=true;realOk=true;textOk=true;booleanOk=true;objectIdOk=true;missingIsNull=true;bottomOk=true",
                        """
                                CYPHER 5
                                MATCH (a:Cypher5ValProbe {probeRun:$probeRun, probeKey:'a'})
                                RETURN $integerValue = a.scalar AS integerOk,
                                       abs($realValue - a.realValue) < 0.000000001 AS realOk,
                                       $textValue = a.textValue AS textOk,
                                       $booleanValue = a.booleanValue AS booleanOk,
                                       $objectKey = a.objectKey AS objectIdOk,
                                       a.missingProperty IS NULL AS missingIsNull,
                                       coalesce(null, $bottom) = $bottom AS bottomOk
                                """,
                        rows -> allTrue(single(rows), "integerOk", "realOk", "textOk", "booleanOk",
                                "objectIdOk", "missingIsNull", "bottomOk")),
                new Probe("CY2", "Pattern",
                        "two forward/reverse paths and one DISTINCT target under exact metadata/qualifiers",
                        "forward=2/1;reverse=2/1",
                        """
                                CYPHER 5
                                MATCH (a:Cypher5ValProbe {probeRun:$probeRun, probeKey:'a'})-[r]->
                                      (b:Cypher5ValProbe {probeRun:$probeRun, probeKey:'b'})
                                WHERE type(r) STARTS WITH 'Link'
                                  AND r.associationKey = $associationKey
                                  AND r.sourceRole = 'source' AND r.targetRole = 'target'
                                  AND r.sourceQualifiers = $sourceQualifiers
                                  AND r.targetQualifiers = $targetQualifiers
                                WITH count(r) AS forwardPaths, count(DISTINCT b) AS forwardTargets
                                MATCH (b:Cypher5ValProbe {probeRun:$probeRun, probeKey:'b'})<-[rr]-
                                      (a:Cypher5ValProbe {probeRun:$probeRun, probeKey:'a'})
                                WHERE type(rr) STARTS WITH 'Link'
                                  AND rr.associationKey = $associationKey
                                  AND rr.sourceRole = 'source' AND rr.targetRole = 'target'
                                  AND rr.sourceQualifiers = $sourceQualifiers
                                  AND rr.targetQualifiers = $targetQualifiers
                                RETURN forwardPaths, forwardTargets,
                                       count(rr) AS reversePaths, count(DISTINCT a) AS reverseTargets
                                """,
                        rows -> {
                            Record row = single(rows);
                            assertLong(row, "forwardPaths", 2L);
                            assertLong(row, "forwardTargets", 1L);
                            assertLong(row, "reversePaths", 2L);
                            assertLong(row, "reverseTargets", 1L);
                            return "forward=2/1;reverse=2/1";
                        }),
                new Probe("CY3", "Filter",
                        "WHERE keeps true only and removes the represented-bottom entity arm before MATCH",
                        "trueRows=1;guardedEntityRows=0",
                        """
                                CYPHER 5
                                RETURN COUNT {
                                  UNWIND [true, false, null] AS predicate
                                  WITH predicate WHERE predicate
                                  RETURN predicate
                                } AS trueRows,
                                COUNT {
                                  WITH $bottom AS receiver
                                  WITH receiver WHERE receiver <> $bottom
                                  MATCH (n:Cypher5ValProbe {probeRun:$probeRun})
                                  RETURN n
                                } AS guardedEntityRows
                                """,
                        rows -> {
                            Record row = single(rows);
                            assertLong(row, "trueRows", 1L);
                            assertLong(row, "guardedEntityRows", 0L);
                            return "trueRows=1;guardedEntityRows=0";
                        }),
                new Probe("CY4", "Existential",
                        "correlated EXISTS is true exactly for a non-empty witness query",
                        "nonEmpty=true;empty=false",
                        """
                                CYPHER 5
                                WITH 1 AS outerValue
                                RETURN EXISTS {
                                  WITH outerValue WHERE outerValue = 1 RETURN 1 AS witness
                                } AS nonEmpty,
                                EXISTS {
                                  WITH outerValue WHERE outerValue = 2 RETURN 1 AS witness
                                } AS empty
                                """,
                        rows -> {
                            Record row = single(rows);
                            assertTrue(row.get("nonEmpty").asBoolean());
                            assertFalse(row.get("empty").asBoolean());
                            return "nonEmpty=true;empty=false";
                        }),
                new Probe("CY5", "Aggregation",
                        "COUNT row/non-null/distinct behavior and represented-bottom finite-set cardinality",
                        "rows=4;nonNull=3;distinct=2;semanticSet=3",
                        """
                                CYPHER 5
                                UNWIND [1, 1, 2, null] AS x
                                WITH count(*) AS rows, count(x) AS nonNull, count(DISTINCT x) AS distinctValues
                                RETURN rows, nonNull, distinctValues,
                                COUNT {
                                  UNWIND [1, 1, 2, null] AS y
                                  WITH DISTINCT coalesce(y, $bottom) AS represented
                                  RETURN represented
                                } AS semanticSetCount
                                """,
                        rows -> {
                            Record row = single(rows);
                            assertLong(row, "rows", 4L);
                            assertLong(row, "nonNull", 3L);
                            assertLong(row, "distinctValues", 2L);
                            assertLong(row, "semanticSetCount", 3L);
                            return "rows=4;nonNull=3;distinct=2;semanticSet=3";
                        }),
                new Probe("CY6", "Projection",
                        "WITH preserves named live aliases and RETURN DISTINCT quotients duplicate rows",
                        "[(1,11),(2,12)]",
                        """
                                CYPHER 5
                                UNWIND [1, 1, 2] AS x
                                WITH x, x + 10 AS liveAlias
                                RETURN DISTINCT x, liveAlias ORDER BY x
                                """,
                        rows -> {
                            assertEquals(2, rows.size());
                            assertLong(rows.get(0), "x", 1L);
                            assertLong(rows.get(0), "liveAlias", 11L);
                            assertLong(rows.get(1), "x", 2L);
                            assertLong(rows.get(1), "liveAlias", 12L);
                            return "[(1,11),(2,12)]";
                        }),
                new Probe("CY7", "Control/truth",
                        "Boolean/comparison/arithmetic/CASE/coalesce validation-truth representatives",
                        "booleanOk=true;compareOk=true;arithmeticOk=true;caseOk=true;nullTruthOk=true;stringOk=true",
                        """
                                CYPHER 5
                                RETURN NOT false AND (true OR false) AS booleanOk,
                                       2 < 3 AND 3 <= 3 AND 4 > 3 AND 4 >= 4 AND 5 <> 6 AS compareOk,
                                       2 + 3 = 5 AND 7 - 2 = 5 AND 3 * 4 = 12 AND 8 / 2 = 4 AS arithmeticOk,
                                       CASE WHEN true THEN 1 ELSE 2 END = 1 AS caseOk,
                                       coalesce(null = 1, false) = false AS nullTruthOk,
                                       toLower('Ab') = 'ab' AND substring('abcd', 1, 2) = 'bc' AS stringOk
                                """,
                        rows -> allTrue(single(rows), "booleanOk", "compareOk", "arithmeticOk",
                                "caseOk", "nullTruthOk", "stringOk")),
                new Probe("CY8", "Row expansion",
                        "UNWIND empty/singleton/duplicate/bottom lists has the emitted finite row behavior",
                        "empty=0;singleton=1;expanded=3;distinct=2",
                        """
                                CYPHER 5
                                RETURN COUNT { UNWIND [] AS x RETURN x } AS emptyRows,
                                       COUNT { UNWIND [1] AS x RETURN x } AS singletonRows,
                                       COUNT { UNWIND [1, 1, $bottom] AS x RETURN x } AS expandedRows,
                                       COUNT {
                                         UNWIND [1, 1, $bottom] AS x
                                         WITH DISTINCT x RETURN x
                                       } AS distinctRows
                                """,
                        rows -> {
                            Record row = single(rows);
                            assertLong(row, "emptyRows", 0L);
                            assertLong(row, "singletonRows", 1L);
                            assertLong(row, "expandedRows", 3L);
                            assertLong(row, "distinctRows", 2L);
                            return "empty=0;singleton=1;expanded=3;distinct=2";
                        }),
                new Probe("CY9", "Branch composition",
                        "nested correlated CALL imports aliases and complementary UNION ALL enables one arm",
                        "values=[8,9];importedSelf=[7,7]",
                        """
                                CYPHER 5
                                WITH 7 AS self
                                UNWIND [true, false] AS cond
                                CALL {
                                  WITH self, cond
                                  WITH self, cond WHERE cond
                                  CALL { WITH self RETURN self + 1 AS nested }
                                  RETURN nested AS value, self AS importedSelf
                                  UNION ALL
                                  WITH self, cond
                                  WITH self, cond WHERE NOT cond
                                  CALL { WITH self RETURN self + 2 AS nested }
                                  RETURN nested AS value, self AS importedSelf
                                }
                                RETURN value, importedSelf ORDER BY value
                                """,
                        rows -> {
                            assertEquals(2, rows.size());
                            assertLong(rows.get(0), "value", 8L);
                            assertLong(rows.get(1), "value", 9L);
                            assertLong(rows.get(0), "importedSelf", 7L);
                            assertLong(rows.get(1), "importedSelf", 7L);
                            return "values=[8,9];importedSelf=[7,7]";
                        })
        );
    }

    static Map<String, Object> parameters(String probeRun) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("probeRun", probeRun);
        values.put("integerValue", 17L);
        values.put("realValue", 2.5d);
        values.put("textValue", "probe");
        values.put("booleanValue", true);
        values.put("objectKey", "Cypher5ValProbe::object::a");
        values.put("bottom", OclBottomToken.value());
        values.put("associationKey", "Cypher5ValProbe::association::Probe");
        values.put("sourceQualifiers", List.of("source-q"));
        values.put("targetQualifiers", List.of("target-q"));
        return Map.copyOf(values);
    }

    record Probe(String id, String assumption, String expected, String expectedObservation,
                 String cypher, Verification verification) {
        String verify(List<Record> rows) {
            return verification.verify(rows);
        }
    }

    @FunctionalInterface
    interface Verification {
        String verify(List<Record> rows);
    }

    private static Record single(List<Record> rows) {
        assertEquals(1, rows.size());
        return rows.get(0);
    }

    private static String allTrue(Record row, String... fields) {
        List<String> observed = new ArrayList<>();
        for (String field : fields) {
            assertTrue(row.get(field).asBoolean(), field);
            observed.add(field + "=true");
        }
        return String.join(";", observed);
    }

    private static void assertLong(Record row, String field, long expected) {
        assertEquals(expected, row.get(field).asLong(), field);
    }
}
