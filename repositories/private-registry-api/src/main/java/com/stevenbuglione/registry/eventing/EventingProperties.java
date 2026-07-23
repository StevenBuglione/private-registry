package com.stevenbuglione.registry.eventing;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("registry.eventing")
public record EventingProperties(
    boolean enabled,
    Duration pollInterval,
    Duration claimRecoveryDelay,
    Duration claimTimeout,
    int pollBatchSize,
    int maximumAttempts,
    Duration completedRetention,
    Duration deadLetterRetention) {

  public EventingProperties {
    if (pollInterval == null) {
      pollInterval = Duration.ofSeconds(30);
    }
    if (claimRecoveryDelay == null) {
      claimRecoveryDelay = Duration.ofMinutes(1);
    }
    if (claimTimeout == null || claimTimeout.isNegative() || claimTimeout.isZero()) {
      claimTimeout = Duration.ofMinutes(5);
    }
    if (pollBatchSize < 1 || pollBatchSize > 100) {
      pollBatchSize = 25;
    }
    if (maximumAttempts < 1) {
      maximumAttempts = 5;
    }
    if (completedRetention == null
        || completedRetention.isNegative()
        || completedRetention.isZero()) {
      completedRetention = Duration.ofDays(7);
    }
    if (deadLetterRetention == null
        || deadLetterRetention.isNegative()
        || deadLetterRetention.isZero()) {
      deadLetterRetention = Duration.ofDays(90);
    }
  }
}
