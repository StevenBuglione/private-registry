package com.stevenbuglione.registry.web;

import com.stevenbuglione.registry.analytics.TrafficAnalyticsService;
import com.stevenbuglione.registry.security.identity.RegistryIdentityService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analytics/page-views")
public class TrafficAnalyticsController {

  private final RegistryIdentityService identities;
  private final TrafficAnalyticsService traffic;

  public TrafficAnalyticsController(
      RegistryIdentityService identities, TrafficAnalyticsService traffic) {
    this.identities = identities;
    this.traffic = traffic;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void record(Authentication authentication, @RequestBody PageViewRequest request) {
    traffic.recordPageView(identities.principal(authentication), request.path());
  }

  record PageViewRequest(String path) {}
}
