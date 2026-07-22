package com.stevenbuglione.registry.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stevenbuglione.registry.eventing.CatalogEventPublisher;
import com.stevenbuglione.registry.eventing.webhook.JfrogWebhookController;
import com.stevenbuglione.registry.eventing.webhook.JfrogWebhookParser;
import com.stevenbuglione.registry.eventing.webhook.JfrogWebhookProperties;
import com.stevenbuglione.registry.eventing.webhook.JfrogWebhookSignatureVerifier;
import com.stevenbuglione.registry.security.identity.AlbAuthenticationFilter;
import com.stevenbuglione.registry.security.identity.AlbTokenVerifier;
import com.stevenbuglione.registry.security.identity.IdentityProperties;
import com.stevenbuglione.registry.security.identity.RegistryIdentityService;
import java.net.URI;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

class SecurityConfigurationTest {

  private AnnotationConfigWebApplicationContext context;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    context = new AnnotationConfigWebApplicationContext();
    context.setServletContext(new org.springframework.mock.web.MockServletContext());
    context.register(TestConfiguration.class);
    context.refresh();
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @AfterEach
  void tearDown() {
    context.close();
  }

  @Test
  void allowsUnsignedAuthenticationBoundaryToReachWebhookSignatureValidation() throws Exception {
    var payload = "{\"event_type\":\"deployed\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);

    mvc.perform(
            post("/internal/webhooks/jfrog")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .header("X-JFrog-Signature", "invalid")
                .header("X-JFrog-Origin", "jfrog.example")
                .header("X-JFrog-Subscription-Id", "registry"))
        .andExpect(status().isUnauthorized());

    verify(context.getBean(JfrogWebhookSignatureVerifier.class))
        .isValid(any(byte[].class), eq("invalid"), eq("test-secret"));
  }

  @Test
  void keepsApiAndNonPostWebhookRequestsClosed() throws Exception {
    mvc.perform(get("/api/closed")).andExpect(status().isUnauthorized());
    mvc.perform(get("/internal/webhooks/jfrog")).andExpect(status().isUnauthorized());
  }

  @Configuration(proxyBeanMethods = false)
  @EnableWebMvc
  @EnableWebSecurity
  @Import(SecurityConfiguration.class)
  static class TestConfiguration {

    @Bean
    IdentityProperties identityProperties() {
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

    @Bean
    AlbAuthenticationFilter albAuthenticationFilter() {
      return new AlbAuthenticationFilter(
          mock(AlbTokenVerifier.class), mock(RegistryIdentityService.class));
    }

    @Bean
    JfrogWebhookProperties jfrogWebhookProperties() {
      return new JfrogWebhookProperties(
          true,
          "test-secret",
          Set.of("jfrog.example"),
          "registry",
          Set.of("iac-provider-release-local"),
          Set.of("hashicorp/"),
          4096);
    }

    @Bean
    JfrogWebhookSignatureVerifier jfrogWebhookSignatureVerifier() {
      return mock(JfrogWebhookSignatureVerifier.class);
    }

    @Bean
    JfrogWebhookParser jfrogWebhookParser() {
      return mock(JfrogWebhookParser.class);
    }

    @Bean
    CatalogEventPublisher catalogEventPublisher() {
      return mock(CatalogEventPublisher.class);
    }

    @Bean
    JfrogWebhookController jfrogWebhookController(
        JfrogWebhookProperties properties,
        JfrogWebhookSignatureVerifier signatureVerifier,
        JfrogWebhookParser parser,
        CatalogEventPublisher publisher) {
      return new JfrogWebhookController(properties, signatureVerifier, parser, publisher);
    }

    @Bean
    ClosedApiController closedApiController() {
      return new ClosedApiController();
    }
  }

  @RestController
  static class ClosedApiController {
    @GetMapping("/api/closed")
    String closed() {
      return "closed";
    }
  }
}
