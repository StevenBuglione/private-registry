package com.stevenbuglione.registry.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stevenbuglione.registry.administration.AdminDashboardService;
import com.stevenbuglione.registry.administration.AdminOperationsService;
import com.stevenbuglione.registry.administration.SyncCredentialService;
import com.stevenbuglione.registry.audit.AuditLogService;
import com.stevenbuglione.registry.security.identity.AccessContext;
import com.stevenbuglione.registry.security.identity.RegistryIdentityService;
import java.util.Set;
import org.junit.jupiter.api.Test;
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
  void delegatesReadOnlyAdministrationAfterTheHttpSecurityBoundary() {
    var expected = mock(AdminDashboardService.Dashboard.class);
    when(dashboards.dashboard()).thenReturn(expected);

    assertThat(controller.dashboard()).isSameAs(expected);
    controller.operations(25);
    controller.auditEvents(30, null);
    controller.syncCredentials();

    verify(dashboards).dashboard();
    verify(operations).recent(25);
    verify(audit).recent(30, null);
    verify(credentials).list();
  }

  @Test
  void usesTheCentrallyAuthorizedAdministratorAsCredentialActor() {
    when(identities.accessContext(authentication))
        .thenReturn(new AccessContext("admin-subject", Set.of(), true));
    var request = new AdminController.CreateSyncCredentialRequest("runner", "module", 30);

    controller.createSyncCredential(authentication, request);

    verify(credentials).create(any(), eq("admin-subject"));
  }
}
