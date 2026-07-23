package com.stevenbuglione.registry.storage;

import java.util.List;

/** Application-facing operational status for governed artifact storage. */
public interface ArtifactStorageStatus {

  boolean ping();

  Status status();

  record Status(boolean reachable, String url, List<RepositoryStatus> repositories) {
    public Status {
      repositories = List.copyOf(repositories);
    }
  }

  record RepositoryStatus(String key, String repositoryClass) {}
}
