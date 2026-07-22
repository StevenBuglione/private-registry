package com.stevenbuglione.registry.catalog;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

class HomepageSettingsServiceTest {

  private final HomepageSettingsService service =
      new HomepageSettingsService(mock(JdbcClient.class));

  @Test
  void rejectsIncompleteOrUnsafeNotificationLinks() {
    assertThatThrownBy(
            () ->
                service.update(
                    update("Learn more", null, List.of("provider/hashicorp/aws")), "admin"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("provided together");

    assertThatThrownBy(
            () ->
                service.update(
                    update("Learn more", "javascript:alert(1)", List.of("provider/hashicorp/aws")),
                    "admin"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("HTTPS or Registry-relative");

    assertThatThrownBy(
            () ->
                service.update(
                    update("Learn more", "//evil.example", List.of("provider/hashicorp/aws")),
                    "admin"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("HTTPS or Registry-relative");
  }

  @Test
  void rejectsInvalidAndOversizedFeaturedProviderSelections() {
    assertThatThrownBy(() -> service.update(update(null, null, List.of("hashicorp/aws")), "admin"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("provider/namespace/name");

    assertThatThrownBy(
            () ->
                service.update(
                    update(
                        null,
                        null,
                        List.of(
                            "provider/hashicorp/aws",
                            "provider/hashicorp/azurerm",
                            "provider/hashicorp/google",
                            "provider/hashicorp/kubernetes",
                            "provider/hashicorp/helm",
                            "provider/hashicorp/random",
                            "provider/hashicorp/tls")),
                    "admin"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("six featured providers");
  }

  private static HomepageSettingsService.Update update(
      @Nullable String linkLabel, @Nullable String linkUrl, List<String> providerIds) {
    return new HomepageSettingsService.Update(
        true, "Registry notice", "Approved content is available.", linkLabel, linkUrl, providerIds);
  }
}
