package org.uet.dse.neo4jtgg.ocl;

import org.junit.jupiter.api.Test;
import org.uet.dse.neo4j.oclite.ast.ASTBinary;
import org.uet.dse.neo4j.oclite.ast.ASTCollectionOp;
import org.uet.dse.neo4j.oclite.ast.ASTContext;
import org.uet.dse.neo4j.oclite.ast.ASTIntegerLiteral;
import org.uet.dse.neo4j.oclite.ast.ASTIterator;
import org.uet.dse.neo4j.oclite.ast.ASTProperty;
import org.uet.dse.neo4j.oclite.ast.ASTVar;
import org.uet.dse.neo4j.oclite.ast.SourceSpan;
import org.uet.dse.neo4jtgg.ocl.diagnostic.OclCodedUnsupportedOperationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OclCertifiedSurfaceNormalizerTest {
    @Test
    void oneRewritesToSelectCardinalityWithoutWideningFrozenAdmission() {
        ASTIterator surface = new ASTIterator(
                new ASTProperty(new ASTVar("self"), "employee"),
                "one", "employee", new ASTProperty(new ASTVar("employee"), "active"));
        surface.setSourceSpan(new SourceSpan(10, 50, 4, 10, 4, 50));
        ASTContext original = new ASTContext("Company", "OneActive", surface);

        assertThrows(OclCodedUnsupportedOperationException.class,
                () -> OclValAdmissionPolicy.verify(original));

        ASTContext normalized = OclCertifiedSurfaceNormalizer.normalize(original);
        ASTBinary equality = assertType(ASTBinary.class, normalized.expression);
        ASTCollectionOp size = assertType(ASTCollectionOp.class, equality.left);
        ASTIterator select = assertType(ASTIterator.class, size.source);
        ASTIntegerLiteral one = assertType(ASTIntegerLiteral.class, equality.right);

        assertNotSame(original, normalized);
        assertEquals("=", equality.op);
        assertEquals("size", size.opName);
        assertEquals("select", select.operation);
        assertEquals(surface.iteratorVariables, select.iteratorVariables);
        assertEquals(1, one.value);
        assertEquals(surface.sourceSpan(), equality.sourceSpan());
        OclValAdmissionPolicy.verify(normalized);
    }

    private static <T> T assertType(Class<T> type, Object value) {
        assertTrue(type.isInstance(value), () -> "Expected " + type.getSimpleName()
                + " but got " + (value == null ? "null" : value.getClass().getSimpleName()));
        return type.cast(value);
    }
}
