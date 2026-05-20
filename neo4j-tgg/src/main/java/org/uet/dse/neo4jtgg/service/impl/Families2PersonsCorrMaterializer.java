package org.uet.dse.neo4jtgg.service.impl;

import org.neo4j.driver.Session;
import org.tzi.use.uml.mm.MModel;
import org.uet.dse.neo4j.model.FullObjectSnapshot;
import org.uet.dse.neo4j.model.LinkState;
import org.uet.dse.neo4j.model.ObjectState;
import org.uet.dse.neo4j.repo.Neo4jObjectRepository;
import org.uet.dse.neo4j.sync.object.ObjectPushService;
import org.uet.dse.neo4jtgg.model.TggWorkspaceContext;
import org.uet.dse.neo4jtgg.model.TggWorkspaceDefinition;
import org.uet.dse.neo4jtgg.model.WorkspaceSide;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class Families2PersonsCorrMaterializer {
    private final Neo4jWorkspaceRuntimeService runtimeService = Neo4jWorkspaceRuntimeService.getInstance();
    private final Neo4jObjectRepository objectRepository = new Neo4jObjectRepository();

    public boolean supports(TggWorkspaceContext context) {
        return context.getWorkspaceDefinition() != null
                && "Families2Persons".equals(context.getWorkspaceDefinition().getTransformationName());
    }

    public int materialize(TggWorkspaceContext context) {
        if (!supports(context)) {
            return 0;
        }

        TggWorkspaceDefinition definition = context.getWorkspaceDefinition();
        FullObjectSnapshot source = runtimeService.loadSnapshot(context, WorkspaceSide.SOURCE);
        FullObjectSnapshot target = runtimeService.loadSnapshot(context, WorkspaceSide.TARGET);
        MModel corrModel = definition.getCorrespondenceModel();

        Map<String, CorrRecord> records = buildCorrespondenceRecords(source, target);
        try (Session session = org.uet.dse.neo4j.manager.Neo4jDriverManager.getInstance().openSession()) {
            session.executeWrite(tx -> {
                for (CorrRecord record : records.values()) {
                    objectRepository.upsertObjectNode(tx, record.objectId, record.corrClassName);
                    objectRepository.upsertBinaryLink(tx,
                            record.objectId,
                            record.sourceObjectId,
                            record.corrClassName + "_" + record.sourceClassName,
                            "LinkAssociateWith",
                            lowerFirst(record.corrClassName),
                            lowerFirst(record.sourceClassName));
                    objectRepository.upsertBinaryLink(tx,
                            record.objectId,
                            record.targetObjectId,
                            record.corrClassName + "_" + record.targetClassName,
                            "LinkAssociateWith",
                            lowerFirst(record.corrClassName),
                            lowerFirst(record.targetClassName));
                }
                return null;
            });
        }

        ObjectPushService.updateObjectVersionOnServer();
        context.appendLog(WorkspaceSide.CORRESPONDENCE,
                "Materialized " + records.size() + " correspondence object(s) on Neo4j for `" + corrModel.name() + "`.");
        return records.size();
    }

    private Map<String, CorrRecord> buildCorrespondenceRecords(FullObjectSnapshot source, FullObjectSnapshot target) {
        Map<String, CorrRecord> records = new LinkedHashMap<>();
        String familyRegisterId = firstObjectOfClass(source, "FamilyRegister");
        String personRegisterId = firstObjectOfClass(target, "PersonRegister");
        if (familyRegisterId != null && personRegisterId != null) {
            addRecord(records, "fr2pr", "FR2PR", familyRegisterId, "FamilyRegister", personRegisterId, "PersonRegister");
        }

        Map<String, ObjectState> families = filterByClass(source, "Family");
        Map<String, ObjectState> familyMembers = filterByClass(source, "FamilyMember");
        Map<String, ObjectState> maleTargets = filterByClass(target, "Male");
        Map<String, ObjectState> femaleTargets = filterByClass(target, "Female");

        Map<String, String> fathers = collectBinaryLinkTargets(source, "Father");
        Map<String, String> mothers = collectBinaryLinkTargets(source, "Mother");
        Map<String, List<String>> sons = collectMultiBinaryLinkTargets(source, "Sons");
        Map<String, List<String>> daughters = collectMultiBinaryLinkTargets(source, "Daughters");

        for (ObjectState male : maleTargets.values()) {
            CorrLookup lookup = resolvePersonLookup(male);
            if (lookup == null) {
                continue;
            }
            String familyId = findFamilyByName(families, lookup.familyName);
            if (familyId == null) {
                continue;
            }

            String fatherId = fathers.get(familyId);
            if (fatherId != null && matchesMemberName(familyMembers.get(fatherId), lookup.memberName)) {
                addRecord(records, "f2mp", "F2MP", fatherId, "FamilyMember", male.name, "Male");
                addRecord(records, "fm2mp", "FM2MP", familyId, "Family", male.name, "Male");
                continue;
            }

            for (String sonId : sons.getOrDefault(familyId, List.of())) {
                if (matchesMemberName(familyMembers.get(sonId), lookup.memberName)) {
                    addRecord(records, "s2mp", "S2MP", sonId, "FamilyMember", male.name, "Male");
                    addRecord(records, "fm2mp", "FM2MP", familyId, "Family", male.name, "Male");
                    break;
                }
            }
        }

        for (ObjectState female : femaleTargets.values()) {
            CorrLookup lookup = resolvePersonLookup(female);
            if (lookup == null) {
                continue;
            }
            String familyId = findFamilyByName(families, lookup.familyName);
            if (familyId == null) {
                continue;
            }

            String motherId = mothers.get(familyId);
            if (motherId != null && matchesMemberName(familyMembers.get(motherId), lookup.memberName)) {
                addRecord(records, "m2fp", "M2FP", motherId, "FamilyMember", female.name, "Female");
                addRecord(records, "fm2fp", "FM2FP", familyId, "Family", female.name, "Female");
                continue;
            }

            for (String daughterId : daughters.getOrDefault(familyId, List.of())) {
                if (matchesMemberName(familyMembers.get(daughterId), lookup.memberName)) {
                    addRecord(records, "d2fp", "D2FP", daughterId, "FamilyMember", female.name, "Female");
                    addRecord(records, "fm2fp", "FM2FP", familyId, "Family", female.name, "Female");
                    break;
                }
            }
        }

        return records;
    }

    private void addRecord(Map<String, CorrRecord> records,
                           String prefix,
                           String corrClassName,
                           String sourceObjectId,
                           String sourceClassName,
                           String targetObjectId,
                           String targetClassName) {
        String objectId = prefix + "_" + sourceObjectId + "_" + targetObjectId;
        records.put(objectId, new CorrRecord(
                objectId,
                corrClassName,
                sourceObjectId,
                sourceClassName,
                targetObjectId,
                targetClassName
        ));
    }

    private String firstObjectOfClass(FullObjectSnapshot snapshot, String className) {
        return snapshot.objects.values().stream()
                .filter(object -> className.equals(object.className))
                .map(object -> object.name)
                .findFirst()
                .orElse(null);
    }

    private Map<String, ObjectState> filterByClass(FullObjectSnapshot snapshot, String className) {
        return snapshot.objects.values().stream()
                .filter(object -> className.equals(object.className))
                .collect(Collectors.toMap(object -> object.name, object -> object, (left, right) -> left, LinkedHashMap::new));
    }

    private Map<String, String> collectBinaryLinkTargets(FullObjectSnapshot snapshot, String associationName) {
        Map<String, String> targets = new HashMap<>();
        for (LinkState link : snapshot.links.values()) {
            if (associationName.equals(link.assocName) && link.participants.size() >= 2) {
                targets.put(link.participants.get(0), link.participants.get(1));
            }
        }
        return targets;
    }

    private Map<String, List<String>> collectMultiBinaryLinkTargets(FullObjectSnapshot snapshot, String associationName) {
        Map<String, List<String>> targets = new HashMap<>();
        for (LinkState link : snapshot.links.values()) {
            if (associationName.equals(link.assocName) && link.participants.size() >= 2) {
                targets.computeIfAbsent(link.participants.get(0), ignored -> new ArrayList<>()).add(link.participants.get(1));
            }
        }
        return targets;
    }

    private CorrLookup resolvePersonLookup(ObjectState person) {
        Object rawName = person.primitiveValues.get("name");
        if (rawName == null) {
            return null;
        }
        String name = rawName.toString().trim();
        String[] commaParts = name.split(",\\s*");
        if (commaParts.length >= 2) {
            return new CorrLookup(commaParts[0].trim(), commaParts[1].trim());
        }

        String[] whitespaceParts = name.split("\\s+");
        if (whitespaceParts.length >= 2) {
            String familyName = whitespaceParts[whitespaceParts.length - 1].trim();
            String memberName = String.join(" ", java.util.Arrays.copyOf(whitespaceParts, whitespaceParts.length - 1)).trim();
            return new CorrLookup(familyName, memberName);
        }
        return null;
    }

    private String findFamilyByName(Map<String, ObjectState> families, String familyName) {
        return families.values().stream()
                .filter(family -> Objects.equals(String.valueOf(family.primitiveValues.get("name")), familyName))
                .map(family -> family.name)
                .findFirst()
                .orElse(null);
    }

    private boolean matchesMemberName(ObjectState member, String expectedName) {
        if (member == null) {
            return false;
        }
        return Objects.equals(String.valueOf(member.primitiveValues.get("name")), expectedName);
    }

    private String lowerFirst(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return Character.toLowerCase(value.charAt(0)) + value.substring(1);
    }

    private record CorrLookup(String familyName, String memberName) {
    }

    private record CorrRecord(String objectId,
                              String corrClassName,
                              String sourceObjectId,
                              String sourceClassName,
                              String targetObjectId,
                              String targetClassName) {
    }
}
