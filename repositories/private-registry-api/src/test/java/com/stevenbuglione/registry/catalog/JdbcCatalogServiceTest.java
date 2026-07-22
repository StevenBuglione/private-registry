package com.stevenbuglione.registry.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JdbcCatalogServiceTest {

  @Test
  void supportsBothProviderDocumentationConventions() {
    assertThat(JdbcCatalogService.documentationPathCandidates("index.md"))
        .containsExactly("index.md", "README.md");
    assertThat(JdbcCatalogService.documentationPathCandidates("README.md"))
        .containsExactly("README.md", "index.md");
  }
}
