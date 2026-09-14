package org.uet.dse.neo4jtgg.experiment;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Session;
import org.neo4j.driver.Transaction;
import org.uet.dse.neo4j.manager.Neo4jDriverManager;
import org.uet.dse.neo4j.manager.SessionManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Executable server-side probes for the CYPHER5_val trust boundary. */
class Cypher5ValDialectRealNeo4jTest {
    @Test
    void selectedServerSatisfiesEveryRequiredDialectProbe() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("neo4j.dialect.it"),
                "Run with -Dneo4j.dialect.it=true and configured Neo4j");
        Neo4jEnvironmentConfig config = Neo4jEnvironmentConfig.load();
        assertEquals(Cypher5ValAssumptionMatrix.DATABASE, config.database(),
                "Evidence database drift; rerun and publish a new CY manifest intentionally");
        connect(config);
        try (Session session = Neo4jDriverManager.getInstance().openSession()) {
            var components = session.run("CALL dbms.components() YIELD name, versions, edition "
                    + "WHERE size(versions) > 0 RETURN name, versions[0] AS version, edition").list();
            assertFalse(components.isEmpty());
            var component = components.stream()
                    .filter(record -> record.get("name").asString().contains("Neo4j Kernel"))
                    .findFirst().orElse(components.get(0));
            assertFalse(component.get("name").asString().isBlank());
            assertFalse(component.get("version").asString().isBlank());
            assertEquals(Cypher5ValAssumptionMatrix.NEO4J_KERNEL,
                    component.get("version").asString(), "Neo4j Kernel evidence drift");
            assertEquals(Cypher5ValAssumptionMatrix.EDITION,
                    component.get("edition").asString().toLowerCase(Locale.ROOT), "Neo4j edition evidence drift");
            String driverDependencyVersion = pinnedDriverVersion();
            assertEquals(Cypher5ValAssumptionMatrix.JAVA_DRIVER_DEPENDENCY, driverDependencyVersion,
                    "Neo4j Java driver dependency drift");
            assertEquals(5L, session.run("CYPHER 5 RETURN 5 AS selectedVersion")
                    .single().get("selectedVersion").asLong());

            String probeRun = "cy5-" + System.currentTimeMillis();
            Map<String, Object> parameters = Cypher5ValAssumptionMatrix.parameters(probeRun);
            Map<String, String> observations = new LinkedHashMap<>();
            try (Transaction transaction = session.beginTransaction()) {
                transaction.run(Cypher5ValAssumptionMatrix.FIXTURE_SETUP, parameters).consume();
                for (Cypher5ValAssumptionMatrix.Probe probe : Cypher5ValAssumptionMatrix.probes()) {
                    String observed = probe.verify(transaction.run(probe.cypher(), parameters).list());
                    assertEquals(probe.expectedObservation(), observed, probe.id() + " observation drift");
                    observations.put(probe.id(), observed);
                    System.out.println("CYPHER5_VAL_ASSUMPTION=" + probe.id()
                            + " status=PASS observed=" + observed);
                }
                transaction.rollback();
            }
            assertEquals(Set.of("CY1", "CY2", "CY3", "CY4", "CY5", "CY6", "CY7", "CY8", "CY9"),
                    observations.keySet());
            System.out.println("CYPHER5_VAL_MATRIX=PASS assumptions=9/9 server="
                    + component.get("version").asString() + " edition="
                    + component.get("edition").asString() + " database=" + config.database()
                    + " driverDependency=" + driverDependencyVersion);
        } finally {
            Neo4jDriverManager manager = Neo4jDriverManager.getInstance();
            if (manager != null) manager.close();
        }
    }

    private String pinnedDriverVersion() throws Exception {
        Path[] candidates = {Path.of("neo4j", "pom.xml"), Path.of("..", "neo4j", "pom.xml")};
        Pattern dependency = Pattern.compile(
                "<artifactId>neo4j-java-driver</artifactId>\\s*<version>([^<]+)</version>");
        for (Path candidate : candidates) {
            if (!Files.isRegularFile(candidate)) continue;
            var match = dependency.matcher(Files.readString(candidate));
            if (match.find()) return match.group(1).trim();
        }
        throw new IllegalStateException("Cannot identify neo4j-java-driver version from neo4j/pom.xml");
    }

    private void connect(Neo4jEnvironmentConfig config) throws Exception {
        Neo4jDriverManager.connect(config.uri(), config.user(), config.password(), config.database(), false, false);
        SessionManager identity = new SessionManager();
        identity.createNewSession();
        Neo4jDriverManager.getInstance().setSessionManager(identity);
    }
}
