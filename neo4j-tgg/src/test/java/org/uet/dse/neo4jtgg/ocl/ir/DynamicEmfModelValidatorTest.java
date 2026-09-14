package org.uet.dse.neo4jtgg.ocl.ir;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamicEmfModelValidatorTest {
    @TempDir
    Path temp;

    @Test
    void ovaCqmAndNvaInstancesConformToTheirActualEcorePackages() throws Exception {
        Path root = workspaceRoot();
        assertTrue(DynamicEmfModelValidator.load(
                root.resolve("md/research/model/OCL-Validation-Algebra.ecore"),
                root.resolve("verification/instances/ova-certified-v1.xmi")).valid());
        assertTrue(DynamicEmfModelValidator.load(
                root.resolve("md/research/model/Cypher-Query-Model.ecore"),
                root.resolve("verification/instances/cqm-certified-v1.xmi")).valid());
        assertTrue(DynamicEmfModelValidator.load(
                root.resolve("md/research/specification/metamodel/Normalized-Validation-Algebra.ecore"),
                root.resolve("verification/instances/nva-certified-v1.xmi")).valid());
    }

    @Test
    void emfRejectsWrongEnumMissingContainmentAndDanglingReference() throws Exception {
        Path root = workspaceRoot();
        Path ecore = root.resolve("md/research/specification/metamodel/Normalized-Validation-Algebra.ecore");
        String valid = Files.readString(root.resolve("verification/instances/nva-certified-v1.xmi"));

        assertInvalid(ecore, valid.replace("kind=\"BOOLEAN\"", "kind=\"BAG\""), "wrong-enum.xmi");
        assertInvalid(ecore, valid.replaceAll("(?s)\\s*<resultType kind=\"BOOLEAN\" typeName=\"Boolean\"/>\\s*", ""),
                "missing-result-type.xmi");

        String withVariable = valid.replaceAll(
                "xsi:type=\"nva:NvaLiteral\"\\s+nodeId=\"nva_literal_true\"\\s+"
                        + "kind=\"BOOLEAN\"\\s+lexicalValue=\"true\"",
                "xsi:type=\"nva:NvaVariable\" nodeId=\"nva_literal_true\" declaration=\"missing_symbol\"");
        assertInvalid(ecore, withVariable, "dangling-reference.xmi");
    }

    @Test
    void nvaStructuralValidityDoesNotForgeSemanticCertification() throws Exception {
        Path root = workspaceRoot();
        Path ecore = root.resolve("md/research/specification/metamodel/Normalized-Validation-Algebra.ecore");
        Path instance = root.resolve("verification/instances/nva-certified-v1.xmi");
        NvaCertifiedModelValidator.Report structural = NvaCertifiedModelValidator.validate(ecore, instance);
        assertTrue(structural.valid(), structural.issues()::toString);
        assertFalse(NvaCertifiedModelValidator.certify(ecore, instance, null).certified());
        assertTrue(NvaCertifiedModelValidator.certify(ecore, instance, ignored -> true).certified());
    }

    @Test
    void nvaRejectsWrongProducerAndWrongPredicateTypeForNamedReasons() throws Exception {
        Path root = workspaceRoot();
        Path ecore = root.resolve("md/research/specification/metamodel/Normalized-Validation-Algebra.ecore");
        String valid = Files.readString(root.resolve("verification/instances/nva-certified-v1.xmi"));

        Path producer = temp.resolve("wrong-producer.xmi");
        Files.writeString(producer, valid.replace("producerVersion=\"t-norm-v1\"",
                "producerVersion=\"ocl-ir-optimizer-v2-capture-safe\""));
        var producerReport = NvaCertifiedModelValidator.validate(ecore, producer);
        assertTrue(producerReport.issues().stream().anyMatch(issue -> issue.code().equals("WF_NVA_PRODUCER")));

        Path type = temp.resolve("wrong-type.xmi");
        Files.writeString(type, valid.replace("kind=\"BOOLEAN\" typeName=\"Boolean\"",
                "kind=\"INTEGER\" typeName=\"Integer\""));
        var typeReport = NvaCertifiedModelValidator.validate(ecore, type);
        assertTrue(typeReport.issues().stream().anyMatch(issue -> issue.code().equals("WF_NVA_INVARIANT_TYPE")));
    }

    @Test
    void nvaMutationsKillScopeBottomAndNormalizationRules() throws Exception {
        Path root = workspaceRoot();
        Path ecore = root.resolve("md/research/specification/metamodel/Normalized-Validation-Algebra.ecore");

        String outOfScopeValue = String.join("\n",
                "<predicate xsi:type=\"nva:NvaLet\" nodeId=\"let_scope\">",
                "  <resultType kind=\"BOOLEAN\" typeName=\"Boolean\"/>",
                "  <declaration symbolId=\"inner_symbol\" name=\"inner\"><declaredType kind=\"OBJECT\" typeName=\"Person\"/></declaration>",
                "  <value xsi:type=\"nva:NvaVariable\" nodeId=\"early_reference\" declaration=\"//@invariants.0/@predicate/@declaration\">",
                "    <resultType kind=\"OBJECT\" typeName=\"Person\"/>",
                "  </value>",
                "  <body xsi:type=\"nva:NvaLiteral\" nodeId=\"let_body\" kind=\"BOOLEAN\" lexicalValue=\"true\">",
                "    <resultType kind=\"BOOLEAN\" typeName=\"Boolean\"/>",
                "  </body>",
                "</predicate>");
        assertNvaIssue(ecore, nvaWithPredicate(outOfScopeValue), "scope-mutation.xmi", "WF_NVA_SCOPE");

        String bottomSet = String.join("\n",
                "<predicate xsi:type=\"nva:NvaLiteral\" nodeId=\"bottom_set\" kind=\"BOTTOM\" lexicalValue=\"__oclBottom\">",
                "  <resultType kind=\"SET\" typeName=\"Set\"><elementType kind=\"OBJECT\" typeName=\"Person\"/></resultType>",
                "</predicate>");
        assertNvaIssue(ecore, nvaWithPredicate(bottomSet), "bottom-mutation.xmi", "WF_NVA_BOTTOM");

        String countNavigationRedex = String.join("\n",
                "<predicate xsi:type=\"nva:NvaCompare\" nodeId=\"count_gt_zero\" operator=\"GT\">",
                "  <resultType kind=\"BOOLEAN\" typeName=\"Boolean\"/>",
                "  <left xsi:type=\"nva:NvaCount\" nodeId=\"count_nav\">",
                "    <resultType kind=\"INTEGER\" typeName=\"Integer\"/>",
                "    <source xsi:type=\"nva:NvaNavigationMany\" nodeId=\"friends_nav\" associationName=\"Friendship\" associationKey=\"friendship\" sourceClassName=\"Person\" targetClassName=\"Person\" direction=\"OUTGOING\">",
                "      <resultType kind=\"SET\" typeName=\"Set\"><elementType kind=\"OBJECT\" typeName=\"Person\"/></resultType>",
                "      <source xsi:type=\"nva:NvaVariable\" nodeId=\"self_ref\" declaration=\"//@invariants.0/@selfVariable\"><resultType kind=\"OBJECT\" typeName=\"Person\"/></source>",
                "    </source>",
                "    <sourceCollectionType kind=\"SET\" typeName=\"Set\"><elementType kind=\"OBJECT\" typeName=\"Person\"/></sourceCollectionType>",
                "  </left>",
                "  <right xsi:type=\"nva:NvaLiteral\" nodeId=\"zero\" kind=\"INTEGER\" lexicalValue=\"0\"><resultType kind=\"INTEGER\" typeName=\"Integer\"/></right>",
                "</predicate>");
        assertNvaIssue(ecore, nvaWithPredicate(countNavigationRedex), "redex-mutation.xmi", "WF_NVA_REDEX");
    }

    private void assertInvalid(Path ecore, String xmi, String name) throws Exception {
        Path instance = temp.resolve(name);
        Files.writeString(instance, xmi);
        try {
            assertFalse(DynamicEmfModelValidator.load(ecore, instance).valid(), name);
        } catch (RuntimeException rejectedDuringLoad) {
            // Lexically illegal enum values and some malformed references are
            // rejected by EMF's XMI loader before Diagnostician can run.  That
            // is a successful negative conformance result, not a test error.
            assertTrue(rejectedDuringLoad.getMessage() != null, name);
        }
    }

    private void assertNvaIssue(Path ecore, String xmi, String name, String expectedCode) throws Exception {
        Path instance = temp.resolve(name);
        Files.writeString(instance, xmi);
        var report = NvaCertifiedModelValidator.validate(ecore, instance);
        assertTrue(report.issues().stream().anyMatch(issue -> issue.code().equals(expectedCode)),
                () -> expectedCode + " not reported: " + report.issues());
    }

    private String nvaWithPredicate(String predicate) {
        return String.join("\n",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
                "<nva:NvaModule xmi:version=\"2.0\" xmlns:xmi=\"http://www.omg.org/XMI\"",
                "    xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:nva=\"https://uet-dse.org/nva/1.0\"",
                "    languageVersion=\"nva-certified-v1\" certificationProfile=\"OCL_VAL_FINITE_SET_V1\">",
                "  <invariants invariantId=\"mutation_inv\" name=\"MutationInvariant\" contextClassName=\"Person\" producerVersion=\"t-norm-v1\">",
                "    <selfVariable symbolId=\"self_symbol\" name=\"self\"><declaredType kind=\"OBJECT\" typeName=\"Person\"/></selfVariable>",
                predicate,
                "  </invariants>",
                "</nva:NvaModule>");
    }

    private Path workspaceRoot() {
        Path working = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        if (Files.isDirectory(working.resolve("md/research/model"))) return working;
        if (working.getParent() != null && Files.isDirectory(working.getParent().resolve("md/research/model"))) {
            return working.getParent();
        }
        throw new IllegalStateException("Cannot locate workspace root from " + working);
    }
}
