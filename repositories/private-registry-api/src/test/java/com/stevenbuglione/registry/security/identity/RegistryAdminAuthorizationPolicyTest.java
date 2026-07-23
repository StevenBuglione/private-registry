package com.stevenbuglione.registry.security.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

class RegistryAdminAuthorizationPolicyTest {

  private final RegistryIdentityService identities = mock(RegistryIdentityService.class);
  private final Authentication authentication = mock(Authentication.class);
  private final RegistryAdminAuthorizationPolicy authorization =
      new RegistryAdminAuthorizationPolicy(identities);

  @Test
  void grantsOnlyResolvedRegistryAdministrators() {
    when(identities.accessContext(authentication))
        .thenReturn(new AccessContext("admin", Set.of(), true))
        .thenReturn(new AccessContext("member", Set.of("APM0000001"), false));
    var request = mock(RequestAuthorizationContext.class);

    assertThat(authorization.authorize(() -> authentication, request).isGranted()).isTrue();
    assertThat(authorization.authorize(() -> authentication, request).isGranted()).isFalse();
  }
}
