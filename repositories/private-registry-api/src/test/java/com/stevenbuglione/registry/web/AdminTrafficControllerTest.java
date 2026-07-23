package com.stevenbuglione.registry.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stevenbuglione.registry.analytics.TrafficAnalyticsService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class AdminTrafficControllerTest {

  private final TrafficAnalyticsService traffic = mock(TrafficAnalyticsService.class);
  private final AdminTrafficController controller = new AdminTrafficController(traffic);

  @Test
  void delegatesTheRequestedReportAfterTheHttpSecurityBoundary() {
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

    assertThat(controller.report(30, 50)).isSameAs(expected);
    verify(traffic).report(30, 50);
  }
}
