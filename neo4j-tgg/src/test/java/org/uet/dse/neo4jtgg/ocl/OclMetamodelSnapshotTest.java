package org.uet.dse.neo4jtgg.ocl;

import org.junit.jupiter.api.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.uet.dse.neo4jtgg.service.impl.DefaultOclToCypherCompiler;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class OclMetamodelSnapshotTest {
    @Test
    void useAndGraphBackedViewsRoundTripAllBinderRelevantM2Facts() {
        MModel original = model();
        OclMetamodelSnapshot useView = OclMetamodelSnapshot.fromUse(original);
        OclMetamodelSnapshot reconstructedView = OclMetamodelSnapshot.fromUse(useView.toUseModel());

        assertEquals(useView, reconstructedView);
    }

    @Test
    void graphBackedViewProducesTheSameBoundCompilerObservables() {
        MModel original = model();
        OclMetamodelSnapshot graphView = OclMetamodelSnapshot.fromUse(original);
        String invariant = "context Library inv Qualified: self.book['HCM']->forAll(b | b.oclIsKindOf(Publication))";

        var useResult = new DefaultOclToCypherCompiler(original).compileInvariantInstrumented(invariant);
        var graphResult = new DefaultOclToCypherCompiler(graphView).compileInvariantInstrumented(invariant);

        assertEquals(useResult.cypher(), graphResult.cypher());
        assertEquals(useResult.parameters(), graphResult.parameters());
        assertEquals(useResult.bound().expression().type(), graphResult.bound().expression().type());
        assertEquals(useResult.validationAlgebra().predicate().type(), graphResult.validationAlgebra().predicate().type());
    }

    @Test
    void viewEqualityDetectsMissingGraphM2Facts() {
        OclMetamodelSnapshot source = OclMetamodelSnapshot.fromUse(model());
        var attributes = new ArrayList<>(source.attributes());
        attributes.remove(0);
        OclMetamodelSnapshot mutated = new OclMetamodelSnapshot(source.modelName(), source.classes(),
                attributes, source.generalizations(), source.associations());

        assertNotEquals(source, mutated);
    }

    private MModel model() {
        String specification = """
                model GraphBackedM2
                class Publication
                attributes
                    title : String
                end
                class Book < Publication
                end
                class Library
                attributes
                    name : String
                end
                association Catalog between
                    Library[1] role library qualifier (city : String)
                    Book[*] role book qualifier (shelf : String)
                end
                """;
        StringWriter diagnostics = new StringWriter();
        MModel model = USECompiler.compileSpecification(specification, "graph-backed-m2.use",
                new PrintWriter(diagnostics, true), new ModelFactory());
        assertNotNull(model, diagnostics.toString());
        return model;
    }
}
