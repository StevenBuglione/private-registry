package com.stevenbuglione.registry.artifactory;

import com.stevenbuglione.registry.config.ArtifactoryProperties;
import java.util.List;
import org.jfrog.artifactory.client.Artifactory;
import org.springframework.stereotype.Service;

@Service
public class ArtifactoryGateway {

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
        var repositories = properties.hasAccessToken()
                ? List.of(repository(properties.moduleRepository()), repository(properties.providerRepository()))
                : List.of(
                        new RepositoryStatus(properties.moduleRepository(), "authentication-required"),
                        new RepositoryStatus(properties.providerRepository(), "authentication-required"));
        return new Status(reachable, properties.url().toString(), repositories);
    }

    private RepositoryStatus repository(String key) {
        var repository = client.repository(key).get();
        return new RepositoryStatus(repository.getKey(), repository.getRclass().toString());
    }

    public record Status(boolean reachable, String url, List<RepositoryStatus> repositories) {}

    public record RepositoryStatus(String key, String repositoryClass) {}
}
