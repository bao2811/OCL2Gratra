package org.uet.dse.neo4jtgg.service.impl;

import org.junit.jupiter.api.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.uet.dse.neo4jtgg.ocl.OclMetamodelSnapshot;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OclCompilerFactoryTest {
    @Test
    void graphAndUseM2ProduceTheSameCompilation() {
        MModel model = model();
        var use = OclCompilerFactory.fromUse(model);
        var graph = OclCompilerFactory.fromGraph(model, OclMetamodelSnapshot.fromUse(model));

        var useResult = use.compiler().compileInvariantInstrumented(
                "context Person inv Adult: self.age >= 18");
        var graphResult = graph.compiler().compileInvariantInstrumented(
                "context Person inv Adult: self.age >= 18");

        assertEquals(OclCompilerFactory.Source.USE_MODEL, use.source());
        assertEquals(OclCompilerFactory.Source.GRAPH_M2, graph.source());
        // Bound nodes retain parser-object identity, so compare their semantic shape downstream.
        assertEquals(useResult.bound().expression().getClass(), graphResult.bound().expression().getClass());
        assertEquals(useResult.validationAlgebra(), graphResult.validationAlgebra());
        assertEquals(useResult.normalizedValidationAlgebra(), graphResult.normalizedValidationAlgebra());
        assertEquals(useResult.cypher(), graphResult.cypher());
        assertEquals(useResult.parameters(), graphResult.parameters());
    }

    @Test
    void staleGraphM2IsRejectedBeforeBinding() {
        MModel model = model();
        OclMetamodelSnapshot expected = OclMetamodelSnapshot.fromUse(model);
        var classes = new ArrayList<>(expected.classes());
        classes.removeIf(value -> value.name().equals("Person"));
        OclMetamodelSnapshot stale = new OclMetamodelSnapshot(expected.modelName(), classes,
                expected.attributes(), expected.generalizations(), expected.associations());

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> OclCompilerFactory.fromGraph(model, stale));
        assertTrue(failure.getMessage().contains("classes missing="));
        assertTrue(failure.getMessage().contains("Person"));
    }

    private MModel model() {
        String source = """
                model CompilerSource
                class Person
                attributes
                    age : Integer
                end
                """;
        StringWriter diagnostics = new StringWriter();
        MModel model = USECompiler.compileSpecification(source, "CompilerSource.use",
                new PrintWriter(diagnostics, true), new ModelFactory());
        if (model == null) throw new AssertionError(diagnostics.toString());
        return model;
    }
}
