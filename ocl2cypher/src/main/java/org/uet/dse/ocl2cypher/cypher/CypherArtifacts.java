package org.uet.dse.ocl2cypher.cypher;

import java.util.*;
import org.uet.dse.ocl2cypher.cypher.CypherAst.*;
import org.uet.dse.ocl2cypher.qcyp.QNode;
import org.uet.dse.ocl2cypher.qcyp.QQuery;
import org.uet.dse.ocl2cypher.runtime.OclType;

/**
 * {@code R : Q_CYP → CypherAS}, factored as {@code R₀(Norm_Q(q))}:
 * {@link org.uet.dse.ocl2cypher.qcyp.NormQ} is a deterministic pre-pass
 * (canonicalizes the dual COLLECTION_COUNT/size etc. so every operation has one
 * normative Q rule body), and {@code R₀} emits the read-only CypherAS with
 * observer-driven patterns/aliases. The serializer later never invents an
 * {@code OPTIONAL MATCH} to fake a bottom — tagged values stay distinct from
 * native {@code null}.
 */
public final class CypherArtifacts {

    private CypherArtifacts() {
    }

    public static final String OCL_BOTTOM = "__oclBottom";
    public static final String OCL_KIND = "__oclKind";
    public static final String OCL_TYPE = "__oclType";
    public static final String OCL_ITEMS = "__oclItems";
    public static final String OCL_VALUE = "__oclValue";

    /** Type tag used in the tagged collection; stable within this profile. */
    public static String typeTag(OclType t) {
        Objects.requireNonNull(t, "type");
        return switch (t.kind()) {
            case BOOLEAN -> "Boolean3";
            case INTEGER -> "Integer";
            case REAL -> "Real";
            case STRING -> "String";
            case CLASS -> "Class:" + t.className();
            case SET -> "Set<" + typeTag(t.elementType()) + ">";
            case BAG -> "Bag<" + typeTag(t.elementType()) + ">";
        };
    }

    /**
     * Canonical type tag carried by a physical value. A defined collection
     * carries its element type because {@code __oclKind} already identifies
     * Set versus Bag; a whole-collection bottom carries the complete
     * collection type so it cannot be confused with an element bottom.
     */
    public static String carrierTypeTag(OclType type, boolean wholeBottom) {
        return type.isCollection() && !wholeBottom
                ? typeTag(type.elementType())
                : typeTag(type);
    }

    public static CypherExpr bottomLiteral(OclType t) {
        if (t.isCollection()) {
            return collectionTag(t, new ListExpr(List.of()), new BooleanLiteral(true));
        }
        return new MapExpr(List.of(
                new MapEntry(OCL_BOTTOM, new BooleanLiteral(true)),
                new MapEntry(OCL_TYPE, new StringLiteral(typeTag(t))),
                new MapEntry(OCL_VALUE, new NullLiteral())));
    }

    public static CypherExpr taggedScalar(OclType t, CypherExpr payload) {
        return new MapExpr(List.of(
                new MapEntry(OCL_BOTTOM, new BooleanLiteral(false)),
                new MapEntry(OCL_TYPE, new StringLiteral(typeTag(t))),
                new MapEntry(OCL_VALUE, payload)));
    }

    public static CypherExpr collectionTag(OclType collType, CypherExpr itemsList) {
        return collectionTag(collType, itemsList, new BooleanLiteral(false));
    }

    /**
     * Tagged collection with an explicit whole-collection bottom flag.  This
     * overload is used at a plan/value boundary, where an empty occurrence
     * list and a bottom source must remain observably different.
     */
    public static CypherExpr collectionTag(OclType collType, CypherExpr itemsList,
                                           CypherExpr wholeBottom) {
        String kind = collType.kind() == OclType.Kind.SET ? "SET" : "BAG";
        CypherExpr typeTag = new CaseExpr(List.of(
                new WhenThen(wholeBottom,
                        new StringLiteral(carrierTypeTag(collType, true)))),
                new StringLiteral(carrierTypeTag(collType, false)));
        return new MapExpr(List.of(
                new MapEntry(OCL_BOTTOM, wholeBottom),
                new MapEntry(OCL_KIND, new StringLiteral(kind)),
                new MapEntry(OCL_TYPE, typeTag),
                new MapEntry(OCL_ITEMS, itemsList)));
    }
}
