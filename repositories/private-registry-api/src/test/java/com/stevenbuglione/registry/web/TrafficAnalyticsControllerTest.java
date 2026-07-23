package com.stevenbuglione.registry.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stevenbuglione.registry.analytics.TrafficAnalyticsService;
import com.stevenbuglione.registry.security.identity.RegistryIdentityService;
import com.stevenbuglione.registry.security.identity.RegistryPrincipal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

class TrafficAnalyticsControllerTest {

  @Test
  void recordsOnlyTheServerResolvedIdentityAndPath() {
    var identities = mock(RegistryIdentityService.class);
    var traffic = mock(TrafficAnalyticsService.class);
    var authentication = mock(Authentication.class);
    var principal =
        new RegistryPrincipal(
            "subject-1", "Ada Lovelace", "ada@example.test", Set.of("registry-user"), List.of());
    when(identities.principal(authentication)).thenReturn(principal);
    var controller = new TrafficAnalyticsController(identities, traffic);

    controller.record(authentication, new TrafficAnalyticsController.PageViewRequest("/modules"));

    verify(traffic).recordPageView(principal, "/modules");
  }
}
