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
            new CatalogQuery.Criteria(null, null, null, null, null, "relevance", null, 25, null));

    assertThat(query.sort()).isEqualTo("updated");
  }

  @Test
  void supportsDownloadSortingWithoutAQuery() {
    var query =
        new CatalogQuery(
            new CatalogQuery.Criteria(
                null, PackageKind.MODULE, "azurerm", null, null, "downloads", null, 4, null));

    assertThat(query.sort()).isEqualTo("downloads");
  }

  @Test
  void normalizesAnExactNamespaceFilter() {
    var query =
        new CatalogQuery(
            new CatalogQuery.Criteria(
                null, null, null, null, null, "updated", null, 25, " Azure "));

    assertThat(query.namespace()).isEqualTo("Azure");
  }

  @Test
  void rejectsUnsafeNamespaceFilters() {
    var criteria =
        new CatalogQuery.Criteria(
            null, null, null, null, null, "updated", null, 25, "Azure/../../secret");

    assertThatThrownBy(() -> new CatalogQuery(criteria))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("namespace");
  }

  @Test
  void rejectsNoneCombinedWithAProviderTier() {
    var criteria =
        new CatalogQuery.Criteria(
            null, PackageKind.PROVIDER, null, "none,official", null, "updated", null, 25, null);

    assertThatThrownBy(() -> new CatalogQuery(criteria))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tier none");
  }

  @Test
  void calculatesValidatedPageOffsetsWithoutBreakingCursorClients() {
    var paged =
        new CatalogQuery(
            new CatalogQuery.Criteria(
                null, PackageKind.MODULE, null, null, null, "updated", null, 9, null),
            4);

    assertThat(paged.page()).isEqualTo(4);
    assertThat(paged.offset()).isEqualTo(27);
  }

  @Test
  void clampsPageValuesThatCouldUnderflowOrOverflowOffsetArithmetic() {
    var criteria =
        new CatalogQuery.Criteria(
            null, PackageKind.MODULE, null, null, null, "updated", null, 100, null);

    assertThat(new CatalogQuery(criteria, -1).page()).isZero();
    assertThat(new CatalogQuery(criteria, Integer.MAX_VALUE).page()).isEqualTo(10_000);
  }

  @Test
  void calculatesTheLargestSupportedOffsetWithoutOverflow() {
    var query =
        new CatalogQuery(
            new CatalogQuery.Criteria(
                null, PackageKind.MODULE, null, null, null, "updated", null, 100, null),
            10_000);

    assertThat(query.offset()).isEqualTo(999_900);
  }
}
