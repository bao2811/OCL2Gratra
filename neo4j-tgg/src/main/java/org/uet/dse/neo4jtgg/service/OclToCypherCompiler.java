package org.uet.dse.neo4jtgg.service;

import org.uet.dse.neo4jtgg.model.CypherCompilationResult;
import org.uet.dse.neo4jtgg.model.OclFileCompilationResult;

public interface OclToCypherCompiler {
    CypherCompilationResult compile(String oclExpression);
    OclFileCompilationResult compileFile(String oclText);
}
