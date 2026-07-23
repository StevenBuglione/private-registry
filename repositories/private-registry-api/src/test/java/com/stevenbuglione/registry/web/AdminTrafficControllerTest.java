package com.stevenbuglione.registry.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.stevenbuglione.registry.analytics.TrafficAnalyticsService;
import com.stevenbuglione.registry.security.identity.AccessContext;
import com.stevenbuglione.registry.security.identity.RegistryIdentityService;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

class AdminTrafficControllerTest {

  private final RegistryIdentityService identities = mock(RegistryIdentityService.class);
  private final TrafficAnalyticsService traffic = mock(TrafficAnalyticsService.class);
  private final Authentication authentication = mock(Authentication.class);
  private final AdminTrafficController controller = new AdminTrafficController(identities, traffic);

  @Test
  void deniesTrafficReportsToNonAdministrators() {
    when(identities.accessContext(authentication))
        .thenReturn(new AccessContext("member", Set.of("APM0000001"), false));

    assertThatThrownBy(() -> controller.report(authentication, 30, 50))
        .isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(traffic);
  }

  @Test
  void returnsTheRequestedReportToAdministrators() {
    when(identities.accessContext(authentication))
        .thenReturn(new AccessContext("admin", Set.of(), true));
    var expected =
        new TrafficAnalyticsService.TrafficReport(
            Instant.parse("2026-07-23T14:30:00Z"),
            30,
            new TrafficAnalyticsService.TrafficSummary(12, 3, 4, 2),
            List.of(),
            List.of(),
            List.of(),
            List.of());
    when(traffic.report(30, 50)).thenReturn(expected);

    assertThat(controller.report(authentication, 30, 50)).isSameAs(expected);
    verify(traffic).report(30, 50);
  }
}
