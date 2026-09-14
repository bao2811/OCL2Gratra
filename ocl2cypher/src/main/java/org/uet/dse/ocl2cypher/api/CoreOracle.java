package org.uet.dse.ocl2cypher.api;

import java.util.List;
import org.uet.dse.ocl2cypher.core.CoreInterpreter;
import org.uet.dse.ocl2cypher.core.CoreInvariant;
import org.uet.dse.ocl2cypher.core.CoreUnit;
import org.uet.dse.ocl2cypher.diagnostics.*;
import org.uet.dse.ocl2cypher.runtime.OclType;
import org.uet.dse.ocl2cypher.runtime.OclValue;
import org.uet.dse.ocl2cypher.source.model.SchemaModel;
import org.uet.dse.ocl2cypher.source.model.Snapshot;
import org.uet.dse.ocl2cypher.source.omg.OmgAs;

/**
 * Internal executable oracle {@code CoreOracle}: compile OCL text and evaluate the single
 * invariant's violation set through the Core interpreter — the whole
 * construction through which {@code N-LOWERING-SOUND} will later be checked
 * against the Q/Cypher counterparts.
 *
 * <p>This class is not an independent OMG/USE oracle because it shares the
 * project's frontend, lowering, types, and Core semantics. Independent
 * differential tests invoke the USE parser, object-state runtime, and evaluator
 * through a test-only adapter.</p>
 *
 * <p>Note the carrier discipline: an invariant violation is every instance whose body
 * evaluates to a value that is {@code != T}, so a bottom-Boolean body counts as
 * a violation and the violation oracle does not collapse its answer to an empty
 * set. Calls that expect a violated-object set therefore use
 * {@link #violationsOclEq(String, SchemaModel, Snapshot)}'s correct two-valued
 * test, not a "bottom → false" filter that would hide the discriminating cases
 * the spec guarantees.
 */
public final class CoreOracle {

    private CoreOracle() {
    }

    /**
     * Return the {@code E_SM} diagnostic when the frontend refuses to build one.
     * Refusal is never turned into a typed bottom whose carriers appear later
     * in a query answer — for such inputs, no Core unit is produced at all.
     *
     * <p>Violation checks must not reach this point when {@code N_SM} would have
     * been expected to reject an unadmitted surface literal into a failure on
     * {@code null}/{@code invalid}, rather than into the typed bottom those
     * same carriers use for "missing property / wrong cardinality" once the
     * input <em>was</em> admitted.
     */
    public static boolean violates(String ocl, SchemaModel sm, Snapshot sn, String objectId) {
        var units = compileSingleInvariant(ocl, sm);
        CoreUnit unit = units.get(0);
        CoreInterpreter.Env env = new CoreInterpreter.Env();
        env.bind(unit.selfVariable(),
                new OclValue.ObjectValue(OclType.clazz(unit.contextClassKey()), objectId));
        OclValue body = CoreInterpreter.evalUnit(sm, sn, unit, env);
        if (body instanceof OclValue.BottomValue) {
            return true;
        }
        if (body instanceof OclValue.BooleanValue bv) {
            return bv.bool() != OclValue.BooleanValue.Bool3.TRUE;
        }
        throw new IllegalStateException("invariant body must be Boolean: " + body);
    }

    public static List<String> violationsOclEq(String ocl, SchemaModel sm, Snapshot sn) {
        var units = compileSingleInvariant(ocl, sm);
        CoreUnit unit = units.get(0);
        java.util.List<String> violations = new java.util.ArrayList<>();
        for (String sid : sn.objectsOfClass(sm, unit.contextClassKey())) {
            if (violates(ocl, sm, sn, sid)) {
                violations.add(sid);
            }
        }
        return java.util.Collections.unmodifiableList(violations);
    }

    private static List<OmgAs.OmgDocument> frontendCompile(String ocl, SchemaModel sm) {
        Result<List<OmgAs.OmgDocument>> fr = FrontendCompiler.compile(ocl, sm);
        if (fr.isFailure()) {
            throw new IllegalArgumentException(
                    "frontend refusal is not a query value — it is a Failure, not a bottom: "
                            + fr.primaryDiagnostic());
        }
        return fr.value();
    }

    private static List<CoreInvariant> compileSingleInvariant(String ocl, SchemaModel sm) {
        var docs = frontendCompile(ocl, sm);
        // OCL_val document: there is one invariant in the current slice.
        OmgAs.OmgDocument doc = docs.get(0);
        OmgAs.Constraint inv = doc.constraints.get(0);
        Result<CoreInvariant> lowered =
                org.uet.dse.ocl2cypher.core.CoreLowering.lower(sm, doc, inv);
        if (lowered.isFailure()) {
            throw new IllegalArgumentException(
                    "admission failure is not a query value — it is a Failure, not a Bottom: "
                            + lowered.primaryDiagnostic());
        }
        return List.of(lowered.value());
    }
}
