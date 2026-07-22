package com.stevenbuglione.registry.web;

import com.stevenbuglione.registry.artifactory.ArtifactoryGateway;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ArtifactoryController {

  private final ArtifactoryGateway artifactory;

  public ArtifactoryController(ArtifactoryGateway artifactory) {
    this.artifactory = artifactory;
  }

  @GetMapping("/api/v1/artifactory/status")
  public ArtifactoryGateway.Status status() {
    return artifactory.status();
  }
}
