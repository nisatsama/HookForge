# HookForge

HookForge accepts tenant-scoped application events, records each event and its transactional outbox row together, publishes outbox messages to Kafka, creates one delivery per active tenant endpoint, and asynchronously sends signed HTTP requests with retry and replay support.

## What is implemented

- Spring Boot API with stateless tenant API-key authentication.
- PostgreSQL persistence and Flyway migrations.
- Transactional outbox and Kafka event routing.
- Tenant-scoped endpoint, event, delivery and API-key management.
- AES-GCM encrypted endpoint secrets and HMAC-signed requests.
- React + TypeScript console integrated with the live API.

See [docs/LOCAL_DEVELOPMENT.md](docs/LOCAL_DEVELOPMENT.md), [docs/API_CONTRACT.md](docs/API_CONTRACT.md), and [docs/BACKEND_AUDIT.md](docs/BACKEND_AUDIT.md).

## Start the application

Requirements: Java 21, Node.js 22.12+, Docker Desktop with Compose, and PowerShell (commands below). Create a 32-byte AES key and set the environment variable for the Compose backend:

```powershell
$env:HOOKFORGE_ENCRYPTION_KEY = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
docker compose -f infra/docker-compose.yml up --build
```

Open the console at http://localhost:5173. The initial API key must be provisioned through a trusted operator path; the current repository has tenant and key services but no safe self-service bootstrap flow. The admin tenant creation route is authenticated as well. Do not expose database access or create a public bootstrap endpoint to work around this limitation.

For local development without the frontend container, see the detailed setup guide. The backend requires `HOOKFORGE_ENCRYPTION_KEY`; PostgreSQL, Kafka, CORS, and API URL settings are documented there.

## Verification

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

Testcontainers integration tests require Docker to be available to the Java process. See [the audit](docs/BACKEND_AUDIT.md) for known backend limitations and open risks.
