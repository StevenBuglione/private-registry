package com.stevenbuglione.registry.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stevenbuglione.registry.model.PackageKind;
import org.junit.jupiter.api.Test;

class CatalogQueryTest {

  @Test
  void supportsRelevanceForSearches() {
    var query =
        new CatalogQuery(
            new CatalogQuery.Criteria(
                "cloud",
                null,
                "aws,azurerm",
                "official,partner",
                "public-cloud,networking",
                null,
                null,
                null,
                null,
                "relevance",
                null,
                25,
                null));

    assertThat(query.sort()).isEqualTo("relevance");
    assertThat(query.providers()).containsExactly("aws", "azurerm");
    assertThat(query.tiers()).containsExactly("official", "partner");
    assertThat(query.categories()).containsExactly("public-cloud", "networking");
  }

  @Test
  void normalizesRelevanceWithoutAQueryToUpdated() {
    var query =
        new CatalogQuery(
            new CatalogQuery.Criteria(
                null, null, null, null, null, null, null, null, null, "relevance", null, 25, null));

    assertThat(query.sort()).isEqualTo("updated");
  }

  @Test
  void supportsDownloadSortingWithoutAQuery() {
    var query =
        new CatalogQuery(
            new CatalogQuery.Criteria(
                null,
                PackageKind.MODULE,
                "azurerm",
                null,
                null,
                null,
                null,
                null,
                null,
                "downloads",
                null,
                4,
                null));

    assertThat(query.sort()).isEqualTo("downloads");
  }

  @Test
  void normalizesAnExactNamespaceFilter() {
    var query =
        new CatalogQuery(
            new CatalogQuery.Criteria(
                null, null, null, null, null, null, null, null, null, "updated", null, 25,
                " Azure "));

    assertThat(query.namespace()).isEqualTo("Azure");
  }

  @Test
  void rejectsUnsafeNamespaceFilters() {
    var criteria =
        new CatalogQuery.Criteria(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            "updated",
            null,
            25,
            "Azure/../../secret");

    assertThatThrownBy(() -> new CatalogQuery(criteria))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("namespace");
  }

  @Test
  void rejectsNoneCombinedWithAProviderTier() {
    var criteria =
        new CatalogQuery.Criteria(
            null,
            PackageKind.PROVIDER,
            null,
            "none,official",
            null,
            null,
            null,
            null,
            null,
            "updated",
            null,
            25,
            null);

    assertThatThrownBy(() -> new CatalogQuery(criteria))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tier none");
  }
}
