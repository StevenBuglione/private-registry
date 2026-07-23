package com.stevenbuglione.registry.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.stevenbuglione.registry.storage.ArtifactStorageStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

class ArtifactoryControllerTest {

  @Test
  void exposesStatusThroughTheApplicationPort() {
    var storage = mock(ArtifactStorageStatus.class);
    var expected =
        new ArtifactStorageStatus.Status(
            true,
            "https://artifacts.example.test",
            List.of(new ArtifactStorageStatus.RepositoryStatus("providers", "LOCAL")));
    when(storage.status()).thenReturn(expected);

    assertThat(new ArtifactoryController(storage).status()).isSameAs(expected);
  }
}
