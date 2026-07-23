CREATE TABLE apm_contexts (
    apm_id varchar(128) PRIMARY KEY,
    display_name varchar(200) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT apm_contexts_id_check CHECK (apm_id ~ '^APM[0-9]{7}$'),
    CONSTRAINT apm_contexts_display_name_check CHECK (btrim(display_name) <> '')
);

INSERT INTO apm_contexts (apm_id, display_name)
SELECT source.apm_id,
       COALESCE(
           max(source.display_name) FILTER (WHERE source.display_name <> source.apm_id),
           source.apm_id)
  FROM (
        SELECT apm_id, display_name
          FROM identity_group_entitlements
         WHERE apm_id IS NOT NULL
        UNION ALL
        SELECT apm_id, apm_id
          FROM package_apm_access
       ) source
 GROUP BY source.apm_id
ON CONFLICT (apm_id) DO NOTHING;

CREATE OR REPLACE FUNCTION registry_ensure_apm_context()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    requested_display_name text;
BEGIN
    IF NEW.apm_id IS NULL THEN
        RETURN NEW;
    END IF;
    requested_display_name :=
        COALESCE(NULLIF(to_jsonb(NEW) ->> 'display_name', ''), NEW.apm_id);
    INSERT INTO apm_contexts (apm_id, display_name)
    VALUES (NEW.apm_id, requested_display_name)
    ON CONFLICT (apm_id) DO UPDATE SET
        display_name = CASE
            WHEN EXCLUDED.display_name <> EXCLUDED.apm_id
                THEN EXCLUDED.display_name
            ELSE apm_contexts.display_name
        END,
        updated_at = now();
    RETURN NEW;
END;
$$;

CREATE TRIGGER identity_group_entitlements_apm_context_trigger
BEFORE INSERT OR UPDATE OF apm_id, display_name
ON identity_group_entitlements
FOR EACH ROW
EXECUTE FUNCTION registry_ensure_apm_context();

CREATE TRIGGER package_apm_access_apm_context_trigger
BEFORE INSERT OR UPDATE OF apm_id
ON package_apm_access
FOR EACH ROW
EXECUTE FUNCTION registry_ensure_apm_context();

ALTER TABLE identity_group_entitlements
    ADD CONSTRAINT identity_group_entitlements_apm_context_fk
    FOREIGN KEY (apm_id) REFERENCES apm_contexts(apm_id)
    ON UPDATE CASCADE
    NOT VALID;

ALTER TABLE package_apm_access
    ADD CONSTRAINT package_apm_access_apm_context_fk
    FOREIGN KEY (apm_id) REFERENCES apm_contexts(apm_id)
    ON UPDATE CASCADE
    NOT VALID;

ALTER TABLE identity_group_entitlements
    VALIDATE CONSTRAINT identity_group_entitlements_apm_context_fk;

ALTER TABLE package_apm_access
    VALIDATE CONSTRAINT package_apm_access_apm_context_fk;

ALTER TABLE packages
    ADD CONSTRAINT packages_id_kind_unique UNIQUE (id, kind);

CREATE TABLE registry_homepage_features (
    feature_kind package_kind NOT NULL,
    package_id uuid NOT NULL,
    display_order smallint NOT NULL CHECK (display_order >= 0),
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (feature_kind, package_id),
    UNIQUE (feature_kind, display_order),
    CONSTRAINT registry_homepage_features_package_kind_fk
        FOREIGN KEY (package_id, feature_kind)
        REFERENCES packages(id, kind)
        ON DELETE CASCADE
);

INSERT INTO registry_homepage_features (feature_kind, package_id, display_order)
SELECT 'provider',
       package_record.id,
       min(selected.ordinality) - 1
  FROM registry_homepage_settings settings
 CROSS JOIN LATERAL
       unnest(string_to_array(settings.featured_provider_ids, ','))
       WITH ORDINALITY selected(value, ordinality)
  JOIN packages package_record
    ON package_record.kind = 'provider'
   AND 'provider/' || package_record.namespace || '/' || package_record.name =
       btrim(selected.value)
 GROUP BY package_record.id;

INSERT INTO registry_homepage_features (feature_kind, package_id, display_order)
SELECT 'module',
       package_record.id,
       min(selected.ordinality) - 1
  FROM registry_homepage_settings settings
 CROSS JOIN LATERAL
       unnest(string_to_array(settings.featured_module_ids, ','))
       WITH ORDINALITY selected(value, ordinality)
  JOIN packages package_record
    ON package_record.kind = 'module'
   AND 'module/' || package_record.namespace || '/' || package_record.name || '/' ||
       package_record.target = btrim(selected.value)
 GROUP BY package_record.id;

UPDATE registry_homepage_settings
   SET notification_message =
       regexp_replace(notification_message, '\mapproved\M', 'available', 'gi')
 WHERE notification_message ~* '\mapproved\M.*\m(providers|modules)\M';

ALTER TABLE registry_homepage_settings
    ADD CONSTRAINT registry_homepage_notification_link_pair_check
    CHECK (
        (notification_link_label IS NULL AND notification_link_url IS NULL)
        OR
        (btrim(notification_link_label) <> '' AND btrim(notification_link_url) <> '')
    ) NOT VALID;

ALTER TABLE registry_homepage_settings
    VALIDATE CONSTRAINT registry_homepage_notification_link_pair_check;

CREATE TABLE registry_categories (
    slug varchar(64) PRIMARY KEY,
    display_name varchar(120) NOT NULL,
    description varchar(300) NOT NULL,
    CONSTRAINT registry_categories_slug_check
        CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    CONSTRAINT registry_categories_display_name_check
        CHECK (btrim(display_name) <> ''),
    CONSTRAINT registry_categories_description_check
        CHECK (btrim(description) <> '')
);

INSERT INTO registry_categories (slug, display_name, description)
VALUES
    ('asset-management', 'Asset Management', 'Asset inventory and lifecycle management.'),
    ('cloud-automation', 'Cloud Automation', 'Cloud provisioning and automation.'),
    ('communication-messaging', 'Communication and Messaging', 'Messaging and communication services.'),
    ('container-orchestration', 'Container Orchestration', 'Container platforms and orchestration.'),
    ('ci-cd', 'CI/CD', 'Continuous integration and delivery.'),
    ('data-management', 'Data Management', 'Data platforms and governance.'),
    ('database', 'Database', 'Database services and administration.'),
    ('infrastructure', 'Infrastructure', 'Core infrastructure services.'),
    ('logging-monitoring', 'Logging and Monitoring', 'Observability, logging, and monitoring.'),
    ('networking', 'Networking', 'Network infrastructure and connectivity.'),
    ('platform', 'Platform', 'Internal and external platform services.'),
    ('security-authentication', 'Security and Authentication', 'Security, identity, and authentication.'),
    ('utility', 'Utility', 'General-purpose utility providers and modules.'),
    ('vcs', 'Version Control', 'Version-control systems and integrations.'),
    ('web-services', 'Web Services', 'Web application and API services.'),
    ('hashicorp-platform', 'HashiCorp Platform', 'HashiCorp platform services.'),
    ('infrastructure-management', 'Infrastructure Management', 'Infrastructure management workflows.'),
    ('public-cloud', 'Public Cloud', 'Public cloud services and infrastructure.');

CREATE TABLE package_categories (
    package_id uuid NOT NULL REFERENCES packages(id) ON DELETE CASCADE,
    category_slug varchar(64) NOT NULL REFERENCES registry_categories(slug)
        ON UPDATE CASCADE,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (package_id, category_slug)
);

CREATE INDEX package_categories_category_package_idx
    ON package_categories (category_slug, package_id);

CREATE OR REPLACE FUNCTION registry_sync_package_categories()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF EXISTS (
        SELECT 1
          FROM unnest(NEW.categories) selected(slug)
          LEFT JOIN registry_categories category ON category.slug = selected.slug
         WHERE category.slug IS NULL
    ) THEN
        RAISE EXCEPTION 'packages.categories contains an unknown category'
            USING ERRCODE = 'check_violation';
    END IF;
    IF cardinality(NEW.categories) <> (
        SELECT count(DISTINCT selected.slug)
          FROM unnest(NEW.categories) selected(slug)
    ) THEN
        RAISE EXCEPTION 'packages.categories must not contain duplicates'
            USING ERRCODE = 'check_violation';
    END IF;

    DELETE FROM package_categories WHERE package_id = NEW.id;
    INSERT INTO package_categories (package_id, category_slug)
    SELECT NEW.id, selected.slug
      FROM unnest(NEW.categories) selected(slug);
    RETURN NEW;
END;
$$;

CREATE TRIGGER packages_categories_sync_trigger
AFTER INSERT OR UPDATE OF categories
ON packages
FOR EACH ROW
EXECUTE FUNCTION registry_sync_package_categories();

UPDATE packages SET categories = categories;

CREATE TABLE registry_traffic_identities (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    subject varchar(255) NOT NULL UNIQUE,
    display_name varchar(200) NOT NULL,
    email varchar(320),
    first_seen_at timestamptz NOT NULL,
    last_seen_at timestamptz NOT NULL,
    CONSTRAINT registry_traffic_identities_subject_check CHECK (btrim(subject) <> ''),
    CONSTRAINT registry_traffic_identities_display_name_check CHECK (btrim(display_name) <> ''),
    CONSTRAINT registry_traffic_identities_seen_order_check CHECK (last_seen_at >= first_seen_at)
);

INSERT INTO registry_traffic_identities (
    subject, display_name, email, first_seen_at, last_seen_at)
SELECT subject,
       (array_agg(display_name ORDER BY occurred_at DESC, id DESC))[1],
       (array_agg(email ORDER BY occurred_at DESC, id DESC)
           FILTER (WHERE email IS NOT NULL))[1],
       min(occurred_at),
       max(occurred_at)
  FROM registry_page_views
 GROUP BY subject;

ALTER TABLE registry_page_views
    ADD COLUMN identity_id uuid;

UPDATE registry_page_views page_view
   SET identity_id = identity.id
  FROM registry_traffic_identities identity
 WHERE identity.subject = page_view.subject;

ALTER TABLE registry_page_views
    ALTER COLUMN identity_id SET NOT NULL,
    ADD CONSTRAINT registry_page_views_identity_fk
        FOREIGN KEY (identity_id) REFERENCES registry_traffic_identities(id)
        ON DELETE RESTRICT;

CREATE INDEX registry_page_views_identity_occurred_at_idx
    ON registry_page_views (identity_id, occurred_at DESC, id DESC);

CREATE OR REPLACE FUNCTION registry_resolve_traffic_identity()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.identity_id IS NOT NULL THEN
        RETURN NEW;
    END IF;
    INSERT INTO registry_traffic_identities (
        subject, display_name, email, first_seen_at, last_seen_at)
    VALUES (
        NEW.subject, NEW.display_name, NEW.email, NEW.occurred_at, NEW.occurred_at)
    ON CONFLICT (subject) DO UPDATE SET
        display_name = EXCLUDED.display_name,
        email = COALESCE(EXCLUDED.email, registry_traffic_identities.email),
        first_seen_at = LEAST(
            registry_traffic_identities.first_seen_at,
            EXCLUDED.first_seen_at),
        last_seen_at = GREATEST(
            registry_traffic_identities.last_seen_at,
            EXCLUDED.last_seen_at)
    RETURNING id INTO NEW.identity_id;
    RETURN NEW;
END;
$$;

CREATE TRIGGER registry_page_views_identity_trigger
BEFORE INSERT OR UPDATE OF subject, display_name, email, occurred_at
ON registry_page_views
FOR EACH ROW
EXECUTE FUNCTION registry_resolve_traffic_identity();

CREATE OR REPLACE FUNCTION registry_remove_orphan_traffic_identities()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    DELETE FROM registry_traffic_identities identity
     WHERE identity.id IN (
               SELECT DISTINCT deleted.identity_id
                 FROM deleted_registry_page_views deleted
           )
       AND NOT EXISTS (
           SELECT 1
             FROM registry_page_views page_view
            WHERE page_view.identity_id = identity.id
       );
    RETURN NULL;
END;
$$;

CREATE TRIGGER registry_page_views_identity_cleanup_trigger
AFTER DELETE
ON registry_page_views
REFERENCING OLD TABLE AS deleted_registry_page_views
FOR EACH STATEMENT
EXECUTE FUNCTION registry_remove_orphan_traffic_identities();

ALTER TABLE catalog_event_queue
    ADD COLUMN semantic_key text,
    ADD COLUMN claim_token uuid;

UPDATE catalog_event_queue
   SET status = 'dead_letter',
       attempts = GREATEST(attempts, 1),
       claimed_at = NULL,
       claim_token = NULL,
       completed_at = GREATEST(COALESCE(completed_at, updated_at, created_at), created_at),
       failure_code = 'legacy_payload_invalid',
       failure_detail = 'V119 rejected a malformed or noncanonical legacy queue payload',
       updated_at = GREATEST(updated_at, created_at)
 WHERE jsonb_typeof(payload) IS DISTINCT FROM 'object'
    OR payload ->> 'schema_version' IS DISTINCT FROM '1'
    OR COALESCE(btrim(payload ->> 'event_id'), '') = ''
    OR COALESCE(payload ->> 'action', '') NOT IN (
        'DEPLOYED',
        'PROPERTIES_CHANGED',
        'DELETED',
        'MOVED',
        'COPIED'
    )
    OR COALESCE(btrim(payload ->> 'origin'), '') = ''
    OR COALESCE(btrim(payload ->> 'subscription_id'), '') = ''
    OR COALESCE(btrim(payload ->> 'repository'), '') = ''
    OR COALESCE(btrim(payload ->> 'path'), '') = ''
    OR payload ->> 'path' LIKE '/%'
    OR payload ->> 'path' LIKE '%..%'
    OR position(E'\\' IN COALESCE(payload ->> 'path', '')) > 0
    OR COALESCE(btrim(payload ->> 'occurred_at'), '') = ''
    OR COALESCE(btrim(payload ->> 'correlation_id'), '') = ''
    OR (
        payload ? 'properties'
        AND jsonb_typeof(payload -> 'properties') IS DISTINCT FROM 'object'
    );

UPDATE catalog_event_queue
   SET payload = jsonb_build_object(
       'schema_version', 1,
       'legacy_payload', payload)
 WHERE jsonb_typeof(payload) <> 'object';

UPDATE catalog_event_queue
   SET payload = jsonb_set(
       payload - 'schemaVersion',
       '{schema_version}',
       '1'::jsonb)
 WHERE payload ->> 'schema_version' IS DISTINCT FROM '1';

WITH calculated AS (
    SELECT id,
           event_id,
           'sha256:' || encode(
               digest(
                   length(COALESCE(
                       payload ->> 'schema_version',
                       payload ->> 'schemaVersion',
                       '1'))::text || ':' ||
                       COALESCE(
                           payload ->> 'schema_version',
                           payload ->> 'schemaVersion',
                           '1') || ';' ||
                   length(COALESCE(payload ->> 'action', ''))::text || ':' ||
                       COALESCE(payload ->> 'action', '') || ';' ||
                   length(COALESCE(payload ->> 'repository', ''))::text || ':' ||
                       COALESCE(payload ->> 'repository', '') || ';' ||
                   length(COALESCE(payload ->> 'path', ''))::text || ':' ||
                       COALESCE(payload ->> 'path', '') || ';' ||
                   length(COALESCE(
                       payload ->> 'occurred_at',
                       payload ->> 'occurredAt',
                       event_id))::text || ':' ||
                       COALESCE(
                           payload ->> 'occurred_at',
                           payload ->> 'occurredAt',
                           event_id) || ';' ||
                   COALESCE((
                       SELECT string_agg(
                           length(property.key)::text || ':' || property.key || ';' ||
                           length(property.value)::text || ':' || property.value || ';',
                           ''
                           ORDER BY property.key)
                         FROM jsonb_each_text(
                             CASE
                                 WHEN jsonb_typeof(queued.payload -> 'properties') = 'object'
                                     THEN queued.payload -> 'properties'
                                 ELSE '{}'::jsonb
                             END) property
                   ), ''),
                   'sha256'),
               'hex') AS canonical_key
      FROM catalog_event_queue queued
),
ranked AS (
    SELECT calculated.id,
           calculated.event_id,
           calculated.canonical_key,
           row_number() OVER (
               PARTITION BY calculated.canonical_key
               ORDER BY queued.created_at, calculated.id) AS duplicate_number
      FROM calculated
      JOIN catalog_event_queue queued ON queued.id = calculated.id
)
UPDATE catalog_event_queue queued
   SET semantic_key = CASE
       WHEN ranked.duplicate_number = 1 THEN ranked.canonical_key
       ELSE 'sha256:' || encode(
           digest(ranked.canonical_key || chr(31) || ranked.event_id, 'sha256'),
           'hex')
   END
  FROM ranked
 WHERE ranked.id = queued.id;

ALTER TABLE catalog_event_queue
    ALTER COLUMN semantic_key SET NOT NULL,
    ADD CONSTRAINT catalog_event_queue_semantic_key_format_check
        CHECK (semantic_key ~ '^sha256:[0-9a-f]{64}$'),
    ADD CONSTRAINT catalog_event_queue_payload_object_check
        CHECK (jsonb_typeof(payload) = 'object'),
    ADD CONSTRAINT catalog_event_queue_schema_version_check
        CHECK (COALESCE(payload ->> 'schema_version', payload ->> 'schemaVersion') = '1'),
    ADD CONSTRAINT catalog_event_queue_semantic_key_unique UNIQUE (semantic_key);

CREATE OR REPLACE FUNCTION registry_notify_catalog_event_queue()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    PERFORM pg_notify('registry_catalog_work', NEW.id::text);
    RETURN NEW;
END;
$$;

CREATE TRIGGER catalog_event_queue_notify_trigger
AFTER INSERT
ON catalog_event_queue
FOR EACH ROW
EXECUTE FUNCTION registry_notify_catalog_event_queue();

UPDATE package_versions
   SET active = false
 WHERE active AND revoked;

ALTER TABLE package_versions
    ADD CONSTRAINT package_versions_id_package_id_unique UNIQUE (id, package_id),
    ADD CONSTRAINT package_versions_active_revoked_check
        CHECK (NOT (active AND revoked)) NOT VALID;

ALTER TABLE package_versions
    VALIDATE CONSTRAINT package_versions_active_revoked_check;

UPDATE lifecycle_events lifecycle
   SET package_id = version.package_id
  FROM package_versions version
 WHERE lifecycle.package_version_id = version.id
   AND lifecycle.package_id IS DISTINCT FROM version.package_id;

ALTER TABLE lifecycle_events
    ADD CONSTRAINT lifecycle_events_version_package_fk
    FOREIGN KEY (package_version_id, package_id)
    REFERENCES package_versions(id, package_id)
    ON DELETE CASCADE
    NOT VALID;

ALTER TABLE lifecycle_events
    VALIDATE CONSTRAINT lifecycle_events_version_package_fk;

CREATE INDEX lifecycle_events_package_effective_idx
    ON lifecycle_events (package_id, effective_at DESC, id DESC);

CREATE INDEX lifecycle_events_version_effective_idx
    ON lifecycle_events (package_version_id, effective_at DESC, id DESC)
    WHERE package_version_id IS NOT NULL;

WITH ordered AS (
    SELECT package_id,
           team_id,
           row_number() OVER (
               PARTITION BY package_id
               ORDER BY owner_order, team_id) - 1 AS normalized_order
      FROM package_owners
)
UPDATE package_owners owner
   SET owner_order = ordered.normalized_order
  FROM ordered
 WHERE owner.package_id = ordered.package_id
   AND owner.team_id = ordered.team_id
   AND owner.owner_order IS DISTINCT FROM ordered.normalized_order;

ALTER TABLE package_owners
    ADD CONSTRAINT package_owners_order_check
        CHECK (owner_order >= 0) NOT VALID,
    ADD CONSTRAINT package_owners_package_order_unique
        UNIQUE (package_id, owner_order);

ALTER TABLE package_owners
    VALIDATE CONSTRAINT package_owners_order_check;

CREATE INDEX package_owners_team_package_idx
    ON package_owners (team_id, package_id);

UPDATE reconciliation_runs
   SET discrepancies = GREATEST(discrepancies, 0),
       repaired = LEAST(
           GREATEST(repaired, 0),
           GREATEST(discrepancies, 0)),
       completed_at = CASE
           WHEN status = 'running' THEN NULL
           ELSE GREATEST(COALESCE(completed_at, started_at), started_at)
       END
 WHERE discrepancies < 0
    OR repaired < 0
    OR repaired > discrepancies
    OR (status = 'running' AND completed_at IS NOT NULL)
    OR (status IN ('completed', 'failed') AND completed_at IS NULL)
    OR completed_at < started_at;

ALTER TABLE reconciliation_runs
    ADD CONSTRAINT reconciliation_runs_counts_check
        CHECK (
            discrepancies >= 0
            AND repaired >= 0
            AND repaired <= discrepancies
        ) NOT VALID,
    ADD CONSTRAINT reconciliation_runs_completion_state_check
        CHECK (
            (status = 'running' AND completed_at IS NULL)
            OR
            (status IN ('completed', 'failed') AND completed_at IS NOT NULL)
        ) NOT VALID,
    ADD CONSTRAINT reconciliation_runs_time_order_check
        CHECK (completed_at IS NULL OR completed_at >= started_at) NOT VALID;

ALTER TABLE reconciliation_runs
    VALIDATE CONSTRAINT reconciliation_runs_counts_check;
ALTER TABLE reconciliation_runs
    VALIDATE CONSTRAINT reconciliation_runs_completion_state_check;
ALTER TABLE reconciliation_runs
    VALIDATE CONSTRAINT reconciliation_runs_time_order_check;

UPDATE ingestion_events
   SET schema_version = GREATEST(schema_version, 1),
       attempts = GREATEST(
           attempts,
           CASE WHEN status = 'received' THEN 0 ELSE 1 END),
       payload = CASE
           WHEN jsonb_typeof(payload) = 'object' THEN payload
           ELSE jsonb_build_object('legacyPayload', payload)
       END,
       last_attempt_at = CASE
           WHEN status = 'received' AND last_attempt_at IS NULL THEN NULL
           ELSE GREATEST(COALESCE(last_attempt_at, received_at), received_at)
       END,
       completed_at = CASE
           WHEN status IN ('completed', 'quarantined')
               THEN GREATEST(
                   COALESCE(completed_at, last_attempt_at, received_at),
                   received_at)
           ELSE NULL
       END
 WHERE schema_version < 1
    OR attempts < CASE WHEN status = 'received' THEN 0 ELSE 1 END
    OR jsonb_typeof(payload) <> 'object'
    OR (status <> 'received' AND last_attempt_at IS NULL)
    OR last_attempt_at < received_at
    OR (
        status IN ('completed', 'quarantined')
        AND (completed_at IS NULL OR completed_at < received_at)
    )
    OR (
        status NOT IN ('completed', 'quarantined')
        AND completed_at IS NOT NULL
    );

ALTER TABLE ingestion_events
    ADD CONSTRAINT ingestion_events_schema_version_check
        CHECK (schema_version >= 1) NOT VALID,
    ADD CONSTRAINT ingestion_events_attempts_check
        CHECK (attempts >= 0) NOT VALID,
    ADD CONSTRAINT ingestion_events_payload_object_check
        CHECK (jsonb_typeof(payload) = 'object') NOT VALID,
    ADD CONSTRAINT ingestion_events_attempt_state_check
        CHECK (status = 'received' OR attempts >= 1) NOT VALID,
    ADD CONSTRAINT ingestion_events_attempt_time_check
        CHECK (
            (status = 'received' AND (
                last_attempt_at IS NULL
                OR last_attempt_at >= received_at
            ))
            OR
            (status <> 'received' AND last_attempt_at >= received_at)
        ) NOT VALID,
    ADD CONSTRAINT ingestion_events_completion_state_check
        CHECK (
            (status IN ('completed', 'quarantined')) =
            (completed_at IS NOT NULL)
        ) NOT VALID,
    ADD CONSTRAINT ingestion_events_completion_time_check
        CHECK (completed_at IS NULL OR completed_at >= received_at) NOT VALID,
    ADD CONSTRAINT ingestion_events_completed_digest_check
        CHECK (status <> 'completed' OR package_digest IS NOT NULL) NOT VALID;

ALTER TABLE ingestion_events
    VALIDATE CONSTRAINT ingestion_events_schema_version_check;
ALTER TABLE ingestion_events
    VALIDATE CONSTRAINT ingestion_events_attempts_check;
ALTER TABLE ingestion_events
    VALIDATE CONSTRAINT ingestion_events_payload_object_check;
ALTER TABLE ingestion_events
    VALIDATE CONSTRAINT ingestion_events_attempt_state_check;
ALTER TABLE ingestion_events
    VALIDATE CONSTRAINT ingestion_events_attempt_time_check;
ALTER TABLE ingestion_events
    VALIDATE CONSTRAINT ingestion_events_completion_state_check;
ALTER TABLE ingestion_events
    VALIDATE CONSTRAINT ingestion_events_completion_time_check;
ALTER TABLE ingestion_events
    VALIDATE CONSTRAINT ingestion_events_completed_digest_check;

UPDATE catalog_event_queue
   SET attempts = GREATEST(
           attempts,
           CASE WHEN status = 'queued' THEN 0 ELSE 1 END),
       available_at = GREATEST(available_at, created_at),
       claimed_at = CASE
           WHEN status = 'processing'
               THEN GREATEST(COALESCE(claimed_at, updated_at, created_at), created_at)
           ELSE NULL
       END,
       claim_token = CASE
           WHEN status = 'processing'
               THEN COALESCE(claim_token, gen_random_uuid())
           ELSE NULL
       END,
       completed_at = CASE
           WHEN status IN ('completed', 'dead_letter')
               THEN GREATEST(COALESCE(completed_at, updated_at, created_at), created_at)
           ELSE NULL
       END,
       updated_at = GREATEST(updated_at, created_at)
 WHERE attempts < CASE WHEN status = 'queued' THEN 0 ELSE 1 END
    OR available_at < created_at
    OR (status = 'processing' AND claimed_at IS NULL)
    OR (status <> 'processing' AND claimed_at IS NOT NULL)
    OR (status = 'processing' AND claim_token IS NULL)
    OR (status <> 'processing' AND claim_token IS NOT NULL)
    OR (
        status IN ('completed', 'dead_letter')
        AND (completed_at IS NULL OR completed_at < created_at)
    )
    OR (
        status NOT IN ('completed', 'dead_letter')
        AND completed_at IS NOT NULL
    )
    OR updated_at < created_at;

ALTER TABLE catalog_event_queue
    ADD CONSTRAINT catalog_event_queue_attempt_state_check
        CHECK (status = 'queued' OR attempts >= 1) NOT VALID,
    ADD CONSTRAINT catalog_event_queue_claim_state_check
        CHECK (
            (status = 'processing') =
            (claimed_at IS NOT NULL AND claim_token IS NOT NULL)
            AND (claimed_at IS NULL) = (claim_token IS NULL)
        ) NOT VALID,
    ADD CONSTRAINT catalog_event_queue_completion_state_check
        CHECK (
            (status IN ('completed', 'dead_letter')) =
            (completed_at IS NOT NULL)
        ) NOT VALID,
    ADD CONSTRAINT catalog_event_queue_time_order_check
        CHECK (
            available_at >= created_at
            AND updated_at >= created_at
            AND (claimed_at IS NULL OR claimed_at >= created_at)
            AND (completed_at IS NULL OR completed_at >= created_at)
        ) NOT VALID;

ALTER TABLE catalog_event_queue
    VALIDATE CONSTRAINT catalog_event_queue_attempt_state_check;
ALTER TABLE catalog_event_queue
    VALIDATE CONSTRAINT catalog_event_queue_claim_state_check;
ALTER TABLE catalog_event_queue
    VALIDATE CONSTRAINT catalog_event_queue_completion_state_check;
ALTER TABLE catalog_event_queue
    VALIDATE CONSTRAINT catalog_event_queue_time_order_check;

UPDATE symbols
   SET kind = CASE kind
       WHEN 'variable' THEN 'input'
       WHEN 'data_source' THEN 'data-source'
       ELSE kind
   END
 WHERE kind IN ('variable', 'data_source');

UPDATE symbols
   SET document_path = 'README.md'
 WHERE document_path IS NULL
    OR btrim(document_path) = '';

ALTER TABLE symbols
    ALTER COLUMN document_path SET NOT NULL;

ALTER TABLE symbols
    ADD CONSTRAINT symbols_kind_check
        CHECK (
            kind IN (
                'input',
                'output',
                'resource',
                'data-source',
                'function',
                'guide',
                'dependency',
                'example',
                'submodule'
            )
        ) NOT VALID,
    ADD CONSTRAINT symbols_document_path_check
        CHECK (
            btrim(document_path) <> ''
            AND document_path NOT LIKE '/%'
            AND document_path NOT LIKE '%..%'
            AND position(E'\\' IN document_path) = 0
        ) NOT VALID,
    ADD CONSTRAINT symbols_input_metadata_check
        CHECK (
            kind IN ('input', 'dependency')
            OR (default_value IS NULL AND NOT is_required)
        ) NOT VALID;

ALTER TABLE symbols
    VALIDATE CONSTRAINT symbols_kind_check;
ALTER TABLE symbols
    VALIDATE CONSTRAINT symbols_document_path_check;
ALTER TABLE symbols
    VALIDATE CONSTRAINT symbols_input_metadata_check;

DROP INDEX IF EXISTS artifact_download_statistics_latest_idx;

CREATE INDEX catalog_event_queue_completed_retention_idx
    ON catalog_event_queue (completed_at, id)
    WHERE status = 'completed';

CREATE INDEX ingestion_events_terminal_retention_idx
    ON ingestion_events (completed_at, id)
    WHERE status IN ('completed', 'quarantined');

CREATE INDEX ingestion_events_stale_processing_idx
    ON ingestion_events (last_attempt_at, id)
    WHERE status = 'processing';

COMMENT ON TABLE apm_contexts IS
    'Normalized APM identities referenced by entitlement and package visibility records.';
COMMENT ON TABLE registry_homepage_features IS
    'Ordered package selections for provider and module homepage feature areas.';
COMMENT ON COLUMN registry_homepage_settings.featured_provider_ids IS
    'Compatibility projection of ordered provider IDs; registry_homepage_features is authoritative.';
COMMENT ON COLUMN registry_homepage_settings.featured_module_ids IS
    'Compatibility projection of ordered module IDs; registry_homepage_features is authoritative.';
COMMENT ON TABLE registry_categories IS
    'Governed Registry browse-category vocabulary.';
COMMENT ON TABLE package_categories IS
    'Normalized many-to-many package category assignments; packages.categories remains a compatibility projection.';
COMMENT ON TABLE registry_traffic_identities IS
    'Normalized authenticated visitor identity metadata referenced by immutable page-view events.';
COMMENT ON COLUMN registry_page_views.identity_id IS
    'Normalized visitor identity for this page-view event.';
COMMENT ON COLUMN registry_page_views.subject IS
    'Compatibility projection of registry_traffic_identities.subject.';
COMMENT ON COLUMN registry_page_views.display_name IS
    'Compatibility projection of registry_traffic_identities.display_name.';
COMMENT ON COLUMN registry_page_views.email IS
    'Compatibility projection of registry_traffic_identities.email.';
COMMENT ON COLUMN package_versions.active IS
    'True only after the version catalog data and documentation are transactionally staged in PostgreSQL; revoked versions are never active.';
COMMENT ON COLUMN catalog_event_queue.semantic_key IS
    'Stable SHA-256 key over the event action, governed artifact identity, occurrence timestamp, and properties; transport IDs are excluded.';

GRANT SELECT ON
    apm_contexts,
    registry_homepage_features,
    registry_categories,
    package_categories,
    registry_traffic_identities
TO registry_app;

GRANT SELECT, INSERT, UPDATE, DELETE ON
    apm_contexts,
    registry_homepage_features,
    registry_categories,
    package_categories,
    registry_traffic_identities
TO registry_indexer;
