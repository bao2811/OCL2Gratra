package org.uet.dse.ocl2cypher.cypher;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.uet.dse.ocl2cypher.cypher.CypherAst.*;

import static org.junit.jupiter.api.Assertions.*;

/** Executable refinement witnesses for S-2 metadata/contract preservation. */
class S2SerializationContractTest {

    @Test
    void preservesDialectParametersAndResultContractModuloCanonicalOrdering() {
        QueryParameter generated = new QueryParameter("__oclGenerated", "Physical:String",
                QueryParameter.Origin.GENERATED, "canonical-secret");
        QueryParameter caller = new QueryParameter("zPublic", "String",
                QueryParameter.Origin.PUBLIC, null);
        ResultContract contract = new ResultContract(ResultShape.SCALAR,
                "result", "String", false, null);
        GeneratedArtifact artifact = artifact(contract, List.of(caller, generated));

        Serializer.Serialized serialized = Serializer.serialize(artifact);

        assertEquals(Dialect.CYPHER_5, serialized.dialect());
        assertSame(contract, serialized.contract(),
                "S must carry the exact immutable decoder contract object");
        assertEquals(List.of(generated, caller), serialized.parameters(),
                "metadata order is the sole canonicalization performed by S");
        assertEquals(artifact.parameters().stream().filter(p ->
                        p.origin() == QueryParameter.Origin.PUBLIC).toList(),
                serialized.parameters().stream().filter(p ->
                        p.origin() == QueryParameter.Origin.PUBLIC).toList());
        assertEquals(artifact.parameters().stream().filter(p ->
                        p.origin() == QueryParameter.Origin.GENERATED).toList(),
                serialized.parameters().stream().filter(p ->
                        p.origin() == QueryParameter.Origin.GENERATED).toList());
    }

    @Test
    void keepsMetadataOutOfBandAndDoesNotInventQueryStructure() {
        QueryParameter generated = new QueryParameter("__oclGenerated", "Physical:String",
                QueryParameter.Origin.GENERATED, "canonical-secret");
        QueryParameter caller = new QueryParameter("zPublic", "String",
                QueryParameter.Origin.PUBLIC, null);
        GeneratedArtifact artifact = artifact(new ResultContract(ResultShape.SCALAR,
                "result", "String", false, null), List.of(generated, caller));

        String text = Serializer.serialize(artifact).cypherText();

        assertTrue(text.contains("$zPublic"));
        assertTrue(text.contains("$__oclGenerated"));
        assertFalse(text.contains("canonical-secret"),
                "generated values must remain outside query text");
        assertFalse(text.contains("DISTINCT"));
        assertFalse(text.contains("CASE"));
        assertEquals(1, occurrences(text, "RETURN "));
        Neo4jCypherParserGate.assertParses(text);
    }

    @Test
    void snapshotsMetadataAndRejectsContradictoryOriginBeforeSerialization() {
        List<QueryParameter> mutable = new ArrayList<>();
        mutable.add(new QueryParameter("zPublic", "String",
                QueryParameter.Origin.PUBLIC, null));
        GeneratedArtifact artifact = artifact(new ResultContract(ResultShape.SCALAR,
                "result", "String", false, null), mutable);
        mutable.clear();
        assertEquals(1, Serializer.serialize(artifact).parameters().size(),
                "artifact construction must snapshot caller metadata");

        QueryParameter invalid = new QueryParameter("badPublic", "String",
                QueryParameter.Origin.PUBLIC, "must-not-have-generated-binding");
        GeneratedArtifact malformed = artifact(new ResultContract(ResultShape.SCALAR,
                "result", "String", false, null), List.of(invalid));
        assertThrows(IllegalArgumentException.class,
                () -> Serializer.serialize(malformed));
    }

    private static GeneratedArtifact artifact(ResultContract contract,
                                              List<QueryParameter> parameters) {
        ReturnClause result = new ReturnClause(false, List.of(
                new ProjectionItem(new ParameterExpr("zPublic"), "result"),
                new ProjectionItem(parameters.stream()
                        .anyMatch(p -> p.name().equals("__oclGenerated"))
                        ? new ParameterExpr("__oclGenerated")
                        : new StringLiteral("constant"), "generatedEcho")));
        return new GeneratedArtifact(Dialect.CYPHER_5,
                new CypherQuery(List.of(result), true), contract, parameters);
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        for (int at = 0; (at = text.indexOf(needle, at)) >= 0; at += needle.length()) {
            count++;
        }
        return count;
    }
}
