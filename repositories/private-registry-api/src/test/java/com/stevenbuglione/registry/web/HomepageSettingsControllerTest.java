package com.stevenbuglione.registry.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stevenbuglione.registry.catalog.CatalogService;
import com.stevenbuglione.registry.catalog.HomepageSettings;
import com.stevenbuglione.registry.catalog.HomepageSettingsService;
import com.stevenbuglione.registry.security.identity.AccessContext;
import com.stevenbuglione.registry.security.identity.RegistryIdentityService;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

class HomepageSettingsControllerTest {

  private final HomepageSettingsService settings = mock(HomepageSettingsService.class);
  private final CatalogService catalog = mock(CatalogService.class);
  private final RegistryIdentityService identities = mock(RegistryIdentityService.class);
  private final Authentication authentication = mock(Authentication.class);
  private final HomepageSettingsController controller =
      new HomepageSettingsController(settings, catalog, identities);

  @Test
  void returnsHomepageSettingsToAuthenticatedUsers() {
    var expected = settings();
    when(settings.get()).thenReturn(expected);
    when(identities.accessContext(authentication))
        .thenReturn(new AccessContext("member-subject", Set.of("APM0000001"), false));
    when(catalog.filterAccessiblePackageIds(any(), eq(List.of("provider/hashicorp/aws"))))
        .thenReturn(List.of("provider/hashicorp/aws"));
    when(catalog.filterAccessiblePackageIds(
            any(), eq(List.of("module/terraform-aws-modules/iam/aws"))))
        .thenReturn(List.of());

    var filtered = controller.get(authentication);

    assertThat(filtered.featuredProviderIds()).containsExactly("provider/hashicorp/aws");
    assertThat(filtered.featuredModuleIds()).isEmpty();
  }

  @Test
  void permitsAdministratorsToUpdateSettings() {
    var expected = settings();
    when(identities.accessContext(authentication))
        .thenReturn(new AccessContext("admin-subject", Set.of(), true));
    when(settings.update(any(), eq("admin-subject"))).thenReturn(expected);
    var request =
        new HomepageSettingsController.UpdateHomepageRequest(
            true,
            "Registry notice",
            "Approved content is available.",
            null,
            null,
            List.of("provider/hashicorp/aws"),
            List.of("module/terraform-aws-modules/iam/aws"));

    assertThat(controller.update(authentication, request)).isEqualTo(expected);
    verify(settings).update(any(), eq("admin-subject"));
  }

  @Test
  void deniesHomepageUpdatesFromNonAdministrators() {
    when(identities.accessContext(authentication))
        .thenReturn(new AccessContext("member-subject", Set.of("APM0000001"), false));
    var request =
        new HomepageSettingsController.UpdateHomepageRequest(
            true,
            "Registry notice",
            "Approved content is available.",
            null,
            null,
            List.of("provider/hashicorp/aws"),
            List.of("module/terraform-aws-modules/iam/aws"));

    assertThatThrownBy(() -> controller.update(authentication, request))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessageContaining("administrator");
  }

  private static HomepageSettings settings() {
    return new HomepageSettings(
        true,
        "Registry notice",
        "Approved content is available.",
        null,
        null,
        List.of("provider/hashicorp/aws"),
        List.of("module/terraform-aws-modules/iam/aws"),
        Instant.parse("2026-07-22T12:00:00Z"));
  }
}
