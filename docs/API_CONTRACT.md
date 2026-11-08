# API contract

All API routes are under `/api/v1`. Except the explicitly noted operational routes, requests require `Authorization: Bearer <API_KEY>`. The authenticated API key determines the tenant; clients must not supply a tenant ID to select scope. Unknown and cross-tenant resource IDs are returned as not found where tenant-scoped lookups apply.

Common errors are JSON with `timestamp`, `status`, `error`, `message`, and `path`. Validation/malformed input returns 400, missing/invalid credentials 401, forbidden access 403, missing tenant-owned resources 404, idempotency/replay conflicts 409, and unexpected failures 500. Pagination uses zero-based `page` and `size` (max 100).

| Method | Endpoint | Auth | Request | Response / tenant behavior |
|---|---|---|---|---|
| GET | `/actuator/health` | No | — | Health status. |
| GET | `/actuator/info` | No | — | Application information. |
| POST | `/api/v1/admin/tenants` | Yes in current security chain | `{ "name": "...", "slug": "..." }` | Tenant metadata. Existing route does not bootstrap a first key. |
| GET | `/api/v1/endpoints` | Yes | — | Tenant's endpoint DTOs; no secret on list. |
| POST | `/api/v1/endpoints` | Yes | `{ "name": "...", "url": "https://..." }` | 201 and endpoint DTO with `secret` returned once. |
| GET | `/api/v1/endpoints/{id}` | Yes | — | Tenant-scoped endpoint DTO. |
| PUT | `/api/v1/endpoints/{id}` | Yes | `{ "name": "...", "url": "https://..." }` | Updated tenant-owned endpoint. |
| PATCH | `/api/v1/endpoints/{id}/enable` | Yes | — | Enables after destination revalidation. |
| PATCH | `/api/v1/endpoints/{id}/disable` | Yes | — | Disables endpoint. |
| POST | `/api/v1/events` | Yes | `{ "eventType": "...", "payload": {} }`, optional `Idempotency-Key` | 201 new event or 200 identical idempotent replay. Writes event and outbox atomically. |
| GET | `/api/v1/events?page=0&size=20` | Yes | — | Tenant-scoped `{items,page,size,totalElements,totalPages,last}`. |
| GET | `/api/v1/events/{id}` | Yes | — | Tenant-scoped event DTO including payload. |
| GET | `/api/v1/deliveries?page=0&size=20&status=&eventId=&endpointId=` | Yes | — | Tenant-scoped page of delivery DTOs. |
| GET | `/api/v1/deliveries/{id}` | Yes | — | Tenant-scoped delivery metadata. |
| GET | `/api/v1/deliveries/{id}/attempts` | Yes | — | Tenant-scoped attempt history. Attempt response bodies/error details are currently included. |
| POST | `/api/v1/deliveries/{id}/replay` | Yes | — | 200 when allowed, 404 if not tenant-owned, 409 when current state cannot replay. |
| GET | `/api/v1/metrics/summary` | Yes | — | Tenant-scoped delivery totals by state. Does not include event time series or delivery success percentage. |
| GET | `/api/v1/api-keys` | Yes | — | Tenant-scoped metadata only: ID, name, key prefix, created/expiry/revocation/last-used timestamps. |
| POST | `/api/v1/api-keys` | Yes | `{ "name": "...", "expiresAt": "optional ISO-8601 instant" }` | 201 and raw `secretKey` once. Only hash/prefix persist. |
| POST | `/api/v1/api-keys/{id}/revoke` | Yes | — | 204; revocation is tenant-scoped. |

## Event publishing and delivery

The API commits the event and outbox row in one database transaction. The polling publisher sends the outbox event to Kafka; the consumer routes it to active tenant endpoints. Delivery uniqueness on `(event_id, endpoint_id)` makes routing idempotent. Workers claim due deliveries using PostgreSQL row locks with `SKIP LOCKED`, send signed requests, and record attempts. Eligible failed/dead deliveries can be replayed by their tenant.

## CORS

Allowed browser origins come from `HOOKFORGE_CORS_ALLOWED_ORIGINS`, defaulting to `http://localhost:5173`. Allowed request headers include `Authorization`, `Content-Type`, and `Idempotency-Key`. Credentials are disabled and wildcard origins are not used.

## Current contract gaps

- No public/trusted bootstrap flow exists to issue the first API key.
- Endpoint subscriptions and endpoint deletion are not implemented by the backend.
- Metrics expose state counts only; no time-series volume or success-rate endpoint exists.
- There is no event filtering/search endpoint beyond tenant-scoped pagination.
- A response duration is present on attempt DTOs; destination URL is available from endpoint data and joined client-side by endpoint ID.
