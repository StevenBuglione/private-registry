package com.stevenbuglione.registry.catalog;

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
  private static final Pattern PROVIDER_ID =
      Pattern.compile("provider/[A-Za-z0-9._-]+/[A-Za-z0-9._-]+");

  private final JdbcClient jdbc;

  public HomepageSettingsService(JdbcClient jdbc) {
    this.jdbc = jdbc;
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
                    splitProviderIds(resultSet.getString("featured_provider_ids")),
                    resultSet.getTimestamp("updated_at").toInstant()))
        .single();
  }

  @Transactional
  public HomepageSettings update(Update update, String actorSubject) {
    var normalized = validate(update);
    jdbc.sql(
            """
            UPDATE registry_homepage_settings
               SET notification_enabled = :notificationEnabled,
                   notification_title = :notificationTitle,
                   notification_message = :notificationMessage,
                   notification_link_label = :notificationLinkLabel,
                   notification_link_url = :notificationLinkUrl,
                   featured_provider_ids = :featuredProviderIds,
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
        .param("updatedBy", actorSubject)
        .update();
    jdbc.sql(
            """
            INSERT INTO audit_events (
                occurred_at,
                actor_type,
                actor_id,
                action,
                resource_type,
                resource_id,
                correlation_id,
                detail)
            VALUES (
                now(),
                'user',
                :actorId,
                'registry.homepage.updated',
                'registry_homepage',
                'home',
                gen_random_uuid()::text,
                jsonb_build_object(
                    'notification_enabled', :notificationEnabled,
                    'featured_provider_ids',
                    to_jsonb(string_to_array(:featuredProviderIds, ','))))
            """)
        .param("actorId", actorSubject)
        .param("notificationEnabled", normalized.notificationEnabled())
        .param("featuredProviderIds", String.join(",", normalized.featuredProviderIds()))
        .update();
    return get();
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
        validateProviderIds(update.featuredProviderIds()));
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

  private static List<String> validateProviderIds(List<String> requestedProviderIds) {
    var providerIds = new LinkedHashSet<String>();
    for (var providerId : requestedProviderIds) {
      var normalized = providerId.trim();
      if (!PROVIDER_ID.matcher(normalized).matches()) {
        throw new IllegalArgumentException(
            "Featured providers must use provider/namespace/name IDs");
      }
      providerIds.add(normalized);
    }
    if (providerIds.size() > MAX_FEATURED_PROVIDERS) {
      throw new IllegalArgumentException("No more than six featured providers may be selected");
    }
    return List.copyOf(providerIds);
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

  private static List<String> splitProviderIds(String value) {
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
      List<String> featuredProviderIds) {

    public Update {
      featuredProviderIds = List.copyOf(featuredProviderIds);
    }
  }
}
