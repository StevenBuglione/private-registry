package com.stevenbuglione.registry.web;

import com.stevenbuglione.registry.catalog.CatalogService;
import com.stevenbuglione.registry.model.CatalogPackage;
import com.stevenbuglione.registry.model.PackageKind;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CatalogController {

    private final CatalogService catalog;

    public CatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/registry/docs/modules/index.json")
    public Map<String, Object> listModules() {
        return packagePage(catalog.listPackages(PackageKind.MODULE));
    }

    @GetMapping("/registry/docs/providers/index.json")
    public Map<String, Object> listProviders() {
        return packagePage(catalog.listPackages(PackageKind.PROVIDER));
    }

    @GetMapping("/top/providers")
    public List<CatalogPackage> topProviders() {
        return catalog.listPackages(PackageKind.PROVIDER);
    }

    @GetMapping("/registry/docs/search")
    public Map<String, Object> search(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "25") int limit) {
        var results = catalog.search(q, PackageKind.from(type), limit);
        return Map.of("results", results, "total", results.size());
    }

    @GetMapping("/registry/docs/modules/{*path}")
    public ResponseEntity<?> moduleRoute(@PathVariable String path) {
        var segments = splitPath(path);
        if (segments.size() < 3) {
            throw new IllegalArgumentException("A module route requires namespace, name, and target");
        }
        var id = "module/" + String.join("/", segments.subList(0, 3));
        return packageRoute(id, segments.subList(3, segments.size()), "README.md");
    }

    @GetMapping("/registry/docs/providers/{*path}")
    public ResponseEntity<?> providerRoute(@PathVariable String path) {
        var segments = splitPath(path);
        if (segments.size() < 2) {
            throw new IllegalArgumentException("A provider route requires namespace and name");
        }
        var id = "provider/" + String.join("/", segments.subList(0, 2));
        return packageRoute(id, segments.subList(2, segments.size()), "index.md");
    }

    @GetMapping("/api/v1/enterprise/packages/{*path}")
    public ResponseEntity<?> enterprisePackage(@PathVariable String path) {
        var normalizedPath = trimSlashes(path);
        var suffix = "";
        for (var candidate : List.of("/governance", "/approvals", "/security", "/owners", "/usage", "/audit", "/jfrog")) {
            if (normalizedPath.endsWith(candidate)) {
                suffix = candidate;
                normalizedPath = normalizedPath.substring(0, normalizedPath.length() - candidate.length());
                break;
            }
        }

        var governance = catalog.getGovernance(normalizedPath);
        return switch (suffix) {
            case "", "/governance" -> ResponseEntity.ok(governance);
            case "/approvals" -> ResponseEntity.ok(Map.of(
                    "package_id", normalizedPath, "approvals", governance.approvals()));
            case "/security" -> ResponseEntity.ok(Map.of(
                    "package_id", normalizedPath, "summary", "no known findings", "findings", List.of()));
            case "/usage" -> ResponseEntity.ok(nullableMap(
                    "package_id", normalizedPath, "authorized_aggregate", null));
            case "/audit" -> ResponseEntity.ok(Map.of(
                    "package_id", normalizedPath, "events", List.of()));
            case "/owners" -> ResponseEntity.ok(nullableMap(
                    "package_id", normalizedPath,
                    "owners", governance.owners(),
                    "support_url", governance.supportUrl()));
            case "/jfrog" -> ResponseEntity.ok(nullableMap(
                    "package_id", normalizedPath, "console_url", governance.jfrogConsoleUrl()));
            default -> throw new IllegalArgumentException("Unsupported enterprise package resource");
        };
    }

    private ResponseEntity<?> packageRoute(String id, List<String> rest, String defaultDocument) {
        var item = catalog.getPackage(id);
        if (rest.isEmpty() || (rest.size() == 1 && "index.json".equals(rest.getFirst()))) {
            return ResponseEntity.ok(item);
        }
        if ("index.json".equals(rest.getLast())) {
            return ResponseEntity.ok(item);
        }

        var documentPath = defaultDocument;
        if (rest.getLast().endsWith(".md")) {
            var start = rest.size() > 1 && item.latestVersion().equals(rest.getFirst()) ? 1 : 0;
            documentPath = String.join("/", rest.subList(start, rest.size()));
        }
        var document = catalog.readDocument(id, documentPath);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(document.contentType()))
                .body(document.content());
    }

    private static Map<String, Object> packagePage(List<CatalogPackage> items) {
        return Map.of("items", items, "total", items.size());
    }

    private static List<String> splitPath(String path) {
        if (path == null || path.isBlank()) {
            return List.of();
        }
        return Arrays.stream(trimSlashes(path).split("/"))
                .filter(segment -> !segment.isBlank())
                .toList();
    }

    private static String trimSlashes(String value) {
        return value == null ? "" : value.replaceAll("^/+|/+$", "");
    }

    private static Map<String, Object> nullableMap(Object... keyValues) {
        var result = new LinkedHashMap<String, Object>();
        for (var index = 0; index < keyValues.length; index += 2) {
            result.put((String) keyValues[index], keyValues[index + 1]);
        }
        return result;
    }
}
