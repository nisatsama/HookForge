# Changelog

## Unreleased — HookForge full stack

- Added an integrated React and TypeScript console with API-key session auth, live API queries, delivery history/replay, endpoint management, event publishing/inspection, key metadata, and settings.
- Added typed API clients, TanStack Query state, validated forms, responsive dark UI, tests, and frontend/backend container build files.
- Fixed delivery tenant extraction to use Spring Security's authenticated principal.
- Added tenant-scoped paginated event history and an aggregate delivery metrics route while preserving the existing uncommitted deletion of `MetricsController.java`.
- Added tenant-scoped API-key metadata listing and explicit configurable CORS.
- Extended Compose to start Postgres, dual-listener Kafka, backend, and frontend with readiness checks.
- Added the API contract, audit, local development guide, and this implementation log.

Known gaps are documented in `docs/BACKEND_AUDIT.md` and `docs/API_CONTRACT.md`. Initial tenant/key provisioning remains an operator prerequisite; the console does not invent a password login or public bootstrap path.
