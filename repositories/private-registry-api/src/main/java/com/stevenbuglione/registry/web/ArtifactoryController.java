package com.stevenbuglione.registry.web;

import com.stevenbuglione.registry.storage.ArtifactStorageStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ArtifactoryController {

  private final ArtifactStorageStatus artifactStorage;

  public ArtifactoryController(ArtifactStorageStatus artifactStorage) {
    this.artifactStorage = artifactStorage;
  }

  @GetMapping("/api/v1/artifactory/status")
  public ArtifactStorageStatus.Status status() {
    return artifactStorage.status();
  }
}
