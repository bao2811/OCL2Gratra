package org.uet.dse.ocl2cypher.runtime;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OclTypeInterningTest {

    @Test
    void classTypesAreIdentityInterned() {
        OclType a = OclType.clazz("Person");
        OclType b = OclType.clazz("Person");
        assertSame(a, b, "clazz(\"Person\") must be reference-identical");
        assertNotSame(OclType.clazz("Person"), OclType.clazz("Company"));
    }

    @Test
    void setTypesAreIdentityInterned() {
        OclType a = OclType.set(OclType.STRING);
        OclType b = OclType.set(OclType.STRING);
        assertSame(a, b, "set(STRING) must be reference-identical");
        assertNotSame(OclType.set(OclType.STRING), OclType.set(OclType.INTEGER));
    }

    @Test
    void bagTypesAreIdentityInterned() {
        OclType a = OclType.bag(OclType.INTEGER);
        OclType b = OclType.bag(OclType.INTEGER);
        assertSame(a, b, "bag(INTEGER) must be reference-identical");
    }

    @Test
    void collectionWithClassElementIsInterned() {
        OclType a = OclType.set(OclType.clazz("Person"));
        OclType b = OclType.set(OclType.clazz("Person"));
        assertSame(a, b, "set(clazz(\"Person\")) must be reference-identical");
        assertNotSame(OclType.set(OclType.clazz("Person")), OclType.set(OclType.clazz("Company")));
    }

    @Test
    void primitiveSingletonsAreIdentityInterned() {
        assertSame(OclType.BOOLEAN, OclType.BOOLEAN);
        assertSame(OclType.INTEGER, OclType.INTEGER);
        assertSame(OclType.REAL, OclType.REAL);
        assertSame(OclType.STRING, OclType.STRING);
    }

    @Test
    void joinReturnsInternedType() {
        OclType a = OclType.set(OclType.clazz("Employee"));
        OclType b = OclType.set(OclType.clazz("Manager"));
        // join with subclass relation: Manager <= Employee
        OclType joined = OclType.join(a, b, (sub, sup) -> sub.equals("Manager") && sup.equals("Employee"));
        assertSame(OclType.set(OclType.clazz("Employee")), joined);
    }
}
