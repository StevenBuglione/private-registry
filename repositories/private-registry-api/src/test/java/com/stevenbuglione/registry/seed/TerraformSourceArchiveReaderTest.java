package com.stevenbuglione.registry.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TerraformSourceArchiveReaderTest {

  @TempDir private Path temporaryDirectory;

  @Test
  void stripsOnlyThePinnedArchiveRootFromSafePaths() {
    assertThat(TerraformSourceArchiveReader.relativePath("release-v1/README.md"))
        .isEqualTo("README.md");
    assertThat(TerraformSourceArchiveReader.relativePath("release-v1/modules/app/main.tf"))
        .isEqualTo("modules/app/main.tf");
    assertThat(TerraformSourceArchiveReader.relativePath("README.md")).isNull();
  }

  @Test
  void rejectsTraversalAbsoluteDriveAndEmptyRelativePaths() {
    assertUnsafe("../escape.tf");
    assertUnsafe("/absolute.tf");
    assertUnsafe("C:/escape.tf");
    assertUnsafe("release-v1/../../escape.tf");
    assertUnsafe("release-v1\\..\\..\\escape.tf");
    assertUnsafe("release-v1//escape.tf");
  }

  @Test
  void rejectsAnUnsafeZipEntryBeforeReadingItsContent() throws IOException {
    var archive =
        archive(
            Map.of(
                "release-v1/README.md", "# Safe",
                "release-v1/../../escape.tf", "variable \"escaped\" {}"));

    assertThatThrownBy(() -> TerraformSourceArchiveReader.read(archive, false))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("unsafe path");
  }

  @Test
  void requiresAReadmeAndSynthesizesAProviderIndexFromIt() throws IOException {
    var provider = archive(Map.of("provider-v1/README.md", "# Example Provider\n\nDescription."));
    var contents = TerraformSourceArchiveReader.read(provider, true);

    assertThat(contents.documents().keySet()).containsExactlyInAnyOrder("README.md", "index.md");
    assertThat(contents.archiveDigest()).matches("^sha256:[0-9a-f]{64}$");

    var missingReadme = archive(Map.of("module-v1/main.tf", "variable \"name\" {}"));
    assertThatThrownBy(() -> TerraformSourceArchiveReader.read(missingReadme, false))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("does not contain a README");
  }

  private void assertUnsafe(String path) {
    assertThatThrownBy(() -> TerraformSourceArchiveReader.relativePath(path))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("unsafe path");
  }

  private Path archive(Map<String, String> entries) throws IOException {
    var path = temporaryDirectory.resolve("source-" + entries.hashCode() + ".zip");
    try (var output = new ZipOutputStream(Files.newOutputStream(path))) {
      for (var entry : entries.entrySet()) {
        output.putNextEntry(new ZipEntry(entry.getKey()));
        output.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
        output.closeEntry();
      }
    }
    return path;
  }
}
