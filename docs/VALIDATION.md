# Validation record

## Verified on the local Windows environment

- Java 17.0.10, Maven 3.9.15, Spring Boot 4.1.1.
- Integration suite: **29 tests, 0 failures, 0 errors, 0 skipped**.
- Final source compilation and executable JAR packaging succeeded.
- `node --check src/main/resources/static/app.js` passed.
- DOM behavior smoke check passed 10 checks against the packaged app: metrics, link creation/list, analytics rendering, Disable, Enable, eight workflow tasks, approval checkbox, safe stop/resume, manual prompt export, and manual analysis import.
- Packaged app returned 200 for health and static assets, 201 for link creation, 302 for redirect, and an incremented click count.
- A link and workflow created before a process restart remained present afterward in the H2 file database.

Stop the application before replacing its JAR on Windows to avoid file-lock conflicts during packaging.

## Suite coverage

1. HTTP create, redirect, no-store response, and UTC click aggregation.
2. Unsupported schemes, relative URLs, embedded credentials, and control characters.
3. Eight competing requests for one alias: one success, seven conflicts.
4. Disable and expiration failures do not record clicks; clear expiration restores behavior.
5. Exact expiration boundary is rejected.
6. Past expiration, reserved aliases, missing codes, and pagination limits.
7. Cross-origin and non-JSON mutations are rejected.
8. Health, static UI, and metrics endpoints.
9. Greenfield candidate compilation, six assertions, separate release approval, and promotion.
10. Brownfield baseline preservation and nine candidate assertions including expiration.
11. Ambiguous requirement pauses and never silently substitutes a canned implementation.
12. Manual analysis, planning, and implementation imports.
13. Invalid/stale handoff JSON cannot advance state.
14. Stale hash/revision approvals are rejected; rejection prevents execution.
15. Disk source tampering invalidates approval.
16. Stop/resume requires renewed approval; requirement revision archives prior state.
17. Three controlled validation failures produce two repair opportunities and a safe stop.
18. Interrupted execution recovery clears approval and does not replay code.
19. Path traversal and missing source files are rejected.
20. Audit modification fails hash-chain verification.
21. Repository reload preserves source hash and optimistic version.
22. Release-only assertion failure retains the previous release pointer.
23. Both independent branches start before either completion is required.
24. A crash between analysis save and clarification gate cannot skip questions.
25. Repeated archive reconciliation does not prevent a new revision.
26. HTTP Disable/Enable accepts an active-only PATCH and preserves the existing expiration; disabled redirects return 410 without adding a click.
27. HTTP expiration-only PATCH and clear-only PATCH accept omitted unrelated fields.
28. HTTP resume restores WAITING_FOR_APPROVAL status without executing code or retaining execution approval.
29. Resuming a manual handoff restores WAITING_FOR_INPUT status.

## Final packaged scenario checks

`scripts/scenario-smoke.cjs` verifies the following against an isolated instance of the packaged JAR using actual HTTP requests. It approves only the reviewed authored Brownfield fixture, never arbitrary imported AI code. The saved result is `docs/validation/scenario-results.json`.

| Scenario | Verified outcome |
| --- | --- |
| Brownfield | Baseline differs from current implementation and tests; validation and release each pass 9 assertions; COMPLETED |
| Ambiguous | Questions block planning; clarification is recorded; IMPLEMENT waits for a manual response without staging canned code |
| Manual ChatGPT handoff | ANALYZE waits for input and exports a prompt with a JSON contract and revision binding |
| Reject approval | REJECTED, zero validation attempts, no validation-start audit event |
| Safe stop / Resume | STOPPED then WAITING_FOR_APPROVAL, unchanged candidate hash, zero validation attempts, renewed approval required |

Each scenario's exported audit chain must pass verification. The tests use a separate in-memory database and workspace, leaving the user's existing runs untouched.

To reproduce, start the JAR on port 8081 with a separate database/workspace, then set `WORKBENCH_URL=http://127.0.0.1:8081` and run `node scripts/scenario-smoke.cjs`. The script creates scenario runs and records actual evidence under `target/scenario-results.json` by default.

## Reproduction

```powershell
# Stop the running application first to release the JAR on Windows.
mvn clean verify
java -jar target/engineering-workbench-1.0.0.jar
```

Test reports live in `target/surefire-reports/`. The small saved summary is in `docs/validation/integration-summary.txt`.

Optional DOM check while the application runs:

```powershell
npm.cmd install --prefix tmp/ui-check jsdom@26 --no-audit --no-fund
node scripts/ui-smoke.cjs
```

This optional test adds labeled local review links/workflows but never approves code execution. It simulates DOM behavior, not pixel layout. The current environment did not expose an automated browser, so a rendered browser screenshot review has not been performed.

## Not verified or not claimed

- PostgreSQL and Docker runtime profiles. Configuration is supplied, but Docker was not installed or required for the verified default.
- Production security, distributed scheduling, externally anchored audit retention, throughput, or availability.
- Automatic understanding of arbitrary requirements. Manual responses and semantic test adequacy remain human responsibilities.
- Autonomous LLM generation, per-task OS sandboxing, cloud deployment, or rollback of arbitrary external effects.
- A code coverage percentage or regulatory compliance certification.
