# Initial presentation outline

Status: implementation walkthrough outline. This file is an outline, not a PowerPoint deck. The delivered version uses manual ChatGPT handoffs and authored demonstrations, with no AI API calls.

## Slide 1: URL shortener with governed SDLC orchestration

Pranav Pagare

A two-day local prototype that turns a requirement into a reviewable engineering change. Java and Spring Boot provide the service and orchestration foundation.

## Slide 2: What the assignment evaluates

The application demonstrates core APIs, analytics, and reliability. The orchestration demonstrates requirement understanding, explicit task dependencies, persistent state, governance, validation, and re-planning.

The final walkthrough covers greenfield, brownfield, and ambiguous requirements.

## Slide 3: Architecture and technology choices

Diagram: local review screen and REST API connect to a modular Spring Boot application using Spring JDBC. Separate modules handle URLs, analytics, workflow state, manual AI handoff, controlled tools, and release evidence. A separate candidate directory contains staged changes. H2 file storage is verified; the optional PostgreSQL profile still needs runtime validation.

Explain how this aligns with Java, Spring Boot, relational database, testing, and delivery experience. Avoid adding unimplemented infrastructure.

## Slide 4: URL shortening behavior

Demonstrate create, redirect, custom alias conflicts, expiration, disable, and daily click counts. Show the database uniqueness constraint and documented error responses.

## Slide 5: Stateful execution and dependency graph

Diagram the clarification branch, parallel independent tasks, synchronization at validation, bounded repair loop, and release approval. Show persisted state across a restart.

## Slide 6: Governance and evidence

Show a code diff and artifact hash, actual test output, a denied tool request, and approval tied to a specific candidate. Explain that deterministic code enforces policy and that generated code requires execution isolation or human review.

## Slide 7: Three scenarios

Greenfield: generate and validate a basic service in an empty candidate workspace.

Brownfield: add expiration to a preserved baseline and show affected modules and regression tests.

Ambiguous: clarify an abuse/analytics requirement, create a new revision, and re-plan affected work.

## Slide 8: Failure and recovery

Walk through a failed test, diagnosis, bounded repair, and re-validation. Show safe stop after exhaustion. Explain local artifact rollback and the limits of database rollback.

## Slide 9: Validation and observed reliability

Show 29 integration tests with zero failures or errors, actual compiler/assertion evidence, ten passing DOM behavior checks, five verified packaged workflow scenarios, and persisted data after restart. Show the live metric sample count and distinguish terminal success from production availability. The UI has functional DOM verification but no rendered browser screenshot review in the current environment.

## Slide 10: Delivery scope and engineering tradeoffs

Single-user, local deployment. Docker is optional for first startup. Explain model access and any manual handoffs, security boundaries, remaining limitations, and the next implementation step. Link to source, setup instructions, and scenario evidence when available.
