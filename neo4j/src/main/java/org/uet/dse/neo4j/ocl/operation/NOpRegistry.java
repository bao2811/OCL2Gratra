package org.uet.dse.neo4j.ocl.operation;

import org.tzi.use.uml.ocl.expr.operations.OpGeneric;
import org.uet.dse.neo4j.ocl.expr.NOpGeneric;
import org.uet.dse.neo4j.ocl.mapper.NFallbackOp;

import java.util.HashMap;
import java.util.Map;

public class NOpRegistry {
    // Map từ tên op sang NOpGeneric implementation
    private static final Map<String, NOpGeneric> registry = new HashMap<>();
    
    static {
        // đăng ký các op neo4j của bạn
        register(new NOp_equal());
        register(new NOp_number_add());
//        register(new NOpOr());
//        register(new NOpSize());
        // ... các op khác
    }
    
    public static void register(NOpGeneric op) {
        registry.put(op.name(), op);
    }
    
    /**
     * Tìm NOpGeneric tương ứng với OpGeneric cũ.
     * Nếu không có implementation neo4j, fallback về wrapper dùng eval cũ.
     */
    public static NOpGeneric resolve(OpGeneric op) {
        NOpGeneric nOp = registry.get(op.name());
        if (nOp != null) return nOp;
        return new NFallbackOp(op);
    }
}