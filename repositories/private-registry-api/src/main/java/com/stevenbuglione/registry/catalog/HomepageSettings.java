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
    boolean featuredProvidersEnabled,
    boolean featuredModulesEnabled,
    List<String> featuredProviderIds,
    List<String> featuredModuleIds,
    Instant updatedAt) {

  public HomepageSettings {
    featuredProviderIds = List.copyOf(featuredProviderIds);
    featuredModuleIds = List.copyOf(featuredModuleIds);
  }
}
