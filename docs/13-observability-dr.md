# Observability and recovery

Monitor API latency/errors, JVM/virtual-thread health, JDBC pool saturation, PostgreSQL CPU/memory/connections/storage, queue age/count by status, dead-letter count, reconciliation results, JFrog latency/errors, and webhook-to-activation latency.

Expose queue and dead-letter application metrics from PostgreSQL rather than relying on an external broker dashboard. Alert on oldest available event, processing claims beyond the recovery threshold, dead-letter growth, reconciliation failures, and search latency/query-plan regressions.

PostgreSQL backup and point-in-time restore recover catalog, documents, search state, events, dead letters, settings, and audit history as one consistent unit. JFrog separately protects governed package bytes. Disaster-recovery testing must prove both systems can be restored/reconciled without introducing a second writable catalog authority.
