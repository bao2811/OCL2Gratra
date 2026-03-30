package org.uet.dse.neo4j.sync.model;

import org.tzi.use.api.UseModelApi;
import org.tzi.use.parser.Context;
import org.tzi.use.parser.Symtable;
import org.tzi.use.parser.ocl.OCLCompiler;
import org.tzi.use.uml.mm.MClass;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.MOperation;
import org.tzi.use.uml.ocl.expr.Expression;
import org.tzi.use.uml.ocl.expr.VarDecl;
import org.tzi.use.util.NullPrintWriter;
import org.uet.dse.neo4j.manager.Neo4jDriverManager;
import org.uet.dse.neo4j.model.ClassState;
import org.uet.dse.neo4j.model.FullModelSnapshot;
import org.uet.dse.neo4j.manager.WorkLogManager;
import org.uet.dse.neo4j.sync.helper.UmlTypeTranslator;

import java.io.PrintWriter;
import java.util.*;
import java.util.stream.Collectors;

public class ModelPullService {

    public void pullModelFromNeo4jAfterSnapshotComparing(MModel targetModel, FullModelSnapshot snapshot) {
        UseModelApi api = new UseModelApi(targetModel);

        try {
            // Order is load-bearing: each phase depends on the types defined before it.
            applyEnumerations(api, snapshot);
            applyConcreteAndAbstractClasses(api, snapshot);
            applyAssociationClasses(api, snapshot);
            applyAttributes(api, snapshot);
            applyOperationSignatures(api, snapshot);
            applyGeneralizations(targetModel, api, snapshot);
            applyAssociations(targetModel, api, snapshot);
            applyOperationBodiesFromSnapshot(targetModel, snapshot.classes);
            applyInvariantsFromSnapshot(targetModel, snapshot.classes);
            applyOperationConstraintsFromSnapshot(targetModel, snapshot.classes);
        } catch (Exception e) {
            WorkLogManager.getInstance().log("lala", e.getMessage());
        }
    }

    private void applyEnumerations(UseModelApi api, FullModelSnapshot snapshot) throws Exception {
        for (ClassState cls : classesOfType(snapshot, "NodeEnumeration")) {
            List<String> literals = cls.getEnumLiterals();
            if (literals == null || literals.isEmpty()) continue;

            api.createEnumeration(cls.getName(), literals);
            WorkLogManager.getInstance().log("PULL_ENUM", "Created Enum: " + cls.getName());
        }
    }

    private void applyConcreteAndAbstractClasses(UseModelApi api, FullModelSnapshot snapshot) throws Exception {
        for (ClassState cls : classesExcluding(snapshot, "NodeAssociationClass", "NodeEnumeration")) {
            boolean isAbstract = "NodeAbstractClass".equals(cls.getMetaNodeName());
            api.createClass(cls.getName(), isAbstract);
        }
    }

    private void applyAssociationClasses(UseModelApi api, FullModelSnapshot snapshot) throws Exception {
        for (ClassState cls : classesOfType(snapshot, "NodeAssociationClass")) {
            Map<String, Object> src = cls.getAcSource();
            Map<String, Object> tgt = cls.getAcTarget();

            api.createAssociationClass(
                    cls.getName(), false,
                    (String) src.get("name"), (String) src.get("role"), (String) src.get("mult"), 0,
                    (String) tgt.get("name"), (String) tgt.get("role"), (String) tgt.get("mult"), 0
            );
        }
    }

    private void applyAttributes(UseModelApi api, FullModelSnapshot snapshot) {
        for (ClassState cls : classesExcluding(snapshot, "NodeEnumeration")) {
            List<Map<String, Object>> sortedAttrs = sortedByIndex(cls.getAttributes().values());

            for (Map<String, Object> attr : sortedAttrs) {
                applyAttribute(api, cls.getName(), attr);
            }
        }
    }

    private void applyAttribute(UseModelApi api, String className, Map<String, Object> attr) {
        String attrName      = (String) attr.get("attrName");
        String umlTypeString = UmlTypeTranslator.reconstructFullTypeString(attr);

        try {
            api.createAttribute(className, attrName, umlTypeString);
        } catch (Exception e) {
            System.err.printf("Failed to create attribute [%s] on class [%s] with type [%s]: %s%n",
                    attrName, className, umlTypeString, e.getMessage());
        }
    }

    private void applyOperationSignatures(UseModelApi api, FullModelSnapshot snapshot) {
        for (ClassState cls : snapshot.classes.values()) {
            for (Map.Entry<String, Map<String, Object>> entry : cls.getOperations().entrySet()) {
                applyOperationSignature(api, cls, entry.getKey(), entry.getValue());
            }
        }
    }

    private void applyOperationSignature(UseModelApi api, ClassState cls, String opName, Map<String, Object> opProps) {
        String className = cls.getName();
        try {
            String returnType    = UmlTypeTranslator.toUmlTypeString((String) opProps.get("returnType"));
            String[][] paramArray = buildParamArray(cls.getOpParamsSorted(opName));

            api.createOperation(className, opName, paramArray, returnType);
            WorkLogManager.getInstance().log("PULL_OP", "Created signature: " + className + "::" + opName);

        } catch (Exception e) {
            String msg = String.format("Failed to define op [%s] on class [%s]: %s", opName, className, e.getMessage());
            System.err.println(msg);
            WorkLogManager.getInstance().log("PULL_OP_ERROR", msg);
        }
    }

    private String[][] buildParamArray(List<ClassState.OpParam> params) {
        String[][] array = new String[params.size()][2];
        for (int i = 0; i < params.size(); i++) {
            ClassState.OpParam p = params.get(i);
            array[i][0] = (p.getName() == null || p.getName().isEmpty()) ? "arg" + i : p.getName();
            array[i][1] = UmlTypeTranslator.toUmlTypeString(p.getType());
        }
        return array;
    }

    private void applyGeneralizations(MModel targetModel, UseModelApi api, FullModelSnapshot snapshot) throws Exception {
        for (ClassState cls : snapshot.classes.values()) {
            MClass child = targetModel.getClass(cls.getName());
            if (child == null) continue;

            for (String parentName : cls.getParents()) {
                MClass parent = targetModel.getClass(parentName);
                if (parent != null) {
                    api.createGeneralizationEx(child, parent);
                }
            }
        }
    }

    private void applyAssociations(MModel targetModel, UseModelApi api, FullModelSnapshot snapshot) throws Exception {
        syncAssociationsToTargetModel(targetModel, api);
        syncTernaryAssociationsFromGraph(targetModel, api, snapshot.ternaryAssociations);
    }

    private Collection<ClassState> classesOfType(FullModelSnapshot snapshot, String metaNodeName) {
        return snapshot.classes.values().stream()
                .filter(c -> metaNodeName.equals(c.getMetaNodeName()))
                .collect(Collectors.toList());
    }

    private Collection<ClassState> classesExcluding(FullModelSnapshot snapshot, String... excludedTypes) {
        Set<String> excluded = Set.of(excludedTypes);
        return snapshot.classes.values().stream()
                .filter(c -> !excluded.contains(c.getMetaNodeName()))
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> sortedByIndex(Collection<Map<String, Object>> attrs) {
        return attrs.stream()
                .sorted(Comparator.comparingInt(a -> ((Number) a.getOrDefault("index", 0)).intValue()))
                .collect(Collectors.toList());
    }




    private void syncTernaryAssociationsFromGraph(MModel targetModel, UseModelApi tempApi, List<Map<String, Object>> ternaryList) {
        for (Map<String, Object> ternaryData : ternaryList) {
            String assocName = (String) ternaryData.get("name");
            List<Object> rawParticipants = (List<Object>) ternaryData.get("participants");

            List<Map<String, Object>> participants = new ArrayList<>();
            for (Object o : rawParticipants) participants.add((Map<String, Object>) o);

            participants.sort(Comparator.comparingInt(p -> ((Number) p.get("idx")).intValue()));

            int n = participants.size();
            String[] classNames = new String[n];
            String[] roleNames = new String[n];
            String[] multiplicities = new String[n];
            int[] aggregationKinds = new int[n];
            boolean[] orderedInfo = new boolean[n];

            for (int i = 0; i < n; i++) {
                Map<String, Object> p = participants.get(i);
                classNames[i] = (String) p.get("cls");
                roleNames[i] = (String) p.get("role");
                multiplicities[i] = (String) p.get("mult");

                String edgeType = (String) p.get("type");
                aggregationKinds[i] = edgeType.equals("ComposeOf") ? 2 : (edgeType.equals("Aggregates") ? 1 : 0);
                orderedInfo[i] = false;
            }

            try {
                tempApi.createAssociation(assocName, classNames, roleNames, multiplicities, aggregationKinds, orderedInfo, new String[0][][]);
                WorkLogManager.getInstance().log("PULL_TERNARY", "Restored: " + assocName + " with " + n + " ends.");
            } catch (Exception e) {
                System.err.println("N-ary Assoc Fail [" + assocName + "]: " + e.getMessage());
            }
        }
    }
    private void applyOperationBodiesFromSnapshot(MModel targetModel, Map<String, ClassState> dbSnapshot) {
        for (ClassState dbCls : dbSnapshot.values()) {
            String className = dbCls.getName();
            MClass useCls = targetModel.getClass(className);
            if (useCls == null) continue;

            for (Map.Entry<String, Map<String, Object>> opEntry : dbCls.getOperations().entrySet()) {
                String opName = opEntry.getKey();
                Map<String, Object> opProps = opEntry.getValue();
                String body = (String) opProps.get("body");
                boolean isQuery = (boolean) opProps.get("isQuery");

                if (body == null || body.trim().isEmpty() || body.equals("null")) continue;

                MOperation useOp = useCls.operation(opName, false);

                if (useOp != null) {
                    compileOpBodySafe(targetModel, useCls, useOp, body, isQuery);
                }
            }
        }
    }

    private void compileOpBodySafe(MModel model, MClass cls, MOperation op, String body, boolean isQuery) {
        if (op == null || body == null || body.equals("null")) return;
        body = body.trim();
        try {
            if (isQuery) {
                // --- XỬ LÝ OCL QUERY (=) ---
                // Loại bỏ dấu ngoặc kép bọc ngoài nếu có do dữ liệu DB
                if (body.startsWith("\"") && body.endsWith("\"")) {
                    body = body.substring(1, body.length() - 1);
                }
                // --- BỘ MÁY 1: OCL COMPILER (Cho succ, pred, isFinalStep) ---
                Symtable symtable = createSymtable(model, cls, op);
                Expression expr = org.tzi.use.parser.ocl.OCLCompiler.compileExpression(
                        model, body, "OCL_Parser", org.tzi.use.util.NullPrintWriter.getInstance(), symtable);

                if (expr != null) {
                    op.setExpression(expr);
                }
            } else {
                // --- XỬ LÝ SOIL IMPERATIVE (begin...end) ---
                // ĐẶC TẢ QUAN TRỌNG: Nếu thiếu begin/end, phải bọc lại để SoilParser không lỗi 'declare'
                String soilCode = body;
                if (!soilCode.toLowerCase().startsWith("begin")) {
                    soilCode = "begin\n" + soilCode + "\nend";
                }
                // --- BỘ MÁY 2: SOIL COMPILER (Cho hàm copy có 'declare') ---
                // SOIL cần input stream để đọc khối lệnh
                java.io.InputStream is = new java.io.ByteArrayInputStream(soilCode.getBytes());
                java.io.PrintWriter errWriter = new java.io.PrintWriter(System.err);

                // 1. Tạo cây cú pháp SOIL
                org.tzi.use.parser.soil.ast.ASTStatement ast =
                        org.tzi.use.parser.soil.SoilCompiler.constructAST(is, "SOIL_Parser", errWriter, false);

                if (ast != null) {
                    // 2. Tạo ngữ cảnh thực thi SOIL
                    Context soilCtx =
                            new Context("SOIL_Context", errWriter, new org.tzi.use.uml.ocl.value.VarBindings(), null);
                    soilCtx.setModel(model);

                    // 3. Biến đổi AST thành Statement và gán vào hàm
                    org.tzi.use.uml.sys.soil.MStatement statement = ast.generateStatement(soilCtx, op);
                    op.setStatement(statement);
                    WorkLogManager.getInstance().log("PULL_SOIL", "Successfully compiled SOIL: " + op.name());
                }
            }
        } catch (Exception e) {
            System.err.println("Compilation failed for " + op.name() + ": " + e.getMessage());
        }
    }

    /**
     * create context for ocl compiler
     */
    private Symtable createSymtable(MModel model, MClass cls, MOperation op) {
        // Khởi tạo bảng ký hiệu từ thư viện của tool USE
        Symtable symtable = new Symtable();

        try {
            symtable.add("self", cls, null);

            if (op != null && op.paramList() != null) {
                org.tzi.use.uml.ocl.expr.VarDeclList paramList = op.paramList();
                for (int i = 0; i < paramList.size(); i++) {
                    org.tzi.use.uml.ocl.expr.VarDecl var = paramList.varDecl(i);
                    symtable.add(var.name(), var.type(), null);

                }
            }

        } catch (org.tzi.use.parser.SemanticException e) {
            System.err.println("Symtable Error: " + e.getMessage());
        }

        return symtable;
    }

    private void applyInvariantsFromSnapshot(MModel targetModel, Map<String, ClassState> dbSnapshot) {
        org.tzi.use.api.UseModelApi tempApi = new org.tzi.use.api.UseModelApi(targetModel);
        for (ClassState dbCls : dbSnapshot.values()) {
            for (Map<String, Object> invProps : dbCls.getInvariants()) {
                String invName = (String) invProps.get("name");
                String oclBody = (String) invProps.get("expr");
                boolean isExist = (boolean) invProps.get("exist");

                try {
                    if (oclBody != null) {
                        tempApi.createInvariant(invName, dbCls.getName(), oclBody, isExist);
                    }
                } catch (Exception e) {
                    System.err.println("OCL Invariant Error in " + dbCls.getName() + ": " + e.getMessage());
                }
            }
        }
    }

    private void applyOperationConstraintsFromSnapshot(MModel targetModel, Map<String, ClassState> dbSnapshot) {
        org.tzi.use.api.UseModelApi tempApi = new org.tzi.use.api.UseModelApi(targetModel);

        for (ClassState dbCls : dbSnapshot.values()) {
            String className = dbCls.getName();
            MClass useCls = targetModel.getClass(className);
            if (useCls == null) continue;

            for (String opName : dbCls.getOperations().keySet()) {
                MOperation useOp = useCls.operation(opName, false);
                if (useOp == null) continue;

                for (Map<String, Object> pc : dbCls.getPreConditions(opName)) {
                    compileAndAddConstraint(targetModel, tempApi, useCls, useOp, pc, true);
                }

                for (Map<String, Object> pc : dbCls.getPostConditions(opName)) {
                    compileAndAddConstraint(targetModel, tempApi, useCls, useOp, pc, false);
                }
            }
        }
    }


    private void compileAndAddConstraint(MModel model, org.tzi.use.api.UseModelApi api,
                                         MClass cls, MOperation op, Map<String, Object> props, boolean isPre) {
        String condName = (String) props.get("name");
        String oclExpr = (String) props.get("expr");
        if (oclExpr == null || oclExpr.isEmpty()) return;

        try {
            Symtable symtable = new Symtable();

            symtable.add("self", cls, null);

            for (int i = 0; i < op.paramList().size(); i++) {
                VarDecl param = op.paramList().varDecl(i);
                symtable.add(param.name(), param.type(), null);
            }

            if (!isPre && op.hasResultType()) {
                symtable.add("result", op.resultType(), null);
            }

            Expression compiledExpr = OCLCompiler.compileExpression(
                    model, oclExpr, "SyncPull", NullPrintWriter.getInstance(), symtable);

            if (compiledExpr != null) {
                api.createPrePostConditionEx(condName, op, isPre, compiledExpr);
                WorkLogManager.getInstance().log("PULL_CONST", "Applied: " + op.name() + "::" + condName);
            }
        } catch (Exception e) {
            System.err.println("Failed to compile constraint [" + condName + "]: " + e.getMessage());
        }
    }

    private void syncAssociationsToTargetModel(MModel targetModel, org.tzi.use.api.UseModelApi tempApi) {
        String dbName = Neo4jDriverManager.getInstance().getActiveDatabase();

        try (org.neo4j.driver.Session session = Neo4jDriverManager.getInstance().getDriver().session(
                org.neo4j.driver.SessionConfig.forDatabase(dbName))) {

            String cypher = "MATCH (s)-[r]->(t) " +
                    "WHERE type(r) IN ['AssociateWith', 'ComposeOf', 'Aggregates'] " +
                    "AND NOT coalesce(r.isTernary, false) " +
                    "RETURN r.associationName as name, s.name as src, t.name as tgt, " +
                    "r.sourceClassrole as sRole, r.sourceMultiplicity as sMult, r.sourceKind as sKind, " +
                    "r.targerClassrole as tRole, r.targetMultiplicity as tMult, r.targetKind as tKind, " +
                    "type(r) as edgeType";

            org.neo4j.driver.Result res = session.run(cypher);

            while (res.hasNext()) {
                org.neo4j.driver.Record rec = res.next();
                String assocName = rec.get("name").asString();

                if (targetModel.getAssociation(assocName) != null) continue;

                String srcName = rec.get("src").asString();
                String sRole = rec.get("sRole").asString("src_" + assocName);
                String sMult = rec.get("sMult").asString("1");
                int sKind = rec.get("sKind").isNull() ? 0 : rec.get("sKind").asInt();

                String tgtName = rec.get("tgt").asString();
                String tRole = rec.get("tRole").asString("tgt_" + assocName);
                String tMult = rec.get("tMult").asString("*");
                int tKind = rec.get("tKind").isNull() ? 0 : rec.get("tKind").asInt();

                if (rec.get("sKind").isNull() && rec.get("tKind").isNull()) {
                    String edgeType = rec.get("edgeType").asString();
                    if (edgeType.equals("ComposeOf")) tKind = 2;
                    else if (edgeType.equals("Aggregates")) tKind = 1;
                }

                try {
                    MClass srcCls = targetModel.getClass(srcName);
                    MClass tgtCls = targetModel.getClass(tgtName);

                    if (srcCls == null || tgtCls == null) continue;

                    tempApi.createAssociation(
                            assocName,
                            srcName, sRole, sMult, sKind,
                            tgtName, tRole, tMult, tKind
                    );

                    WorkLogManager.getInstance().log("PULL_ASSOC", "Restored: " + assocName);

                } catch (Exception e) {
                    System.err.println("Error creating assoc " + assocName + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            WorkLogManager.getInstance().log("PULL_DB_ERROR", e.getMessage());
        }
    }
}
