package com.stevenbuglione.registry.web;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.stevenbuglione.registry.administration.AdminDashboardService;
import com.stevenbuglione.registry.administration.AdminOperationsService;
import com.stevenbuglione.registry.administration.SyncCredentialService;
import com.stevenbuglione.registry.audit.AuditLogService;
import com.stevenbuglione.registry.security.identity.AccessContext;
import com.stevenbuglione.registry.security.identity.RegistryIdentityService;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

class AdminControllerTest {

  private final RegistryIdentityService identities = mock(RegistryIdentityService.class);
  private final AdminDashboardService dashboards = mock(AdminDashboardService.class);
  private final AdminOperationsService operations = mock(AdminOperationsService.class);
  private final SyncCredentialService credentials = mock(SyncCredentialService.class);
  private final AuditLogService audit = mock(AuditLogService.class);
  private final Authentication authentication = mock(Authentication.class);
  private final AdminController controller =
      new AdminController(identities, dashboards, operations, credentials, audit);

  @Test
  void deniesEveryAdministrationSurfaceToNonAdministrators() {
    when(identities.accessContext(authentication))
        .thenReturn(new AccessContext("member-subject", Set.of("APM0000001"), false));

    assertDenied(() -> controller.dashboard(authentication));
    assertDenied(() -> controller.operations(authentication, 50));
    assertDenied(() -> controller.auditEvents(authentication, 50, null));
    assertDenied(() -> controller.syncCredentials(authentication));
    assertDenied(
        () ->
            controller.createSyncCredential(
                authentication,
                new AdminController.CreateSyncCredentialRequest("runner", "module", 30)));

    verifyNoInteractions(dashboards, operations, credentials, audit);
  }

  private static void assertDenied(Runnable operation) {
    assertThatThrownBy(operation::run)
        .isInstanceOf(AccessDeniedException.class)
        .hasMessageContaining("administrator");
  }
}
