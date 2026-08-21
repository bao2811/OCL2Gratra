package org.uet.dse.neo4jtgg.ocl.ir;

import org.uet.dse.neo4j.encoding.CanonicalGraphEncoding;
import org.uet.dse.neo4j.encoding.CanonicalGraphVocabulary;
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
        if (plan.evaluationPolicy() == null) {
            violations.add(new Violation("WF_CQM_EVALUATION", "$.evaluationPolicy",
                    "Evaluation policy is required"));
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
            if (plan.evaluationPolicy()
                    != OclCypherPlan.EvaluationPolicy.MATERIALIZE_REUSED_EXPRESSIONS) {
                violations.add(new Violation("CERT_CQM_EVALUATION", "$.evaluationPolicy",
                        "Certified CQM requires materialization of reused expressions"));
            }
            validateCertifiedGraphContext(plan.graphBinding(), violations);
            validateCqmExpression(plan.predicate(), "$.predicate", Set.of("self"), true, violations,
                    java.util.Collections.newSetFromMap(new IdentityHashMap<>()));
            if (plan.graphBinding() != null) {
                validateCqmBindingScope(plan.predicate(), plan.graphBinding().modelKey(), "$.predicate",
                        violations, java.util.Collections.newSetFromMap(new IdentityHashMap<>()));
            }
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
            validateOvaSetLiteralTypes(set.type(), set.elements(), "WF_OVA_SET_TYPE", path, violations);
        } else if (expression instanceof OclIr.Not not) {
            validateOvaExpression(not.expression(), path + ".operand", scope, stage, certified, violations, visited);
            requireBoolean(not.expression(), "WF_OVA_NOT", path + ".operand", violations);
            if (!isBoolean(not.type())) {
                violations.add(new Violation("WF_OVA_NOT", path + ".type", "not result must be Boolean"));
            }
        } else if (expression instanceof OclIr.Binary binary) {
            requireAllowed(binary.operator(), BINARY_OPERATORS, "WF_OVA_OPERATOR", path + ".operator", violations);
            validateOvaExpression(binary.left(), path + ".left", scope, stage, certified, violations, visited);
            validateOvaExpression(binary.right(), path + ".right", scope, stage, certified, violations, visited);
            validateBinaryTypes(binary.operator(), binary.left(), binary.right(), binary.type(),
                    "WF_OVA_BINARY_TYPE", path, violations);
        } else if (expression instanceof OclIr.If ifExpression) {
            validateOvaExpression(ifExpression.condition(), path + ".condition", scope, stage, certified, violations, visited);
            requireBoolean(ifExpression.condition(), "WF_OVA_IF", path + ".condition", violations);
            validateOvaExpression(ifExpression.thenBranch(), path + ".thenBranch", scope, stage, certified, violations, visited);
            validateOvaExpression(ifExpression.elseBranch(), path + ".elseBranch", scope, stage, certified, violations, visited);
            requireCompatibleType(ifExpression.thenBranch().type(), ifExpression.elseBranch().type(),
                    "WF_OVA_IF_TYPE", path, violations);
            requireCompatibleType(ifExpression.type(), ifExpression.thenBranch().type(),
                    "WF_OVA_IF_TYPE", path + ".type", violations);
            requireCompatibleType(ifExpression.type(), ifExpression.elseBranch().type(),
                    "WF_OVA_IF_TYPE", path + ".type", violations);
        } else if (expression instanceof OclIr.Let let) {
            requireNonBlank(let.variableName(), "WF_OVA_BINDER", path + ".variableName", violations);
            validateType(let.variableType(), path + ".variableType", certified, "OVA", violations);
            validateOvaExpression(let.value(), path + ".value", scope, stage, certified, violations, visited);
            validateOvaExpression(let.body(), path + ".body", extend(scope, let.variableName()), stage,
                    certified, violations, visited);
            requireCompatibleType(let.variableType(), let.value().type(),
                    "WF_OVA_LET_TYPE", path + ".value", violations);
            requireSameType(let.type(), let.body().type(), "WF_OVA_LET_TYPE", path + ".body", violations);
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
            validateIteratorTypes(iterator.operationName(), iterator.body().type(), iterator.type(),
                    "WF_OVA_ITERATOR_TYPE", path, violations);
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
            validateCqmSetLiteralTypes(set.type(), set.elements(), "WF_CQM_SET_TYPE", path, violations);
        } else if (expression instanceof OclCypherPlan.NotPlan not) {
            validateCqmExpression(not.expression(), path + ".operand", scope, certified, violations, visited);
            requireBoolean(not.expression(), "WF_CQM_NOT", path + ".operand", violations);
            if (!isBoolean(not.type())) {
                violations.add(new Violation("WF_CQM_NOT", path + ".type", "not result must be Boolean"));
            }
        } else if (expression instanceof OclCypherPlan.BinaryPlan binary) {
            requireAllowed(binary.operator(), BINARY_OPERATORS, "WF_CQM_OPERATOR", path + ".operator", violations);
            validateCqmExpression(binary.left(), path + ".left", scope, certified, violations, visited);
            validateCqmExpression(binary.right(), path + ".right", scope, certified, violations, visited);
            validateBinaryTypes(binary.operator(), binary.left(), binary.right(), binary.type(),
                    "WF_CQM_BINARY_TYPE", path, violations);
        } else if (expression instanceof OclCypherPlan.IfPlan ifPlan) {
            validateCqmExpression(ifPlan.condition(), path + ".condition", scope, certified, violations, visited);
            requireBoolean(ifPlan.condition(), "WF_CQM_IF", path + ".condition", violations);
            validateCqmExpression(ifPlan.thenBranch(), path + ".thenBranch", scope, certified, violations, visited);
            validateCqmExpression(ifPlan.elseBranch(), path + ".elseBranch", scope, certified, violations, visited);
            requireCompatibleType(ifPlan.thenBranch().type(), ifPlan.elseBranch().type(),
                    "WF_CQM_IF_TYPE", path, violations);
            requireCompatibleType(ifPlan.type(), ifPlan.thenBranch().type(),
                    "WF_CQM_IF_TYPE", path + ".type", violations);
            requireCompatibleType(ifPlan.type(), ifPlan.elseBranch().type(),
                    "WF_CQM_IF_TYPE", path + ".type", violations);
        } else if (expression instanceof OclCypherPlan.LetPlan let) {
            requireNonBlank(let.variableName(), "WF_CQM_BINDER", path + ".variableName", violations);
            validateType(let.variableType(), path + ".variableType", certified, "CQM", violations);
            validateCqmExpression(let.value(), path + ".value", scope, certified, violations, visited);
            validateCqmExpression(let.body(), path + ".body", extend(scope, let.variableName()),
                    certified, violations, visited);
            requireCompatibleType(let.variableType(), let.value().type(),
                    "WF_CQM_LET_TYPE", path + ".value", violations);
            requireSameType(let.type(), let.body().type(), "WF_CQM_LET_TYPE", path + ".body", violations);
        } else if (expression instanceof OclCypherPlan.AttributeAccessPlan attribute) {
            requireNonBlank(attribute.attributeName(), "WF_CQM_ATTRIBUTE", path + ".attributeName", violations);
            if (attribute.attribute() == null || attribute.attributeType() == null) {
                violations.add(new Violation("WF_CQM_ATTRIBUTE", path,
                        "Attribute plan must retain resolved UML metadata"));
            }
            validateCqmExpression(attribute.source(), path + ".source", scope, certified, violations, visited);
            if (certified) validateAttributeBinding(attribute, path + ".binding", violations);
        } else if (expression instanceof OclCypherPlan.NavigationAccessPlan navigation) {
            if (navigation.navigation() == null || !navigation.navigation().supportsDirectCypherNavigation()) {
                violations.add(new Violation("WF_CQM_NAVIGATION", path,
                        "Navigation plan must retain one admitted resolved binary direction"));
            }
            validateCqmExpression(navigation.source(), path + ".source", scope, certified, violations, visited);
            validateCqmList(navigation.qualifiers(), path + ".qualifiers", scope, certified, violations, visited);
            if (certified) validateNavigationBinding(navigation, path + ".binding", violations);
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
            validateIteratorTypes(iterator.operationName(), iterator.body().type(), iterator.type(),
                    "WF_CQM_ITERATOR_TYPE", path, violations);
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

    private static void validateCertifiedGraphContext(OclCypherPlan.GraphContextBinding binding,
                                                      List<Violation> violations) {
        if (binding == null) {
            violations.add(new Violation("CERT_CQM_GRAPH_BINDING", "$.graphBinding",
                    "Certified CQM requires an explicit graph-context binding"));
            return;
        }
        if (!CanonicalGraphEncoding.PROFILE_ID.equals(binding.profileId())) {
            violations.add(new Violation("CERT_CQM_GRAPH_PROFILE", "$.graphBinding.profileId",
                    "Expected " + CanonicalGraphEncoding.PROFILE_ID));
        }
        requireNonBlank(binding.modelKey(), "CERT_CQM_GRAPH_BINDING", "$.graphBinding.modelKey", violations);
        requireNonBlank(binding.contextClassKey(), "CERT_CQM_GRAPH_BINDING",
                "$.graphBinding.contextClassKey", violations);
        requireKeyModelScope(binding.contextClassKey(), binding.modelKey(), "CERT_CQM_MODEL_SCOPE",
                "$.graphBinding.contextClassKey", violations);
        requireExact(binding.objectLabel(), "Object", "CERT_CQM_GRAPH_BINDING",
                "$.graphBinding.objectLabel", violations);
        requireExact(binding.classLabel(), "UmlClass", "CERT_CQM_GRAPH_BINDING",
                "$.graphBinding.classLabel", violations);
        requireExact(binding.conformanceRelationship(), CanonicalGraphVocabulary.OBJECT_INSTANCE_OF,
                "CERT_CQM_GRAPH_BINDING", "$.graphBinding.conformanceRelationship", violations);
        requireExact(binding.identityProperty(), "use_id", "CERT_CQM_GRAPH_BINDING",
                "$.graphBinding.identityProperty", violations);
    }

    private static void validateCqmBindingScope(OclCypherPlan.ExpressionPlan expression, String modelKey,
                                                String path, List<Violation> violations,
                                                Set<OclCypherPlan.ExpressionPlan> visited) {
        if (expression == null || !visited.add(expression) || modelKey == null) return;
        if (expression instanceof OclCypherPlan.AttributeAccessPlan access && access.binding() != null) {
            requireKeyModelScope(access.binding().attributeKey(), modelKey,
                    "CERT_CQM_MODEL_SCOPE", path + ".binding.attributeKey", violations);
        }
        if (expression instanceof OclCypherPlan.NavigationAccessPlan access && access.binding() != null) {
            requireKeyModelScope(access.binding().associationKey(), modelKey,
                    "CERT_CQM_MODEL_SCOPE", path + ".binding.associationKey", violations);
        }
        for (java.lang.reflect.RecordComponent component : expression.getClass().getRecordComponents()) {
            try {
                Object value = component.getAccessor().invoke(expression);
                if (value instanceof OclCypherPlan.ExpressionPlan child) {
                    validateCqmBindingScope(child, modelKey, path + "." + component.getName(), violations, visited);
                } else if (value instanceof OclCypherPlan.NavigationMatchPlan match) {
                    validateCqmBindingScope(match.owner(), modelKey, path + ".match.owner", violations, visited);
                    validateCqmBindingScope(match.navigation(), modelKey, path + ".match.navigation", violations, visited);
                    validateCqmBindingScope(match.predicate(), modelKey, path + ".match.predicate", violations, visited);
                } else if (value instanceof List<?> values) {
                    for (int i = 0; i < values.size(); i++) {
                        if (values.get(i) instanceof OclCypherPlan.ExpressionPlan child) {
                            validateCqmBindingScope(child, modelKey,
                                    path + "." + component.getName() + "[" + i + "]", violations, visited);
                        }
                    }
                }
            } catch (ReflectiveOperationException ex) {
                violations.add(new Violation("WF_CQM_TOTALITY", path,
                        "Cannot inspect CQM record component " + component.getName()));
            }
        }
    }

    private static void requireKeyModelScope(String key, String modelKey, String code, String path,
                                             List<Violation> violations) {
        if (key == null || !key.startsWith(modelKey + "::")) {
            violations.add(new Violation(code, path,
                    "Physical key must be scoped by graph modelKey '" + modelKey + "'"));
        }
    }

    private static void validateAttributeBinding(OclCypherPlan.AttributeAccessPlan access, String path,
                                                 List<Violation> violations) {
        OclCypherPlan.AttributeBinding binding = access.binding();
        if (binding == null) {
            violations.add(new Violation("CERT_CQM_ATTRIBUTE_BINDING", path,
                    "Certified attribute access requires an explicit physical binding"));
            return;
        }
        requireNonBlank(binding.attributeKey(), "CERT_CQM_ATTRIBUTE_BINDING", path + ".attributeKey", violations);
        if (access.attribute() != null && binding.attributeKey() != null) {
            String suffix = "::attribute::" + access.attribute().owner().name() + "::" + access.attributeName();
            if (!binding.attributeKey().endsWith(suffix)) {
                violations.add(new Violation("CERT_CQM_ATTRIBUTE_BINDING", path + ".attributeKey",
                        "Attribute key does not realize resolved UML attribute " + suffix));
            }
        }
        requireExact(binding.slotLabel(), "AttributeValue", "CERT_CQM_ATTRIBUTE_BINDING",
                path + ".slotLabel", violations);
        requireExact(binding.ownerRelationship(), "ObjectHasAttribute", "CERT_CQM_ATTRIBUTE_BINDING",
                path + ".ownerRelationship", violations);
        requireExact(binding.valueProperty(), "value", "CERT_CQM_ATTRIBUTE_BINDING",
                path + ".valueProperty", violations);
    }

    private static void validateNavigationBinding(OclCypherPlan.NavigationAccessPlan access, String path,
                                                  List<Violation> violations) {
        OclCypherPlan.NavigationBinding binding = access.binding();
        if (binding == null) {
            violations.add(new Violation("CERT_CQM_NAVIGATION_BINDING", path,
                    "Certified navigation requires an explicit physical binding"));
            return;
        }
        requireNonBlank(binding.associationKey(), "CERT_CQM_NAVIGATION_BINDING",
                path + ".associationKey", violations);
        if (access.navigation() != null) {
            String suffix = "::association::" + access.navigation().associationName();
            if (binding.associationKey() == null || !binding.associationKey().endsWith(suffix)) {
                violations.add(new Violation("CERT_CQM_NAVIGATION_BINDING", path + ".associationKey",
                        "Association key does not realize resolved UML association " + suffix));
            }
            requireExact(binding.sourceRole(), access.navigation().sourceRoleName(),
                    "CERT_CQM_NAVIGATION_BINDING", path + ".sourceRole", violations);
            requireExact(binding.targetRole(), access.navigation().targetRoleName(),
                    "CERT_CQM_NAVIGATION_BINDING", path + ".targetRole", violations);
            if (binding.direction() != access.navigation().direction()) {
                violations.add(new Violation("CERT_CQM_NAVIGATION_BINDING", path + ".direction",
                        "Physical direction does not agree with resolved UML navigation"));
            }
        }
        requireNonBlank(binding.sourceRole(), "CERT_CQM_NAVIGATION_BINDING", path + ".sourceRole", violations);
        requireNonBlank(binding.targetRole(), "CERT_CQM_NAVIGATION_BINDING", path + ".targetRole", violations);
        if (binding.direction() == null) {
            violations.add(new Violation("CERT_CQM_NAVIGATION_BINDING", path + ".direction",
                    "Navigation direction is required"));
        }
        requireExact(binding.relationshipTypePrefix(), "Link", "CERT_CQM_NAVIGATION_BINDING",
                path + ".relationshipTypePrefix", violations);
        requireExact(binding.sourceQualifierProperty(), "sourceQualifiers", "CERT_CQM_NAVIGATION_BINDING",
                path + ".sourceQualifierProperty", violations);
        requireExact(binding.targetQualifierProperty(), "targetQualifiers", "CERT_CQM_NAVIGATION_BINDING",
                path + ".targetQualifierProperty", violations);
    }

    private static void validateOvaSetLiteralTypes(OclTypeBinding setType,
                                                List<? extends OclIr.Expression> elements,
                                                String code, String path, List<Violation> violations) {
        if (setType == null || !setType.isCollection()) {
            violations.add(new Violation(code, path + ".type", "Set literal must have a collection type"));
            return;
        }
        if (elements == null) return;
        for (int i = 0; i < elements.size(); i++) {
            requireCompatibleType(setType.elementBinding(), elements.get(i).type(), code,
                    path + ".elements[" + i + "]", violations);
        }
    }

    private static void validateCqmSetLiteralTypes(OclTypeBinding setType,
                                                List<? extends OclCypherPlan.ExpressionPlan> elements,
                                                String code, String path, List<Violation> violations) {
        if (setType == null || !setType.isCollection()) {
            violations.add(new Violation(code, path + ".type", "Set plan must have a collection type"));
            return;
        }
        if (elements == null) return;
        for (int i = 0; i < elements.size(); i++) {
            requireCompatibleType(setType.elementBinding(), elements.get(i).type(), code,
                    path + ".elements[" + i + "]", violations);
        }
    }

    private static void validateBinaryTypes(String operator, OclIr.Expression left, OclIr.Expression right,
                                            OclTypeBinding result, String code, String path,
                                            List<Violation> violations) {
        validateBinaryTypes(operator, left == null ? null : left.type(), right == null ? null : right.type(),
                result, code, path, violations);
    }

    private static void validateBinaryTypes(String operator, OclCypherPlan.ExpressionPlan left,
                                            OclCypherPlan.ExpressionPlan right, OclTypeBinding result,
                                            String code, String path, List<Violation> violations) {
        validateBinaryTypes(operator, left == null ? null : left.type(), right == null ? null : right.type(),
                result, code, path, violations);
    }

    private static void validateBinaryTypes(String operator, OclTypeBinding left, OclTypeBinding right,
                                            OclTypeBinding result, String code, String path,
                                            List<Violation> violations) {
        if (operator == null || left == null || right == null || result == null) return;
        String normalized = operator.toLowerCase(Locale.ROOT);
        if (Set.of("and", "or", "xor", "implies").contains(normalized)) {
            if (!isBoolean(left) || !isBoolean(right) || !isBoolean(result)) {
                violations.add(new Violation(code, path,
                        "Boolean operator requires Boolean operands and result"));
            }
        } else if (Set.of("+", "-", "*", "/").contains(normalized)) {
            if (!isNumeric(left) || !isNumeric(right) || !isNumeric(result)) {
                violations.add(new Violation(code, path,
                        "Arithmetic operator requires numeric operands and result"));
            }
        } else if (Set.of(">", "<", ">=", "<=").contains(normalized)) {
            if ((!isNumeric(left) || !isNumeric(right)) && !left.equals(right)) {
                violations.add(new Violation(code, path,
                        "Ordering operands must be compatible numeric or identical scalar types"));
            }
            if (!isBoolean(result)) {
                violations.add(new Violation(code, path + ".type", "Comparison result must be Boolean"));
            }
        } else if (Set.of("=", "<>").contains(normalized)) {
            requireCompatibleType(left, right, code, path, violations);
            if (!isBoolean(result)) {
                violations.add(new Violation(code, path + ".type", "Equality result must be Boolean"));
            }
        }
    }

    private static void validateIteratorTypes(String operationName, OclTypeBinding body,
                                              OclTypeBinding result, String code, String path,
                                              List<Violation> violations) {
        if (operationName == null || body == null || result == null) return;
        switch (operationName.toLowerCase(Locale.ROOT)) {
            case "exists", "forall" -> {
                if (!isBoolean(body) || !isBoolean(result)) {
                    violations.add(new Violation(code, path,
                            operationName + " requires a Boolean body and Boolean result"));
                }
            }
            case "isunique" -> {
                if (!isBoolean(result)) {
                    violations.add(new Violation(code, path + ".type",
                            "isUnique result must be Boolean"));
                }
            }
            case "select", "reject" -> {
                if (!isBoolean(body) || !result.isCollection()) {
                    violations.add(new Violation(code, path,
                            operationName + " requires a Boolean body and collection result"));
                }
            }
            case "collect" -> {
                if (!result.isCollection()) {
                    violations.add(new Violation(code, path + ".type", "collect result must be a collection"));
                } else {
                    requireCompatibleType(result.elementBinding(), body, code, path + ".body", violations);
                }
            }
            default -> {
                // Admission/totality validators report unsupported iterator names.
            }
        }
    }

    private static void requireCompatibleType(OclTypeBinding expected, OclTypeBinding actual,
                                              String code, String path, List<Violation> violations) {
        if (typesCompatible(expected, actual)) return;
        violations.add(new Violation(code, path,
                "Incompatible types: expected " + expected + " but found " + actual));
    }

    private static boolean typesCompatible(OclTypeBinding expected, OclTypeBinding actual) {
        if (expected == null || actual == null || expected.equals(actual)
                || isVoid(expected) || isVoid(actual)
                || (isNumeric(expected) && isNumeric(actual))) return true;
        // This metamodel-level validator has no UML generalization graph. USE has
        // already type-checked node conformance; here we can validate only the
        // node/scalar shape without incorrectly requiring identical class names.
        if (expected.isNode() && actual.isNode()) return true;
        if (!expected.isCollection() || !actual.isCollection()) return false;
        boolean compatibleKind = expected.collectionKind() == actual.collectionKind()
                || expected.collectionKind() == OclTypeBinding.CollectionKind.COLLECTION;
        return compatibleKind && typesCompatible(expected.elementBinding(), actual.elementBinding());
    }

    private static void requireSameType(OclTypeBinding expected, OclTypeBinding actual,
                                        String code, String path, List<Violation> violations) {
        if (expected != null && actual != null && !expected.equals(actual)) {
            violations.add(new Violation(code, path,
                    "Types must be identical: " + expected + " versus " + actual));
        }
    }

    private static boolean isNumeric(OclTypeBinding type) {
        return type != null && !type.isCollection() && !type.isNode() && !type.isClassReference()
                && ("Integer".equalsIgnoreCase(type.typeName()) || "Real".equalsIgnoreCase(type.typeName()));
    }

    private static boolean isVoid(OclTypeBinding type) {
        return type != null && "Void".equalsIgnoreCase(type.typeName());
    }

    private static void requireExact(String actual, String expected, String code, String path,
                                     List<Violation> violations) {
        if (!expected.equals(actual)) {
            violations.add(new Violation(code, path,
                    "Expected '" + expected + "' but found '" + actual + "'"));
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
