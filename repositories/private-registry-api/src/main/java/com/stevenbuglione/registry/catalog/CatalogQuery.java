package com.stevenbuglione.registry.catalog;

import com.stevenbuglione.registry.model.PackageKind;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public final class CatalogQuery {

  private static final int MAX_PAGE = 10_000;
  private static final Set<String> SORTS = Set.of("updated", "name", "relevance", "downloads");
  private static final Set<String> TIERS =
      Set.of("official", "partner", "partner-premier", "community", "none");
  private static final Set<String> CATEGORIES =
      Set.of(
          "asset-management",
          "cloud-automation",
          "communication-messaging",
          "container-orchestration",
          "ci-cd",
          "data-management",
          "database",
          "infrastructure",
          "logging-monitoring",
          "networking",
          "platform",
          "security-authentication",
          "utility",
          "vcs",
          "web-services",
          "hashicorp-platform",
          "infrastructure-management",
          "public-cloud");
  private final @Nullable String q;
  private final @Nullable PackageKind kind;
  private final @Nullable String namespace;
  private final List<String> providers;
  private final List<String> tiers;
  private final List<String> categories;
  private final String sort;
  private final @Nullable String cursor;
  private final int limit;
  private final int page;

  public CatalogQuery(Criteria criteria) {
    this(criteria, null);
  }

  public CatalogQuery(Criteria criteria, @Nullable Integer page) {
    this.q = normalize(criteria.q());
    this.kind = criteria.kind();
    this.namespace = normalizeNamespace(criteria.namespace());
    this.providers = csvValues(criteria.provider(), null, "provider");
    this.tiers = csvValues(criteria.tier(), TIERS, "tier");
    validateTierCombination(this.tiers);
    this.categories = csvValues(criteria.category(), CATEGORIES, "category");
    this.sort = normalizeSort(criteria.sort(), this.q);
    this.cursor = normalize(criteria.cursor());
    this.limit = criteria.limit() <= 0 ? 25 : Math.min(criteria.limit(), 100);
    this.page = normalizePage(page);
  }

  public @Nullable String q() {
    return q;
  }

  public @Nullable PackageKind kind() {
    return kind;
  }

  public @Nullable String namespace() {
    return namespace;
  }

  public List<String> providers() {
    return providers;
  }

  public List<String> tiers() {
    return tiers;
  }

  public List<String> categories() {
    return categories;
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

  public int page() {
    return page;
  }

  public int offset() {
    return page <= 1 ? 0 : Math.multiplyExact(page - 1, limit);
  }

  private static @Nullable String normalize(@Nullable String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private static @Nullable String normalizeNamespace(@Nullable String value) {
    var normalized = normalize(value);
    if (normalized != null && !normalized.matches("[A-Za-z0-9._-]{1,128}")) {
      throw new IllegalArgumentException("namespace contains an unsupported filter value");
    }
    return normalized;
  }

  private static int normalizePage(@Nullable Integer page) {
    return Math.clamp(Objects.requireNonNullElse(page, 0).longValue(), 0, MAX_PAGE);
  }

  private static void validateTierCombination(List<String> values) {
    if (values.contains("none") && values.size() > 1) {
      throw new IllegalArgumentException("tier none cannot be combined with other values");
    }
  }

  private static String normalizeSort(String value, @Nullable String query) {
    var normalized = normalize(value);
    var selected = normalized == null ? "updated" : normalized;
    if ("relevance".equals(selected) && query == null) {
      selected = "updated";
    }
    if (!SORTS.contains(selected)) {
      throw new IllegalArgumentException(
          "sort must be one of relevance, updated, name, or downloads");
    }
    return selected;
  }

  private static List<String> csvValues(
      @Nullable String value, @Nullable Set<String> allowed, String name) {
    var normalized = normalize(value);
    if (normalized == null) {
      return List.of();
    }
    var values =
        Arrays.stream(normalized.split(",", -1))
            .map(String::trim)
            .filter(candidate -> !candidate.isBlank())
            .distinct()
            .toList();
    if (values.isEmpty()
        || values.stream()
            .anyMatch(
                candidate ->
                    !candidate.matches("[A-Za-z0-9._-]{1,128}")
                        || (allowed != null && !allowed.contains(candidate)))) {
      throw new IllegalArgumentException(name + " contains an unsupported filter value");
    }
    return values;
  }

  public record Criteria(
      @Nullable String q,
      @Nullable PackageKind kind,
      @Nullable String provider,
      @Nullable String tier,
      @Nullable String category,
      String sort,
      @Nullable String cursor,
      int limit,
      @Nullable String namespace) {}
}
