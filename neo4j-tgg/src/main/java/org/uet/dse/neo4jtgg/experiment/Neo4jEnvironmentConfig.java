package org.uet.dse.neo4jtgg.experiment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Loads Neo4j defaults without introducing a dotenv runtime dependency. */
public record Neo4jEnvironmentConfig(String uri, String database, String user,
                                     String password, Path sourceFile) {
    private static final String URI = "NEO4J_URI";
    private static final String DATABASE = "NEO4J_DB";
    private static final String USER = "NEO4J_USER";
    private static final String PASSWORD = "NEO4J_PASSWORD";

    public static Neo4jEnvironmentConfig load() {
        Path envFile = findEnvFile();
        Map<String, String> fileValues = envFile == null ? Map.of() : parse(envFile);
        return new Neo4jEnvironmentConfig(
                value(URI, fileValues, "bolt://localhost:7687"),
                value(DATABASE, fileValues, "neo4j"),
                value(USER, fileValues, "neo4j"),
                value(PASSWORD, fileValues, ""),
                envFile);
    }

    static Map<String, String> parse(Path file) {
        Map<String, String> values = new LinkedHashMap<>();
        try {
            for (String sourceLine : Files.readAllLines(file)) {
                String line = sourceLine.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (line.startsWith("export ")) line = line.substring(7).trim();
                int separator = line.indexOf('=');
                if (separator <= 0) continue;
                String key = line.substring(0, separator).trim();
                String raw = line.substring(separator + 1).trim();
                values.put(key, unquote(raw));
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read Neo4j environment file: " + file, exception);
        }
        return Map.copyOf(values);
    }

    private static String value(String key, Map<String, String> fileValues, String fallback) {
        String systemProperty = System.getProperty(key);
        if (present(systemProperty)) return systemProperty.trim();
        String environment = System.getenv(key);
        if (present(environment)) return environment.trim();
        String file = fileValues.get(key);
        return present(file) ? file.trim() : fallback;
    }

    private static Path findEnvFile() {
        String explicit = System.getProperty("neo4j.env.file");
        if (present(explicit)) {
            Path path = Path.of(explicit).toAbsolutePath().normalize();
            return Files.isRegularFile(path) ? path : null;
        }
        Path current = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        for (int depth = 0; current != null && depth < 6; depth++, current = current.getParent()) {
            Path candidate = current.resolve(".env");
            if (Files.isRegularFile(candidate)) return candidate;
        }
        return null;
    }

    private static String unquote(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '\'' && last == '\'') || (first == '"' && last == '"')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }
}
