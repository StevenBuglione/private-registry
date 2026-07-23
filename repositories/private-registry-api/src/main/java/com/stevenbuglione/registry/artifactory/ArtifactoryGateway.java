package com.stevenbuglione.registry.artifactory;

import com.stevenbuglione.registry.config.ArtifactoryProperties;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jfrog.artifactory.client.Artifactory;
import org.jfrog.artifactory.client.ArtifactoryRequest;
import org.jfrog.artifactory.client.impl.ArtifactoryRequestImpl;
import org.jfrog.artifactory.client.model.RepoPath;
import org.jfrog.artifactory.client.model.repository.settings.impl.GenericRepositorySettingsImpl;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

@Service
public class ArtifactoryGateway {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final Artifactory client;
  private final ArtifactoryProperties properties;

  public ArtifactoryGateway(Artifactory client, ArtifactoryProperties properties) {
    this.client = client;
    this.properties = properties;
  }

  public boolean ping() {
    return client.system().ping();
  }

  public Status status() {
    var reachable = ping();
    var repositories =
        properties.hasAccessToken()
            ? List.of(
                repository(properties.moduleRepository()),
                repository(properties.providerRepository()))
            : List.of(
                new RepositoryStatus(properties.moduleRepository(), "authentication-required"),
                new RepositoryStatus(properties.providerRepository(), "authentication-required"));
    return new Status(reachable, properties.url().toString(), repositories);
  }

  private RepositoryStatus repository(String key) {
    var repository = client.repository(key).get();
    return new RepositoryStatus(repository.getKey(), repository.getRclass().toString());
  }

  /**
   * Ensures that a generic local repository exists without replacing an existing repository.
   * Repository creation deliberately stays behind this adapter so application code never falls back
   * to an ad-hoc Artifactory HTTP client.
   */
  public void ensureLocalRepository(String repositoryKey, String description) {
    validateRepositoryKey(repositoryKey);
    var handle = client.repository(repositoryKey);
    if (handle.exists()) {
      return;
    }
    var repository =
        client
            .repositories()
            .builders()
            .localRepositoryBuilder()
            .key(repositoryKey)
            .description(description)
            .repositorySettings(new GenericRepositorySettingsImpl())
            .build();
    client.repositories().create(0, repository);
  }

  public ArtifactMetadata metadata(String repositoryKey, String path) {
    validateArtifactLocation(repositoryKey, path);
    org.jfrog.artifactory.client.model.File file =
        client.repository(repositoryKey).file(path).info();
    var checksums = file.getChecksums();
    var properties = client.repository(repositoryKey).file(path).getProperties();
    return new ArtifactMetadata(
        repositoryKey,
        path,
        file.getSize(),
        checksums == null ? null : checksums.getSha256(),
        file.getLastModified() == null ? null : file.getLastModified().toInstant(),
        immutableProperties(properties));
  }

  /** Reads Artifactory's authoritative download counter through the official JFrog client. */
  public ArtifactDownloadStatistics downloadStatistics(String repositoryKey, String path) {
    validateArtifactLocation(repositoryKey, path);
    var apiPath =
        "/api/storage/"
            + encodePathSegment(repositoryKey)
            + "/"
            + java.util.Arrays.stream(path.split("/", -1))
                .map(ArtifactoryGateway::encodePathSegment)
                .collect(java.util.stream.Collectors.joining("/"));
    try {
      var request =
          new ArtifactoryRequestImpl()
              .method(ArtifactoryRequest.Method.GET)
              .apiUrl(apiPath)
              .addQueryParam("stats", "")
              .responseType(ArtifactoryRequest.ContentType.JSON);
      var artifactoryResponse = client.restCall(request);
      if (!artifactoryResponse.isSuccessResponse()) {
        throw new IOException(
            "Artifactory returned " + artifactoryResponse.getStatusLine().getStatusCode());
      }
      var response = JSON.readValue(artifactoryResponse.getRawBody(), FileStatisticsResponse.class);
      var lastDownloadedAt =
          response.lastDownloaded() > 0 ? Instant.ofEpochMilli(response.lastDownloaded()) : null;
      return new ArtifactDownloadStatistics(
          Math.max(0, response.downloadCount()), lastDownloadedAt, Instant.now());
    } catch (IOException exception) {
      throw new UncheckedIOException(
          "Unable to read Artifactory download statistics for " + repositoryKey + "/" + path,
          exception);
    }
  }

  public byte[] download(String repositoryKey, String path, long maximumBytes) {
    validateArtifactLocation(repositoryKey, path);
    if (maximumBytes < 1) {
      throw new IllegalArgumentException("maximumBytes must be positive");
    }
    try (var input = client.repository(repositoryKey).download(path).doDownload()) {
      var content =
          input.readNBytes(Math.toIntExact(Math.min(maximumBytes + 1, Integer.MAX_VALUE)));
      if (content.length > maximumBytes) {
        throw new ArtifactTooLargeException(repositoryKey + "/" + path, maximumBytes);
      }
      return content;
    } catch (IOException exception) {
      throw new UncheckedIOException("Unable to download " + repositoryKey + "/" + path, exception);
    }
  }

  public ArtifactMetadata upload(
      String repositoryKey, String path, byte[] content, Map<String, ?> artifactProperties) {
    validateArtifactLocation(repositoryKey, path);
    Objects.requireNonNull(content, "content");
    var upload = client.repository(repositoryKey).upload(path, new ByteArrayInputStream(content));
    applyProperties(upload, artifactProperties);
    var file = upload.withSize(content.length).doUpload();
    return metadata(repositoryKey, path, file, artifactProperties);
  }

  /** Uploads a repeatable file body using the official JFrog Java Client. */
  public ArtifactMetadata upload(
      String repositoryKey, String path, Path content, Map<String, ?> artifactProperties) {
    validateArtifactLocation(repositoryKey, path);
    Objects.requireNonNull(content, "content");
    if (!Files.isRegularFile(content)) {
      throw new IllegalArgumentException("Upload content must be a regular file");
    }
    var upload = client.repository(repositoryKey).upload(path, content.toFile());
    applyProperties(upload, artifactProperties);
    var file = upload.doUpload();
    return metadata(repositoryKey, path, file, artifactProperties);
  }

  private static ArtifactMetadata metadata(
      String repositoryKey,
      String path,
      org.jfrog.artifactory.client.model.File file,
      Map<String, ?> artifactProperties) {
    var checksums = file.getChecksums();
    return new ArtifactMetadata(
        repositoryKey,
        path,
        file.getSize(),
        checksums == null ? null : checksums.getSha256(),
        file.getLastModified() == null ? null : file.getLastModified().toInstant(),
        immutableProperties(artifactProperties));
  }

  private static void applyProperties(
      org.jfrog.artifactory.client.UploadableArtifact upload, Map<String, ?> artifactProperties) {
    artifactProperties.forEach(
        (key, value) -> {
          if (value instanceof Iterable<?> iterable) {
            var values = new java.util.ArrayList<>();
            iterable.forEach(values::add);
            upload.withProperty(key, values.toArray());
          } else {
            upload.withProperty(key, value);
          }
        });
  }

  public void setProperties(String repositoryKey, String path, Map<String, ?> artifactProperties) {
    validateArtifactLocation(repositoryKey, path);
    client
        .repository(repositoryKey)
        .file(path)
        .properties()
        .addProperties(artifactProperties)
        .doSet(false);
  }

  public List<ArtifactLocation> searchByProperty(
      List<String> repositoryKeys, String propertyName, String propertyValue) {
    if (repositoryKeys.isEmpty()) {
      return List.of();
    }
    List<RepoPath> results;
    try {
      results =
          client
              .searches()
              .repositories(repositoryKeys.toArray(String[]::new))
              .itemsByProperty()
              .property(propertyName, propertyValue)
              .doSearch();
    } catch (IOException exception) {
      throw new UncheckedIOException("Artifactory property search failed", exception);
    }
    return results.stream()
        .map(result -> new ArtifactLocation(result.getRepoKey(), result.getItemPath()))
        .toList();
  }

  private static Map<String, List<String>> immutableProperties(Map<String, ?> source) {
    var copy = new LinkedHashMap<String, List<String>>();
    source.forEach(
        (key, value) -> {
          if (value instanceof Iterable<?> iterable) {
            var values = new java.util.ArrayList<String>();
            iterable.forEach(element -> values.add(String.valueOf(element)));
            copy.put(key, List.copyOf(values));
          } else if (value != null) {
            copy.put(key, List.of(String.valueOf(value)));
          }
        });
    return Map.copyOf(copy);
  }

  private static void validateArtifactLocation(String repositoryKey, String path) {
    validateRepositoryKey(repositoryKey);
    if (path == null
        || path.isBlank()
        || path.startsWith("/")
        || path.contains("..")
        || path.contains("\\")) {
      throw new IllegalArgumentException("Invalid Artifactory path");
    }
  }

  private static void validateRepositoryKey(String repositoryKey) {
    if (repositoryKey == null || !repositoryKey.matches("[A-Za-z0-9._-]{1,128}")) {
      throw new IllegalArgumentException("Invalid Artifactory repository key");
    }
  }

  private static String encodePathSegment(String value) {
    return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
  }

  public record Status(boolean reachable, String url, List<RepositoryStatus> repositories) {}

  public record RepositoryStatus(String key, String repositoryClass) {}

  public record ArtifactMetadata(
      String repository,
      String path,
      long size,
      @Nullable String sha256,
      @Nullable Instant modifiedAt,
      Map<String, List<String>> properties) {}

  public record ArtifactLocation(String repository, String path) {}

  public record ArtifactDownloadStatistics(
      long downloadCount, @Nullable Instant lastDownloadedAt, Instant observedAt) {}

  record FileStatisticsResponse(
      long downloadCount,
      long lastDownloaded,
      @Nullable String lastDownloadedBy,
      long remoteDownloadCount,
      long remoteLastDownloaded) {}

  public static final class ArtifactTooLargeException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ArtifactTooLargeException(String artifact, long maximumBytes) {
      super("Artifact " + artifact + " exceeds " + maximumBytes + " bytes");
    }
  }
}
