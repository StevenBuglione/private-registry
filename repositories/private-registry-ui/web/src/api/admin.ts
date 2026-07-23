import { z } from "zod";
import type {
  AdminDashboard,
  AuditEvent,
  CreatedSyncCredential,
  CreateSyncCredential,
  OperationalEvent,
  SyncCredential,
  TrafficReport,
} from "../types";
import { csrfHeaders, request } from "./client";

const reconciliationSchema = z
  .object({
    id: z.string().min(1),
    mode: z.string(),
    scope: z.string(),
    status: z.string(),
    discrepancies: z.number().int().nonnegative(),
    repaired: z.number().int().nonnegative(),
    started_at: z.string().min(1),
    completed_at: z.string().min(1).optional(),
  })
  .strict();

const dashboardSchema = z
  .object({
    generated_at: z.string().min(1),
    status: z.enum(["healthy", "degraded"]),
    worker_enabled: z.boolean(),
    dependencies: z.record(z.string(), z.string()),
    catalog: z
      .object({
        providers: z.number().int().nonnegative(),
        modules: z.number().int().nonnegative(),
        active_versions: z.number().int().nonnegative(),
        documents: z.number().int().nonnegative(),
        downloads: z.number().int().nonnegative(),
      })
      .strict(),
    queue: z
      .object({
        queued: z.number().int().nonnegative(),
        processing: z.number().int().nonnegative(),
        retry: z.number().int().nonnegative(),
        completed: z.number().int().nonnegative(),
        dead_letter: z.number().int().nonnegative(),
      })
      .strict(),
    ingestion: z
      .object({
        completed: z.number().int().nonnegative(),
        failed: z.number().int().nonnegative(),
        quarantined: z.number().int().nonnegative(),
        latency_p95_ms: z.number().nonnegative(),
        last_completed_at: z.string().min(1).optional(),
      })
      .strict(),
    reconciliation: reconciliationSchema.optional(),
    database_size_bytes: z.number().nonnegative(),
  })
  .strict()
  .transform(
    (wire): AdminDashboard => ({
      generatedAt: wire.generated_at,
      status: wire.status,
      workerEnabled: wire.worker_enabled,
      dependencies: wire.dependencies,
      catalog: {
        providers: wire.catalog.providers,
        modules: wire.catalog.modules,
        activeVersions: wire.catalog.active_versions,
        documents: wire.catalog.documents,
        downloads: wire.catalog.downloads,
      },
      queue: {
        queued: wire.queue.queued,
        processing: wire.queue.processing,
        retry: wire.queue.retry,
        completed: wire.queue.completed,
        deadLetter: wire.queue.dead_letter,
      },
      ingestion: {
        completed: wire.ingestion.completed,
        failed: wire.ingestion.failed,
        quarantined: wire.ingestion.quarantined,
        latencyP95Ms: wire.ingestion.latency_p95_ms,
        lastCompletedAt: wire.ingestion.last_completed_at,
      },
      reconciliation:
        wire.reconciliation === undefined
          ? undefined
          : {
              id: wire.reconciliation.id,
              mode: wire.reconciliation.mode,
              scope: wire.reconciliation.scope,
              status: wire.reconciliation.status,
              discrepancies: wire.reconciliation.discrepancies,
              repaired: wire.reconciliation.repaired,
              startedAt: wire.reconciliation.started_at,
              completedAt: wire.reconciliation.completed_at,
            },
      databaseSizeBytes: wire.database_size_bytes,
    }),
  );

const trafficSchema = z
  .object({
    generated_at: z.string().min(1),
    days: z.number().int().positive(),
    summary: z
      .object({
        page_views: z.number().int().nonnegative(),
        unique_visitors: z.number().int().nonnegative(),
        page_views_today: z.number().int().nonnegative(),
        visitors_today: z.number().int().nonnegative(),
      })
      .strict(),
    daily: z.array(
      z
        .object({
          day: z.string().min(1),
          page_views: z.number().int().nonnegative(),
          unique_visitors: z.number().int().nonnegative(),
        })
        .strict(),
    ),
    top_routes: z.array(
      z
        .object({
          path: z.string().min(1),
          page_views: z.number().int().nonnegative(),
          unique_visitors: z.number().int().nonnegative(),
          last_viewed_at: z.string().min(1),
        })
        .strict(),
    ),
    visitors: z.array(
      z
        .object({
          subject: z.string().min(1),
          display_name: z.string().min(1),
          email: z.string().min(1).optional(),
          page_views: z.number().int().nonnegative(),
          first_seen_at: z.string().min(1),
          last_seen_at: z.string().min(1),
          last_path: z.string().min(1),
        })
        .strict(),
    ),
    recent_access: z.array(
      z
        .object({
          subject: z.string().min(1),
          display_name: z.string().min(1),
          email: z.string().min(1).optional(),
          path: z.string().min(1),
          occurred_at: z.string().min(1),
        })
        .strict(),
    ),
  })
  .strict()
  .transform(
    (wire): TrafficReport => ({
      generatedAt: wire.generated_at,
      days: wire.days,
      summary: {
        pageViews: wire.summary.page_views,
        uniqueVisitors: wire.summary.unique_visitors,
        pageViewsToday: wire.summary.page_views_today,
        visitorsToday: wire.summary.visitors_today,
      },
      daily: wire.daily.map((value) => ({
        day: value.day,
        pageViews: value.page_views,
        uniqueVisitors: value.unique_visitors,
      })),
      topRoutes: wire.top_routes.map((value) => ({
        path: value.path,
        pageViews: value.page_views,
        uniqueVisitors: value.unique_visitors,
        lastViewedAt: value.last_viewed_at,
      })),
      visitors: wire.visitors.map((value) => ({
        subject: value.subject,
        displayName: value.display_name,
        email: value.email,
        pageViews: value.page_views,
        firstSeenAt: value.first_seen_at,
        lastSeenAt: value.last_seen_at,
        lastPath: value.last_path,
      })),
      recentAccess: wire.recent_access.map((value) => ({
        subject: value.subject,
        displayName: value.display_name,
        email: value.email,
        path: value.path,
        occurredAt: value.occurred_at,
      })),
    }),
  );

const operationalEventSchema = z
  .object({
    source: z.enum(["ingestion", "queue", "reconciliation"]),
    event_id: z.string().min(1),
    status: z.string(),
    title: z.string(),
    detail: z.string(),
    repository: z.string().optional(),
    path: z.string().optional(),
    correlation_id: z.string(),
    occurred_at: z.string().min(1),
  })
  .strict()
  .transform(
    (wire): OperationalEvent => ({
      source: wire.source,
      eventId: wire.event_id,
      status: wire.status,
      title: wire.title,
      detail: wire.detail,
      repository: wire.repository,
      path: wire.path,
      correlationId: wire.correlation_id,
      occurredAt: wire.occurred_at,
    }),
  );

const auditEventSchema = z
  .object({
    id: z.string().min(1),
    occurred_at: z.string().min(1),
    actor_type: z.string(),
    actor_id: z.string(),
    action: z.string(),
    resource_type: z.string(),
    resource_id: z.string(),
    correlation_id: z.string(),
    detail: z.unknown(),
  })
  .strict()
  .transform(
    (wire): AuditEvent => ({
      id: wire.id,
      occurredAt: wire.occurred_at,
      actorType: wire.actor_type,
      actorId: wire.actor_id,
      action: wire.action,
      resourceType: wire.resource_type,
      resourceId: wire.resource_id,
      correlationId: wire.correlation_id,
      detail: wire.detail,
    }),
  );

const credentialScope = {
  MODULE: "module",
  PROVIDER: "provider",
  ALL: "all",
} as const;

const credentialStatus = {
  ACTIVE: "active",
  EXPIRED: "expired",
  REVOKED: "revoked",
} as const;

const syncCredentialSchema = z
  .object({
    id: z.string().min(1),
    name: z.string().min(1),
    scope: z.enum(["MODULE", "PROVIDER", "ALL"]),
    key_prefix: z.string().min(1),
    created_by: z.string().min(1),
    created_at: z.string().min(1),
    expires_at: z.string().min(1),
    revoked_at: z.string().min(1).optional(),
    revoked_by: z.string().min(1).optional(),
    last_used_at: z.string().min(1).optional(),
    use_count: z.number().int().nonnegative(),
    status: z.enum(["ACTIVE", "EXPIRED", "REVOKED"]),
  })
  .strict()
  .transform(
    (wire): SyncCredential => ({
      id: wire.id,
      name: wire.name,
      scope: credentialScope[wire.scope],
      keyPrefix: wire.key_prefix,
      createdBy: wire.created_by,
      createdAt: wire.created_at,
      expiresAt: wire.expires_at,
      revokedAt: wire.revoked_at,
      revokedBy: wire.revoked_by,
      lastUsedAt: wire.last_used_at,
      useCount: wire.use_count,
      status: credentialStatus[wire.status],
    }),
  );

const createdCredentialSchema = z
  .object({
    credential: syncCredentialSchema,
    token: z.string().min(1),
  })
  .strict();

export async function getAdminDashboard(): Promise<AdminDashboard> {
  return request("/admin/dashboard", dashboardSchema);
}

export async function getTrafficReport(days: number): Promise<TrafficReport> {
  const parameters = new URLSearchParams({
    days: String(days),
    visitorLimit: "50",
  });
  return request(`/admin/traffic?${parameters.toString()}`, trafficSchema);
}

export async function getAdminOperations(): Promise<OperationalEvent[]> {
  return request("/admin/operations?limit=75", z.array(operationalEventSchema));
}

export async function getAuditEvents(): Promise<AuditEvent[]> {
  return request("/admin/audit-events?limit=75", z.array(auditEventSchema));
}

export async function getSyncCredentials(): Promise<SyncCredential[]> {
  return request("/admin/sync-credentials", z.array(syncCredentialSchema));
}

export async function createSyncCredential(
  value: CreateSyncCredential,
  csrfToken?: string,
): Promise<CreatedSyncCredential> {
  return request("/admin/sync-credentials", createdCredentialSchema, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...csrfHeaders(csrfToken),
    },
    body: JSON.stringify({
      name: value.name,
      scope: value.scope,
      expires_in_days: value.expiresInDays,
    }),
  });
}

export async function revokeSyncCredential(
  id: string,
  csrfToken?: string,
): Promise<SyncCredential> {
  return request(
    `/admin/sync-credentials/${encodeURIComponent(id)}`,
    syncCredentialSchema,
    { method: "DELETE", headers: csrfHeaders(csrfToken) },
  );
}
