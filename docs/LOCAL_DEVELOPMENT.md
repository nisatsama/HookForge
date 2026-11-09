# Local development

## Requirements

- Java 21 (the Maven wrapper is included under `backend/`).
- Node.js 22.12 or newer and npm (Vitest 5 toolchain requirement).
- Docker Desktop with Docker Compose and enough resources for PostgreSQL, Kafka, and Testcontainers.

## Environment

Backend settings are Spring properties and can be supplied as environment variables:

| Variable | Default / purpose |
|---|---|
| `DB_URL` | Local default `jdbc:postgresql://localhost:5433/hookforge`; Compose uses `jdbc:postgresql://postgres:5432/hookforge`. |
| `DB_USERNAME` | Local development database user, default `hookforge`. |
| `DB_PASSWORD` | Local development database password. Override outside a private local environment. |
| `KAFKA_BOOTSTRAP_SERVERS` | Local `localhost:9092`; Compose backend `kafka:29092`. |
| `HOOKFORGE_ENCRYPTION_KEY` | Required Base64 encoding of exactly 32 random bytes for AES-256-GCM. Keep stable for existing endpoint secrets; rotation requires an explicit re-encryption plan. |
| `HOOKFORGE_CORS_ALLOWED_ORIGINS` | Comma-separated browser origins, default `http://localhost:5173`. No wildcard with credentials is used. |
| `VITE_API_BASE_URL` | Frontend API origin, default `http://localhost:8080`. |

Generate a local key in PowerShell:

```powershell
$env:HOOKFORGE_ENCRYPTION_KEY = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
```

Use a secure secret manager for persistent environments. Losing or replacing the encryption key makes existing endpoint secrets undecryptable.

## Start services with Compose

From repository root:

```powershell
$env:HOOKFORGE_ENCRYPTION_KEY = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
docker compose -f infra/docker-compose.yml up --build
```

The stack exposes the API at `http://localhost:8080`, the Vite-built console at `http://localhost:5173`, PostgreSQL at `localhost:5433`, and Kafka at `localhost:9092`. Compose health checks wait for PostgreSQL and Kafka before starting the backend. Flyway applies migrations on backend startup.

For hot reload, start only infrastructure with Compose, then run:

```powershell
docker compose -f infra/docker-compose.yml up -d postgres kafka
cd backend
./mvnw spring-boot:run
```

In a second terminal:

```powershell
cd frontend
Copy-Item .env.example .env.local
npm install
npm run dev
```

Set the same `HOOKFORGE_ENCRYPTION_KEY` in the backend terminal. Configure `HOOKFORGE_CORS_ALLOWED_ORIGINS=http://localhost:5173` if changing the frontend origin.

## Authentication bootstrap constraint

The backend authenticates with bearer API keys. It has no email/password login and no public tenant/key bootstrap endpoint. Tenant administration currently requires an authenticated tenant principal, so a first key must be created by an authorized operator using an established trusted provisioning mechanism. The console accepts a provisioned key in memory only; it does not persist credentials. This bootstrap gap should be addressed before a self-serve deployment.

## Tests and builds

```powershell
cd backend
./mvnw clean test
./mvnw package
cd ../frontend
npm install
npm run build
npm run lint
npm run test
```

Most backend integration tests use Testcontainers and require Docker. The API-key authentication integration test can instead use a local PostgreSQL instance without Docker. It activates the `test` profile and uses `HOOKFORGE_TEST_DB_URL`, `HOOKFORGE_TEST_DB_USERNAME`, and `HOOKFORGE_TEST_DB_PASSWORD` (defaults target `localhost:5433/hookforge` with the local development username/password). It creates an isolated `hookforge_api_key_test` schema for Flyway and JPA; it does not use the application's public schema. For example, set the three `HOOKFORGE_TEST_DB_*` variables in the test shell, then run `./mvnw -Dtest=ApiKeyAuthenticationIntegrationTest test`. Do not point this profile at production. Frontend lint currently uses TypeScript compilation. There is no browser E2E suite configured yet.

## Troubleshooting

- PostgreSQL is exposed on host port `5433` to avoid a common local `5432` collision.
- Kafka uses an internal Compose listener for the backend and an external localhost listener for host tools.
- If API calls fail in the browser, confirm the Vite API URL and the backend allowed-origin setting.
- If startup reports `HOOKFORGE_ENCRYPTION_KEY` missing or malformed, configure Base64 for 32 random bytes; never commit the value.
- If Testcontainers cannot start, check Docker Desktop availability and resource limits.
