package org.uet.dse.neo4jtgg.service;

import org.uet.dse.neo4jtgg.model.OclValidationResult;
import org.uet.dse.neo4jtgg.model.TggWorkspaceContext;
import org.uet.dse.neo4jtgg.model.WorkspaceSide;

public interface OclValidationService {
    OclValidationResult validate(TggWorkspaceContext context, WorkspaceSide side, String oclExpression);
}
