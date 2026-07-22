package com.stevenbuglione.registry.catalog;

import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record HomepageSettings(
    boolean notificationEnabled,
    String notificationTitle,
    String notificationMessage,
    @Nullable String notificationLinkLabel,
    @Nullable String notificationLinkUrl,
    List<String> featuredProviderIds,
    Instant updatedAt) {

  public HomepageSettings {
    featuredProviderIds = List.copyOf(featuredProviderIds);
  }
}
