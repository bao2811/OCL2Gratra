package org.uet.dse.neo4j.ocl.mapper;

import org.tzi.use.uml.ocl.expr.operations.OpGeneric;
import org.tzi.use.uml.ocl.type.Type;
import org.tzi.use.uml.ocl.value.Value;
import org.uet.dse.neo4j.ocl.NEvalContext;
import org.uet.dse.neo4j.ocl.expr.NOpGeneric;

// Fallback khi chưa có neo4j implementation
public class NFallbackOp extends NOpGeneric {
    private final OpGeneric delegate;
    
    public NFallbackOp(OpGeneric delegate) {
        this.delegate = delegate;
    }
    
    @Override
    public String name() { return delegate.name(); }
    
    @Override
    public int kind() { return delegate.kind(); }

    @Override
    public boolean isInfixOrPrefix() {
        return false;
    }

    @Override
    public Type matches(Type[] params) {
        return null;
    }

    @Override
    public boolean isBooleanOperation() { return delegate.isBooleanOperation(); }
    
    @Override
    public Value nEval(NEvalContext ctx, Value[] args, Type resultType) {
        // delegate về OCL eval cũ - không dùng neo4j
        // args đã được eval rồi nên truyền thẳng vào
       // return delegate.eval(ctx.toOclContext(), args, resultType);
        return null;
    }
}