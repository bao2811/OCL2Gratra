package org.uet.dse.neo4j.ocl.mapper;

import org.tzi.use.uml.ocl.expr.*;
import org.uet.dse.neo4j.ocl.expr.*;
import org.uet.dse.neo4j.ocl.operation.NOpRegistry;

public class NExpressionRewriter implements ExpressionVisitor {
    private NExpression result;
    private boolean firstTime = true;
    
    public static NExpression rewrite(Expression expr) {
        NExpressionRewriter rewriter = new NExpressionRewriter();
        expr.processWithVisitor(rewriter);

        if (rewriter.result == null) {
            // fallback: wrap thô, dùng eval cũ
            //return new NFallbackExpression(expr);
            System.out.println("input expr for wwriter cant be null");
            throw new RuntimeException("null expr");
        }
        return rewriter.result;
    }




    @Override
    public void visitStdOp(ExpStdOp exp) {
        try {
            if (firstTime) {
                result = new NExpStdOp(exp);
            }
            // Map OpGeneric -> NOpGeneric
            ((NExpStdOp) result).fOp = NOpRegistry.resolve(exp.getOperation());

            Expression[] originalArgs = exp.args();
            ((NExpStdOp) result).fArgs = new NExpression[originalArgs.length];
            for (int i = 0; i < originalArgs.length; i++) {
                ((NExpStdOp) result).fArgs[i] = NExpressionRewriter.rewrite(originalArgs[i]);
            }
        } catch (ExpInvalidException e) {
            System.out.println("error reriting decorator");
            firstTime = true;
        }

    }

    @Override
    public void visitConstInteger(ExpConstInteger exp) {
        try {
            if (firstTime) {
                result = new NExprConstInteger(exp);
            }
            // Map OpGeneric -> NOpGeneric
            ((NExprConstInteger) result).fValue = exp.value();

        } catch (ExpInvalidException e) {
            System.out.println("error reriting decorator");
            firstTime = true;
        }

    }

    @Override
    public void visitVariable(ExpVariable exp) {
        try {
            if (firstTime) {
                result = new NExpVariable(exp);
            }


        } catch (Exception e) {
            System.out.println("error rewriting decorator");
            firstTime = true;
        }
    }

    @Override
    public void visitUndefined(ExpUndefined exp) {
        try {
            if (firstTime) {
                result = new NExpUndefined(exp);
            }


        } catch (Exception e) {
            System.out.println("error rewriting decorator");
            firstTime = true;
        }
    }

    @Override
    public void visitAttrOp(ExpAttrOp exp) {
        try {
            if (firstTime) {
                result = new NExpAttrOp(exp);

                ((NExpAttrOp) result).fObjExp = NExpressionRewriter.rewrite(exp.objExp());
            }

        } catch (Exception e) {
            System.out.println("error rerwriting decorator");
            firstTime = true;
        }

    }

    @Override
    public void visitConstString(ExpConstString exp) {
        try {
            if (firstTime) {
                result = new NExpConstString(exp);
            }
            // Map OpGeneric -> NOpGeneric
            ((NExpConstString) result).fValue = exp.value();

        } catch (Exception e) {
            System.out.println("error reriting decorator");
            firstTime = true;
        }
    }

    @Override
    public void visitNavigation(ExpNavigation exp) {

    }

    @Override
    public void visitObjAsSet(ExpObjAsSet exp) {

    }

    @Override
    public void visitObjOp(ExpObjOp exp) {

    }

    @Override
    public void visitInstanceConstructor(ExpInstanceConstructor exp) {

    }

    @Override
    public void visitObjRef(ExpObjRef exp) {

    }

    @Override
    public void visitOne(ExpOne exp) {

    }

    @Override
    public void visitOrderedSetLiteral(ExpOrderedSetLiteral exp) {

    }

    @Override
    public void visitQuery(ExpQuery exp) {

    }

    @Override
    public void visitReject(ExpReject exp) {

    }

    @Override
    public void visitWithValue(ExpressionWithValue exp) {

    }

    @Override
    public void visitSelect(ExpSelect exp) {

    }

    @Override
    public void visitSequenceLiteral(ExpSequenceLiteral exp) {

    }

    @Override
    public void visitSetLiteral(ExpSetLiteral exp) {

    }

    @Override
    public void visitSortedBy(ExpSortedBy exp) {

    }

    @Override
    public void visitTupleLiteral(ExpTupleLiteral exp) {

    }

    @Override
    public void visitTupleSelectOp(ExpTupleSelectOp exp) {

    }

    @Override
    public void visitClosure(ExpClosure exp) {

    }

    @Override
    public void visitOclInState(ExpOclInState exp) {

    }

    @Override
    public void visitVarDeclList(VarDeclList varDeclList) {

    }

    @Override
    public void visitVarDecl(VarDecl varDecl) {

    }

    @Override
    public void visitObjectByUseId(ExpObjectByUseId exp) {

    }

    @Override
    public void visitConstUnlimitedNatural(ExpConstUnlimitedNatural exp) {

    }

    @Override
    public void visitSelectByKind(ExpSelectByKind exp) {

    }

    @Override
    public void visitExpSelectByType(ExpSelectByType exp) {

    }

    @Override
    public void visitRange(ExpRange exp) {

    }

    @Override
    public void visitNavigationClassifierSource(ExpNavigationClassifierSource exp) {

    }

    @Override
    public void visitAllInstances(ExpAllInstances exp) {

    }

    @Override
    public void visitAny(ExpAny exp) {

    }

    @Override
    public void visitAsType(ExpAsType exp) {

    }

    @Override
    public void visitBagLiteral(ExpBagLiteral exp) {

    }

    @Override
    public void visitCollect(ExpCollect exp) {

    }

    @Override
    public void visitCollectNested(ExpCollectNested exp) {

    }

    @Override
    public void visitConstBoolean(ExpConstBoolean exp) {

    }

    @Override
    public void visitConstEnum(ExpConstEnum exp) {

    }

    @Override
    public void visitConstReal(ExpConstReal exp) {

    }

    @Override
    public void visitEmptyCollection(ExpEmptyCollection exp) {

    }

    @Override
    public void visitExists(ExpExists exp) {

    }

    @Override
    public void visitForAll(ExpForAll exp) {

    }

    @Override
    public void visitIf(ExpIf exp) {

    }

    @Override
    public void visitIsKindOf(ExpIsKindOf exp) {

    }

    @Override
    public void visitIsTypeOf(ExpIsTypeOf exp) {

    }

    @Override
    public void visitIsUnique(ExpIsUnique exp) {

    }

    @Override
    public void visitIterate(ExpIterate exp) {

    }

    @Override
    public void visitLet(ExpLet exp) {

    }

}