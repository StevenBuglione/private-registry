package com.stevenbuglione.registry.security.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class GraphMembershipClientTest {

    @Test
    void batchesAtTwentyAndCachesSuccessfulMemberships() throws Exception {
        var response = response(200, "{\"value\":[\"group-01\",\"group-21\",\"not-requested\"]}");
        var httpClient = client(response);
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        var client = new GraphMembershipClient(
                httpClient,
                new ObjectMapper(),
                properties(),
                executor);
        var candidates = new LinkedHashSet<String>();
        for (var index = 1; index <= 21; index++) {
            candidates.add("group-%02d".formatted(index));
        }

        assertThat(client.checkMemberships("subject", "delegated-token", candidates))
                .containsExactlyInAnyOrder("group-01", "group-21");
        assertThat(client.checkMemberships("subject", "new-token-is-not-cached", candidates))
                .containsExactlyInAnyOrder("group-01", "group-21");
        verify(httpClient, times(2)).send(any(HttpRequest.class), any());
        executor.close();
    }

    @Test
    void failsClosedAndDoesNotCacheGraphFailures() throws Exception {
        var response = response(503, "{}");
        var httpClient = client(response);
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        var client = new GraphMembershipClient(
                httpClient,
                new ObjectMapper(),
                properties(),
                executor);

        assertThatThrownBy(() -> client.checkMemberships("subject", "token", Set.of("group-01")))
                .isInstanceOf(IdentityProviderUnavailableException.class)
                .hasMessageContaining("503");
        assertThatThrownBy(() -> client.checkMemberships("subject", "token", Set.of("group-01")))
                .isInstanceOf(IdentityProviderUnavailableException.class);
        verify(httpClient, times(2)).send(any(HttpRequest.class), any());
        executor.close();
    }

    private static IdentityProperties properties() {
        return new IdentityProperties(
                false,
                "",
                "",
                "",
                "us-east-1",
                URI.create("https://graph.microsoft.com/v1.0/me/checkMemberGroups"),
                Duration.ofSeconds(2),
                Duration.ofSeconds(60),
                "/",
                "",
                "");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static HttpClient client(HttpResponse<String> response) throws Exception {
        var client = mock(HttpClient.class);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        return client;
    }

    private static HttpResponse<String> response(int status, String body) {
        @SuppressWarnings("unchecked")
        var response = (HttpResponse<String>) mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        return response;
    }
}
