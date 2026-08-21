package org.uet.dse.neo4jtgg.ocl.ir;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OclMetamodelRefinementContractTest {
    private static final Pattern SUBCLASS = Pattern.compile(
            "(?m)^\\s*class\\s+([A-Za-z_][A-Za-z0-9_]*)\\s+extends\\s+%s\\s*\\{");

    @Test
    void everyCertifiedOvaExpressionClassifierAndJavaConstructorHasOneMappingStatus() throws IOException {
        Path root = workspaceRoot();
        Set<String> emfClassifiers = subclasses(
                root.resolve("md/research/model/OCL-Validation-Algebra.emf"), "Expression");
        List<Row> rows = rows(root.resolve("verification/coverage/ova_metamodel_java_refinement.csv"));

        Set<String> mappedClassifiers = rows.stream()
                .filter(row -> !row.metamodelClassifier().equals("-"))
                .map(Row::metamodelClassifier)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        assertTrue(mappedClassifiers.containsAll(emfClassifiers),
                "Missing OVA classifier rows: " + difference(emfClassifiers, mappedClassifiers));

        Map<String, String> javaStatus = javaStatuses(rows, "OclIr.");
        Set<String> optimized = Arrays.stream(OclIr.OptimizedExpression.class.getPermittedSubclasses())
                .map(Class::getSimpleName).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, String> expressionStatus = javaStatus.entrySet().stream()
                .filter(entry -> optimized.contains(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (left, right) -> left, LinkedHashMap::new));
        assertEquals(optimized, expressionStatus.keySet());
        assertEquals("EXCLUDED", expressionStatus.get("NavigationAggregation"));
        assertTrue(expressionStatus.entrySet().stream()
                .filter(entry -> !entry.getKey().equals("NavigationAggregation"))
                .allMatch(entry -> entry.getValue().equals("CERTIFIED")));
    }

    @Test
    void everyCertifiedCqmExpressionClassifierAndJavaConstructorHasOneMappingStatus() throws IOException {
        Path root = workspaceRoot();
        Set<String> emfClassifiers = subclasses(
                root.resolve("md/research/model/Cypher-Query-Model.emf"), "PlanExpression");
        List<Row> rows = rows(root.resolve("verification/coverage/cqm_metamodel_java_refinement.csv"));

        Set<String> mappedClassifiers = rows.stream()
                .filter(row -> !row.metamodelClassifier().equals("-"))
                .map(Row::metamodelClassifier)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        assertTrue(mappedClassifiers.containsAll(emfClassifiers),
                "Missing CQM classifier rows: " + difference(emfClassifiers, mappedClassifiers));
        assertTrue(mappedClassifiers.contains("NavigationMatchPlan"));

        Map<String, String> javaStatus = javaStatuses(rows, "OclCypherPlan.");
        Set<String> planConstructors = Arrays.stream(OclCypherPlan.ExpressionPlan.class.getPermittedSubclasses())
                .map(Class::getSimpleName).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, String> expressionStatus = javaStatus.entrySet().stream()
                .filter(entry -> planConstructors.contains(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (left, right) -> left, LinkedHashMap::new));
        assertEquals(planConstructors, expressionStatus.keySet());
        assertEquals("EXCLUDED", expressionStatus.get("NavigationAggregationPlan"));
        assertTrue(expressionStatus.entrySet().stream()
                .filter(entry -> !entry.getKey().equals("NavigationAggregationPlan"))
                .allMatch(entry -> entry.getValue().equals("CERTIFIED")));
    }

    @Test
    void generalAggregationConstructorsHaveNoCertifiedClassifier() {
        var ovaAggregation = new OclIr.NavigationAggregation(
                null, "x", null, null, "max",
                org.uet.dse.neo4jtgg.ocl.OclTypeBinding.scalar("Integer"));
        var cqmAggregation = new OclCypherPlan.NavigationAggregationPlan(
                null, null, "max",
                org.uet.dse.neo4jtgg.ocl.OclTypeBinding.scalar("Integer"));

        assertFalse(OclMetamodelRefinement.ovaClassifierOf(ovaAggregation).isPresent());
        assertFalse(OclMetamodelRefinement.cqmClassifierOf(cqmAggregation).isPresent());
    }

    private Set<String> subclasses(Path emf, String base) throws IOException {
        Pattern pattern = Pattern.compile(String.format(SUBCLASS.pattern(), Pattern.quote(base)));
        Matcher matcher = pattern.matcher(Files.readString(emf));
        Set<String> result = new LinkedHashSet<>();
        while (matcher.find()) result.add(matcher.group(1));
        return result;
    }

    private List<Row> rows(Path csv) throws IOException {
        return Files.readAllLines(csv).stream().skip(1).filter(line -> !line.isBlank()).map(line -> {
            String[] fields = line.split(";", -1);
            if (fields.length != 4) throw new IllegalArgumentException("Malformed refinement row: " + line);
            return new Row(fields[0], fields[1], fields[2], fields[3]);
        }).toList();
    }

    private Map<String, String> javaStatuses(List<Row> rows, String prefix) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Row row : rows) {
            for (String target : row.javaTarget().split("\\|")) {
                if (!target.startsWith(prefix)) continue;
                String constructor = target.substring(prefix.length());
                if (constructor.contains("<") || constructor.contains("+")) continue;
                String previous = result.put(constructor, row.status());
                assertTrue(previous == null || previous.equals(row.status()),
                        "Conflicting mapping status for " + constructor);
            }
        }
        return result;
    }

    private Set<String> difference(Set<String> expected, Set<String> actual) {
        Set<String> result = new LinkedHashSet<>(expected);
        result.removeAll(actual);
        return result;
    }

    private Path workspaceRoot() {
        Path working = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        if (Files.isDirectory(working.resolve("md/research/model"))) return working;
        if (working.getParent() != null && Files.isDirectory(working.getParent().resolve("md/research/model"))) {
            return working.getParent();
        }
        throw new IllegalStateException("Cannot locate workspace root from " + working);
    }

    private record Row(String metamodelClassifier, String javaTarget, String status,
                       String refinementCondition) {
    }
}
