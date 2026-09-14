package org.uet.dse.ocl2cypher;

import java.util.List;
import org.uet.dse.ocl2cypher.api.FrontendCompiler;
import org.uet.dse.ocl2cypher.api.ValueQueryRequest;
import org.uet.dse.ocl2cypher.core.CoreInterpreter;
import org.uet.dse.ocl2cypher.core.CoreLowering;
import org.uet.dse.ocl2cypher.core.CoreValidator;
import org.uet.dse.ocl2cypher.graph.GraphBuilder;
import org.uet.dse.ocl2cypher.qcyp.QCypTranslator;
import org.uet.dse.ocl2cypher.qcyp.QInterpreter;
import org.uet.dse.ocl2cypher.qcyp.QValidator;
import org.uet.dse.ocl2cypher.runtime.OclType;
import org.uet.dse.ocl2cypher.runtime.OclValue;

/** Differential T-2 witness: exact Core semantics versus Q semantics over the built graph. */
public final class T2SemanticPreservationCorpus {
    public record Counts(int expressions, int objectEvaluations,
                         int expressionRoots, int planRoots) {
    }

    private T2SemanticPreservationCorpus() {
    }

    public static Counts run() {
        var schema = N4SemanticPreservationCorpus.schema();
        var snapshot = N4SemanticPreservationCorpus.snapshot();
        var built = GraphBuilder.build(schema, snapshot);
        require(built.isSuccess(), "graph build: " + built.diagnostics());
        var graph = built.value().graph();

        int evaluations = 0;
        int expressionRoots = 0;
        int planRoots = 0;
        List<String> expressions = N4SemanticPreservationCorpus.expressions();
        for (String expression : expressions) {
            var frontend = FrontendCompiler.compileValueQuery(
                    ValueQueryRequest.contextual(expression, "Person"), schema);
            require(frontend.isSuccess(), expression + " frontend: " + frontend.diagnostics());
            var core = CoreLowering.lowerValueQuery(schema, frontend.value());
            require(core.isSuccess(), expression + " lowering: " + core.diagnostics());
            require(CoreValidator.validate(schema, core.value()).isEmpty(),
                    expression + " Core WF: " + CoreValidator.validate(schema, core.value()));

            var translated = QCypTranslator.translate(core.value());
            require(translated.isSuccess(), expression + " translation: " + translated.diagnostics());
            require(QValidator.validate(translated.value()).isEmpty(),
                    expression + " Q WF: " + QValidator.validate(translated.value()));
            if (translated.value().expressionBody() != null) expressionRoots++;
            else planRoots++;

            for (String identity : List.of("p1", "p2")) {
                OclValue self = new OclValue.ObjectValue(OclType.clazz("Person"), identity);
                CoreInterpreter.Env coreEnvironment = new CoreInterpreter.Env();
                coreEnvironment.bind(core.value().selfVariable(), self);
                OclValue coreValue = CoreInterpreter.evalUnit(schema, snapshot,
                        core.value(), coreEnvironment);

                CoreInterpreter.Env qEnvironment = new CoreInterpreter.Env();
                qEnvironment.bind(core.value().selfVariable(), self);
                OclValue qValue = QInterpreter.value(schema, graph, core.value(),
                        translated.value(), qEnvironment);

                require(coreValue.equals(qValue), expression + " on " + identity
                        + ": Core=" + coreValue + ", Q=" + qValue);
                require(coreValue.type() == qValue.type(), expression + " on " + identity
                        + ": result type identity was not preserved");
                evaluations++;
            }
        }
        require(expressionRoots > 0 && planRoots > 0,
                "corpus must exercise both expression and plan roots");
        return new Counts(expressions.size(), evaluations, expressionRoots, planRoots);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
