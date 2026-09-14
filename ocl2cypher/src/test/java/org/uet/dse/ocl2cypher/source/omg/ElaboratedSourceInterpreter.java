package org.uet.dse.ocl2cypher.source.omg;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.uet.dse.ocl2cypher.runtime.Boolean3;
import org.uet.dse.ocl2cypher.runtime.OclOps;
import org.uet.dse.ocl2cypher.runtime.OclType;
import org.uet.dse.ocl2cypher.runtime.OclValue;
import org.uet.dse.ocl2cypher.source.model.SchemaModel;
import org.uet.dse.ocl2cypher.source.model.Snapshot;

/**
 * Executable oracle for the elaborated OMG-AS denotation used by N-4 tests.
 *
 * <p>This interpreter deliberately does not call {@code CoreLowering} or
 * {@code CoreInterpreter}.  It follows references by {@link OmgAs.Variable}
 * identity and observes the source {@link Snapshot} directly, so a wrong
 * constructor mapping or binder mapping in N_SM is visible to the differential
 * test.  The shared {@link OclOps} module is the declared OCL_val primitive
 * algebra, not a lowering implementation.</p>
 */
public final class ElaboratedSourceInterpreter {

    private ElaboratedSourceInterpreter() {
    }

    public static final class Env {
        private final IdentityHashMap<OmgAs.Variable, OclValue> values;

        public Env() {
            values = new IdentityHashMap<>();
        }

        private Env(IdentityHashMap<OmgAs.Variable, OclValue> values) {
            this.values = values;
        }

        public void bind(OmgAs.Variable declaration, OclValue value) {
            values.put(Objects.requireNonNull(declaration), Objects.requireNonNull(value));
        }

        public Env child() {
            return new Env(new IdentityHashMap<>(values));
        }

        private OclValue lookup(OmgAs.Variable declaration) {
            OclValue value = values.get(declaration);
            if (value == null) {
                throw new IllegalStateException("unbound OMG declaration " + declaration.name);
            }
            return value;
        }
    }

    public static OclValue eval(SchemaModel schema, Snapshot snapshot, Env env,
                                OmgAs.OclExpression expression) {
        if (expression instanceof OmgAs.BooleanLiteralExp literal) {
            return literal.booleanSymbol ? Boolean3.TRUE : Boolean3.FALSE;
        }
        if (expression instanceof OmgAs.IntegerLiteralExp literal) {
            return new OclValue.IntegerValue(literal.integerSymbol);
        }
        if (expression instanceof OmgAs.RealLiteralExp literal) {
            return new OclValue.RealValue(literal.realSymbol);
        }
        if (expression instanceof OmgAs.StringLiteralExp literal) {
            return new OclValue.StringValue(literal.stringSymbol);
        }
        if (expression instanceof OmgAs.VariableExp variable) {
            return env.lookup(variable.referredVariable);
        }
        if (expression instanceof OmgAs.CoerceExp coercion) {
            return coerce(eval(schema, snapshot, env, coercion.sourceExpression),
                    coercion.type);
        }
        if (expression instanceof OmgAs.LetExp let) {
            OclValue initializer = eval(schema, snapshot, env,
                    let.variable.initExpression);
            if (!initializer.type().equals(let.variable.type)) {
                initializer = coerce(initializer, let.variable.type);
            }
            Env bodyEnvironment = env.child();
            bodyEnvironment.bind(let.variable, initializer);
            return eval(schema, snapshot, bodyEnvironment, let.in);
        }
        if (expression instanceof OmgAs.IfExp conditional) {
            OclValue condition = eval(schema, snapshot, env, conditional.condition);
            if (condition.isBottom()) {
                return new OclValue.BottomValue(conditional.type);
            }
            return Boolean3.boolOf(condition) == OclValue.BooleanValue.Bool3.TRUE
                    ? eval(schema, snapshot, env, conditional.thenExpression)
                    : eval(schema, snapshot, env, conditional.elseExpression);
        }
        if (expression instanceof OmgAs.CollectionLiteralExp collection) {
            List<OclValue> members = new ArrayList<>();
            for (OmgAs.CollectionLiteralPart part : collection.part) {
                if (!(part instanceof OmgAs.CollectionItem item)) {
                    throw new IllegalStateException("unadmitted collection range reached oracle");
                }
                members.add(eval(schema, snapshot, env, item.item));
            }
            return collection.kind == OmgAs.OmgCollectionKind.SET
                    ? new OclValue.SetValue(collection.type, members)
                    : new OclValue.BagValue(collection.type, members);
        }
        if (expression instanceof OmgAs.PropertyCallExp property) {
            return evalProperty(schema, snapshot, env, property);
        }
        if (expression instanceof OmgAs.AssociationClassCallExp navigation) {
            return evalAssociationClass(schema, snapshot, env, navigation);
        }
        if (expression instanceof OmgAs.OperationCallExp operation) {
            return evalOperation(schema, snapshot, env, operation);
        }
        if (expression instanceof OmgAs.IteratorExp iterator) {
            return evalIterator(schema, snapshot, env, iterator);
        }
        throw new IllegalStateException("no source-semantics case for "
                + expression.getClass().getSimpleName());
    }

    private static OclValue evalProperty(SchemaModel schema, Snapshot snapshot, Env env,
                                         OmgAs.PropertyCallExp property) {
        OclValue receiver = eval(schema, snapshot, env, property.source);
        if (receiver.isBottom()) return new OclValue.BottomValue(property.type);
        String stableId = ((OclValue.ObjectValue) receiver).stableId();
        var attribute = schema.attributeByKey(property.referredProperty);
        if (attribute != null) {
            return snapshot.attributeSlot(stableId, attribute.name())
                    .orElseGet(() -> new OclValue.BottomValue(property.type));
        }
        var navigation = schema.navigation(property.source.type.className(),
                property.navigationSource);
        if (navigation == null) {
            throw new IllegalStateException("unresolved source navigation "
                    + property.navigationSource);
        }
        List<OclValue> qualifiers = evalQualifiers(schema, snapshot, env,
                property.qualifier, property.type);
        if (qualifiers == null) return new OclValue.BottomValue(property.type);
        List<String> targets = snapshot.linkTargets(schema, navigation.roleName(), stableId,
                navigation.reverse(), qualifiers);
        return objectResult(property.type, targets);
    }

    private static OclValue evalAssociationClass(SchemaModel schema, Snapshot snapshot,
                                                 Env env,
                                                 OmgAs.AssociationClassCallExp navigation) {
        OclValue receiver = eval(schema, snapshot, env, navigation.source);
        if (receiver.isBottom()) return new OclValue.BottomValue(navigation.type);
        List<OclValue> qualifiers = evalQualifiers(schema, snapshot, env,
                navigation.qualifier, navigation.type);
        if (qualifiers == null) return new OclValue.BottomValue(navigation.type);
        List<String> objects = snapshot.associationClassObjects(schema,
                navigation.referredAssociationClass,
                ((OclValue.ObjectValue) receiver).stableId(), qualifiers);
        return objectResult(navigation.type, objects);
    }

    private static List<OclValue> evalQualifiers(SchemaModel schema, Snapshot snapshot,
                                                 Env env,
                                                 List<OmgAs.OclExpression> expressions,
                                                 OclType resultType) {
        List<OclValue> values = new ArrayList<>();
        for (OmgAs.OclExpression expression : expressions) {
            OclValue value = eval(schema, snapshot, env, expression);
            if (value.isBottom()) return null;
            values.add(value);
        }
        return values;
    }

    private static OclValue objectResult(OclType resultType, List<String> identities) {
        if (!resultType.isCollection()) {
            return identities.size() == 1
                    ? new OclValue.ObjectValue(resultType, identities.get(0))
                    : new OclValue.BottomValue(resultType);
        }
        List<OclValue> members = identities.stream()
                .map(identity -> (OclValue) new OclValue.ObjectValue(
                        resultType.elementType(), identity))
                .toList();
        return resultType.kind() == OclType.Kind.SET
                ? new OclValue.SetValue(resultType, members)
                : new OclValue.BagValue(resultType, members);
    }

    private static OclValue evalOperation(SchemaModel schema, Snapshot snapshot, Env env,
                                          OmgAs.OperationCallExp operation) {
        OclOperation kind = OclOperation.resolved(operation.referredOperation);
        if (kind == OclOperation.ALL_INSTANCES) {
            String classKey = ((OmgAs.TypeExp) operation.source).referredType;
            List<OclValue> instances = snapshot.objectsOfClass(schema, classKey).stream()
                    .map(identity -> (OclValue) new OclValue.ObjectValue(
                            OclType.clazz(classKey), identity))
                    .toList();
            return new OclValue.SetValue(OclType.set(OclType.clazz(classKey)), instances);
        }

        OclValue source = eval(schema, snapshot, env, operation.source);
        if (kind == OclOperation.OCL_IS_TYPE_OF
                || kind == OclOperation.OCL_IS_KIND_OF) {
            String target = ((OmgAs.TypeExp) operation.argument.get(0)).referredType;
            String dynamic = dynamicClass(snapshot, source);
            boolean matches = kind == OclOperation.OCL_IS_TYPE_OF
                    ? dynamic.equals(target) : schema.conforms(dynamic, target);
            return matches ? Boolean3.TRUE : Boolean3.FALSE;
        }
        if (kind == OclOperation.OCL_AS_TYPE) {
            String target = ((OmgAs.TypeExp) operation.argument.get(0)).referredType;
            if (source.isBottom()) return new OclValue.BottomValue(operation.type);
            String dynamic = dynamicClass(snapshot, source);
            return schema.conforms(dynamic, target)
                    ? new OclValue.ObjectValue(operation.type,
                            ((OclValue.ObjectValue) source).stableId())
                    : new OclValue.BottomValue(operation.type);
        }
        if (operation.argument.isEmpty()) {
            return unary(kind, source, operation.type);
        }
        OclValue argument = eval(schema, snapshot, env, operation.argument.get(0));
        return binary(kind, source, argument, operation.type);
    }

    private static String dynamicClass(Snapshot snapshot, OclValue source) {
        if (source instanceof OclValue.BottomValue) return source.type().className();
        OclValue.ObjectValue object = (OclValue.ObjectValue) source;
        return snapshot.hasObject(object.stableId())
                ? snapshot.object(object.stableId()).dynamicClassKey()
                : object.type().className();
    }

    private static OclValue unary(OclOperation operation, OclValue operand,
                                  OclType resultType) {
        if (operand.isBottom()) return new OclValue.BottomValue(resultType);
        return switch (operation) {
            case BOOLEAN_NOT -> OclOps.boolNot(operand);
            case NUMERIC_NEGATE -> OclOps.negate(operand, resultType);
            case NUMERIC_ABS -> OclOps.abs(operand, resultType);
            case REAL_FLOOR -> OclOps.floor(operand);
            case REAL_ROUND -> OclOps.round(operand);
            case COLLECTION_SIZE -> OclOps.size(operand, operand.type());
            case COLLECTION_IS_EMPTY -> OclOps.isEmpty(operand);
            case COLLECTION_NOT_EMPTY -> OclOps.notEmpty(operand);
            case COLLECTION_SUM -> OclOps.sum(operand, resultType);
            default -> throw new IllegalStateException("not an admitted unary operation: "
                    + operation);
        };
    }

    private static OclValue binary(OclOperation operation, OclValue left,
                                   OclValue right, OclType resultType) {
        return switch (operation) {
            case NUMERIC_ADD -> OclOps.numeric(left, right, resultType, "+");
            case NUMERIC_SUBTRACT -> OclOps.numeric(left, right, resultType, "-");
            case NUMERIC_MULTIPLY -> OclOps.numeric(left, right, resultType, "*");
            case REAL_DIVIDE -> OclOps.numeric(left, right, resultType, "/");
            case INTEGER_DIVIDE -> OclOps.numeric(left, right, resultType, "div");
            case INTEGER_MOD -> OclOps.numeric(left, right, resultType, "mod");
            case NUMERIC_MAX -> OclOps.numeric(left, right, resultType, "max");
            case NUMERIC_MIN -> OclOps.numeric(left, right, resultType, "min");
            case LESS_THAN -> OclOps.compare(left, right, "<");
            case LESS_THAN_OR_EQUAL -> OclOps.compare(left, right, "<=");
            case GREATER_THAN -> OclOps.compare(left, right, ">");
            case GREATER_THAN_OR_EQUAL -> OclOps.compare(left, right, ">=");
            case VALUE_EQUAL -> OclOps.equal(left, right);
            case VALUE_NOT_EQUAL -> OclOps.notEqual(left, right);
            case BOOLEAN_AND -> OclOps.boolAnd(left, right);
            case BOOLEAN_OR -> OclOps.boolOr(left, right);
            case BOOLEAN_XOR -> OclOps.boolXor(left, right);
            case BOOLEAN_IMPLIES -> OclOps.boolImplies(left, right);
            case COLLECTION_COUNT -> OclOps.count(left, right, left.type());
            case COLLECTION_INCLUDES -> OclOps.includes(left, right);
            case COLLECTION_EXCLUDES -> OclOps.excludes(left, right);
            case COLLECTION_INCLUDES_ALL -> OclOps.includesAll(left, right);
            case COLLECTION_EXCLUDES_ALL -> OclOps.excludesAll(left, right);
            case SET_UNION -> OclOps.setUnion(left, right, resultType);
            case SET_INTERSECTION -> OclOps.setIntersection(left, right, resultType);
            default -> throw new IllegalStateException("not an admitted binary operation: "
                    + operation);
        };
    }

    private static OclValue evalIterator(SchemaModel schema, Snapshot snapshot, Env env,
                                         OmgAs.IteratorExp iterator) {
        OclValue source = eval(schema, snapshot, env, iterator.source);
        if (source.isBottom()) return new OclValue.BottomValue(iterator.type);
        List<OclValue> occurrences = OclOps.occurrences(source);
        OmgAs.Variable binder = iterator.iterator.get(0);
        if (iterator.name.equals("exists") || iterator.name.equals("forAll")) {
            List<OclValue> predicates = new ArrayList<>();
            for (OclValue occurrence : occurrences) {
                Env bodyEnvironment = env.child();
                bodyEnvironment.bind(binder, occurrence);
                predicates.add(eval(schema, snapshot, bodyEnvironment, iterator.body));
            }
            return iterator.name.equals("exists")
                    ? OclOps.foldExists(predicates) : OclOps.foldForAll(predicates);
        }
        if (iterator.name.equals("collect")) {
            List<OclValue> images = new ArrayList<>();
            for (OclValue occurrence : occurrences) {
                Env bodyEnvironment = env.child();
                bodyEnvironment.bind(binder, occurrence);
                images.add(eval(schema, snapshot, bodyEnvironment, iterator.body));
            }
            return new OclValue.BagValue(iterator.type, images);
        }
        List<OclValue> kept = new ArrayList<>();
        for (OclValue occurrence : occurrences) {
            Env bodyEnvironment = env.child();
            bodyEnvironment.bind(binder, occurrence);
            OclValue predicate = eval(schema, snapshot, bodyEnvironment, iterator.body);
            if (predicate.isBottom()) return new OclValue.BottomValue(iterator.type);
            boolean truth = Boolean3.boolOf(predicate) == OclValue.BooleanValue.Bool3.TRUE;
            if ((iterator.name.equals("select") && truth)
                    || (iterator.name.equals("reject") && !truth)) {
                kept.add(occurrence);
            }
        }
        return iterator.type.kind() == OclType.Kind.SET
                ? new OclValue.SetValue(iterator.type, kept)
                : new OclValue.BagValue(iterator.type, kept);
    }

    private static OclValue coerce(OclValue source, OclType target) {
        if (source.isBottom()) return new OclValue.BottomValue(target);
        if (source instanceof OclValue.IntegerValue integer && target.equals(OclType.REAL)) {
            return new OclValue.RealValue(new BigDecimal(integer.value()));
        }
        if (source instanceof OclValue.ObjectValue object && target.isClass()) {
            return new OclValue.ObjectValue(target, object.stableId());
        }
        OclValue.CollectionValue collection = (OclValue.CollectionValue) source;
        List<OclValue> converted = new ArrayList<>();
        for (OclValue member : OclOps.occurrences(collection)) {
            converted.add(member.type().equals(target.elementType())
                    ? member : coerce(member, target.elementType()));
        }
        return target.kind() == OclType.Kind.SET
                ? new OclValue.SetValue(target, converted)
                : new OclValue.BagValue(target, converted);
    }
}
