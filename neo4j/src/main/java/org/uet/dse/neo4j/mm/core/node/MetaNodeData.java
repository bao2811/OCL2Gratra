package org.uet.dse.neo4j.mm.core.node;

public enum MetaNodeData {

    NODE_ATTRIBUTE("NodeAttribute", "Attribute"),
    NODE_OPERATION("NodeOperation", "Operation"),
    NODE_PARAM("NodeParam", "Param"),
    NODE_ASSOCIATION_CLASS("NodeAssociationClass", "AssociationClass"),
    NODE_ABSTRACT_CLASS("NodeAbstractClass", "AbstractClass"),
    NODE_CONCRETE_CLASS("NodeConcreteClass", "ConcreteClass"),
    NODE_ENUMERATION("NodeEnumeration", "Enumeration"),
    NODE_CLASS_INVARIANT("NodeClassInvariant", "ClassInvariant"),
    NODE_PRE_CONDITION("NodePreCondition", "PreCondition"),
    NODE_POST_CONDITION("NodePostCondition", "PostCondition"),
    NODE_TERNARY_ASSOCIATION("NodeTernaryAssociation", "TernaryAssociation"),
    NODE_NESTED_COLLECTION("NodeNestedCollection", "NestedCollection");

    private final String name;
    private final String label;

    MetaNodeData(String name, String label) {
        this.name = name;
        this.label = label;
    }

    public String getName() {
        return name;
    }

    public String getLabel() {
        return label;
    }
}
