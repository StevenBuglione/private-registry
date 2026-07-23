package com.stevenbuglione.registry.web;

import com.stevenbuglione.registry.analytics.TrafficAnalyticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/traffic")
public class AdminTrafficController {

  private final TrafficAnalyticsService traffic;

  public AdminTrafficController(TrafficAnalyticsService traffic) {
    this.traffic = traffic;
  }

  @GetMapping
  public TrafficAnalyticsService.TrafficReport report(
      @RequestParam(defaultValue = "30") int days,
      @RequestParam(defaultValue = "50") int visitorLimit) {
    return traffic.report(days, visitorLimit);
  }
}
