package org.uet.dse.neo4jtgg.experiment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class GeneratedCypherCanonicalTreeTest {
    @Test
    void nestedIteratorAliasesAreAlphaEquivalentButCaptureIsNot() {
        var expected = tree("RETURN any(x IN [1] WHERE any(x IN [2] WHERE x > 0) AND x > 0) AS answer");
        var renamed = tree("RETURN any(outer IN [1] WHERE any(inner IN [2] WHERE inner > 0) AND outer > 0) AS result");
        var captured = tree("RETURN any(outer IN [1] WHERE any(inner IN [2] WHERE outer > 0) AND outer > 0) AS result");

        assertEquals(expected.document(), renamed.document());
        assertNotEquals(expected.document(), captured.document());
    }

    @Test
    void fullTreePreservesSchemaTokensDirectionAndParameters() {
        var expected = tree("MATCH (a:Object)-[edge:LinkEmployment]->(b:Object) "
                + "WHERE edge.associationKey = $p1 RETURN b AS value");
        var renamed = tree("MATCH (source:Object)-[rel:LinkEmployment]->(target:Object) "
                + "WHERE rel.associationKey = $p1 RETURN target AS result");
        assertEquals(expected.document(), renamed.document());

        assertNotEquals(expected.document(), tree("MATCH (source:Object)-[rel:LinkEmployment]<-(target:Object) "
                + "WHERE rel.associationKey = $p1 RETURN target AS result").document());
        assertNotEquals(expected.document(), tree("MATCH (source:Object)-[rel:LinkOther]->(target:Object) "
                + "WHERE rel.associationKey = $p1 RETURN target AS result").document());
        assertNotEquals(expected.document(), tree("MATCH (source:Object)-[rel:LinkEmployment]->(target:Object) "
                + "WHERE rel.name = $p1 RETURN target AS result").document());
        assertNotEquals(expected.document(), tree("MATCH (source:Object)-[rel:LinkEmployment]->(target:Object) "
                + "WHERE rel.associationKey = $p2 RETURN target AS result").document());
    }

    @Test
    void mapKeysAndLabelsRemainExactWhileAliasesNormalize() {
        var expected = tree("MATCH (n:Object {classKey:$p1}) RETURN n AS value");
        var renamed = tree("MATCH (object:Object {classKey:$p1}) RETURN object AS result");
        assertEquals(expected.document(), renamed.document());
        assertNotEquals(expected.document(),
                tree("MATCH (object:UmlClass {classKey:$p1}) RETURN object AS result").document());
        assertNotEquals(expected.document(),
                tree("MATCH (object:Object {name:$p1}) RETURN object AS result").document());
    }

    private static GeneratedCypherCanonicalTree.CanonicalTree tree(String cypher) {
        return GeneratedCypherCanonicalTree.parse(cypher);
    }
}
