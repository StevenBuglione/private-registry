package com.stevenbuglione.registry.web;

import com.stevenbuglione.registry.analytics.TrafficAnalyticsService;
import com.stevenbuglione.registry.security.identity.RegistryIdentityService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/traffic")
public class AdminTrafficController {

  private final RegistryIdentityService identities;
  private final TrafficAnalyticsService traffic;

  public AdminTrafficController(
      RegistryIdentityService identities, TrafficAnalyticsService traffic) {
    this.identities = identities;
    this.traffic = traffic;
  }

  @GetMapping
  public TrafficAnalyticsService.TrafficReport report(
      Authentication authentication,
      @RequestParam(defaultValue = "30") int days,
      @RequestParam(defaultValue = "50") int visitorLimit) {
    var access = identities.accessContext(authentication);
    if (!access.registryAdmin()) {
      throw new AccessDeniedException("Registry administrator access is required");
    }
    return traffic.report(days, visitorLimit);
  }
}
