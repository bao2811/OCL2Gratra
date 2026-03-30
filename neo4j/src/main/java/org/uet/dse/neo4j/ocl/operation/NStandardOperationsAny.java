package org.uet.dse.neo4j.ocl.operation;

import com.google.common.collect.Multimap;
import org.tzi.use.uml.ocl.type.Type;
import org.tzi.use.uml.ocl.type.TypeFactory;
import org.tzi.use.uml.ocl.value.BooleanValue;
import org.tzi.use.uml.ocl.value.Value;
import org.tzi.use.util.StringUtil;
import org.uet.dse.neo4j.ocl.NEvalContext;
import org.uet.dse.neo4j.ocl.expr.NExpression;
import org.uet.dse.neo4j.ocl.expr.NOpGeneric;

public class NStandardOperationsAny {
	public static void registerTypeOperations(Multimap<String, NOpGeneric> opmap) {
		// generic operations on all types
		NOpGeneric.registerOperation(new NOp_equal(), opmap);
		NOpGeneric.registerOperation(new NOp_notequal(), opmap);
		NOpGeneric.registerOperation(new NOp_isDefined(), opmap);
		NOpGeneric op = new NOp_isUndefined();
		NOpGeneric.registerOperation(op, opmap);
		NOpGeneric.registerOperation("oclIsUndefined", op, opmap);
	}
}

// --------------------------------------------------------
//
// Generic operations on all types.
//
// --------------------------------------------------------

/* = : T1 x T2 -> Boolean, with T2 <= T1 or T1 <= T2 */
final class NOp_equal extends NOpGeneric {
	public String name() {
		return "=";
	}

	public int kind() {
		return SPECIAL;
	}

	public boolean isInfixOrPrefix() {
		return true;
	}

	public Type matches(Type params[]) {
		if (params.length == 2 && params[0].getLeastCommonSupertype(params[1]) != null)
			return TypeFactory.mkBoolean();
		else
			return null;
	}

	@Override
	public String checkWarningUnrelatedTypes(NExpression args[]) {
		Type lcst = args[0].type().getLeastCommonSupertype(args[1].type());
		
		if ((!(args[0].type().isTypeOfOclAny() || args[1].type().isTypeOfOclAny()) && lcst.isTypeOfOclAny()) ||
				(!(args[0].type().isTypeOfCollection() || args[1].type().isTypeOfCollection()) && lcst.isTypeOfCollection())) {
			return "Expression " + StringUtil.inQuotes(this.stringRep(args, "")) +
					 " can never evaluate to true because " + StringUtil.inQuotes(args[0].type()) + 
					 " and " + StringUtil.inQuotes(args[1].type()) + " are unrelated.";
		}
		
		return null;
	}
	
	public Value nEval(NEvalContext ctx, Value[] args, Type resultType) {
		boolean res;

		if (args[0].isUndefined())
			return BooleanValue.get(args[1].isUndefined());
		
		if (args[1].type().conformsTo(args[0].type()))
			res = args[0].equals(args[1]);
		else if (args[0].type().conformsTo(args[1].type()))
			res = args[1].equals(args[0]);
		else
			res = false;

		return BooleanValue.get(res);
	}
}

// --------------------------------------------------------

/* <> : T1 x T2 -> Boolean, with T2 <= T1 or T1 <= T2 */
final class NOp_notequal extends NOpGeneric {
	public String name() {
		return "<>";
	}

	public int kind() {
		return SPECIAL;
	}

	public boolean isInfixOrPrefix() {
		return true;
	}

	public Type matches(Type params[]) {
		if (params.length == 2 && params[0].getLeastCommonSupertype(params[1]) != null)
			return TypeFactory.mkBoolean();
		else
			return null;
	}

	public Value nEval(NEvalContext ctx, Value[] args, Type resultType) {
		if (args[0].isUndefined())
			return BooleanValue.get(!args[1].isUndefined());
		
		boolean res = !args[0].equals(args[1]);
		return BooleanValue.get(res);
	}
	
	@Override
	public String checkWarningUnrelatedTypes(NExpression args[]) {
		Type lcst = args[0].type().getLeastCommonSupertype(args[1].type());
		
		if ((!(args[0].type().isTypeOfOclAny() || args[1].type().isTypeOfOclAny()) && lcst.isTypeOfOclAny()) ||
				(!(args[0].type().isTypeOfCollection() || args[1].type().isTypeOfCollection()) && lcst.isTypeOfCollection())) {
			return "Expression " + StringUtil.inQuotes(this.stringRep(args, "")) + 
					 " can never evaluate to false because " + StringUtil.inQuotes(args[0].type()) + 
					 " and " + StringUtil.inQuotes(args[1].type()) + " are unrelated.";
		}
		
		return null;
	}
}

// --------------------------------------------------------

/* isDefined : T -> Boolean */
final class NOp_isDefined extends NOpGeneric {
	public String name() {
		return "isDefined";
	}

	public int kind() {
		return SPECIAL;
	}

	public boolean isInfixOrPrefix() {
		return false;
	}

	public Type matches(Type params[]) {
		return (params.length == 1) ? TypeFactory.mkBoolean() : null;
	}

	public Value nEval(NEvalContext ctx, Value[] args, Type resultType) {
		boolean res = !args[0].isUndefined();
		return BooleanValue.get(res);
	}
	
	@Override
	public String checkWarningUnrelatedTypes(NExpression args[]) {
		if (args[0].type().isTypeOfVoidType()) {
			return "Expression " + StringUtil.inQuotes(this.stringRep(args, "")) + 
					 " can never evaluate to true because " + StringUtil.inQuotes(args[0].type()) + 
					 " is always undefined";
		}
		
		return null;
	}
}

// --------------------------------------------------------

/* isUndefined : T -> Boolean */
final class NOp_isUndefined extends NOpGeneric {
	public String name() {
		return "isUndefined";
	}

	public int kind() {
		return SPECIAL;
	}

	public boolean isInfixOrPrefix() {
		return false;
	}

	public Type matches(Type params[]) {
		return (params.length == 1) ? TypeFactory.mkBoolean() : null;
	}

	public Value nEval(NEvalContext ctx, Value[] args, Type resultType) {
		boolean res = args[0].isUndefined();
		return BooleanValue.get(res);
	}
	
	@Override
	public String checkWarningUnrelatedTypes(NExpression args[]) {
		if (args[0].type().isTypeOfVoidType()) {
			return "Expression " + StringUtil.inQuotes(this.stringRep(args, "")) + 
					 " can never evaluate to false because " + StringUtil.inQuotes(args[0].type()) + 
					 " is always undefined";
		}
		
		return null;
	}
}
