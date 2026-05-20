package org.uet.dse.neo4jtgg.service;

import org.uet.dse.neo4jtgg.model.CypherCompilationResult;

public interface OclToCypherCompiler {
    CypherCompilationResult compile(String oclExpression);
}
