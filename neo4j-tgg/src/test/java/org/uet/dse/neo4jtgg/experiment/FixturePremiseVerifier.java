package org.uet.dse.neo4jtgg.experiment;

import org.tzi.use.uml.sys.MSystem;
import org.uet.dse.neo4jtgg.ocl.OclExecutionPremiseChecker;

/** Per-fixture executable evidence for ScalarClosed and BottomSeparated premises. */
final class FixturePremiseVerifier {
    private FixturePremiseVerifier() {
    }

    static void verify(MSystem system, InstrumentedCompilationResult compilation) {
        OclExecutionPremiseChecker.requireUseSystemScalarClosed(
                system, compilation.validationAlgebra());
        OclExecutionPremiseChecker.requireGeneratedBottomSeparated(compilation.parameters());
    }

}
