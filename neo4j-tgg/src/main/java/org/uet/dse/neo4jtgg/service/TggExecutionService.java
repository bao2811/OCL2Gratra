package org.uet.dse.neo4jtgg.service;

import org.uet.dse.neo4jtgg.engine.TransformationOptions;
import org.uet.dse.neo4jtgg.engine.TransformationReport;
import org.uet.dse.neo4jtgg.model.TggWorkspaceContext;

public interface TggExecutionService {
    TransformationReport preview(TggWorkspaceContext context, TransformationOptions options);

    TransformationReport run(TggWorkspaceContext context, TransformationOptions options);
}
