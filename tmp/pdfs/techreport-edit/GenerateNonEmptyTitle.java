import org.uet.dse.ocl2cypher.api.FrontendCompiler;
import org.uet.dse.ocl2cypher.caseStudy.LibraryCorpus;
import org.uet.dse.ocl2cypher.core.CoreLowering;
import org.uet.dse.ocl2cypher.cypher.CypherAst;
import org.uet.dse.ocl2cypher.cypher.Realization;
import org.uet.dse.ocl2cypher.cypher.Serializer;
import org.uet.dse.ocl2cypher.graph.GraphBuilder;
import org.uet.dse.ocl2cypher.qcyp.QCypTranslator;

public final class GenerateNonEmptyTitle {
    public static void main(String[] args) {
        String source = "context Book inv NonEmptyTitle: self.title <> ''";
        var schema = LibraryCorpus.schema();
        var built = GraphBuilder.build(schema, LibraryCorpus.main());
        if (built.isFailure()) throw new IllegalStateException(built.diagnostics().toString());
        var frontend = FrontendCompiler.compile(source, schema);
        if (frontend.isFailure()) throw new IllegalStateException(frontend.diagnostics().toString());
        var document = frontend.value().get(0);
        var core = CoreLowering.lower(schema, document, document.constraints.get(0));
        if (core.isFailure()) throw new IllegalStateException(core.diagnostics().toString());
        var q = QCypTranslator.translate(core.value());
        if (q.isFailure()) throw new IllegalStateException(q.diagnostics().toString());
        var artifact = Realization.realize(q.value(), built.value().graph(), CypherAst.Dialect.CYPHER_5);
        if (artifact.isFailure()) throw new IllegalStateException(artifact.diagnostics().toString());
        System.out.print(Serializer.cypherText(artifact.value()));
    }
}
