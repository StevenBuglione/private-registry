package com.stevenbuglione.registry.web;

import com.stevenbuglione.registry.catalog.HomepageSettings;
import com.stevenbuglione.registry.catalog.HomepageSettingsService;
import com.stevenbuglione.registry.security.identity.RegistryIdentityService;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/registry/homepage")
public class HomepageSettingsController {

  private final HomepageSettingsService settings;
  private final RegistryIdentityService identities;

  public HomepageSettingsController(
      HomepageSettingsService settings, RegistryIdentityService identities) {
    this.settings = settings;
    this.identities = identities;
  }

  @GetMapping
  public HomepageSettings get(Authentication authentication) {
    var context = identities.accessContext(authentication);
    return settings.get(context);
  }

  @PutMapping
  public HomepageSettings update(
      Authentication authentication, @RequestBody UpdateHomepageRequest request) {
    var context = identities.accessContext(authentication);
    return settings.update(request.toUpdate(), context.subject());
  }

  record UpdateHomepageRequest(
      boolean notificationEnabled,
      String notificationTitle,
      String notificationMessage,
      @Nullable String notificationLinkLabel,
      @Nullable String notificationLinkUrl,
      @Nullable List<String> featuredProviderIds,
      @Nullable List<String> featuredModuleIds) {

    HomepageSettingsService.Update toUpdate() {
      return new HomepageSettingsService.Update(
          notificationEnabled,
          notificationTitle,
          notificationMessage,
          notificationLinkLabel,
          notificationLinkUrl,
          featuredProviderIds == null ? List.of() : featuredProviderIds,
          featuredModuleIds == null ? List.of() : featuredModuleIds);
    }
  }
}
