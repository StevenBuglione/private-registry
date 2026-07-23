package com.stevenbuglione.registry.seed;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Coordinates archive extraction and framework-free Terraform metadata parsing. */
final class TerraformMetadataExtractor {

  private TerraformMetadataExtractor() {}

  static Extraction extract(Path sourceArchive, boolean provider) {
    var contents = TerraformSourceArchiveReader.read(sourceArchive, provider);
    var symbols =
        provider
            ? TerraformHclSymbolParser.providerSymbols(contents.documents().values())
            : TerraformHclSymbolParser.moduleSymbols(
                contents.terraform(), contents.examples(), contents.submodules());
    return new Extraction(
        contents.documents().values().stream()
            .sorted(Comparator.comparing(ExtractedDocument::path))
            .toList(),
        symbols,
        contents.archiveDigest());
  }

  record Extraction(
      List<ExtractedDocument> documents, List<ExtractedSymbol> symbols, String archiveDigest) {
    Extraction {
      documents = List.copyOf(documents);
      symbols = List.copyOf(symbols);
    }
  }

  static final class ExtractedDocument {

    private final String path;
    private final String title;
    private final String contentType;
    private final @Nullable String description;
    private final byte[] content;

    ExtractedDocument(
        String path,
        String title,
        String contentType,
        @Nullable String description,
        byte[] content) {
      this.path = path;
      this.title = title;
      this.contentType = contentType;
      this.description = description;
      this.content = content.clone();
    }

    String path() {
      return path;
    }

    String title() {
      return title;
    }

    String contentType() {
      return contentType;
    }

    @Nullable String description() {
      return description;
    }

    byte[] content() {
      return content.clone();
    }
  }

  record ExtractedSymbol(
      String kind,
      String name,
      @Nullable String description,
      String path,
      @Nullable String type,
      @Nullable String defaultValue,
      boolean required,
      boolean sensitive) {}
}
