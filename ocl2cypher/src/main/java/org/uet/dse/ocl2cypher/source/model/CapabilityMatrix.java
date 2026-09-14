package org.uet.dse.ocl2cypher.source.model;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import org.uet.dse.ocl2cypher.source.omg.OclOperation;

/**
 * Closed, fail-safe operation capability manifest for the executable OCL slice.
 *
 * <p>Every source operation records a surface witness and the expected
 * constructor family at the AS/Core/Q/R/S boundaries. Tests compile every
 * supported witness through the complete pipeline and parse the generated
 * Cypher with Neo4j's parser. A new enum member remains rejected until its row
 * is explicitly reviewed.</p>
 */
public final class CapabilityMatrix {

    public record Capability(boolean admitted,
                             String surfaceWitness,
                             String asConstructor,
                             String coreConstructor,
                             String qFamily,
                             String realizationRule,
                             String serializerSupport,
                             String oracleCoverage,
                             String diagnosticCode,
                             String testId,
                             String reason) {
        /** True when the witness contains Real syntax requiring an exact certificate. */
        public boolean requiresExactNumericCertificate() {
            return surfaceWitness != null && surfaceWitness.matches(".*\\d+\\.\\d+.*");
        }
        public Capability {
            Objects.requireNonNull(asConstructor, "asConstructor");
            Objects.requireNonNull(testId, "testId");
            Objects.requireNonNull(reason, "reason");
            if (admitted) {
                Objects.requireNonNull(surfaceWitness, "surfaceWitness");
                Objects.requireNonNull(coreConstructor, "coreConstructor");
                Objects.requireNonNull(qFamily, "qFamily");
                Objects.requireNonNull(realizationRule, "realizationRule");
                Objects.requireNonNull(serializerSupport, "serializerSupport");
                Objects.requireNonNull(oracleCoverage, "oracleCoverage");
                if (diagnosticCode != null) {
                    throw new IllegalArgumentException(
                            "supported capability cannot declare a rejection diagnostic");
                }
            } else if (diagnosticCode == null || diagnosticCode.isBlank()) {
                throw new IllegalArgumentException(
                        "rejected capability requires a stable diagnostic code");
            }
        }
    }

    private record Witness(String body, String core, String q) {
    }

    private static final Map<OclOperation, Capability> ENTRIES = build();

    private CapabilityMatrix() {
    }

    public static Capability capability(OclOperation operation) {
        return ENTRIES.get(Objects.requireNonNull(operation, "operation"));
    }

    public static boolean isAdmitted(OclOperation operation) {
        return capability(operation).admitted();
    }

    public static Map<OclOperation, Capability> entries() {
        return ENTRIES;
    }

    private static Map<OclOperation, Capability> build() {
        EnumMap<OclOperation, Capability> entries = new EnumMap<>(OclOperation.class);
        for (OclOperation operation : OclOperation.values()) {
            entries.put(operation, rejected(operation));
        }

        support(entries, OclOperation.BOOLEAN_NOT,
                witness("not false", "CoreExpr.Unary", "QExpr.Unary"));
        support(entries, OclOperation.BOOLEAN_AND,
                witness("true and false", "CoreExpr.Binary", "QExpr.Binary"));
        support(entries, OclOperation.BOOLEAN_OR,
                witness("true or false", "CoreExpr.Binary", "QExpr.Binary"));
        support(entries, OclOperation.BOOLEAN_XOR,
                witness("true xor false", "CoreExpr.Binary", "QExpr.Binary"));
        support(entries, OclOperation.BOOLEAN_IMPLIES,
                witness("false implies true", "CoreExpr.Binary", "QExpr.Binary"));
        support(entries, OclOperation.VALUE_EQUAL,
                witness("1 = 1", "CoreExpr.Binary", "QExpr.Binary"));
        support(entries, OclOperation.VALUE_NOT_EQUAL,
                witness("1 <> 2", "CoreExpr.Binary", "QExpr.Binary"));
        support(entries, OclOperation.LESS_THAN,
                witness("1 < 2", "CoreExpr.Binary", "QExpr.Binary"));
        support(entries, OclOperation.LESS_THAN_OR_EQUAL,
                witness("1 <= 2", "CoreExpr.Binary", "QExpr.Binary"));
        support(entries, OclOperation.GREATER_THAN,
                witness("2 > 1", "CoreExpr.Binary", "QExpr.Binary"));
        support(entries, OclOperation.GREATER_THAN_OR_EQUAL,
                witness("2 >= 1", "CoreExpr.Binary", "QExpr.Binary"));
        support(entries, OclOperation.NUMERIC_ADD,
                witness("1 + 2 = 3", "CoreExpr.Binary", "QExpr.Binary"));
        support(entries, OclOperation.NUMERIC_SUBTRACT,
                witness("3 - 2 = 1", "CoreExpr.Binary", "QExpr.Binary"));
        support(entries, OclOperation.NUMERIC_MULTIPLY,
                witness("2 * 3 = 6", "CoreExpr.Binary", "QExpr.Binary"));
        support(entries, OclOperation.REAL_DIVIDE,
                witness("4.0 / 2.0 = 2.0", "CoreExpr.Binary", "QExpr.Binary"));
        support(entries, OclOperation.INTEGER_DIVIDE,
                witness("7 div 2 = 3", "CoreExpr.Binary", "QExpr.Binary"));
        support(entries, OclOperation.INTEGER_MOD,
                witness("7 mod 2 = 1", "CoreExpr.Binary", "QExpr.Binary"));
        support(entries, OclOperation.NUMERIC_NEGATE,
                witness("-1 < 0", "CoreExpr.Unary", "QExpr.Unary"));
        support(entries, OclOperation.NUMERIC_ABS,
                witness("(-2).abs() = 2", "CoreExpr.Unary", "QExpr.Unary"));
        support(entries, OclOperation.REAL_FLOOR,
                witness("1.9.floor() = 1", "CoreExpr.Unary", "QExpr.Unary"));
        support(entries, OclOperation.REAL_ROUND,
                witness("1.5.round() = 2", "CoreExpr.Unary", "QExpr.Unary"));
        support(entries, OclOperation.NUMERIC_MAX,
                witness("1.max(2) = 2", "CoreExpr.Binary", "QExpr.Binary"));
        support(entries, OclOperation.NUMERIC_MIN,
                witness("1.min(2) = 1", "CoreExpr.Binary", "QExpr.Binary"));
        support(entries, OclOperation.COLLECTION_SIZE,
                witness("Set{1, 2}->size() = 2", "CoreExpr.Unary", "QExpr.Unary"));
        support(entries, OclOperation.COLLECTION_IS_EMPTY,
                witness("Set{1}->isEmpty()", "CoreExpr.Unary", "QExpr.Unary"));
        support(entries, OclOperation.COLLECTION_NOT_EMPTY,
                witness("Bag{1}->notEmpty()", "CoreExpr.Unary", "QExpr.Unary"));
        support(entries, OclOperation.COLLECTION_SUM,
                witness("Bag{1, 2}->sum() = 3", "CoreExpr.Unary", "QExpr.Unary"));
        support(entries, OclOperation.COLLECTION_COUNT,
                witness("Bag{1, 1}->count(1) = 2", "CoreExpr.Binary", "QExpr.CountFamily"));
        support(entries, OclOperation.COLLECTION_INCLUDES,
                witness("Set{1, 2}->includes(1)", "CoreExpr.Binary", "QExpr.IncludesFamily"));
        support(entries, OclOperation.COLLECTION_EXCLUDES,
                witness("Set{1, 2}->excludes(3)", "CoreExpr.Binary", "QExpr.IncludesFamily"));
        support(entries, OclOperation.COLLECTION_INCLUDES_ALL,
                witness("Set{1, 2}->includesAll(Set{2})", "CoreExpr.Binary",
                        "QExpr.IncludesFamily"));
        support(entries, OclOperation.COLLECTION_EXCLUDES_ALL,
                witness("Bag{1, 2}->excludesAll(Bag{3})", "CoreExpr.Binary",
                        "QExpr.IncludesFamily"));
        support(entries, OclOperation.SET_UNION,
                witness("Set{1}->union(Set{2})->size() = 2", "CoreExpr.Binary",
                        "QExpr.SetAlgebra"));
        support(entries, OclOperation.SET_INTERSECTION,
                witness("Set{1, 2}->intersection(Set{2})->size() = 1",
                        "CoreExpr.Binary", "QExpr.SetAlgebra"));
        support(entries, OclOperation.ALL_INSTANCES,
                witness("Person.allInstances()->notEmpty()", "CoreExpr.AllInstances",
                        "QPlan.ScanClass"));
        support(entries, OclOperation.OCL_IS_TYPE_OF,
                witness("self.oclIsTypeOf(Person)", "CoreExpr.TypeTest", "QExpr.TypeTest"));
        support(entries, OclOperation.OCL_IS_KIND_OF,
                witness("self.oclIsKindOf(Person)", "CoreExpr.TypeTest", "QExpr.TypeTest"));
        support(entries, OclOperation.OCL_AS_TYPE,
                witness("self.oclAsType(Person) = self", "CoreExpr.TypeCast", "QExpr.TypeCast"));

        return Map.copyOf(entries);
    }

    private static Witness witness(String body, String core, String q) {
        return new Witness(body, core, q);
    }

    private static Capability rejected(OclOperation operation) {
        return new Capability(false, null, "OmgAs.OperationCallExp", null, null,
                null, null, null, "N_UNSUPPORTED_OPERATION",
                "CAP-REJECT-" + operation.name(),
                "operation has no reviewed end-to-end capability row");
    }

    private static void support(EnumMap<OclOperation, Capability> entries,
                                OclOperation operation, Witness witness) {
        entries.put(operation, new Capability(true, witness.body(),
                "OmgAs.OperationCallExp(referredOperation=" + operation.name() + ")",
                witness.core(), witness.q(), "R_OP_" + operation.name(),
                "CypherAst+Serializer/CYPHER_5",
                "E/N/T/R/S+CypherParser; independent differential pending", null,
                "CAP-SUPPORTED-" + operation.name(),
                "covered by the current executable slice"));
    }
}
