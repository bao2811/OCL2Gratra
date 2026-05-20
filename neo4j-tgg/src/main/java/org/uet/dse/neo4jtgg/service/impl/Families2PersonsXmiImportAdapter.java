package org.uet.dse.neo4jtgg.service.impl;

import org.uet.dse.neo4jtgg.model.ImportBatch;
import org.uet.dse.neo4jtgg.model.ImportLinkSpec;
import org.uet.dse.neo4jtgg.model.ImportObjectSpec;
import org.uet.dse.neo4jtgg.model.TggWorkspaceContext;
import org.uet.dse.neo4jtgg.model.WorkspaceSide;
import org.uet.dse.neo4jtgg.service.XmiImportAdapter;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Families2PersonsXmiImportAdapter implements XmiImportAdapter {
    @Override
    public boolean supports(WorkspaceSide side) {
        return side == WorkspaceSide.SOURCE || side == WorkspaceSide.TARGET;
    }

    @Override
    public ImportBatch parse(TggWorkspaceContext context, WorkspaceSide side, File file) throws Exception {
        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file);
        document.getDocumentElement().normalize();

        ImportBatch batch = new ImportBatch(side, file);
        if (side == WorkspaceSide.SOURCE) {
            parseFamilies(document.getDocumentElement(), batch, new HashMap<>());
        } else {
            parsePersons(document.getDocumentElement(), batch, new HashMap<>());
        }
        return batch;
    }

    private void parseFamilies(Element root, ImportBatch batch, Map<String, Integer> ids) {
        String registerId = createStableId(root, "familyRegister", ids);
        if (looksLike(root, "FamilyRegister") || hasChildElement(root, "families")) {
            batch.addObject(new ImportObjectSpec(registerId, "FamilyRegister"));
        }

        NodeList familyNodes = root.getChildNodes();
        for (int i = 0; i < familyNodes.getLength(); i++) {
            if (familyNodes.item(i) instanceof Element familyElement && looksLikeCollectionMember(familyElement, "families", "Family")) {
                String familyId = createStableId(familyElement, "family", ids);
                ImportObjectSpec family = new ImportObjectSpec(familyId, "Family");
                putAttributeIfPresent(family, familyElement, "name");
                batch.addObject(family);
                batch.addLink(new ImportLinkSpec("FamilyRegistration", List.of(registerId, familyId)));
                parseFamilyMembers(batch, ids, familyElement, familyId, "Father", "father");
                parseFamilyMembers(batch, ids, familyElement, familyId, "Mother", "mother");
                parseFamilyMembers(batch, ids, familyElement, familyId, "Sons", "sons");
                parseFamilyMembers(batch, ids, familyElement, familyId, "Daughters", "daughters");
            }
        }
    }

    private void parseFamilyMembers(ImportBatch batch, Map<String, Integer> ids, Element familyElement, String familyId,
                                    String associationName, String elementName) {
        NodeList children = familyElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element memberElement && looksLikeCollectionMember(memberElement, elementName, "FamilyMember")) {
                String memberId = createStableId(memberElement, "familyMember", ids);
                ImportObjectSpec member = new ImportObjectSpec(memberId, "FamilyMember");
                putAttributeIfPresent(member, memberElement, "name");
                batch.addObject(member);
                batch.addLink(new ImportLinkSpec(associationName, List.of(familyId, memberId)));
            }
        }
    }

    private void parsePersons(Element root, ImportBatch batch, Map<String, Integer> ids) {
        String registerId = createStableId(root, "personRegister", ids);
        if (looksLike(root, "PersonRegister") || hasChildElement(root, "persons")) {
            batch.addObject(new ImportObjectSpec(registerId, "PersonRegister"));
        }

        NodeList personNodes = root.getChildNodes();
        for (int i = 0; i < personNodes.getLength(); i++) {
            if (personNodes.item(i) instanceof Element personElement && looksLikeCollectionMember(personElement, "persons", null)) {
                String className = inferPersonClass(personElement);
                if (className == null) {
                    continue;
                }

                String personId = createStableId(personElement, className.toLowerCase(), ids);
                ImportObjectSpec person = new ImportObjectSpec(personId, className);
                putAttributeIfPresent(person, personElement, "name");
                putAttributeIfPresent(person, personElement, "birthday");
                batch.addObject(person);
                batch.addLink(new ImportLinkSpec("PersonRegistration", List.of(registerId, personId)));
            }
        }
    }

    private String inferPersonClass(Element personElement) {
        String localName = safeLocalName(personElement);
        if ("Male".equals(localName) || "Female".equals(localName)) {
            return localName;
        }
        String type = firstNonBlank(personElement.getAttribute("xmi:type"), personElement.getAttribute("type"), personElement.getAttribute("class"));
        if (type.endsWith("Male")) {
            return "Male";
        }
        if (type.endsWith("Female")) {
            return "Female";
        }
        return null;
    }

    private void putAttributeIfPresent(ImportObjectSpec spec, Element element, String attributeName) {
        String value = firstNonBlank(element.getAttribute(attributeName), textOfFirstChild(element, attributeName));
        if (value != null && !value.isBlank()) {
            spec.getAttributes().put(attributeName, "'" + value.replace("'", "") + "'");
        }
    }

    private boolean hasChildElement(Element root, String name) {
        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element child && name.equals(safeLocalName(child))) {
                return true;
            }
        }
        return false;
    }

    private String textOfFirstChild(Element root, String name) {
        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element child && name.equals(safeLocalName(child))) {
                return child.getTextContent();
            }
        }
        return null;
    }

    private boolean looksLike(Element element, String className) {
        String local = safeLocalName(element);
        if (className.equals(local)) {
            return true;
        }
        String type = firstNonBlank(element.getAttribute("xmi:type"), element.getAttribute("type"), element.getAttribute("class"));
        return type != null && type.endsWith(className);
    }

    private boolean looksLikeCollectionMember(Element element, String containmentName, String className) {
        String local = safeLocalName(element);
        if (containmentName.equals(local)) {
            return true;
        }
        return className != null && looksLike(element, className);
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
            return explicit.replaceAll("[^A-Za-z0-9_]", "_");
        }
        int next = ids.compute(prefix, (k, v) -> v == null ? 1 : v + 1);
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
}
