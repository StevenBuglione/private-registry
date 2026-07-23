# Operations runbook

## Local

From `repositories/private-registry-api`:

```powershell
docker compose up --build --detach --wait
docker compose --profile seed run --rm seeder
docker compose restart api
```

Verify `/health/ready`, `/health/worker`, PostgreSQL search/documents, queue and dead-letter counts, JFrog repository properties, authenticated UI behavior, and browser console/network state.

## Production rollout

1. Apply the PostgreSQL-centered platform foundation with services disabled.
2. Publish immutable UI/API images and run Flyway.
3. Deploy UI and combined API/worker services.
4. Configure signed JFrog webhooks.
5. Run a full reconciliation.
6. Prove a signed event is accepted, processed, searchable, and visible through SSE/UI.
7. Prove a terminal test event is inspectable in the PostgreSQL dead-letter view.

## Routine operations

- Review database queue age, retries, and dead letters.
- Review reconciliation reports and JFrog dependency health.
- Test PostgreSQL restore and JFrog reconciliation regularly.
- Roll back application images without reverting forward-compatible migrations.
