package com.stevenbuglione.registry.artifactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.stevenbuglione.registry.config.ArtifactoryProperties;
import java.net.URI;
import java.time.Duration;
import org.jfrog.artifactory.client.Artifactory;
import org.jfrog.artifactory.client.ArtifactorySystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ArtifactoryGatewayTest {

  @Mock private Artifactory client;

  @Mock private ArtifactorySystem system;

  @Test
  void reportsAnonymousConnectivityWithoutExposingCredentials() {
    when(client.system()).thenReturn(system);
    when(system.ping()).thenReturn(true);
    var properties =
        new ArtifactoryProperties(
            URI.create("https://artifacts.example.invalid/artifactory"),
            "",
            Duration.ofSeconds(3),
            Duration.ofSeconds(5),
            "modules-remote",
            "providers-remote");

    var status = new ArtifactoryGateway(client, properties).status();

    assertThat(status.reachable()).isTrue();
    assertThat(status.url()).isEqualTo("https://artifacts.example.invalid/artifactory");
    assertThat(status.repositories())
        .extracting(ArtifactoryGateway.RepositoryStatus::repositoryClass)
        .containsExactly("authentication-required", "authentication-required");
  }
}
