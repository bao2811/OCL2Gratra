package org.uet.dse.neo4jtgg.ocl;

import org.neo4j.driver.QueryRunner;
import org.tzi.use.uml.mm.MAttribute;
import org.uet.dse.neo4jtgg.ocl.ir.OclIr;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Executable checker for the finite scalar domain used by the certified profile. */
public final class OclScalarClosureChecker {
    private static final BigInteger MIN_INT64 = BigInteger.valueOf(Long.MIN_VALUE);
    private static final BigInteger MAX_INT64 = BigInteger.valueOf(Long.MAX_VALUE);
    private static final BigDecimal MAX_EXACT_REAL_INTEGER = new BigDecimal("9007199254740992");
    private static final int MAX_CARTESIAN_OBSERVATIONS = 100_000;

    public enum Status { PASS, FAIL, OUT_OF_SCOPE }

    public record Result(Status status, List<String> violations) {
        public Result {
            violations = List.copyOf(violations);
        }

        public boolean passed() { return status == Status.PASS; }
    }

    @FunctionalInterface
    public interface AttributeValues {
        Collection<?> valuesOf(MAttribute attribute);
    }

    private OclScalarClosureChecker() {
    }

    public static Result checkValues(Collection<?> values, String location) {
        Inspection inspection = new Inspection(null);
        if (values != null) {
            for (Object value : values) inspection.checkScalar(value, location);
        }
        return inspection.result();
    }

    public static Result checkGraph(QueryRunner session, String modelKey) {
        if (session == null || modelKey == null || modelKey.isBlank()) {
            return new Result(Status.OUT_OF_SCOPE,
                    List.of("A live session and non-blank modelKey are required"));
        }
        List<Object> values = session.run(
                        "MATCH (av:AttributeValue {modelKey:$modelKey}) "
                                + "WHERE av.value IS NOT NULL RETURN av.value AS value",
                        Map.of("modelKey", modelKey))
                .list(record -> record.get("value").asObject());
        return checkValues(values, "stored AttributeValue.value");
    }

    public static Result check(OclIr.InvariantQuery invariant, AttributeValues observations) {
        if (invariant == null || invariant.predicate() == null) {
            return new Result(Status.OUT_OF_SCOPE, List.of("No invariant expression was supplied"));
        }
        Inspection inspection = new Inspection(observations);
        inspection.inspect(invariant.predicate(), new HashMap<>());
        return inspection.result();
    }

    public static void requireValuesClosed(Collection<?> values, String location) {
        requirePass(checkValues(values, location), "ScalarClosed");
    }

    public static void requireGraphClosed(QueryRunner session, String modelKey) {
        requirePass(checkGraph(session, modelKey), "ScalarClosed");
    }

    public static void requirePass(Result result, String premise) {
        if (!result.passed()) {
            throw new IllegalStateException(premise + "=" + result.status() + ": "
                    + String.join("; ", result.violations()));
        }
    }

    private static final class Inspection {
        private final AttributeValues observations;
        private final List<String> failures = new ArrayList<>();
        private final List<String> unknowns = new ArrayList<>();

        private Inspection(AttributeValues observations) {
            this.observations = observations;
        }

        private NumericValues inspect(OclIr.Expression expression, Map<String, NumericValues> environment) {
            if (expression == null) return NumericValues.empty();
            if (expression instanceof OclIr.Literal literal) {
                checkScalar(literal.value(), "OCL literal");
                return numeric(literal.value(), literal.type());
            }
            if (expression instanceof OclIr.Variable variable) {
                if (isNumeric(variable.type())) {
                    NumericValues values = environment.get(variable.name());
                    if (values == null) unknowns.add("No observed numeric domain for variable " + variable.name());
                    return values;
                }
                return NumericValues.empty();
            }
            if (expression instanceof OclIr.AttributeAccess attribute) {
                inspect(attribute.source(), environment);
                if (observations == null) {
                    if (isNumeric(attribute.type())) unknowns.add("No observations for attribute " + attribute.attributeName());
                    return null;
                }
                Collection<?> values = observations.valuesOf(attribute.attribute());
                if (values == null) {
                    unknowns.add("No observations for attribute " + attribute.attributeName());
                    return null;
                }
                NumericValues numericValues = NumericValues.empty();
                for (Object value : values) {
                    checkScalar(value, "attribute " + attribute.attributeName());
                    NumericValues next = numeric(value, attribute.type());
                    if (next != null) numericValues = numericValues.merge(next);
                }
                return numericValues;
            }
            if (expression instanceof OclIr.Binary binary) {
                NumericValues left = inspect(binary.left(), environment);
                NumericValues right = inspect(binary.right(), environment);
                if (Set.of("+", "-", "*", "/").contains(binary.operator())) {
                    return arithmetic(binary, left, right);
                }
                return NumericValues.empty();
            }
            if (expression instanceof OclIr.Not value) {
                inspect(value.expression(), environment); return NumericValues.empty();
            }
            if (expression instanceof OclIr.SetLiteral value) {
                NumericValues result = NumericValues.empty();
                for (OclIr.Expression element : value.elements()) {
                    NumericValues next = inspect(element, environment);
                    if (next != null) result = result.merge(next);
                }
                return result;
            }
            if (expression instanceof OclIr.If value) {
                inspect(value.condition(), environment);
                NumericValues thenValues = inspect(value.thenBranch(), environment);
                NumericValues elseValues = inspect(value.elseBranch(), environment);
                return mergeNullable(thenValues, elseValues);
            }
            if (expression instanceof OclIr.Let value) {
                NumericValues bound = inspect(value.value(), environment);
                Map<String, NumericValues> nested = new HashMap<>(environment);
                if (bound != null) nested.put(value.variableName(), bound);
                return inspect(value.body(), nested);
            }
            if (expression instanceof OclIr.IteratorOperation value) {
                NumericValues source = inspect(value.source(), environment);
                Map<String, NumericValues> nested = new HashMap<>(environment);
                if (source != null) nested.put(value.iteratorName(), source);
                return inspect(value.body(), nested);
            }
            if (expression instanceof OclIr.NavigationAccess value) {
                inspect(value.source(), environment);
                value.qualifiers().forEach(item -> inspect(item, environment));
                return NumericValues.empty();
            }
            if (expression instanceof OclIr.MethodCall value) {
                inspect(value.source(), environment);
                value.arguments().forEach(item -> inspect(item, environment));
                return NumericValues.empty();
            }
            if (expression instanceof OclIr.CollectionOperation value) {
                inspect(value.source(), environment);
                value.arguments().forEach(item -> inspect(item, environment));
                return NumericValues.empty();
            }
            return NumericValues.empty();
        }

        private NumericValues arithmetic(OclIr.Binary binary, NumericValues left, NumericValues right) {
            if (left == null || right == null) {
                unknowns.add("Arithmetic operator " + binary.operator() + " has an unobserved operand domain");
                return null;
            }
            if ((long) left.values().size() * right.values().size() > MAX_CARTESIAN_OBSERVATIONS) {
                unknowns.add("Arithmetic observation product exceeds " + MAX_CARTESIAN_OBSERVATIONS);
                return null;
            }
            boolean realResult = "Real".equals(binary.type().typeName()) || "/".equals(binary.operator());
            Set<BigDecimal> results = new LinkedHashSet<>();
            for (BigDecimal l : left.values()) {
                for (BigDecimal r : right.values()) {
                    if (realResult) {
                        requireExactIntegerConversion(l, binary.left().type(), binary.operator());
                        requireExactIntegerConversion(r, binary.right().type(), binary.operator());
                        double ld = l.doubleValue();
                        double rd = r.doubleValue();
                        if ("/".equals(binary.operator()) && rd == 0.0d) {
                            failures.add("Division by zero is reachable");
                            continue;
                        }
                        double result = switch (binary.operator()) {
                            case "+" -> ld + rd;
                            case "-" -> ld - rd;
                            case "*" -> ld * rd;
                            case "/" -> ld / rd;
                            default -> throw new IllegalStateException(binary.operator());
                        };
                        if (!Double.isFinite(result)) failures.add("Non-finite Real result is reachable for " + binary.operator());
                        else results.add(BigDecimal.valueOf(result));
                    } else {
                        BigInteger li = l.toBigIntegerExact();
                        BigInteger ri = r.toBigIntegerExact();
                        BigInteger result = switch (binary.operator()) {
                            case "+" -> li.add(ri);
                            case "-" -> li.subtract(ri);
                            case "*" -> li.multiply(ri);
                            default -> throw new IllegalStateException(binary.operator());
                        };
                        if (result.compareTo(MIN_INT64) < 0 || result.compareTo(MAX_INT64) > 0) {
                            failures.add("Int64 overflow is reachable for " + binary.operator());
                        } else results.add(new BigDecimal(result));
                    }
                }
            }
            return new NumericValues(results);
        }

        private void requireExactIntegerConversion(BigDecimal value, OclTypeBinding type, String operator) {
            if ("Integer".equals(type.typeName()) && value.abs().compareTo(MAX_EXACT_REAL_INTEGER) > 0) {
                failures.add("Inexact Integer-to-Real conversion is reachable for " + operator + ": " + value);
            }
        }

        private void checkScalar(Object value, String location) {
            if (value == null || value instanceof Boolean) return;
            if (value instanceof String string) {
                if (!isUnicodeScalarSequence(string)) failures.add(location + " contains an unpaired UTF-16 surrogate");
                return;
            }
            if (value instanceof Collection<?> collection) {
                collection.forEach(element -> checkScalar(element, location + " element"));
                return;
            }
            if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) return;
            if (value instanceof BigInteger integer) {
                if (integer.compareTo(MIN_INT64) < 0 || integer.compareTo(MAX_INT64) > 0)
                    failures.add(location + " is outside Int64: " + integer);
                return;
            }
            if (value instanceof Double number) {
                if (!Double.isFinite(number)) failures.add(location + " is not finite Real64: " + number);
                return;
            }
            if (value instanceof Float number) {
                if (!Float.isFinite(number)) failures.add(location + " is not finite Real32: " + number);
                return;
            }
            if (value instanceof BigDecimal decimal) {
                if (!Double.isFinite(decimal.doubleValue())) failures.add(location + " is outside finite Real64: " + decimal);
                return;
            }
            if (!OclBottomToken.isToken(value)) failures.add(location + " has unsupported scalar type " + value.getClass().getName());
        }

        private NumericValues numeric(Object value, OclTypeBinding type) {
            if (!(value instanceof Number number) || !isNumeric(type)) return NumericValues.empty();
            try {
                return new NumericValues(Set.of(number instanceof BigDecimal decimal
                        ? decimal : number instanceof BigInteger integer
                        ? new BigDecimal(integer) : new BigDecimal(number.toString())));
            } catch (NumberFormatException exception) {
                failures.add("Numeric value cannot be represented in the checked domain: " + value);
                return NumericValues.empty();
            }
        }

        private Result result() {
            if (!failures.isEmpty()) return new Result(Status.FAIL, failures);
            if (!unknowns.isEmpty()) return new Result(Status.OUT_OF_SCOPE, unknowns);
            return new Result(Status.PASS, List.of());
        }

        private boolean isUnicodeScalarSequence(String value) {
            for (int index = 0; index < value.length(); index++) {
                char current = value.charAt(index);
                if (Character.isHighSurrogate(current)) {
                    if (++index >= value.length() || !Character.isLowSurrogate(value.charAt(index))) return false;
                } else if (Character.isLowSurrogate(current)) return false;
            }
            return true;
        }
    }

    private static boolean isNumeric(OclTypeBinding type) {
        return type != null && ("Integer".equals(type.typeName()) || "Real".equals(type.typeName()));
    }

    private static NumericValues mergeNullable(NumericValues left, NumericValues right) {
        if (left == null || right == null) return null;
        return left.merge(right);
    }

    private record NumericValues(Set<BigDecimal> values) {
        private NumericValues { values = Set.copyOf(values); }
        private static NumericValues empty() { return new NumericValues(Set.of()); }
        private NumericValues merge(NumericValues other) {
            Set<BigDecimal> merged = new LinkedHashSet<>(values);
            merged.addAll(other.values);
            return new NumericValues(merged);
        }
    }
}
