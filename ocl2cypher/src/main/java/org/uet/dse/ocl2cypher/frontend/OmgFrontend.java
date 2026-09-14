package org.uet.dse.ocl2cypher.frontend;

import java.util.List;
import org.uet.dse.ocl2cypher.diagnostics.Result;
import org.uet.dse.ocl2cypher.source.model.SchemaModel;
import org.uet.dse.ocl2cypher.source.omg.OmgAs;

/** Public typed frontend whose successful products are exclusively OMG-AS carriers. */
public final class OmgFrontend {

    private final OclFrontend parser;

    public OmgFrontend(SchemaModel schema) {
        this.parser = new OclFrontend(schema);
    }

    public Result<List<OmgAs.OmgDocument>> elaborateDocuments(String source) {
        return parser.elaborateOmgDocuments(source);
    }

    public Result<OmgAs.ExpressionInOcl> elaborateValueExpression(
            String source, String contextClassKey, String contextVariableName) {
        return parser.elaborateOmgValueExpression(
                source, contextClassKey, contextVariableName);
    }
}
