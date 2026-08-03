package org.uet.dse.neo4jtgg.ocl;

import org.junit.jupiter.api.Test;
import org.uet.dse.neo4jtgg.ocl.ir.OclCypherPlan;
import org.uet.dse.neo4jtgg.ocl.ir.OclCypherRenderer;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OclBottomTokenContractTest {
    @Test
    void canonicalTokenHasOneImmutableTaggedMapRepresentation() {
        assertSame(OclBottomToken.value(), OclBottomToken.value());
        assertEquals(Map.of(OclBottomToken.MARKER, true), OclBottomToken.value());
        assertTrue(OclBottomToken.isToken(OclBottomToken.value()));
        assertThrows(UnsupportedOperationException.class,
                () -> OclBottomToken.value().put("other", true));
    }

    @Test
    void externalInputsCannotSmuggleTokenButEqualLookingStringsRemainScalars() {
        assertThrows(IllegalArgumentException.class,
                () -> OclBottomToken.requireNoExternalToken(Map.of("arg", OclBottomToken.value())));
        assertThrows(IllegalArgumentException.class,
                () -> OclBottomToken.requireNoExternalToken(
                        Map.of("arg", List.of(Map.of("nested", OclBottomToken.value())))));
        assertDoesNotThrow(() -> OclBottomToken.requireNoExternalToken(
                Map.of("id", "{__oclBottom:true}", "literal", "__oclBottom")));
    }

    @Test
    void setRenderingConvertsNullBeforeUniquenessAndReusesOneTokenParameter() {
        OclTypeBinding integer = OclTypeBinding.scalar("Integer");
        OclTypeBinding setOfInteger = OclTypeBinding.collectionOf(integer, OclTypeBinding.CollectionKind.SET);
        OclCypherPlan.SetLiteralPlan set = new OclCypherPlan.SetLiteralPlan(List.of(
                new OclCypherPlan.LiteralPlan(null, integer),
                new OclCypherPlan.LiteralPlan(null, integer),
                new OclCypherPlan.LiteralPlan(1L, integer)), setOfInteger);

        OclCypherRenderer.RenderedTopLevelExpression rendered =
                new OclCypherRenderer("BottomContract").renderTopLevelExpression(set);

        long tokens = rendered.parameters().values().stream().filter(OclBottomToken::isToken).count();
        assertEquals(1L, tokens);
        assertTrue(rendered.cypher().contains("coalesce(null, $"));
        assertTrue(rendered.cypher().contains("reduce("));
        OclBottomToken.requireWellFormedGeneratedParameters(rendered.parameters());
    }
}
