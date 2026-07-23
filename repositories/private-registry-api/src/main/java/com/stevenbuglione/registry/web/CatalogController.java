package com.stevenbuglione.registry.web;

import com.stevenbuglione.registry.catalog.CatalogQuery;
import com.stevenbuglione.registry.catalog.CatalogService;
import com.stevenbuglione.registry.model.PackageKind;
import com.stevenbuglione.registry.security.identity.RegistryIdentityService;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CatalogController {

  private final CatalogService catalog;
  private final RegistryIdentityService identities;

  public CatalogController(CatalogService catalog, RegistryIdentityService identities) {
    this.catalog = catalog;
    this.identities = identities;
  }

  @GetMapping("/registry/docs/modules/index.json")
  public Map<String, Object> listModules(Authentication authentication) {
    var context = identities.accessContext(authentication);
    var page = catalog.findPackages(context, query(null, PackageKind.MODULE, 100));
    return RegistryCompatibilityMapper.packageList(PackageKind.MODULE, page.items());
  }

  @GetMapping("/registry/docs/providers/index.json")
  public Map<String, Object> listProviders(Authentication authentication) {
    var context = identities.accessContext(authentication);
    var page = catalog.findPackages(context, query(null, PackageKind.PROVIDER, 100));
    return RegistryCompatibilityMapper.packageList(PackageKind.PROVIDER, page.items());
  }

  @GetMapping("/top/providers")
  public List<Map<String, Object>> topProviders(Authentication authentication) {
    var context = identities.accessContext(authentication);
    return RegistryCompatibilityMapper.topProviders(
        catalog.findPackages(context, query(null, PackageKind.PROVIDER, 100)).items());
  }

  @GetMapping("/registry/docs/search")
  public List<Map<String, Object>> search(
      Authentication authentication,
      @RequestParam(defaultValue = "") String q,
      @RequestParam(required = false) @Nullable String type,
      @RequestParam(defaultValue = "25") int limit) {
    var context = identities.accessContext(authentication);
    var page = catalog.findPackages(context, query(q, PackageKind.from(type), limit));
    return RegistryCompatibilityMapper.searchResults(page.items());
  }

  @GetMapping("/registry/docs/modules/{*path}")
  public ResponseEntity<?> moduleRoute(Authentication authentication, @PathVariable String path) {
    var segments = splitPath(path);
    if (segments.size() < 3) {
      throw new IllegalArgumentException("A module route requires namespace, name, and target");
    }
    var id = "module/" + String.join("/", segments.subList(0, 3));
    return packageRoute(authentication, id, segments.subList(3, segments.size()), "README.md");
  }

  @GetMapping("/registry/docs/providers/{*path}")
  public ResponseEntity<?> providerRoute(Authentication authentication, @PathVariable String path) {
    var segments = splitPath(path);
    if (segments.size() < 2) {
      throw new IllegalArgumentException("A provider route requires namespace and name");
    }
    var id = "provider/" + String.join("/", segments.subList(0, 2));
    return packageRoute(authentication, id, segments.subList(2, segments.size()), "index.md");
  }

  @GetMapping("/api/v1/enterprise/packages/{*path}")
  public ResponseEntity<?> enterprisePackage(
      Authentication authentication, @PathVariable String path) {
    var context = identities.accessContext(authentication);
    var normalizedPath = trimSlashes(path);
    var suffix = "";
    for (var candidate :
        List.of(
            "/governance", "/approvals", "/security", "/owners", "/usage", "/audit", "/jfrog")) {
      if (normalizedPath.endsWith(candidate)) {
        suffix = candidate;
        normalizedPath = normalizedPath.substring(0, normalizedPath.length() - candidate.length());
        break;
      }
    }

    var governance = catalog.getGovernance(context, normalizedPath);
    return switch (suffix) {
      case "", "/governance" -> ResponseEntity.ok(governance);
      case "/approvals" ->
          ResponseEntity.ok(
              Map.of("package_id", normalizedPath, "approvals", governance.approvals()));
      case "/security" ->
          ResponseEntity.ok(
              Map.of(
                  "package_id",
                  normalizedPath,
                  "summary",
                  "no known findings",
                  "findings",
                  List.of()));
      case "/usage" ->
          ResponseEntity.ok(
              nullableMap("package_id", normalizedPath, "authorized_aggregate", null));
      case "/audit" -> ResponseEntity.ok(Map.of("package_id", normalizedPath, "events", List.of()));
      case "/owners" ->
          ResponseEntity.ok(
              nullableMap(
                  "package_id", normalizedPath,
                  "owners", governance.owners(),
                  "support_url", governance.supportUrl()));
      case "/jfrog" ->
          ResponseEntity.ok(
              nullableMap(
                  "package_id", normalizedPath, "console_url", governance.jfrogConsoleUrl()));
      default -> throw new IllegalArgumentException("Unsupported enterprise package resource");
    };
  }

  private ResponseEntity<?> packageRoute(
      Authentication authentication, String id, List<String> rest, String defaultDocument) {
    var context = identities.accessContext(authentication);
    var item = catalog.getPackage(context, id);
    if (rest.isEmpty() || (rest.size() == 1 && "index.json".equals(rest.getFirst()))) {
      return ResponseEntity.ok(RegistryCompatibilityMapper.packageSummary(item));
    }
    if ("index.json".equals(rest.getLast())) {
      return ResponseEntity.ok(RegistryCompatibilityMapper.packageVersion(item, rest.getFirst()));
    }

    var documentPath = defaultDocument;
    if (rest.getLast().endsWith(".md")) {
      var versionSegment =
          rest.size() > 1
              && (item.latestVersion().equals(rest.getFirst()) || "latest".equals(rest.getFirst()));
      var start = versionSegment ? 1 : 0;
      documentPath = String.join("/", rest.subList(start, rest.size()));
    }
    var document = catalog.readDocument(context, id, documentPath);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(document.contentType()))
        .body(document.content());
  }

  private static CatalogQuery query(@Nullable String q, @Nullable PackageKind kind, int limit) {
    return new CatalogQuery(
        new CatalogQuery.Criteria(q, kind, null, null, null, "updated", null, limit, null));
  }

  static List<String> splitPath(@Nullable String path) {
    if (path == null || path.isBlank()) {
      return List.of();
    }
    return Arrays.stream(trimSlashes(path).split("/", -1))
        .filter(segment -> !segment.isBlank())
        .toList();
  }

  static String trimSlashes(@Nullable String value) {
    return value == null
        ? ""
        : StringUtils.trimTrailingCharacter(StringUtils.trimLeadingCharacter(value, '/'), '/');
  }

  private static Map<String, @Nullable Object> nullableMap(@Nullable Object... keyValues) {
    var result = new LinkedHashMap<String, @Nullable Object>();
    for (var index = 0; index < keyValues.length; index += 2) {
      result.put((String) keyValues[index], keyValues[index + 1]);
    }
    return result;
  }
}
