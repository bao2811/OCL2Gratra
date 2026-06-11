package org.uet.dse.neo4jtgg.benchmark;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.jupiter.api.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.uet.dse.neo4j.OCLLexer;
import org.uet.dse.neo4j.OCLParser;
import org.uet.dse.neo4jtgg.model.CypherCompilationResult;
import org.uet.dse.neo4jtgg.ocl.OclMetamodelIndex;
import org.uet.dse.neo4jtgg.ocl.OclSemanticBinder;
import org.uet.dse.neo4jtgg.ocl.ir.*;
import org.uet.dse.neo4jtgg.service.impl.DefaultOclToCypherCompiler;
import org.uet.dse.neo4j.oclite.ast.ASTFile;
import org.uet.dse.neo4j.oclite.ast.ASTContext;
import org.uet.dse.neo4j.oclite.ast.ASTNode;
import org.uet.dse.neo4j.oclite.ast.ASTVisitor;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Semantic preservation evaluation: compares Java evaluator violations vs IR
 * compiled violations on realistic test data, grouped by OCL category.
 *
 * Uses self-contained TestObject/TestRuntime/evaluators (same logic as
 * OclDualCheckTest).
 */
class SemanticPreservationTest {

    private static final String SPEC = """
            model EvalDemo
            class Company
            attributes
                name : String
            end
            class Person
            attributes
                age : Integer
                firstName : String
                salary : Integer
            end
            class Department
            attributes
                deptName : String
            end
            association CompanyEmployee between
                Company[*] role employer
                Person[*] role employee
            end
            association CompanyManager between
                Company[*] role managedCompany
                Person[1] role manager
            end
            association DeptPerson between
                Department[*] role department
                Person[*] role staff
            end
            """;

    record EvalResult(String group, String oclExpr, int javaViolations, int irViolations, boolean match) {

    }

    @Test
    void evaluateSemanticPreservationByCategory() {
        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(SPEC, "eval.use",
                new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        // Build test data - realistic company scenario
        TestObj jack = obj("p1", "Person").attr("age", 55L).attr("firstName", "Jack").attr("salary", 6000L);
        TestObj mary = obj("p2", "Person").attr("age", 40L).attr("firstName", "Mary").attr("salary", 4500L);
        TestObj tom = obj("p3", "Person").attr("age", 70L).attr("firstName", "Tom").attr("salary", 3000L);
        TestObj young = obj("p4", "Person").attr("age", 16L).attr("firstName", "Young").attr("salary", 0L);
        TestObj noname = obj("p5", "Person").attr("age", 30L).attr("salary", 5000L);

        TestObj acme = obj("c1", "Company").attr("name", "Acme")
                .link("employee", jack, mary).link("manager", mary);
        TestObj beta = obj("c2", "Company").attr("name", "Beta")
                .link("employee", tom, young).link("manager", tom);
        TestObj emptyCo = obj("c3", "Company").attr("name", "EmptyCo");

        TestObj dept1 = obj("d1", "Department").attr("deptName", "Engineering").link("staff", jack, mary);
        TestObj dept2 = obj("d2", "Department").attr("deptName", "Sales");

        jack.link("employer", acme).link("department", dept1);
        mary.link("employer", acme).link("managedCompany", acme).link("department", dept1);
        tom.link("employer", beta).link("managedCompany", beta);
        young.link("employer", beta);

        RT runtime = new RT(List.of(acme, beta, emptyCo, jack, mary, tom, young, noname, dept1, dept2));

        // Define OCL expressions by category
        Map<String, List<String>> categories = new LinkedHashMap<>();

        categories.put("Navigation", List.of(
                "context Company inv HasEmployees: self.employee->notEmpty()",
                "context Person inv HasEmployer: self.employer->notEmpty()",
                "context Company inv ManagerDefined: self.manager.firstName.isDefined()",
                "context Department inv HasStaff: self.staff->notEmpty()"
        ));
        categories.put("Iterator (forAll/exists/select)", List.of(
                "context Company inv WorkingAge: self.employee->forAll(p | p.age <= 65)",
                "context Company inv HasSenior: self.employee->exists(p | p.age > 50)",
                "context Company inv AdultOnly: self.employee->select(p | p.age >= 18)->size() = self.employee->size()",
                "context Company inv AllNamed: self.employee->forAll(e | e.firstName.isDefined())"
        ));
        categories.put("Collection (size/isEmpty/count)", List.of(
                "context Company inv AtLeastOne: self.employee->size() >= 1",
                "context Person inv NoEmployer: self.employer->isEmpty()",
                "context Department inv StaffNotEmpty: self.staff->notEmpty()"
        ));
        categories.put("Arithmetic/Comparison", List.of(
                "context Person inv PositiveSalary: self.salary > 0",
                "context Company inv NameDefined: self.name.isDefined()"
        ));
        categories.put("Logic (implies/and/or)", List.of(
                "context Person inv AdultRule: self.age >= 18 implies self.salary > 0",
                "context Company inv HasBothOrNone: self.employee->notEmpty() implies self.manager.firstName.isDefined()"
        ));
        categories.put("Control flow (if/let)", List.of(
                "context Person inv IfCheck: if self.age >= 18 then self.salary > 0 else true endif",
                "context Person inv LetThreshold: let t = 18 in self.age >= t implies self.employer->notEmpty()"
        ));

        // Evaluate all
        List<EvalResult> results = new ArrayList<>();
        OclMetamodelIndex idx = new OclMetamodelIndex(model);
        OclSemanticBinder binder = new OclSemanticBinder(idx);
        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);

        for (var entry : categories.entrySet()) {
            for (String ocl : entry.getValue()) {
                ASTNode ast = new ASTVisitor().visit(
                        new OCLParser(new CommonTokenStream(new OCLLexer(CharStreams.fromString(ocl)))).oclFile());
                ASTFile file = (ASTFile) ast;
                ASTContext ctx = file.invariants().get(0);
                OclSemanticBinder.BoundContextInvariant bound = binder.bindContext(ctx);
                OclIr.InvariantQuery opt = new OclIrOptimizer().optimizeInvariant(
                        new OclIrBuilder().buildInvariant(bound));
                String ctxClass = ctx.className;

                int jv = 0, iv = 0;
                for (TestObj self : runtime.all(ctxClass)) {
                    var scope = Map.<String, Object>of("self", self);
                    if (!toBool(evalBound(bound.expression(), scope, runtime))) {
                        jv++;
                    }
                    if (!toBool(evalIr(opt.predicate(), scope, runtime))) {
                        iv++;
                    }
                }
                results.add(new EvalResult(entry.getKey(), ocl, jv, iv, jv == iv));
                assertTrue(compiler.compile(ocl).isSupported(), "Unsupported: " + ocl);
            }
        }

        // Print report
        StringBuilder rpt = new StringBuilder();
        rpt.append("\n=== Semantic Preservation Evaluation ===\n\n");
        rpt.append(String.format("%-35s %-15s %-15s %-8s %-8s\n",
                "OCL Group", "Java Violations", "IR Violations", "Match", "Rules"));
        rpt.append("-".repeat(85)).append("\n");

        Map<String, int[]> grp = new LinkedHashMap<>();
        for (EvalResult r : results) {
            grp.computeIfAbsent(r.group, k -> new int[3]);
            grp.get(r.group)[0] += r.javaViolations;
            grp.get(r.group)[1] += r.irViolations;
            grp.get(r.group)[2]++;
        }
        for (var e : grp.entrySet()) {
            rpt.append(String.format("%-35s %-15d %-15d %-8s %-8d\n",
                    e.getKey(), e.getValue()[0], e.getValue()[1],
                    e.getValue()[0] == e.getValue()[1] ? "Yes" : "NO", e.getValue()[2]));
        }
        rpt.append("-".repeat(85)).append("\n");
        long m = results.stream().filter(r -> r.match).count();
        rpt.append(String.format("TOTAL: %d/%d rules match (%.1f%%)\n\n", m, results.size(), 100.0 * m / results.size()));

        rpt.append("--- Detail ---\n");
        for (EvalResult r : results) {
            String s = r.oclExpr.substring(r.oclExpr.indexOf("inv ") + 4);
            if (s.length() > 55) {
                s = s.substring(0, 52) + "...";
            }
            rpt.append(String.format("  [%s] %-55s Java=%d IR=%d\n",
                    r.match ? "OK" : "!!", s, r.javaViolations, r.irViolations));
        }

        System.out.println(rpt);
        for (EvalResult r : results) {
            assertEquals(r.javaViolations, r.irViolations, "Mismatch: " + r.oclExpr);
        }
    }

    // --- Minimal evaluators (same logic as OclDualCheckTest) ---
    private boolean toBool(Object v) {
        return v instanceof Boolean b && b;
    }

    private Object evalBound(OclSemanticBinder.BoundExpression e, Map<String, Object> s, RT rt) {
        if (e instanceof OclSemanticBinder.BoundVariable v) {
            return s.get(v.ast().name);
        }
        if (e instanceof OclSemanticBinder.BoundLiteral l) {
            return l.value();
        }
        if (e instanceof OclSemanticBinder.BoundNot n) {
            return !toBool(evalBound(n.expression(), s, rt));
        }
        if (e instanceof OclSemanticBinder.BoundIf i) {
            return toBool(evalBound(i.condition(), s, rt)) ? evalBound(i.thenBranch(), s, rt) : evalBound(i.elseBranch(), s, rt);
        }
        if (e instanceof OclSemanticBinder.BoundLet l) {
            var ns = new LinkedHashMap<>(s);
            ns.put(l.ast().variableName, evalBound(l.value(), s, rt));
            return evalBound(l.body(), ns, rt);
        }
        if (e instanceof OclSemanticBinder.BoundBinary b) {
            return evalBin(b.ast().op, evalBound(b.left(), s, rt), evalBound(b.right(), s, rt));
        }
        if (e instanceof OclSemanticBinder.BoundProperty p) {
            Object src = evalBound(p.source(), s, rt);
            return p.isAttribute() ? rt.attr(src, p.ast().name) : rt.nav(src, p.navigation().roleName());
        }
        if (e instanceof OclSemanticBinder.BoundMethodCall m) {
            Object src = evalBound(m.source(), s, rt);
            return evalMethod(m.ast().methodName, src, m.arguments(), s, rt);
        }
        if (e instanceof OclSemanticBinder.BoundCollectionOperation c) {
            Object src = evalBound(c.source(), s, rt);
            return evalCollOp(c.ast().opName, src, c.arguments(), s, rt);
        }
        if (e instanceof OclSemanticBinder.BoundIterator it) {
            Object src = evalBound(it.source(), s, rt);
            return evalIter(it.ast().operation, rt.toList(src), it.ast().iteratorName, it.body(), s, rt);
        }
        throw new IllegalStateException(e.getClass().getName());
    }

    private Object evalIr(OclIr.Expression e, Map<String, Object> s, RT rt) {
        if (e instanceof OclIr.Variable v) {
            return s.get(v.name());
        }
        if (e instanceof OclIr.Literal l) {
            return l.value();
        }
        if (e instanceof OclIr.Not n) {
            return !toBool(evalIr(n.expression(), s, rt));
        }
        if (e instanceof OclIr.If i) {
            return toBool(evalIr(i.condition(), s, rt)) ? evalIr(i.thenBranch(), s, rt) : evalIr(i.elseBranch(), s, rt);
        }
        if (e instanceof OclIr.Let l) {
            var ns = new LinkedHashMap<>(s);
            ns.put(l.variableName(), evalIr(l.value(), s, rt));
            return evalIr(l.body(), ns, rt);
        }
        if (e instanceof OclIr.Binary b) {
            return evalBin(b.operator(), evalIr(b.left(), s, rt), evalIr(b.right(), s, rt));
        }
        if (e instanceof OclIr.AttributeAccess a) {
            return rt.attr(evalIr(a.source(), s, rt), a.attributeName());
        }
        if (e instanceof OclIr.NavigationAccess n) {
            return rt.nav(evalIr(n.source(), s, rt), n.navigation().roleName());
        }
        if (e instanceof OclIr.MethodCall m) {
            Object src = evalIr(m.source(), s, rt);
            List<Object> args = new ArrayList<>();
            for (var a : m.arguments()) {
                args.add(evalIr(a, s, rt));
            }
            return evalMethodDirect(m.methodName(), src, args, rt);
        }
        if (e instanceof OclIr.CollectionOperation c) {
            Object src = evalIr(c.source(), s, rt);
            List<Object> args = new ArrayList<>();
            for (var a : c.arguments()) {
                args.add(evalIr(a, s, rt));
            }
            return evalCollOpDirect(c.operationName(), rt.toList(src), args, rt);
        }
        if (e instanceof OclIr.IteratorOperation it) {
            return evalIter(it.operationName(), rt.toList(evalIr(it.source(), s, rt)),
                    it.iteratorName(), it.body(), s, rt);
        }
        if (e instanceof OclIr.NavigationPredicateCheck p) {
            List<?> values = rt.toList(rt.nav(evalIr(p.navigation().source(), s, rt),
                    p.navigation().navigation().roleName()));
            return switch (p.kind()) {
                case EXISTS ->
                    values.stream().anyMatch(v -> p.predicate() == null
                    || toBool(evalIr(p.predicate(), childScope(s, p.iteratorName(), v), rt)));
                case NOT_EXISTS ->
                    values.stream().noneMatch(v -> p.predicate() == null
                    || toBool(evalIr(p.predicate(), childScope(s, p.iteratorName(), v), rt)));
                case FORALL ->
                    values.stream().allMatch(v
                    -> toBool(evalIr(p.predicate(), childScope(s, p.iteratorName(), v), rt)));
            };
        }
        if (e instanceof OclIr.NavigationCountComparison c) {
            List<?> values = rt.toList(rt.nav(evalIr(c.navigation().source(), s, rt),
                    c.navigation().navigation().roleName()));
            long count = values.stream().filter(v -> c.predicate() == null
                    || toBool(evalIr(c.predicate(), childScope(s, c.iteratorName(), v), rt))).count();
            return evalBin(c.operator(), count, c.literal());
        }
        throw new IllegalStateException(e.getClass().getName());
    }

    private Map<String, Object> childScope(Map<String, Object> scope, String name, Object value) {
        var ns = new LinkedHashMap<>(scope);
        ns.put(name, value);
        return ns;
    }

    private Object evalIter(String op, List<?> vals, String iName, Object body, Map<String, Object> s, RT rt) {
        return switch (op.toLowerCase()) {
            case "forall" ->
                vals.stream().allMatch(v -> toBool(evalBody(body, iName, v, s, rt)));
            case "exists" ->
                vals.stream().anyMatch(v -> toBool(evalBody(body, iName, v, s, rt)));
            case "select" -> {
                List<Object> r = new ArrayList<>();
                for (var v : vals) {
                    if (toBool(evalBody(body, iName, v, s, rt))) {
                        r.add(v);

                    }
                }
                yield r;
            }
            case "collect" -> {
                List<Object> r = new ArrayList<>();
                for (var v : vals) {
                    r.add(evalBody(body, iName, v, s, rt));

                }
                yield r;
            }
            case "any" ->
                vals.stream().filter(v -> toBool(evalBody(body, iName, v, s, rt))).findFirst().orElse(null);
            case "one" ->
                vals.stream().filter(v -> toBool(evalBody(body, iName, v, s, rt))).count() == 1;
            default ->
                throw new IllegalStateException(op);
        };
    }

    private Object evalBody(Object body, String iName, Object val, Map<String, Object> s, RT rt) {
        var ns = new LinkedHashMap<>(s);
        ns.put(iName, val);
        if (body instanceof OclSemanticBinder.BoundExpression be) {
            return evalBound(be, ns, rt);
        }
        if (body instanceof OclIr.Expression ie) {
            return evalIr(ie, ns, rt);
        }
        throw new IllegalStateException();
    }

    private Object evalBin(String op, Object l, Object r) {
        Object ln = l instanceof Number n ? n.doubleValue() : l;
        Object rn = r instanceof Number n ? n.doubleValue() : r;
        return switch (op) {
            case "=" ->
                Objects.equals(ln, rn);
            case "<>" ->
                !Objects.equals(ln, rn);
            case "and" ->
                toBool(l) && toBool(r);
            case "or" ->
                toBool(l) || toBool(r);
            case "implies" ->
                !toBool(l) || toBool(r);
            case ">" ->
                ((Number) l).doubleValue() > ((Number) r).doubleValue();
            case "<" ->
                ((Number) l).doubleValue() < ((Number) r).doubleValue();
            case ">=" ->
                ((Number) l).doubleValue() >= ((Number) r).doubleValue();
            case "<=" ->
                ((Number) l).doubleValue() <= ((Number) r).doubleValue();
            case "+" ->
                ((Number) l).doubleValue() + ((Number) r).doubleValue();
            case "-" ->
                ((Number) l).doubleValue() - ((Number) r).doubleValue();
            case "*" ->
                ((Number) l).doubleValue() * ((Number) r).doubleValue();
            default ->
                throw new IllegalStateException(op);
        };
    }

    private Object evalMethod(String name, Object src, List<OclSemanticBinder.BoundExpression> args, Map<String, Object> s, RT rt) {
        List<Object> evaled = new ArrayList<>();
        for (var a : args) {
            evaled.add(evalBound(a, s, rt));
        }
        return evalMethodDirect(name, src, evaled, rt);
    }

    private Object evalCollOp(String name, Object src, List<OclSemanticBinder.BoundExpression> args, Map<String, Object> s, RT rt) {
        List<Object> evaled = new ArrayList<>();
        for (var a : args) {
            evaled.add(evalBound(a, s, rt));
        }
        return evalCollOpDirect(name, rt.toList(src), evaled, rt);
    }

    private Object evalMethodDirect(String name, Object src, List<Object> args, RT rt) {
        return switch (name.toLowerCase()) {
            case "isdefined" ->
                src != null;
            case "isundefined" ->
                src == null;
            default ->
                throw new IllegalStateException(name);
        };
    }

    private Object evalCollOpDirect(String name, List<?> vals, List<Object> args, RT rt) {
        return switch (name.toLowerCase()) {
            case "size" ->
                vals.size();
            case "isempty" ->
                vals.isEmpty();
            case "notempty" ->
                !vals.isEmpty();
            case "includes" ->
                vals.stream().anyMatch(v -> Objects.equals(v instanceof Number n ? n.doubleValue() : v,
                args.get(0) instanceof Number n ? n.doubleValue() : args.get(0)));
            case "excludes" ->
                vals.stream().noneMatch(v -> Objects.equals(v instanceof Number n ? n.doubleValue() : v,
                args.get(0) instanceof Number n ? n.doubleValue() : args.get(0)));
            default ->
                throw new IllegalStateException(name);
        };
    }

    // --- Test data classes ---
    static final class TestObj {

        final String id, className;
        final Map<String, Object> attributes = new LinkedHashMap<>();
        final Map<String, List<TestObj>> links = new LinkedHashMap<>();

        TestObj(String id, String cn) {
            this.id = id;
            this.className = cn;
        }

        TestObj attr(String n, Object v) {
            attributes.put(n, v);
            return this;
        }

        TestObj link(String r, TestObj... ts) {
            links.put(r, new ArrayList<>(List.of(ts)));
            return this;
        }
    }

    static TestObj obj(String id, String cn) {
        return new TestObj(id, cn);
    }

    static final class RT {

        final Map<String, List<TestObj>> byClass = new LinkedHashMap<>();

        RT(List<TestObj> objs) {
            for (var o : objs) {
                byClass.computeIfAbsent(o.className, k -> new ArrayList<>()).add(o);

            }
        }

        List<TestObj> all(String cn) {
            return byClass.getOrDefault(cn, List.of());
        }

        Object attr(Object src, String name) {
            if (src instanceof Collection<?> c) {
                var r = new ArrayList<>();
                for (var i : c) {
                    var v = attr(i, name);
                    if (v != null) {
                        r.add(v);

                    }
                }
                return r.size() == 1 ? r.get(0) : r;
            }
            return src instanceof TestObj o ? o.attributes.get(name) : null;
        }

        List<Object> nav(Object src, String role) {
            if (src instanceof Collection<?> c) {
                var r = new ArrayList<Object>();
                for (var i : c) {
                    r.addAll(nav(i, role));

                }
                return r;
            }
            return src instanceof TestObj o ? new ArrayList<>(o.links.getOrDefault(role, List.of())) : List.of();
        }

        List<?> toList(Object src) {
            if (src == null) {
                return List.of();
            }
            if (src instanceof List<?> l) {
                return l;
            }
            if (src instanceof Collection<?> c) {
                return new ArrayList<>(c);
            }
            return List.of(src);
        }
    }
}
