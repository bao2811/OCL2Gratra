package org.uet.dse.ocl2cypher.caseStudy;

import org.junit.jupiter.api.Test;
import org.uet.dse.ocl2cypher.runtime.OclType;
import static org.junit.jupiter.api.Assertions.*;

/** Finite B9 witnesses for the type factories used in the experiment, not N-5 proof. */
class ExperimentTypeScopeTest {
    @Test void concurrentFactoriesReturnOneIdentity() throws Exception {
        var pool = java.util.concurrent.Executors.newFixedThreadPool(8);
        String key = "InterningTest::" + java.util.UUID.randomUUID();
        try {
            var tasks = new java.util.ArrayList<java.util.concurrent.Callable<OclType[]>>();
            for (int i = 0; i < 128; i++) tasks.add(() -> {
                var type = OclType.clazz(new String(key));
                return new OclType[] { type, OclType.set(type), OclType.bag(type) };
            });
            var results = pool.invokeAll(tasks);
            var first = results.get(0).get();
            for (var result : results) {
                var types = result.get();
                for (int i = 0; i < first.length; i++) assertSame(first[i], types[i]);
            }
        } finally { pool.shutdownNow(); }
    }

    @Test void canonicalKeysRemainDistinctAndInvalidCollectionsReject() {
        assertNotSame(OclType.clazz("A::C"), OclType.clazz("B::C"));
        assertNotSame(OclType.clazz("C"), OclType.clazz("c"));
        assertThrows(NullPointerException.class, () -> OclType.clazz(null));
        assertThrows(IllegalArgumentException.class, () -> OclType.set(null));
        assertThrows(IllegalArgumentException.class, () -> OclType.bag(OclType.set(OclType.STRING)));
        assertThrows(IllegalArgumentException.class, () -> OclType.set(OclType.bag(OclType.STRING)));
    }
    @Test void experimentTypeFactoriesAgreeStructurallyAndByIdentity() {
        for (String name : java.util.List.of("Person", "Employee", "Car", "MaintenanceRecord")) {
            var a = OclType.clazz(new String(name));
            var b = OclType.clazz(new String(name));
            assertEquals(a, b);
            assertSame(a, b);
            assertSame(OclType.set(a), OclType.set(b));
            assertSame(OclType.bag(a), OclType.bag(b));
            assertNotEquals(OclType.set(a), OclType.bag(b));
        }
        assertSame(OclType.set(OclType.STRING), OclType.set(OclType.STRING));
        assertNotEquals(OclType.clazz("Person"), OclType.clazz("Employee"));
    }
}
