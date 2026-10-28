# CAMPUSOS Deployment Readiness

Last reviewed: 2026-10-28  
Repository: `VISSHAL-ANAND/JAVA_PROJRCT`  
Latest verified main commit: `b55dd1e984588ac815aa8993035765a28c3c099e`  
Latest CI: [run 40666235021](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/actions/runs/40666235021) — **7/7 jobs passed**

## Decision

**Ready to prepare a dedicated staging deployment; not yet cleared for production.** The CI evidence is promising for bursts of 1,000 protected requests, but it does not certify a real hosting/database tier or sustained traffic.

## Evidence from the actual production Docker image in CI

| Scenario | Result | Status |
| --- | --- | --- |
| 1,000 concurrent authenticated protected-route requests | 1,000/1,000 success; p95 911 ms; max 1.015 s | Passes CI burst gate |
| 1,000 concurrent database-backed readiness requests | 1,000/1,000 success; p95 538 ms | Passes CI burst gate |
| 1,000 simultaneous database-backed successful logins | 1,000/1,000 success; p95 12.773 s; max 13.359 s | Correctness passes; proposed staging p95 <10 s target not met |
| MySQL 8.4 and PostgreSQL 17 integration/load suites | Booking contention, 1,000 issue writes, 1,000-user emergency fan-out, token revocation, pagination, login limiting, 5,000 authenticated reads, and five-round mixed workloads passed | Passes CI |
| Frontend audit/tests/build, Java verification, container build/runtime | All passed in latest CI | Passes CI |

CI uses disposable databases and shared GitHub runners. The measurements are useful regression evidence, not a promise that an arbitrary production plan can handle 1,000 simultaneous users.

## Required before production

- [ ] Create an isolated staging frontend, Java API service, and managed PostgreSQL database owned/approved for CAMPUSOS.
- [ ] Apply the correct bootstrap schema to a new database, or review/apply only the matching migrations to an existing staging database.
- [ ] Create a least-privilege app role; verify permissions and connection limits.
- [ ] Configure a stable staging-only `CAMPUSOS_AUTH_SECRET`, JDBC URL/credentials, pool size, exact frontend CORS origin, and frontend `VITE_API_BASE_URL`. Never put backend secrets in `VITE_*` variables.
- [ ] Verify HTTPS, health/readiness, login/logout, issue creation, technician assignment/status/history, notifications, emergency broadcast, booking/cancellation, and admin workflows end-to-end.
- [ ] Run the guarded 14-minute k6 test that ramps to 1,000 virtual users. Follow [load/README.md](load/README.md); use only disposable accounts/data and an explicitly approved staging host.
- [ ] Capture CPU/memory, HTTP queue/worker metrics, JDBC pool waits, database connections/locks, error rate, and p95 latency. Acceptance target: <1% failed requests, API p95 <2 s, login p95 <10 s. Investigate any miss; do not weaken the target merely to get a green run.
- [ ] Complete security review, including session-token storage/XSS risk, TLS/CORS, password-hash policy, secret rotation, audit logging, abuse/rate limits, and error/log redaction.
- [ ] Verify backups and restore, monitoring/alerts, graceful shutdown/termination grace period, and rollback procedure.

## Hosting architecture

- **Frontend:** Vercel static/SPA build from the repository root using `vercel.json`.
- **Backend:** a separate Java/container-capable web service using the root `Dockerfile`; Vercel's frontend deployment does not host the long-running Java HTTP server.
- **Database:** dedicated managed PostgreSQL/Supabase project, separate from any unrelated application database.

The connected Vercel context currently exposes no teams/projects, so the Vercel project and its permissions must be made accessible before automated deployment can proceed. No live infrastructure was created or modified during this review.
