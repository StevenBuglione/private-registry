package com.stevenbuglione.registry.seed;

import com.stevenbuglione.registry.seed.TerraformMetadataExtractor.ExtractedDocument;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.zip.ZipInputStream;
import org.jspecify.annotations.Nullable;

/** Reads bounded source archives and normalizes Terraform Registry documentation. */
final class TerraformSourceArchiveReader {

  private static final int MAX_ARCHIVE_ENTRIES = 50_000;

  private TerraformSourceArchiveReader() {}

  static ArchiveContents read(Path sourceArchive, boolean provider) {
    var builder = readEntries(sourceArchive, provider);
    finalizeRootDocuments(builder, provider);
    return new ArchiveContents(
        builder.terraform,
        builder.documents,
        List.copyOf(builder.examples),
        List.copyOf(builder.submodules),
        TerraformArchiveIO.digest(sourceArchive));
  }

  private static ArchiveBuilder readEntries(Path sourceArchive, boolean provider) {
    var builder = new ArchiveBuilder();
    var entries = 0;
    try (var input = Files.newInputStream(sourceArchive);
        var archive = new ZipInputStream(input)) {
      for (var entry = archive.getNextEntry(); entry != null; entry = archive.getNextEntry()) {
        entries++;
        if (entries > MAX_ARCHIVE_ENTRIES) {
          throw new IllegalStateException("Source archive contains too many entries");
        }
        if (entry.isDirectory()) {
          continue;
        }
        var path = TerraformArchivePath.relativePath(entry.getName());
        if (path != null) {
          processEntry(archive, path, provider, builder);
        }
      }
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Unable to extract Terraform source metadata from " + sourceArchive, exception);
    }
    return builder;
  }

  private static void processEntry(
      ZipInputStream archive, String path, boolean provider, ArchiveBuilder builder)
      throws IOException {
    var lower = path.toLowerCase(Locale.ROOT);
    if (!provider) {
      recordModuleChild(path, builder);
    }
    if (lower.endsWith(".tf") && !provider && TerraformArchivePath.isModuleTerraformPath(path)) {
      builder.terraform.put(
          path, TerraformMarkdown.decode(TerraformArchiveIO.readTextEntry(archive, path)));
    } else if (TerraformArchivePath.isReadme(lower)) {
      recordReadme(archive, path, provider, builder);
    } else if (provider) {
      recordProviderDocument(archive, path, lower, builder);
    }
  }

  private static void recordModuleChild(String path, ArchiveBuilder builder) {
    var example = TerraformArchivePath.moduleChild(path, "examples");
    if (example != null) {
      builder.examples.add(example);
    }
    var submodule = TerraformArchivePath.moduleChild(path, "modules");
    if (submodule != null) {
      builder.submodules.add(submodule);
    }
  }

  private static void recordReadme(
      ZipInputStream archive, String path, boolean provider, ArchiveBuilder builder)
      throws IOException {
    var documentPath = provider ? "README.md" : TerraformArchivePath.moduleReadmePath(path);
    if (documentPath == null) {
      return;
    }
    var content = TerraformArchiveIO.readTextEntry(archive, path);
    var readme =
        new ExtractedDocument(
            documentPath,
            TerraformMarkdown.title(content, "README"),
            "text/markdown",
            TerraformMarkdown.description(content),
            TerraformMarkdown.stripLeadingFrontmatter(content));
    if (!provider || path.equalsIgnoreCase("README.md")) {
      builder.documents.put(documentPath, readme);
    } else if (builder.fallbackReadme == null) {
      builder.fallbackReadme = readme;
    }
  }

  private static void recordProviderDocument(
      ZipInputStream archive, String path, String lower, ArchiveBuilder builder)
      throws IOException {
    var classified = TerraformArchivePath.classifyProviderDocument(path);
    if (classified == null) {
      return;
    }
    var content = TerraformArchiveIO.readTextEntry(archive, path);
    var document =
        new ExtractedDocument(
            classified.path(),
            TerraformMarkdown.title(content, classified.name()),
            "text/markdown",
            TerraformMarkdown.description(content),
            TerraformMarkdown.stripLeadingFrontmatter(content));
    if (classified.path().equals("index.md")
        && lower.endsWith("website/docs/index.html.markdown")) {
      builder.documents.put("index.md", document);
    } else {
      builder.documents.putIfAbsent(classified.path(), document);
    }
  }

  private static void finalizeRootDocuments(ArchiveBuilder builder, boolean provider) {
    if (!builder.documents.containsKey("README.md") && builder.fallbackReadme != null) {
      builder.documents.put("README.md", builder.fallbackReadme);
    }
    if (!builder.documents.containsKey("README.md")) {
      throw new IllegalStateException("Pinned upstream source archive does not contain a README");
    }
    if (provider && !builder.documents.containsKey("index.md")) {
      var readme = Objects.requireNonNull(builder.documents.get("README.md"), "README.md document");
      builder.documents.put(
          "index.md",
          new ExtractedDocument(
              "index.md",
              readme.title(),
              readme.contentType(),
              readme.description(),
              readme.content()));
    }
  }

  static @Nullable String relativePath(String archivePath) {
    return TerraformArchivePath.relativePath(archivePath);
  }

  record ArchiveContents(
      Map<String, String> terraform,
      Map<String, ExtractedDocument> documents,
      List<String> examples,
      List<String> submodules,
      String archiveDigest) {
    ArchiveContents {
      terraform = Collections.unmodifiableMap(new LinkedHashMap<>(terraform));
      documents = Collections.unmodifiableMap(new LinkedHashMap<>(documents));
      examples = List.copyOf(examples);
      submodules = List.copyOf(submodules);
    }
  }

  private static final class ArchiveBuilder {

    private final Map<String, String> terraform = new LinkedHashMap<>();
    private final Map<String, ExtractedDocument> documents = new LinkedHashMap<>();
    private final LinkedHashSet<String> examples = new LinkedHashSet<>();
    private final LinkedHashSet<String> submodules = new LinkedHashSet<>();
    private @Nullable ExtractedDocument fallbackReadme;
  }
}
