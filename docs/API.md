# API reference

Base URL: `http://127.0.0.1:8080`. Mutations require `Content-Type: application/json`. No paid AI API is called. This is a loopback-only single-user control plane.

## URL application

| Method | Path | Request / result |
| --- | --- | --- |
| POST | /api/links | `{destination, alias?, expiresAt?}`. Returns 201 and link metadata |
| GET | /api/links?page=0&size=20 | Paginated `{items,page,size,total}`; size 1-100 |
| GET | /api/links/{id} | Link metadata |
| PATCH | /api/links/{id} | `{active?, expiresAt?, clearExpiration?}`. Returns updated link |
| GET | /api/links/{id}/analytics | `{linkId, timezone:"UTC", totalClicks, daily:[{date,clicks}]}` |
| GET | /s/{code} | 302 with Location and Cache-Control:no-store; records a click |

Link metadata: `{id,code,destination,createdAt,expiresAt,active}`. Timestamps are ISO-8601 instants. Codes are case-sensitive. Aliases contain 3-64 ASCII letters, digits, hyphens, or underscores. Reserved aliases include api, admin, health, assets, index, and favicon.

Examples:

```powershell
$link = Invoke-RestMethod http://127.0.0.1:8080/api/links -Method Post -ContentType application/json -Body '{"destination":"https://example.com","alias":"review-demo"}'
Invoke-RestMethod "http://127.0.0.1:8080/api/links/$($link.id)/analytics"
```

Invalid URL/expiration/alias/pagination: 400. Missing link: 404. Conflicting alias: 409. Disabled/expired redirect: 410. Storage failure: 503. Analytics include only successful redirects. The backend never follows destination URLs.

## Workflow application

| Method | Path | Contract |
| --- | --- | --- |
| POST | /api/runs | `{requirement,mode:"MANUAL" or "DEMO",scenario:"GREENFIELD" or "BROWNFIELD" or "AMBIGUOUS"}` |
| GET | /api/runs | Run summaries |
| GET | /api/runs/{id} | Complete current run and task graph |
| GET | /api/runs/{id}/prompt/{stage} | Prompt and binding `{stage,revision,version,prompt}` |
| POST | /api/runs/{id}/handoff | `{stage,revision,version,response}` where response is a JSON string, not a nested object |
| POST | /api/runs/{id}/clarification | `{answer}` |
| POST | /api/runs/{id}/revision | `{requirement}`; archives current state and invalidates approvals |
| POST | /api/runs/{id}/stop | `{}` |
| POST | /api/runs/{id}/resume | `{}` |
| POST | /api/runs/{id}/approvals/execution | `{revision,hash,approve,reason}` |
| POST | /api/runs/{id}/approvals/release | Same approval shape; distinct action |
| GET | /api/runs/{id}/events | Events, hashChainValid, and trust-boundary note |
| GET | /api/runs/{id}/revisions | Archived revision snapshots |
| GET | /api/runs/{id}/export | Current run, events, archives, and chain verification |
| GET | /api/metrics | Observed counters, success fraction, terminal latency, and definitions |
| GET | /api/health | Database readiness and mode |

Stage names: ANALYZE, PLAN, IMPLEMENT, TEST_SPEC, DOC_DRAFT, VALIDATE, SUMMARY, RELEASE. Manual response import applies only to ANALYZE, PLAN, IMPLEMENT and only when waiting for input. See SCENARIOS.md for JSON schemas by example.

Approvals must match the exact current revision and source hash. Invalid state/stale content returns 409; invalid JSON/contracts return 400. Rejection is recorded and terminal. A root requirement revision can restart a terminal run once no tool is executing. Local run count is bounded to 100 by default.

Errors return `{error}`; unexpected errors include an incident identifier. Invalid Host/Origin/Content-Type is rejected by the servlet filter and may use the servlet container's standard error body.
