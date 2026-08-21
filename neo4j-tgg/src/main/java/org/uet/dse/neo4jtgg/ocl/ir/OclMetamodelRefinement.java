package org.uet.dse.neo4jtgg.ocl.ir;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Executable refinement witnesses from production Java records to OVA/CQM classifiers. */
public final class OclMetamodelRefinement {
    private OclMetamodelRefinement() {
    }

    public static RefinementReport refineOva(OclIr.InvariantQuery invariant) {
        List<String> errors = new ArrayList<>();
        List<NodeWitness> witnesses = new ArrayList<>();
        OclCertifiedModelValidator.ValidationReport validation =
                OclCertifiedModelValidator.certifiedOva(invariant);
        validation.violations().forEach(v -> errors.add(v.code() + "@" + v.path() + ": " + v.message()));
        if (invariant != null) {
            witnesses.add(new NodeWitness("$", "Invariant", "InvariantQuery"));
            walkOva(invariant.predicate(), "$.predicate", witnesses, errors);
        }
        return new RefinementReport(witnesses, errors);
    }

    public static RefinementReport refineCqm(OclCypherPlan.InvariantPlan plan) {
        List<String> errors = new ArrayList<>();
        List<NodeWitness> witnesses = new ArrayList<>();
        OclCertifiedModelValidator.ValidationReport validation =
                OclCertifiedModelValidator.certifiedCqm(plan);
        validation.violations().forEach(v -> errors.add(v.code() + "@" + v.path() + ": " + v.message()));
        if (plan != null) {
            witnesses.add(new NodeWitness("$", "InvariantPlan", "InvariantPlan"));
            walkCqm(plan.predicate(), "$.predicate", witnesses, errors);
        }
        return new RefinementReport(witnesses, errors);
    }

    public static Optional<String> ovaClassifierOf(OclIr.Expression expression) {
        if (expression instanceof OclIr.Variable) return Optional.of("VariableExpression");
        if (expression instanceof OclIr.Literal) return Optional.of("LiteralExpression");
        if (expression instanceof OclIr.SetLiteral) return Optional.of("SetExpression");
        if (expression instanceof OclIr.Not) return Optional.of("UnaryExpression");
        if (expression instanceof OclIr.Binary) return Optional.of("BinaryExpression");
        if (expression instanceof OclIr.If) return Optional.of("IfExpression");
        if (expression instanceof OclIr.Let) return Optional.of("LetExpression");
        if (expression instanceof OclIr.AttributeAccess) return Optional.of("AttributeAccessExpression");
        if (expression instanceof OclIr.NavigationAccess) return Optional.of("NavigationAccessExpression");
        if (expression instanceof OclIr.MethodCall || expression instanceof OclIr.CollectionOperation) {
            return Optional.of("CallExpression");
        }
        if (expression instanceof OclIr.IteratorOperation) return Optional.of("IteratorExpression");
        if (expression instanceof OclIr.NavigationPredicateCheck) {
            return Optional.of("NavigationPredicateExpression");
        }
        if (expression instanceof OclIr.NavigationCountComparison) {
            return Optional.of("NavigationCountExpression");
        }
        if (expression instanceof OclIr.NavigationUniquenessCheck) {
            return Optional.of("NavigationUniquenessExpression");
        }
        // NavigationAggregation deliberately has no classifier in certified OVA v1.
        return Optional.empty();
    }

    public static Optional<String> cqmClassifierOf(OclCypherPlan.ExpressionPlan expression) {
        if (expression instanceof OclCypherPlan.VariablePlan) return Optional.of("VariablePlan");
        if (expression instanceof OclCypherPlan.LiteralPlan) return Optional.of("LiteralPlan");
        if (expression instanceof OclCypherPlan.SetLiteralPlan) return Optional.of("SetPlan");
        if (expression instanceof OclCypherPlan.NotPlan) return Optional.of("UnaryPlan");
        if (expression instanceof OclCypherPlan.BinaryPlan) return Optional.of("BinaryPlan");
        if (expression instanceof OclCypherPlan.IfPlan) return Optional.of("IfPlan");
        if (expression instanceof OclCypherPlan.LetPlan) return Optional.of("LetPlan");
        if (expression instanceof OclCypherPlan.AttributeAccessPlan) return Optional.of("AttributeAccessPlan");
        if (expression instanceof OclCypherPlan.NavigationAccessPlan) return Optional.of("NavigationAccessPlan");
        if (expression instanceof OclCypherPlan.MethodCallPlan
                || expression instanceof OclCypherPlan.CollectionOperationPlan) {
            return Optional.of("CallPlan");
        }
        if (expression instanceof OclCypherPlan.IteratorOperationPlan) return Optional.of("IteratorPlan");
        if (expression instanceof OclCypherPlan.ExistsSubqueryPlan) return Optional.of("ExistsSubqueryPlan");
        if (expression instanceof OclCypherPlan.NotExistsSubqueryPlan) return Optional.of("NotExistsSubqueryPlan");
        if (expression instanceof OclCypherPlan.CountSubqueryComparisonPlan) {
            return Optional.of("CountComparisonPlan");
        }
        if (expression instanceof OclCypherPlan.NavigationUniquenessPlan) return Optional.of("UniquenessPlan");
        // NavigationAggregationPlan deliberately has no classifier in certified CQM v1.
        return Optional.empty();
    }

    private static void walkOva(OclIr.Expression expression, String path,
                                List<NodeWitness> witnesses, List<String> errors) {
        if (expression == null) return;
        Optional<String> classifier = ovaClassifierOf(expression);
        if (classifier.isEmpty()) {
            errors.add("REF_OVA@" + path + ": no certified OVA classifier for "
                    + expression.getClass().getSimpleName());
        } else {
            witnesses.add(new NodeWitness(path, classifier.get(), expression.getClass().getSimpleName()));
        }

        if (expression instanceof OclIr.SetLiteral set) {
            walkOvaList(set.elements(), path + ".elements", witnesses, errors);
        } else if (expression instanceof OclIr.Not not) {
            walkOva(not.expression(), path + ".operand", witnesses, errors);
        } else if (expression instanceof OclIr.Binary binary) {
            walkOva(binary.left(), path + ".left", witnesses, errors);
            walkOva(binary.right(), path + ".right", witnesses, errors);
        } else if (expression instanceof OclIr.If ifExpression) {
            walkOva(ifExpression.condition(), path + ".condition", witnesses, errors);
            walkOva(ifExpression.thenBranch(), path + ".thenBranch", witnesses, errors);
            walkOva(ifExpression.elseBranch(), path + ".elseBranch", witnesses, errors);
        } else if (expression instanceof OclIr.Let let) {
            walkOva(let.value(), path + ".value", witnesses, errors);
            walkOva(let.body(), path + ".body", witnesses, errors);
        } else if (expression instanceof OclIr.AttributeAccess attribute) {
            walkOva(attribute.source(), path + ".source", witnesses, errors);
        } else if (expression instanceof OclIr.NavigationAccess navigation) {
            walkOva(navigation.source(), path + ".source", witnesses, errors);
            walkOvaList(navigation.qualifiers(), path + ".qualifiers", witnesses, errors);
        } else if (expression instanceof OclIr.MethodCall call) {
            walkOva(call.source(), path + ".source", witnesses, errors);
            walkOvaList(call.arguments(), path + ".arguments", witnesses, errors);
        } else if (expression instanceof OclIr.CollectionOperation call) {
            walkOva(call.source(), path + ".source", witnesses, errors);
            walkOvaList(call.arguments(), path + ".arguments", witnesses, errors);
        } else if (expression instanceof OclIr.IteratorOperation iterator) {
            walkOva(iterator.source(), path + ".source", witnesses, errors);
            walkOva(iterator.body(), path + ".body", witnesses, errors);
        } else if (expression instanceof OclIr.NavigationPredicateCheck check) {
            walkOva(check.navigation(), path + ".navigation", witnesses, errors);
            walkOva(check.predicate(), path + ".predicate", witnesses, errors);
        } else if (expression instanceof OclIr.NavigationCountComparison count) {
            walkOva(count.navigation(), path + ".navigation", witnesses, errors);
            walkOva(count.predicate(), path + ".predicate", witnesses, errors);
        } else if (expression instanceof OclIr.NavigationAggregation aggregation) {
            walkOva(aggregation.navigation(), path + ".navigation", witnesses, errors);
            walkOva(aggregation.predicate(), path + ".predicate", witnesses, errors);
            walkOva(aggregation.projection(), path + ".projection", witnesses, errors);
        } else if (expression instanceof OclIr.NavigationUniquenessCheck uniqueness) {
            walkOva(uniqueness.navigation(), path + ".navigation", witnesses, errors);
            walkOva(uniqueness.predicate(), path + ".predicate", witnesses, errors);
            walkOva(uniqueness.projection(), path + ".projection", witnesses, errors);
        }
    }

    private static void walkCqm(OclCypherPlan.ExpressionPlan expression, String path,
                                List<NodeWitness> witnesses, List<String> errors) {
        if (expression == null) return;
        Optional<String> classifier = cqmClassifierOf(expression);
        if (classifier.isEmpty()) {
            errors.add("REF_CQM@" + path + ": no certified CQM classifier for "
                    + expression.getClass().getSimpleName());
        } else {
            witnesses.add(new NodeWitness(path, classifier.get(), expression.getClass().getSimpleName()));
        }

        if (expression instanceof OclCypherPlan.SetLiteralPlan set) {
            walkCqmList(set.elements(), path + ".elements", witnesses, errors);
        } else if (expression instanceof OclCypherPlan.NotPlan not) {
            walkCqm(not.expression(), path + ".operand", witnesses, errors);
        } else if (expression instanceof OclCypherPlan.BinaryPlan binary) {
            walkCqm(binary.left(), path + ".left", witnesses, errors);
            walkCqm(binary.right(), path + ".right", witnesses, errors);
        } else if (expression instanceof OclCypherPlan.IfPlan ifPlan) {
            walkCqm(ifPlan.condition(), path + ".condition", witnesses, errors);
            walkCqm(ifPlan.thenBranch(), path + ".thenBranch", witnesses, errors);
            walkCqm(ifPlan.elseBranch(), path + ".elseBranch", witnesses, errors);
        } else if (expression instanceof OclCypherPlan.LetPlan let) {
            walkCqm(let.value(), path + ".value", witnesses, errors);
            walkCqm(let.body(), path + ".body", witnesses, errors);
        } else if (expression instanceof OclCypherPlan.AttributeAccessPlan attribute) {
            walkCqm(attribute.source(), path + ".source", witnesses, errors);
        } else if (expression instanceof OclCypherPlan.NavigationAccessPlan navigation) {
            walkCqm(navigation.source(), path + ".source", witnesses, errors);
            walkCqmList(navigation.qualifiers(), path + ".qualifiers", witnesses, errors);
        } else if (expression instanceof OclCypherPlan.MethodCallPlan call) {
            walkCqm(call.source(), path + ".source", witnesses, errors);
            walkCqmList(call.arguments(), path + ".arguments", witnesses, errors);
        } else if (expression instanceof OclCypherPlan.CollectionOperationPlan call) {
            walkCqm(call.source(), path + ".source", witnesses, errors);
            walkCqmList(call.arguments(), path + ".arguments", witnesses, errors);
        } else if (expression instanceof OclCypherPlan.IteratorOperationPlan iterator) {
            walkCqm(iterator.source(), path + ".source", witnesses, errors);
            walkCqm(iterator.body(), path + ".body", witnesses, errors);
        } else if (expression instanceof OclCypherPlan.ExistsSubqueryPlan exists) {
            walkMatch(exists.match(), path + ".match", witnesses, errors);
        } else if (expression instanceof OclCypherPlan.NotExistsSubqueryPlan notExists) {
            walkMatch(notExists.match(), path + ".match", witnesses, errors);
        } else if (expression instanceof OclCypherPlan.CountSubqueryComparisonPlan count) {
            walkMatch(count.match(), path + ".match", witnesses, errors);
        } else if (expression instanceof OclCypherPlan.NavigationAggregationPlan aggregation) {
            walkMatch(aggregation.match(), path + ".match", witnesses, errors);
            walkCqm(aggregation.projection(), path + ".projection", witnesses, errors);
        } else if (expression instanceof OclCypherPlan.NavigationUniquenessPlan uniqueness) {
            walkMatch(uniqueness.match(), path + ".match", witnesses, errors);
            walkCqm(uniqueness.projection(), path + ".projection", witnesses, errors);
        }
    }

    private static void walkMatch(OclCypherPlan.NavigationMatchPlan match, String path,
                                  List<NodeWitness> witnesses, List<String> errors) {
        if (match == null) return;
        witnesses.add(new NodeWitness(path, "NavigationMatchPlan", "NavigationMatchPlan"));
        walkCqm(match.owner(), path + ".owner", witnesses, errors);
        walkCqm(match.navigation(), path + ".navigation", witnesses, errors);
        walkCqm(match.predicate(), path + ".predicate", witnesses, errors);
    }

    private static void walkOvaList(List<? extends OclIr.Expression> expressions, String path,
                                    List<NodeWitness> witnesses, List<String> errors) {
        if (expressions == null) return;
        for (int i = 0; i < expressions.size(); i++) {
            walkOva(expressions.get(i), path + "[" + i + "]", witnesses, errors);
        }
    }

    private static void walkCqmList(List<? extends OclCypherPlan.ExpressionPlan> expressions, String path,
                                    List<NodeWitness> witnesses, List<String> errors) {
        if (expressions == null) return;
        for (int i = 0; i < expressions.size(); i++) {
            walkCqm(expressions.get(i), path + "[" + i + "]", witnesses, errors);
        }
    }

    public record NodeWitness(String path, String metamodelClassifier, String javaConstructor) {
    }

    public record RefinementReport(List<NodeWitness> witnesses, List<String> errors) {
        public RefinementReport {
            witnesses = List.copyOf(witnesses);
            errors = List.copyOf(errors);
        }

        public boolean valid() {
            return errors.isEmpty();
        }

        public void requireValid(String relationName) {
            if (!valid()) {
                throw new IllegalArgumentException(relationName + " failed: " + errors);
            }
        }
    }
}
