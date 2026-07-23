package com.stevenbuglione.registry.seed;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CatalogDocumentPublicationTest {

  @Test
  void preservesTheWorkerFailureWhenAConcurrentPublicationFails() {
    var catalog = mock(CatalogMetadataPublication.class);
    var failure = new IllegalStateException("Artifactory unavailable");
    when(catalog.publishDocument(anyString(), any(byte[].class), anyMap())).thenThrow(failure);
    var publication = new CatalogDocumentPublication(catalog);
    var document =
        new TerraformMetadataExtractor.ExtractedDocument(
            "README.md",
            "Provider",
            "text/markdown",
            null,
            "documentation".getBytes(StandardCharsets.UTF_8));

    assertThatThrownBy(
            () ->
                publication.publish(
                    providerEntry(),
                    "1.0.0",
                    "v1/providers/hashicorp/aws/1.0.0",
                    List.of(document)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Catalog document upload failed")
        .hasRootCause(failure);
  }

  private static CuratedSeedCatalog.SeedEntry providerEntry() {
    return new CuratedSeedCatalog.SeedEntry(
        "provider",
        "hashicorp",
        "aws",
        null,
        "AWS",
        "AWS provider",
        "platform",
        "medium",
        null,
        List.of(),
        List.of("APM0000001"),
        List.of("1.0.0"),
        Map.of(),
        "https://example.test/provider.zip",
        Map.of());
  }
}
