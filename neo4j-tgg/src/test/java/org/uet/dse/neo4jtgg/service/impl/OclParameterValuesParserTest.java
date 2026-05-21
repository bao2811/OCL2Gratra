package org.uet.dse.neo4jtgg.service.impl;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OclParameterValuesParserTest {

    @Test
    void parsesCommonScalarValues() {
        Map<String, Object> values = OclParameterValuesParser.parse(
                "amount=100, rate=2.5, enabled=true, note='hello', raw=text");

        assertEquals(100L, values.get("amount"));
        assertEquals(2.5d, values.get("rate"));
        assertEquals(Boolean.TRUE, values.get("enabled"));
        assertEquals("hello", values.get("note"));
        assertEquals("text", values.get("raw"));
    }

    @Test
    void keepsCommaInsideQuotedString() {
        Map<String, Object> values = OclParameterValuesParser.parse("message='a,b,c', empty=null");

        assertEquals("a,b,c", values.get("message"));
        assertTrue(values.containsKey("empty"));
        assertNull(values.get("empty"));
    }

    @Test
    void rejectsInvalidEntryWithoutSeparator() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> OclParameterValuesParser.parse("amount=100, broken"));

        assertTrue(error.getMessage().contains("name=value"));
    }
}
