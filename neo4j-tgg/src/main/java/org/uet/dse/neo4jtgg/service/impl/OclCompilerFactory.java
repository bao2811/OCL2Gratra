package org.uet.dse.neo4jtgg.service.impl;

import org.tzi.use.uml.mm.MModel;
import org.uet.dse.neo4jtgg.ocl.OclMetamodelSnapshot;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Selects the M2 view used by the binder and rejects stale graph schemas. */
public final class OclCompilerFactory {
    private OclCompilerFactory() {
    }

    public enum Source { USE_MODEL, GRAPH_M2 }

    public record Selection(DefaultOclToCypherCompiler compiler,
                            Source source,
                            OclMetamodelSnapshot snapshot) {
        public Selection {
            Objects.requireNonNull(compiler, "compiler");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(snapshot, "snapshot");
        }
    }

    public static Selection fromUse(MModel model) {
        Objects.requireNonNull(model, "model");
        OclMetamodelSnapshot snapshot = OclMetamodelSnapshot.fromUse(model);
        return new Selection(new DefaultOclToCypherCompiler(model), Source.USE_MODEL, snapshot);
    }

    public static Selection fromGraph(MModel expectedModel, OclMetamodelSnapshot graphSnapshot) {
        Objects.requireNonNull(expectedModel, "expectedModel");
        Objects.requireNonNull(graphSnapshot, "graphSnapshot");
        OclMetamodelSnapshot expected = OclMetamodelSnapshot.fromUse(expectedModel);
        if (!expected.equals(graphSnapshot)) {
            throw new IllegalStateException("Graph-backed M2 differs from the loaded USE model: "
                    + difference(expected, graphSnapshot));
        }
        return new Selection(new DefaultOclToCypherCompiler(graphSnapshot),
                Source.GRAPH_M2, graphSnapshot);
    }

    private static String difference(OclMetamodelSnapshot expected,
                                     OclMetamodelSnapshot actual) {
        return "model=" + expected.modelName() + ", graphModel=" + actual.modelName()
                + diff("classes", expected.classes(), actual.classes())
                + diff("attributes", expected.attributes(), actual.attributes())
                + diff("generalizations", expected.generalizations(), actual.generalizations())
                + diff("associations", expected.associations(), actual.associations());
    }

    private static String diff(String label, java.util.List<?> expected, java.util.List<?> actual) {
        Set<Object> missing = new LinkedHashSet<>(expected);
        missing.removeAll(actual);
        Set<Object> spurious = new LinkedHashSet<>(actual);
        spurious.removeAll(expected);
        if (missing.isEmpty() && spurious.isEmpty()) return "";
        return "; " + label + " missing=" + missing + " spurious=" + spurious;
    }
}
