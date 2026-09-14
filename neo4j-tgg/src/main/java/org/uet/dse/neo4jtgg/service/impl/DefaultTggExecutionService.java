package org.uet.dse.neo4jtgg.service.impl;

import org.uet.dse.neo4j.model.FullObjectSnapshot;
import org.uet.dse.neo4j.model.LinkState;
import org.uet.dse.neo4j.model.ObjectState;
import org.uet.dse.neo4jtgg.engine.BackwardRuleApplicationEngine;
import org.uet.dse.neo4jtgg.engine.CorrRuntimeTraceHelper;
import org.uet.dse.neo4jtgg.engine.ForwardTransformationResult;
import org.uet.dse.neo4jtgg.engine.ForwardRuleApplicationEngine;
import org.uet.dse.neo4jtgg.engine.TransformationDirection;
import org.uet.dse.neo4jtgg.engine.TransformationMode;
import org.uet.dse.neo4jtgg.engine.TransformationOptions;
import org.uet.dse.neo4jtgg.engine.TransformationReport;
import org.uet.dse.neo4jtgg.model.ImportBatch;
import org.uet.dse.neo4jtgg.model.ImportLinkSpec;
import org.uet.dse.neo4jtgg.model.ImportObjectSpec;
import org.uet.dse.neo4jtgg.model.TggRuleInfo;
import org.uet.dse.neo4jtgg.model.TggWorkspaceContext;
import org.uet.dse.neo4jtgg.model.TggWorkspaceDefinition;
import org.uet.dse.neo4jtgg.model.WorkspaceSide;
import org.uet.dse.neo4jtgg.service.TggExecutionService;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class DefaultTggExecutionService implements TggExecutionService {
    private final Neo4jWorkspaceRuntimeService runtimeService = Neo4jWorkspaceRuntimeService.getInstance();
    private final RuleDrivenCorrMaterializer corrMaterializer = new RuleDrivenCorrMaterializer();
    private boolean useRuleDrivenEngine = true;

    @Override
    public TransformationReport preview(TggWorkspaceContext context, TransformationOptions options) {
        return execute(context, options);
    }

    @Override
    public TransformationReport run(TggWorkspaceContext context, TransformationOptions options) {
        return execute(context, options);
    }

    private TransformationReport execute(TggWorkspaceContext context, TransformationOptions options) {
        TggWorkspaceDefinition definition = context.getWorkspaceDefinition();
        if (definition == null) {
            throw new IllegalStateException("Workspace metadata is not loaded yet.");
        }

        WorkspaceSide driverSide = options.direction() == TransformationDirection.FORWARD
                ? WorkspaceSide.SOURCE
                : WorkspaceSide.TARGET;
        WorkspaceSide receiverSide = options.direction() == TransformationDirection.FORWARD
                ? WorkspaceSide.TARGET
                : WorkspaceSide.SOURCE;

        FullObjectSnapshot driverSnapshot = runtimeService.loadSnapshot(context, driverSide);
        FullObjectSnapshot receiverSnapshot = runtimeService.loadSnapshot(context, receiverSide);
        FullObjectSnapshot corrSnapshot = runtimeService.loadSnapshot(context, WorkspaceSide.CORRESPONDENCE);

        TransformationReport report = new TransformationReport(options.direction(), options.mode());
        report.addInfo("Driver side: " + driverSide.getDisplayName() + " objects=" + driverSnapshot.objects.size()
                + ", links=" + driverSnapshot.links.size());
        report.addInfo("Receiver side: " + receiverSide.getDisplayName() + " objects=" + receiverSnapshot.objects.size()
                + ", links=" + receiverSnapshot.links.size());
        report.addInfo("Correspondence side: objects=" + corrSnapshot.objects.size()
                + ", links=" + corrSnapshot.links.size());

        for (TggRuleInfo rule : definition.getRules()) {
            report.addMatchedRule(rule.getName());
        }

        // Try rule-driven engine first
        if (useRuleDrivenEngine && !definition.getRules().isEmpty()) {
            return executeRuleDriven(context, options, driverSnapshot, receiverSnapshot, corrSnapshot, report);
        }

        // Fallback to hardcoded path
        if ("Families2Persons".equals(definition.getTransformationName())) {
            return executeFamilies2Persons(context, options, driverSnapshot, receiverSnapshot, corrSnapshot, report);
        }

        report.addWarning("No transformation engine is implemented yet for `" + definition.getTransformationName() + "`.");
        return report;
    }

    private TransformationReport executeRuleDriven(TggWorkspaceContext context,
                                                    TransformationOptions options,
                                                    FullObjectSnapshot sourceSnapshot,
                                                    FullObjectSnapshot targetSnapshot,
                                                    FullObjectSnapshot corrSnapshot,
                                                    TransformationReport report) {
        long startMs = System.currentTimeMillis();
        ImportBatch batch;
        ForwardTransformationResult forwardResult = null;

        if (options.direction() == TransformationDirection.FORWARD) {
            ForwardRuleApplicationEngine engine = new ForwardRuleApplicationEngine(report);
            forwardResult = engine.executeForward(context, sourceSnapshot, targetSnapshot, corrSnapshot);
            batch = forwardResult.targetBatch();
            report.addInfo("Rule-driven forward engine completed in "
                    + (System.currentTimeMillis() - startMs) + "ms.");
        } else {
            BackwardRuleApplicationEngine engine = new BackwardRuleApplicationEngine(report);
            batch = engine.applyBackward(context, sourceSnapshot, targetSnapshot, corrSnapshot);
            report.addInfo("Rule-driven backward engine completed in "
                    + (System.currentTimeMillis() - startMs) + "ms.");
        }

        if (batch.getObjects().isEmpty()) {
            report.addWarning("Rule-driven engine produced no objects.");
            return report;
        }

        if (options.mode() == TransformationMode.APPLY) {
            if (options.direction() == TransformationDirection.FORWARD && forwardResult != null) {
                runtimeService.applyForwardTransformationResult(context, forwardResult);
                verifyForwardArtifacts(context, forwardResult);
            } else {
                runtimeService.applyImport(context, batch);
            }
            int corrCreated = options.direction() == TransformationDirection.FORWARD && forwardResult != null
                    ? forwardResult.corrCreations().size()
                    : corrMaterializer.materialize(context);
            report.addInfo("Applied rule-driven " + options.direction() + " transformation.");
            report.addInfo("Materialized correspondence objects: " + corrCreated + ".");
        } else {
            report.addInfo("Preview only. No writes executed.");
        }

        return report;
    }

    private TransformationReport executeFamilies2Persons(TggWorkspaceContext context,
                                                         TransformationOptions options,
                                                         FullObjectSnapshot sourceSnapshot,
                                                         FullObjectSnapshot targetSnapshot,
                                                         FullObjectSnapshot corrSnapshot,
                                                         TransformationReport report) {
        if (options.direction() == TransformationDirection.BACKWARD) {
            report.addWarning("Backward transformation for `Families2Persons` is not implemented yet.");
            return report;
        }

        ImportBatch targetBatch = buildFamilies2PersonsForwardBatch(context, sourceSnapshot, targetSnapshot, report);
        if (targetBatch.getObjects().isEmpty()) {
            report.addWarning("No target objects were produced from the current source snapshot.");
            return report;
        }

        report.addInfo("Prepared target import batch: objects=" + targetBatch.getObjects().size()
                + ", links=" + targetBatch.getLinks().size());

        if (options.mode() == TransformationMode.APPLY) {
            runtimeService.applyImport(context, targetBatch);
            int corrCreated = corrMaterializer.materialize(context);
            report.addInfo("Applied forward transformation to target model `" + context.getWorkspaceDefinition().getTargetModel().name() + "`.");
            report.addInfo("Materialized correspondence objects: " + corrCreated + ".");
        } else {
            report.addInfo("Preview only. No target or correspondence writes were executed.");
        }

        return report;
    }

    private ImportBatch buildFamilies2PersonsForwardBatch(TggWorkspaceContext context,
                                                          FullObjectSnapshot sourceSnapshot,
                                                          FullObjectSnapshot targetSnapshot,
                                                          TransformationReport report) {
        ImportBatch batch = new ImportBatch(WorkspaceSide.TARGET,
                new File(context.getTggFile().getParentFile(), "Families2Persons.forward.synthetic"));

        String personRegisterId = firstObjectIdByClass(targetSnapshot, "PersonRegister");
        if (personRegisterId == null) {
            personRegisterId = "personRegister1";
            batch.addObject(new ImportObjectSpec(personRegisterId, "PersonRegister"));
            report.addCreatedObject(personRegisterId + " : PersonRegister");
        } else {
            report.addInfo("Reusing existing target register `" + personRegisterId + "`.");
        }

        String familyRegisterId = firstObjectIdByClass(sourceSnapshot, "FamilyRegister");
        if (familyRegisterId == null) {
            report.addWarning("Source snapshot does not contain a FamilyRegister object.");
            return batch;
        }

        Map<String, ObjectState> families = filterByClass(sourceSnapshot, "Family");
        Map<String, ObjectState> familyMembers = filterByClass(sourceSnapshot, "FamilyMember");

        Map<String, String> fathers = collectBinaryLinkTargets(sourceSnapshot, "Father");
        Map<String, String> mothers = collectBinaryLinkTargets(sourceSnapshot, "Mother");
        Map<String, List<String>> sons = collectMultiBinaryLinkTargets(sourceSnapshot, "Sons");
        Map<String, List<String>> daughters = collectMultiBinaryLinkTargets(sourceSnapshot, "Daughters");

        for (ObjectState family : families.values()) {
            String familyName = stringValue(family.primitiveValues.get("name"));
            if (familyName == null || familyName.isBlank()) {
                continue;
            }

            addTargetPerson(batch, report, targetSnapshot, personRegisterId, "Male", familyName, fathers.get(family.name), familyMembers, "Father2Male");
            addTargetPerson(batch, report, targetSnapshot, personRegisterId, "Female", familyName, mothers.get(family.name), familyMembers, "Mother2Female");

            for (String sonId : sons.getOrDefault(family.name, List.of())) {
                addTargetPerson(batch, report, targetSnapshot, personRegisterId, "Male", familyName, sonId, familyMembers, "Son2Male");
            }
            for (String daughterId : daughters.getOrDefault(family.name, List.of())) {
                addTargetPerson(batch, report, targetSnapshot, personRegisterId, "Female", familyName, daughterId, familyMembers, "Daughter2Female");
            }
        }

        return batch;
    }

    private void addTargetPerson(ImportBatch batch,
                                 TransformationReport report,
                                 FullObjectSnapshot targetSnapshot,
                                 String personRegisterId,
                                 String targetClass,
                                 String familyName,
                                 String memberId,
                                 Map<String, ObjectState> familyMembers,
                                 String ruleName) {
        if (memberId == null) {
            return;
        }
        ObjectState member = familyMembers.get(memberId);
        if (member == null) {
            return;
        }

        String memberName = stringValue(member.primitiveValues.get("name"));
        if (memberName == null || memberName.isBlank()) {
            return;
        }

        String targetName = familyName + ", " + memberName;
        String objectId = targetClass.toLowerCase() + "_" + sanitizeId(familyName) + "_" + sanitizeId(memberName);

        ImportObjectSpec spec = new ImportObjectSpec(objectId, targetClass);
        spec.getAttributes().put("name", "'" + targetName.replace("'", "") + "'");
        batch.addObject(spec);
        batch.addLink(new ImportLinkSpec("PersonRegistration", List.of(personRegisterId, objectId)));

        if (targetSnapshot.objects.containsKey(objectId)) {
            report.addUpdatedObject(objectId + " : " + targetClass + " {name='" + targetName + "'}");
        } else {
            report.addCreatedObject(objectId + " : " + targetClass + " {name='" + targetName + "'}");
        }
        report.addCreatedLink("PersonRegistration [" + personRegisterId + ", " + objectId + "]");
        report.addInfo("Rule `" + ruleName + "` mapped `" + memberId + "` -> `" + objectId + "`.");
    }

    private String firstObjectIdByClass(FullObjectSnapshot snapshot, String className) {
        return snapshot.objects.values().stream()
                .filter(object -> className.equals(object.className))
                .map(object -> object.name)
                .findFirst()
                .orElse(null);
    }

    private Map<String, ObjectState> filterByClass(FullObjectSnapshot snapshot, String className) {
        Map<String, ObjectState> result = new LinkedHashMap<>();
        for (ObjectState object : snapshot.objects.values()) {
            if (className.equals(object.className)) {
                result.put(object.name, object);
            }
        }
        return result;
    }

    private Map<String, String> collectBinaryLinkTargets(FullObjectSnapshot snapshot, String associationName) {
        Map<String, String> targets = new LinkedHashMap<>();
        for (LinkState link : snapshot.links.values()) {
            if (associationName.equals(link.assocName) && link.participants.size() >= 2) {
                targets.put(link.participants.get(0), link.participants.get(1));
            }
        }
        return targets;
    }

    private Map<String, List<String>> collectMultiBinaryLinkTargets(FullObjectSnapshot snapshot, String associationName) {
        Map<String, List<String>> targets = new LinkedHashMap<>();
        for (LinkState link : snapshot.links.values()) {
            if (associationName.equals(link.assocName) && link.participants.size() >= 2) {
                targets.computeIfAbsent(link.participants.get(0), ignored -> new ArrayList<>()).add(link.participants.get(1));
            }
        }
        return targets;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String sanitizeId(String value) {
        return Objects.requireNonNullElse(value, "undefined").replaceAll("[^A-Za-z0-9_]", "_");
    }

    private void verifyForwardArtifacts(TggWorkspaceContext context, ForwardTransformationResult result) {
        FullObjectSnapshot targetSnapshot = runtimeService.loadSnapshot(context, WorkspaceSide.TARGET);
        FullObjectSnapshot corrSnapshot = runtimeService.loadSnapshot(context, WorkspaceSide.CORRESPONDENCE);

        Set<String> missingTargetObjects = new LinkedHashSet<>();
        for (ImportObjectSpec spec : result.targetBatch().getObjects()) {
            if (!targetSnapshot.objects.containsKey(spec.getObjectName())) {
                missingTargetObjects.add(spec.getObjectName());
            }
        }

        Set<String> missingTargetLinks = new LinkedHashSet<>();
        for (ImportLinkSpec link : result.targetBatch().getLinks()) {
            String identity = link.getIdentity();
            if (!targetSnapshot.links.containsKey(identity)) {
                missingTargetLinks.add(identity);
            }
        }

        Set<String> missingCorrObjects = new LinkedHashSet<>();
        Set<String> missingTraceLinks = new LinkedHashSet<>();
        for (ForwardTransformationResult.CorrCreation creation : result.corrCreations()) {
            if (!corrSnapshot.objects.containsKey(creation.record().objectId())) {
                missingCorrObjects.add(creation.record().objectId());
            }
            for (Map.Entry<String, String> binding : creation.sourceBindings().entrySet()) {
                String assocName = CorrRuntimeTraceHelper.bindingAssociationName(
                        creation.record().appliedRuleName(), WorkspaceSide.SOURCE, binding.getKey());
                String identity = assocName + "_" + creation.record().objectId() + "_" + binding.getValue();
                if (!corrSnapshot.links.containsKey(identity)) {
                    missingTraceLinks.add(identity);
                }
            }
            for (Map.Entry<String, String> binding : creation.targetBindings().entrySet()) {
                String assocName = CorrRuntimeTraceHelper.bindingAssociationName(
                        creation.record().appliedRuleName(), WorkspaceSide.TARGET, binding.getKey());
                String identity = assocName + "_" + creation.record().objectId() + "_" + binding.getValue();
                if (!corrSnapshot.links.containsKey(identity)) {
                    missingTraceLinks.add(identity);
                }
            }
        }

        if (!missingTargetObjects.isEmpty() || !missingTargetLinks.isEmpty()
                || !missingCorrObjects.isEmpty() || !missingTraceLinks.isEmpty()) {
            throw new IllegalStateException("Forward transformation persistence verification failed. Missing target objects="
                    + missingTargetObjects + ", target links=" + missingTargetLinks
                    + ", corr objects=" + missingCorrObjects + ", trace links=" + missingTraceLinks + ".");
        }
    }
}
