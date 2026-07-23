package com.stevenbuglione.registry.seed;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.zip.ZipInputStream;

/** Performs bounded archive entry reads and source archive digest calculation. */
final class TerraformArchiveIO {

  private static final int MAX_TEXT_FILE_BYTES = 16 * 1024 * 1024;

  private TerraformArchiveIO() {}

  static byte[] readTextEntry(ZipInputStream archive, String path) throws IOException {
    var output = new ByteArrayOutputStream();
    var buffer = new byte[16 * 1024];
    var size = 0;
    for (var read = archive.read(buffer); read >= 0; read = archive.read(buffer)) {
      size += read;
      if (size > MAX_TEXT_FILE_BYTES) {
        throw new IllegalStateException("Source metadata file exceeds limit: " + path);
      }
      output.write(buffer, 0, read);
    }
    return output.toByteArray();
  }

  static String digest(Path archive) {
    try (var input = Files.newInputStream(archive)) {
      var digest = MessageDigest.getInstance("SHA-256");
      var buffer = new byte[64 * 1024];
      for (var read = input.read(buffer); read >= 0; read = input.read(buffer)) {
        digest.update(buffer, 0, read);
      }
      return "sha256:" + HexFormat.of().formatHex(digest.digest());
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to checksum source archive " + archive, exception);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is not available", exception);
    }
  }
}
