package org.uet.dse.neo4jtgg.ocl;

import org.uet.dse.neo4j.oclite.ast.ASTBinary;
import org.uet.dse.neo4j.oclite.ast.ASTBooleanLiteral;
import org.uet.dse.neo4j.oclite.ast.ASTCollectionOp;
import org.uet.dse.neo4j.oclite.ast.ASTCollectionLiteral;
import org.uet.dse.neo4j.oclite.ast.ASTCollectionRange;
import org.uet.dse.neo4j.oclite.ast.ASTContext;
import org.uet.dse.neo4j.oclite.ast.ASTEnumLiteral;
import org.uet.dse.neo4j.oclite.ast.ASTExpression;
import org.uet.dse.neo4j.oclite.ast.ASTIf;
import org.uet.dse.neo4j.oclite.ast.ASTInvalidLiteral;
import org.uet.dse.neo4j.oclite.ast.ASTIntegerLiteral;
import org.uet.dse.neo4j.oclite.ast.ASTIterator;
import org.uet.dse.neo4j.oclite.ast.ASTIterate;
import org.uet.dse.neo4j.oclite.ast.ASTLet;
import org.uet.dse.neo4j.oclite.ast.ASTMethodCall;
import org.uet.dse.neo4j.oclite.ast.ASTNot;
import org.uet.dse.neo4j.oclite.ast.ASTNullLiteral;
import org.uet.dse.neo4j.oclite.ast.ASTProperty;
import org.uet.dse.neo4j.oclite.ast.ASTRealLiteral;
import org.uet.dse.neo4j.oclite.ast.ASTSetLiteral;
import org.uet.dse.neo4j.oclite.ast.ASTStringLiteral;
import org.uet.dse.neo4j.oclite.ast.ASTVar;
import org.uet.dse.neo4j.oclite.ast.ASTUnary;
import org.uet.dse.neo4jtgg.ocl.diagnostic.OclCodedUnsupportedOperationException;
import org.uet.dse.neo4jtgg.ocl.diagnostic.OclDiagnosticCode;

import java.util.Locale;
import java.util.Set;

/**
 * Closed-world syntactic admission policy for the theorem-certified OCL_val
 * research pipeline.
 *
 * <p>The general compiler may retain experimental constructs. Calls that
 * produce proof/conformance artifacts must pass this policy before binding so
 * parser support cannot silently widen the theorem domain.</p>
 */
public final class OclValAdmissionPolicy {
    private static final Set<String> ITERATORS = Set.of(
            "exists", "forall", "select", "reject", "collect", "isunique");
    private static final Set<String> COLLECTION_OPERATIONS = Set.of(
            "includes", "excludes", "includesall", "excludesall",
            "union", "intersection", "asset", "size", "isempty", "notempty");
    private static final Set<String> METHODS = Set.of(
            "allinstances", "ocliskindof", "oclastype");

    private OclValAdmissionPolicy() {
    }

    public static void verify(ASTContext invariant) {
        if (invariant == null || invariant.expression == null) {
            reject("missing invariant expression");
        }
        verifyExpression(invariant.expression);
    }

    private static void verifyExpression(ASTExpression expression) {
        if (expression instanceof ASTVar
                || expression instanceof ASTBooleanLiteral
                || expression instanceof ASTIntegerLiteral
                || expression instanceof ASTRealLiteral
                || expression instanceof ASTStringLiteral
                || expression instanceof ASTEnumLiteral
                || expression instanceof ASTNullLiteral) {
            return;
        }
        if (expression instanceof ASTSetLiteral setLiteral) {
            setLiteral.elements.forEach(OclValAdmissionPolicy::verifyExpression);
            return;
        }
        if (expression instanceof ASTCollectionLiteral collectionLiteral) {
            reject("collection literal kind `" + collectionLiteral.kind
                    + "`; OCL_val currently admits finite Set literals only");
        }
        if (expression instanceof ASTCollectionRange) {
            reject("collection literal range");
        }
        if (expression instanceof ASTInvalidLiteral) {
            reject("invalid literal");
        }
        if (expression instanceof ASTUnary unary) {
            reject("unary operator `" + unary.operator + "`");
        }
        if (expression instanceof ASTNot not) {
            verifyExpression(not.expression);
            return;
        }
        if (expression instanceof ASTBinary binary) {
            verifyExpression(binary.left);
            verifyExpression(binary.right);
            return;
        }
        if (expression instanceof ASTIf ifExpression) {
            verifyExpression(ifExpression.condition);
            verifyExpression(ifExpression.thenBranch);
            verifyExpression(ifExpression.elseBranch);
            return;
        }
        if (expression instanceof ASTLet let) {
            verifyExpression(let.value);
            verifyExpression(let.body);
            return;
        }
        if (expression instanceof ASTProperty property) {
            if (property.atPre) {
                reject("property call `" + property.name + "@pre`");
            }
            verifyExpression(property.source);
            property.qualifiers.forEach(OclValAdmissionPolicy::verifyExpression);
            return;
        }
        if (expression instanceof ASTIterator iterator) {
            requireAdmitted("iterator", iterator.operation, ITERATORS);
            if (iterator.iteratorVariables.size() != 1) {
                reject("iterator `" + iterator.operation + "` with "
                        + iterator.iteratorVariables.size() + " variables");
            }
            verifyExpression(iterator.source);
            verifyExpression(iterator.body);
            return;
        }
        if (expression instanceof ASTIterate) {
            reject("iterate expression");
        }
        if (expression instanceof ASTCollectionOp collectionOperation) {
            requireAdmitted("collection operation", collectionOperation.opName, COLLECTION_OPERATIONS);
            verifyExpression(collectionOperation.source);
            collectionOperation.args.forEach(OclValAdmissionPolicy::verifyExpression);
            return;
        }
        if (expression instanceof ASTMethodCall methodCall) {
            if (methodCall.atPre) {
                reject("operation call `" + methodCall.methodName + "@pre`");
            }
            if ("oclIsTypeOf".equalsIgnoreCase(methodCall.methodName)) {
                throw new OclCodedUnsupportedOperationException(
                        OclDiagnosticCode.OCL_IS_TYPE_OF_OUTSIDE_CERTIFIED_FRAGMENT,
                        "method `oclIsTypeOf` is outside OCL_val until a proved direct "
                                + "runtime-class accessor is available.");
            }
            requireAdmitted("method", methodCall.methodName, METHODS);
            verifyExpression(methodCall.source);
            methodCall.args.forEach(OclValAdmissionPolicy::verifyExpression);
            return;
        }
        reject("AST node " + expression.getClass().getSimpleName());
    }

    private static void requireAdmitted(String kind, String name, Set<String> admitted) {
        String normalized = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (!admitted.contains(normalized)) {
            reject(kind + " `" + name + "`");
        }
    }

    private static void reject(String construct) {
        throw new OclCodedUnsupportedOperationException(
                OclDiagnosticCode.OCL_VAL_EXCLUDED_CONSTRUCT,
                construct + " is outside the frozen OCL_val theorem fragment; "
                        + "experimental general-compiler support does not imply certified admission.");
    }
}
