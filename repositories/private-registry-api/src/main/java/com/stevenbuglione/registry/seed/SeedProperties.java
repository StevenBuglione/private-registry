package com.stevenbuglione.registry.seed;

import java.time.Duration;
import java.nio.file.Path;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("registry.seed")
public record SeedProperties(
        boolean enabled,
        String manifestResource,
        String providerRepository,
        String moduleRepository,
        String catalogRepository,
        Duration connectionTimeout,
        Duration requestTimeout,
        long maximumDownloadBytes,
        Path cacheDirectory,
        int uploadAttempts,
        Duration uploadRetryBackoff,
        List<String> packages,
        List<String> versions,
        boolean allowUnpinnedDigests,
        boolean exitAfterCompletion) {

    public SeedProperties {
        if (manifestResource == null || manifestResource.isBlank()) {
            manifestResource = "classpath:seed/curated-catalog-v1.json";
        }
        if (providerRepository == null || providerRepository.isBlank()) {
            providerRepository = "iac-provider-release-local";
        }
        if (moduleRepository == null || moduleRepository.isBlank()) {
            moduleRepository = "iac-module-release-local";
        }
        if (catalogRepository == null || catalogRepository.isBlank()) {
            catalogRepository = "iac-catalog-release-local";
        }
        if (connectionTimeout == null) {
            connectionTimeout = Duration.ofSeconds(15);
        }
        if (requestTimeout == null) {
            requestTimeout = Duration.ofMinutes(5);
        }
        if (maximumDownloadBytes < 1) {
            maximumDownloadBytes = 629_145_600;
        }
        if (cacheDirectory == null) {
            cacheDirectory = Path.of(System.getProperty("java.io.tmpdir"), "registry-seed-cache");
        }
        if (uploadAttempts < 1) {
            uploadAttempts = 4;
        }
        if (uploadRetryBackoff == null) {
            uploadRetryBackoff = Duration.ofSeconds(15);
        }
        packages = packages == null ? List.of() : packages.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .toList();
        versions = versions == null ? List.of() : versions.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .toList();
    }
}
