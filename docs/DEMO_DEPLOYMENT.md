# Public demo deployment and provisioning

The demo is read-only and uses only the configured `HOOKFORGE_DEMO_TENANT_ID`. Public GET controllers never accept a tenant ID from a URL, query, or request body. Provisioning is a PostgreSQL script in the backend image; there is no HTTP seed route and it is disabled unless an operator explicitly runs the script.

## Demo pipeline safety

Migration V9 adds `tenants.is_demo`, defaulting existing tenants to `FALSE`. The operator script creates exactly one tenant marked `TRUE`, five active endpoints on the reserved `.invalid` domain, 40 events, 76 deliveries and 118 attempts. It inserts no API keys and no outbox events. Conflicting IDs/slug or altered fixture endpoints abort the transaction; rerunning an unchanged seed inserts nothing new and does not update records.

Four independent backend guards prevent sample data from sending network requests:

1. The outbox poll query skips events attached to demo tenants.
2. Kafka event routing returns without creating deliveries for a demo tenant.
3. Delivery claim, stale recovery and legacy recovery queries skip demo-tenant rows.
4. Delivery preparation refuses to prepare a demo delivery for the HTTP client.

The fixture URLs use `example.invalid`, and the signing secret has no usable encrypted value. These provide defense in depth; the tenant marker is the decisive worker isolation boundary. Keep the demo tenant separate from customer data and never create an API key for it.

## Render configuration

Set these variables on the **backend web service**, never on Vercel:

| Variable | Value |
|---|---|
| `HOOKFORGE_DEMO_TENANT_ID` | A newly generated, stable UUID reserved for the demo. Use the same UUID for the seed command. Do not use a customer's tenant UUID. |
| `HOOKFORGE_CORS_ALLOWED_ORIGINS` | Exact frontend origin, for example `https://your-app.vercel.app`; preserve any required existing origins as a comma-separated list. |
| `DB_URL` | Existing backend JDBC URL, normally `jdbc:postgresql://...`. The provisioning command below removes the JDBC prefix before calling `psql`. |
| `DB_USERNAME`, `DB_PASSWORD` | Existing database credentials. |
| `HOOKFORGE_ENCRYPTION_KEY` | Existing 32-byte Base64 backend key. |
| `KAFKA_BOOTSTRAP_SERVERS` | Existing backend Kafka configuration. |

Do not copy backend credentials or the demo tenant ID into Vercel. Keep `VITE_API_BASE_URL` as a Vercel **build-time** environment variable pointing to the backend HTTPS origin, for example `https://your-api.onrender.com`. The Vite app uses this variable for API requests.

The Flyway migration runs on backend startup against the configured PostgreSQL database. The database must be reachable using the Render service's configured `DB_URL`/credentials. This change does not create a database or alter Render/Vercel settings automatically.

## Operator provisioning on Render

Deploy the backend revision containing migration V9, the worker guards, `postgresql-client`, and `/app/scripts/provision-demo.sql` first. Wait until the deploy is healthy and Flyway has completed. Generate one UUID using an operator-controlled tool (`uuidgen`), set it as `HOOKFORGE_DEMO_TENANT_ID` in the Render backend service environment, save that change, and let Render deploy it. Do not put this UUID in frontend settings or public docs.

Then run the SQL in an isolated, one-off process based on the deployed backend image. This does not execute the web service start command:

```sh
render jobs create YOUR_RENDER_BACKEND_SERVICE_ID \
  --start-command 'PGPASSWORD="$DB_PASSWORD" psql "${DB_URL#jdbc:}" -U "$DB_USERNAME" -v ON_ERROR_STOP=1 -v demo_tenant_id="$HOOKFORGE_DEMO_TENANT_ID" -f /app/scripts/provision-demo.sql'
```

Use the Render Shell/ephemeral shell on a compatible paid service as an alternative, then run the `psql` command inside that shell. Render documents that one-off jobs inherit the base service's environment and latest successful build, and terminate when their command exits. An ephemeral Shell does not run the service start command. See [Render one-off jobs](https://render.com/docs/one-off-jobs) and [Render Shell/SSH](https://render.com/docs/ssh). Render's internal database URL is preferred when the service and database share a region; this app's `DB_URL` is JDBC-shaped, so the command strips its `jdbc:` prefix for `psql`, and supplies the existing separate username/password variables. See [Render Postgres connections](https://render.com/docs/postgresql-creating-connecting).

The SQL is transactional, deterministic and safe to repeat. It refuses a conflicting tenant slug/ID and refuses incomplete or modified fixture endpoint rows rather than overwriting them. A failed `psql` command exits nonzero; inspect the error and stop rather than changing customer records to make the seed pass. It prints no key or endpoint secret. The demo UUID is not a credential, but keep it out of public documentation.

If the Render service plan does not support Shell/SSH or one-off jobs, run the script with an authorized `psql` client against the Render database using a secure connection and the same `-v demo_tenant_id=... -v ON_ERROR_STOP=1` options. Never put database passwords in source control, Vercel variables, or command history.

## Vercel routing and variables

Set the Vercel project root directory to `frontend`. Set `VITE_API_BASE_URL` to the backend's HTTPS origin for every deployed environment. `frontend/vercel.json` rewrites browser paths to `index.html`, so `/`, `/login`, `/endpoints`, `/events`, and `/deliveries/<id>` load the SPA on direct navigation. Match the backend `HOOKFORGE_CORS_ALLOWED_ORIGINS` to the deployed frontend origin. This repo does not configure deployment domains or deploy either service.

## Verification

1. Confirm the Render backend is healthy and Flyway V9 completed.
2. Run the one-off provisioning command. Re-running it should complete without changing fixture data.
3. Query the demo tenant counts in an authorized operator SQL session: 5 endpoints, 40 events, 76 deliveries and 118 attempts. Confirm it has no API keys and no outbox events for its fixture event IDs.
4. Open `https://your-app.vercel.app/`; confirm the demo banner, dashboard and Login link. Open demo endpoint/event/delivery/detail routes directly and confirm the browser stays on the public demo API.
5. Click Login; confirm `/login` displays the existing API-key field and a return-to-demo link. Authenticate with a real tenant key; confirm the real tenant dashboard uses its regular `/api/v1/**` routes and does not display demo data. Sign out; confirm `/` loads the demo again.
6. Confirm anonymous `GET /api/v1/demo/metrics/summary` works; anonymous `GET /api/v1/endpoints` and anonymous `POST /api/v1/demo/...` are rejected. Repeated demo reads beyond the in-process limit return HTTP 429 with `Retry-After`.

The rate limiter is bounded and per backend process. Multiple Render instances have independent in-memory windows; this is not a globally coordinated rate limit. No public API key or customer endpoint can alter demo records through the demo controllers.
