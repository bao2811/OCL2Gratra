package org.uet.dse.neo4j.oclite.ast;

/** Unresolved variable declaration retained by the surface AST. */
public record ASTVariableDeclaration(String name, String typeName) {
}
