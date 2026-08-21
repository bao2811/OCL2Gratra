# Runtime recapture audit — 2026-08-21

Status: `BLOCKED_BY_ENVIRONMENT`

This file is an audit record, not a passing runtime manifest and not evidence
that any open runtime proof obligation has been discharged.

The selected real-Neo4j suites were enabled explicitly:

```text
Cypher5ValDialectRealNeo4jTest
OclValRealNeo4jCoverageTest
AdapterAdequacyCertificateRealNeo4jTest
OclValSemanticDiscriminatorRealNeo4jTest
FamiliesToPersonsCaseStudyRealNeo4jTest
```

Surefire started seven test cases. One non-database fixture passed; the six
database-dependent cases ended with
`org.neo4j.driver.exceptions.ServiceUnavailableException` while
`Neo4jDriverManager.verifyConnectivity` attempted to connect. There were no
assertion failures from a completed Neo4j semantic comparison.

Consequences:

- the checked-in PASS manifests were not rewritten;
- `PO-15`, `PO-16`, `PO-21`, `PO-22`, and `PO-23` remain `partial`;
- recapture requires the pinned Neo4j runtime to be running and reachable;
- source hashes and PASS observations must be generated only after that clean
  runtime execution succeeds.
