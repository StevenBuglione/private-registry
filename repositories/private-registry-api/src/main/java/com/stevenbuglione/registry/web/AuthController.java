package com.stevenbuglione.registry.web;

import com.stevenbuglione.registry.security.identity.ApmEntitlement;
import com.stevenbuglione.registry.security.identity.OidcLogoutService;
import com.stevenbuglione.registry.security.identity.RegistryIdentityService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private final RegistryIdentityService identities;
  private final OidcLogoutService logoutService;

  public AuthController(RegistryIdentityService identities, OidcLogoutService logoutService) {
    this.identities = identities;
    this.logoutService = logoutService;
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
    return new LogoutResponse(logoutService.logout(request, response, authentication));
  }

  public record SessionResponse(
      String subject,
      String displayName,
      @Nullable String email,
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
