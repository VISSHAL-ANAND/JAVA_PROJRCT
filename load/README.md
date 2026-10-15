# CAMPUSOS staging load test

This is a **manual, opt-in staging test**. CI already exercises 1,000 concurrent logins, writes, mixed reads/writes, database-backed reads, emergency fan-out, pagination, and booking contention against disposable MySQL/PostgreSQL services. This k6 profile adds a sustained 14-minute test against the actual chosen staging host.

## Preconditions

- Use a **dedicated disposable CAMPUSOS staging deployment and database**, never production or a shared campus database.
- Configure HTTPS, the exact `CAMPUSOS_CORS_ORIGINS`, a stable staging-only `CAMPUSOS_AUTH_SECRET`, and the intended JDBC pool size.
- Seed 1,000 disposable student accounts whose emails are `PREFIX1@DOMAIN` through `PREFIX1000@DOMAIN`, all with the same staging-only password. Use the supported admin API or a reviewed seed procedure; do not use real student accounts or real passwords.
- Ensure at least one staff account is available for issue assignment. The script writes staging issues about once per 20 iterations per virtual user and uses a run-specific title prefix for targeted cleanup.
- Confirm the host name is exactly the staging API hostname. The script refuses non-HTTPS origins, a hostname mismatch, or a missing explicit approval flag.

## Run on Windows PowerShell

Install k6 using its official instructions, then set the environment values for the disposable staging environment:

```powershell
$env:CAMPUSOS_STAGING_URL = "https://staging-api.example.com"
$env:CAMPUSOS_STAGING_HOST = "staging-api.example.com"
$env:CAMPUSOS_LOAD_TEST_APPROVAL = "I_CONFIRM_THIS_IS_DEDICATED_STAGING"
$env:CAMPUSOS_LOAD_TEST_EMAIL_PREFIX = "loadtest-"
$env:CAMPUSOS_LOAD_TEST_EMAIL_DOMAIN = "example.com"
$env:CAMPUSOS_LOAD_TEST_PASSWORD = "A-unique-staging-only-password"
$env:CAMPUSOS_LOAD_TEST_RUN_ID = "20261015-a"
k6 run load/staging-k6.mjs
```

Replace the example hostname and account pattern with the exact dedicated staging values. Do not commit these environment values or paste the password into logs/issues.

## Profile and acceptance targets

- Ramps 100 → 500 → 1,000 virtual users, holds 1,000 for five minutes, then ramps down.
- Each virtual user signs in and repeatedly exercises its own ticket list, notifications, resource inventory, and occasional issue submission.
- Targets: under 1% failed HTTP requests, over 99% successful checks, API p95 below 2 seconds, login p95 below 10 seconds. These are proposed staging acceptance targets, not promises that every host/database tier can meet them. Investigate failures rather than weakening thresholds without evidence.
- Capture k6 summary output plus API-host CPU/memory/thread/queue metrics, JDBC pool active/pending counts, database CPU/connections/locks, and provider throttling/errors.

## Cleanup and post-run review

Issue rows are named `CAMPUSOS_LOADTEST_<RUN_ID>_...`. After saving metrics, remove only this run's test data using the database owner's reviewed cleanup procedure, respecting foreign-key order (notifications and ticket history, tickets, issues, then any disposable users). Do not run broad `DELETE` statements against shared or production data.

A passing run is still only a point-in-time result. Repeat after host/database sizing changes and during a representative traffic window. Keep the exact k6 summary and environment sizing in the release record.
