package org.uet.dse.neo4j.sync.helper;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CanonicalScalarValueCodecTest {
    @Test
    void bottomCannotCollideWithTheLegalStringUndefined() {
        String bottom = CanonicalScalarValueCodec.encode(null, "String");
        String ordinary = CanonicalScalarValueCodec.encode("Undefined", "String");

        assertEquals("v1|V", bottom);
        assertEquals("v1|S|Undefined", ordinary);
        assertNotEquals(bottom, ordinary);
        assertNull(CanonicalScalarValueCodec.decode(bottom, "String"));
        assertEquals("Undefined", CanonicalScalarValueCodec.decode(ordinary, "String"));
    }

    @Test
    void stringEncodingIsExactAndInjectiveForQuotesWhitespaceUnicodeAndDelimiters() {
        String value = "  O'Brien | 100% Việt Nam \n";
        String encoded = CanonicalScalarValueCodec.encode(value, "String");

        assertEquals("v1|S|  O'Brien %7C 100%25 Việt Nam \n", encoded);
        assertEquals(value, CanonicalScalarValueCodec.decode(encoded, "String"));
    }

    @Test
    void primitiveAndEnumTagsRemainDisjoint() {
        assertEquals(42L, CanonicalScalarValueCodec.decode(
                CanonicalScalarValueCodec.encode(42, "Integer"), "Integer"));
        assertEquals(0.0d, CanonicalScalarValueCodec.decode(
                CanonicalScalarValueCodec.encode(-0.0d, "Real"), "Real"));
        assertEquals(Boolean.TRUE, CanonicalScalarValueCodec.decode(
                CanonicalScalarValueCodec.encode(true, "Boolean"), "Boolean"));
        assertEquals("#ACTIVE", CanonicalScalarValueCodec.decode(
                CanonicalScalarValueCodec.encode("#ACTIVE", "Status"), "Status"));
    }

    @Test
    void acceptsPersistedDatabaseAliasesForIntegerAndReal() {
        assertEquals("v1|I|42", CanonicalScalarValueCodec.encode(42L, "Int"));
        assertEquals(42L, CanonicalScalarValueCodec.decode("v1|I|42", "Int"));
        assertEquals("v1|R|2.5", CanonicalScalarValueCodec.encode(2.5d, "Double"));
        assertEquals(2.5d, CanonicalScalarValueCodec.decode("v1|R|2.5", "Double"));
    }

    @Test
    void realEncodingUsesOneCanonicalPayloadForBothSignedZeros() {
        String positiveZero = CanonicalScalarValueCodec.encode(0.0d, "Real");
        String negativeZero = CanonicalScalarValueCodec.encode(-0.0d, "Real");

        assertEquals("v1|R|0.0", positiveZero);
        assertEquals(positiveZero, negativeZero);
        double decoded = (Double) CanonicalScalarValueCodec.decode(negativeZero, "Real");
        assertEquals(Double.doubleToRawLongBits(0.0d), Double.doubleToRawLongBits(decoded));
        assertThrows(IllegalArgumentException.class,
                () -> CanonicalScalarValueCodec.decode("v1|R|-0.0", "Real"));
    }

    @Test
    void malformedLegacyAndCrossTypedPayloadsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> CanonicalScalarValueCodec.decode("Undefined", "String"));
        assertThrows(IllegalArgumentException.class,
                () -> CanonicalScalarValueCodec.decode("v1|S|42", "Integer"));
        assertThrows(IllegalArgumentException.class,
                () -> CanonicalScalarValueCodec.decode("v1|S|bad%escape", "String"));
        assertThrows(IllegalArgumentException.class,
                () -> CanonicalScalarValueCodec.encode(Double.NaN, "Real"));
    }
}
