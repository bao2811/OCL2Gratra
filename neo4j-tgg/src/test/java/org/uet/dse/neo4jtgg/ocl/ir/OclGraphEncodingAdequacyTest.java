package org.uet.dse.neo4jtgg.ocl.ir;

import org.junit.jupiter.api.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.uet.dse.neo4j.encoding.CanonicalGraphEncoding;
import org.uet.dse.neo4jtgg.model.CypherCompilationResult;
import org.uet.dse.neo4jtgg.service.impl.DefaultOclToCypherCompiler;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression checks for the graph-side assumptions used by Theorem 0.
 *
 * <p>The proof-level statement is stronger than these renderer checks:
 * {@code Phi(MM,M)} should be information-preserving up to validation
 * equivalence, i.e. there exists a computable {@code Psi} such that
 * {@code Psi(Phi(MM,M)) ==_val (MM,M)} for the supported UML/OCL fragment.
 * These tests cover the executable adequacy obligations currently visible in
 * generated Cypher: object/type membership, attribute values, association
 * metadata, qualified navigation, and {@code allInstances()} access.</p>
 */
class OclGraphEncodingAdequacyTest {
    @Test
    void invariantContextUsesObjectAndTypePreservationPattern() {
        CypherCompilationResult result = compiler(familyModel()).compile(
                "context Family inv Named: self.name.isDefined()");

        assertTrue(result.isSupported(), result.getReason());
        assertTrue(result.getCypher().contains("MATCH (self:Object {modelKey:"), result.getCypher());
        assertTrue(result.getCypher().contains(
                "-[:ObjectInstanceOf]->(cls:UmlClass {modelKey:"), result.getCypher());
        assertTrue(result.getCypher().contains("classKey:"), result.getCypher());
        assertTrue(result.getParameters().containsValue(CanonicalGraphEncoding.modelKey("Demo")));
        assertTrue(result.getCypher().contains("RETURN DISTINCT self.use_id AS useId"), result.getCypher());
    }

    @Test
    void attributeAccessUsesCanonicalAttributeEncodingPattern() {
        CypherCompilationResult result = compiler(familyModel()).compile(
                "context Family inv Named: self.name.isDefined()");

        assertTrue(result.isSupported(), result.getReason());
        assertTrue(result.getCypher().contains("ObjectHasAttribute"), result.getCypher());
        assertTrue(result.getCypher().contains("AttributeValue"), result.getCypher());
        assertTrue(result.getCypher().contains("val.attributeKey = $"), result.getCypher());
        assertTrue(result.getCypher().contains("STARTS WITH 'v1|S|'"), result.getCypher());
        assertTrue(result.getCypher().contains("'%7C', '|'"), result.getCypher());
        assertFalse(result.getCypher().contains("replace(head([(")
                && result.getCypher().contains("\"'\", \"\")"), result.getCypher());
        assertTrue(result.getParameters().containsValue(
                CanonicalGraphEncoding.attributeKey("Demo", "Family", "name")));
    }

    @Test
    void inheritedAttributeUsesTheDefiningOwnerKeySelectedByTheBinder() {
        CypherCompilationResult result = compiler(inheritanceModel()).compile(
                "context Employee inv Named: self.name.isDefined()");

        assertTrue(result.isSupported(), result.getReason());
        assertTrue(result.getParameters().containsValue(
                CanonicalGraphEncoding.classKey("Demo", "Employee")), result.getParameters().toString());
        assertTrue(result.getParameters().containsValue(
                CanonicalGraphEncoding.attributeKey("Demo", "Person", "name")),
                result.getParameters().toString());
    }

    @Test
    void navigationAccessUsesAssociationMetadataPattern() {
        CypherCompilationResult result = compiler(familyModel()).compile(
                "context Family inv HasChildren: self.children->notEmpty()");

        assertTrue(result.isSupported(), result.getReason());
        assertTrue(result.getCypher().contains("type(r) STARTS WITH 'Link'"), result.getCypher());
        assertTrue(result.getCypher().contains("r.associationKey = $"), result.getCypher());
        assertTrue(result.getCypher().contains("r.sourceRole = $"), result.getCypher());
        assertTrue(result.getCypher().contains("r.targetRole = $"), result.getCypher());
        assertTrue(result.getParameters().containsValue(
                CanonicalGraphEncoding.associationKey("Demo", "FamilyChildren")),
                result.getParameters().toString());
        assertTrue(result.getParameters().containsValue("family"), result.getParameters().toString());
        assertTrue(result.getParameters().containsValue("children"), result.getParameters().toString());
    }

    @Test
    void naryNavigationUsesCanonicalHubAndRoleSpokes() {
        String spec = """
                model Demo
                class ServiceDepot
                end
                class Check
                end
                class Car
                end
                association Maintenance between
                    ServiceDepot[0..1] role serviceDepot
                    Check[*] role check
                    Car[*] role car
                end
                """;
        CypherCompilationResult result = compiler(compile(spec)).compile(
                "context Car inv OneDepot: self.serviceDepot->size() <= 1");

        assertTrue(result.isSupported(), result.getReason());
        assertTrue(result.getCypher().contains(":LinkHub"), result.getCypher());
        assertTrue(result.getCypher().contains(".linkKey = "), result.getCypher());
        assertTrue(result.getCypher().contains(".role = $"), result.getCypher());
        assertTrue(result.getParameters().containsValue(
                CanonicalGraphEncoding.associationKey("Demo", "Maintenance")),
                result.getParameters().toString());
        assertTrue(result.getParameters().containsValue("car"), result.getParameters().toString());
        assertTrue(result.getParameters().containsValue("serviceDepot"), result.getParameters().toString());
    }

    @Test
    void topLevelAllInstancesUsesObjectTypeEncodingPattern() {
        CypherCompilationResult result = compiler(familyModel()).compile("Family.allInstances()->size()");

        assertTrue(result.isSupported(), result.getReason());
        assertTrue(result.getCypher().contains("ObjectInstanceOf"), result.getCypher());
        assertTrue(result.getCypher().contains("RETURN size(head(COLLECT { WITH COLLECT { MATCH"),
                result.getCypher());
        assertTrue(result.getCypher().contains("THEN [] ELSE finiteSet"), result.getCypher());
        assertTrue(result.getCypher().contains("AS finiteSet"), result.getCypher());
        assertTrue(result.getCypher().contains("RETURN DISTINCT"), result.getCypher());
        assertTrue(result.getCypher().contains("AS value"), result.getCypher());
        assertTrue(result.getParameters().containsValue(
                CanonicalGraphEncoding.classKey("Demo", "Family")), result.getParameters().toString());
    }

    @Test
    void scalarQualifiedNavigationUsesQualifierEncodingPattern() {
        CypherCompilationResult result = compiler(qualifiedModel()).compile(
                "context Library inv ShelfLookup: self.book['A1']->notEmpty()");

        assertTrue(result.isSupported(), result.getReason());
        assertTrue(result.getCypher().contains("sourceQualifiers[0]"), result.getCypher());
        assertTrue(result.getCypher().contains("AND NOT ("), result.getCypher());
        assertTrue(result.getCypher().contains("AND r.sourceQualifiers[0]"), result.getCypher());
        assertTrue(result.getCypher().contains("'v1|S|' +"), result.getCypher());
        assertTrue(result.getCypher().contains("'%', '%25'"), result.getCypher());
        assertTrue(result.getCypher().contains("'|', '%7C'"), result.getCypher());
        assertTrue(result.getParameters().containsValue(
                CanonicalGraphEncoding.associationKey("Demo", "Catalog")),
                result.getParameters().toString());
        assertTrue(result.getParameters().containsValue("library"), result.getParameters().toString());
        assertTrue(result.getParameters().containsValue("book"), result.getParameters().toString());
    }

    @Test
    void reverseQualifiedNavigationUsesTargetEndQualifierEncodingPattern() {
        CypherCompilationResult result = compiler(qualifiedModel()).compile(
                "context Book inv ReverseShelfLookup: self.library['B1']->notEmpty()");

        assertTrue(result.isSupported(), result.getReason());
        assertTrue(result.getCypher().contains(")<-[r]-("), result.getCypher());
        assertTrue(result.getCypher().contains("targetQualifiers[0]"), result.getCypher());
        assertTrue(result.getCypher().contains("r.associationKey = $"), result.getCypher());
        assertTrue(result.getParameters().containsValue(
                CanonicalGraphEncoding.associationKey("Demo", "Catalog")),
                result.getParameters().toString());
    }

    private DefaultOclToCypherCompiler compiler(MModel model) {
        return new DefaultOclToCypherCompiler(model);
    }

    private MModel familyModel() {
        String spec = """
                model Demo
                class Family
                attributes
                    name : String
                end
                class Person
                attributes
                    name : String
                    age : Integer
                end
                association FamilyChildren between
                    Family[*] role family
                    Person[*] role children
                end
                """;
        return compile(spec);
    }

    private MModel qualifiedModel() {
        String spec = """
                model Demo
                class Library
                end
                class Book
                end
                association Catalog between
                    Library[1] role library qualifier (shelf : String)
                    Book[*] role book qualifier (catalogue : String)
                end
                """;
        return compile(spec);
    }

    private MModel inheritanceModel() {
        String spec = """
                model Demo
                class Person
                attributes
                    name : String
                end
                class Employee < Person
                end
                """;
        return compile(spec);
    }

    private MModel compile(String spec) {
        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());
        return model;
    }
}
