package scenarios.common;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DotEnvNeo4jConfig {
    private static final String ENV_NEO4J_URI = "NEO4J_URI";
    private static final String ENV_NEO4J_USER = "NEO4J_USER";
    private static final String ENV_NEO4J_PASSWORD = "NEO4J_PASSWORD";
    private static final String ENV_NEO4J_DB = "NEO4J_DB";
    private static final String ENV_NEO4J_CREATE = "NEO4J_CREATE_DB";
    private static final String ENV_NEO4J_DELETE = "NEO4J_DELETE_ON_EXIT";

    private final Map<String, String> values;

    private DotEnvNeo4jConfig(Map<String, String> values) {
        this.values = values;
    }

    public static DotEnvNeo4jConfig loadDefault() throws IOException {
        Path rootEnv = Path.of(".env");
        if (Files.exists(rootEnv)) {
            return new DotEnvNeo4jConfig(parse(rootEnv));
        }
        Path testEnv = Path.of("test", "scenarios", ".env");
        if (Files.exists(testEnv)) {
            return new DotEnvNeo4jConfig(parse(testEnv));
        }
        return new DotEnvNeo4jConfig(Map.of());
    }

    private static Map<String, String> parse(Path path) throws IOException {
        Map<String, String> values = new LinkedHashMap<>();
        List<String> lines = Files.readAllLines(path);
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int idx = line.indexOf('=');
            if (idx <= 0) {
                continue;
            }
            String key = line.substring(0, idx).trim();
            String value = line.substring(idx + 1).trim();
            if ((value.startsWith("\"") && value.endsWith("\""))
                    || (value.startsWith("'") && value.endsWith("'"))) {
                value = value.substring(1, value.length() - 1);
            }
            values.put(key, value);
        }
        return values;
    }

    public String requireUri() {
        return require(ENV_NEO4J_URI);
    }

    public String requireUser() {
        return require(ENV_NEO4J_USER);
    }

    public String requirePassword() {
        return require(ENV_NEO4J_PASSWORD);
    }

    public String database() {
        return read(ENV_NEO4J_DB, "neo4j");
    }

    public boolean createDatabase() {
        return Boolean.parseBoolean(read(ENV_NEO4J_CREATE, "false"));
    }

    public boolean deleteOnExit() {
        return Boolean.parseBoolean(read(ENV_NEO4J_DELETE, "false"));
    }

    private String require(String key) {
        String value = read(key, null);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required Neo4j config: " + key);
        }
        return value;
    }

    private String read(String key, String defaultValue) {
        String env = System.getenv(key);
        if (env != null && !env.isBlank()) {
            return env;
        }
        String prop = System.getProperty(key);
        if (prop != null && !prop.isBlank()) {
            return prop;
        }
        String value = values.get(key);
        return (value != null && !value.isBlank()) ? value : defaultValue;
    }
}
