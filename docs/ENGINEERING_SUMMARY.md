# Engineering summary

## Outcome

Implemented a persistent URL application and a governed local delivery workflow in Java/Spring Boot. The keyless model deliberately uses manual AI handoffs and explicitly authored demonstrations. It provides real execution, validation, and governance without claiming autonomous model reasoning.

The project favors a small reviewable system within the two-day deadline. Core URL and workflow behavior reside in separate packages, with a static review interface and no frontend build dependency.

## Design decisions

Spring JDBC makes SQL and transaction behavior visible. Database uniqueness resolves concurrent code allocation. H2 file storage reduces startup steps. PostgreSQL remains an optional profile requiring separate runtime validation.

The workflow stores an explicit dependency graph and state snapshots. Two workers handle independent branches; synchronous state transitions and optimistic database versions prevent accidental overwrites in one instance. Input/output schemas and artifact hashes define gates. Model output never becomes an arbitrary tool command.

Execution approval covers local compilation/tests. Release approval covers a second validation and artifact promotion. Both require human reasons. Approval invalidation, restart recovery, and bounded repair prevent silent continuation with stale evidence.

## Validation approach

The suite uses a real HTTP server, database constraints, concurrent clients, and actual Java compiler/subprocess execution for trusted fixtures. It includes behavioral and failure-path evidence rather than checking only object construction. Release smoke failure must preserve the previous local pointer. Audit tampering must fail verification.

The final command and counts appear in VALIDATION.md. No coverage percentage, throughput, production availability, or distributed correctness claim is made.

## Assignment coverage

| Requirement | Implementation and limit |
| --- | --- |
| Requirement understanding | Structured analysis and clarification, manually imported or authored demo |
| Decomposition | Structured plan plus explicit policy-owned dependency graph |
| Brownfield reasoning | Preserved basic candidate and expiration change impact; no arbitrary repository indexing |
| Stateful orchestration | Persisted tasks, joins, repair transitions, revision history, restart recovery |
| Governance | Execution/release approvals, exact source hashes, fixed tools, local policy checks |
| Output generation | Actual staged source and manual AI file import; no autonomous LLM generation |
| Validation | Real compiler/assertion evidence, HTTP/database tests, bounded failure behavior |
| Observability | Audit events, exported lineage, observed run metrics |
| Re-planning | New root revision invalidates affected graph and approvals; fixed graph structure |
| Release | Local artifact validation/promotion and pointer rollback; no cloud deployment |

## Remaining limitations

Autonomous reasoning requires a future live provider. Code execution requires a future dedicated sandbox before accepting untrusted inputs. Multi-user access, distributed scheduling/leases, independent audit anchoring, production URL abuse management, schema evolution tooling, and service deployment are outside the delivered prototype. The smaller candidate service is in memory; the primary service is persistent.

These limitations should remain visible in the recruiter/client presentation so the discussion focuses on implemented evidence and defensible next steps.
