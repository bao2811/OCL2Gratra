package org.uet.dse.ocl2cypher.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.uet.dse.ocl2cypher.diagnostics.Result;
import org.uet.dse.ocl2cypher.runtime.Boolean3;
import org.uet.dse.ocl2cypher.runtime.OclType;
import org.uet.dse.ocl2cypher.runtime.OclValue;
import org.uet.dse.ocl2cypher.source.model.SchemaModel;
import org.uet.dse.ocl2cypher.source.model.Snapshot;
import org.uet.dse.ocl2cypher.source.model.QualifierValue;
import org.uet.dse.ocl2cypher.source.model.UmlAssociation;
import org.uet.dse.ocl2cypher.source.model.UmlAttribute;
import org.uet.dse.ocl2cypher.source.model.UmlClass;
import org.uet.dse.ocl2cypher.source.model.UmlQualifier;

/** Characterization and round-trip tests for the graph attribute codec boundary. */
class GraphCodecRoundTripTest {

    @Test
    void booleanTrueAndFalseRoundTripWithoutChangingMeaning() {
        SchemaModel sm = personSchema(
                UmlAttribute.of("Person", "active", OclType.BOOLEAN));
        Snapshot sn = Snapshot.builder()
                .object("alice", "Person")
                .object("bob", "Person")
                .attribute("alice", "active", Boolean3.TRUE)
                .attribute("bob", "active", Boolean3.FALSE)
                .build();

        GraphModel graph = successfulGraph(GraphBuilder.build(sm, sn));

        assertEquals(Boolean3.TRUE,
                GraphObservation.attribute(graph, sm, "alice", "active"));
        assertEquals(Boolean3.FALSE,
                GraphObservation.attribute(graph, sm, "bob", "active"));
    }

    @Test
    void definedStringEqualToLegacyBottomSentinelRemainsDefined() {
        SchemaModel sm = personSchema(
                UmlAttribute.of("Person", "token", OclType.STRING));
        Snapshot sn = Snapshot.builder()
                .object("alice", "Person")
                .attribute("alice", "token", new OclValue.StringValue("__BOTTOM__"))
                .build();

        GraphModel graph = successfulGraph(GraphBuilder.build(sm, sn));

        assertEquals(new OclValue.StringValue("__BOTTOM__"),
                GraphObservation.attribute(graph, sm, "alice", "token"));
    }

    @Test
    void objectValuedAttributeRoundTripsAsObjectIdentity() {
        OclType person = OclType.clazz("Person");
        SchemaModel sm = personSchema(UmlAttribute.of("Person", "manager", person));
        Snapshot sn = Snapshot.builder()
                .object("alice", "Person")
                .object("bob", "Person")
                .attribute("alice", "manager", new OclValue.ObjectValue(person, "bob"))
                .build();

        GraphModel graph = successfulGraph(GraphBuilder.build(sm, sn));

        assertEquals(new OclValue.ObjectValue(person, "bob"),
                GraphObservation.attribute(graph, sm, "alice", "manager"));
    }

    @Test
    void allAtomicCarriersAndTypedBottomRoundTrip() {
        SchemaModel sm = SchemaModel.builder("m")
                .clazz(UmlClass.of("Person"))
                .attribute(UmlAttribute.of("Person", "i", OclType.INTEGER))
                .attribute(UmlAttribute.of("Person", "r", OclType.REAL))
                .attribute(UmlAttribute.of("Person", "missing", OclType.STRING))
                .build();
        Snapshot sn = Snapshot.builder()
                .object("alice", "Person")
                .attribute("alice", "i", new OclValue.IntegerValue(
                        new BigInteger("123456789012345678901234567890")))
                .attribute("alice", "r", new OclValue.RealValue(
                        new BigDecimal("-1234567890.0012300")))
                .attribute("alice", "missing", new OclValue.BottomValue(OclType.STRING))
                .build();

        GraphModel graph = successfulGraph(GraphBuilder.build(sm, sn));

        assertEquals(sn.attributeSlot("alice", "i").orElseThrow(),
                GraphObservation.attribute(graph, sm, "alice", "i"));
        assertEquals(sn.attributeSlot("alice", "r").orElseThrow(),
                GraphObservation.attribute(graph, sm, "alice", "r"));
        assertEquals(new OclValue.BottomValue(OclType.STRING),
                GraphObservation.attribute(graph, sm, "alice", "missing"));
    }

    @Test
    void danglingObjectAttributeIsRejectedAtCodecBoundary() {
        OclType person = OclType.clazz("Person");
        SchemaModel sm = personSchema(UmlAttribute.of("Person", "manager", person));
        Snapshot sn = Snapshot.builder()
                .object("alice", "Person")
                .attribute("alice", "manager", new OclValue.ObjectValue(person, "ghost"))
                .build();

        Result<GraphBuilder.GraphBuildArtifact> result = GraphBuilder.build(sm, sn);

        assertTrue(result.isFailure());
        assertEquals("G_CODEC_REFERENCE", result.primaryDiagnostic().code());
    }

    @Test
    void objectAttributeTargetMustConformToDeclaredClass() {
        OclType person = OclType.clazz("Person");
        SchemaModel sm = SchemaModel.builder("m")
                .clazz(UmlClass.of("Person"))
                .clazz(UmlClass.of("Company"))
                .attribute(UmlAttribute.of("Person", "manager", person))
                .build();
        Snapshot sn = Snapshot.builder()
                .object("alice", "Person")
                .object("acme", "Company")
                .attribute("alice", "manager", new OclValue.ObjectValue(person, "acme"))
                .build();

        Result<GraphBuilder.GraphBuildArtifact> result = GraphBuilder.build(sm, sn);

        assertTrue(result.isFailure());
        assertEquals("G_CODEC_REFERENCE_TYPE", result.primaryDiagnostic().code());
    }

    @Test
    void typedBottomUsesStateAndNeverAReservedPayload() {
        SchemaModel sm = personSchema(
                UmlAttribute.of("Person", "token", OclType.STRING));
        Snapshot sn = Snapshot.builder()
                .object("alice", "Person")
                .attribute("alice", "token", new OclValue.BottomValue(OclType.STRING))
                .build();

        GraphModel graph = successfulGraph(GraphBuilder.build(sm, sn));
        GraphModel.Node slot = graph.node(
                GraphKey.slot("m", "alice", "Person::token"));

        assertEquals(GraphValueCodec.BOTTOM,
                slot.properties().get(GraphValueCodec.VALUE_STATE));
        assertFalse(slot.properties().containsKey(GraphValueCodec.PAYLOAD));
        assertEquals(new OclValue.BottomValue(OclType.STRING),
                GraphObservation.attribute(graph, sm, "alice", "token"));
    }

    @Test
    void decoderRejectsNonCanonicalBooleanInsteadOfGuessingFalse() {
        Map<String, String> properties = Map.of(
                GraphValueCodec.VALUE_STATE, GraphValueCodec.DEFINED,
                GraphValueCodec.VALUE_TYPE, OclType.BOOLEAN.toString(),
                GraphValueCodec.CODEC_ID, GraphValueCodec.codecId(OclType.BOOLEAN),
                GraphValueCodec.PAYLOAD, "TRUE");

        GraphValueCodec.CodecException error = assertThrows(
                GraphValueCodec.CodecException.class,
                () -> GraphValueCodec.decode(OclType.BOOLEAN, properties));

        assertEquals("G_CODEC_BOOLEAN", error.code());
    }

    @Test
    void validRepRejectsMutatedCodecMetadata() {
        SchemaModel sm = personSchema(
                UmlAttribute.of("Person", "active", OclType.BOOLEAN));
        Snapshot sn = Snapshot.builder()
                .object("alice", "Person")
                .attribute("alice", "active", Boolean3.TRUE)
                .build();
        GraphModel original = successfulGraph(GraphBuilder.build(sm, sn));
        GraphModel mutated = copyWithSlotProperty(original,
                GraphKey.slot("m", "alice", "Person::active"),
                GraphValueCodec.CODEC_ID,
                "ocl-string-v1");

        Result<ValidRepChecker.Witness> result = ValidRepChecker.check(sm, sn, mutated);

        assertTrue(result.isFailure());
        assertEquals("G_CODEC_ID", result.primaryDiagnostic().code());
    }

    @Test
    void everyExecutableAtomicCodecHasRoundTripAndPairwiseInjectiveImages() {
        OclType person = OclType.clazz("Person");
        Map<OclType, List<OclValue>> families = Map.of(
                OclType.BOOLEAN, List.of(Boolean3.TRUE, Boolean3.FALSE,
                        new OclValue.BottomValue(OclType.BOOLEAN)),
                OclType.INTEGER, List.of(
                        new OclValue.IntegerValue(BigInteger.ZERO),
                        new OclValue.IntegerValue(new BigInteger("-123456789012345678901234567890")),
                        new OclValue.IntegerValue(new BigInteger("123456789012345678901234567890")),
                        new OclValue.BottomValue(OclType.INTEGER)),
                OclType.REAL, List.of(
                        new OclValue.RealValue(new BigDecimal("0.0")),
                        new OclValue.RealValue(new BigDecimal("-0.0012300")),
                        new OclValue.RealValue(new BigDecimal("12345678901234567890.12500")),
                        new OclValue.BottomValue(OclType.REAL)),
                OclType.STRING, List.of(
                        new OclValue.StringValue(""),
                        new OclValue.StringValue("__BOTTOM__"),
                        new OclValue.StringValue("Unicode 🚗\n:value"),
                        new OclValue.BottomValue(OclType.STRING)),
                person, List.of(
                        new OclValue.ObjectValue(person, "person::one"),
                        new OclValue.ObjectValue(person, "person:two"),
                        new OclValue.BottomValue(person)));

        for (Map.Entry<OclType, List<OclValue>> family : families.entrySet()) {
            List<GraphValueCodec.EncodedValue> images = family.getValue().stream()
                    .map(value -> GraphValueCodec.encode(family.getKey(), value)).toList();
            for (int i = 0; i < family.getValue().size(); i++) {
                GraphValueCodec.EncodedValue encoded = images.get(i);
                Map<String, String> properties = new LinkedHashMap<>();
                properties.put(GraphValueCodec.VALUE_STATE, encoded.state());
                properties.put(GraphValueCodec.VALUE_TYPE, encoded.typeTag());
                properties.put(GraphValueCodec.CODEC_ID, encoded.codecId());
                if (encoded.payload() != null) {
                    properties.put(GraphValueCodec.PAYLOAD, encoded.payload());
                }
                assertEquals(family.getValue().get(i),
                        GraphValueCodec.decode(family.getKey(), properties));
                for (int j = i + 1; j < images.size(); j++) {
                    assertFalse(encoded.equals(images.get(j)),
                            () -> "codec collision for " + family.getKey());
                }
            }
        }
    }

    @Test
    void objectValuedQualifierUsesDeclaredCodecAndStableIdentity() {
        OclType person = OclType.clazz("Person");
        SchemaModel sm = SchemaModel.builder("m")
                .clazz(UmlClass.of("Company"))
                .clazz(UmlClass.of("Employee"))
                .clazz(UmlClass.of("Person"))
                .association(new UmlAssociation("employment-key", "employment",
                        "Company", "employer", 0, -1,
                        "Employee", "employees", 0, -1,
                        List.of(UmlQualifier.typed("reviewer", person)), false, true))
                .build();
        OclValue.ObjectValue reviewer = new OclValue.ObjectValue(person, "reviewer::1");
        Snapshot sn = Snapshot.builder()
                .object("company", "Company")
                .object("employee", "Employee")
                .object("reviewer::1", "Person")
                .link("employment", "company", "employee",
                        List.of(new QualifierValue("reviewer", reviewer)))
                .build();

        GraphModel graph = successfulGraph(GraphBuilder.build(sm, sn));
        GraphModel.Relationship link = graph.relationships().stream()
                .filter(r -> GraphModel.LINK_ASSOCIATE_WITH.equals(r.physicalType()))
                .findFirst().orElseThrow();

        assertEquals("reviewer::1", link.properties().get("qualifier::0"));
        assertEquals(List.of("employee"), GraphObservation.linkTargets(
                graph, sm, "company", "employees", false, List.of(reviewer)));
    }

    private static SchemaModel personSchema(UmlAttribute attribute) {
        return SchemaModel.builder("m")
                .clazz(UmlClass.of("Person"))
                .attribute(attribute)
                .build();
    }

    private static GraphModel successfulGraph(Result<GraphBuilder.GraphBuildArtifact> result) {
        assertTrue(result.isSuccess(), () -> result.isFailure()
                ? result.primaryDiagnostic().toString() : "expected successful graph build");
        return result.value().graph();
    }

    private static GraphModel copyWithSlotProperty(GraphModel source, String slotKey,
                                                    String property, String value) {
        GraphModel copy = new GraphModel(source.modelKey());
        for (GraphModel.Node node : source.nodes()) {
            Map<String, String> properties = new LinkedHashMap<>(node.properties());
            if (node.stableKey().equals(slotKey)) {
                properties.put(property, value);
            }
            copy.addNode(new GraphModel.Node(node.stableKey(), node.modelKey(),
                    node.projection(), node.observationRole(), node.labels(), properties));
        }
        for (GraphModel.Relationship relationship : source.relationships()) {
            copy.addRelationship(relationship);
        }
        return copy;
    }
}
