# Runtime recapture audit — 2026-08-21

Status: `HISTORICAL_BLOCKED_RUN_SUPERSEDED`

The blocking behavior documented below is historical. A later dirty-worktree
recapture completed successfully; see
[`runtime-recapture-2026-08-22.tsv`](runtime-recapture-2026-08-22.tsv). The new
audit does not yet replace the clean, source-hashed publication manifests.

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

## Second attempt after the database became reachable

Source revision: `34e340753bec9cd823b383b885282ac06d1de06c`

The database became reachable and the following selected-runtime checks
completed successfully on Neo4j Kernel `2026.06.0`, Enterprise edition,
database `demo`:

- `Cypher5ValDialectRealNeo4jTest`: PASS, CY1--CY9 = 9/9;
- `AdapterAdequacyCertificateRealNeo4jTest`: PASS, 1/1.

The complete evidence capture still could not be certified:

- the 47-case differential suite first lost the `demo` writer and, after the
  database recovered, exceeded a ten-minute execution limit;
- `FamiliesToPersonsCaseStudyRealNeo4jTest` reached
  `PersonRegister::NoBlankNames`, where Neo4j returned
  `TransientException: Java heap space`; the following cleanup/case then lost
  the WRITE server;
- `OclValSemanticDiscriminatorRealNeo4jTest` reached
  `Company::CollectUsesSetImage`, where Neo4j returned
  `DatabaseException: Unexpected expression`;
- the active Neo4j configuration used an initial heap of `512m` and maximum
  heap of `1G`; the server debug/query logs record an
  `OutOfMemoryError: Java heap space` for the generated validation query.

The PASS manifests remain unchanged. A valid recapture requires restarting
`demo` with a larger heap and rerunning the differential and discriminator
suites. The `Unexpected expression` result must then be reproduced on the
restarted server before it is classified as a renderer/runtime-profile defect.
