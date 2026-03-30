package org.uet.dse.neo4j.mm.core.common;

public enum NType {
    Boolean, Object, String, Void, Int, Float, Double, None;

    public static NType fromString(String typeStr) {
        try {
            return NType.valueOf(typeStr);
        } catch (Exception e) {
            return NType.Object; // Mặc định là Object nếu là tên Class/Enum người dùng định nghĩa
        }
    }
}
