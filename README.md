# Engineering Workbench

A locally runnable URL shortener and governed SDLC workflow, built with Java 17 and Spring Boot. No AI API key is required.

The application supports a **manual ChatGPT handoff**: export a stage prompt, use your existing assistant, review its JSON response, and import it. An **authored demonstration mode** stages repeatable fixtures. The application never claims these fixtures are autonomous AI output.

For the prebuilt submission and the complete reviewer walkthrough, begin with **[start.md](start.md)**.

## Quick start

Requirements: a full JDK 17 or newer (`java` and `javac`), Maven 3.6.3 or newer, and network access for the first dependency download. Docker is optional.

From the project directory:

```powershell
mvn clean verify
java -jar target/engineering-workbench-1.0.0.jar
```

Open **http://127.0.0.1:8080**. Use Ctrl+C in the terminal to stop. The default database and workspaces persist in `data/`. A second process cannot open the same H2 file database. To use another port, pass `--server.port=8081` to the Java command.

Windows helpers:

```powershell
.\scripts\run.ps1
.\scripts\test.ps1
```

These scripts use a project-local Maven cache and leave your global Maven settings unchanged. If PowerShell's script policy prevents running them, use the direct Maven and Java commands above. No policy change is necessary.

## Reviewer walkthrough

1. Open **URL shortener**, create a destination and alias, follow the short URL, and inspect UTC daily analytics. Try a conflicting alias, disable a link, and check its 410 response.
2. Open **SDLC workflows**, create an authored Greenfield demonstration, and review the candidate files. These are JDK-only Java sources staged in an isolated directory.
3. Check the explicit review checkbox, enter a reason, and approve **execution**. The tool really compiles Java and executes `CandidateTest` with bounded process timeouts. Inspect command output and exit codes.
4. Approve **release** separately. The tool copies files into a local release directory, reruns tests there, and promotes a local release pointer only after successful validation. It does not deploy a public service.
5. Try **Brownfield** to inspect an expiration change against a retained baseline. Try **Ambiguous** to record clarification and then import a real manual implementation. Ambiguous demo mode deliberately does not substitute a canned implementation for your answer.
6. Export evidence, revise a requirement, or stop/resume. A revision invalidates prior approvals and archives its predecessor.

See [scenario walkthroughs](docs/SCENARIOS.md) for complete manual-mode instructions and JSON response contracts.

## What is implemented

| Area | Behavior |
| --- | --- |
| URL service | Persistent mappings, random codes, aliases, expiration, enable/disable, pagination, HTTP 302 redirects, UTC daily analytics |
| Workflow | Explicit dependency graph, persistent snapshots and revisions, independent worker branches, clarification, manual handoffs, bounded repair, stop/resume, restart recovery |
| Governance | Human execution/release gates, revision/hash-bound approvals, fixed tool commands, filename allowlist, source integrity checks, local release rollback |
| Evidence | Actual compilation/assertion output, attempts, timestamps, exported source/baseline, hash-chained audit events, measured workflow counters |
| Interface | Local review dashboard, URL manager, task/dependency cards, source inspection, approvals, prompt import/export, audit view |

The primary Spring Boot application uses database persistence. The generated/staged JDK candidate is intentionally a smaller in-memory URL service. Both are runnable, with different documented scopes.

## Tests

```powershell
mvn verify
```

Tests launch the actual Spring Boot HTTP server on a random loopback port and use an isolated H2 database. They also compile and execute trusted authored candidate files. Coverage includes concurrent aliases, redirects/analytics, expiration boundaries, cross-origin mutations, manual JSON handoff, stale and rejected approvals, changed source files, parallel branch synchronization, revision invalidation, restart recovery, retry exhaustion, audit tampering, and release rollback.

Reports: `target/surefire-reports/`. See [validation notes](docs/VALIDATION.md). No code-coverage percentage is claimed.

## Optional PostgreSQL and Docker

For an existing PostgreSQL installation:

```powershell
$env:DB_URL='jdbc:postgresql://127.0.0.1:5432/workbench'
$env:DB_USER='workbench'
$env:DB_PASSWORD='your-local-password'
java -jar target/engineering-workbench-1.0.0.jar --spring.profiles.active=postgres
```

The database must already exist. Tables initialize on startup using idempotent schema version 1 SQL. Future schema changes need numbered migrations; this prototype does not claim an automated migration/rollback system.

Alternatively, set `DB_PASSWORD` locally and run:

```powershell
docker compose up --build
```

Compose publishes the application only on `127.0.0.1:8080`. It supplies PostgreSQL and a full JDK for candidate compilation. The optional PostgreSQL/container configuration has not been runtime-validated in the current environment.

## Boundaries and engineering tradeoffs

- Single local user. Loopback binding, Host/Origin checks, JSON-only mutations, and browser security headers reduce accidental exposure. There is no multi-user authentication or authorization system.
- Generated Java executes with the local process's OS permissions after explicit human review. Filename checks, timeouts, stripped environment variables, and fixed commands are **not an OS sandbox**. Run only reviewed code. Container isolation is a future hardening step; merely putting the entire application in Docker is not per-task isolation.
- No autonomous LLM calls. Manual mode requires a human to move prompts/responses. Demo planning and code are authored fixtures. This only partially satisfies the assignment's autonomous reasoning requirement.
- A small fixed SDLC graph supports stateful branching and repair loops. A revised root requirement rebuilds the graph and invalidates all descendants. The system does not generate arbitrary executable DAGs from model output.
- The scheduler is for a single application instance. It serializes state transitions, uses database optimistic versions, and recovers RUNNING tasks after restart. It is not a distributed lease service.
- Filesystem promotion and database completion are separate operations. A crash between them can leave a valid release pointer with a run awaiting renewed approval. Recovery never automatically re-executes code.
- Click logging is synchronous. If database logging fails, the redirect fails instead of silently losing analytics. URLs are never fetched by the backend. Raw client IPs are not collected.
- Audit hashes detect modifications to existing events but are not externally anchored. A privileged actor could rewrite the chain or remove its tail.
- Local release rollback keeps the prior artifact pointer. It does not roll back arbitrary database changes or undo external effects from generated code.
- Metrics describe this small local sample. They are not production SLO/MTTR evidence.

## Project map

```text
src/main/java/dev/pranav/workbench/
  links/        URL API and transactional click logging
  workflow/     Explicit state graph, provider contract, persistence and audit
  execution/    Candidate workspaces and fixed Java tool commands
  common/       Local request policy, errors, health
src/main/resources/
  static/       Review UI and API specification
  db/           Schema version 1
src/test/       HTTP/database/workflow integration suite
docs/           Architecture, scenarios, validation, presentation outline
scripts/        Local build and run helpers
```

Read [architecture](docs/ARCHITECTURE.md), [API reference](docs/API.md), and [engineering summary](docs/ENGINEERING_SUMMARY.md). The initial slide outline is in [docs/presentation-outline.md](docs/presentation-outline.md).
