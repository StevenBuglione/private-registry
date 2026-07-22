package com.stevenbuglione.registry.security.identity;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

@Service
public class RegistryIdentityService {

  private static final RegistryPrincipal LOCAL_ADMINISTRATOR =
      new RegistryPrincipal(
          "local-development",
          "Local Registry Administrator",
          null,
          Set.of("registry-admin"),
          List.of());

  private final IdentityProperties properties;
  private final EntitlementConfigurationRepository entitlements;
  private final GraphMembershipClient graph;
  private final ObjectProvider<OAuth2AuthorizedClientService> authorizedClients;

  public RegistryIdentityService(
      IdentityProperties properties,
      EntitlementConfigurationRepository entitlements,
      GraphMembershipClient graph,
      ObjectProvider<OAuth2AuthorizedClientService> authorizedClients) {
    this.properties = properties;
    this.entitlements = entitlements;
    this.graph = graph;
    this.authorizedClients = authorizedClients;
  }

  public RegistryPrincipal principal(@Nullable Authentication authentication) {
    if (authentication != null
        && authentication.getPrincipal() instanceof RegistryPrincipal principal) {
      return principal;
    }
    if (properties.permitAll()
        && (authentication == null || authentication instanceof AnonymousAuthenticationToken)) {
      return LOCAL_ADMINISTRATOR;
    }
    if (authentication instanceof OAuth2AuthenticationToken oauth
        && oauth.getPrincipal() instanceof OidcUser oidcUser) {
      return fromOidc(oauth, oidcUser);
    }
    throw new AuthenticationCredentialsNotFoundException("A Registry identity is required");
  }

  public AccessContext accessContext(@Nullable Authentication authentication) {
    return principal(authentication).accessContext();
  }

  public RegistryPrincipal fromAlb(
      AlbTokenVerifier.VerifiedIdentity identity, String delegatedAccessToken) {
    return enrich(
        identity.subject(), identity.displayName(), identity.email(), delegatedAccessToken);
  }

  private RegistryPrincipal fromOidc(OAuth2AuthenticationToken authentication, OidcUser user) {
    var clientService = authorizedClients.getIfAvailable();
    if (clientService == null) {
      throw new IdentityProviderUnavailableException(
          "OAuth2 authorized-client storage is unavailable");
    }
    var client =
        clientService.loadAuthorizedClient(
            authentication.getAuthorizedClientRegistrationId(), authentication.getName());
    if (client == null || client.getAccessToken() == null) {
      throw new IdentityProviderUnavailableException(
          "The Microsoft Graph access token is unavailable");
    }
    var subject = requiredSubject(user.getSubject());
    var displayName = displayName(user.getClaimAsString("name"), user.getFullName(), subject);
    var email =
        optionalEmail(
            user.getEmail(),
            user.getClaimAsString("preferred_username"),
            user.getClaimAsString("upn"));
    return enrich(subject, displayName, email, client.getAccessToken().getTokenValue());
  }

  private RegistryPrincipal enrich(
      String subject, String displayName, @Nullable String email, String delegatedAccessToken) {
    var configuredGroupIds = entitlements.configuredGroupIds();
    var memberships = graph.checkMemberships(subject, delegatedAccessToken, configuredGroupIds);
    var resolved = entitlements.resolve(memberships);
    var roles = new LinkedHashSet<String>();
    roles.add("registry-user");
    if (resolved.administrator()) {
      roles.add("registry-admin");
    }
    return new RegistryPrincipal(
        subject, displayName, email, Set.copyOf(roles), resolved.entitlements());
  }

  static String requiredSubject(@Nullable String subject) {
    if (subject == null || subject.isBlank()) {
      throw new AuthenticationCredentialsNotFoundException(
          "The OpenID Connect subject claim is required");
    }
    return subject;
  }

  static String displayName(@Nullable String name, @Nullable String fullName, String subject) {
    var resolved = firstNonBlank(name, fullName, subject);
    return resolved == null ? "Registry user" : resolved;
  }

  static @Nullable String optionalEmail(
      @Nullable String email,
      @Nullable String preferredUsername,
      @Nullable String userPrincipalName) {
    return firstNonBlank(email, preferredUsername, userPrincipalName);
  }

  private static @Nullable String firstNonBlank(
      @Nullable String first, @Nullable String second, @Nullable String third) {
    if (first != null && !first.isBlank()) {
      return first;
    }
    if (second != null && !second.isBlank()) {
      return second;
    }
    return third == null || third.isBlank() ? null : third;
  }
}
