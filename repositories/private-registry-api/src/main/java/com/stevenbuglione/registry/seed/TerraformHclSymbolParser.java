package com.stevenbuglione.registry.seed;

import com.stevenbuglione.registry.seed.TerraformMetadataExtractor.ExtractedDocument;
import com.stevenbuglione.registry.seed.TerraformMetadataExtractor.ExtractedSymbol;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Converts Terraform documents and HCL source into stable Registry symbols. */
final class TerraformHclSymbolParser {

  private static final Pattern BLOCK =
      Pattern.compile(
          "(?m)(?<![A-Za-z0-9_-])(variable|output|resource|data|module)\\s+\"([^\"]+)\"(?:\\s+\"([^\"]+)\")?\\s*\\{");
  private static final Pattern REQUIRED_PROVIDERS =
      Pattern.compile("(?m)(?<![A-Za-z0-9_-])required_providers\\s*(?:=\\s*)?\\{");
  private static final Pattern PROVIDER_ENTRY =
      Pattern.compile("(?m)^\\s*([A-Za-z0-9_-]+)\\s*=\\s*\\{");

  private TerraformHclSymbolParser() {}

  static List<ExtractedSymbol> providerSymbols(Iterable<ExtractedDocument> documents) {
    var symbols = new ArrayList<ExtractedSymbol>();
    for (var document : documents) {
      if (!document.path().equals("README.md") && !document.path().equals("index.md")) {
        symbols.add(providerSymbol(document));
      }
    }
    return symbols.stream()
        .sorted(Comparator.comparing(ExtractedSymbol::kind).thenComparing(ExtractedSymbol::name))
        .toList();
  }

  static List<ExtractedSymbol> moduleSymbols(
      Map<String, String> terraform, Iterable<String> examples, Iterable<String> submodules) {
    var symbols = new LinkedHashMap<String, ExtractedSymbol>();
    terraform.forEach((path, content) -> parseTerraformFile(path, content, symbols));
    addModuleChildren(symbols, submodules, "submodule", "modules");
    addModuleChildren(symbols, examples, "example", "examples");
    return symbols.values().stream()
        .sorted(
            Comparator.comparing(ExtractedSymbol::kind)
                .thenComparing(ExtractedSymbol::name)
                .thenComparing(ExtractedSymbol::path))
        .toList();
  }

  private static ExtractedSymbol providerSymbol(ExtractedDocument document) {
    var slash = document.path().indexOf('/');
    var directory = slash < 0 ? "guides" : document.path().substring(0, slash);
    var kind =
        switch (directory) {
          case "resources" -> "resource";
          case "data-sources" -> "data-source";
          case "functions" -> "function";
          default -> "guide";
        };
    var filename = slash < 0 ? document.path() : document.path().substring(slash + 1);
    var name = filename.endsWith(".md") ? filename.substring(0, filename.length() - 3) : filename;
    return new ExtractedSymbol(
        kind, name, document.description(), document.path(), kind, null, false, false);
  }

  private static void parseTerraformFile(
      String path, String content, Map<String, ExtractedSymbol> symbols) {
    var normalized = TerraformHclScanner.stripComments(content);
    var matcher = BLOCK.matcher(normalized);
    while (matcher.find()) {
      var closing = TerraformHclScanner.findClosingBrace(normalized, matcher.end() - 1);
      if (closing < 0) {
        throw new IllegalStateException("Unclosed Terraform block in " + path);
      }
      var symbol =
          moduleSymbol(
              path,
              matcher.group(1),
              matcher.group(2),
              matcher.group(3),
              normalized.substring(matcher.end(), closing));
      symbols.putIfAbsent(symbolIdentity(symbol), symbol);
    }
    requiredProviderSymbols(path, normalized)
        .forEach(symbol -> symbols.putIfAbsent(symbolIdentity(symbol), symbol));
  }

  private static void addModuleChildren(
      Map<String, ExtractedSymbol> symbols,
      Iterable<String> children,
      String kind,
      String directory) {
    children.forEach(
        child ->
            symbols.putIfAbsent(
                kind + ":" + child,
                new ExtractedSymbol(
                    kind, child, null, directory + "/" + child, kind, null, false, false)));
  }

  private static String symbolIdentity(ExtractedSymbol symbol) {
    return symbol.kind() + ":" + symbol.name() + ":" + symbolScope(symbol.path());
  }

  private static String symbolScope(String path) {
    var parts = path.replace('\\', '/').split("/", -1);
    return parts.length >= 2 && (parts[0].equals("modules") || parts[0].equals("examples"))
        ? parts[0] + "/" + parts[1]
        : "";
  }

  private static ExtractedSymbol moduleSymbol(
      String path, String blockKind, String firstLabel, String secondLabel, String body) {
    var sensitive = Boolean.parseBoolean(TerraformHclScanner.attribute(body, "sensitive"));
    return switch (blockKind) {
      case "variable" -> variable(path, firstLabel, body, sensitive);
      case "output" ->
          new ExtractedSymbol(
              "output",
              firstLabel,
              TerraformHclScanner.unquote(TerraformHclScanner.attribute(body, "description")),
              path,
              null,
              null,
              false,
              sensitive);
      case "resource", "data" ->
          new ExtractedSymbol(
              blockKind.equals("data") ? "data-source" : "resource",
              firstLabel + "." + secondLabel,
              null,
              path,
              firstLabel,
              null,
              false,
              false);
      case "module" ->
          new ExtractedSymbol(
              "dependency",
              firstLabel,
              TerraformHclScanner.unquote(TerraformHclScanner.attribute(body, "source")),
              path,
              "module",
              null,
              true,
              false);
      default -> throw new IllegalStateException("Unexpected Terraform block kind " + blockKind);
    };
  }

  private static ExtractedSymbol variable(
      String path, String name, String body, boolean sensitive) {
    var defaultValue = TerraformHclScanner.attribute(body, "default");
    return new ExtractedSymbol(
        "input",
        name,
        TerraformHclScanner.unquote(TerraformHclScanner.attribute(body, "description")),
        path,
        TerraformHclScanner.attribute(body, "type"),
        defaultValue,
        defaultValue == null,
        sensitive);
  }

  private static List<ExtractedSymbol> requiredProviderSymbols(String path, String content) {
    var symbols = new ArrayList<ExtractedSymbol>();
    var required = REQUIRED_PROVIDERS.matcher(content);
    while (required.find()) {
      var end = TerraformHclScanner.findClosingBrace(content, required.end() - 1);
      if (end < 0) {
        throw new IllegalStateException("Unclosed required_providers block in " + path);
      }
      addRequiredProviders(path, content.substring(required.end(), end), symbols);
    }
    return List.copyOf(symbols);
  }

  private static void addRequiredProviders(
      String path, String body, List<ExtractedSymbol> symbols) {
    var provider = PROVIDER_ENTRY.matcher(body);
    while (provider.find()) {
      var providerEnd = TerraformHclScanner.findClosingBrace(body, provider.end() - 1);
      if (providerEnd >= 0) {
        var providerBody = body.substring(provider.end(), providerEnd);
        symbols.add(
            new ExtractedSymbol(
                "dependency",
                provider.group(1),
                TerraformHclScanner.unquote(TerraformHclScanner.attribute(providerBody, "source")),
                path,
                "provider",
                TerraformHclScanner.unquote(TerraformHclScanner.attribute(providerBody, "version")),
                true,
                false));
      }
    }
  }
}
