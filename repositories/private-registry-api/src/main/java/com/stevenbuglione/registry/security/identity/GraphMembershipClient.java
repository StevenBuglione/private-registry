package com.stevenbuglione.registry.security.identity;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class GraphMembershipClient {

    static final int MAX_GROUPS_PER_REQUEST = 20;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final IdentityProperties properties;
    private final ExecutorService executor;
    private final AsyncCache<CacheKey, Set<String>> cache;

    @Autowired
    public GraphMembershipClient(HttpClient httpClient, ObjectMapper objectMapper, IdentityProperties properties) {
        this(httpClient, objectMapper, properties, Executors.newVirtualThreadPerTaskExecutor());
    }

    GraphMembershipClient(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            IdentityProperties properties,
            ExecutorService executor) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.executor = executor;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(properties.membershipCacheTtl())
                .buildAsync();
    }

    public Set<String> checkMemberships(String subject, String delegatedAccessToken, Set<String> candidateGroupIds) {
        if (candidateGroupIds.isEmpty()) {
            return Set.of();
        }
        if (delegatedAccessToken == null || delegatedAccessToken.isBlank()) {
            throw new IdentityProviderUnavailableException("A delegated Microsoft Graph access token is required");
        }

        var candidates = candidateGroupIds.stream().sorted().toList();
        var key = new CacheKey(subject, candidates);
        var result = cache.get(key, (ignored, cacheExecutor) -> CompletableFuture.supplyAsync(
                () -> fetchMemberships(delegatedAccessToken, candidates), executor));

        try {
            return result.join();
        } catch (CompletionException exception) {
            cache.synchronous().invalidate(key);
            var cause = exception.getCause();
            if (cause instanceof IdentityProviderUnavailableException unavailable) {
                throw unavailable;
            }
            throw new IdentityProviderUnavailableException("Microsoft Graph membership lookup failed", cause);
        }
    }

    private Set<String> fetchMemberships(String accessToken, List<String> candidates) {
        var memberships = new HashSet<String>();
        for (var offset = 0; offset < candidates.size(); offset += MAX_GROUPS_PER_REQUEST) {
            var batch = candidates.subList(offset, Math.min(offset + MAX_GROUPS_PER_REQUEST, candidates.size()));
            var request = HttpRequest.newBuilder(properties.graphEndpoint())
                    .timeout(properties.graphTimeout())
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody(batch)))
                    .build();
            try {
                var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new IdentityProviderUnavailableException(
                            "Microsoft Graph membership lookup returned HTTP " + response.statusCode());
                }
                var root = objectMapper.readTree(response.body());
                var values = root.path("value");
                if (!values.isArray()) {
                    throw new IdentityProviderUnavailableException("Microsoft Graph returned an invalid membership response");
                }
                values.forEach(value -> {
                    if (value.isString() && batch.contains(value.stringValue())) {
                        memberships.add(value.stringValue());
                    }
                });
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IdentityProviderUnavailableException("Microsoft Graph membership lookup was interrupted", exception);
            } catch (IOException exception) {
                throw new IdentityProviderUnavailableException("Microsoft Graph membership lookup failed", exception);
            }
        }
        return memberships.stream().sorted(Comparator.naturalOrder()).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private String requestBody(List<String> batch) {
        try {
            return objectMapper.writeValueAsString(Map.of("groupIds", batch));
        } catch (JacksonException exception) {
            throw new IdentityProviderUnavailableException("Unable to create Microsoft Graph membership request", exception);
        }
    }

    @PreDestroy
    void close() {
        executor.close();
    }

    private record CacheKey(String subject, List<String> candidateGroupIds) {
        private CacheKey {
            candidateGroupIds = List.copyOf(candidateGroupIds);
        }
    }

}
