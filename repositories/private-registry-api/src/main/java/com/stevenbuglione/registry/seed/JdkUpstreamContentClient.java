package com.stevenbuglione.registry.seed;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;

final class JdkUpstreamContentClient implements UpstreamContentClient {

  private static final String USER_AGENT = "registry-curated-seeder/1";

  private final SeedProperties properties;
  private final HttpClient http;

  JdkUpstreamContentClient(SeedProperties properties) {
    this.properties = properties;
    this.http =
        HttpClient.newBuilder()
            .connectTimeout(properties.connectionTimeout())
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
  }

  @Override
  public void downloadTo(URI uri, Path destination) {
    try {
      var response = http.send(request(uri), HttpResponse.BodyHandlers.ofFile(destination));
      requireSuccessful(uri, response.statusCode());
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to download " + uri, exception);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while downloading " + uri, exception);
    }
  }

  @Override
  public byte[] downloadBytes(URI uri) {
    try {
      var response = http.send(request(uri), HttpResponse.BodyHandlers.ofByteArray());
      requireSuccessful(uri, response.statusCode());
      return response.body();
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to download " + uri, exception);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while downloading " + uri, exception);
    }
  }

  private HttpRequest request(URI uri) {
    return HttpRequest.newBuilder(uri)
        .timeout(properties.requestTimeout())
        .header("User-Agent", USER_AGENT)
        .GET()
        .build();
  }

  private static void requireSuccessful(URI uri, int statusCode) {
    if (statusCode < 200 || statusCode >= 300) {
      throw new IllegalStateException("Upstream returned " + statusCode + " for " + uri);
    }
  }
}
