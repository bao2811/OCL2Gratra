package org.uet.dse.neo4jtgg.ocl;

import org.neo4j.driver.Session;
import org.tzi.use.uml.mm.MAttribute;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uml.sys.MSystem;
import org.uet.dse.neo4j.encoding.CanonicalGraphEncoding;
import org.uet.dse.neo4j.helper.ValueMapper;
import org.uet.dse.neo4jtgg.ocl.ir.OclIr;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-execution witnesses for the dynamic ScalarClosed and generated-bottom
 * premises of the certified invariant pipeline.
 *
 * <p>The graph scan in {@link OclScalarClosureChecker#checkGraph} validates the
 * stored scalar domain only.  This checker additionally supplies those values
 * to the invariant-aware checker so arithmetic reachability (division by zero,
 * overflow, finite Real results, and exact numeric coercion) is evaluated for
 * the actual normalized validation algebra.</p>
 */
public final class OclExecutionPremiseChecker {
    private OclExecutionPremiseChecker() {
    }

    /** Checks expression-level ScalarClosed against values in a native USE state. */
    public static OclScalarClosureChecker.Result checkUseSystemScalarClosed(
            MSystem system, OclIr.InvariantQuery invariant) {
        if (system == null) {
            return new OclScalarClosureChecker.Result(
                    OclScalarClosureChecker.Status.OUT_OF_SCOPE,
                    List.of("A native USE system is required for expression-level ScalarClosed"));
        }
        return OclScalarClosureChecker.check(invariant,
                attribute -> observedUseValues(system, attribute));
    }

    public static void requireUseSystemScalarClosed(MSystem system, OclIr.InvariantQuery invariant) {
        OclScalarClosureChecker.requirePass(
                checkUseSystemScalarClosed(system, invariant), "ScalarClosed");
    }

    /**
     * Checks expression-level ScalarClosed using exact canonical attribute keys
     * from one graph model.  The observed domain is intentionally a safe
     * over-approximation: all stored values of each referenced attribute are
     * considered, even when a navigation predicate would make some pairs
     * unreachable.
     */
    public static OclScalarClosureChecker.Result checkGraphScalarClosed(
            Session session, String modelName, OclIr.InvariantQuery invariant) {
        if (session == null || modelName == null || modelName.isBlank()) {
            return new OclScalarClosureChecker.Result(
                    OclScalarClosureChecker.Status.OUT_OF_SCOPE,
                    List.of("A live session and non-blank model name are required for expression-level ScalarClosed"));
        }
        String modelKey = CanonicalGraphEncoding.modelKey(modelName);
        Map<MAttribute, Collection<?>> observations = new HashMap<>();
        return OclScalarClosureChecker.check(invariant,
                attribute -> observations.computeIfAbsent(attribute,
                        ignored -> observedGraphValues(session, modelName, modelKey, attribute)));
    }

    public static void requireGraphScalarClosed(
            Session session, String modelName, OclIr.InvariantQuery invariant) {
        OclScalarClosureChecker.requirePass(
                checkGraphScalarClosed(session, modelName, invariant), "ScalarClosed");
    }

    /**
     * Ensures renderer-created parameters contain at most one canonical bottom
     * token and never contain a nested/look-alike token.
     */
    public static void requireGeneratedBottomSeparated(Map<String, Object> parameters) {
        OclBottomToken.requireWellFormedGeneratedParameters(parameters);
        if (parameters == null) {
            return;
        }
        long tokenCount = parameters.values().stream().filter(OclBottomToken::isToken).count();
        if (tokenCount > 1) {
            throw new IllegalStateException(
                    "BottomSeparated=FAIL: generated parameters contain more than one canonical bottom token");
        }
    }

    private static List<Object> observedUseValues(MSystem system, MAttribute requiredAttribute) {
        List<Object> values = new ArrayList<>();
        var state = system.state();
        for (MObject object : state.allObjects()) {
            for (MAttribute attribute : object.cls().allAttributes()) {
                if (attribute.equals(requiredAttribute)) {
                    values.add(ValueMapper.mapUseValue(object.state(state).attributeValue(attribute)));
                }
            }
        }
        return values;
    }

    private static List<Object> observedGraphValues(
            Session session, String modelName, String modelKey, MAttribute attribute) {
        String attributeKey = CanonicalGraphEncoding.attributeKey(
                modelName, attribute.owner().name(), attribute.name());
        return session.run(
                        "MATCH (:Object {modelKey:$modelKey})-[:ObjectHasAttribute]->"
                                + "(value:AttributeValue {modelKey:$modelKey, attributeKey:$attributeKey}) "
                                + "RETURN value.value AS value",
                        Map.of("modelKey", modelKey, "attributeKey", attributeKey))
                .list(record -> record.get("value").isNull()
                        ? null : record.get("value").asObject());
    }
}
