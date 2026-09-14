package org.uet.dse.neo4jtgg.experiment;

import org.tzi.use.uml.sys.MSystem;
import org.uet.dse.neo4j.oclite.ast.ASTContext;

import java.util.Set;

public interface ObjectSideReferenceEvaluator {
    Set<String> violationIds(MSystem system, ASTContext invariant);

    default Set<String> violationIds(MSystem system, ASTContext invariant, String originalExpressionSource) {
        return violationIds(system, invariant);
    }
}
