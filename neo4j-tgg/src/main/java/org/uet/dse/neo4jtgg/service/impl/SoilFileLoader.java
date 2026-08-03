package org.uet.dse.neo4jtgg.service.impl;

import org.tzi.use.parser.soil.SoilCompiler;
import org.tzi.use.uml.sys.MSystem;
import org.tzi.use.uml.sys.soil.MStatement;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Loads USE shell-style {@code .soil} files through the SOIL statement compiler. */
public final class SoilFileLoader {
    public void load(MSystem system, String source, String sourceName) throws Exception {
        Objects.requireNonNull(system, "system");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(sourceName, "sourceName");
        List<String> commands = commands(source);
        for (int index = 0; index < commands.size(); index++) {
            String command = normalizeShellCommand(commands.get(index));
            StringWriter diagnostics = new StringWriter();
            MStatement statement = SoilCompiler.compileStatement(system.model(), system.state(),
                    system.getVariableEnvironment(), command,
                    sourceName + "#command-" + (index + 1),
                    new PrintWriter(diagnostics, true), false);
            if (statement == null) {
                throw new IllegalArgumentException("Invalid SOIL command " + (index + 1)
                        + " in " + sourceName + ": " + command + System.lineSeparator() + diagnostics);
            }
            system.execute(statement);
        }
    }

    static List<String> commands(String source) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : source.lines().toList()) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("--")) continue;
            if (trimmed.startsWith("!")) {
                flush(result, current);
                current.append(trimmed.substring(1).trim());
            } else {
                if (!current.isEmpty()) current.append(' ');
                current.append(trimmed);
            }
        }
        flush(result, current);
        return List.copyOf(result);
    }

    private static String normalizeShellCommand(String command) {
        // USE shell accepts "!set o.a := v" while the SOIL parser expects "o.a := v".
        return command.startsWith("set ") ? command.substring(4).trim() : command;
    }

    private static void flush(List<String> target, StringBuilder command) {
        if (!command.isEmpty()) target.add(command.toString());
        command.setLength(0);
    }
}
