package com.stevenbuglione.registry.ingestion;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "registry.ingestion", name = "enabled", havingValue = "true")
public class OpenSearchCatalogSearchIndex implements CatalogSearchIndex {

  private final OpenSearchClient client;

  public OpenSearchCatalogSearchIndex(OpenSearchClient client) {
    this.client = client;
  }

  @Override
  public void index(String indexName, String documentId, Map<String, Object> document) {
    try {
      client.index(request -> request.index(indexName).id(documentId).document(document));
    } catch (IOException exception) {
      throw new UncheckedIOException("Unable to index catalog document " + documentId, exception);
    }
  }
}
