package org.uet.dse.ocl2cypher;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.uet.dse.ocl2cypher.api.FrontendCompiler;
import org.uet.dse.ocl2cypher.core.CoreLowering;
import org.uet.dse.ocl2cypher.cypher.CypherAst;
import org.uet.dse.ocl2cypher.cypher.Realization;
import org.uet.dse.ocl2cypher.cypher.Serializer;
import org.uet.dse.ocl2cypher.graph.GraphBuilder;
import org.uet.dse.ocl2cypher.qcyp.QCypTranslator;
import org.uet.dse.ocl2cypher.runtime.OclType;
import org.uet.dse.ocl2cypher.runtime.OclValue;
import org.uet.dse.ocl2cypher.source.model.SchemaModel;
import org.uet.dse.ocl2cypher.source.model.Snapshot;
import org.uet.dse.ocl2cypher.source.model.UmlAttribute;
import org.uet.dse.ocl2cypher.source.model.UmlClass;
import org.uet.dse.ocl2cypher.trace.Trace;
import org.uet.dse.ocl2cypher.trace.TraceCollector;

/** Rule-08 evidence: successful boundaries form a traceable E/N/T/R/S chain. */
class TracePipelineTest {

    @Test
    void successfulPipelineRecordsAllBoundariesWithoutChangingCypher() {
        SchemaModel schema = SchemaModel.builder("trace-model")
                .clazz(UmlClass.of("Person"))
                .attribute(UmlAttribute.of("Person", "active", OclType.BOOLEAN))
                .build();
        Snapshot snapshot = Snapshot.builder()
                .object("p", "Person")
                .attribute("p", "active", new OclValue.BooleanValue(
                        OclType.BOOLEAN, OclValue.BooleanValue.Bool3.TRUE))
                .build();
        TraceCollector traces = new TraceCollector();

        var frontend = FrontendCompiler.compile(
                "context Person inv Active: self.active", schema, traces);
        assertTrue(frontend.isSuccess(), () -> frontend.diagnostics().toString());
        var document = frontend.value().get(0);
        var core = CoreLowering.lower(schema, document, document.constraints.get(0), traces);
        assertTrue(core.isSuccess(), () -> core.diagnostics().toString());
        var query = QCypTranslator.translate(core.value(), traces);
        assertTrue(query.isSuccess(), () -> query.diagnostics().toString());
        var graph = GraphBuilder.build(schema, snapshot);
        assertTrue(graph.isSuccess(), () -> graph.diagnostics().toString());
        var artifact = Realization.realize(query.value(), graph.value().graph(),
                CypherAst.Dialect.CYPHER_5, traces);
        assertTrue(artifact.isSuccess(), () -> artifact.diagnostics().toString());

        String ordinary = Serializer.serialize(artifact.value()).cypherText();
        String traced = Serializer.serialize(artifact.value(), traces).cypherText();
        assertEquals(ordinary, traced, "trace metadata must be serialization-transparent");
        assertTrue(artifact.value().query().span().isKnown());

        List<Trace.Stage> stages = traces.traces().stream().map(Trace::stage).distinct().toList();
        assertEquals(List.of(Trace.Stage.E_SM, Trace.Stage.N_SM, Trace.Stage.T_G,
                Trace.Stage.R, Trace.Stage.S), stages);
        assertTrue(traces.traces().stream().allMatch(t -> !t.ruleId().isBlank()));
        List<Trace> chain = traces.traces().stream()
                .filter(t -> !t.ruleId().equals("E-DOC")).toList();
        for (int i = 0; i + 1 < chain.size(); i++) {
            assertEquals(chain.get(i).targetId(), chain.get(i + 1).sourceId(),
                    "trace chain breaks between " + chain.get(i).stage()
                            + " and " + chain.get(i + 1).stage());
        }
    }

    @Test
    void failedBoundaryDoesNotCreateSuccessfulTrace() {
        SchemaModel schema = SchemaModel.builder("trace-model")
                .clazz(UmlClass.of("Person")).build();
        TraceCollector traces = new TraceCollector();
        var failure = FrontendCompiler.compile("context Missing inv Bad: true", schema, traces);
        assertTrue(failure.isFailure());
        assertTrue(traces.traces().isEmpty());
    }
}
