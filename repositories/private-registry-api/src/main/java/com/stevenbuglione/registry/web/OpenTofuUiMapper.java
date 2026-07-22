package com.stevenbuglione.registry.web;

import com.stevenbuglione.registry.catalog.NotFoundException;
import com.stevenbuglione.registry.model.CatalogPackage;
import com.stevenbuglione.registry.model.PackageKind;
import com.stevenbuglione.registry.model.PackageVersion;
import com.stevenbuglione.registry.model.SearchResult;
import com.stevenbuglione.registry.model.Symbol;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class OpenTofuUiMapper {

    private OpenTofuUiMapper() {}

    static Map<String, Object> packageList(PackageKind kind, List<CatalogPackage> items) {
        return Map.of(
                kind == PackageKind.MODULE ? "modules" : "providers",
                items.stream().map(OpenTofuUiMapper::packageSummary).toList());
    }

    static Map<String, Object> packageSummary(CatalogPackage item) {
        var result = new LinkedHashMap<String, Object>();
        result.put("addr", address(item));
        result.put("description", item.description());
        result.put("fork_count", 0);
        result.put("is_blocked", false);
        result.put("popularity", 0);
        result.put("versions", item.versions().stream().map(OpenTofuUiMapper::versionDescriptor).toList());
        return result;
    }

    static Map<String, Object> packageVersion(CatalogPackage item, String requestedVersion) {
        var version = findVersion(item, requestedVersion);
        return item.kind() == PackageKind.MODULE
                ? moduleVersion(item, version)
                : providerVersion(item, version);
    }

    static List<Map<String, Object>> searchResults(List<SearchResult> results) {
        return results.stream().map(OpenTofuUiMapper::searchResult).toList();
    }

    static List<Map<String, Object>> topProviders(List<CatalogPackage> providers) {
        return providers.stream()
                .map(item -> Map.<String, Object>of(
                        "addr", item.namespace() + "/" + item.name(),
                        "version", item.latestVersion(),
                        "popularity", 0))
                .toList();
    }

    private static Map<String, Object> address(CatalogPackage item) {
        var result = new LinkedHashMap<String, Object>();
        result.put("display", item.kind() == PackageKind.MODULE
                ? item.namespace() + "/" + item.name() + "/" + item.target()
                : item.namespace() + "/" + item.name());
        result.put("name", item.name());
        result.put("namespace", item.namespace());
        if (item.kind() == PackageKind.MODULE) {
            result.put("target", item.target());
        }
        return result;
    }

    private static Map<String, Object> versionDescriptor(PackageVersion version) {
        return Map.of("id", version.version(), "published", version.publishedAt());
    }

    private static Map<String, Object> moduleVersion(CatalogPackage item, PackageVersion version) {
        var result = new LinkedHashMap<String, Object>();
        result.put("dependencies", List.of());
        result.put("examples", Map.of());
        result.put("id", version.version());
        result.put("incompatible_license", false);
        result.put("licenses", List.of());
        result.put("link", item.sourceAddress());
        result.put("outputs", outputSymbols(item.symbols()));
        result.put("providers", List.of());
        result.put("published", version.publishedAt());
        result.put("readme", true);
        result.put("resources", resourceSymbols(item.symbols()));
        result.put("schema_error", "");
        result.put("submodules", Map.of());
        result.put("variables", inputSymbols(item.symbols()));
        result.put("vcs_repository", item.sourceAddress());
        return result;
    }

    private static Map<String, Object> providerVersion(CatalogPackage item, PackageVersion version) {
        var result = new LinkedHashMap<String, Object>();
        result.put("cdktf_docs", Map.of());
        result.put("docs", providerDocs(item));
        result.put("id", version.version());
        result.put("incompatible_license", false);
        result.put("license", List.of());
        result.put("link", item.sourceAddress());
        result.put("published", version.publishedAt());
        return result;
    }

    private static Map<String, Object> providerDocs(CatalogPackage item) {
        var result = new LinkedHashMap<String, Object>();
        result.put("index", Map.of("title", item.title(), "name", "index", "description", item.description()));
        result.put("resources", providerDocItems(item.symbols(), "resource"));
        result.put("datasources", providerDocItems(item.symbols(), "datasource"));
        result.put("functions", providerDocItems(item.symbols(), "function"));
        result.put("guides", List.of());
        return result;
    }

    private static List<Map<String, Object>> providerDocItems(List<Symbol> symbols, String kind) {
        return symbols.stream()
                .filter(symbol -> kind.equals(symbol.kind()))
                .map(symbol -> Map.<String, Object>of(
                        "name", symbol.name(),
                        "title", symbol.name(),
                        "description", symbol.description() == null ? "" : symbol.description()))
                .toList();
    }

    private static Map<String, Object> inputSymbols(List<Symbol> symbols) {
        var result = new LinkedHashMap<String, Object>();
        symbols.stream().filter(symbol -> "input".equals(symbol.kind())).forEach(symbol -> {
            var input = new LinkedHashMap<String, Object>();
            input.put("default", null);
            input.put("description", symbol.description() == null ? "" : symbol.description());
            input.put("required", true);
            input.put("sensitive", false);
            input.put("type", "string");
            result.put(symbol.name(), input);
        });
        return result;
    }

    private static Map<String, Object> outputSymbols(List<Symbol> symbols) {
        var result = new LinkedHashMap<String, Object>();
        symbols.stream().filter(symbol -> "output".equals(symbol.kind())).forEach(symbol -> result.put(
                symbol.name(),
                Map.of(
                        "description", symbol.description() == null ? "" : symbol.description(),
                        "sensitive", false)));
        return result;
    }

    private static List<Map<String, Object>> resourceSymbols(List<Symbol> symbols) {
        return symbols.stream()
                .filter(symbol -> "resource".equals(symbol.kind()))
                .map(symbol -> Map.<String, Object>of(
                        "address", symbol.name(), "name", symbol.name(), "type", symbol.name()))
                .toList();
    }

    private static Map<String, Object> searchResult(SearchResult result) {
        var item = result.packageDetails();
        var linkVariables = new LinkedHashMap<String, String>();
        linkVariables.put("namespace", item.namespace());
        linkVariables.put("name", item.name());
        linkVariables.put("version", item.latestVersion());
        linkVariables.put("id", item.name());
        if (item.kind() == PackageKind.MODULE) {
            linkVariables.put("target_system", item.target());
        }

        var mapped = new LinkedHashMap<String, Object>();
        mapped.put("addr", item.kind() == PackageKind.MODULE
                ? item.namespace() + "/" + item.name() + "/" + item.target()
                : item.namespace() + "/" + item.name());
        mapped.put("description", result.description());
        mapped.put("id", result.id());
        mapped.put("last_updated", item.updatedAt());
        mapped.put("link_variables", linkVariables);
        mapped.put("rank", 1);
        mapped.put("term_match_count", "1");
        mapped.put("title", item.title());
        mapped.put("type", result.type());
        mapped.put("version", item.latestVersion());
        return mapped;
    }

    private static PackageVersion findVersion(CatalogPackage item, String requestedVersion) {
        var version = "latest".equals(requestedVersion) ? item.latestVersion() : requestedVersion;
        return item.versions().stream()
                .filter(candidate -> candidate.version().equals(version))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Package version not found: " + version));
    }
}
