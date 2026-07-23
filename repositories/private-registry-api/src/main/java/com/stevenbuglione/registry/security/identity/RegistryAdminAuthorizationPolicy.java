package com.stevenbuglione.registry.security.identity;

import java.util.function.Supplier;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

/** Central policy for HTTP surfaces reserved for Registry administrators. */
@Component
public final class RegistryAdminAuthorizationPolicy
    implements AuthorizationManager<RequestAuthorizationContext> {

  private final RegistryIdentityService identities;

  public RegistryAdminAuthorizationPolicy(RegistryIdentityService identities) {
    this.identities = identities;
  }

  @Override
  public AuthorizationResult authorize(
      Supplier<? extends Authentication> authentication, RequestAuthorizationContext context) {
    return new AuthorizationDecision(
        identities.accessContext(authentication.get()).registryAdmin());
  }
}
