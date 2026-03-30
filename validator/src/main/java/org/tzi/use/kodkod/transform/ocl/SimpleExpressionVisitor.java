package org.tzi.use.kodkod.transform.ocl;

import org.apache.log4j.Logger;
import org.tzi.use.kodkod.transform.TransformationException;
import org.tzi.use.uml.ocl.expr.*;

/**
 * Simple implementation of the visitor interface for use expressions.
 * 
 * @author Hendrik Reitmann
 * 
 */
public class SimpleExpressionVisitor implements ExpressionVisitor {

	private static final Logger LOG = Logger.getLogger(SimpleExpressionVisitor.class);

	@Override
	public void visitAllInstances(ExpAllInstances exp) {
		LOG.debug("ExpAllInstances");
	}

	@Override
	public void visitAny(ExpAny exp) {
		LOG.debug("ExpAny");
		visitQuery(exp);
	}

	@Override
	public void visitAsType(ExpAsType exp) {
		LOG.debug("ExpAsType");
	}

	@Override
	public void visitAttrOp(ExpAttrOp exp) {
		LOG.debug("ExpAttrOp");
	}

	@Override
	public void visitBagLiteral(ExpBagLiteral exp) {
		LOG.debug("ExpBagLiteral");
	}

	@Override
	public void visitCollect(ExpCollect exp) {
		LOG.debug("ExpCollect");
		visitQuery(exp);
	}

	@Override
	public void visitCollectNested(ExpCollectNested exp) {
		LOG.debug("ExpCollectNested");
		visitQuery(exp);
	}

	@Override
	public void visitConstBoolean(ExpConstBoolean exp) {
		LOG.debug("ExpConstBoolean");
	}

	@Override
	public void visitConstEnum(ExpConstEnum exp) {
		LOG.debug("ExpConstEnum");
	}

	@Override
	public void visitConstInteger(ExpConstInteger exp) {
		LOG.debug("ExpConstInteger");
	}

	@Override
	public void visitConstReal(ExpConstReal exp) {
		LOG.debug("ExpConstReal");
	}

	@Override
	public void visitConstString(ExpConstString exp) {
		LOG.debug("ExpConstString");
	}

	@Override
	public void visitEmptyCollection(ExpEmptyCollection exp) {
		LOG.debug("ExpEmptyCollection");
	}

	@Override
	public void visitExists(ExpExists exp) {
		LOG.debug("ExpExists");
		visitQuery(exp);
	}

	@Override
	public void visitForAll(ExpForAll exp) {
		LOG.debug("ExpForAll");
		visitQuery(exp);
	}

	@Override
	public void visitIf(ExpIf exp) {
		LOG.debug("ExpIf");
	}

	@Override
	public void visitIsKindOf(ExpIsKindOf exp) {
		LOG.debug("ExpIsKindOf");
	}

	@Override
	public void visitIsTypeOf(ExpIsTypeOf exp) {
		LOG.debug("ExpIsTypeOf");
	}

	@Override
	public void visitIsUnique(ExpIsUnique exp) {
		LOG.debug("ExpIsUnique");
		visitQuery(exp);
	}

	@Override
	public void visitIterate(ExpIterate exp) {
		LOG.debug("ExpIterate");
	}

	@Override
	public void visitLet(ExpLet exp) {
		LOG.debug("ExpLet");
	}

	@Override
	public void visitNavigation(ExpNavigation exp) {
		LOG.debug("ExpNavigation");
	}

	@Override
	public void visitNavigationClassifierSource(ExpNavigationClassifierSource exp) {
		LOG.debug("ExpNavigationClassifierSource");
	}
	
	@Override
	public void visitObjAsSet(ExpObjAsSet exp) {
		LOG.debug("ExpObjAsSet");
	}

	@Override
	public void visitObjOp(ExpObjOp exp) {
		LOG.debug("ExpObjOp");
	}

	@Override
	public void visitInstanceConstructor(ExpInstanceConstructor exp) {

	}

	@Override
	public void visitObjRef(ExpObjRef exp) {
		LOG.debug("ExpObjRef");
	}

	@Override
	public void visitOne(ExpOne exp) {
		LOG.debug("ExpOne");
		visitQuery(exp);
	}

	@Override
	public void visitOrderedSetLiteral(ExpOrderedSetLiteral exp) {
		LOG.debug("ExpOrderedSetLiteral");
	}

	@Override
	public void visitQuery(ExpQuery exp) {
		LOG.debug("ExpQuery");
	}

	@Override
	public void visitReject(ExpReject exp) {
		LOG.debug("ExpReject");
		visitQuery(exp);
	}

	@Override
	public void visitWithValue(ExpressionWithValue exp) {
		LOG.debug("ExpressionWithValue");
	}

	@Override
	public void visitSelect(ExpSelect exp) {
		LOG.debug("ExpSelect");
		visitQuery(exp);
	}

	@Override
	public void visitSequenceLiteral(ExpSequenceLiteral exp) {
		LOG.debug("ExpSequenceLiteral");
	}

	@Override
	public void visitSetLiteral(ExpSetLiteral exp) {
		LOG.debug("ExpSetLiteral");
	}

	@Override
	public void visitSortedBy(ExpSortedBy exp) {
		LOG.debug("ExpSortedBy");
	}

	@Override
	public void visitStdOp(ExpStdOp exp) {
		LOG.debug("ExpStdOp");
	}

	@Override
	public void visitTupleLiteral(ExpTupleLiteral exp) {
		LOG.debug("ExpTupleLiteral");
	}

	@Override
	public void visitTupleSelectOp(ExpTupleSelectOp exp) {
		LOG.debug("ExpTupleSelectOp");
	}

	@Override
	public void visitUndefined(ExpUndefined exp) {
		LOG.debug("ExpUndefined");
	}

	@Override
	public void visitVariable(ExpVariable exp) {
		LOG.debug("ExpVariable");
	}

	@Override
	public void visitClosure(ExpClosure expClosure) {
		LOG.debug("ExpClosure");
		visitQuery(expClosure);
	}

	@Override
	public void visitOclInState(ExpOclInState expOclInState) {
		throw new TransformationException("oclInState not supported");
	}

	@Override
	public void visitVarDeclList(VarDeclList varDeclList) {
				
	}

	@Override
	public void visitVarDecl(VarDecl varDecl) {
				
	}

	@Override
	public void visitObjectByUseId(ExpObjectByUseId expObjectByUseId) {
				
	}

	@Override
	public void visitConstUnlimitedNatural(
			ExpConstUnlimitedNatural expressionConstUnlimitedNatural) {
		throw new TransformationException("UnlimitedNatural not supported");
	}

	@Override
	public void visitSelectByKind(ExpSelectByKind expSelectByKind) {
		LOG.debug("ExpSelectByKind");
	}

	@Override
	public void visitExpSelectByType(ExpSelectByType expSelectByType) {
		LOG.debug("ExpSelectByType");
	}

	@Override
	public void visitRange(ExpRange exp) {
		LOG.debug("ExpRange");
	}

}
