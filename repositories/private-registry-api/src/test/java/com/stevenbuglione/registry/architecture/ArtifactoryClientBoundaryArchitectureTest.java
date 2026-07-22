package com.stevenbuglione.registry.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ArtifactoryClientBoundaryArchitectureTest {

    private static final Path MAIN_JAVA = Path.of("src", "main", "java");
    private static final Set<String> RAW_HTTP_ALLOWLIST = Set.of(
            "com/stevenbuglione/registry/config/IdentityConfiguration.java",
            "com/stevenbuglione/registry/security/identity/AlbTokenVerifier.java",
            "com/stevenbuglione/registry/security/identity/GraphMembershipClient.java",
            "com/stevenbuglione/registry/seed/ArtifactoryCatalogSeeder.java");
    private static final List<String> ARTIFACTORY_REST_MARKERS = List.of(
            "/api/storage",
            "/api/search",
            "/api/repositories",
            "/api/system",
            "/api/security",
            "X-JFrog-Art-Api",
            "X-Checksum-Deploy");

    @Test
    void rawHttpIsLimitedToIdentityAndPublicUpstreamDownloads() throws IOException {
        try (var sources = Files.walk(MAIN_JAVA)) {
            var violations = sources.filter(path -> path.toString().endsWith(".java"))
                    .filter(ArtifactoryClientBoundaryArchitectureTest::importsRawHttp)
                    .map(MAIN_JAVA::relativize)
                    .map(ArtifactoryClientBoundaryArchitectureTest::portablePath)
                    .filter(path -> !RAW_HTTP_ALLOWLIST.contains(path))
                    .toList();

            assertThat(violations)
                    .as("Raw Java HTTP clients must not cross the reviewed identity/upstream-download boundary")
                    .isEmpty();
        }
    }

    @Test
    void noProductionSourceConstructsArtifactoryRestRequests() throws IOException {
        try (var sources = Files.walk(MAIN_JAVA)) {
            var violations = sources.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> ARTIFACTORY_REST_MARKERS.stream().anyMatch(marker -> contains(path, marker)))
                    .map(MAIN_JAVA::relativize)
                    .map(ArtifactoryClientBoundaryArchitectureTest::portablePath)
                    .toList();

            assertThat(violations)
                    .as("All Artifactory reads, writes, searches, and repository mutations must use ArtifactoryGateway")
                    .isEmpty();
        }
    }

    @Test
    void gatewayUsesTheOfficialJfrogClientAndSeederUsesOnlyTheGateway() throws IOException {
        var gateway = Files.readString(MAIN_JAVA.resolve(
                "com/stevenbuglione/registry/artifactory/ArtifactoryGateway.java"));
        var seeder = Files.readString(MAIN_JAVA.resolve(
                "com/stevenbuglione/registry/seed/ArtifactoryCatalogSeeder.java"));

        assertThat(gateway).contains("org.jfrog.artifactory.client.Artifactory");
        assertThat(seeder)
                .contains("ArtifactoryGateway")
                .doesNotContain("/api/storage", "/api/repositories", "X-JFrog-Art-Api");
    }

    private static boolean importsRawHttp(Path path) {
        return contains(path, "java.net.http.HttpClient") || contains(path, "java.net.http.HttpRequest");
    }

    private static boolean contains(Path path, String text) {
        try {
            return Files.readString(path).contains(text);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to inspect " + path, exception);
        }
    }

    private static String portablePath(Path path) {
        return path.toString().replace('\\', '/');
    }
}
