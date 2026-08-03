package org.uet.dse.neo4jtgg.experiment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GeneratedCypherSyntaxTreeTest {
    @Test
    void normalizesGeneratedQueryAndNestedExpressionSubqueries() {
        String query = "MATCH (self:Object)-[:ObjectInstanceOf]->(cls {classKey:$p1}) "
                + "WHERE EXISTS { MATCH (self)-[r]->(target:Object) WHERE r.associationKey = $p2 } "
                + "AND COUNT { UNWIND [1, 2] AS x RETURN DISTINCT x AS value } >= 1 "
                + "RETURN DISTINCT self.use_id AS useId";
        var first = GeneratedCypherSyntaxTree.parse(query);
        var normalized = GeneratedCypherSyntaxTree.render(first);
        assertEquals(first, GeneratedCypherSyntaxTree.parse(normalized));
    }

    @Test
    void acceptsClosedUnionAllAndRejectsBareOrEmptyUnionArms() {
        var parsed = GeneratedCypherSyntaxTree.parse(
                "RETURN 1 AS value UNION ALL RETURN 2 AS value");
        assertEquals(parsed, GeneratedCypherSyntaxTree.parse(GeneratedCypherSyntaxTree.render(parsed)));
        assertThrows(IllegalArgumentException.class,
                () -> GeneratedCypherSyntaxTree.parse("RETURN 1 AS value UNION RETURN 2 AS value"));
        assertThrows(IllegalArgumentException.class,
                () -> GeneratedCypherSyntaxTree.parse("RETURN 1 AS value UNION ALL"));
    }

    @Test
    void rejectsTextOutsideCertifiedGeneratedFragment() {
        assertThrows(IllegalArgumentException.class,
                () -> GeneratedCypherSyntaxTree.parse("MATCH (n) RETURN <predicate> AS value"));
        assertThrows(IllegalArgumentException.class,
                () -> GeneratedCypherSyntaxTree.parse("MATCH (n) // injected\nRETURN n AS value"));
        assertThrows(IllegalArgumentException.class,
                () -> GeneratedCypherSyntaxTree.parse("RETURN $ AS value"));
        assertThrows(IllegalArgumentException.class,
                () -> GeneratedCypherSyntaxTree.parse("RETURN @value AS value"));
        assertThrows(IllegalArgumentException.class,
                () -> GeneratedCypherSyntaxTree.parse("WHERE true RETURN 1 AS value"));
    }

    @Test
    void rejectsMalformedClausesAndDelimiters() {
        assertThrows(IllegalArgumentException.class,
                () -> GeneratedCypherSyntaxTree.parse("MATCH RETURN 1 AS value"));
        assertThrows(IllegalArgumentException.class,
                () -> GeneratedCypherSyntaxTree.parse("MATCH (n RETURN n AS value"));
        assertThrows(IllegalArgumentException.class,
                () -> GeneratedCypherSyntaxTree.parse("RETURN 'unterminated AS value"));
        assertThrows(IllegalArgumentException.class,
                () -> GeneratedCypherSyntaxTree.parse("CALL RETURN 1 AS value"));
    }
}
