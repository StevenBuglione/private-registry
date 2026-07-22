package com.stevenbuglione.registry.security.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

class OidcLogoutServiceTest {

  @Test
  void clearsLocalAuthenticationAndBuildsAnAbsoluteRedirect() {
    var properties =
        new IdentityProperties(
            false,
            "",
            "",
            "",
            "us-east-1",
            URI.create("https://graph.microsoft.com/v1.0/me/checkMemberGroups"),
            Duration.ofSeconds(5),
            Duration.ofSeconds(60),
            "/",
            "",
            "");
    ObjectProvider<OAuth2AuthorizedClientService> authorizedClients = new ObjectProvider<>() {};
    ObjectProvider<ClientRegistrationRepository> clientRegistrations = new ObjectProvider<>() {};
    var service = new OidcLogoutService(properties, authorizedClients, clientRegistrations);
    var request = new MockHttpServletRequest("POST", "/api/v1/auth/logout");
    request.setScheme("http");
    request.setServerName("localhost");
    request.setServerPort(3000);
    var response = new MockHttpServletResponse();
    var authentication = new TestingAuthenticationToken("subject", "credentials", "ROLE_USER");
    SecurityContextHolder.getContext().setAuthentication(authentication);

    try {
      var target = service.logout(request, response, authentication);

      assertThat(target).isEqualTo("http://localhost:3000/");
      assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    } finally {
      SecurityContextHolder.clearContext();
    }
  }
}
