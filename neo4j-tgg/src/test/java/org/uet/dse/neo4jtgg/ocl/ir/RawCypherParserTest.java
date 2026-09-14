package org.uet.dse.neo4jtgg.ocl.ir;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RawCypherParserTest {
    private final RawCypherParser parser = new RawCypherParser();
    private final RawCypherRenderer renderer = new RawCypherRenderer();

    @Test
    void distinguishesRangeDotsFromDecimalPoints() {
        String query = "RETURN [1, 2, 3][0..1] AS value";
        assertEquals(query, renderer.render(parser.parse(query)));

        String decimal = "RETURN 1.25 AS value";
        assertEquals(decimal, renderer.render(parser.parse(decimal)));
    }

    @Test
    void reportsTheFirstNonClauseNodeInMalformedCollectSubquery() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> parser.parse("RETURN COLLECT { (n)-->(m) RETURN m } AS value"));
        assertTrue(failure.getMessage().contains("first node=GROUP(())"), failure.getMessage());
    }
}
