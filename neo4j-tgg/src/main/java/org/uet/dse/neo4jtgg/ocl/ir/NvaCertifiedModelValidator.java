package org.uet.dse.neo4jtgg.ocl.ir;

import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Executable {@code EcoreConforms_NVA + WF_NVA + NF_R} validator. */
public final class NvaCertifiedModelValidator {
    public static final String PROFILE = "OCL_VAL_FINITE_SET_V1";
    public static final String VERSION = "nva-certified-v1";
    public static final String PRODUCER = "t-norm-v1";

    private static final Set<String> BOOLEAN_RESULTS = Set.of(
            "NvaNot", "NvaAnd", "NvaOr", "NvaCompare", "NvaExists",
            "NvaIsUnique", "NvaSetRelation", "NvaTypeKindOf");
    private static final Set<String> SET_RESULTS = Set.of(
            "NvaSetLiteral", "NvaNavigationMany", "NvaViewSet", "NvaSelect",
            "NvaCollect", "NvaSetCombination", "NvaAsSet", "NvaAllInstances");
    private static final Set<String> COLLECTION_CONSUMERS = Set.of(
            "NvaViewSet", "NvaExists", "NvaSelect", "NvaCollect", "NvaIsUnique",
            "NvaSetRelation", "NvaSetCombination", "NvaAsSet", "NvaCount");

    private NvaCertifiedModelValidator() {
    }

    public static Report validate(Path ecorePath, Path xmiPath) throws IOException {
        DynamicEmfModelValidator.LoadedModel loaded = DynamicEmfModelValidator.load(ecorePath, xmiPath);
        List<Issue> issues = new ArrayList<>();
        loaded.issues().forEach(issue -> issues.add(new Issue(issue.code(), issue.path(), issue.message())));
        EObject root = loaded.root();
        if (root == null) return new Report(loaded, issues);
        if (!"NvaModule".equals(root.eClass().getName())) {
            issues.add(new Issue("WF_NVA_ROOT", "$", "Root must be NvaModule"));
            return new Report(loaded, issues);
        }
        requireExact(root, "languageVersion", VERSION, "WF_NVA_VERSION", "$", issues);
        requireExact(root, "certificationProfile", PROFILE, "WF_NVA_PROFILE", "$", issues);
        validateUniqueIds(root, issues);
        validateTree(root, "$", new ArrayDeque<>(), issues);
        return new Report(loaded, issues);
    }

    /**
     * A semantic witness is deliberately external to the NVA model. Passing
     * structural validation alone never certifies denotational correctness.
     */
    public static CertificationReport certify(Path ecorePath, Path xmiPath,
                                               SemanticRefinementWitness witness) throws IOException {
        Report structural = validate(ecorePath, xmiPath);
        List<Issue> issues = new ArrayList<>(structural.issues());
        if (structural.valid() && (witness == null || !witness.refines(structural.model().root()))) {
            issues.add(new Issue("REF_OVA_NVA", "$",
                    "A successful external T_NORM denotational-refinement witness is required"));
        }
        return new CertificationReport(structural, issues);
    }

    private static void validateUniqueIds(EObject root, List<Issue> issues) {
        Set<String> ids = new HashSet<>();
        List<EObject> objects = new ArrayList<>();
        objects.add(root);
        TreeIterator<EObject> iterator = root.eAllContents();
        while (iterator.hasNext()) objects.add(iterator.next());
        for (EObject object : objects) {
            for (EAttribute attribute : object.eClass().getEAllAttributes()) {
                if (!attribute.isID()) continue;
                Object value = object.eGet(attribute);
                if (!(value instanceof String id) || id.isBlank()) {
                    issues.add(new Issue("WF_NVA_ID", object.eClass().getName(),
                            attribute.getName() + " must be nonblank"));
                } else if (!ids.add(id)) {
                    issues.add(new Issue("WF_NVA_ID", object.eClass().getName(),
                            "Duplicate ID " + id));
                }
            }
        }
    }

    private static void validateTree(EObject object, String path, Deque<Map<String, EObject>> scopes,
                                     List<Issue> issues) {
        String kind = object.eClass().getName();
        if ("NvaInvariant".equals(kind)) {
            requireExact(object, "producerVersion", PRODUCER, "WF_NVA_PRODUCER", path, issues);
            EObject self = child(object, "selfVariable");
            EObject predicate = child(object, "predicate");
            Map<String, EObject> scope = declarationScope(self, path + ".selfVariable", issues);
            scopes.push(scope);
            validateTree(self, path + ".selfVariable", scopes, issues);
            validateTree(predicate, path + ".predicate", scopes, issues);
            requireType(predicate, "BOOLEAN", "WF_NVA_INVARIANT_TYPE", path + ".predicate", issues);
            scopes.pop();
            return;
        }

        if ("NvaLet".equals(kind)) {
            validateTree(child(object, "resultType"), path + ".resultType", scopes, issues);
            validateTree(child(object, "value"), path + ".value", scopes, issues);
            EObject declaration = child(object, "declaration");
            Map<String, EObject> scope = declarationScope(declaration, path + ".declaration", issues);
            scopes.push(scope);
            validateTree(declaration, path + ".declaration", scopes, issues);
            validateTree(child(object, "body"), path + ".body", scopes, issues);
            scopes.pop();
            validateExpression(object, path, issues);
            return;
        }

        if (isIterator(kind)) {
            validateTree(child(object, "resultType"), path + ".resultType", scopes, issues);
            validateTree(child(object, "source"), path + ".source", scopes, issues);
            validateTree(child(object, "sourceCollectionType"), path + ".sourceCollectionType", scopes, issues);
            EObject declaration = child(object, "iterator");
            Map<String, EObject> scope = declarationScope(declaration, path + ".iterator", issues);
            scopes.push(scope);
            validateTree(declaration, path + ".iterator", scopes, issues);
            validateTree(child(object, "body"), path + ".body", scopes, issues);
            scopes.pop();
            if (!"NvaCollect".equals(kind)) {
                requireType(child(object, "body"), "BOOLEAN", "WF_NVA_ITERATOR_BODY", path + ".body", issues);
            }
            validateExpression(object, path, issues);
            return;
        }

        if ("NvaVariable".equals(kind)) validateVariable(object, path, scopes, issues);
        if (isExpression(object)) validateExpression(object, path, issues);

        for (EReference reference : object.eClass().getEAllContainments()) {
            Object value = object.eGet(reference);
            if (reference.isMany()) {
                int index = 0;
                for (Object item : (List<?>) value) {
                    validateTree((EObject) item, path + "." + reference.getName() + "[" + index++ + "]", scopes, issues);
                }
            } else if (value instanceof EObject child) {
                validateTree(child, path + "." + reference.getName(), scopes, issues);
            }
        }
    }

    private static void validateExpression(EObject expression, String path, List<Issue> issues) {
        String kind = expression.eClass().getName();
        EObject resultType = child(expression, "resultType");
        if (BOOLEAN_RESULTS.contains(kind)) requireType(expression, "BOOLEAN", "WF_NVA_TYPE", path, issues);
        if (SET_RESULTS.contains(kind)) requireType(expression, "SET", "WF_NVA_TYPE", path, issues);
        if ("NvaCount".equals(kind)) requireType(expression, "INTEGER", "WF_NVA_TYPE", path, issues);
        if (COLLECTION_CONSUMERS.contains(kind)) {
            EObject sourceCollectionType = child(expression, "sourceCollectionType");
            requireTypeObject(sourceCollectionType, "SET", "WF_NVA_COLLECTION", path + ".sourceCollectionType", issues);
            EObject source = child(expression, Set.of("NvaSetRelation", "NvaSetCombination").contains(kind)
                    ? "left" : "source");
            if (source != null && sourceCollectionType != null
                    && !sameType(child(source, "resultType"), sourceCollectionType)) {
                issues.add(new Issue("WF_NVA_COLLECTION", path,
                        "sourceCollectionType must equal the source result type"));
            }
        }
        if ("NvaSetLiteral".equals(kind) && list(expression, "elements").isEmpty()) {
            issues.add(new Issue("WF_NVA_SET", path, "Certified Set literal must be nonempty"));
        }
        if ("NvaLiteral".equals(kind)) {
            String literalKind = string(expression, "kind");
            if (("NULL".equals(literalKind) || "BOTTOM".equals(literalKind))
                    && resultType != null && "SET".equals(string(resultType, "kind"))) {
                issues.add(new Issue("WF_NVA_BOTTOM", path,
                        "Whole-collection bottom is represented by a typed branch, not a Set literal"));
            }
        }
        if ("NvaCompare".equals(kind) && isCountNavigationRedex(expression)) {
            issues.add(new Issue("WF_NVA_REDEX", path,
                    "Count(NavigationMany)>0/=0 must be normalized to Exists/Not(Exists)"));
        }
    }

    private static boolean isCountNavigationRedex(EObject compare) {
        String operator = string(compare, "operator");
        if (!("GT".equals(operator) || "EQ".equals(operator))) return false;
        EObject left = child(compare, "left");
        EObject right = child(compare, "right");
        if (left == null || right == null || !"NvaCount".equals(left.eClass().getName())
                || !"NvaLiteral".equals(right.eClass().getName())) return false;
        EObject source = child(left, "source");
        return source != null && "NvaNavigationMany".equals(source.eClass().getName())
                && "0".equals(string(right, "lexicalValue"));
    }

    private static void validateVariable(EObject variable, String path, Deque<Map<String, EObject>> scopes,
                                         List<Issue> issues) {
        EObject declaration = child(variable, "declaration");
        String symbol = declaration == null ? null : string(declaration, "symbolId");
        EObject resolved = null;
        for (Map<String, EObject> scope : scopes) {
            if (scope.containsKey(symbol)) {
                resolved = scope.get(symbol);
                break;
            }
        }
        if (declaration == null || resolved != declaration) {
            issues.add(new Issue("WF_NVA_SCOPE", path,
                    "Variable must reference the nearest in-scope declaration"));
        }
    }

    private static Map<String, EObject> declarationScope(EObject declaration, String path, List<Issue> issues) {
        Map<String, EObject> scope = new HashMap<>();
        if (declaration == null) {
            issues.add(new Issue("WF_NVA_SCOPE", path, "Missing declaration"));
            return scope;
        }
        String symbol = string(declaration, "symbolId");
        if (symbol == null || symbol.isBlank()) {
            issues.add(new Issue("WF_NVA_SCOPE", path, "Declaration symbolId must be nonblank"));
        } else {
            scope.put(symbol, declaration);
        }
        return scope;
    }

    private static boolean isIterator(String kind) {
        return Set.of("NvaExists", "NvaSelect", "NvaCollect", "NvaIsUnique").contains(kind);
    }

    private static boolean isExpression(EObject object) {
        return object.eClass().getEAllSuperTypes().stream().anyMatch(type -> "NvaExpression".equals(type.getName()));
    }

    private static void requireType(EObject expression, String expected, String code, String path,
                                    List<Issue> issues) {
        requireTypeObject(child(expression, "resultType"), expected, code, path + ".resultType", issues);
    }

    private static void requireTypeObject(EObject type, String expected, String code, String path,
                                          List<Issue> issues) {
        if (type == null || !expected.equals(string(type, "kind"))) {
            issues.add(new Issue(code, path, "Expected type kind " + expected));
        }
    }

    private static boolean sameType(EObject left, EObject right) {
        if (left == right) return true;
        if (left == null || right == null) return false;
        return java.util.Objects.equals(string(left, "kind"), string(right, "kind"))
                && java.util.Objects.equals(string(left, "typeName"), string(right, "typeName"))
                && sameType(child(left, "elementType"), child(right, "elementType"));
    }

    private static void requireExact(EObject object, String feature, String expected, String code,
                                     String path, List<Issue> issues) {
        if (!expected.equals(string(object, feature))) {
            issues.add(new Issue(code, path + "." + feature,
                    "Expected '" + expected + "' but found '" + string(object, feature) + "'"));
        }
    }

    private static EObject child(EObject object, String name) {
        if (object == null) return null;
        EStructuralFeature feature = object.eClass().getEStructuralFeature(name);
        return feature == null ? null : (EObject) object.eGet(feature);
    }

    @SuppressWarnings("unchecked")
    private static List<EObject> list(EObject object, String name) {
        EStructuralFeature feature = object.eClass().getEStructuralFeature(name);
        return feature == null ? List.of() : (List<EObject>) object.eGet(feature);
    }

    private static String string(EObject object, String name) {
        if (object == null) return null;
        EStructuralFeature feature = object.eClass().getEStructuralFeature(name);
        Object value = feature == null ? null : object.eGet(feature);
        return value == null ? null : value.toString();
    }

    @FunctionalInterface
    public interface SemanticRefinementWitness {
        boolean refines(EObject nvaRoot);
    }

    public record Issue(String code, String path, String message) {
    }

    public record Report(DynamicEmfModelValidator.LoadedModel model, List<Issue> issues) {
        public Report {
            issues = List.copyOf(issues);
        }

        public boolean valid() {
            return issues.isEmpty();
        }

        public void requireValid() {
            if (!valid()) throw new IllegalArgumentException("ValidNVA failed: " + issues);
        }
    }

    public record CertificationReport(Report structural, List<Issue> issues) {
        public CertificationReport {
            issues = List.copyOf(issues);
        }

        public boolean certified() {
            return issues.isEmpty();
        }
    }
}
