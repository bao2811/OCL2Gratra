package org.uet.dse.ocl2cypher.diagnostics;

/**
 * Minimum contract of any stage that can emit a diagnostic-bearing result.
 *
 * <p>Every compiler boundary propagates premise failures with the originating
 * diagnostic preserved. The outermost stage may wrap the failure in its own
 * diagnostic code, keeping the inner code in {@link Diagnostic#causeCode()}: see
 * Rule 00 section 3. Callers do not convert a failure into a typed bottom.
 */
public interface Diagnosable {

    default void rejectIf(Result<?> premise, Diagnostic wrapped) {
        // Intentionally left empty: the caller is expected to thread
        // premise diagnostics into the synthesized wrap via
        // Diagnostic.causeCode() when building a stage-level failure.
    }
}
