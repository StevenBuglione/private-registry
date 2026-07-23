package com.stevenbuglione.registry.seed;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/** Validates source archive paths and classifies Terraform source and documentation locations. */
final class TerraformArchivePath {

  private static final List<DocumentLocation> PROVIDER_DOCUMENT_LOCATIONS =
      List.of(
          new DocumentLocation("resources", "website/docs/r/"),
          new DocumentLocation("data-sources", "website/docs/d/"),
          new DocumentLocation("functions", "website/docs/functions/"),
          new DocumentLocation("guides", "website/docs/guides/"),
          new DocumentLocation("resources", "docs/resources/"),
          new DocumentLocation("data-sources", "docs/data-sources/"),
          new DocumentLocation("functions", "docs/functions/"),
          new DocumentLocation("guides", "docs/guides/"));

  private TerraformArchivePath() {}

  static @Nullable String relativePath(String archivePath) {
    var normalized = archivePath.replace('\\', '/');
    try {
      requireSafeRelativePath(normalized);
      var separator = normalized.indexOf('/');
      if (separator < 0 || separator == normalized.length() - 1) {
        return null;
      }
      var relative = normalized.substring(separator + 1);
      requireSafeRelativePath(relative);
      return relative;
    } catch (InvalidPathException exception) {
      throw new IllegalStateException("Source archive contains an unsafe path", exception);
    }
  }

  static boolean isReadme(String path) {
    return path.equals("readme.md") || path.endsWith("/readme.md");
  }

  static @Nullable String moduleChild(String path, String directory) {
    var normalized = path.replace('\\', '/');
    var prefix = directory + "/";
    if (!normalized.startsWith(prefix)) {
      return null;
    }
    var remainder = normalized.substring(prefix.length());
    var separator = remainder.indexOf('/');
    return separator <= 0 ? null : remainder.substring(0, separator);
  }

  static boolean isModuleTerraformPath(String path) {
    if (!path.contains("/")) {
      return true;
    }
    var parts = path.replace('\\', '/').split("/", -1);
    return parts.length == 3
        && (parts[0].equals("modules") || parts[0].equals("examples"))
        && !parts[1].isBlank();
  }

  static @Nullable String moduleReadmePath(String path) {
    var normalized = path.replace('\\', '/');
    if (normalized.equalsIgnoreCase("README.md")) {
      return "README.md";
    }
    var parts = normalized.split("/", -1);
    if (isModuleChildReadme(parts)) {
      return parts[0] + "/" + parts[1] + "/README.md";
    }
    return null;
  }

  static @Nullable ClassifiedDocument classifyProviderDocument(String path) {
    var normalized = path.replace('\\', '/');
    var lower = normalized.toLowerCase(Locale.ROOT);
    if (isProviderIndex(lower)) {
      return new ClassifiedDocument("index", "index.md");
    }
    return PROVIDER_DOCUMENT_LOCATIONS.stream()
        .filter(location -> lower.contains(location.marker()))
        .findFirst()
        .map(location -> classifyProviderDocument(normalized, lower, location))
        .orElse(null);
  }

  private static void requireSafeRelativePath(String path) {
    var candidate = Path.of(path);
    if (path.isBlank()
        || path.startsWith("/")
        || path.matches("^[A-Za-z]:.*")
        || candidate.isAbsolute()
        || candidate.normalize().startsWith("..")) {
      throw new IllegalStateException("Source archive contains an unsafe path");
    }
  }

  private static boolean isModuleChildReadme(String[] parts) {
    return parts.length == 3
        && (parts[0].equals("modules") || parts[0].equals("examples"))
        && !parts[1].isBlank()
        && parts[2].equalsIgnoreCase("README.md");
  }

  private static boolean isProviderIndex(String lower) {
    return lower.endsWith("website/docs/index.html.markdown") || lower.endsWith("docs/index.md");
  }

  private static @Nullable ClassifiedDocument classifyProviderDocument(
      String normalized, String lower, DocumentLocation location) {
    var relative =
        normalized.substring(lower.indexOf(location.marker()) + location.marker().length());
    if (relative.contains("/") || !hasMarkdownExtension(lower)) {
      return null;
    }
    var name =
        relative
            .replaceFirst("(?i)\\.html\\.markdown$", "")
            .replaceFirst("(?i)\\.markdown$", "")
            .replaceFirst("(?i)\\.md$", "");
    return name.isBlank()
        ? null
        : new ClassifiedDocument(name, location.kind() + "/" + name + ".md");
  }

  private static boolean hasMarkdownExtension(String lower) {
    return lower.endsWith(".md") || lower.endsWith(".markdown");
  }

  record ClassifiedDocument(String name, String path) {}

  private record DocumentLocation(String kind, String marker) {}
}
