package org.uet.dse.neo4j.sync.helper;

import org.tzi.use.uml.ocl.value.Value;
import org.uet.dse.neo4j.helper.ValueMapper;

import java.util.ArrayList;
import java.util.List;

public final class QualifierValueCodec {
    private QualifierValueCodec() {
    }

    public static List<String> encodeQualifierValues(List<Value> qualifierValues) {
        List<String> encoded = new ArrayList<>();
        if (qualifierValues == null) {
            return encoded;
        }
        for (Value qualifierValue : qualifierValues) {
            Object mapped = ValueMapper.mapUseValue(qualifierValue);
            encoded.add(OclSerializer.serialize(mapped));
        }
        return encoded;
    }
}
