package org.uet.dse.neo4j.ocl.operation;

import org.tzi.use.uml.ocl.type.Type;
import org.tzi.use.uml.ocl.value.Value;
import org.uet.dse.neo4j.ocl.NEvalContext;
import org.uet.dse.neo4j.ocl.expr.NExpression;
import org.uet.dse.neo4j.ocl.expr.NOpGeneric;

public abstract class NBooleanOperation extends NOpGeneric {
	public int kind() {
		return SPECIAL;
	}

	public Value nEval(NEvalContext ctx, Value[] args, Type resultType) {
		throw new RuntimeException("Use evalWithArgs");
	}

	public boolean isBooleanOperation() {
    	return true;
    }
	
	public abstract Value evalWithArgs(NEvalContext ctx, NExpression args[]);
}
