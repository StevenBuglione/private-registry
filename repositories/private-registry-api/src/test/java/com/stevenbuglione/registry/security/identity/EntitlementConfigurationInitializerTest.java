package com.stevenbuglione.registry.security.identity;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.jdbc.core.simple.JdbcClient;

class EntitlementConfigurationInitializerTest {

  @Test
  void leavesExistingEntitlementsUntouchedWhenThisProcessHasNoIdentityConfiguration() {
    var jdbc = mock(JdbcClient.class);
    var properties =
        new IdentityProperties(
            false,
            "",
            "",
            "",
            "us-east-1",
            URI.create("https://graph.microsoft.com/v1.0/me/checkMemberGroups"),
            Duration.ofSeconds(5),
            Duration.ofSeconds(60),
            "/",
            "",
            "");
    var initializer = new EntitlementConfigurationInitializer(jdbc, properties);

    initializer.run(mock(ApplicationArguments.class));

    verifyNoInteractions(jdbc);
  }
}
