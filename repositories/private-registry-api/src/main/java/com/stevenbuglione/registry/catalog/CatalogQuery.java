package com.stevenbuglione.registry.catalog;

import com.stevenbuglione.registry.model.PackageKind;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public final class CatalogQuery {

  private static final Set<String> SORTS = Set.of("updated", "name", "risk", "relevance");
  private final @Nullable String q;
  private final @Nullable PackageKind kind;
  private final @Nullable String provider;
  private final @Nullable String apmId;
  private final @Nullable String lifecycle;
  private final @Nullable String approval;
  private final @Nullable String risk;
  private final String sort;
  private final @Nullable String cursor;
  private final int limit;

  public CatalogQuery(Criteria criteria) {
    this.q = normalize(criteria.q());
    this.kind = criteria.kind();
    this.provider = normalize(criteria.provider());
    this.apmId = normalize(criteria.apmId());
    this.lifecycle = normalize(criteria.lifecycle());
    this.approval = normalize(criteria.approval());
    this.risk = normalize(criteria.risk());
    var normalizedSort = normalize(criteria.sort());
    normalizedSort = normalizedSort == null ? "updated" : normalizedSort;
    if ("relevance".equals(normalizedSort) && this.q == null) {
      normalizedSort = "updated";
    }
    if (!SORTS.contains(normalizedSort)) {
      throw new IllegalArgumentException("sort must be one of relevance, updated, name, or risk");
    }
    this.sort = normalizedSort;
    this.cursor = normalize(criteria.cursor());
    this.limit = criteria.limit() <= 0 ? 25 : Math.min(criteria.limit(), 100);
  }

  public @Nullable String q() {
    return q;
  }

  public @Nullable PackageKind kind() {
    return kind;
  }

  public @Nullable String provider() {
    return provider;
  }

  public @Nullable String apmId() {
    return apmId;
  }

  public @Nullable String lifecycle() {
    return lifecycle;
  }

  public @Nullable String approval() {
    return approval;
  }

  public @Nullable String risk() {
    return risk;
  }

  public String sort() {
    return sort;
  }

  public @Nullable String cursor() {
    return cursor;
  }

  public int limit() {
    return limit;
  }

  private static @Nullable String normalize(@Nullable String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  public record Criteria(
      @Nullable String q,
      @Nullable PackageKind kind,
      @Nullable String provider,
      @Nullable String apmId,
      @Nullable String lifecycle,
      @Nullable String approval,
      @Nullable String risk,
      String sort,
      @Nullable String cursor,
      int limit) {}
}
