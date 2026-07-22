package com.stevenbuglione.registry.web;

import com.stevenbuglione.registry.security.identity.ApmEntitlement;
import com.stevenbuglione.registry.security.identity.IdentityProperties;
import com.stevenbuglione.registry.security.identity.RegistryIdentityService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final RegistryIdentityService identities;
    private final IdentityProperties properties;
    private final ObjectProvider<OAuth2AuthorizedClientService> authorizedClients;
    private final ObjectProvider<ClientRegistrationRepository> clientRegistrations;

    public AuthController(
            RegistryIdentityService identities,
            IdentityProperties properties,
            ObjectProvider<OAuth2AuthorizedClientService> authorizedClients,
            ObjectProvider<ClientRegistrationRepository> clientRegistrations) {
        this.identities = identities;
        this.properties = properties;
        this.authorizedClients = authorizedClients;
        this.clientRegistrations = clientRegistrations;
    }

    @GetMapping("/session")
    public SessionResponse session(Authentication authentication, CsrfToken csrfToken) {
        var principal = identities.principal(authentication);
        return new SessionResponse(
                principal.subject(),
                principal.displayName(),
                principal.email(),
                principal.roles(),
                principal.entitlements(),
                csrfToken.getToken());
    }

    @PostMapping("/logout")
    public LogoutResponse logout(
            HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        var target = logoutTarget(request, authentication);
        if (authentication instanceof OAuth2AuthenticationToken oauth) {
            authorizedClients.ifAvailable(service -> service.removeAuthorizedClient(
                    oauth.getAuthorizedClientRegistrationId(), oauth.getName()));
        }
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        return new LogoutResponse(target);
    }

    private String logoutTarget(HttpServletRequest request, Authentication authentication) {
        var postLogoutRedirect = absolutePostLogoutRedirect(request);
        if (!(authentication instanceof OAuth2AuthenticationToken oauth)) {
            return postLogoutRedirect;
        }
        var registrations = clientRegistrations.getIfAvailable();
        if (registrations == null) {
            return postLogoutRedirect;
        }
        var registration = registrations.findByRegistrationId(oauth.getAuthorizedClientRegistrationId());
        if (registration == null) {
            return postLogoutRedirect;
        }
        var endpoint = registration.getProviderDetails().getConfigurationMetadata().get("end_session_endpoint");
        if (!(endpoint instanceof String endSessionEndpoint) || endSessionEndpoint.isBlank()) {
            return postLogoutRedirect;
        }
        var builder = UriComponentsBuilder.fromUriString(endSessionEndpoint)
                .queryParam("post_logout_redirect_uri", postLogoutRedirect);
        if (oauth.getPrincipal() instanceof OidcUser oidcUser && oidcUser.getIdToken() != null) {
            builder.queryParam("id_token_hint", oidcUser.getIdToken().getTokenValue());
        }
        return builder.build().encode().toUriString();
    }

    private String absolutePostLogoutRedirect(HttpServletRequest request) {
        var configured = properties.postLogoutRedirectUri();
        if (URI.create(configured).isAbsolute()) {
            return configured;
        }
        return ServletUriComponentsBuilder.fromRequest(request)
                .replacePath(configured.startsWith("/") ? configured : "/" + configured)
                .replaceQuery(null)
                .build()
                .toUriString();
    }

    public record SessionResponse(
            String subject,
            String displayName,
            String email,
            Set<String> roles,
            List<ApmEntitlement> apmEntitlements,
            String csrfToken) {
        public SessionResponse {
            roles = Set.copyOf(roles);
            apmEntitlements = List.copyOf(apmEntitlements);
        }
    }

    public record LogoutResponse(String redirectUri) {}
}
