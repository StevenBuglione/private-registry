package com.stevenbuglione.registry.seed;

import com.stevenbuglione.registry.ingestion.CatalogManifestV1;
import com.stevenbuglione.registry.ingestion.ContentDigest;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Publishes extracted documentation concurrently while preserving task failure context. */
@Component
@ConditionalOnProperty(prefix = "registry.seed", name = "enabled", havingValue = "true")
final class CatalogDocumentPublication {

  private final CatalogMetadataPublication catalog;

  CatalogDocumentPublication(CatalogMetadataPublication catalog) {
    this.catalog = catalog;
  }

  List<CatalogManifestV1.Document> publish(
      CuratedSeedCatalog.SeedEntry entry,
      String version,
      String basePath,
      List<TerraformMetadataExtractor.ExtractedDocument> documents) {
    var threadFactory = Thread.ofVirtual().name("catalog-document-upload-", 0).factory();
    try (var executor = java.util.concurrent.Executors.newFixedThreadPool(8, threadFactory)) {
      var tasks =
          documents.stream()
              .<java.util.concurrent.Callable<CatalogManifestV1.Document>>map(
                  document -> () -> publishOne(entry, version, basePath, document))
              .toList();
      var futures = executor.invokeAll(tasks);
      var result = new ArrayList<CatalogManifestV1.Document>(futures.size());
      for (var future : futures) {
        try {
          result.add(future.get());
        } catch (java.util.concurrent.ExecutionException exception) {
          throw new IllegalStateException("Catalog document upload failed", exception);
        }
      }
      return List.copyOf(result);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while uploading catalog documents", exception);
    }
  }

  private CatalogManifestV1.Document publishOne(
      CuratedSeedCatalog.SeedEntry entry,
      String version,
      String basePath,
      TerraformMetadataExtractor.ExtractedDocument document) {
    var artifactPath =
        document.path().equals("README.md")
            ? basePath + "/README.md"
            : basePath + "/docs/" + document.path();
    var digest = ContentDigest.sha256(document.content());
    var documentationProperties =
        new java.util.LinkedHashMap<String, Object>(
            SeedCatalogPolicy.artifactProperties(entry, version, "documentation"));
    documentationProperties.put("registry.sha256", digest);
    documentationProperties.put("registry.document.path", document.path());
    catalog.publishDocument(artifactPath, document.content(), documentationProperties);
    return new CatalogManifestV1.Document(
        document.path(),
        document.title(),
        document.contentType(),
        artifactPath,
        digest,
        document.content().length);
  }
}
