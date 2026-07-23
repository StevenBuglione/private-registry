# Registry administration

The Registry administration workspace is available at `/admin` to Microsoft Entra users who resolve to the configured `registry-admin` group. All administration APIs re-check the server-side access context; hiding navigation in the browser is not treated as authorization.

## Workspace

The workspace includes:

- **Overview** — PostgreSQL and Artifactory dependency status, catalog totals, current queue depth, retry and dead-letter counts, 24-hour ingestion outcomes, P95 ingestion latency, database size, and the most recent reconciliation.
- **Homepage** — the public notification and up to six featured providers. Each save records complete before-and-after audit evidence.
- **Sync credentials** — scoped, expiring credentials for trusted GitHub runners. A token is displayed once; only its SHA-256 digest is stored.
- **Operational logs** — structured ingestion, retry, dead-letter, quarantine, and reconciliation events. Raw event payloads, process logs, access tokens, and secrets are intentionally excluded.
- **Audit log** — immutable records for homepage updates, credential creation and revocation, and every runner-triggered sync.

Entra remains the system of record for administrators and APM memberships. The Registry does not duplicate group or user administration.

## Create a GitHub runner credential

1. Sign in as a Registry administrator.
2. Open the user menu, select **Admin settings**, then **Sync credentials**.
3. Choose a descriptive name, the narrowest package scope, and an expiration.
4. Copy the token immediately into a masked GitHub Actions secret such as `REGISTRY_SYNC_TOKEN`.
5. Revoke the credential from the same page when the workflow is retired or the token may have been exposed.

Tokens use 256 bits of cryptographic randomness, are limited to 365 days, can be revoked immediately, and are never recoverable from the database.

## GitHub Actions request

Call the sync endpoint only after the package archive and its ready catalog manifest have been published to Artifactory:

```yaml
permissions:
  contents: read

steps:
  - name: Notify Registry
    env:
      REGISTRY_URL: ${{ vars.REGISTRY_URL }}
      REGISTRY_SYNC_TOKEN: ${{ secrets.REGISTRY_SYNC_TOKEN }}
    shell: bash
    run: |
      curl --fail-with-body \
        --request POST \
        --url "${REGISTRY_URL}/api/v1/sync/artifacts" \
        --header "Authorization: Bearer ${REGISTRY_SYNC_TOKEN}" \
        --header "Idempotency-Key: ${GITHUB_RUN_ID}-${GITHUB_RUN_ATTEMPT}" \
        --header "Content-Type: application/json" \
        --data '{
          "kind": "module",
          "repository": "iac-module-release-local",
          "path": "Azure/vnet/azurerm/1.0.0.zip"
        }'
```

Provider credentials may target only `iac-provider-release-local`; module credentials may target only `iac-module-release-local`. The request is idempotent for a credential and `Idempotency-Key` pair. A `202 Accepted` response means PostgreSQL durably accepted the reconciliation hint—not that indexing has already completed.

## Response and failure handling

| Status | Meaning |
| --- | --- |
| `202` | The event was durably queued. |
| `400` | The kind, repository, path, action, or idempotency key is invalid. |
| `401` | The token is invalid, expired, or revoked. |
| `403` | The token scope does not permit the requested package kind. |
| `503` | Durable ingestion is disabled or unavailable. |

GitHub workflows should retry `503` and transient transport failures with bounded exponential backoff. They should not retry `400`, `401`, or `403` until the request or credential is corrected.

## Operational response

- Investigate any nonzero dead-letter or quarantine count.
- Correlate operational and audit records with the request ID or event ID.
- Rotate runner credentials before expiration and immediately after suspected exposure.
- Keep GitHub environment protections and required reviewers on workflows that publish governed artifacts.
- Use the signed JFrog webhook as the primary deployment notification when the Registry is reachable from JFrog; runner triggers are a supported explicit alternative and use the same durable queue.
