# NotifyHub

A standalone notification microservice built with **Java 17 + Spring Boot 3.4**. Backend applications send email and SMS notifications through one REST API; delivery happens asynchronously through **Kafka**, with **Redis + PostgreSQL** idempotency, centralized templates, retry topics with a Dead Letter Topic (DLT), JWT authentication, and **Prometheus + Grafana** monitoring.

## Features

- **Channels:** email (SMTP) and SMS (Twilio) behind a pluggable sender/provider design (Strategy pattern)
- **Async delivery:** the API returns `202 Accepted`; Kafka consumers deliver the message
- **Idempotency:** Redis fast check + PostgreSQL unique constraint as the final guard
- **Retry + DLT:** failed deliveries are retried through Kafka retry topics, then moved to a DLT and marked `FAILED`
- **Templates:** `{{placeholder}}` templates managed through the API, or send raw content directly
- **History:** filter and paginate your own notifications
- **Auth:** JWT access tokens, rotating refresh tokens in an `HttpOnly` cookie, Google OAuth2 login
- **Data safety:** recipients are masked in logs; users only see their own notifications
- **Ops:** Flyway migrations, Swagger/OpenAPI, Actuator + Prometheus metrics, Docker Compose stack

## Architecture

```text
Client App ──REST──▶ NotifyHub API ──▶ PostgreSQL   (notification saved as PENDING)
                          │  ▲
                          │  └── Redis (idempotency fast check)
                          │
                          └─ after transaction commit ─▶ Kafka: notification-events
                                                              │
                                                              ▼
                                                    NotificationEventConsumer
                                                              │
                                                              ▼
                                                     NotificationEngine
                                                       │            │
                                                 EmailSender     SmsSender
                                                       │            │
                                                       └─────┬──────┘
                                                             ▼
                                            status updated in PostgreSQL
```

### Request flow

1. `POST /api/v1/notifications` → idempotency check (Redis, then PostgreSQL)
2. Validate channel, then resolve content (raw body **or** template, not both)
3. Save the notification as `PENDING`
4. After the DB transaction commits, publish an event to Kafka
5. The consumer hands the event to the `NotificationEngine`, which picks the sender by channel
6. Status is updated to `SENT`, or the message goes through retry → DLT → `FAILED`

### Status lifecycle

```text
PENDING ──▶ SENT                       (delivered on the first attempt)

PENDING ──▶ RETRYING ──▶ SENT          (a retry succeeded)
                    └──▶ FAILED        (retries exhausted, message reached the DLT)
```

`attempts` on each notification counts delivery attempts.

## Idempotency

Every request must carry an `idempotencyKey`.

| Layer | Role |
| --- | --- |
| Redis `SET NX` (60 s TTL) | Fast rejection of duplicates and concurrent retries |
| PostgreSQL `UNIQUE (idempotency_key)` | Final guarantee, even if Redis expires |

A duplicate request is rejected with **`409 Conflict`**.

## Retry and DLT

- Consumer uses Spring Kafka `@RetryableTopic`: **3 delivery attempts in total** (the first try + 2 retries)
- Exponential backoff: **1 s, then 2 s**
- Topics: `notification-events`, its retry topics, and a DLT (created automatically by Spring Kafka)
- When a message reaches the DLT, the notification is set to `FAILED` and a failure reason is stored

## Templates

```text
Subject: Your NotifyHub Order Confirmation
Body:    Hello {{name}}, your order {{orderId}} has been confirmed.
```

- `data` values are substituted into `{{placeholders}}`
- A missing placeholder in `data`, or a `null` data object, returns **`400 Bad Request`** (no blank substitution)
- `DELETE` deactivates a template instead of removing it; inactive templates cannot be used to send
- Templates are shared across all authenticated users

## API

All endpoints except `/auth/**` need `Authorization: Bearer <accessToken>`.

### Authentication

| Method | Path | Description |
| --- | --- | --- |
| POST | `/auth/register` | Create a user (role `USER`) |
| POST | `/auth/login` | Returns an access token and sets the `refresh_token` cookie |
| POST | `/auth/refresh` | Rotates the refresh token and returns a new access token (rate limited: 5 requests/min per IP) |
| POST | `/auth/logout` | Revokes the refresh token and clears the cookie |
| GET | `/oauth2/authorization/google` | Starts Google login |

### Templates

| Method | Path | Description |
| --- | --- | --- |
| POST | `/api/v1/templates` | Create a template (`201`) |
| GET | `/api/v1/templates/{code}` | Fetch a template |
| PUT | `/api/v1/templates/{code}` | Update channel, subject and body |
| DELETE | `/api/v1/templates/{code}` | Deactivate a template (`204`) |

### Notifications

| Method | Path | Description |
| --- | --- | --- |
| POST | `/api/v1/notifications` | Send a notification (`202`) |
| GET | `/api/v1/notifications/{id}` | Fetch one of your notifications |
| GET | `/api/v1/notifications` | List your notifications (filters below) |

**Send with a template**

```http
POST /api/v1/notifications
Authorization: Bearer <accessToken>
Content-Type: application/json
```

```json
{
  "channel": "EMAIL",
  "recipient": "user@example.com",
  "templateCode": "ORDER_CONFIRMATION",
  "data": { "name": "Honey", "orderId": "ORD-1001" },
  "idempotencyKey": "order-confirmation-001"
}
```

**Send raw content** (use `rawSubject` + `rawBody` instead of `templateCode`)

```json
{
  "channel": "EMAIL",
  "recipient": "user@example.com",
  "rawSubject": "Welcome to NotifyHub",
  "rawBody": "Hello, welcome!",
  "idempotencyKey": "welcome-001"
}
```

Provide either `templateCode` or raw content, not both. Supported channels: `EMAIL`, `SMS`.

**History filters**

```text
GET /api/v1/notifications?status=FAILED&channel=EMAIL&fromDate=2026-09-01T00:00:00&page=0&size=20
```

Query parameters: `recipient`, `status`, `channel`, `fromDate`, `toDate`, plus Spring `page` / `size` / `sort` (max page size 100).

## Security

- Access tokens: JWT, default lifetime 15 minutes (`JWT_EXPIRATION`)
- Refresh tokens: random, stored **SHA-256 hashed** in PostgreSQL, grouped in token families, rotated on every refresh
- Refresh cookie: `HttpOnly`, `SameSite=Strict`, path `/auth`; `Secure` is controlled by `COOKIE_SECURE` (set `true` behind HTTPS)
- Authorization: currently a single role, `USER`, required for all `/api/v1/**` endpoints
- Ownership: a user can only read their own notifications
- Logs: recipients are masked (`user@example.com` → `us***@ex***.com`, phone numbers → `***3210`)
- Optimistic locking: `Notification` has a `@Version` field to guard concurrent status updates
- Public endpoints: `/auth/**`, `/actuator/health`, `/actuator/prometheus`, Swagger UI. Restrict `/actuator/prometheus` at the network level in production

## Tech Stack

| Area | Technology |
| --- | --- |
| Language / framework | Java 17, Spring Boot 3.4.5, Spring Web, Spring Validation |
| Persistence | Spring Data JPA, PostgreSQL 15, Flyway |
| Messaging | Apache Kafka (Spring Kafka, KRaft mode) |
| Cache / idempotency | Redis 7 (Spring Data Redis) |
| Security | Spring Security, OAuth2 Resource Server (JWT), OAuth2 Client (Google) |
| Delivery | Spring Mail (SMTP), Twilio SDK |
| Docs | springdoc OpenAPI / Swagger UI |
| Observability | Actuator, Micrometer, Prometheus, Grafana |
| Testing | JUnit 5, Mockito, MockMvc, Testcontainers |
| Build / run | Maven (wrapper included), Docker, Docker Compose |

## Prerequisites

- Java 17+
- Docker and Docker Compose (the Compose stack, and the integration tests via Testcontainers, need Docker running)

You do not need to install Maven; use the `./mvnw` wrapper.

## Configuration

```bash
cp .env.example .env
```

Then fill in your values. **Never commit `.env`.**

| Variable | Purpose |
| --- | --- |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL connection |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_DATABASE` | Redis connection |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` | SMTP server |
| `EMAIL_PROVIDER` | `SMTP` (default) or `GENERIC` |
| `SMS_PROVIDER` | `TWILIO` (default), `GENERIC` or `CLIENT_CUSTOM` |
| `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, `TWILIO_PHONE_NUMBER` | Twilio credentials |
| `JWT_SECRET`, `JWT_EXPIRATION` | JWT signing secret and access token lifetime (seconds) |
| `REFRESH_TOKEN_EXPIRATION` | Refresh token lifetime (seconds) |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | Google OAuth2 login |
| `COOKIE_SECURE` | `Secure` flag on the refresh cookie |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka address (defaults to `localhost:9092` in the `local` profile) |
| `SPRING_PROFILES_ACTIVE` | `local` or `prod` |

Inside Docker Compose, the app container overrides the database, Redis and Kafka addresses to the service names (`postgres`, `redis`, `kafka`), so `localhost` values in `.env` only apply to local runs.

## Running with Docker Compose

```bash
docker compose up -d --build
docker compose ps
curl http://localhost:8081/actuator/health
```

Expected: `{"status":"UP"}`

| Service | URL |
| --- | --- |
| NotifyHub (container port 8080) | http://localhost:8081 |
| Swagger UI | http://localhost:8081/swagger-ui/index.html |
| Health | http://localhost:8081/actuator/health |
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3000 |

Compose starts: NotifyHub, PostgreSQL, Redis, Kafka (KRaft, no Zookeeper), Prometheus, and Grafana.

**Grafana:** dashboards are not pre-provisioned. Log in with the default `admin` / `admin`, add a Prometheus data source with URL `http://prometheus:9090`, and build or import dashboards on the `/actuator/prometheus` metrics. Prometheus scrapes the app using `monitoring/prometheus.yml`.

## Running locally

Start PostgreSQL, Redis and Kafka (for example with `docker compose up -d postgres redis kafka`), then:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

The app runs on http://localhost:8080 (locally the port is 8080; Compose maps it to 8081 on your machine).

## Testing

```bash
./mvnw clean test
```

The suite includes unit tests (Mockito), controller tests (MockMvc), and integration tests on real PostgreSQL, Redis and Kafka containers (Testcontainers), including the retry/DLT flow. Docker must be running.

## Project structure

```text
notifyhub/
├── src/main/java/com/notifyhub/
│   ├── config/          OpenAPI config
│   ├── engine/          NotificationEngine (sender resolution, status updates)
│   ├── enums/           Channel, NotificationStatus
│   ├── exception/       Global exception handler + custom exceptions
│   ├── messaging/       Kafka config, producer, consumer (retry/DLT), events
│   ├── notification/    controller, service, repository, entity, dto, mapper,
│   │                    idempotency, email/, sms/, sender/, specification/
│   ├── security/        auth controller, JWT config, refresh tokens, Google OAuth2
│   └── template/        controller, service, renderer, repository, entity, dto
├── src/main/resources/
│   ├── application*.yml
│   └── db/migration/    Flyway migrations (V1__init_schema.sql)
├── src/test/            unit + integration tests
├── monitoring/          prometheus.yml
├── docker-compose.yml
├── Dockerfile
└── .env.example
```

## Known limitations

- **Kafka publish is not transactional with the database.** The event is published after commit; if Kafka is down at that moment, the notification stays `PENDING`. An outbox pattern would fix this.
- **Idempotency keys are globally unique**, not scoped per user, and the Redis lock lasts 60 seconds. If a request fails validation after the lock is taken, retrying with the same key within 60 seconds returns `409`.
- **Redis is required** for sending notifications; there is no fallback to PostgreSQL-only checks if Redis is down.
- **Refresh-token rate limiting is in-memory**, so it is per instance and resets on restart.
- **Single role:** every registered user has role `USER`, and templates are shared between users.
- **DLT failure reason is generic**; the last provider error is only stored while the notification is `RETRYING`.
- Push notifications, WhatsApp, and multi-tenant quotas are out of scope.

## Author

**Honey Chauhan** — built as a backend engineering project covering Spring Boot, Kafka, Redis, PostgreSQL, Docker, security, testing, and observability.