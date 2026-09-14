package org.uet.dse.ocl2cypher.caseStudy;

import java.io.IOException;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;
import org.uet.dse.ocl2cypher.runtime.*;
import static org.junit.jupiter.api.Assertions.*;

class CaseStudyScalarReaderTest {
    @Test void booleanIsNotAnInteger() throws Exception {
        assertEquals(Boolean3.TRUE, CaseStudyReplayer.decodeScalar("true", OclType.BOOLEAN));
        assertEquals(Boolean3.FALSE, CaseStudyReplayer.decodeScalar("false", OclType.BOOLEAN));
        assertThrows(IOException.class, () -> CaseStudyReplayer.decodeScalar("0", OclType.BOOLEAN));
        assertThrows(IOException.class, () -> CaseStudyReplayer.decodeScalar("unknown", OclType.BOOLEAN));
    }
    @Test void numbersAreExactSourceValuesNotNativeCertificates() throws Exception {
        assertEquals(new OclValue.IntegerValue(new BigInteger("9223372036854775808")),
                CaseStudyReplayer.decodeScalar("9223372036854775808", OclType.INTEGER));
        assertEquals(new OclValue.RealValue(new java.math.BigDecimal("-100.0")),
                CaseStudyReplayer.decodeScalar("-100.0", OclType.REAL));
        assertThrows(IOException.class, () -> CaseStudyReplayer.decodeScalar("1.5", OclType.INTEGER));
        assertThrows(IOException.class, () -> CaseStudyReplayer.decodeScalar("'12'", OclType.INTEGER));
    }
    @Test void stringsKeepSpacesAndEmptyContent() throws Exception {
        assertEquals(new OclValue.StringValue(" a "), CaseStudyReplayer.decodeScalar("' a '", OclType.STRING));
        assertEquals(new OclValue.StringValue(""), CaseStudyReplayer.decodeScalar("''", OclType.STRING));
        assertThrows(IOException.class, () -> CaseStudyReplayer.decodeScalar("unquoted", OclType.STRING));
        assertThrows(IOException.class, () -> CaseStudyReplayer.decodeScalar("Set{'x'}", OclType.set(OclType.STRING)));
    }
}
