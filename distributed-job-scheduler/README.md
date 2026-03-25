# Distributed Job Scheduler

A production-grade distributed job scheduler built with **Java 17**, **Spring Boot 3**, **PostgreSQL**, and **Redis**. Designed as a portfolio project demonstrating senior backend SDE skills: distributed locking, retry logic, priority queues, pluggable job handlers, and full observability.

---

## What This Project Is

This system lets clients submit jobs (email sends, HTTP callbacks, arbitrary log tasks) via REST API and have them reliably executed at a scheduled time. Jobs are stored in **PostgreSQL** with full lifecycle tracking and **audit history** (every status transition is recorded). Distributed execution safety is provided by **Redis locks** — only one worker across any number of running instances captures each job. Failed jobs are automatically re-queued with **exponential backoff** until they exhaust their retries and move to a `DEAD` state. New job types can be added by implementing a single `JobHandler` interface — no other code changes required.

---

## Architecture

```
                        ┌──────────────────────────────────────────────────┐
                        │               Spring Boot App                    │
                        │                                                  │
   Client ─────────────►│  JobController   /api/v1/jobs                   │
   (REST)               │  MetricsController /api/v1/metrics               │
                        │           │                                      │
                        │           ▼                                      │
                        │   JobSchedulerService  (every 5s)                │
                        │           │                                      │
                        │    For each due PENDING job:                     │
                        │           │                                      │
                        │     ┌─────▼───────┐        ┌──────────────────┐ │
                        │     │ Redis Lock  │        │   PostgreSQL DB  │ │
                        │     │ tryAcquire()│        │   jobs table     │ │
                        │     └─────┬───────┘        │   job_events     │ │
                        │           │ ✓ locked        └──────────────────┘ │
                        │           ▼                                      │
                        │     WorkerPool (ThreadPoolExecutor)              │
                        │           │                                      │
                        │           ▼                                      │
                        │     JobExecutor                                  │
                        │      ├── EmailJobHandler      (type: EMAIL)      │
                        │      ├── HttpCallbackJobHandler(type: HTTP_CALLBACK)│
                        │      └── LogJobHandler         (type: LOG)       │
                        │           │                                      │
                        │    success │ fail                                │
                        │           ▼                                      │
                        │     RetryHandler (exponential backoff)           │
                        │     always: release Redis lock                   │
                        └──────────────────────────────────────────────────┘
```

---

## How to Run Locally

### Option 1: Full Docker (recommended)

```bash
git clone <repo-url>
cd distributed-job-scheduler
docker-compose up --build
```

The app starts at `http://localhost:8080`. Swagger UI: `http://localhost:8080/swagger-ui.html`.

### Option 2: Local Dev (PostgreSQL + Redis from Docker, app via Maven)

```bash
# Start dependencies only
docker-compose up postgres redis

# Run the app
mvn spring-boot:run
```

### Run Tests

```bash
mvn test
```

> Note: Integration tests in `JobSchedulerServiceTest` require Docker to be running (Testcontainers auto-provisions PostgreSQL and Redis containers).

---

## curl Examples

### Submit a Job
```bash
curl -X POST http://localhost:8080/api/v1/jobs \
  -H "Content-Type: application/json" \
  -d '{
    "name": "send-welcome-email",
    "type": "EMAIL",
    "payload": {"to": "user@example.com", "subject": "Welcome"},
    "scheduledAt": "2026-03-26T10:00:00",
    "priority": "HIGH",
    "maxRetries": 3
  }'
```

### Get a Job by ID
```bash
curl http://localhost:8080/api/v1/jobs/{id}
```

### List Jobs with Filters
```bash
curl "http://localhost:8080/api/v1/jobs?status=PENDING&priority=HIGH&page=0&size=10"
```

### Cancel a Pending Job
```bash
curl -X DELETE http://localhost:8080/api/v1/jobs/{id}
```

### Retry a Failed/Dead Job
```bash
curl -X POST http://localhost:8080/api/v1/jobs/{id}/retry
```

### Get Execution History
```bash
curl http://localhost:8080/api/v1/jobs/{id}/logs
```

### Metrics Summary
```bash
curl http://localhost:8080/api/v1/metrics/summary
```

### Queue Depth by Priority
```bash
curl http://localhost:8080/api/v1/metrics/queue-depth
```

---

## Design Decisions

### Why polling over push?
Polling (every 5 seconds) is the simplest distributed scheduling mechanism with zero additional infrastructure. A push-based approach (e.g., Kafka) would be more scalable but adds operational complexity. The batch-size cap (50 jobs/tick) and configurable poll interval give enough headroom for most workloads. The tradeoff is up to 5s latency between when a job becomes due and when it starts.

### Why Redis for locking vs DB-level locking?
DB-level locking (`SELECT FOR UPDATE SKIP LOCKED`) would work but couples lock duration to transaction time — if a transaction is slow, all other instances block. Redis locks are fire-and-forget with TTL, so even if a node dies mid-execution, the lock expires automatically (60s TTL). Redis commands are orders of magnitude faster than row-level DB operations, and the Lua script guarantees atomic check-then-delete without WATCH/MULTI overhead.

### Why exponential backoff?
Linear or fixed retry intervals hammer failing external services (SMTP, webhooks) at a constant rate, worsening their overload. Exponential backoff (2s → 4s → 8s...) gives downstream services time to recover and prevents thundering-herd retry storms in multi-job scenarios.

### What does @Version (optimistic locking) add on top of Redis?
Redis locks have a TTL of 60 seconds. If a job takes longer than 60 seconds, another scheduler instance could acquire the same lock and start executing the same job before the first instance finishes. The `@Version` field on `Job` adds a DB-level check: even if two instances both start executing the same job, only the first `UPDATE` will succeed — the second will throw `OptimisticLockingFailureException`, which the scheduler catches and releases the lock without completing.

### Pluggable Handlers
Spring's `Map<String, JobHandler>` injection pattern auto-discovers any `@Component` that implements `JobHandler`. Adding a new job type requires only implementing the interface and annotating the class — zero changes to `JobSchedulerService`, `JobExecutor`, or the controller.

---

## Potential Improvements

| Area | Improvement |
|---|---|
| **Ingest** | Replace REST submission with Kafka consumer for higher-throughput ingestion |
| **Observability** | Expose Prometheus `/actuator/prometheus` metrics endpoint with job counters and histograms |
| **Live status** | WebSocket-based real-time job status updates via Spring WebSocket |
| **Horizontal scaling** | Currently stateless — just run multiple instances behind a load balancer; Redis locking handles coordination |
| **Job chaining** | Support DAG-style job dependencies (job B runs only after job A completes) |
| **Cron scheduling** | Add cron expression support to `JobRequest` for recurring jobs |
| **Dead-letter UI** | Admin dashboard to inspect and replay DEAD jobs |
| **Idempotency keys** | Client-provided idempotency keys to prevent duplicate job submissions |

---

## API Reference

| Method | Path | Description | Success | Error |
|--------|------|-------------|---------|-------|
| `POST` | `/api/v1/jobs` | Submit a new job | 201 | 400 (validation / unknown type) |
| `GET` | `/api/v1/jobs` | List jobs with filters | 200 | 400 (invalid filter) |
| `GET` | `/api/v1/jobs/{id}` | Get job by ID | 200 | 404 |
| `DELETE` | `/api/v1/jobs/{id}` | Cancel a PENDING job | 200 | 404, 409 (not PENDING) |
| `POST` | `/api/v1/jobs/{id}/retry` | Retry FAILED or DEAD job | 200 | 400 (wrong status), 404 |
| `GET` | `/api/v1/jobs/{id}/logs` | Get job execution history | 200 | 404 |
| `GET` | `/api/v1/metrics/summary` | Scheduler health summary | 200 | — |
| `GET` | `/api/v1/metrics/queue-depth` | Pending job count by priority | 200 | — |

### Sample Request — POST /api/v1/jobs
```json
{
  "name": "send-welcome-email",
  "type": "EMAIL",
  "payload": { "to": "user@example.com", "subject": "Welcome" },
  "scheduledAt": "2026-03-26T10:00:00",
  "priority": "HIGH",
  "maxRetries": 3
}
```

### Sample Response — GET /api/v1/jobs/{id}
```json
{
  "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "name": "send-welcome-email",
  "type": "EMAIL",
  "payload": { "to": "user@example.com", "subject": "Welcome" },
  "status": "PENDING",
  "priority": "HIGH",
  "scheduledAt": "2026-03-26T10:00:00",
  "nextRunAt": "2026-03-26T10:00:00",
  "startedAt": null,
  "completedAt": null,
  "retryCount": 0,
  "maxRetries": 3,
  "lastError": null,
  "createdAt": "2026-03-25T10:20:00",
  "updatedAt": "2026-03-25T10:20:00"
}
```

---

## Tech Stack

| Layer | Technology |
|---|---|
| Runtime | Java 17 |
| Framework | Spring Boot 3.2 |
| Database | PostgreSQL 15 (via Spring Data JPA + Hibernate) |
| JSONB Mapping | hypersistence-utils (JsonType) |
| Cache / Lock | Redis 7 (Lettuce client — SET NX EX + Lua scripts) |
| Migrations | Flyway |
| Build | Maven |
| Testing | JUnit 5 + Mockito + Testcontainers + embedded-redis |
| API Docs | SpringDoc OpenAPI (Swagger UI) |
| Logging | SLF4J + Logback |
| Containers | Docker + Docker Compose |
