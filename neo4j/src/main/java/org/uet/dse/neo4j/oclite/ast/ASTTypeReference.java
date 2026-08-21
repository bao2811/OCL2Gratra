package org.uet.dse.neo4j.oclite.ast;

/** Unresolved type syntax, for example Person or Set(Person). */
public final class ASTTypeReference extends ASTNode {
    public final String spelling;

    public ASTTypeReference(String spelling) {
        this.spelling = spelling;
    }
}
