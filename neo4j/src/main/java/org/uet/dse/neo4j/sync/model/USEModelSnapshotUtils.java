package org.uet.dse.neo4j.sync.model;

import org.tzi.use.uml.mm.*;
import org.tzi.use.uml.ocl.expr.VarDecl;
import org.uet.dse.neo4j.model.AssociationState;
import org.uet.dse.neo4j.model.ClassState;
import org.uet.dse.neo4j.model.FullModelSnapshot;
import org.uet.dse.neo4j.sync.helper.UmlTypeTranslator;

import java.util.HashMap;
import java.util.Map;

public class USEModelSnapshotUtils {
    private MModel useModel;

    public USEModelSnapshotUtils(MModel useModel) {
        this.useModel = useModel;
    }

    public FullModelSnapshot getFullModelSnapshotFromUSE() {
        FullModelSnapshot fullSnapshot = new FullModelSnapshot();
        Map<String, ClassState> stateMap = new HashMap<>();

        for (MClass mCls : useModel.classes()) {
            ClassState cls = buildClassState(mCls);
            stateMap.put(cls.getName(), cls);
        }

        for (org.tzi.use.uml.ocl.type.EnumType e : useModel.enumTypes()) {
            ClassState enumCls = buildEnumClassState(e);
            fullSnapshot.classes.put(enumCls.getName(), enumCls);
        }

        for (MAssociation mAssoc : useModel.associations()) {
            if (mAssoc instanceof MAssociationClass) continue;
            AssociationState as = buildAssociationState(mAssoc);
            fullSnapshot.associations.put(as.name, as);
        }

        return fullSnapshot;
    }


    private ClassState buildClassState(MClass mCls) {
        ClassState cls = new ClassState();
        cls.setName(mCls.name());
        cls.setMetaNodeName(resolveClassMetaName(mCls));

        for (MAttribute attr : mCls.attributes()) {
            cls.getAttributes().put(attr.name(), buildAttributeProps(attr));
        }

        for (MOperation op : mCls.operations()) {
            populateOperation(cls, op);
        }

        for (MClassInvariant inv : useModel.classInvariants(mCls)) {
            cls.addInvariant(buildInvariantMap(inv));
        }

        mCls.parents().forEach(p -> cls.getParents().add(p.name()));

        if (mCls instanceof MAssociationClass) {
            populateAssociationClassEnds(cls, (MAssociationClass) mCls);
        }

        return cls;
    }

    private String resolveClassMetaName(MClass mCls) {
        if (mCls instanceof MAssociationClass) return "NodeAssociationClass";
        if (mCls.isAbstract()) return "NodeAbstractClass";
        return "NodeConcreteClass";
    }

    private Map<String, Object> buildAttributeProps(MAttribute attr) {
        org.tzi.use.uml.ocl.type.Type type = attr.type();
        boolean isColl = type.isKindOfCollection(org.tzi.use.uml.ocl.type.Type.VoidHandling.EXCLUDE_VOID);

        Map<String, Object> props = new HashMap<>();
        props.put("attrName", attr.name());
        props.put("type", UmlTypeTranslator.toDatabaseType(type));
        props.put("index", attr.getPositionInModel());
        props.put("isCollection", isColl);
        props.put("collectionType", isColl ? resolveCollectionType(type) : "None");
        return props;
    }

    private String resolveCollectionType(org.tzi.use.uml.ocl.type.Type type) {
        if (type.isKindOfSet(org.tzi.use.uml.ocl.type.Type.VoidHandling.EXCLUDE_VOID)) return "Set";
        if (type.isKindOfOrderedSet(org.tzi.use.uml.ocl.type.Type.VoidHandling.EXCLUDE_VOID)) return "OrderedSet";
        if (type.isKindOfSequence(org.tzi.use.uml.ocl.type.Type.VoidHandling.EXCLUDE_VOID)) return "Sequence";
        if (type.isKindOfBag(org.tzi.use.uml.ocl.type.Type.VoidHandling.EXCLUDE_VOID)) return "Bag";
        return "Set"; // default
    }


    private void populateOperation(ClassState cls, MOperation op) {
        cls.getOperations().put(op.name(), buildOperationProps(op));

        int pIdx = 0;
        for (VarDecl p : op.paramList()) {
            cls.addOperationParam(op.name(), p.name(), UmlTypeTranslator.toDatabaseType(p.type()), pIdx++);
        }

        for (MPrePostCondition pre : op.preConditions()) {
            cls.addPreCondition(op.name(), buildConditionMap(pre));
        }
        for (MPrePostCondition post : op.postConditions()) {
            cls.addPostCondition(op.name(), buildConditionMap(post));
        }
    }

    private Map<String, Object> buildOperationProps(MOperation op) {
        boolean retColl = op.resultType() != null
                && op.resultType().isKindOfCollection(org.tzi.use.uml.ocl.type.Type.VoidHandling.EXCLUDE_VOID);

        Map<String, Object> props = new HashMap<>();
        props.put("returnType", UmlTypeTranslator.toDatabaseType(op.resultType()));
        props.put("isReturnCollection", retColl);
        return props;
    }

    private Map<String, Object> buildConditionMap(MPrePostCondition condition) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", condition.name());
        map.put("expr", condition.expression().toString());
        return map;
    }

    private Map<String, Object> buildInvariantMap(MClassInvariant inv) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", inv.name());
        map.put("expr", inv.bodyExpression().toString());
        map.put("exist", inv.isExistential());
        return map;
    }


    private void populateAssociationClassEnds(ClassState cls, MAssociationClass ac) {
        MAssociationEnd src = ac.associationEnds().get(0);
        MAssociationEnd tgt = ac.associationEnds().get(1);
        cls.setAcSource(src.cls().name(), src.name(), src.multiplicity().toString());
        cls.setAcTarget(tgt.cls().name(), tgt.name(), tgt.multiplicity().toString());
    }


    private ClassState buildEnumClassState(org.tzi.use.uml.ocl.type.EnumType e) {
        ClassState enumCls = new ClassState();
        enumCls.setName(e.name());
        enumCls.setMetaNodeName("NodeEnumeration");
        enumCls.setEnumLiterals(e.getLiterals());
        return enumCls;
    }


    private AssociationState buildAssociationState(MAssociation mAssoc) {
        MAssociationEnd src = mAssoc.associationEnds().get(0);
        MAssociationEnd tgt = mAssoc.associationEnds().get(1);

        AssociationState as = new AssociationState();
        as.name = mAssoc.name();
        as.srcName = src.cls().name();
        as.srcRole = src.name();
        as.srcMult = src.multiplicity().toString();
        as.tgtName = tgt.cls().name();
        as.tgtRole = tgt.name();
        as.tgtMult = tgt.multiplicity().toString();
        as.type = resolveAssociationType(tgt.aggregationKind());
        return as;
    }

    private String resolveAssociationType(int aggregationKind) {
        if (aggregationKind == 2) return "ComposeOf";
        if (aggregationKind == 1) return "Aggregates";
        return "AssociateWith";
    }
}
