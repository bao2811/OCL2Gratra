package org.uet.dse.neo4jtgg.service.impl;

import org.tzi.use.uml.mm.MAssociation;
import org.tzi.use.uml.mm.MAssociationEnd;
import org.tzi.use.uml.mm.MAttribute;
import org.tzi.use.uml.mm.MClass;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.ocl.type.Type;
import org.uet.dse.neo4jtgg.model.ImportBatch;
import org.uet.dse.neo4jtgg.model.ImportLinkSpec;
import org.uet.dse.neo4jtgg.model.ImportObjectSpec;
import org.uet.dse.neo4jtgg.model.TggWorkspaceContext;
import org.uet.dse.neo4jtgg.model.TggWorkspaceDefinition;
import org.uet.dse.neo4jtgg.model.WorkspaceSide;
import org.uet.dse.neo4jtgg.service.XmiImportAdapter;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class GenericXmiImportAdapter implements XmiImportAdapter {

    @Override
    public boolean supports(WorkspaceSide side) {
        return side == WorkspaceSide.SOURCE || side == WorkspaceSide.TARGET;
    }

    @Override
    public ImportBatch parse(TggWorkspaceContext context, WorkspaceSide side, File file) throws Exception {
        MModel model = requireModel(context, side);
        return parse(model, side, file);
    }

    /** Parses XMI/XML for the single-model research workflow. */
    public ImportBatch parse(MModel model, File file) throws Exception {
        return parse(model, WorkspaceSide.SOURCE, file);
    }

    private ImportBatch parse(MModel model, WorkspaceSide side, File file) throws Exception {
        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file);
        document.getDocumentElement().normalize();

        ModelIntrospection introspection = new ModelIntrospection(model);
        ImportBatch batch = new ImportBatch(side, file);
        ParseState state = new ParseState();

        Element root = document.getDocumentElement();
        MClass rootClass = resolveRootClass(root, introspection);
        if (rootClass == null) {
            throw new IllegalStateException("Cannot infer root class from XML `" + file.getName()
                    + "` for model `" + model.name() + "`.");
        }

        parseObject(root, rootClass, batch, introspection, state);
        return batch;
    }

    private MModel requireModel(TggWorkspaceContext context, WorkspaceSide side) {
        TggWorkspaceDefinition definition = context.getWorkspaceDefinition();
        if (definition == null || definition.getModel(side) == null) {
            throw new IllegalStateException("Workspace metadata for " + side.getDisplayName()
                    + " is not loaded. Run Workspace before importing XMI/XML.");
        }
        return definition.getModel(side);
    }

    private MClass resolveRootClass(Element root, ModelIntrospection introspection) {
        MClass direct = introspection.findClassByElement(root);
        if (direct != null) {
            return direct;
        }

        Set<MClass> candidates = new LinkedHashSet<>();
        List<String> childTags = childElements(root).stream()
                .map(this::safeLocalName)
                .toList();
        for (MClass cls : introspection.model().classes()) {
            if (cls.associations().isEmpty()) {
                continue;
            }
            boolean matches = cls.associations().stream()
                    .flatMap(association -> association.associationEnds().stream())
                    .filter(end -> sameOrSubtype(cls, end.cls()))
                    .flatMap(end -> otherEnds(end).stream())
                    .map(MAssociationEnd::name)
                    .anyMatch(role -> childTags.stream().anyMatch(tag -> normalizedEquals(tag, role)));
            if (matches) {
                candidates.add(cls);
            }
        }
        return candidates.size() == 1 ? candidates.iterator().next() : null;
    }

    private String parseObject(Element element,
                               MClass objectClass,
                               ImportBatch batch,
                               ModelIntrospection introspection,
                               ParseState state) {
        String objectId = createStableId(element, objectClass.nameAsRolename(), state.generatedIds());
        if (state.seenObjects().add(objectId)) {
            ImportObjectSpec spec = new ImportObjectSpec(objectId, objectClass.name());
            populateAttributes(spec, objectClass, element, introspection);
            batch.addObject(spec);
        }

        for (Element child : childElements(element)) {
            AssociationMatch match = introspection.resolveAssociation(objectClass, child);
            if (match == null) {
                continue;
            }
            String childId = parseObject(child, match.childClass(), batch, introspection, state);
            addLink(batch, match.association(), objectId, objectClass, childId, match.childClass(), state.seenLinks());
        }
        return objectId;
    }

    private void populateAttributes(ImportObjectSpec spec,
                                    MClass objectClass,
                                    Element element,
                                    ModelIntrospection introspection) {
        for (MAttribute attribute : objectClass.allAttributes()) {
            String value = firstNonBlank(
                    element.getAttribute(attribute.name()),
                    textOfFirstSimpleChild(element, attribute.name(), introspection));
            if (value == null || value.isBlank()) {
                continue;
            }
            spec.getAttributes().put(attribute.name(), serializeAttributeValue(attribute.type(), value));
        }
    }

    private String serializeAttributeValue(Type type, String rawValue) {
        String trimmed = rawValue.trim();
        if (type.isTypeOfInteger() || type.isTypeOfReal() || type.isTypeOfBoolean()) {
            return trimmed;
        }
        return "'" + trimmed.replace("'", "") + "'";
    }

    private void addLink(ImportBatch batch,
                         MAssociation association,
                         String parentId,
                         MClass parentClass,
                         String childId,
                         MClass childClass,
                         Set<String> seenLinks) {
        if (association.associationEnds().size() != 2) {
            return;
        }
        MAssociationEnd left = association.associationEnds().get(0);
        MAssociationEnd right = association.associationEnds().get(1);
        List<String> endpoints;
        if (sameOrSubtype(parentClass, left.cls()) && sameOrSubtype(childClass, right.cls())) {
            endpoints = List.of(parentId, childId);
        } else if (sameOrSubtype(parentClass, right.cls()) && sameOrSubtype(childClass, left.cls())) {
            endpoints = List.of(childId, parentId);
        } else {
            return;
        }

        String identity = association.name() + endpoints.stream().map(endpoint -> "_" + endpoint).reduce("", String::concat);
        if (seenLinks.add(identity)) {
            batch.addLink(new ImportLinkSpec(association.name(), endpoints));
        }
    }

    private String textOfFirstSimpleChild(Element root, String name, ModelIntrospection introspection) {
        for (Element child : childElements(root)) {
            if (!normalizedEquals(safeLocalName(child), name)) {
                continue;
            }
            if (introspection.findClassByElement(child) != null) {
                continue;
            }
            if (!childElements(child).isEmpty()) {
                continue;
            }
            return child.getTextContent();
        }
        return null;
    }

    private List<Element> childElements(Element element) {
        NodeList children = element.getChildNodes();
        List<Element> result = new ArrayList<>();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element childElement) {
                result.add(childElement);
            }
        }
        return result;
    }

    private List<MAssociationEnd> otherEnds(MAssociationEnd associationEnd) {
        return associationEnd.getAllOtherAssociationEnds();
    }

    private boolean sameOrSubtype(MClass objectClass, MClass endClass) {
        return objectClass.equals(endClass) || objectClass.allParents().contains(endClass);
    }

    private String safeLocalName(Element element) {
        return element.getLocalName() != null ? element.getLocalName() : element.getTagName().replaceFirst("^.*:", "");
    }

    private String createStableId(Element element, String prefix, Map<String, Integer> ids) {
        String explicit = firstNonBlank(
                element.getAttribute("xmi:id"),
                element.getAttribute("id"),
                element.getAttribute("name"));
        if (explicit != null && !explicit.isBlank()) {
            return sanitizeIdentifier(explicit);
        }
        int next = ids.compute(prefix, (key, value) -> value == null ? 1 : value + 1);
        return prefix + next;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private boolean normalizedEquals(String left, String right) {
        return normalizeName(left).equals(normalizeName(right));
    }

    private String normalizeName(String value) {
        return value == null ? "" : value.replaceFirst("^.*:", "").toLowerCase(Locale.ROOT);
    }

    private String sanitizeIdentifier(String value) {
        return value.replaceAll("[^A-Za-z0-9_]", "_");
    }

    private record ParseState(Map<String, Integer> generatedIds,
                              Set<String> seenObjects,
                              Set<String> seenLinks) {
        private ParseState() {
            this(new HashMap<>(), new LinkedHashSet<>(), new LinkedHashSet<>());
        }
    }

    private record AssociationMatch(MAssociation association, MClass childClass) {
    }

    private final class ModelIntrospection {
        private final MModel model;
        private final Map<String, MClass> classesByName = new LinkedHashMap<>();

        private ModelIntrospection(MModel model) {
            this.model = model;
            for (MClass cls : model.classes()) {
                classesByName.put(normalizeName(cls.name()), cls);
            }
        }

        private MModel model() {
            return model;
        }

        private MClass findClassByElement(Element element) {
            String explicitType = firstNonBlank(
                    element.getAttribute("xmi:type"),
                    element.getAttribute("type"),
                    element.getAttribute("class"));
            if (explicitType != null) {
                String simpleType = explicitType.contains(":")
                        ? explicitType.substring(explicitType.lastIndexOf(':') + 1)
                        : explicitType;
                MClass typed = classesByName.get(normalizeName(simpleType));
                if (typed != null) {
                    return typed;
                }
            }
            return classesByName.get(normalizeName(safeLocalName(element)));
        }

        private AssociationMatch resolveAssociation(MClass parentClass, Element childElement) {
            String childTag = safeLocalName(childElement);
            MClass explicitChildClass = findClassByElement(childElement);
            AssociationMatch fallback = null;

            for (MAssociation association : parentClass.allAssociations()) {
                if (association.associationEnds().size() != 2) {
                    continue;
                }
                for (MAssociationEnd parentEnd : association.associationEnds()) {
                    if (!sameOrSubtype(parentClass, parentEnd.cls())) {
                        continue;
                    }
                    for (MAssociationEnd childEnd : otherEnds(parentEnd)) {
                        if (explicitChildClass != null && !sameOrSubtype(explicitChildClass, childEnd.cls())) {
                            continue;
                        }
                        if (normalizedEquals(childTag, childEnd.name())) {
                            return new AssociationMatch(association, explicitChildClass != null ? explicitChildClass : childEnd.cls());
                        }
                        if (explicitChildClass != null && sameOrSubtype(explicitChildClass, childEnd.cls())) {
                            fallback = new AssociationMatch(association, explicitChildClass);
                        } else if (normalizedEquals(childTag, childEnd.cls().name())) {
                            fallback = new AssociationMatch(association, childEnd.cls());
                        }
                    }
                }
            }
            return fallback;
        }
    }
}
