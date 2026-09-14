package org.uet.dse.ocl2cypher.qcyp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.uet.dse.ocl2cypher.core.CoreDeclaration;
import org.uet.dse.ocl2cypher.core.CoreExpr;
import org.uet.dse.ocl2cypher.core.CoreInvariant;
import org.uet.dse.ocl2cypher.diagnostics.SourceSpan;
import org.uet.dse.ocl2cypher.graph.GraphBuilder;
import org.uet.dse.ocl2cypher.runtime.Boolean3;
import org.uet.dse.ocl2cypher.runtime.OclType;
import org.uet.dse.ocl2cypher.runtime.OclValue;
import org.uet.dse.ocl2cypher.source.model.SchemaModel;
import org.uet.dse.ocl2cypher.source.model.Snapshot;
import org.uet.dse.ocl2cypher.source.model.UmlAttribute;
import org.uet.dse.ocl2cypher.source.model.UmlClass;

class T7WrapperTest {
    private static final SourceSpan S = SourceSpan.UNKNOWN;

    @Test
    void violationWrapperReturnsExactlyFalseBottomAndAbsentContextIds() {
        int checked = 0;
        check(true, 0);
        checked++;
        for (int mask = 0; mask < 256; mask++) {
            check(false, mask);
            checked++;
        }
        assertEquals(257, checked);
    }

    private static void check(boolean emptyContext, int mask) {
        SchemaModel schema = SchemaModel.builder("t7-wrapper")
                .clazz(UmlClass.of("C"))
                .clazz(UmlClass.of("Left", "C"))
                .clazz(UmlClass.of("Right", "C"))
                .clazz(UmlClass.of("Leaf", "Left", "Right"))
                .clazz(UmlClass.of("Other"))
                .attribute(UmlAttribute.of("C", "ok", OclType.BOOLEAN))
                .build();
        Snapshot.Builder snapshot = Snapshot.builder().object("outside", "Other");
        Set<String> expected = new HashSet<>();
        String[] identities = {"base", "left", "right", "diamond"};
        String[] classes = {"C", "Left", "Right", "Leaf"};
        int remaining = mask;
        if (!emptyContext) {
            for (int index = 0; index < identities.length; index++) {
                int assignment = remaining % 4;
                remaining /= 4;
                snapshot.object(identities[index], classes[index]);
                if (assignment != 3) {
                    OclValue value = assignment == 0
                            ? Boolean3.TRUE
                            : assignment == 1
                                    ? Boolean3.FALSE
                                    : new OclValue.BottomValue(OclType.BOOLEAN);
                    snapshot.attribute(identities[index], "ok", value);
                }
                if (assignment != 0) {
                    expected.add(identities[index]);
                }
            }
        }

        var built = GraphBuilder.build(schema, snapshot.build());
        assertTrue(built.isSuccess(), () -> "graph build: " + built.diagnostics());
        CoreDeclaration self = new CoreDeclaration(
                1, "self", CoreDeclaration.Kind.SELF, OclType.clazz("C"));
        CoreExpr body = new CoreExpr.AttributeRead(
                S, new CoreExpr.Variable(S, self), "C", "ok", OclType.BOOLEAN);
        CoreInvariant invariant = new CoreInvariant("Check", "C", self, body);
        var translated = QCypTranslator.translate(invariant);
        assertTrue(translated.isSuccess(), () -> "translation: " + translated.diagnostics());

        var actual = QInterpreter.violations(
                schema, built.value().graph(), invariant, translated.value());
        assertEquals(expected, new HashSet<>(actual));
        assertEquals(expected.size(), actual.size(), "extent must not duplicate stable IDs");
    }
}
