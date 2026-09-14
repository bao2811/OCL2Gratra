package org.uet.dse.neo4jtgg.ocl;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OclTypeConformanceTest {
    private static OclMetamodelIndex index;

    @BeforeAll
    static void loadMetamodel() {
        StringWriter diagnostics = new StringWriter();
        MModel model = USECompiler.compileSpecification("""
                model Typing
                class Person
                end
                class Employee < Person
                end
                class Manager < Employee
                end
                class Auditor < Person
                end
                class Hybrid < Employee, Auditor
                end
                class Company
                end
                """, "typing.use", new PrintWriter(diagnostics, true), new ModelFactory());
        assertNotNull(model, diagnostics.toString());
        index = new OclMetamodelIndex(model);
    }

    @Test
    void implementsConcreteScalarBottomAndTopRules() {
        assertFalse(conforms(null, scalar("Integer")));
        assertFalse(conforms(scalar("Integer"), null));
        assertTrue(conforms(scalar("Void"), node("Company")));
        assertTrue(conforms(set(scalar("String")), scalar("OclAny")));
        assertTrue(conforms(scalar("Integer"), scalar("Integer")));
        assertTrue(conforms(scalar("Integer"), scalar("Real")));
        assertTrue(conforms(scalar("UnlimitedNatural"), scalar("Integer")));
        assertFalse(conforms(scalar("UnlimitedNatural"), scalar("Real")));
        assertFalse(conforms(scalar("String"), scalar("Integer")));
    }

    @Test
    void implementsReflexiveTransitiveUmlGeneralization() {
        assertTrue(conforms(node("Manager"), node("Manager")));
        assertTrue(conforms(node("Manager"), node("Employee")));
        assertTrue(conforms(node("Manager"), node("Person")));
        assertTrue(conforms(node("Hybrid"), node("Employee")));
        assertTrue(conforms(node("Hybrid"), node("Auditor")));
        assertTrue(conforms(node("Hybrid"), node("Person")));
        assertFalse(conforms(node("Person"), node("Manager")));
        assertFalse(conforms(node("Company"), node("Person")));
        assertFalse(conforms(node("Person"), scalar("String")));
    }

    @Test
    void auditsDirectParentClosureAgainstUseAllParents() {
        UmlClassHierarchyIndex hierarchy = index.classHierarchy();
        assertEquals(Set.of("Employee", "Auditor"), hierarchy.directParentsOf("Hybrid"));
        assertEquals(Set.of("Employee", "Auditor", "Person"), hierarchy.allParentsOf("Hybrid"));
        assertTrue(hierarchy.conformsTo("Hybrid", "Hybrid"));
        assertTrue(hierarchy.conformsTo("Hybrid", "Person"));
        assertFalse(hierarchy.conformsTo("Person", "Hybrid"));
    }

    @Test
    void rejectsInvalidHierarchyClosureInputs() {
        assertThrows(IllegalArgumentException.class,
                () -> UmlClassHierarchyIndex.computeClosure(Map.of(
                        "A", Set.of("B"), "B", Set.of("A"))));
        assertThrows(IllegalArgumentException.class,
                () -> UmlClassHierarchyIndex.computeClosure(Map.of(
                        "Child", Set.of("Missing"))));
        assertThrows(IllegalArgumentException.class,
                () -> index.classHierarchy().conformsTo("Missing", "Person"));
    }

    @Test
    void implementsRecursiveCovariantCollectionConformance() {
        assertTrue(conforms(set(node("Manager")), set(node("Person"))));
        assertTrue(conforms(set(node("Manager")), collection(node("Person"))));
        assertTrue(conforms(set(set(node("Manager"))), set(set(node("Person")))));
        assertFalse(conforms(set(node("Person")), set(node("Manager"))));
        assertFalse(conforms(set(node("Person")), sequence(node("Person"))));
        assertFalse(conforms(set(node("Person")), node("Person")));
    }

    @Test
    void refinementMatrixCoversEveryExecutableBranch() throws Exception {
        Path matrix = resolveWorkspaceFile("verification/coverage/ocl_type_conformance_refinement.csv");
        List<String[]> rows = Files.readAllLines(matrix).stream()
                .skip(1)
                .filter(line -> !line.isBlank())
                .map(line -> line.split(",", -1))
                .toList();
        Set<String> expected = new LinkedHashSet<>(Arrays.asList(
                "TC-NULL", "TC-VOID", "TC-OCLANY", "TC-COLLECTION-SHAPE",
                "TC-COLLECTION-KIND", "TC-COLLECTION-RECURSION", "TC-UML-REFLEXIVE",
                "TC-UML-TRANSITIVE", "TC-KIND", "TC-INTEGER-REAL",
                "TC-UNLIMITED-INTEGER", "TC-SCALAR-EXACT"));
        assertTrue(rows.stream().allMatch(row -> row.length == 6));
        assertTrue(rows.stream().allMatch(row -> "EXECUTABLE_WITNESS".equals(row[5])));
        assertTrue(rows.stream().allMatch(row -> !row[2].isBlank()));
        assertTrue(rows.stream().map(row -> row[0]).collect(
                java.util.stream.Collectors.toCollection(LinkedHashSet::new)).equals(expected));
    }

    @Test
    void hierarchyRefinementMatrixCoversClosureBoundary() throws Exception {
        Path matrix = resolveWorkspaceFile("verification/coverage/uml_class_hierarchy_refinement.csv");
        List<String[]> rows = Files.readAllLines(matrix).stream()
                .skip(1)
                .filter(line -> !line.isBlank())
                .map(line -> line.split(",", -1))
                .toList();
        Set<String> expected = new LinkedHashSet<>(Arrays.asList(
                "UCH-DIRECT", "UCH-CLOSURE", "UCH-USE-AUDIT",
                "UCH-REFLEXIVE", "UCH-TRANSITIVE"));
        assertTrue(rows.stream().allMatch(row -> row.length == 6));
        assertTrue(rows.stream().allMatch(row -> "EXECUTABLE_WITNESS".equals(row[5])));
        assertEquals(expected, rows.stream().map(row -> row[0]).collect(
                java.util.stream.Collectors.toCollection(LinkedHashSet::new)));
    }

    private static boolean conforms(OclTypeBinding actual, OclTypeBinding declared) {
        return OclTypeConformance.conformsTo(index, actual, declared);
    }

    private static OclTypeBinding scalar(String name) {
        return OclTypeBinding.scalar(name);
    }

    private static OclTypeBinding node(String name) {
        return OclTypeBinding.node(name);
    }

    private static OclTypeBinding set(OclTypeBinding element) {
        return OclTypeBinding.collectionOf(element, OclTypeBinding.CollectionKind.SET);
    }

    private static OclTypeBinding collection(OclTypeBinding element) {
        return OclTypeBinding.collectionOf(element, OclTypeBinding.CollectionKind.COLLECTION);
    }

    private static OclTypeBinding sequence(OclTypeBinding element) {
        return OclTypeBinding.collectionOf(element, OclTypeBinding.CollectionKind.SEQUENCE);
    }

    private static Path resolveWorkspaceFile(String relative) {
        Path working = Path.of("").toAbsolutePath().normalize();
        for (Path root : List.of(working, working.resolve(".."))) {
            Path candidate = root.resolve(relative).normalize();
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Cannot locate workspace file: " + relative);
    }
}
