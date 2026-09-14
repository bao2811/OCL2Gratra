package org.uet.dse.neo4jtgg.service.impl;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OclDocumentRuleParameterValuesParserTest {

    @Test
    void parsesRuleScopedParameterLines() {
        Map<String, Map<String, Object>> values = OclDocumentRuleParameterValuesParser.parse("""
                Family::addDaughter::UnnamedPre => name='Lisa'
                BankAccount::withdraw::PositiveAmount => amount=100, enabled=true
                """);

        assertEquals("Lisa", values.get("Family::addDaughter::UnnamedPre").get("name"));
        assertEquals(100L, values.get("BankAccount::withdraw::PositiveAmount").get("amount"));
        assertEquals(Boolean.TRUE, values.get("BankAccount::withdraw::PositiveAmount").get("enabled"));
    }

    @Test
    void rejectsEntryWithoutRuleSeparator() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> OclDocumentRuleParameterValuesParser.parse(
                        "Family::addDaughter::UnnamedPre name='Lisa'"));

        assertTrue(error.getMessage().contains("QualifiedRule => name=value"));
    }
}
