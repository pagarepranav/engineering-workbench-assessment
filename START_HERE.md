# Pranav Pagare assessment submission

This submission contains a runnable Java/Spring Boot URL shortener and a governed SDLC workflow prototype.

For the complete launch instructions and all verified scenario walkthroughs, read **[start.md](start.md)**.

## Open the application

1. Extract the entire ZIP.
2. Install or use a full JDK 17 or newer. A JDK is needed for the workflow's Java compilation step.
3. Open a terminal in the extracted project folder and run:

```powershell
java -jar app/engineering-workbench-1.0.0.jar
```

4. Open **http://127.0.0.1:8080**.

The packaged application needs no Maven, Docker, database server, or AI API key to start. It creates a persistent local H2 database under `data/`. Maven and dependency-download access are needed only to rebuild or run the source test suite. Use Ctrl+C in the terminal to stop the application.

## Suggested review order

1. **URL shortener:** create a link, follow it, inspect analytics, disable it, and check alias conflicts.
2. **SDLC workflows:** select Authored demonstration and Greenfield. Review source and test files before approving local execution. Inspect actual compiler and assertion output, then approve the local artifact release separately.
3. **Brownfield:** inspect the expiration change against its retained baseline.
4. **Ambiguous:** record clarification and inspect the manual ChatGPT handoff.
5. Review [architecture](docs/ARCHITECTURE.md), [scenario instructions](docs/SCENARIOS.md), and [test evidence](docs/VALIDATION.md).

## AI execution approach

This version uses manual ChatGPT prompt/response handoffs and explicitly labeled authored demonstration fixtures. It does not call an LLM API or claim autonomous model reasoning. The orchestration, persistence, validation tools, approvals, retries, audit records, and local artifact release behavior execute in the application.

This is a deliberate scope limitation against the assessment's autonomous AI objective. A provider interface separates manual handoff from the workflow so a live provider can be added later.

Generated/imported Java runs with the local OS user's permissions after explicit human review. It is not OS sandboxed. The main URL service is persistent; the smaller candidate service staged by the demonstration uses in-memory storage.

## Included material

- `app/engineering-workbench-1.0.0.jar`: prebuilt executable.
- `src/` and `pom.xml`: complete application source and integration tests.
- `README.md`: setup, features, optional PostgreSQL/Docker configuration, and limitations.
- `docs/`: architecture, API, scenarios, engineering summary, presentation outline, and validation evidence.
- `scripts/`: optional local build/run and DOM smoke-check helpers.
- `SHA256SUMS.txt`: checksums of the packaged files.

Validation: **29 integration tests passed with zero failures or errors**, plus ten passing DOM behavior checks, including Disable and Enable. Five additional scenario checks verify Brownfield, Ambiguous, Manual ChatGPT handoff, Reject approval, and Safe stop / Resume. Docker/PostgreSQL runtime validation and a rendered browser screenshot review are outside the verified scope. See the validation record for precise boundaries.

Author: Pranav Pagare
