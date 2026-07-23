package com.stevenbuglione.registry.catalog;

import com.stevenbuglione.registry.audit.AuditLogService;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
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
    return jdbc.sql(
            """
            SELECT notification_enabled,
                   notification_title,
                   notification_message,
                   notification_link_label,
                   notification_link_url,
                   featured_provider_ids,
                   featured_module_ids,
                   updated_at
              FROM registry_homepage_settings
             WHERE id = 1
            """)
        .query(
            (resultSet, rowNumber) ->
                new HomepageSettings(
                    resultSet.getBoolean("notification_enabled"),
                    resultSet.getString("notification_title"),
                    resultSet.getString("notification_message"),
                    resultSet.getString("notification_link_label"),
                    resultSet.getString("notification_link_url"),
                    splitIds(resultSet.getString("featured_provider_ids")),
                    splitIds(resultSet.getString("featured_module_ids")),
                    resultSet.getTimestamp("updated_at").toInstant()))
        .single();
  }

  @Transactional
  public HomepageSettings update(Update update, String actorSubject) {
    var normalized = validate(update);
    var before = get();
    jdbc.sql(
            """
            UPDATE registry_homepage_settings
               SET notification_enabled = :notificationEnabled,
                   notification_title = :notificationTitle,
                   notification_message = :notificationMessage,
                   notification_link_label = :notificationLinkLabel,
                   notification_link_url = :notificationLinkUrl,
                   featured_provider_ids = :featuredProviderIds,
                   featured_module_ids = :featuredModuleIds,
                   updated_by = :updatedBy,
                   updated_at = now()
             WHERE id = 1
            """)
        .param("notificationEnabled", normalized.notificationEnabled())
        .param("notificationTitle", normalized.notificationTitle())
        .param("notificationMessage", normalized.notificationMessage())
        .param("notificationLinkLabel", normalized.notificationLinkLabel())
        .param("notificationLinkUrl", normalized.notificationLinkUrl())
        .param("featuredProviderIds", String.join(",", normalized.featuredProviderIds()))
        .param("featuredModuleIds", String.join(",", normalized.featuredModuleIds()))
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
      packageIds.add(normalized);
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

  private static List<String> splitIds(String value) {
    if (value.isBlank()) {
      return List.of();
    }
    return Pattern.compile(",")
        .splitAsStream(value)
        .map(String::trim)
        .filter(providerId -> !providerId.isEmpty())
        .toList();
  }

  public record Update(
      boolean notificationEnabled,
      String notificationTitle,
      String notificationMessage,
      @Nullable String notificationLinkLabel,
      @Nullable String notificationLinkUrl,
      List<String> featuredProviderIds,
      List<String> featuredModuleIds) {

    public Update {
      featuredProviderIds = List.copyOf(featuredProviderIds);
      featuredModuleIds = List.copyOf(featuredModuleIds);
    }
  }
}
