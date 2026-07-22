package com.stevenbuglione.registry.ingestion;

import java.util.Map;

public interface CatalogSearchIndex {

  void index(String indexName, String documentId, Map<String, Object> document);
}
