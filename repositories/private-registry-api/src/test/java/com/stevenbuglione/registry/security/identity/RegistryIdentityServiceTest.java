package com.stevenbuglione.registry.security.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;

class RegistryIdentityServiceTest {

  @Test
  void rejectsAnOidcIdentityWithoutASubject() {
    assertThatThrownBy(() -> RegistryIdentityService.requiredSubject(null))
        .isInstanceOf(AuthenticationCredentialsNotFoundException.class)
        .hasMessageContaining("subject");
    assertThatThrownBy(() -> RegistryIdentityService.requiredSubject("  "))
        .isInstanceOf(AuthenticationCredentialsNotFoundException.class)
        .hasMessageContaining("subject");
  }

  @Test
  void resolvesDisplayNameWithoutChangingIdentity() {
    assertThat(RegistryIdentityService.displayName("Ada Lovelace", "Ada", "subject-1"))
        .isEqualTo("Ada Lovelace");
    assertThat(RegistryIdentityService.displayName(null, null, "subject-1")).isEqualTo("subject-1");
  }

  @Test
  void keepsMissingEmailNullable() {
    assertThat(RegistryIdentityService.optionalEmail(null, " ", null)).isNull();
    assertThat(RegistryIdentityService.optionalEmail(null, "ada@example.com", null))
        .isEqualTo("ada@example.com");
  }
}
