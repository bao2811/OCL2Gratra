package org.uet.dse.neo4jtgg.experiment;

import org.uet.dse.neo4j.oclite.ast.ASTContext;
import org.uet.dse.neo4jtgg.ocl.OclSemanticBinder;
import org.uet.dse.neo4jtgg.ocl.ir.OclCypherPlan;
import org.uet.dse.neo4jtgg.ocl.ir.OclIr;

import java.util.Map;

public record InstrumentedCompilationResult(ASTContext ast,
                                            OclSemanticBinder.BoundContextInvariant bound,
                                            OclIr.InvariantQuery validationAlgebra,
                                            OclIr.InvariantQuery normalizedValidationAlgebra,
                                            OclCypherPlan.InvariantPlan queryPlan,
                                            String cypher,
                                            Map<String, Object> parameters,
                                            PipelineStageTimings timings) {
    public InstrumentedCompilationResult {
        parameters = Map.copyOf(parameters);
    }
}
