# Pranav Pagare assessment project

This project contains a runnable URL shortener and a governed SDLC workflow prototype built with Java 17 and Spring Boot.

## Start from a GitHub checkout

The Git repository contains source code and verification evidence. Generated JARs, local data, caches, and ZIPs are excluded. After cloning, open a terminal in the repository folder and use JDK 17+ and Maven:

```powershell
mvn clean verify
java -jar target/engineering-workbench-1.0.0.jar
```

Then open **http://127.0.0.1:8080**. The prebuilt-ZIP instructions below apply when using the separately packaged submission.

## Start the packaged application

1. Download and extract `Pranav_Pagare_Assessment.zip` completely.
2. Open a terminal in the extracted `Pranav_Pagare_Assessment` folder, which contains this file and the `app` folder.
3. Use a full JDK 17 or newer. Check `java -version` and `javac -version`.
4. Run:

```powershell
java -jar app/engineering-workbench-1.0.0.jar
```

5. Open **http://127.0.0.1:8080**.

No Maven, Docker, external database server, or AI API key is needed to start the packaged app. The workflow compiles Java, so a full JDK is required. The application creates its persistent local database and workspaces in `data/`. Use Ctrl+C in the terminal to stop it.

Run only one instance against the same `data/` directory. If H2 reports that its file is locked, stop the other instance; do not delete the database. If port 8080 is occupied, stop the other app or append `--server.port=8082` and open that port instead.

## First check the URL application

1. Select **URL shortener**.
2. Enter destination `https://example.com`, choose an unused alias such as `review-demo`, and leave expiration empty for this check.
3. Click **Create link** and open its short URL. It should redirect to the destination.
4. Scroll down to **Your links**. On your link's row, click **Analytics** under Actions. The panel above should show the recorded click. Analytics initially waits for you to select a link; use **Refresh analytics** after another redirect.
5. Click **Disable**. Opening that short URL now returns 410 and does not add a click. Click **Enable** to restore redirects.
6. Try creating the same alias again. Expect an alias conflict.

## Greenfield workflow

1. Open **SDLC workflows** and click **+ New workflow**.
2. Choose **Authored demonstration** and **Greenfield**. Keep the suggested requirement and create the run.
3. ANALYZE, PLAN, IMPLEMENT, TEST_SPEC, and DOC_DRAFT should succeed. VALIDATE waits for your approval.
4. Below the task cards, expand the candidate Java files, including `CandidateTest.java`, and review them.
5. At **Human review before local code execution**, check the review checkbox and enter a reason, such as `Reviewed demonstration source and tests`. Click **Approve execution**.
6. Expand **VALIDATE / Output and evidence**. Expect `PASS: 6 candidate assertions` and exit code 0 for compilation and test execution.
7. Review the separate release approval. Check its checkbox, record a reason, and click **Approve release**.
8. The run should become **COMPLETED**. Release evidence repeats the six assertions. Expand the audit trail and click **Export evidence**.

## Remaining assessment scenarios

Each scenario uses a new workflow so its history remains separate.

### Brownfield

Choose **Authored demonstration** and **Brownfield / add expiration**. Keep the suggested requirement. Compare current and baseline `LinkStore.java` and `CandidateTest.java`: the change adds expiration validation and exact-boundary enforcement.

Review and approve execution. VALIDATE should show **`PASS: 9 candidate assertions`** and exit code 0. Approve release separately. The run should complete, and release validation should repeat the nine assertions. Export its evidence.

### Ambiguous

Choose **Authored demonstration** and **Ambiguous / clarify first**. The run asks questions and waits for clarification before planning.

For example, enter: `Implement expiration first; collect no raw IP addresses; use UTC aggregate click counts.` Record the clarification.

The workflow then pauses at **IMPLEMENT / manual ChatGPT handoff**. This is the expected result, not a failure. No canned implementation is silently substituted for your answer. The exported implementation prompt contains the clarification. Continuing requires a reviewed JSON file bundle from your assistant; see [SCENARIOS.md](docs/SCENARIOS.md) for the contract.

### Manual ChatGPT handoff

Choose **Manual ChatGPT handoff** and **Greenfield**. The run must pause at **ANALYZE** and display a prompt to copy into ChatGPT.

This verifies the handoff boundary. No model is called by the application. To continue, use the prompt in your assistant, review its JSON response, and import it. The same process follows for PLAN and IMPLEMENT. Questions in analysis must be resolved before planning. Imported Java requires explicit review and approval before local execution.

### Reject approval

Create a new **Authored demonstration / Greenfield** run and wait for execution approval. Enter a rejection reason and click **Reject**.

The run should become **REJECTED**. VALIDATE should have **0 attempts**, with no compiler/test execution. The audit trail records the rejection. Rejection ends that revision; it does not continue automatically.

### Safe stop and resume

Create another **Authored demonstration / Greenfield** run and wait for execution approval. Click **Safe stop**. The run should show **STOPPED**.

Click **Resume**. It should show **WAITING FOR APPROVAL**, and VALIDATE should still have **0 attempts**. Renewed execution approval is required. Resume alone does not authorize code execution.

If you stop while a tool is already running, it may finish under its bounded timeout. Its result is recorded but not promoted. Wait for it to finish before resuming.

## Recorded verification

The supplied validation record includes:

- **29 integration tests**, with zero failures or errors.
- **10 DOM behavior checks**, including analytics and Disable/Enable.
- **All five remaining scenarios verified through actual HTTP requests against the packaged application:** Brownfield, Ambiguous, Manual ChatGPT handoff, Reject approval, and Safe stop / Resume.
- Real Brownfield compiler execution and nine passing assertions during both validation and release.
- Audit-chain integrity checks for all five scenario runs.

See [validation record](docs/VALIDATION.md) and [scenario results](docs/validation/scenario-results.json) for evidence. Scenario checks used an isolated database and did not modify the user's existing runs. They executed only reviewed authored fixtures, not external AI output.

To rebuild and run the Java integration suite from source, install Maven and run `mvn verify`. The optional DOM check additionally uses Node.js and jsdom, as described in the validation record. No code coverage percentage, production availability, or rendered browser screenshot review is claimed.

## Assessment scope and limitations

The orchestration, persistence, task dependencies, independent worker branches, approvals, validation tools, bounded repair, audit history, and local release behavior execute in the application.

AI interaction uses **manual ChatGPT handoffs** and explicitly labeled **authored demonstrations**. There is no live LLM API integration or autonomous model reasoning. This partially satisfies the assessment's autonomous AI objective. An `AgentProvider` interface separates the manual provider from the workflow for future integration.

The primary Spring Boot URL application has persistent storage. The smaller candidate service staged by the demonstration uses in-memory storage. A release is a validated local artifact directory, not a cloud deployment or running-service rollout.

Generated/imported Java executes with the local user's OS permissions after human approval. It is not OS sandboxed. Review source and test adequacy before approving. PostgreSQL and Docker configurations are supplied as optional paths but have not been runtime-verified here.

## Project contents

- `app/engineering-workbench-1.0.0.jar`: runnable packaged application.
- `src/` and `pom.xml`: full source and Java integration tests.
- `README.md`: detailed setup and design boundaries.
- `docs/ARCHITECTURE.md`: components, graph, state, and control flow.
- `docs/API.md` and `/openapi.yaml` in the running app: API contracts.
- `docs/SCENARIOS.md`: detailed manual-response contracts and examples.
- `docs/ENGINEERING_SUMMARY.md`: decisions, tradeoffs, and limitations.
- `docs/presentation-outline.md`: presentation outline, not a PowerPoint file.
- `docs/validation/`: test summaries and scenario evidence.
- `scripts/`: build/run, packaging, and verification helpers.
- `SHA256SUMS.txt`: checksums for packaged files.

Author: Pranav Pagare
