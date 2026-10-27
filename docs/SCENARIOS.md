# Scenario walkthroughs

## Greenfield

1. In SDLC workflows, choose **Authored demonstration** and **Greenfield**.
2. Enter a requirement for basic URL creation, redirect, aliases, validation, and click counts.
3. Inspect ANALYZE and PLAN, then the four staged candidate files. The source is an authored fixture and the interface labels its provenance.
4. Wait for TEST_SPEC and DOC_DRAFT to finish independently. VALIDATE must wait for both branches and implementation.
5. Review the source and approve local execution with a reason. Inspect real `javac` output and `CandidateTest` assertions.
6. Review SUMMARY and approve the exact hash for release. Inspect the release tool output and exported audit evidence.
7. The pointer `data/workspaces/release-<run-id>.txt` identifies the promoted artifact directory. To run the candidate, change into that directory and use `java -cp classes Server`. Its HTTP server binds to port 9090. POST `/links?url=https%3A%2F%2Fexample.com&alias=demo`, then GET `/s/demo`.

The candidate uses memory storage and a small API. The primary Spring Boot URL service remains the fuller persistent application on port 8080.

## Brownfield

Choose **Brownfield** in demonstration mode. The run retains the basic implementation as its baseline. The revised candidate adds creation-time expiration validation, exact-boundary expiration enforcement in resolve, and three additional assertions. Expand both current and baseline source in the UI, or compare them in the evidence export.

This scenario shows impact on the store and tests. The candidate has no database migration because its data is in memory; the primary service's persistent expiration support already exists.

## Ambiguous

Choose **Ambiguous** with a requirement such as “Improve abuse protection and analytics.” The run asks what limits, retention, and behavior you want. Record your answer. The demo then stops at a manual IMPLEMENT handoff, because canned Java cannot truthfully implement arbitrary answers.

Copy its prompt into ChatGPT, review the returned file bundle, and import the JSON. Confirm the tests actually establish your chosen acceptance criteria. Human review remains responsible for semantics; schema validation only establishes structure.

## Manual handoff from start to finish

Choose **Manual ChatGPT handoff** for any scenario. At each waiting stage, copy the displayed prompt to your assistant and paste its reviewed JSON response. The import request must include the displayed run revision and version. If a background branch changed the version, refresh and review the latest prompt before retrying.

Analysis example:

```json
{
  "summary": "Build a local URL service",
  "acceptanceCriteria": ["Valid HTTP/HTTPS destinations redirect", "Alias conflicts are rejected"],
  "questions": [],
  "assumptions": ["Single local user"]
}
```

If questions are nonempty, resolve them in the clarification box before planning continues.

Plan example:

```json
{
  "architecture": "JDK URL store with a loopback HTTP service",
  "tasks": ["Implement mappings and redirects", "Write executable assertions", "Document local startup"],
  "risks": ["Generated code requires review before local execution"]
}
```

Implementation response shape:

```json
{
  "files": {
    "LinkStore.java": "complete Java 17 source in the default package",
    "CandidateTest.java": "complete source with public static void main and real assertions",
    "Server.java": "complete JDK-only runnable HTTP service source",
    "README.md": "startup instructions and limitations"
  },
  "rationale": "Explain the change and the acceptance criteria covered by the tests"
}
```

The strings above describe the shape, not compilable source. The exported prompt supplies full context, baseline, current files, and failure evidence. Return full source strings using valid JSON escaping and no Markdown fences. Tests must fail with an exception or nonzero exit when behavior is wrong. The executor does not detect vacuous tests; the reviewer must assess adequacy.

## Failures, repairs, and rejection

For a manual candidate, introduce a controlled assertion failure in CandidateTest, review it, and approve execution. Validation must fail, persist command evidence, and return to IMPLEMENT for repair. Import corrected files and approve their hash. At most two repair opportunities are offered; a third failed validation ends the run.

Reject either approval to end the run with an explanation. To try a revised requirement, use **Revise requirement**. The previous revision remains in exported evidence. A safe stop prevents new work and promotion; an existing tool is allowed to finish under its timeout and records its outcome. Resume only after it finishes, and approve execution again.

For restart recovery, stop the application while a tool is RUNNING, then restart it against the same database. The engine clears approvals and requires renewed review before execution. Normal waiting runs persist without recovery changes.

## Evidence to show a reviewer

- Requirement, clarification, stage contracts, and provider provenance.
- Dependency states and independent branch start/completion events.
- Full source, baseline comparison, hash, and approval reasons.
- Real tool commands, exit codes, assertion output, and repair history.
- Local release outcome, archived revisions, and audit chain verification.
- Observed metrics with sample count and limitations.
