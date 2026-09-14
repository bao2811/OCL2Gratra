package org.uet.dse.ocl2cypher.conformance;

import java.math.BigInteger;
import java.util.List;
import java.util.Set;
import org.uet.dse.ocl2cypher.runtime.OclType;
import org.uet.dse.ocl2cypher.runtime.OclValue;
import org.uet.dse.ocl2cypher.source.model.SchemaModel;
import org.uet.dse.ocl2cypher.source.model.Snapshot;
import org.uet.dse.ocl2cypher.source.model.UmlAssociation;
import org.uet.dse.ocl2cypher.source.model.UmlAttribute;
import org.uet.dse.ocl2cypher.source.model.UmlClass;

/**
 * Property fixtures: for each differential case the snapshot and the expected
 * invariant name place produce the surface identity the source oracle produces.
 *
 * <p>Note the carrier choice used later by Cypher: a single invariant whose
 * body is a predicate-bottom must remain indistinguishable from a missing
 * source, and the short-circuit for a Boolean connective has to know where on
 * the row its alias became whole bottom.
 */
public final class PropertyFixtures {

    private PropertyFixtures() {
    }

    public static SchemaModel personSchema() {
        return SchemaModel.builder("m")
                .clazz(UmlClass.of("Person"))
                .attribute(UmlAttribute.of("Person", "age", OclType.INTEGER))
                .build();
    }

    public static SchemaModel companySnapshotSchema() {
        return SchemaModel.builder("m")
                .clazz(UmlClass.of("Company"))
                .clazz(UmlClass.of("Employee"))
                .attribute(UmlAttribute.of("Employee", "age", OclType.INTEGER))
                .attribute(UmlAttribute.of("Employee", "salary", OclType.INTEGER))
                .association(UmlAssociation.binary("employment", "Company", "employer",
                        "Employee", "employees"))
                .build();
    }

    public static Snapshot mixedPersons() {
        return Snapshot.builder()
                .object("alice", "Person").attribute("alice", "age", intV(20))
                .object("bob", "Person").attribute("bob", "age", intV(15))
                .object("carol", "Person") // missing age → bottom → violation
                .build();
    }

    public static Snapshot companyMixed() {
        return Snapshot.builder()
                .object("acme", "Company")
                .object("e1", "Employee").attribute("e1", "age", intV(20)).attribute("e1", "salary", intV(100))
                .object("e2", "Employee").attribute("e2", "age", intV(25)).attribute("e2", "salary", intV(50))
                .object("e3", "Employee").attribute("e3", "age", intV(10)).attribute("e3", "salary", intV(999))
                .link("employment", "acme", "e1")
                .link("employment", "acme", "e2")
                .link("employment", "acme", "e3")
                .object("empty-co", "Company")
                .build();
    }

    public static OclValue intV(long n) {
        return new OclValue.IntegerValue(BigInteger.valueOf(n));
    }
}
