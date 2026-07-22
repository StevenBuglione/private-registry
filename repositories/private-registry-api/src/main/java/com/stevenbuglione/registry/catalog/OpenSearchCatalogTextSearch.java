package com.stevenbuglione.registry.catalog;

import com.stevenbuglione.registry.security.identity.AccessContext;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Objects;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.springframework.stereotype.Service;

@Service
public class OpenSearchCatalogTextSearch implements CatalogTextSearch {

  private static final String INDEX = "private-registry-packages-write";
  private final OpenSearchClient client;

  public OpenSearchCatalogTextSearch(OpenSearchClient client) {
    this.client = client;
  }

  @Override
  public List<String> findPackageIds(
      AccessContext accessContext, CatalogQuery query, int maximumResults) {
    var searchQuery = Objects.requireNonNull(query.q(), "A search query is required");
    var packageKind = query.kind();
    try {
      var response =
          client.search(
              search ->
                  search
                      .index(INDEX)
                      .size(maximumResults)
                      .source(source -> source.filter(filter -> filter.includes("id")))
                      .query(
                          root ->
                              root.bool(
                                  bool -> {
                                    bool.must(
                                        clause ->
                                            clause.multiMatch(
                                                match ->
                                                    match
                                                        .query(searchQuery)
                                                        .fields(
                                                            "name^5",
                                                            "title^4",
                                                            "namespace^3",
                                                            "description",
                                                            "keywords")));
                                    if (packageKind != null) {
                                      bool.filter(
                                          clause ->
                                              clause.term(
                                                  term ->
                                                      term.field("kind")
                                                          .value(
                                                              FieldValue.of(
                                                                  packageKind.jsonValue()))));
                                    }
                                    if (query.provider() != null) {
                                      bool.filter(
                                          clause ->
                                              clause.bool(
                                                  provider ->
                                                      provider
                                                          .should(
                                                              name ->
                                                                  name.term(
                                                                      term ->
                                                                          term.field("name.keyword")
                                                                              .value(
                                                                                  FieldValue.of(
                                                                                      query
                                                                                          .provider()))))
                                                          .should(
                                                              target ->
                                                                  target.term(
                                                                      term ->
                                                                          term.field("target")
                                                                              .value(
                                                                                  FieldValue.of(
                                                                                      query
                                                                                          .provider()))))
                                                          .minimumShouldMatch("1")));
                                    }
                                    if (query.lifecycle() != null) {
                                      bool.filter(
                                          clause ->
                                              clause.term(
                                                  term ->
                                                      term.field("lifecycle")
                                                          .value(
                                                              FieldValue.of(query.lifecycle()))));
                                    }
                                    if (query.risk() != null) {
                                      bool.filter(
                                          clause ->
                                              clause.term(
                                                  term ->
                                                      term.field("risk_tier")
                                                          .value(FieldValue.of(query.risk()))));
                                    }
                                    var allowedApmIds =
                                        query.apmId() == null
                                            ? accessContext.apmIds()
                                            : java.util.Set.of(query.apmId());
                                    if (!accessContext.registryAdmin() || query.apmId() != null) {
                                      bool.filter(
                                          clause ->
                                              clause.terms(
                                                  terms ->
                                                      terms
                                                          .field("apm_ids")
                                                          .terms(
                                                              values ->
                                                                  values.value(
                                                                      allowedApmIds.stream()
                                                                          .map(FieldValue::of)
                                                                          .toList()))));
                                    }
                                    return bool;
                                  })),
              Object.class);
      return response.hits().hits().stream()
          .map(hit -> hit.id() == null ? null : hit.id())
          .filter(java.util.Objects::nonNull)
          .distinct()
          .toList();
    } catch (IOException exception) {
      throw new UncheckedIOException("Unable to search the catalog index", exception);
    }
  }
}
