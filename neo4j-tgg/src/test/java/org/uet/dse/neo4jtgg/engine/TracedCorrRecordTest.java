package org.uet.dse.neo4jtgg.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TracedCorrRecordTest {

    @Test
    void testCreate_SetsTimestamps() {
        long before = System.currentTimeMillis();
        TracedCorrRecord record = TracedCorrRecord.create(
                "corr1", "FR2PR",
                "fr1", "FamilyRegister",
                "pr1", "PersonRegister",
                "Families2Persons", TransformationDirection.FORWARD);
        long after = System.currentTimeMillis();

        assertEquals("corr1", record.objectId());
        assertEquals("FR2PR", record.corrClassName());
        assertEquals("fr1", record.sourceObjectId());
        assertEquals("FamilyRegister", record.sourceClassName());
        assertEquals("pr1", record.targetObjectId());
        assertEquals("PersonRegister", record.targetClassName());
        assertEquals("Families2Persons", record.appliedRuleName());
        assertEquals(TransformationDirection.FORWARD, record.direction());
        assertTrue(record.createdAt() >= before && record.createdAt() <= after);
        assertEquals(record.createdAt(), record.lastVerifiedAt());
    }

    @Test
    void testWithVerifiedNow_UpdatesTimestamp() throws InterruptedException {
        TracedCorrRecord original = TracedCorrRecord.create(
                "corr1", "F2MP", "homer", "FamilyMember",
                "male1", "Male", "FatherToMale", TransformationDirection.FORWARD);

        Thread.sleep(10); // ensure time passes
        TracedCorrRecord verified = original.withVerifiedNow();

        // Original fields unchanged
        assertEquals(original.objectId(), verified.objectId());
        assertEquals(original.corrClassName(), verified.corrClassName());
        assertEquals(original.appliedRuleName(), verified.appliedRuleName());
        assertEquals(original.createdAt(), verified.createdAt());

        // lastVerifiedAt updated
        assertTrue(verified.lastVerifiedAt() > original.lastVerifiedAt());
    }

    @Test
    void testCorrKey_Deterministic() {
        TracedCorrRecord record = TracedCorrRecord.create(
                "corr1", "FR2PR", "src1", "S", "tgt1", "T",
                "Rule1", TransformationDirection.FORWARD);

        assertEquals("Rule1_src1_tgt1", record.corrKey());
    }

    @Test
    void testCorrKey_DifferentSources_DifferentKeys() {
        TracedCorrRecord r1 = TracedCorrRecord.create(
                "c1", "C", "src1", "S", "tgt1", "T", "R", TransformationDirection.FORWARD);
        TracedCorrRecord r2 = TracedCorrRecord.create(
                "c2", "C", "src2", "S", "tgt1", "T", "R", TransformationDirection.FORWARD);

        assertNotEquals(r1.corrKey(), r2.corrKey());
    }

    @Test
    void testBackwardDirection() {
        TracedCorrRecord record = TracedCorrRecord.create(
                "corr_back", "F2MP", "member1", "FamilyMember",
                "male1", "Male", "FatherToMale", TransformationDirection.BACKWARD);

        assertEquals(TransformationDirection.BACKWARD, record.direction());
    }

    @Test
    void testRecordEquality() {
        long now = System.currentTimeMillis();
        TracedCorrRecord r1 = new TracedCorrRecord("id", "C", "s", "S", "t", "T", "R",
                TransformationDirection.FORWARD, now, now);
        TracedCorrRecord r2 = new TracedCorrRecord("id", "C", "s", "S", "t", "T", "R",
                TransformationDirection.FORWARD, now, now);

        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
    }
}
