package org.uet.dse.neo4j.oclite.ast;

public class ASTEnumLiteral extends ASTLiteral {
    public final String enumTypeName;
    public final String literalName;

    public ASTEnumLiteral(String enumTypeName, String literalName) {
        this.enumTypeName = enumTypeName;
        this.literalName = literalName;
    }
}
