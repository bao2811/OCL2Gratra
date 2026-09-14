package org.uet.dse.neo4j.sync.helper;

import org.junit.jupiter.api.Test;
import org.tzi.use.uml.ocl.type.TypeFactory;
import org.tzi.use.uml.ocl.value.BooleanValue;
import org.tzi.use.uml.ocl.value.EnumValue;
import org.tzi.use.uml.ocl.value.IntegerValue;
import org.tzi.use.uml.ocl.value.RealValue;
import org.tzi.use.uml.ocl.value.StringValue;
import org.tzi.use.uml.ocl.value.UndefinedValue;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QualifierValueCodecTest {
    @Test
    void qualifierEncodingUsesTheStaticTypeAndKeepsBottomDisjoint() {
        var status = TypeFactory.mkEnum("Status", List.of("ACTIVE"));

        assertEquals(List.of(
                        "v1|S|Undefined",
                        "v1|S|O'Brien %7C 100%25",
                        "v1|I|7",
                        "v1|R|7.5",
                        "v1|B|true",
                        "v1|E|#ACTIVE",
                        "v1|V"),
                QualifierValueCodec.encodeQualifierValues(List.of(
                        new StringValue("Undefined"),
                        new StringValue("O'Brien | 100%"),
                        IntegerValue.valueOf(7),
                        new RealValue(7.5),
                        BooleanValue.TRUE,
                        new EnumValue(status, "ACTIVE"),
                        UndefinedValue.instance)));
    }

    @Test
    void qualifierEncodingIdentifiesBothRealSignedZeros() {
        assertEquals(List.of("v1|R|0.0", "v1|R|0.0"),
                QualifierValueCodec.encodeQualifierValues(List.of(
                        new RealValue(0.0d), new RealValue(-0.0d))));
    }
}
