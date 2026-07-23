package com.stevenbuglione.registry.ingestion;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class CatalogWriteRepository {

  private final JdbcClient jdbc;
  private final CatalogActivationNotifier activationNotifier;

  public CatalogWriteRepository(JdbcClient jdbc, CatalogActivationNotifier activationNotifier) {
    this.jdbc = jdbc;
    this.activationNotifier = activationNotifier;
  }

  @Transactional
  public StagedVersion stage(CatalogManifestV1 manifest, List<StoredManifestDocument> documents) {
    var packageId = upsertPackage(manifest);
    if (isAuthoritativePackageMetadata(packageId, manifest)) {
      upsertOwners(packageId, manifest);
      replaceApmAccess(packageId, manifest.access().apmIds());
    }
    var versionId = upsertVersion(packageId, manifest);
    replaceDocuments(versionId, documents);
    replaceSymbols(versionId, manifest.symbols());
    replaceApproval(versionId, manifest);
    activateVersion(packageId, manifest);
    return new StagedVersion(
        packageId, versionId, manifest.publicId(), manifest.identity().version());
  }

  private UUID upsertPackage(CatalogManifestV1 manifest) {
    var display = manifest.display();
    return jdbc.sql(
            """
                        INSERT INTO packages (
                            kind, namespace, name, target, title, description, source_address,
                            visibility, risk_tier, verification, support_level, lifecycle,
                            search_keywords, registry_tier, categories
                        ) VALUES (
                            CAST(:kind AS package_kind), :namespace, :name, :target, :title, :description,
                            :sourceAddress, :visibility, :riskTier, :verification,
                            CAST(:supportLevel AS support_level), CAST(:lifecycle AS lifecycle_state),
                            :searchKeywords, :registryTier, :categories
                        )
                        ON CONFLICT (kind, namespace, name, target) DO UPDATE SET
                            title = CASE WHEN NOT EXISTS (
                                SELECT 1 FROM package_versions newer
                                 WHERE newer.package_id = packages.id AND newer.published_at > :publishedAt
                            ) THEN EXCLUDED.title ELSE packages.title END,
                            description = CASE WHEN NOT EXISTS (
                                SELECT 1 FROM package_versions newer
                                 WHERE newer.package_id = packages.id AND newer.published_at > :publishedAt
                            ) THEN EXCLUDED.description ELSE packages.description END,
                            source_address = CASE WHEN NOT EXISTS (
                                SELECT 1 FROM package_versions newer
                                 WHERE newer.package_id = packages.id AND newer.published_at > :publishedAt
                            ) THEN EXCLUDED.source_address ELSE packages.source_address END,
                            visibility = CASE WHEN NOT EXISTS (
                                SELECT 1 FROM package_versions newer
                                 WHERE newer.package_id = packages.id AND newer.published_at > :publishedAt
                            ) THEN EXCLUDED.visibility ELSE packages.visibility END,
                            risk_tier = CASE WHEN NOT EXISTS (
                                SELECT 1 FROM package_versions newer
                                 WHERE newer.package_id = packages.id AND newer.published_at > :publishedAt
                            ) THEN EXCLUDED.risk_tier ELSE packages.risk_tier END,
                            verification = CASE WHEN NOT EXISTS (
                                SELECT 1 FROM package_versions newer
                                 WHERE newer.package_id = packages.id AND newer.published_at > :publishedAt
                            ) THEN EXCLUDED.verification ELSE packages.verification END,
                            support_level = CASE WHEN NOT EXISTS (
                                SELECT 1 FROM package_versions newer
                                 WHERE newer.package_id = packages.id AND newer.published_at > :publishedAt
                            ) THEN EXCLUDED.support_level ELSE packages.support_level END,
                            lifecycle = CASE WHEN NOT EXISTS (
                                SELECT 1 FROM package_versions newer
                                 WHERE newer.package_id = packages.id AND newer.published_at > :publishedAt
                            ) THEN EXCLUDED.lifecycle ELSE packages.lifecycle END,
                            search_keywords = CASE WHEN NOT EXISTS (
                                SELECT 1 FROM package_versions newer
                                 WHERE newer.package_id = packages.id AND newer.published_at > :publishedAt
                            ) THEN EXCLUDED.search_keywords ELSE packages.search_keywords END,
                            registry_tier = CASE WHEN NOT EXISTS (
                                SELECT 1 FROM package_versions newer
                                 WHERE newer.package_id = packages.id AND newer.published_at > :publishedAt
                            ) THEN EXCLUDED.registry_tier ELSE packages.registry_tier END,
                            categories = CASE WHEN NOT EXISTS (
                                SELECT 1 FROM package_versions newer
                                 WHERE newer.package_id = packages.id AND newer.published_at > :publishedAt
                            ) THEN EXCLUDED.categories ELSE packages.categories END,
                            updated_at = CASE WHEN NOT EXISTS (
                                SELECT 1 FROM package_versions newer
                                 WHERE newer.package_id = packages.id AND newer.published_at > :publishedAt
                            ) THEN now() ELSE packages.updated_at END
                        RETURNING id
                        """)
        .param("kind", manifest.packageKind().jsonValue())
        .param("namespace", manifest.identity().namespace())
        .param("name", manifest.identity().name())
        .param("target", manifest.targetOrEmpty())
        .param("title", display.title())
        .param("description", display.description())
        .param("sourceAddress", manifest.source().repository())
        .param("visibility", display.visibility())
        .param("riskTier", display.riskTier())
        .param("verification", display.verification())
        .param("supportLevel", display.supportLevel())
        .param("lifecycle", display.lifecycle())
        .param(
            "searchKeywords",
            display.keywords() == null ? new String[0] : display.keywords().toArray(String[]::new))
        .param("registryTier", RegistryTaxonomy.tier(manifest))
        .param("categories", RegistryTaxonomy.categories(manifest))
        .param("publishedAt", java.sql.Timestamp.from(manifest.release().publishedAt()))
        .query(UUID.class)
        .single();
  }

  private boolean isAuthoritativePackageMetadata(UUID packageId, CatalogManifestV1 manifest) {
    return !jdbc.sql(
            """
                        SELECT EXISTS (
                            SELECT 1 FROM package_versions
                             WHERE package_id = :packageId AND published_at > :publishedAt
                        )
                        """)
        .param("packageId", packageId)
        .param("publishedAt", java.sql.Timestamp.from(manifest.release().publishedAt()))
        .query(Boolean.class)
        .single();
  }

  private void upsertOwners(UUID packageId, CatalogManifestV1 manifest) {
    var owners =
        manifest.display().owners() == null ? List.<String>of() : manifest.display().owners();
    jdbc.sql("DELETE FROM package_owners WHERE package_id = :packageId")
        .param("packageId", packageId)
        .update();
    for (var index = 0; index < owners.size(); index++) {
      var owner = owners.get(index);
      if (owner == null || !owner.matches("[A-Za-z0-9._-]{1,128}")) {
        throw new QuarantineException(
            "invalid_owner", "Owner identifiers must be stable, safe IDs");
      }
      jdbc.sql(
              """
                            INSERT INTO teams (id, display_name, support_url)
                            VALUES (:id, :displayName, :supportUrl)
                            ON CONFLICT (id) DO UPDATE SET
                                support_url = EXCLUDED.support_url,
                                updated_at = now()
                            """)
          .param("id", owner)
          .param("displayName", owner)
          .param("supportUrl", manifest.display().supportChannel())
          .update();
      jdbc.sql(
              """
                            INSERT INTO package_owners (package_id, team_id, owner_order)
                            VALUES (:packageId, :teamId, :ownerOrder)
                            """)
          .param("packageId", packageId)
          .param("teamId", owner)
          .param("ownerOrder", index)
          .update();
    }
  }

  private void replaceApmAccess(UUID packageId, List<String> apmIds) {
    jdbc.sql("DELETE FROM package_apm_access WHERE package_id = :packageId")
        .param("packageId", packageId)
        .update();
    apmIds.stream()
        .distinct()
        .forEach(
            apmId ->
                jdbc.sql(
                        """
                        INSERT INTO package_apm_access (package_id, apm_id)
                        VALUES (:packageId, :apmId)
                        """)
                    .param("packageId", packageId)
                    .param("apmId", apmId)
                    .update());
  }

  private UUID upsertVersion(UUID packageId, CatalogManifestV1 manifest) {
    var existingDigest =
        jdbc.sql(
                """
                        SELECT package_digest
                          FROM package_versions
                         WHERE package_id = :packageId AND version = :version
                        """)
            .param("packageId", packageId)
            .param("version", manifest.identity().version())
            .query(String.class)
            .optional();
    if (existingDigest.isPresent()
        && !existingDigest.orElseThrow().equals(manifest.release().packageDigest())) {
      throw new QuarantineException(
          "immutable_version_conflict",
          "A released version cannot be replaced with a different digest");
    }
    return jdbc.sql(
            """
                        INSERT INTO package_versions (
                            package_id, version, package_digest, documentation_digest,
                            documentation_root, artifact_repository, artifact_path,
                            source_repository, source_commit, source_tag,
                            terraform_constraint, published_at,
                            prerelease, deprecated, revoked, active
                        ) VALUES (
                            :packageId, :version, :packageDigest, :documentationDigest,
                            :documentationRoot, :artifactRepository, :artifactPath,
                            :sourceRepository, :sourceCommit, :sourceTag,
                            :terraformConstraint, :publishedAt,
                            :prerelease, :deprecated, :revoked, NOT :revoked
                        )
                        ON CONFLICT (package_id, version) DO UPDATE SET
                            documentation_digest = EXCLUDED.documentation_digest,
                            documentation_root = EXCLUDED.documentation_root,
                            deprecated = EXCLUDED.deprecated,
                            revoked = EXCLUDED.revoked,
                            active = NOT EXCLUDED.revoked
                        RETURNING id
                        """)
        .param("packageId", packageId)
        .param("version", manifest.identity().version())
        .param("packageDigest", manifest.release().packageDigest())
        .param("documentationDigest", manifest.release().documentationDigest())
        .param("documentationRoot", manifest.release().documentationPath())
        .param("artifactRepository", manifest.registry().repository())
        .param("artifactPath", manifest.registry().artifactPath())
        .param("sourceRepository", manifest.source().repository())
        .param("sourceCommit", manifest.source().commit())
        .param("sourceTag", manifest.source().tag())
        .param("terraformConstraint", manifest.compatibility().terraform())
        .param("publishedAt", java.sql.Timestamp.from(manifest.release().publishedAt()))
        .param("prerelease", manifest.release().prerelease())
        .param("deprecated", manifest.release().deprecated())
        .param("revoked", manifest.release().revoked())
        .query(UUID.class)
        .single();
  }

  private void replaceDocuments(UUID versionId, List<StoredManifestDocument> documents) {
    jdbc.sql("DELETE FROM documentation_pages WHERE package_version_id = :versionId")
        .param("versionId", versionId)
        .update();
    documents.forEach(
        document ->
            jdbc.sql(
                    """
                        INSERT INTO documentation_pages (
                            package_version_id, path, title, content_type, storage_key,
                            digest, size_bytes, content
                        ) VALUES (
                            :versionId, :path, :title, :contentType, :storageKey,
                            :digest, :sizeBytes, :content
                        )
                        """)
                .param("versionId", versionId)
                .param("path", document.path())
                .param("title", document.title())
                .param("contentType", document.stored().contentType())
                .param("storageKey", document.stored().key())
                .param("digest", document.stored().digest())
                .param("sizeBytes", document.stored().sizeBytes())
                .param("content", document.stored().content())
                .update());
  }

  private void replaceSymbols(UUID versionId, @Nullable List<CatalogManifestV1.Symbol> symbols) {
    jdbc.sql("DELETE FROM symbols WHERE package_version_id = :versionId")
        .param("versionId", versionId)
        .update();
    if (symbols == null) {
      return;
    }
    symbols.forEach(
        symbol ->
            jdbc.sql(
                    """
                        INSERT INTO symbols (
                            package_version_id, kind, name, description, document_path,
                            symbol_type, default_value, is_required, sensitive
                        ) VALUES (
                            :versionId, :kind, :name, :description, :path,
                            :type, :defaultValue, :required, :sensitive
                        )
                        """)
                .param("versionId", versionId)
                .param("kind", symbol.kind())
                .param("name", symbol.name())
                .param("description", symbol.description())
                .param("path", symbol.path())
                .param("type", symbol.type())
                .param("defaultValue", symbol.defaultValue())
                .param("required", symbol.required())
                .param("sensitive", symbol.sensitive())
                .update());
  }

  private void replaceApproval(UUID versionId, CatalogManifestV1 manifest) {
    jdbc.sql(
            "DELETE FROM approvals WHERE package_version_id = :versionId AND approval_type = 'registry'")
        .param("versionId", versionId)
        .update();
    if (!"approved".equals(manifest.display().lifecycle())) {
      return;
    }
    jdbc.sql(
            """
                        INSERT INTO approvals (
                            package_version_id, approval_type, decision, decided_by, decided_at,
                            policy_version, justification, evidence_uri
                        ) VALUES (
                            :versionId, 'registry', 'approved', 'registry-ingestion', :decidedAt,
                            :policyVersion, 'Governed catalog manifest accepted', :evidence
                        )
                        """)
        .param("versionId", versionId)
        .param("decidedAt", java.sql.Timestamp.from(manifest.release().publishedAt()))
        .param("policyVersion", Integer.toString(manifest.schemaVersion()))
        .param(
            "evidence",
            "registry-manifest://" + manifest.publicId() + "/" + manifest.identity().version())
        .update();
  }

  private void activateVersion(UUID packageId, CatalogManifestV1 manifest) {
    jdbc.sql(
            """
            UPDATE package_versions
               SET active = NOT revoked
             WHERE package_id = :packageId
            """)
        .param("packageId", packageId)
        .update();
    if (!manifest.release().revoked()) {
      activationNotifier.notifyChanged(manifest.publicId(), manifest.identity().version());
    }
  }

  public record StoredManifestDocument(
      String path, String title, DocumentStore.StoredDocument stored) {}

  public record StagedVersion(UUID packageId, UUID versionId, String publicId, String version) {}
}
