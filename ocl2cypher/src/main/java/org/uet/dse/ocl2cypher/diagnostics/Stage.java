package org.uet.dse.ocl2cypher.diagnostics;

/**
 * Boundary that produced a diagnostic.
 *
 * <p>The stage names follow the public transformation chain fixed by
 * {@code research/Transformation Specification/Rule/00-Rule-Notation-and-Contexts.md}
 * section 3. {@code EXECUTION} is the runtime boundary added by the reference
 * implementation; it is not a compilation boundary.
 */
public enum Stage {
    E_SM,
    N_SM,
    T_MM,
    F_G,
    T_G,
    R,
    S,
    EXECUTION
}
