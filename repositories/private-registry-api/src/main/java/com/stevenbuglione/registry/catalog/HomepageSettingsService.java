package com.stevenbuglione.registry.catalog;

import com.stevenbuglione.registry.audit.AuditLogService;
import com.stevenbuglione.registry.security.identity.AccessContext;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class HomepageSettingsService {

  private static final int MAX_FEATURED_PROVIDERS = 6;
  private static final int MAX_FEATURED_MODULES = 6;
  private static final Pattern PROVIDER_ID =
      Pattern.compile("provider/[A-Za-z0-9._-]+/[A-Za-z0-9._-]+");
  private static final Pattern MODULE_ID =
      Pattern.compile("module/[A-Za-z0-9._-]+/[A-Za-z0-9._-]+/[A-Za-z0-9._-]+");

  private final JdbcClient jdbc;
  private final AuditLogService audit;

  public HomepageSettingsService(JdbcClient jdbc, AuditLogService audit) {
    this.jdbc = jdbc;
    this.audit = audit;
  }

  public HomepageSettings get() {
    return get(AccessContext.localAdministrator());
  }

  public HomepageSettings get(AccessContext accessContext) {
    var presentation =
        jdbc.sql(
                """
            SELECT notification_enabled,
                   notification_title,
                   notification_message,
                   notification_link_label,
                   notification_link_url,
                   featured_providers_enabled,
                   featured_modules_enabled,
                   updated_at
              FROM registry_homepage_settings
             WHERE id = 1
            """)
            .query(
                (resultSet, rowNumber) ->
                    new Presentation(
                        resultSet.getBoolean("notification_enabled"),
                        resultSet.getString("notification_title"),
                        resultSet.getString("notification_message"),
                        resultSet.getString("notification_link_label"),
                        resultSet.getString("notification_link_url"),
                        resultSet.getBoolean("featured_providers_enabled"),
                        resultSet.getBoolean("featured_modules_enabled"),
                        resultSet.getTimestamp("updated_at").toInstant()))
            .single();
    return new HomepageSettings(
        presentation.notificationEnabled(),
        presentation.notificationTitle(),
        presentation.notificationMessage(),
        presentation.notificationLinkLabel(),
        presentation.notificationLinkUrl(),
        presentation.featuredProvidersEnabled(),
        presentation.featuredModulesEnabled(),
        featuredPackageIds("provider", accessContext),
        featuredPackageIds("module", accessContext),
        presentation.updatedAt());
  }

  @Transactional
  public HomepageSettings update(Update update, String actorSubject) {
    var normalized = validate(update);
    var providerFeatures = resolveFeatures("provider", normalized.featuredProviderIds());
    var moduleFeatures = resolveFeatures("module", normalized.featuredModuleIds());
    var before = get();
    replaceFeatures(providerFeatures, moduleFeatures);
    jdbc.sql(
            """
            UPDATE registry_homepage_settings
               SET notification_enabled = :notificationEnabled,
                   notification_title = :notificationTitle,
                   notification_message = :notificationMessage,
                   notification_link_label = :notificationLinkLabel,
                   notification_link_url = :notificationLinkUrl,
                   featured_providers_enabled = :featuredProvidersEnabled,
                   featured_modules_enabled = :featuredModulesEnabled,
                   updated_by = :updatedBy,
                   updated_at = now()
             WHERE id = 1
            """)
        .param("notificationEnabled", normalized.notificationEnabled())
        .param("notificationTitle", normalized.notificationTitle())
        .param("notificationMessage", normalized.notificationMessage())
        .param("notificationLinkLabel", normalized.notificationLinkLabel())
        .param("notificationLinkUrl", normalized.notificationLinkUrl())
        .param("featuredProvidersEnabled", normalized.featuredProvidersEnabled())
        .param("featuredModulesEnabled", normalized.featuredModulesEnabled())
        .param("updatedBy", actorSubject)
        .update();
    var after = get();
    var detail = new LinkedHashMap<String, Object>();
    detail.put("before", before);
    detail.put("after", after);
    audit.record(
        new AuditLogService.AuditEntry(
            "user",
            actorSubject,
            "registry.homepage.updated",
            "registry_homepage",
            "home",
            detail));
    return after;
  }

  private List<String> featuredPackageIds(String featureKind, AccessContext accessContext) {
    return jdbc.sql(
            """
            SELECT CASE
                       WHEN package_record.kind = 'module'
                           THEN 'module/' || package_record.namespace || '/' ||
                                package_record.name || '/' || package_record.target
                       ELSE 'provider/' || package_record.namespace || '/' || package_record.name
                   END AS public_id
              FROM registry_homepage_features feature
              JOIN packages package_record ON package_record.id = feature.package_id
             WHERE feature.feature_kind = CAST(:featureKind AS package_kind)
               AND (
                   :registryAdmin
                   OR EXISTS (
                       SELECT 1
                         FROM package_apm_access access
                        WHERE access.package_id = package_record.id
                          AND access.apm_id = ANY(CAST(:apmIds AS text[]))
                   )
               )
             ORDER BY feature.display_order, feature.package_id
            """)
        .param("featureKind", featureKind)
        .param("registryAdmin", accessContext.registryAdmin())
        .param("apmIds", accessContext.apmIds().toArray(String[]::new))
        .query(
            (resultSet, rowNumber) ->
                Objects.requireNonNull(resultSet.getString("public_id"), "public_id"))
        .list();
  }

  private List<Feature> resolveFeatures(String featureKind, List<String> publicIds) {
    if (publicIds.isEmpty()) {
      return List.of();
    }
    var features =
        jdbc.sql(
                """
                SELECT package_record.id AS package_id,
                       selected.ordinality - 1 AS display_order
                  FROM unnest(CAST(:publicIds AS text[]))
                       WITH ORDINALITY selected(public_id, ordinality)
                  JOIN packages package_record
                    ON package_record.kind = CAST(:featureKind AS package_kind)
                   AND CASE
                           WHEN package_record.kind = 'module'
                               THEN 'module/' || package_record.namespace || '/' ||
                                    package_record.name || '/' || package_record.target
                           ELSE 'provider/' || package_record.namespace || '/' ||
                                package_record.name
                       END = selected.public_id
                 ORDER BY selected.ordinality
                """)
            .param("publicIds", publicIds.toArray(String[]::new))
            .param("featureKind", featureKind)
            .query(
                (resultSet, rowNumber) ->
                    new Feature(
                        featureKind,
                        resultSet.getObject("package_id", UUID.class),
                        resultSet.getInt("display_order")))
            .list();
    if (features.size() != publicIds.size()) {
      throw new IllegalArgumentException(
          "Every featured " + featureKind + " must reference an existing Registry package");
    }
    return features;
  }

  private void replaceFeatures(List<Feature> providers, List<Feature> modules) {
    jdbc.sql("DELETE FROM registry_homepage_features").update();
    java.util.stream.Stream.concat(providers.stream(), modules.stream())
        .forEach(
            feature ->
                jdbc.sql(
                        """
                        INSERT INTO registry_homepage_features (
                            feature_kind, package_id, display_order)
                        VALUES (
                            CAST(:featureKind AS package_kind), :packageId, :displayOrder)
                        """)
                    .param("featureKind", feature.kind())
                    .param("packageId", feature.packageId())
                    .param("displayOrder", feature.displayOrder())
                    .update());
  }

  private static Update validate(Update update) {
    var title = requiredText(update.notificationTitle(), "Notification title", 120);
    var message = requiredText(update.notificationMessage(), "Notification message", 600);
    var linkLabel = optionalText(update.notificationLinkLabel(), "Notification link label", 80);
    var linkUrl = optionalText(update.notificationLinkUrl(), "Notification link URL", 500);
    validateLink(linkLabel, linkUrl);
    return new Update(
        update.notificationEnabled(),
        title,
        message,
        linkLabel,
        linkUrl,
        update.featuredProvidersEnabled(),
        update.featuredModulesEnabled(),
        validatePackageIds(
            update.featuredProviderIds(),
            PROVIDER_ID,
            MAX_FEATURED_PROVIDERS,
            "providers",
            "provider/namespace/name"),
        validatePackageIds(
            update.featuredModuleIds(),
            MODULE_ID,
            MAX_FEATURED_MODULES,
            "modules",
            "module/namespace/name/provider"));
  }

  private static void validateLink(@Nullable String linkLabel, @Nullable String linkUrl) {
    if ((linkLabel == null) != (linkUrl == null)) {
      throw new IllegalArgumentException(
          "Notification link label and URL must be provided together");
    }
    if (linkUrl != null
        && !(linkUrl.startsWith("https://")
            || (linkUrl.startsWith("/") && !linkUrl.startsWith("//")))) {
      throw new IllegalArgumentException(
          "Notification link URL must be an HTTPS or Registry-relative URL");
    }
  }

  private static List<String> validatePackageIds(
      List<String> requestedIds,
      Pattern pattern,
      int maximum,
      String packageLabel,
      String expectedFormat) {
    var packageIds = new LinkedHashSet<String>();
    for (var packageId : requestedIds) {
      var normalized = packageId.trim();
      if (!pattern.matcher(normalized).matches()) {
        throw new IllegalArgumentException(
            "Featured " + packageLabel + " must use " + expectedFormat + " IDs");
      }
      if (!packageIds.add(normalized)) {
        throw new IllegalArgumentException(
            "Featured " + packageLabel + " must not contain duplicates");
      }
    }
    if (packageIds.size() > maximum) {
      throw new IllegalArgumentException(
          "No more than six featured " + packageLabel + " may be selected");
    }
    return List.copyOf(packageIds);
  }

  private static String requiredText(String value, String label, int maxLength) {
    var normalized = value.trim();
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException(label + " is required");
    }
    if (normalized.length() > maxLength) {
      throw new IllegalArgumentException(label + " is too long");
    }
    return normalized;
  }

  private static @Nullable String optionalText(
      @Nullable String value, String label, int maxLength) {
    if (value == null || value.isBlank()) {
      return null;
    }
    var normalized = value.trim();
    if (normalized.length() > maxLength) {
      throw new IllegalArgumentException(label + " is too long");
    }
    return normalized;
  }

  private record Presentation(
      boolean notificationEnabled,
      String notificationTitle,
      String notificationMessage,
      @Nullable String notificationLinkLabel,
      @Nullable String notificationLinkUrl,
      boolean featuredProvidersEnabled,
      boolean featuredModulesEnabled,
      Instant updatedAt) {}

  private record Feature(String kind, UUID packageId, int displayOrder) {}

  public record Update(
      boolean notificationEnabled,
      String notificationTitle,
      String notificationMessage,
      @Nullable String notificationLinkLabel,
      @Nullable String notificationLinkUrl,
      boolean featuredProvidersEnabled,
      boolean featuredModulesEnabled,
      List<String> featuredProviderIds,
      List<String> featuredModuleIds) {

    public Update {
      featuredProviderIds = List.copyOf(featuredProviderIds);
      featuredModuleIds = List.copyOf(featuredModuleIds);
    }
  }
}
