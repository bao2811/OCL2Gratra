package org.uet.dse.ocl2cypher.caseStudy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import org.uet.dse.ocl2cypher.runtime.OclType;
import org.uet.dse.ocl2cypher.runtime.OclValue;
import org.uet.dse.ocl2cypher.source.model.*;

/** Data-only USE/SOIL fixture reader. Operations and embedded constraints are not
 * compiled here: replay requests come from the separate invariant file.
 * N-ary tuples are preserved, never flattened into binary links. */
final class CarRentalFixture {
    record Executable(SchemaModel schema, Snapshot snapshot) {}
    record End(String className, String role, int lower, int upper) {}
    record Association(String name, List<End> ends) {
        Association { ends = List.copyOf(ends); }
    }
    record Tuple(String association, List<String> objects) {
        Tuple { objects = List.copyOf(objects); }
    }
    // declarations contains only scalar attributes; objects also retains archival
    // collection slots. Neither is a complete executable schema/snapshot pair.
    record Loaded(SchemaModel declarations, Snapshot objects,
                  Map<String, Association> associations, List<Tuple> tuples,
                  Map<String, OclType> sourceAttributeTypes) {
        Loaded {
            associations = Collections.unmodifiableMap(new LinkedHashMap<>(associations));
            tuples = List.copyOf(tuples);
            sourceAttributeTypes = Map.copyOf(sourceAttributeTypes);
        }

        OclType attributeType(String receiver, String name) throws IOException {
            return resolveAttributeType(declarations, sourceAttributeTypes, receiver, name);
        }

        /** Complete binary/scalar data conversion, not an admission certificate. */
        Executable toExecutable() throws IOException {
            requireBinaryExecutionSupport();
            for (var entry : sourceAttributeTypes.entrySet())
                if (entry.getValue().isCollection())
                    throw new IOException("CASE_STUDY_SCALAR_ATTRIBUTES_REQUIRED: " + entry.getKey());
            var schema = SchemaModel.builder(declarations.modelKey());
            declarations.classes().forEach(schema::clazz);
            declarations.attributes().forEach(schema::attribute);
            for (Association a : associations.values()) {
                End s = a.ends().get(0), t = a.ends().get(1);
                schema.association(new UmlAssociation(a.name(), a.name(),
                        s.className(), s.role(), s.lower(), s.upper(),
                        t.className(), t.role(), t.lower(), t.upper(), List.of(), false, true));
            }
            var snapshot = Snapshot.builder();
            for (var o : objects.objects()) {
                snapshot.object(o.stableId(), o.dynamicClassKey());
                objects.attributeSlots(o.stableId()).forEach((name, value) -> snapshot.attribute(o.stableId(), name, value));
            }
            for (Tuple tuple : tuples)
                snapshot.link(tuple.association(), tuple.objects().get(0), tuple.objects().get(1));
            return new Executable(schema.build(), snapshot.build());
        }

        /** Fail before exposing an incomplete snapshot to any evaluator. */
        void requireBinaryExecutionSupport() throws IOException {
            for (Association a : associations.values()) {
                if (a.ends().size() != 2)
                    throw new IOException("CASE_STUDY_NARY_UNSUPPORTED: " + a.name()
                            + " has " + a.ends().size() + " ends; tuples retained, replay not executed");
            }
        }
    }

    static Loaded read(Path model, Path soil) throws IOException {
        try {
            return readData(model, soil);
        } catch (IllegalArgumentException e) {
            throw new IOException(model.getFileName() + ": invalid model data: " + e.getMessage(), e);
        }
    }

    private static Loaded readData(Path model, Path soil) throws IOException {
        var builder = SchemaModel.builder("carrental");
        // Archival source declarations are not executable scalar observers.
        Map<String, OclType> sourceAttributeTypes = new LinkedHashMap<>();
        Map<String, Association> associations = new LinkedHashMap<>();
        String owner = null, association = null;
        boolean attributes = false, operations = false;
        List<End> ends = new ArrayList<>();
        for (String raw : Files.readAllLines(model)) {
            String line = raw.split("--", 2)[0].trim();
            if (line.isEmpty()) continue;
            if (line.equals("constraints")) break;
            if (line.startsWith("model ")) continue;
            var cls = Pattern.compile("(abstract\\s+)?class\\s+(\\w+)(?:\\s*<\\s*(\\w+))?").matcher(line);
            if (cls.matches()) {
                owner = cls.group(2);
                builder.clazz(new UmlClass(owner, owner, cls.group(1) != null, false,
                        cls.group(3) == null ? List.of() : List.of(cls.group(3))));
                continue;
            }
            if (line.equals("end")) {
                if (association != null) {
                    if (ends.size() < 2 || associations.putIfAbsent(association,
                            new Association(association, ends)) != null)
                        throw new IOException("Invalid or duplicate association: " + association);
                }
                owner = null; association = null; ends = new ArrayList<>();
                attributes = false; operations = false;
                continue;
            }
            if (owner != null && line.equals("attributes")) { attributes = true; continue; }
            if (owner != null && line.equals("operations")) { attributes = false; operations = true; continue; }
            if (owner != null && operations) continue;
            if (owner != null && attributes) {
                var attr = Pattern.compile("(\\w+)\\s*:\\s*(String|Integer|Real|Boolean|Set\\(String\\))").matcher(line);
                if (!attr.matches()) throw new IOException("Unsupported attribute declaration: " + line);
                OclType type = switch (attr.group(2)) {
                    case "String" -> OclType.STRING;
                    case "Integer" -> OclType.INTEGER;
                    case "Real" -> OclType.REAL;
                    case "Boolean" -> OclType.BOOLEAN;
                    default -> OclType.set(OclType.STRING);
                };
                String key = owner + "::" + attr.group(1);
                if (sourceAttributeTypes.putIfAbsent(key, type) != null)
                    throw new IOException("Duplicate attribute declaration: " + key);
                if (!type.isCollection()) builder.attribute(UmlAttribute.of(owner, attr.group(1), type));
                continue;
            }
            var assoc = Pattern.compile("association\\s+(\\w+)\\s+between").matcher(line);
            if (assoc.matches()) { association = assoc.group(1); continue; }
            var end = Pattern.compile("(\\w+)\\[(\\*|[0-9]+(?:\\.\\.[0-9]+)?)\\](?:\\s+role\\s+(\\w+))?").matcher(line);
            if (association != null && end.matches()) {
                String name = end.group(1), mult = end.group(2);
                String[] bounds = mult.split("\\.\\.");
                int lower = mult.equals("*") ? 0 : Integer.parseInt(bounds[0]);
                int upper = mult.equals("*") ? -1 : Integer.parseInt(bounds[bounds.length - 1]);
                ends.add(new End(name, end.group(3) == null
                        ? Character.toLowerCase(name.charAt(0)) + name.substring(1) : end.group(3), lower, upper));
                continue;
            }
            throw new IOException("Unsupported model data declaration: " + line);
        }
        if (owner != null || association != null) throw new IOException("Unclosed model declaration");
        SchemaModel declarations = builder.build();
        for (UmlClass c : declarations.classes()) for (String parent : c.directSuperclassKeys())
            if (!declarations.hasClass(parent)) throw new IOException("Unknown superclass: " + parent);
        if (declarations.hasInheritanceCycle()) throw new IOException("Cyclic fixture inheritance");
        for (Association a : associations.values()) for (End e : a.ends())
            if (!declarations.hasClass(e.className())) throw new IOException("Unknown association class: " + e.className());
        Snapshot.Builder objects = Snapshot.builder();
        Map<String, String> classes = new LinkedHashMap<>();
        List<Tuple> tuples = new ArrayList<>();
        int number = 0;
        for (String raw : Files.readAllLines(soil)) {
            number++;
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("--")) continue;
            try {
                var create = Pattern.compile("!new\\s+(\\w+)\\('([^']+)'\\)").matcher(line);
                if (create.matches()) {
                    String type = create.group(1), id = create.group(2);
                    if (!declarations.hasClass(type) || declarations.clazz(type).isAbstract())
                        throw new IOException("Unknown or abstract object class: " + type);
                    objects.object(id, type); classes.put(id, type); continue;
                }
                var set = Pattern.compile("!set\\s+(\\w+)\\.(\\w+)\\s*:=\\s*(.+)").matcher(line);
                if (set.matches()) {
                    String id = set.group(1), name = set.group(2);
                    if (!classes.containsKey(id)) throw new IOException("Unknown attribute owner: " + id);
                    var type = resolveAttributeType(declarations, sourceAttributeTypes, classes.get(id), name);
                    if (type == null) throw new IOException("Unknown attribute: " + id + "." + name);
                    objects.attribute(id, name, literal(set.group(3), type)); continue;
                }
                var insert = Pattern.compile("!insert\\s*\\(([^)]+)\\)\\s+into\\s+(\\w+)").matcher(line);
                if (insert.matches()) {
                    Association a = associations.get(insert.group(2));
                    List<String> ids = Arrays.stream(insert.group(1).split(",", -1)).map(String::trim).toList();
                    if (a == null || ids.size() != a.ends().size()) throw new IOException("Unknown association or wrong tuple arity");
                    for (int i = 0; i < ids.size(); i++) {
                        String type = classes.get(ids.get(i));
                        if (type == null || !declarations.conforms(type, a.ends().get(i).className()))
                            throw new IOException("Missing or mistyped tuple endpoint: " + ids.get(i));
                    }
                    Tuple tuple = new Tuple(a.name(), ids);
                    if (tuples.contains(tuple)) throw new IOException("Duplicate tuple: " + a.name());
                    tuples.add(tuple); continue;
                }
                throw new IOException("Unsupported SOIL command");
            } catch (IllegalArgumentException | IOException e) {
                throw new IOException(soil.getFileName() + ":" + number + ": " + e.getMessage(), e);
            }
        }
        return new Loaded(declarations, objects.build(), associations, tuples, sourceAttributeTypes);
    }

    /** This fixture grammar admits single inheritance; resolve nearest owner. */
    private static OclType resolveAttributeType(SchemaModel schema, Map<String, OclType> types,
                                                String receiver, String name) throws IOException {
        Set<String> visited = new HashSet<>();
        while (receiver != null) {
            if (!visited.add(receiver)) throw new IOException("Cyclic fixture inheritance: " + receiver);
            OclType type = types.get(receiver + "::" + name);
            if (type != null) return type;
            UmlClass c = schema.clazz(receiver);
            if (c == null) throw new IOException("Unknown receiver class: " + receiver);
            var parents = c.directSuperclassKeys();
            if (parents.size() > 1) throw new IOException("Multiple inheritance is outside this fixture reader");
            receiver = parents.isEmpty() ? null : parents.get(0);
        }
        return null;
    }

    private static OclValue literal(String token, OclType type) throws IOException {
        if (!type.equals(OclType.set(OclType.STRING))) return CaseStudyReplayer.decodeScalar(token, type);
        if (!token.matches("Set\\{\\s*(?:'[^'\\\\]*'\\s*(?:,\\s*'[^'\\\\]*'\\s*)*)?\\}"))
            throw new IOException("Unsupported Set(String) literal");
        List<OclValue> members = new ArrayList<>();
        var matcher = Pattern.compile("'[^']*'").matcher(token);
        while (matcher.find()) members.add(CaseStudyReplayer.decodeScalar(matcher.group(), OclType.STRING));
        return new OclValue.SetValue(type, members);
    }
}
