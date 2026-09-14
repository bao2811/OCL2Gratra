package org.uet.dse.neo4jtgg.ocl.ir;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EPackage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Executable constructor catalog for the normative NVA-to-CQM relation.
 *
 * <p>The catalog is structural evidence. {@code spec_plan_sim_sound} in Lean
 * composes these constructor cases under their stated local semantic
 * agreements; the catalog does not turn finite Java/Neo4j tests into a proof.</p>
 */
public final class SpecPlanSimContract {
    private SpecPlanSimContract() {
    }

    public enum Status {
        PROVED_UNDER_LOCAL_AGREEMENT,
        EXCLUDED
    }

    public record Rule(String ruleId, String nvaConstructor, String cqmConstructor,
                       boolean parameterCorrespondence, boolean aliasCorrespondence,
                       boolean scopeCorrespondence, boolean multiplicityCorrespondence,
                       boolean collectionShapeCorrespondence, String semanticObligation,
                       Status status) {
        public Rule {
            if (ruleId == null || ruleId.isBlank() || nvaConstructor == null || nvaConstructor.isBlank()
                    || cqmConstructor == null || cqmConstructor.isBlank()
                    || semanticObligation == null || semanticObligation.isBlank() || status == null) {
                throw new IllegalArgumentException("Incomplete SpecPlanSim rule");
            }
        }
    }

    private static final List<Rule> RULES = List.of(
            rule("SPS-VARIABLE", "NvaVariable", "VariablePlan", "LR-Variable"),
            rule("SPS-LITERAL", "NvaLiteral", "LiteralPlan", "LR-Literal"),
            rule("SPS-SET", "NvaSetLiteral", "SetPlan", "LR-SetLiteral"),
            rule("SPS-ATTRIBUTE", "NvaAttribute", "AttributeAccessPlan", "LR-Attribute"),
            rule("SPS-NAV-ONE", "NvaNavigationOne", "NavigationAccessPlan", "LR-NavigationOne"),
            rule("SPS-NAV-MANY", "NvaNavigationMany", "NavigationAccessPlan", "LR-NavigationMany"),
            rule("SPS-VIEW-SET", "NvaViewSet", "CallPlan", "LR-CollectionView"),
            rule("SPS-ALL-INSTANCES", "NvaAllInstances", "CallPlan", "LR-AllInstances"),
            rule("SPS-NOT", "NvaNot", "UnaryPlan", "LR-Boolean"),
            rule("SPS-AND", "NvaAnd", "BinaryPlan", "LR-Boolean"),
            rule("SPS-OR", "NvaOr", "BinaryPlan", "LR-Boolean"),
            rule("SPS-COMPARE", "NvaCompare", "BinaryPlan", "LR-Scalar"),
            rule("SPS-ARITH", "NvaArithmetic", "BinaryPlan", "LR-Scalar"),
            rule("SPS-COERCE", "NvaCoerce", "CallPlan", "LR-Coercion"),
            rule("SPS-IF", "NvaIf", "IfPlan", "LR-Conditional"),
            rule("SPS-LET", "NvaLet", "LetPlan", "LR-Let"),
            rule("SPS-EXISTS", "NvaExists", "IteratorPlan", "LR-Quantifier"),
            rule("SPS-SELECT", "NvaSelect", "IteratorPlan", "LR-Filter"),
            rule("SPS-COLLECT", "NvaCollect", "IteratorPlan", "LR-Collect"),
            rule("SPS-UNIQUE", "NvaIsUnique", "IteratorPlan", "LR-IsUnique"),
            rule("SPS-SET-REL", "NvaSetRelation", "CallPlan", "LR-SetRelation"),
            rule("SPS-SET-COMB", "NvaSetCombination", "CallPlan", "LR-SetCombination"),
            rule("SPS-AS-SET", "NvaAsSet", "CallPlan", "LR-AsSet"),
            rule("SPS-COUNT", "NvaCount", "CallPlan", "LR-Cardinality"),
            rule("SPS-KIND-OF", "NvaTypeKindOf", "CallPlan", "LR-TypeAccess"),
            rule("SPS-CAST", "NvaCast", "CallPlan", "LR-TypeAccess")
    );

    private static Rule rule(String id, String nva, String cqm, String semantic) {
        return new Rule(id, nva, cqm, true, true, true, true, true, semantic,
                Status.PROVED_UNDER_LOCAL_AGREEMENT);
    }

    public static List<Rule> rules() {
        return RULES;
    }

    public static Report validate(EPackage nva, EPackage cqm) {
        List<String> errors = new ArrayList<>();
        Map<String, Integer> occurrences = new LinkedHashMap<>();
        for (Rule rule : RULES) {
            occurrences.merge(rule.nvaConstructor(), 1, Integer::sum);
            if (!(nva.getEClassifier(rule.nvaConstructor()) instanceof EClass)) {
                errors.add("Missing NVA classifier " + rule.nvaConstructor());
            }
            if (!(cqm.getEClassifier(rule.cqmConstructor()) instanceof EClass)) {
                errors.add("Missing CQM classifier " + rule.cqmConstructor());
            }
            if (!rule.parameterCorrespondence() || !rule.aliasCorrespondence()
                    || !rule.scopeCorrespondence() || !rule.multiplicityCorrespondence()
                    || !rule.collectionShapeCorrespondence()) {
                errors.add("Incomplete correspondence conditions for " + rule.ruleId());
            }
        }
        EClass expression = (EClass) nva.getEClassifier("NvaExpression");
        Set<String> concreteExpressions = nva.getEClassifiers().stream()
                .filter(EClass.class::isInstance)
                .map(EClass.class::cast)
                .filter(type -> !type.isAbstract() && (type == expression || type.getEAllSuperTypes().contains(expression)))
                .map(EClass::getName)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        for (String constructor : concreteExpressions) {
            if (occurrences.getOrDefault(constructor, 0) != 1) {
                errors.add("Expected exactly one SpecPlanSim rule for " + constructor);
            }
        }
        for (String constructor : occurrences.keySet()) {
            if (!concreteExpressions.contains(constructor)) {
                errors.add("Catalog contains non-reachable NVA constructor " + constructor);
            }
        }
        return new Report(concreteExpressions, errors);
    }

    public record Report(Set<String> constructors, List<String> errors) {
        public Report {
            constructors = Set.copyOf(constructors);
            errors = List.copyOf(errors);
        }

        public boolean valid() {
            return errors.isEmpty();
        }
    }
}
