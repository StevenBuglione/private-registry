package com.stevenbuglione.registry.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CatalogQueryTest {

    @Test
    void supportsRelevanceForSearches() {
        var query = new CatalogQuery(
                "cloud", null, null, null, null, null, null, "relevance", null, 25);

        assertThat(query.sort()).isEqualTo("relevance");
    }

    @Test
    void normalizesRelevanceWithoutAQueryToUpdated() {
        var query = new CatalogQuery(
                null, null, null, null, null, null, null, "relevance", null, 25);

        assertThat(query.sort()).isEqualTo("updated");
    }
}
