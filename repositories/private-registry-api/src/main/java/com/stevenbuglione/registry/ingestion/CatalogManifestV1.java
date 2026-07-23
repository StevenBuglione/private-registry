package com.stevenbuglione.registry.ingestion;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.stevenbuglione.registry.model.PackageKind;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public final class CatalogManifestV1 {

  private static final Set<String> SYMBOL_KINDS =
      Set.of("input", "output", "resource", "data-source", "function", "guide", "dependency");
  private static final Set<String> REGISTRY_TIERS =
      Set.of("official", "partner", "partner-premier", "community");
  private static final Set<String> REGISTRY_CATEGORIES =
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
  private final int schemaVersion;
  private final String kind;
  private final Identity identity;
  private final Display display;
  private final RegistryLocation registry;
  private final Compatibility compatibility;
  private final Source source;
  private final Release release;
  private final Access access;
  private final @Nullable List<Document> documents;
  private final @Nullable List<Symbol> symbols;

  @JsonCreator
  public CatalogManifestV1(
      @JsonProperty("schemaVersion") int schemaVersion,
      @JsonProperty("kind") String kind,
      @JsonProperty("identity") Identity identity,
      @JsonProperty("display") Display display,
      @JsonProperty("registry") RegistryLocation registry,
      @JsonProperty("compatibility") Compatibility compatibility,
      @JsonProperty("source") Source source,
      @JsonProperty("release") Release release,
      @JsonProperty("access") Access access,
      @JsonProperty("documents") @Nullable List<Document> documents,
      @JsonProperty("symbols") @Nullable List<Symbol> symbols) {
    this.schemaVersion = schemaVersion;
    this.kind = kind;
    this.identity = identity;
    this.display = display;
    this.registry = registry;
    this.compatibility = compatibility;
    this.source = source;
    this.release = release;
    this.access = access;
    this.documents = documents;
    this.symbols = symbols;
  }

  @JsonProperty("schemaVersion")
  public int schemaVersion() {
    return schemaVersion;
  }

  @JsonProperty("kind")
  public String kind() {
    return kind;
  }

  @JsonProperty("identity")
  public Identity identity() {
    return identity;
  }

  @JsonProperty("display")
  public Display display() {
    return display;
  }

  @JsonProperty("registry")
  public RegistryLocation registry() {
    return registry;
  }

  @JsonProperty("compatibility")
  public Compatibility compatibility() {
    return compatibility;
  }

  @JsonProperty("source")
  public Source source() {
    return source;
  }

  @JsonProperty("release")
  public Release release() {
    return release;
  }

  @JsonProperty("access")
  public Access access() {
    return access;
  }

  @JsonProperty("documents")
  public @Nullable List<Document> documents() {
    return documents;
  }

  @JsonProperty("symbols")
  public @Nullable List<Symbol> symbols() {
    return symbols;
  }

  @Override
  public boolean equals(@Nullable Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof CatalogManifestV1 that)) {
      return false;
    }
    return schemaVersion == that.schemaVersion
        && kind.equals(that.kind)
        && identity.equals(that.identity)
        && display.equals(that.display)
        && registry.equals(that.registry)
        && compatibility.equals(that.compatibility)
        && source.equals(that.source)
        && release.equals(that.release)
        && access.equals(that.access)
        && Objects.equals(documents, that.documents)
        && Objects.equals(symbols, that.symbols);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        schemaVersion,
        kind,
        identity,
        display,
        registry,
        compatibility,
        source,
        release,
        access,
        documents,
        symbols);
  }

  public void validate() {
    validateSchemaVersion();
    var packageKind = packageKind();
    validateRequiredSections();
    validateIdentity(packageKind);
    validateDisplay();
    validateRegistrySourceAndRelease();
    validateAccess();
    validateDocuments();
    validateSymbols();
  }

  private void validateSchemaVersion() {
    if (schemaVersion != 1) {
      throw new QuarantineException("unsupported_manifest_schema", "Manifest schema must be 1");
    }
  }

  private void validateRequiredSections() {
    require(identity, "identity");
    require(display, "display");
    require(registry, "registry");
    require(compatibility, "compatibility");
    require(source, "source");
    require(release, "release");
    require(access, "access");
  }

  private void validateIdentity(PackageKind packageKind) {
    requireText(identity.namespace(), "identity.namespace");
    requireText(identity.name(), "identity.name");
    requireText(identity.version(), "identity.version");
    if (packageKind == PackageKind.MODULE) {
      requireText(identity.target(), "identity.target");
    } else if (identity.target() != null && !identity.target().isBlank()) {
      throw new QuarantineException("invalid_provider_target", "Providers cannot declare a target");
    }
  }

  private void validateDisplay() {
    requireText(display.title(), "display.title");
    requireText(display.description(), "display.description");
    requireText(display.supportLevel(), "display.supportLevel");
    requireText(display.verification(), "display.verification");
    requireText(display.lifecycle(), "display.lifecycle");
    requireText(display.riskTier(), "display.riskTier");
    requireText(display.visibility(), "display.visibility");
    validateRegistryTier();
    validateRegistryCategories();
  }

  private void validateRegistryTier() {
    if (display.tier() != null && !REGISTRY_TIERS.contains(display.tier())) {
      throw new QuarantineException(
          "invalid_registry_tier", "display.tier is not part of the Registry vocabulary");
    }
  }

  private void validateRegistryCategories() {
    if (display.categories() == null) {
      return;
    }
    var categories = new HashSet<String>();
    display.categories().forEach(category -> validateRegistryCategory(category, categories));
  }

  private static void validateRegistryCategory(String category, Set<String> categories) {
    requireText(category, "display.categories");
    if (!REGISTRY_CATEGORIES.contains(category)) {
      throw new QuarantineException(
          "invalid_registry_category",
          "display.categories contains a value outside the Registry vocabulary");
    }
    if (!categories.add(category)) {
      throw new QuarantineException(
          "duplicate_registry_category", "display.categories must be unique");
    }
  }

  private void validateRegistrySourceAndRelease() {
    requireText(registry.repository(), "registry.repository");
    requireSafePath(registry.artifactPath(), "registry.artifactPath");
    requireText(source.repository(), "source.repository");
    requireText(source.commit(), "source.commit");
    requireText(source.tag(), "source.tag");
    requireText(release.packageDigest(), "release.packageDigest");
    requireDigest(release.packageDigest(), "release.packageDigest");
    requireText(release.documentationDigest(), "release.documentationDigest");
    requireDigest(release.documentationDigest(), "release.documentationDigest");
    require(release.publishedAt(), "release.publishedAt");
  }

  private void validateAccess() {
    if (access.apmIds() == null || access.apmIds().isEmpty()) {
      throw new QuarantineException(
          "missing_apm_assignment", "At least one APM assignment is required");
    }
    access.apmIds().forEach(apmId -> requireText(apmId, "access.apmIds"));
  }

  private void validateDocuments() {
    if (documents == null) {
      return;
    }
    documents.forEach(
        document -> {
          requireSafePath(document.path(), "documents.path");
          requireSafePath(document.artifactPath(), "documents.artifactPath");
          requireDigest(document.digest(), "documents.digest");
        });
  }

  private void validateSymbols() {
    if (symbols == null) {
      return;
    }
    var identities = new HashSet<String>();
    symbols.forEach(symbol -> validateSymbol(symbol, identities));
  }

  private static void validateSymbol(Symbol symbol, Set<String> identities) {
    requireText(symbol.kind(), "symbols.kind");
    requireText(symbol.name(), "symbols.name");
    if (!SYMBOL_KINDS.contains(symbol.kind())) {
      throw new QuarantineException(
          "invalid_symbol_kind", "Symbol kind is not part of the Registry vocabulary");
    }
    requireOptionalSafePath(symbol.path(), "symbols.path");
    requireOptionalText(symbol.type(), "symbols.type");
    if (!identities.add(symbol.kind() + "\u0000" + symbol.name())) {
      throw new QuarantineException(
          "duplicate_symbol", "Symbol kind and name must be unique within a package version");
    }
  }

  public PackageKind packageKind() {
    try {
      var result = PackageKind.from(kind);
      if (result == null) {
        throw new IllegalArgumentException("empty kind");
      }
      return result;
    } catch (IllegalArgumentException exception) {
      throw new QuarantineException(
          "invalid_package_kind", "Package kind must be module or provider", exception);
    }
  }

  public String publicId() {
    return packageKind() == PackageKind.MODULE
        ? "module/%s/%s/%s".formatted(identity.namespace(), identity.name(), identity.target())
        : "provider/%s/%s".formatted(identity.namespace(), identity.name());
  }

  public String targetOrEmpty() {
    return identity.target() == null ? "" : identity.target();
  }

  private static void require(@Nullable Object value, String field) {
    if (value == null) {
      throw new QuarantineException("invalid_manifest", field + " is required");
    }
  }

  private static void requireText(@Nullable String value, String field) {
    if (value == null || value.isBlank()) {
      throw new QuarantineException("invalid_manifest", field + " is required");
    }
  }

  private static void requireSafePath(@Nullable String value, String field) {
    if (value == null || value.isBlank()) {
      throw new QuarantineException("invalid_manifest", field + " is required");
    }
    if (value.startsWith("/") || value.contains("..") || value.contains("\\")) {
      throw new QuarantineException("unsafe_artifact_path", field + " is unsafe");
    }
  }

  private static void requireOptionalSafePath(@Nullable String value, String field) {
    if (value != null) {
      requireSafePath(value, field);
    }
  }

  private static void requireOptionalText(@Nullable String value, String field) {
    if (value != null && value.isBlank()) {
      throw new QuarantineException("invalid_manifest", field + " cannot be blank");
    }
  }

  private static void requireDigest(@Nullable String value, String field) {
    if (value == null || !value.matches("sha256:[0-9a-f]{64}")) {
      throw new QuarantineException("invalid_digest", field + " must be a SHA-256 digest");
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
  public record Identity(String namespace, String name, @Nullable String target, String version) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
  public record Display(
      String title,
      String description,
      @Nullable List<String> keywords,
      @Nullable List<String> owners,
      @Nullable String supportChannel,
      String supportLevel,
      String verification,
      String lifecycle,
      String riskTier,
      String visibility,
      @Nullable String tier,
      @Nullable List<String> categories) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
  public record RegistryLocation(
      @Nullable String hostname,
      String repository,
      String source,
      String artifactPath,
      @Nullable String consoleUrl) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
  public record Compatibility(String terraform) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
  public record Source(String repository, String commit, String tag) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
  public record Release(
      Instant publishedAt,
      String packageDigest,
      @Nullable String documentationPath,
      String documentationDigest,
      @Nullable String sbomPath,
      @Nullable String provenancePath,
      @Nullable String changelogPath,
      boolean prerelease,
      boolean deprecated,
      boolean revoked) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
  public record Access(List<String> apmIds) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
  public record Document(
      String path,
      String title,
      String contentType,
      String artifactPath,
      String digest,
      long sizeBytes) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
  public record Symbol(
      String kind,
      String name,
      @Nullable String description,
      @Nullable String path,
      @Nullable String type,
      @JsonProperty("default_value") @Nullable String defaultValue,
      boolean required,
      boolean sensitive) {}
}
