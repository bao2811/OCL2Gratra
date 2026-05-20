package org.uet.dse.neo4jtgg.service.impl;

import org.tzi.use.api.UseApiException;
import org.tzi.use.api.UseModelApi;
import org.tzi.use.uml.mm.MAssociation;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.uet.dse.neo4jtgg.model.TggRuleInfo;
import org.uet.dse.neo4jtgg.model.TggWorkspaceContext;
import org.uet.dse.neo4jtgg.model.TggWorkspaceDefinition;
import org.uet.dse.neo4jtgg.model.WorkspaceSide;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Neo4jTggWorkspaceLoader {
    private static final Pattern TRANSFORMATION_PATTERN = Pattern.compile("^\\s*transformation\\s+(\\w+)\\s*$");
    private static final Pattern RULE_PATTERN = Pattern.compile("^\\s*rule\\s+(\\w+)\\s*$");
    private static final Pattern CORR_CLASS_PATTERN = Pattern.compile("\\bin\\s+\\w+\\s*:\\s*([A-Za-z_][A-Za-z0-9_]*)");
    private static final Pattern TYPED_VARIABLE_PATTERN = Pattern.compile("^\\s*(\\w+)\\s*:\\s*([A-Za-z_][A-Za-z0-9_]*)");
    private static final Pattern ASSOCIATION_PATTERN = Pattern.compile("^\\s*\\(([^,]+),\\s*([^\\)]+)\\)\\s*:\\s*(\\w+)\\s*$");
    private static final Pattern CORR_LINK_PATTERN = Pattern.compile(
            "^\\s*\\(([^,]+),\\s*([^\\)]+)\\)\\s+as\\s+\\(([^,]+),\\s*([^\\)]+)\\)\\s+in\\s+(\\w+)\\s*:\\s*(\\w+)\\s*$");
    private static final Pattern BRACKET_PREDICATE_PATTERN = Pattern.compile("^\\s*\\[(.+)]\\s*$");
    private static final Pattern CORR_INVARIANT_PATTERN = Pattern.compile("^\\s*(\\w+)\\s*:\\s*\\[(.+)]\\s*$");

    public TggWorkspaceDefinition load(TggWorkspaceContext context, PrintWriter logWriter) throws IOException {
        MModel sourceModel = compileModel(context.getSourceFile().getAbsolutePath(), logWriter);
        MModel targetModel = compileModel(context.getTargetFile().getAbsolutePath(), logWriter);
        String tggContent = normalizeTggContent(Files.readString(context.getTggFile().toPath()));

        Set<String> sourceClasses = collectClassNames(sourceModel);
        Set<String> targetClasses = collectClassNames(targetModel);
        Set<String> corrClasses = parseCorrespondenceClasses(tggContent, sourceClasses, targetClasses);
        String transformationName = parseTransformationName(tggContent, sourceModel.name() + "2" + targetModel.name());
        List<ParsedRule> parsedRules = parseRuleBlocks(tggContent);
        List<TggRuleInfo> rules = parsedRules.stream().map(parsedRule -> parsedRule.ruleInfo).toList();
        MModel correspondenceModel = buildCorrespondenceModel(transformationName, parsedRules, corrClasses, sourceModel, targetModel);

        return new TggWorkspaceDefinition(
                transformationName,
                sourceModel,
                correspondenceModel,
                targetModel,
                rules,
                sourceClasses,
                corrClasses,
                targetClasses
        );
    }

    private MModel compileModel(String filePath, PrintWriter logWriter) throws IOException {
        try (FileInputStream inputStream = new FileInputStream(filePath)) {
            MModel model = USECompiler.compileSpecification(inputStream, filePath, logWriter, new ModelFactory());
            if (model == null) {
                throw new IllegalStateException("Failed to compile USE model: " + filePath);
            }
            return model;
        }
    }

    private Set<String> collectClassNames(MModel model) {
        Set<String> classNames = new LinkedHashSet<>();
        model.classes().forEach(cls -> classNames.add(cls.name()));
        return classNames;
    }

    private String parseTransformationName(String tggContent, String fallback) {
        for (String line : tggContent.split("\\R")) {
            Matcher matcher = TRANSFORMATION_PATTERN.matcher(line);
            if (matcher.matches()) {
                return matcher.group(1);
            }
        }
        return fallback;
    }

    private Set<String> parseCorrespondenceClasses(String tggContent, Set<String> sourceClasses, Set<String> targetClasses) {
        Set<String> classNames = new LinkedHashSet<>();
        Matcher matcher = CORR_CLASS_PATTERN.matcher(tggContent);
        while (matcher.find()) {
            String className = matcher.group(1);
            if (!sourceClasses.contains(className) && !targetClasses.contains(className)) {
                classNames.add(className);
            }
        }
        return classNames;
    }

    private List<ParsedRule> parseRuleBlocks(String tggContent) {
        List<ParsedRule> rules = new ArrayList<>();
        TggRuleInfo currentRule = null;
        WorkspaceSide currentSide = null;
        boolean readingBody = false;
        Map<WorkspaceSide, SectionBuffer> sectionBuffers = createSectionBuffers();

        for (String line : tggContent.split("\\R")) {
            Matcher ruleMatcher = RULE_PATTERN.matcher(line);
            if (ruleMatcher.matches()) {
                if (currentRule != null) {
                    rules.add(toParsedRule(currentRule, sectionBuffers));
                }
                currentRule = new TggRuleInfo(ruleMatcher.group(1));
                currentSide = null;
                resetBuffers(sectionBuffers);
                continue;
            }

            if (currentRule == null) {
                continue;
            }

            if (line.contains("checkSource(")) {
                currentSide = WorkspaceSide.SOURCE;
                readingBody = false;
                continue;
            }
            if (line.contains("checkTarget(")) {
                currentSide = WorkspaceSide.TARGET;
                readingBody = false;
                continue;
            }
            if (line.contains("checkCorr(")) {
                currentSide = WorkspaceSide.CORRESPONDENCE;
                readingBody = false;
                continue;
            }
            if (line.trim().equals("){")) {
                readingBody = true;
                continue;
            }
            if (line.trim().equals("}")) {
                currentSide = null;
                readingBody = false;
                continue;
            }
            if (line.trim().equals("end")) {
                rules.add(toParsedRule(currentRule, sectionBuffers));
                currentRule = null;
                currentSide = null;
                readingBody = false;
                resetBuffers(sectionBuffers);
                continue;
            }

            if (currentSide != null) {
                SectionBuffer buffer = sectionBuffers.get(currentSide);
                if (readingBody) {
                    buffer.body().append(line).append('\n');
                } else {
                    buffer.header().append(line).append('\n');
                }
            }
        }

        if (currentRule != null) {
            rules.add(toParsedRule(currentRule, sectionBuffers));
        }

        return rules;
    }

    private ParsedRule toParsedRule(TggRuleInfo rule, Map<WorkspaceSide, SectionBuffer> sectionBuffers) {
        String sourceText = sectionBuffers.get(WorkspaceSide.SOURCE).allText();
        String targetText = sectionBuffers.get(WorkspaceSide.TARGET).allText();
        String corrText = sectionBuffers.get(WorkspaceSide.CORRESPONDENCE).allText();
        rule.setSideConstraints(WorkspaceSide.SOURCE, sourceText);
        rule.setSideConstraints(WorkspaceSide.TARGET, targetText);
        rule.setSideConstraints(WorkspaceSide.CORRESPONDENCE, corrText);

        rule.setTypedVariables(WorkspaceSide.SOURCE, parseTypedVariables(sourceText));
        rule.setTypedVariables(WorkspaceSide.TARGET, parseTypedVariables(targetText));
        rule.setAssociationPatterns(WorkspaceSide.SOURCE, parseAssociationPatterns(sourceText));
        rule.setAssociationPatterns(WorkspaceSide.TARGET, parseAssociationPatterns(targetText));
        rule.setPredicates(WorkspaceSide.SOURCE, parsePredicates(sourceText));
        rule.setPredicates(WorkspaceSide.TARGET, parsePredicates(targetText));

        List<TggRuleInfo.CorrPattern> requiredCorr = parseCorrespondenceLinks(sectionBuffers.get(WorkspaceSide.CORRESPONDENCE).header().toString());
        List<TggRuleInfo.CorrPattern> outputCorr = parseCorrespondenceLinks(sectionBuffers.get(WorkspaceSide.CORRESPONDENCE).body().toString());
        List<TggRuleInfo.CorrInvariant> invariants = parseCorrInvariants(sectionBuffers.get(WorkspaceSide.CORRESPONDENCE).body().toString());
        rule.setRequiredCorrPatterns(requiredCorr);
        rule.setOutputCorrPatterns(outputCorr);
        rule.setCorrInvariants(invariants);

        return new ParsedRule(
                rule,
                rule.getTypedVariables(WorkspaceSide.SOURCE),
                rule.getTypedVariables(WorkspaceSide.TARGET),
                outputCorr
        );
    }

    private Map<WorkspaceSide, SectionBuffer> createSectionBuffers() {
        Map<WorkspaceSide, SectionBuffer> sectionBuffers = new LinkedHashMap<>();
        for (WorkspaceSide side : WorkspaceSide.values()) {
            sectionBuffers.put(side, new SectionBuffer(new StringBuilder(), new StringBuilder()));
        }
        return sectionBuffers;
    }

    private void resetBuffers(Map<WorkspaceSide, SectionBuffer> sectionBuffers) {
        sectionBuffers.values().forEach(SectionBuffer::clear);
    }

    private Map<String, String> parseTypedVariables(String sectionText) {
        Map<String, String> variables = new LinkedHashMap<>();
        for (String line : sectionText.split("\\R")) {
            Matcher matcher = TYPED_VARIABLE_PATTERN.matcher(line);
            if (matcher.matches()) {
                variables.put(matcher.group(1).trim(), matcher.group(2).trim());
            }
        }
        return variables;
    }

    private List<TggRuleInfo.AssociationPattern> parseAssociationPatterns(String sectionText) {
        List<TggRuleInfo.AssociationPattern> patterns = new ArrayList<>();
        for (String line : sectionText.split("\\R")) {
            Matcher matcher = ASSOCIATION_PATTERN.matcher(line);
            if (matcher.matches()) {
                patterns.add(new TggRuleInfo.AssociationPattern(
                        normalizeVariableName(matcher.group(1)),
                        normalizeVariableName(matcher.group(2)),
                        matcher.group(3).trim()
                ));
            }
        }
        return patterns;
    }

    private List<String> parsePredicates(String sectionText) {
        List<String> predicates = new ArrayList<>();
        for (String line : sectionText.split("\\R")) {
            Matcher matcher = BRACKET_PREDICATE_PATTERN.matcher(line);
            if (matcher.matches()) {
                predicates.add(matcher.group(1).trim());
            }
        }
        return predicates;
    }

    private List<TggRuleInfo.CorrPattern> parseCorrespondenceLinks(String corrSectionText) {
        List<TggRuleInfo.CorrPattern> patterns = new ArrayList<>();
        for (String line : corrSectionText.split("\\R")) {
            Matcher matcher = CORR_LINK_PATTERN.matcher(line);
            if (matcher.matches()) {
                patterns.add(new TggRuleInfo.CorrPattern(
                        normalizeVariableName(matcher.group(1)),
                        normalizeVariableName(matcher.group(2)),
                        normalizeVariableName(matcher.group(3)),
                        normalizeVariableName(matcher.group(4)),
                        matcher.group(5).trim(),
                        matcher.group(6).trim()
                ));
            }
        }
        return patterns;
    }

    private List<TggRuleInfo.CorrInvariant> parseCorrInvariants(String corrSectionText) {
        List<TggRuleInfo.CorrInvariant> invariants = new ArrayList<>();
        for (String line : corrSectionText.split("\\R")) {
            Matcher matcher = CORR_INVARIANT_PATTERN.matcher(line);
            if (matcher.matches()) {
                invariants.add(new TggRuleInfo.CorrInvariant(
                        matcher.group(1).trim(),
                        matcher.group(2).trim()
                ));
            }
        }
        return invariants;
    }

    private String normalizeVariableName(String rawName) {
        return rawName.replace("*", "").trim();
    }

    private MModel buildCorrespondenceModel(String transformationName,
                                            List<ParsedRule> parsedRules,
                                            Set<String> corrClasses,
                                            MModel sourceModel,
                                            MModel targetModel) {
        UseModelApi api = new UseModelApi(transformationName + "_Correspondence");

        try {
            for (String corrClass : corrClasses) {
                if (api.getClass(corrClass) == null) {
                    api.createClass(corrClass, false);
                }
            }

            Set<String> createdAssociations = new LinkedHashSet<>();
            for (ParsedRule rule : parsedRules) {
                for (TggRuleInfo.CorrPattern link : rule.corrLinks) {
                    if (api.getClass(link.corrClassName()) == null) {
                        api.createClass(link.corrClassName(), false);
                    }

                    String srcClass = rule.sourceVariables.get(link.sourceVarName());
                    if (srcClass != null && sourceModel.getClass(srcClass) != null) {
                        createCorrAssociation(api, createdAssociations, link.corrClassName(), srcClass);
                    }

                    String targetClass = rule.targetVariables.get(link.targetVarName());
                    if (targetClass != null && targetModel.getClass(targetClass) != null) {
                        createCorrAssociation(api, createdAssociations, link.corrClassName(), targetClass);
                    }
                }
            }
        } catch (UseApiException exception) {
            throw new IllegalStateException("Failed to build correspondence model: " + exception.getMessage(), exception);
        }

        return api.getModel();
    }

    private void createCorrAssociation(UseModelApi api,
                                       Set<String> createdAssociations,
                                       String corrClassName,
                                       String domainClassName) throws UseApiException {
        String associationName = corrClassName + "_" + domainClassName;
        if (!createdAssociations.add(associationName)) {
            return;
        }

        MAssociation existing = api.getAssociation(associationName);
        if (existing != null) {
            return;
        }

        if (api.getClass(domainClassName) == null) {
            api.createClass(domainClassName, false);
        }

        api.createAssociation(
                associationName,
                new String[]{corrClassName, domainClassName},
                new String[]{lowerFirst(corrClassName), lowerFirst(domainClassName)},
                new String[]{"0..*", "0..*"},
                new int[]{0, 0},
                new boolean[]{false, false},
                new String[0][][]
        );
    }

    private String lowerFirst(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return Character.toLowerCase(value.charAt(0)) + value.substring(1);
    }

    private record ParsedRule(TggRuleInfo ruleInfo,
                              Map<String, String> sourceVariables,
                              Map<String, String> targetVariables,
                              List<TggRuleInfo.CorrPattern> corrLinks) {
    }

    private String normalizeTggContent(String tggContent) {
        return tggContent
                .replace("}checkSource(", "}\ncheckSource(")
                .replace("}checkTarget(", "}\ncheckTarget(")
                .replace("}checkCorr(", "}\ncheckCorr(");
    }

    private record SectionBuffer(StringBuilder header, StringBuilder body) {
        String allText() {
            return header.toString() + body;
        }

        void clear() {
            header.setLength(0);
            body.setLength(0);
        }
    }
}
