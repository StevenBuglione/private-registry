package com.stevenbuglione.registry.web;

import com.stevenbuglione.registry.catalog.CatalogChangeNotifier;
import com.stevenbuglione.registry.catalog.CatalogPage;
import com.stevenbuglione.registry.catalog.CatalogQuery;
import com.stevenbuglione.registry.catalog.CatalogService;
import com.stevenbuglione.registry.model.CatalogPackage;
import com.stevenbuglione.registry.model.PackageKind;
import com.stevenbuglione.registry.security.identity.RegistryIdentityService;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/catalog")
public class CatalogApiController {

  private final CatalogService catalog;
  private final RegistryIdentityService identities;
  private final CatalogChangeNotifier notifier;

  public CatalogApiController(
      CatalogService catalog, RegistryIdentityService identities, CatalogChangeNotifier notifier) {
    this.catalog = catalog;
    this.identities = identities;
    this.notifier = notifier;
  }

  @GetMapping("/packages")
  public CatalogPage<CatalogPackage> packages(
      Authentication authentication,
      @RequestParam(required = false) @Nullable String q,
      @RequestParam(required = false) @Nullable String kind,
      @RequestParam(required = false) @Nullable String provider,
      @RequestParam(required = false) @Nullable String tier,
      @RequestParam(required = false) @Nullable String category,
      @RequestParam(name = "apm_id", required = false) @Nullable String apmId,
      @RequestParam(required = false) @Nullable String lifecycle,
      @RequestParam(required = false) @Nullable String approval,
      @RequestParam(required = false) @Nullable String risk,
      @RequestParam(defaultValue = "updated") String sort,
      @RequestParam(required = false) @Nullable String cursor,
      @RequestParam(defaultValue = "25") int limit) {
    var context = identities.accessContext(authentication);
    return catalog.findPackages(
        context,
        new CatalogQuery(
            new CatalogQuery.Criteria(
                q,
                PackageKind.from(kind),
                provider,
                tier,
                category,
                apmId,
                lifecycle,
                approval,
                risk,
                sort,
                cursor,
                limit)));
  }

  @GetMapping("/counts")
  public Map<String, Long> counts(Authentication authentication) {
    var context = identities.accessContext(authentication);
    return Map.of(
        "modules", catalog.countPackages(context, PackageKind.MODULE),
        "providers", catalog.countPackages(context, PackageKind.PROVIDER));
  }

  @GetMapping("/packages/{*path}")
  public ResponseEntity<?> packageResource(
      Authentication authentication,
      @PathVariable String path,
      @RequestParam(name = "apm_id", required = false) @Nullable String apmId,
      @RequestParam(name = "path", required = false) @Nullable String documentPath) {
    var context = identities.accessContext(authentication).scopedToApm(apmId);
    var route = PackageRoute.parse(path);
    return switch (route.resource()) {
      case "summary" ->
          ResponseEntity.ok(catalog.getPackage(context, route.packageId(), route.version()));
      case "versions" ->
          ResponseEntity.ok(
              catalog.getPackage(context, route.packageId(), route.version()).versions());
      case "governance" ->
          ResponseEntity.ok(catalog.getGovernance(context, route.packageId(), route.version()));
      case "documentation" -> {
        var requestedPath = safeDocumentPath(documentPath, route.defaultDocument());
        var document =
            catalog.readDocument(context, route.packageId(), route.version(), requestedPath);
        yield ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(document.contentType()))
            .body(document.content());
      }
      default -> throw new IllegalArgumentException("Unsupported catalog package resource");
    };
  }

  static String safeDocumentPath(@Nullable String requestedPath, String defaultDocument) {
    if (requestedPath == null || requestedPath.isBlank()) {
      return defaultDocument;
    }
    var normalized = requestedPath.trim();
    if (normalized.startsWith("/")
        || normalized.contains("\\")
        || normalized.chars().anyMatch(Character::isISOControl)) {
      throw new IllegalArgumentException("Documentation path must be a safe relative path");
    }
    var segments = normalized.split("/", -1);
    for (var segment : segments) {
      if (segment.isBlank() || ".".equals(segment) || "..".equals(segment)) {
        throw new IllegalArgumentException("Documentation path must be a safe relative path");
      }
    }
    return normalized;
  }

  @GetMapping(path = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter events(
      Authentication authentication,
      @RequestParam(name = "apm_id", required = false) @Nullable String apmId) {
    return notifier.subscribe(identities.accessContext(authentication).scopedToApm(apmId));
  }

  private static final class PackageRoute {

    private static final List<String> RESOURCES =
        List.of("versions", "documentation", "governance");
    private final String packageId;
    private final @Nullable String version;
    private final String resource;
    private final String defaultDocument;

    private PackageRoute(
        String packageId, @Nullable String version, String resource, String defaultDocument) {
      this.packageId = packageId;
      this.version = version;
      this.resource = resource;
      this.defaultDocument = defaultDocument;
    }

    String packageId() {
      return packageId;
    }

    @Nullable String version() {
      return version;
    }

    String resource() {
      return resource;
    }

    String defaultDocument() {
      return defaultDocument;
    }

    static PackageRoute parse(String rawPath) {
      var segments = CatalogController.splitPath(rawPath);
      if (segments.isEmpty()) {
        throw new IllegalArgumentException("A package route is required");
      }
      var kind = PackageKind.from(segments.getFirst());
      if (kind == null) {
        throw new IllegalArgumentException("A package kind is required");
      }
      var baseSize = kind == PackageKind.MODULE ? 4 : 3;
      if (segments.size() < baseSize) {
        throw new IllegalArgumentException(
            kind == PackageKind.MODULE
                ? "A module route requires namespace, name, and target"
                : "A provider route requires namespace and name");
      }
      if (segments.size() > baseSize + 2) {
        throw new IllegalArgumentException("The package route has too many segments");
      }
      String version = null;
      var resource = "summary";
      var remaining = segments.subList(baseSize, segments.size());
      if (remaining.size() == 1) {
        if (RESOURCES.contains(remaining.getFirst())) {
          resource = remaining.getFirst();
        } else {
          version = remaining.getFirst();
        }
      } else if (remaining.size() == 2) {
        version = remaining.getFirst();
        resource = remaining.getLast();
        if (!RESOURCES.contains(resource)) {
          throw new IllegalArgumentException("Unsupported catalog package resource");
        }
      }
      var packageId = String.join("/", segments.subList(0, baseSize));
      return new PackageRoute(
          packageId, version, resource, kind == PackageKind.MODULE ? "README.md" : "index.md");
    }
  }
}
