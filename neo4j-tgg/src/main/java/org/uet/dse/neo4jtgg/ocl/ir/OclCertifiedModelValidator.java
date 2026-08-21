package org.uet.dse.neo4jtgg.ocl.ir;

import org.uet.dse.neo4jtgg.ocl.OclTypeBinding;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Executable WF_OVA, WF_CQM, and CertifiedOVA/CertifiedCQM predicates for the
 * production Java refinements of the research metamodels.
 *
 * <p>The EMF models carry resource identifiers that are erased by the Java
 * refinement. This validator checks all retained structural, scope, typing,
 * stage, and certified-language conditions. Resource-level identifier
 * uniqueness remains an EMF serialization constraint.</p>
 */
public final class OclCertifiedModelValidator {
    public static final String CERTIFICATION_PROFILE = "OCL_VAL_FINITE_SET_V1";
    public static final String SEMANTIC_PRODUCER_VERSION = "semantic-ir-v1";
    public static final String OPTIMIZED_PRODUCER_VERSION = OclIrOptimizer.VERSION;

    private static final Set<String> BINARY_OPERATORS = Set.of(
            "=", "<>", "and", "or", "xor", "implies",
            ">", "<", ">=", "<=", "+", "-", "*", "/");
    private static final Set<String> CERTIFIED_METHODS = Set.of(
            "allinstances", "ocliskindof", "oclastype");
    private static final Set<String> CERTIFIED_COLLECTION_OPERATIONS = Set.of(
            "size", "isempty", "notempty", "includes", "excludes",
            "includesall", "excludesall", "union", "intersection", "asset");
    private static final Set<String> CERTIFIED_ITERATORS = Set.of(
            "exists", "forall", "select", "reject", "collect", "isunique");
    private static final Set<OclTypeBinding.CollectionKind> CERTIFIED_COLLECTION_KINDS = Set.of(
            OclTypeBinding.CollectionKind.COLLECTION,
            OclTypeBinding.CollectionKind.SET);

    private OclCertifiedModelValidator() {
    }

    /** Executable WF_OVA over the Java OVA refinement. */
    public static ValidationReport wfOva(OclIr.InvariantQuery invariant) {
        List<Violation> violations = new ArrayList<>();
        if (invariant == null) {
            violations.add(new Violation("WF_OVA_ROOT", "$", "Invariant is required"));
            return new ValidationReport(violations);
        }
        requireNonBlank(invariant.contextClassName(), "WF_OVA_CONTEXT", "$.contextClassName", violations);
        requireNonBlank(invariant.invariantName(), "WF_OVA_NAME", "$.invariantName", violations);
        if (invariant.stage() == null) {
            violations.add(new Violation("WF_OVA_STAGE", "$.stage", "OVA stage is required"));
        }
        requireNonBlank(invariant.producerVersion(), "WF_OVA_VERSION", "$.producerVersion", violations);
        if (invariant.stage() == OclIr.Stage.SEMANTIC
                && !SEMANTIC_PRODUCER_VERSION.equals(invariant.producerVersion())) {
            violations.add(new Violation("WF_OVA_VERSION", "$.producerVersion",
                    "Semantic OVA must use " + SEMANTIC_PRODUCER_VERSION));
        }
        if (invariant.stage() == OclIr.Stage.OPTIMIZED
                && !OPTIMIZED_PRODUCER_VERSION.equals(invariant.producerVersion())) {
            violations.add(new Violation("WF_OVA_VERSION", "$.producerVersion",
                    "Optimized OVA must use " + OPTIMIZED_PRODUCER_VERSION));
        }
        validateOvaExpression(invariant.predicate(), "$.predicate", Set.of("self"), invariant.stage(),
                false, violations, java.util.Collections.newSetFromMap(new IdentityHashMap<>()));
        if (invariant.predicate() != null && !isBoolean(invariant.predicate().type())) {
            violations.add(new Violation("WF_OVA_BOOLEAN_ROOT", "$.predicate.type",
                    "Invariant predicate must have Boolean type"));
        }
        return new ValidationReport(violations);
    }

    /** Executable CertifiedOVA predicate; includes WF_OVA. */
    public static ValidationReport certifiedOva(OclIr.InvariantQuery invariant) {
        List<Violation> violations = new ArrayList<>(wfOva(invariant).violations());
        if (invariant != null) {
            validateOvaExpression(invariant.predicate(), "$.predicate", Set.of("self"), invariant.stage(),
                    true, violations, java.util.Collections.newSetFromMap(new IdentityHashMap<>()));
        }
        return new ValidationReport(violations);
    }

    /** Executable WF_CQM over the Java CQM refinement. */
    public static ValidationReport wfCqm(OclCypherPlan.InvariantPlan plan) {
        List<Violation> violations = new ArrayList<>();
        if (plan == null) {
            violations.add(new Violation("WF_CQM_ROOT", "$", "Invariant plan is required"));
            return new ValidationReport(violations);
        }
        requireNonBlank(plan.contextClassName(), "WF_CQM_CONTEXT", "$.contextClassName", violations);
        requireNonBlank(plan.invariantName(), "WF_CQM_NAME", "$.invariantName", violations);
        if (plan.violationPolicy() == null) {
            violations.add(new Violation("WF_CQM_POLICY", "$.violationPolicy",
                    "Violation policy is required"));
        }
        validateCqmExpression(plan.predicate(), "$.predicate", Set.of("self"), false, violations,
                java.util.Collections.newSetFromMap(new IdentityHashMap<>()));
        if (plan.predicate() != null && !isBoolean(plan.predicate().type())) {
            violations.add(new Violation("WF_CQM_BOOLEAN_ROOT", "$.predicate.type",
                    "Invariant plan predicate must have Boolean type"));
        }
        return new ValidationReport(violations);
    }

    /** Certified CQM predicate; includes WF_CQM and the fixed violation policy. */
    public static ValidationReport certifiedCqm(OclCypherPlan.InvariantPlan plan) {
        List<Violation> violations = new ArrayList<>(wfCqm(plan).violations());
        if (plan != null) {
            if (plan.violationPolicy() != OclCypherPlan.ViolationPolicy.NOT_VALIDATION_TRUE) {
                violations.add(new Violation("CERT_CQM_POLICY", "$.violationPolicy",
                        "Certified CQM requires NOT_VALIDATION_TRUE"));
            }
            validateCqmExpression(plan.predicate(), "$.predicate", Set.of("self"), true, violations,
                    java.util.Collections.newSetFromMap(new IdentityHashMap<>()));
        }
        return new ValidationReport(violations);
    }

    public static void requireWfOva(OclIr.InvariantQuery invariant) {
        wfOva(invariant).requireValid("WF_OVA");
    }

    public static void requireCertifiedOva(OclIr.InvariantQuery invariant) {
        certifiedOva(invariant).requireValid("CertifiedOVA");
    }

    public static void requireWfCqm(OclCypherPlan.InvariantPlan plan) {
        wfCqm(plan).requireValid("WF_CQM");
    }

    public static void requireCertifiedCqm(OclCypherPlan.InvariantPlan plan) {
        certifiedCqm(plan).requireValid("CertifiedCQM");
    }

    private static void validateOvaExpression(OclIr.Expression expression, String path, Set<String> scope,
                                              OclIr.Stage stage, boolean certified,
                                              List<Violation> violations, Set<OclIr.Expression> visited) {
        if (expression == null) {
            violations.add(new Violation("WF_OVA_NULL", path, "Expression is required"));
            return;
        }
        // Java refinement records may share immutable child objects; EMF node-id and
        // single-container constraints are checked at the serialized resource boundary.
        validateType(expression.type(), path + ".type", certified, "OVA", violations);
        if (stage == OclIr.Stage.SEMANTIC && expression instanceof OclIr.OptimizedExpression
                && !(expression instanceof OclIr.SemanticExpression)) {
            violations.add(new Violation("WF_OVA_STAGE", path,
                    "Optimized-only constructor occurs in SEMANTIC OVA"));
        }

        if (expression instanceof OclIr.Variable variable) {
            requireNonBlank(variable.name(), "WF_OVA_VARIABLE", path + ".name", violations);
            if (variable.type() != null && !variable.type().isClassReference()
                    && !scope.contains(variable.name())) {
                violations.add(new Violation("WF_OVA_SCOPE", path,
                        "Variable is not visible in the lexical environment: " + variable.name()));
            }
        } else if (expression instanceof OclIr.Literal) {
            // Literal payload validity is type-specific and checked by binder/scalar-closure gates.
        } else if (expression instanceof OclIr.SetLiteral set) {
            if (set.elements() == null) {
                violations.add(new Violation("WF_OVA_CHILDREN", path + ".elements", "Elements are required"));
            } else {
                for (int i = 0; i < set.elements().size(); i++) {
                    validateOvaExpression(set.elements().get(i), path + ".elements[" + i + "]", scope,
                            stage, certified, violations, visited);
                }
            }
        } else if (expression instanceof OclIr.Not not) {
            validateOvaExpression(not.expression(), path + ".operand", scope, stage, certified, violations, visited);
            requireBoolean(not.expression(), "WF_OVA_NOT", path + ".operand", violations);
        } else if (expression instanceof OclIr.Binary binary) {
            requireAllowed(binary.operator(), BINARY_OPERATORS, "WF_OVA_OPERATOR", path + ".operator", violations);
            validateOvaExpression(binary.left(), path + ".left", scope, stage, certified, violations, visited);
            validateOvaExpression(binary.right(), path + ".right", scope, stage, certified, violations, visited);
        } else if (expression instanceof OclIr.If ifExpression) {
            validateOvaExpression(ifExpression.condition(), path + ".condition", scope, stage, certified, violations, visited);
            requireBoolean(ifExpression.condition(), "WF_OVA_IF", path + ".condition", violations);
            validateOvaExpression(ifExpression.thenBranch(), path + ".thenBranch", scope, stage, certified, violations, visited);
            validateOvaExpression(ifExpression.elseBranch(), path + ".elseBranch", scope, stage, certified, violations, visited);
        } else if (expression instanceof OclIr.Let let) {
            requireNonBlank(let.variableName(), "WF_OVA_BINDER", path + ".variableName", violations);
            validateType(let.variableType(), path + ".variableType", certified, "OVA", violations);
            validateOvaExpression(let.value(), path + ".value", scope, stage, certified, violations, visited);
            validateOvaExpression(let.body(), path + ".body", extend(scope, let.variableName()), stage,
                    certified, violations, visited);
        } else if (expression instanceof OclIr.AttributeAccess attribute) {
            requireNonBlank(attribute.attributeName(), "WF_OVA_ATTRIBUTE", path + ".attributeName", violations);
            if (attribute.attribute() == null || attribute.attributeType() == null) {
                violations.add(new Violation("WF_OVA_ATTRIBUTE", path,
                        "Attribute access must retain resolved UML metadata"));
            }
            validateOvaExpression(attribute.source(), path + ".source", scope, stage, certified, violations, visited);
        } else if (expression instanceof OclIr.NavigationAccess navigation) {
            if (navigation.navigation() == null || !navigation.navigation().supportsDirectCypherNavigation()) {
                violations.add(new Violation("WF_OVA_NAVIGATION", path,
                        "Navigation must retain one admitted resolved binary direction"));
            }
            validateOvaExpression(navigation.source(), path + ".source", scope, stage, certified, violations, visited);
            if (navigation.qualifiers() == null) {
                violations.add(new Violation("WF_OVA_NAVIGATION", path + ".qualifiers",
                        "Qualifier list is required"));
            } else {
                for (int i = 0; i < navigation.qualifiers().size(); i++) {
                    validateOvaExpression(navigation.qualifiers().get(i), path + ".qualifiers[" + i + "]",
                            scope, stage, certified, violations, visited);
                }
            }
        } else if (expression instanceof OclIr.MethodCall call) {
            requireNonBlank(call.methodName(), "WF_OVA_METHOD", path + ".methodName", violations);
            if (certified) {
                requireAllowed(call.methodName(), CERTIFIED_METHODS, "CERT_OVA_METHOD", path + ".methodName", violations);
            }
            validateOvaExpression(call.source(), path + ".source", scope, stage, certified, violations, visited);
            validateOvaList(call.arguments(), path + ".arguments", scope, stage, certified, violations, visited);
        } else if (expression instanceof OclIr.CollectionOperation operation) {
            requireNonBlank(operation.operationName(), "WF_OVA_COLLECTION_OP", path + ".operationName", violations);
            validateType(operation.sourceCollectionType(), path + ".sourceCollectionType", certified, "OVA", violations);
            if (certified) {
                requireAllowed(operation.operationName(), CERTIFIED_COLLECTION_OPERATIONS,
                        "CERT_OVA_COLLECTION_OP", path + ".operationName", violations);
            }
            validateOvaExpression(operation.source(), path + ".source", scope, stage, certified, violations, visited);
            validateOvaList(operation.arguments(), path + ".arguments", scope, stage, certified, violations, visited);
        } else if (expression instanceof OclIr.IteratorOperation iterator) {
            requireNonBlank(iterator.operationName(), "WF_OVA_ITERATOR", path + ".operationName", violations);
            requireNonBlank(iterator.iteratorName(), "WF_OVA_BINDER", path + ".iteratorName", violations);
            validateType(iterator.sourceCollectionType(), path + ".sourceCollectionType", certified, "OVA", violations);
            validateType(iterator.iteratorVariableType(), path + ".iteratorVariableType", certified, "OVA", violations);
            if (certified) {
                requireAllowed(iterator.operationName(), CERTIFIED_ITERATORS,
                        "CERT_OVA_ITERATOR", path + ".operationName", violations);
            }
            validateOvaExpression(iterator.source(), path + ".source", scope, stage, certified, violations, visited);
            validateOvaExpression(iterator.body(), path + ".body", extend(scope, iterator.iteratorName()),
                    stage, certified, violations, visited);
        } else if (expression instanceof OclIr.NavigationPredicateCheck check) {
            validateOptimizedStage(stage, path, violations);
            validateOvaExpression(check.navigation(), path + ".navigation", scope, stage, certified, violations, visited);
            validateOptionalOvaPredicate(check.iteratorName(), check.predicate(), path, scope, stage,
                    certified, violations, visited);
        } else if (expression instanceof OclIr.NavigationCountComparison count) {
            validateOptimizedStage(stage, path, violations);
            requireAllowed(count.operator(), Set.of("=", "<>", ">", ">=", "<", "<="),
                    "WF_OVA_COUNT_OPERATOR", path + ".operator", violations);
            validateOvaExpression(count.navigation(), path + ".navigation", scope, stage, certified, violations, visited);
            validateOptionalOvaPredicate(count.iteratorName(), count.predicate(), path, scope, stage,
                    certified, violations, visited);
        } else if (expression instanceof OclIr.NavigationAggregation aggregation) {
            validateOptimizedStage(stage, path, violations);
            if (certified) {
                violations.add(new Violation("CERT_OVA_EXCLUDED", path,
                        "Navigation aggregation is outside " + CERTIFICATION_PROFILE));
            }
            validateOvaExpression(aggregation.navigation(), path + ".navigation", scope, stage, certified, violations, visited);
            validateOptionalOvaPredicate(aggregation.iteratorName(), aggregation.predicate(), path, scope, stage,
                    certified, violations, visited);
            validateOvaExpression(aggregation.projection(), path + ".projection",
                    extend(scope, aggregation.iteratorName()), stage, certified, violations, visited);
        } else if (expression instanceof OclIr.NavigationUniquenessCheck uniqueness) {
            validateOptimizedStage(stage, path, violations);
            validateOvaExpression(uniqueness.navigation(), path + ".navigation", scope, stage, certified, violations, visited);
            validateOptionalOvaPredicate(uniqueness.iteratorName(), uniqueness.predicate(), path, scope, stage,
                    certified, violations, visited);
            validateOvaExpression(uniqueness.projection(), path + ".projection",
                    extend(scope, uniqueness.iteratorName()), stage, certified, violations, visited);
        } else {
            violations.add(new Violation("WF_OVA_TOTALITY", path,
                    "Unknown OVA Java refinement constructor: " + expression.getClass().getName()));
        }
    }

    private static void validateCqmExpression(OclCypherPlan.ExpressionPlan expression, String path,
                                              Set<String> scope, boolean certified,
                                              List<Violation> violations,
                                              Set<OclCypherPlan.ExpressionPlan> visited) {
        if (expression == null) {
            violations.add(new Violation("WF_CQM_NULL", path, "Plan expression is required"));
            return;
        }
        // CQM plans deliberately share immutable owner/source nodes in navigation
        // matches. This is a valid Java refinement of two equal EMF subterms.
        validateType(expression.type(), path + ".type", certified, "CQM", violations);

        if (expression instanceof OclCypherPlan.VariablePlan variable) {
            requireNonBlank(variable.name(), "WF_CQM_VARIABLE", path + ".name", violations);
            if (variable.type() != null && !variable.type().isClassReference() && !scope.contains(variable.name())) {
                violations.add(new Violation("WF_CQM_SCOPE", path,
                        "Plan variable is not visible: " + variable.name()));
            }
        } else if (expression instanceof OclCypherPlan.LiteralPlan) {
            // Literal payload validity is covered by parameter/scalar closure contracts.
        } else if (expression instanceof OclCypherPlan.SetLiteralPlan set) {
            validateCqmList(set.elements(), path + ".elements", scope, certified, violations, visited);
        } else if (expression instanceof OclCypherPlan.NotPlan not) {
            validateCqmExpression(not.expression(), path + ".operand", scope, certified, violations, visited);
            requireBoolean(not.expression(), "WF_CQM_NOT", path + ".operand", violations);
        } else if (expression instanceof OclCypherPlan.BinaryPlan binary) {
            requireAllowed(binary.operator(), BINARY_OPERATORS, "WF_CQM_OPERATOR", path + ".operator", violations);
            validateCqmExpression(binary.left(), path + ".left", scope, certified, violations, visited);
            validateCqmExpression(binary.right(), path + ".right", scope, certified, violations, visited);
        } else if (expression instanceof OclCypherPlan.IfPlan ifPlan) {
            validateCqmExpression(ifPlan.condition(), path + ".condition", scope, certified, violations, visited);
            requireBoolean(ifPlan.condition(), "WF_CQM_IF", path + ".condition", violations);
            validateCqmExpression(ifPlan.thenBranch(), path + ".thenBranch", scope, certified, violations, visited);
            validateCqmExpression(ifPlan.elseBranch(), path + ".elseBranch", scope, certified, violations, visited);
        } else if (expression instanceof OclCypherPlan.LetPlan let) {
            requireNonBlank(let.variableName(), "WF_CQM_BINDER", path + ".variableName", violations);
            validateType(let.variableType(), path + ".variableType", certified, "CQM", violations);
            validateCqmExpression(let.value(), path + ".value", scope, certified, violations, visited);
            validateCqmExpression(let.body(), path + ".body", extend(scope, let.variableName()),
                    certified, violations, visited);
        } else if (expression instanceof OclCypherPlan.AttributeAccessPlan attribute) {
            requireNonBlank(attribute.attributeName(), "WF_CQM_ATTRIBUTE", path + ".attributeName", violations);
            if (attribute.attribute() == null || attribute.attributeType() == null) {
                violations.add(new Violation("WF_CQM_ATTRIBUTE", path,
                        "Attribute plan must retain resolved UML metadata"));
            }
            validateCqmExpression(attribute.source(), path + ".source", scope, certified, violations, visited);
        } else if (expression instanceof OclCypherPlan.NavigationAccessPlan navigation) {
            if (navigation.navigation() == null || !navigation.navigation().supportsDirectCypherNavigation()) {
                violations.add(new Violation("WF_CQM_NAVIGATION", path,
                        "Navigation plan must retain one admitted resolved binary direction"));
            }
            validateCqmExpression(navigation.source(), path + ".source", scope, certified, violations, visited);
            validateCqmList(navigation.qualifiers(), path + ".qualifiers", scope, certified, violations, visited);
        } else if (expression instanceof OclCypherPlan.MethodCallPlan call) {
            requireNonBlank(call.methodName(), "WF_CQM_METHOD", path + ".methodName", violations);
            if (certified) {
                requireAllowed(call.methodName(), CERTIFIED_METHODS, "CERT_CQM_METHOD", path + ".methodName", violations);
            }
            validateCqmExpression(call.source(), path + ".source", scope, certified, violations, visited);
            validateCqmList(call.arguments(), path + ".arguments", scope, certified, violations, visited);
        } else if (expression instanceof OclCypherPlan.CollectionOperationPlan operation) {
            requireNonBlank(operation.operationName(), "WF_CQM_COLLECTION_OP", path + ".operationName", violations);
            validateType(operation.sourceCollectionType(), path + ".sourceCollectionType", certified, "CQM", violations);
            if (certified) {
                requireAllowed(operation.operationName(), CERTIFIED_COLLECTION_OPERATIONS,
                        "CERT_CQM_COLLECTION_OP", path + ".operationName", violations);
            }
            validateCqmExpression(operation.source(), path + ".source", scope, certified, violations, visited);
            validateCqmList(operation.arguments(), path + ".arguments", scope, certified, violations, visited);
        } else if (expression instanceof OclCypherPlan.IteratorOperationPlan iterator) {
            requireNonBlank(iterator.operationName(), "WF_CQM_ITERATOR", path + ".operationName", violations);
            requireNonBlank(iterator.iteratorName(), "WF_CQM_BINDER", path + ".iteratorName", violations);
            validateType(iterator.sourceCollectionType(), path + ".sourceCollectionType", certified, "CQM", violations);
            validateType(iterator.iteratorVariableType(), path + ".iteratorVariableType", certified, "CQM", violations);
            if (certified) {
                requireAllowed(iterator.operationName(), CERTIFIED_ITERATORS,
                        "CERT_CQM_ITERATOR", path + ".operationName", violations);
            }
            validateCqmExpression(iterator.source(), path + ".source", scope, certified, violations, visited);
            validateCqmExpression(iterator.body(), path + ".body", extend(scope, iterator.iteratorName()),
                    certified, violations, visited);
        } else if (expression instanceof OclCypherPlan.ExistsSubqueryPlan exists) {
            validateNavigationMatch(exists.match(), path + ".match", scope, certified, violations, visited);
        } else if (expression instanceof OclCypherPlan.NotExistsSubqueryPlan notExists) {
            validateNavigationMatch(notExists.match(), path + ".match", scope, certified, violations, visited);
        } else if (expression instanceof OclCypherPlan.CountSubqueryComparisonPlan count) {
            requireAllowed(count.operator(), Set.of("=", "<>", ">", ">=", "<", "<="),
                    "WF_CQM_COUNT_OPERATOR", path + ".operator", violations);
            validateNavigationMatch(count.match(), path + ".match", scope, certified, violations, visited);
        } else if (expression instanceof OclCypherPlan.NavigationAggregationPlan aggregation) {
            if (certified) {
                violations.add(new Violation("CERT_CQM_EXCLUDED", path,
                        "Navigation aggregation is outside " + CERTIFICATION_PROFILE));
            }
            validateNavigationMatch(aggregation.match(), path + ".match", scope, certified, violations, visited);
            validateCqmExpression(aggregation.projection(), path + ".projection",
                    matchScope(scope, aggregation.match()), certified, violations, visited);
        } else if (expression instanceof OclCypherPlan.NavigationUniquenessPlan uniqueness) {
            validateNavigationMatch(uniqueness.match(), path + ".match", scope, certified, violations, visited);
            validateCqmExpression(uniqueness.projection(), path + ".projection",
                    matchScope(scope, uniqueness.match()), certified, violations, visited);
        } else {
            violations.add(new Violation("WF_CQM_TOTALITY", path,
                    "Unknown CQM Java refinement constructor: " + expression.getClass().getName()));
        }
    }

    private static void validateNavigationMatch(OclCypherPlan.NavigationMatchPlan match, String path,
                                                Set<String> scope, boolean certified,
                                                List<Violation> violations,
                                                Set<OclCypherPlan.ExpressionPlan> visited) {
        if (match == null) {
            violations.add(new Violation("WF_CQM_MATCH", path, "Navigation match is required"));
            return;
        }
        requireNonBlank(match.targetAlias(), "WF_CQM_ALIAS", path + ".targetAlias", violations);
        validateType(match.targetType(), path + ".targetType", certified, "CQM", violations);
        validateCqmExpression(match.owner(), path + ".owner", scope, certified, violations, visited);
        validateCqmExpression(match.navigation(), path + ".navigation", scope, certified, violations, visited);
        if (match.predicateMode() == null) {
            violations.add(new Violation("WF_CQM_PREDICATE_MODE", path, "Predicate mode is required"));
        } else if (match.predicateMode() == OclCypherPlan.PredicateMode.NONE && match.predicate() != null) {
            violations.add(new Violation("WF_CQM_PREDICATE_MODE", path,
                    "PredicateMode.NONE must not carry a predicate"));
        } else if (match.predicateMode() != OclCypherPlan.PredicateMode.NONE && match.predicate() == null) {
            violations.add(new Violation("WF_CQM_PREDICATE_MODE", path,
                    "NORMAL/NEGATED predicate mode requires a predicate"));
        }
        if (match.predicate() != null) {
            validateCqmExpression(match.predicate(), path + ".predicate", matchScope(scope, match),
                    certified, violations, visited);
            requireBoolean(match.predicate(), "WF_CQM_MATCH_PREDICATE", path + ".predicate", violations);
        }
    }

    private static void validateOptionalOvaPredicate(String iteratorName, OclIr.Expression predicate, String path,
                                                     Set<String> scope, OclIr.Stage stage, boolean certified,
                                                     List<Violation> violations, Set<OclIr.Expression> visited) {
        requireNonBlank(iteratorName, "WF_OVA_BINDER", path + ".iteratorName", violations);
        if (predicate != null) {
            validateOvaExpression(predicate, path + ".predicate", extend(scope, iteratorName), stage,
                    certified, violations, visited);
            requireBoolean(predicate, "WF_OVA_PREDICATE", path + ".predicate", violations);
        }
    }

    private static void validateOvaList(List<? extends OclIr.Expression> expressions, String path,
                                        Set<String> scope, OclIr.Stage stage, boolean certified,
                                        List<Violation> violations, Set<OclIr.Expression> visited) {
        if (expressions == null) {
            violations.add(new Violation("WF_OVA_CHILDREN", path, "Expression list is required"));
            return;
        }
        for (int i = 0; i < expressions.size(); i++) {
            validateOvaExpression(expressions.get(i), path + "[" + i + "]", scope, stage,
                    certified, violations, visited);
        }
    }

    private static void validateCqmList(List<? extends OclCypherPlan.ExpressionPlan> expressions, String path,
                                        Set<String> scope, boolean certified, List<Violation> violations,
                                        Set<OclCypherPlan.ExpressionPlan> visited) {
        if (expressions == null) {
            violations.add(new Violation("WF_CQM_CHILDREN", path, "Plan expression list is required"));
            return;
        }
        for (int i = 0; i < expressions.size(); i++) {
            validateCqmExpression(expressions.get(i), path + "[" + i + "]", scope,
                    certified, violations, visited);
        }
    }

    private static void validateType(OclTypeBinding type, String path, boolean certified,
                                     String layer, List<Violation> violations) {
        if (type == null || type.kind() == null || type.collectionKind() == null) {
            violations.add(new Violation("WF_" + layer + "_TYPE", path, "Complete type binding is required"));
            return;
        }
        requireNonBlank(type.typeName(), "WF_" + layer + "_TYPE", path + ".typeName", violations);
        if (type.isCollection()) {
            if (type.collectionKind() == OclTypeBinding.CollectionKind.NONE || type.elementBinding() == null) {
                violations.add(new Violation("WF_" + layer + "_TYPE", path,
                        "Collection type requires kind and element type"));
            }
            if (certified && !CERTIFIED_COLLECTION_KINDS.contains(type.collectionKind())) {
                violations.add(new Violation("CERT_" + layer + "_COLLECTION", path,
                        type.collectionKind() + " is outside finite-set certified v1"));
            }
            if (certified && type.elementBinding() != null && type.elementBinding().isCollection()) {
                violations.add(new Violation("CERT_" + layer + "_NESTED_COLLECTION", path,
                        "Nested collection values are outside certified v1"));
            }
        } else if (type.collectionKind() != OclTypeBinding.CollectionKind.NONE || type.elementBinding() != null) {
            violations.add(new Violation("WF_" + layer + "_TYPE", path,
                    "Non-collection type must use CollectionKind.NONE and no element type"));
        }
    }

    private static void validateOptimizedStage(OclIr.Stage stage, String path, List<Violation> violations) {
        if (stage != OclIr.Stage.OPTIMIZED) {
            violations.add(new Violation("WF_OVA_STAGE", path,
                    "Graph-specialized OVA constructor requires OPTIMIZED stage"));
        }
    }

    private static void requireBoolean(OclIr.Expression expression, String code, String path,
                                       List<Violation> violations) {
        if (expression != null && !isBoolean(expression.type())) {
            violations.add(new Violation(code, path, "Boolean expression is required"));
        }
    }

    private static void requireBoolean(OclCypherPlan.ExpressionPlan expression, String code, String path,
                                       List<Violation> violations) {
        if (expression != null && !isBoolean(expression.type())) {
            violations.add(new Violation(code, path, "Boolean plan expression is required"));
        }
    }

    private static boolean isBoolean(OclTypeBinding type) {
        return type != null && !type.isCollection() && !type.isNode() && !type.isClassReference()
                && "Boolean".equalsIgnoreCase(type.typeName());
    }

    private static Set<String> extend(Set<String> scope, String name) {
        Set<String> extended = new HashSet<>(scope);
        if (name != null && !name.isBlank()) {
            extended.add(name);
        }
        return Set.copyOf(extended);
    }

    private static Set<String> matchScope(Set<String> scope, OclCypherPlan.NavigationMatchPlan match) {
        return match == null ? scope : extend(scope, match.targetAlias());
    }

    private static void requireAllowed(String value, Set<String> allowed, String code, String path,
                                       List<Violation> violations) {
        if (value == null || !allowed.contains(value.toLowerCase(Locale.ROOT))) {
            violations.add(new Violation(code, path,
                    "Unsupported value '" + value + "'; expected one of " + allowed));
        }
    }

    private static void requireNonBlank(String value, String code, String path,
                                        List<Violation> violations) {
        if (value == null || value.isBlank()) {
            violations.add(new Violation(code, path, "Nonblank value is required"));
        }
    }

    public record Violation(String code, String path, String message) {
    }

    public record ValidationReport(List<Violation> violations) {
        public ValidationReport {
            violations = List.copyOf(violations);
        }

        public boolean valid() {
            return violations.isEmpty();
        }

        public void requireValid(String predicateName) {
            if (!valid()) {
                throw new IllegalArgumentException(predicateName + " failed: " + violations);
            }
        }
    }
}
