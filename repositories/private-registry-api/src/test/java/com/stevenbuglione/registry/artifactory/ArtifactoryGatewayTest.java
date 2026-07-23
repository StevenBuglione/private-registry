package com.stevenbuglione.registry.artifactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.stevenbuglione.registry.config.ArtifactoryProperties;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import org.jfrog.artifactory.client.Artifactory;
import org.jfrog.artifactory.client.ArtifactoryResponse;
import org.jfrog.artifactory.client.ArtifactorySystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ArtifactoryGatewayTest {

  @Mock private Artifactory client;

  @Mock private ArtifactorySystem system;

  @Mock private ArtifactoryResponse response;

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

  @Test
  void readsFileStatisticsThroughTheOfficialJfrogClient() throws Exception {
    when(client.restCall(any())).thenReturn(response);
    when(response.isSuccessResponse()).thenReturn(true);
    when(response.getRawBody())
        .thenReturn(
            """
            {
              "downloadCount": 42,
              "lastDownloaded": 1751408000000,
              "lastDownloadedBy": "registry-user",
              "remoteDownloadCount": 0,
              "remoteLastDownloaded": 0
            }
            """);
    var properties =
        new ArtifactoryProperties(
            URI.create("https://artifacts.example.invalid/artifactory"),
            "token",
            Duration.ofSeconds(3),
            Duration.ofSeconds(5),
            "modules-local",
            "providers-local");

    var statistics =
        new ArtifactoryGateway(client, properties)
            .downloadStatistics("modules-local", "Azure/web/0.16.0.zip");

    assertThat(statistics.downloadCount()).isEqualTo(42);
    assertThat(statistics.lastDownloadedAt()).isEqualTo(Instant.ofEpochMilli(1_751_408_000_000L));
  }
}
