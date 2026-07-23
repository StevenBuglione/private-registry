package com.stevenbuglione.registry.catalog;

import com.stevenbuglione.registry.model.PackageKind;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public final class CatalogQuery {

  private static final Set<String> SORTS = Set.of("updated", "name", "risk", "relevance");
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
  private final List<String> providers;
  private final List<String> tiers;
  private final List<String> categories;
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
    this.providers = csvValues(criteria.provider(), null, "provider");
    this.tiers = csvValues(criteria.tier(), TIERS, "tier");
    validateTierCombination(this.tiers);
    this.categories = csvValues(criteria.category(), CATEGORIES, "category");
    this.apmId = normalize(criteria.apmId());
    this.lifecycle = normalize(criteria.lifecycle());
    this.approval = normalize(criteria.approval());
    this.risk = normalize(criteria.risk());
    this.sort = normalizeSort(criteria.sort(), this.q);
    this.cursor = normalize(criteria.cursor());
    this.limit = criteria.limit() <= 0 ? 25 : Math.min(criteria.limit(), 100);
  }

  public @Nullable String q() {
    return q;
  }

  public @Nullable PackageKind kind() {
    return kind;
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
      throw new IllegalArgumentException("sort must be one of relevance, updated, name, or risk");
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
      @Nullable String apmId,
      @Nullable String lifecycle,
      @Nullable String approval,
      @Nullable String risk,
      String sort,
      @Nullable String cursor,
      int limit) {}
}
