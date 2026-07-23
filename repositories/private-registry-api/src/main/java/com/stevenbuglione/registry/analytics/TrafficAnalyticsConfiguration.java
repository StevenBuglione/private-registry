package com.stevenbuglione.registry.analytics;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
class TrafficAnalyticsConfiguration {}
