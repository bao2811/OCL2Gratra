package org.uet.dse.neo4jtgg.ocl;

import org.tzi.use.api.UseApiException;
import org.tzi.use.api.UseModelApi;
import org.tzi.use.uml.mm.MAssociation;
import org.tzi.use.uml.mm.MAssociationEnd;
import org.tzi.use.uml.mm.MAttribute;
import org.tzi.use.uml.mm.MClass;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Immutable M2 view shared by the USE-backed and graph-backed binders. */
public record OclMetamodelSnapshot(String modelName,
                                   List<ClassDef> classes,
                                   List<AttributeDef> attributes,
                                   List<GeneralizationDef> generalizations,
                                   List<AssociationDef> associations) {
    public OclMetamodelSnapshot {
        modelName = required(modelName, "modelName");
        classes = sorted(classes, Comparator.comparing(ClassDef::name));
        attributes = sorted(attributes, Comparator.comparing(AttributeDef::owner).thenComparing(AttributeDef::name));
        generalizations = sorted(generalizations,
                Comparator.comparing(GeneralizationDef::child).thenComparing(GeneralizationDef::parent));
        associations = sorted(associations, Comparator.comparing(AssociationDef::name));
    }

    public static OclMetamodelSnapshot fromUse(MModel model) {
        List<ClassDef> classes = model.classes().stream()
                .map(cls -> new ClassDef(cls.name(), cls.isAbstract())).toList();
        List<AttributeDef> attributes = new ArrayList<>();
        List<GeneralizationDef> generalizations = new ArrayList<>();
        for (MClass cls : model.classes()) {
            for (MAttribute attribute : cls.attributes()) {
                attributes.add(new AttributeDef(cls.name(), attribute.name(), attribute.type().toString()));
            }
            for (MClass parent : cls.parents()) {
                generalizations.add(new GeneralizationDef(cls.name(), parent.name()));
            }
        }
        List<AssociationDef> associations = model.associations().stream()
                .filter(association -> association.associationEnds().size() == 2)
                .map(OclMetamodelSnapshot::association).toList();
        return new OclMetamodelSnapshot(model.name(), classes, attributes, generalizations, associations);
    }

    public MModel toUseModel() {
        MModel model = new ModelFactory().createModel(modelName);
        UseModelApi api = new UseModelApi(model);
        try {
            for (ClassDef cls : classes) api.createClass(cls.name(), cls.isAbstract());
            for (AttributeDef attribute : attributes) {
                api.createAttribute(attribute.owner(), attribute.name(), attribute.type());
            }
            for (GeneralizationDef generalization : generalizations) {
                api.createGeneralization(generalization.child(), generalization.parent());
            }
            for (AssociationDef association : associations) {
                EndDef left = association.left();
                EndDef right = association.right();
                api.createAssociation(association.name(),
                        new String[]{left.className(), right.className()},
                        new String[]{left.role(), right.role()},
                        new String[]{left.multiplicity(), right.multiplicity()},
                        new int[]{left.aggregationKind(), right.aggregationKind()},
                        new boolean[]{left.ordered(), right.ordered()},
                        new String[][][]{qualifiers(left.qualifiers()), qualifiers(right.qualifiers())});
            }
        } catch (UseApiException ex) {
            throw new IllegalArgumentException("Invalid graph-backed M2 snapshot: " + ex.getMessage(), ex);
        }
        return model;
    }

    private static AssociationDef association(MAssociation association) {
        return new AssociationDef(association.name(), end(association.associationEnds().get(0)),
                end(association.associationEnds().get(1)));
    }

    private static EndDef end(MAssociationEnd end) {
        List<QualifierDef> qualifiers = end.getQualifiers().stream()
                .map(value -> new QualifierDef(value.name(), value.type().toString())).toList();
        return new EndDef(end.cls().name(), end.nameAsRolename(), end.multiplicity().toString(),
                end.aggregationKind(), end.isOrdered(), qualifiers);
    }

    private static String[][] qualifiers(List<QualifierDef> qualifiers) {
        return qualifiers.stream().map(value -> new String[]{value.name(), value.type()}).toArray(String[][]::new);
    }

    private static <T> List<T> sorted(List<T> source, Comparator<? super T> comparator) {
        Objects.requireNonNull(source);
        return source.stream().sorted(comparator).toList();
    }

    private static String required(String value, String label) {
        String normalized = Objects.requireNonNull(value, label).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(label + " must not be blank");
        return normalized;
    }

    public record ClassDef(String name, boolean isAbstract) {
        public ClassDef { name = required(name, "class name"); }
    }

    public record AttributeDef(String owner, String name, String type) {
        public AttributeDef {
            owner = required(owner, "attribute owner");
            name = required(name, "attribute name");
            type = required(type, "attribute type");
        }
    }

    public record GeneralizationDef(String child, String parent) {
        public GeneralizationDef {
            child = required(child, "child");
            parent = required(parent, "parent");
        }
    }

    public record AssociationDef(String name, EndDef left, EndDef right) {
        public AssociationDef {
            name = required(name, "association name");
            Objects.requireNonNull(left);
            Objects.requireNonNull(right);
        }
    }

    public record EndDef(String className, String role, String multiplicity, int aggregationKind,
                         boolean ordered, List<QualifierDef> qualifiers) {
        public EndDef {
            className = required(className, "end class");
            role = required(role, "end role");
            multiplicity = required(multiplicity, "end multiplicity");
            qualifiers = List.copyOf(qualifiers);
        }
    }

    public record QualifierDef(String name, String type) {
        public QualifierDef {
            name = required(name, "qualifier name");
            type = required(type, "qualifier type");
        }
    }
}
