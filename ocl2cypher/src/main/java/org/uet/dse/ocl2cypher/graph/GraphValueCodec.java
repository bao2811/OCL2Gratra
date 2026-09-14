package org.uet.dse.ocl2cypher.graph;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;
import java.util.Objects;
import org.uet.dse.ocl2cypher.runtime.Boolean3;
import org.uet.dse.ocl2cypher.runtime.OclType;
import org.uet.dse.ocl2cypher.runtime.OclValue;
import org.uet.dse.ocl2cypher.source.model.SchemaModel;
import org.uet.dse.ocl2cypher.source.model.Snapshot;

/**
 * Total codec for scalar UML attribute slots in the current graph profile.
 *
 * <p>Bottom is represented by {@link #VALUE_STATE}, never by a payload token.
 * Consequently every Java string, including the legacy {@code __BOTTOM__}
 * sentinel, remains a defined OCL String.  The codec and type tags make a slot
 * self-describing enough for {@code ValidRep} to reject a mismatched graph
 * instead of silently guessing a decoder from the payload.
 */
public final class GraphValueCodec {

    public static final String VALUE_STATE = "valueState";
    public static final String VALUE_TYPE = "valueType";
    public static final String CODEC_ID = "codecId";
    public static final String PAYLOAD = "value";
    public static final String DEFINED = "DEFINED";
    public static final String BOTTOM = "BOTTOM";

    private GraphValueCodec() {
    }

    /** Encoded scalar. Payload is absent exactly for typed bottom. */
    public record EncodedValue(String codecId, String typeTag, String state, String payload) {
        public EncodedValue {
            Objects.requireNonNull(codecId, "codecId");
            Objects.requireNonNull(typeTag, "typeTag");
            Objects.requireNonNull(state, "state");
            if (DEFINED.equals(state)) {
                Objects.requireNonNull(payload, "defined payload");
            } else if (!BOTTOM.equals(state) || payload != null) {
                throw new IllegalArgumentException("invalid encoded slot state");
            }
        }
    }

    /** Exception with a stable boundary diagnostic code. */
    public static final class CodecException extends IllegalArgumentException {
        private final String code;

        public CodecException(String code, String message) {
            super(message);
            this.code = Objects.requireNonNull(code, "code");
        }

        public String code() {
            return code;
        }
    }

    public static EncodedValue encode(OclType declared, OclValue value) {
        Objects.requireNonNull(declared, "declared type");
        Objects.requireNonNull(value, "value");
        requireAtomic(declared);
        if (!declared.equals(value.type())) {
            throw error("G_CODEC_TYPE", "slot value type " + value.type()
                    + " differs from declared type " + declared);
        }
        String codec = codecId(declared);
        if (value instanceof OclValue.BottomValue) {
            return new EncodedValue(codec, declared.toString(), BOTTOM, null);
        }
        String payload = switch (declared.kind()) {
            case BOOLEAN -> {
                if (!(value instanceof OclValue.BooleanValue booleanValue)) {
                    throw wrongCarrier(declared, value);
                }
                yield switch (booleanValue.bool()) {
                    case TRUE -> "true";
                    case FALSE -> "false";
                    case BOTTOM -> throw error("G_CODEC_BOOLEAN",
                            "Boolean bottom must use BottomValue(Boolean)");
                };
            }
            case INTEGER -> {
                if (!(value instanceof OclValue.IntegerValue integerValue)) {
                    throw wrongCarrier(declared, value);
                }
                yield integerValue.value().toString();
            }
            case REAL -> {
                if (!(value instanceof OclValue.RealValue realValue)) {
                    throw wrongCarrier(declared, value);
                }
                try {
                    yield realValue.value().toPlainString();
                } catch (ArithmeticException nonDecimal) {
                    throw error("G_CODEC_REPRESENTABILITY",
                            "Real is not representable by the finite-decimal storage codec");
                }
            }
            case STRING -> {
                if (!(value instanceof OclValue.StringValue stringValue)) {
                    throw wrongCarrier(declared, value);
                }
                yield stringValue.value();
            }
            case CLASS -> {
                if (!(value instanceof OclValue.ObjectValue objectValue)) {
                    throw wrongCarrier(declared, value);
                }
                yield objectValue.stableId();
            }
            case SET, BAG -> throw error("G_CODEC_UNSUPPORTED_TYPE",
                    "collection-valued attributes are outside the graph codec profile");
        };
        return new EncodedValue(codec, declared.toString(), DEFINED, payload);
    }

    public static OclValue decode(OclType declared, Map<String, String> properties) {
        Objects.requireNonNull(declared, "declared type");
        Objects.requireNonNull(properties, "slot properties");
        requireAtomic(declared);
        String expectedCodec = codecId(declared);
        String actualCodec = properties.get(CODEC_ID);
        if (!expectedCodec.equals(actualCodec)) {
            throw error("G_CODEC_ID", "expected codec " + expectedCodec
                    + ", found " + actualCodec);
        }
        String actualType = properties.get(VALUE_TYPE);
        if (!declared.toString().equals(actualType)) {
            throw error("G_CODEC_TYPE", "expected type tag " + declared
                    + ", found " + actualType);
        }
        String state = properties.get(VALUE_STATE);
        if (BOTTOM.equals(state)) {
            if (properties.containsKey(PAYLOAD)) {
                throw error("G_CODEC_BOTTOM_PAYLOAD", "bottom slot must not carry a payload");
            }
            return new OclValue.BottomValue(declared);
        }
        if (!DEFINED.equals(state)) {
            throw error("G_CODEC_STATE", "unknown or missing value state: " + state);
        }
        if (!properties.containsKey(PAYLOAD)) {
            throw error("G_CODEC_MISSING_PAYLOAD", "defined slot has no payload");
        }
        String payload = properties.get(PAYLOAD);
        try {
            return switch (declared.kind()) {
                case BOOLEAN -> switch (payload) {
                    case "true" -> Boolean3.TRUE;
                    case "false" -> Boolean3.FALSE;
                    default -> throw error("G_CODEC_BOOLEAN",
                            "Boolean payload must be canonical true or false");
                };
                case INTEGER -> new OclValue.IntegerValue(new BigInteger(payload));
                case REAL -> new OclValue.RealValue(new BigDecimal(payload));
                case STRING -> new OclValue.StringValue(payload);
                case CLASS -> new OclValue.ObjectValue(declared, payload);
                case SET, BAG -> throw error("G_CODEC_UNSUPPORTED_TYPE",
                        "collection-valued attributes are outside the graph codec profile");
            };
        } catch (NumberFormatException e) {
            throw error("G_CODEC_NUMBER", "invalid " + declared + " payload: " + payload);
        }
    }

    /** Validate the referential part that cannot be checked by the scalar codec alone. */
    public static void validateObjectReference(OclType declared, OclValue value,
                                               SchemaModel schema, Snapshot snapshot) {
        if (!declared.isClass() || value instanceof OclValue.BottomValue) {
            return;
        }
        if (!(value instanceof OclValue.ObjectValue objectValue)) {
            throw wrongCarrier(declared, value);
        }
        if (!snapshot.hasObject(objectValue.stableId())) {
            throw error("G_CODEC_REFERENCE", "object attribute references missing object "
                    + objectValue.stableId());
        }
        String dynamicClass = snapshot.object(objectValue.stableId()).dynamicClassKey();
        if (!schema.conforms(dynamicClass, declared.className())) {
            throw error("G_CODEC_REFERENCE_TYPE", "object attribute target "
                    + objectValue.stableId() + " has dynamic class " + dynamicClass
                    + ", which does not conform to " + declared.className());
        }
    }

    public static String codecId(OclType type) {
        requireAtomic(type);
        return switch (type.kind()) {
            case BOOLEAN -> "ocl-boolean-v1";
            case INTEGER -> "ocl-integer-decimal-v1";
            case REAL -> "ocl-real-decimal-v1";
            case STRING -> "ocl-string-v1";
            case CLASS -> "ocl-object-reference-v1";
            case SET, BAG -> throw error("G_CODEC_UNSUPPORTED_TYPE",
                    "collection-valued attributes are outside the graph codec profile");
        };
    }

    private static void requireAtomic(OclType type) {
        if (type.isCollection()) {
            throw error("G_CODEC_UNSUPPORTED_TYPE",
                    "collection-valued attributes are outside the graph codec profile");
        }
    }

    private static CodecException wrongCarrier(OclType declared, OclValue value) {
        return error("G_CODEC_CARRIER", "wrong runtime carrier "
                + value.getClass().getSimpleName() + " for " + declared);
    }

    private static CodecException error(String code, String message) {
        return new CodecException(code, message);
    }
}
