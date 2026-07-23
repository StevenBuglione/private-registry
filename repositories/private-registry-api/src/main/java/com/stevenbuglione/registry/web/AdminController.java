package com.stevenbuglione.registry.web;

import com.stevenbuglione.registry.administration.AdminDashboardService;
import com.stevenbuglione.registry.administration.AdminOperationsService;
import com.stevenbuglione.registry.administration.SyncCredentialService;
import com.stevenbuglione.registry.audit.AuditLogService;
import com.stevenbuglione.registry.security.identity.RegistryIdentityService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

  private final RegistryIdentityService identities;
  private final AdminDashboardService dashboards;
  private final AdminOperationsService operations;
  private final SyncCredentialService credentials;
  private final AuditLogService audit;

  public AdminController(
      RegistryIdentityService identities,
      AdminDashboardService dashboards,
      AdminOperationsService operations,
      SyncCredentialService credentials,
      AuditLogService audit) {
    this.identities = identities;
    this.dashboards = dashboards;
    this.operations = operations;
    this.credentials = credentials;
    this.audit = audit;
  }

  @GetMapping("/dashboard")
  public AdminDashboardService.Dashboard dashboard() {
    return dashboards.dashboard();
  }

  @GetMapping("/operations")
  public List<AdminOperationsService.OperationalEvent> operations(
      @RequestParam(defaultValue = "50") int limit) {
    return operations.recent(limit);
  }

  @GetMapping("/audit-events")
  public List<AuditLogService.AuditEvent> auditEvents(
      @RequestParam(defaultValue = "50") int limit,
      @RequestParam(required = false) @Nullable Instant before) {
    return audit.recent(limit, before);
  }

  @GetMapping("/sync-credentials")
  public List<SyncCredentialService.CredentialView> syncCredentials() {
    return credentials.list();
  }

  @PostMapping("/sync-credentials")
  public SyncCredentialService.CreatedCredential createSyncCredential(
      Authentication authentication, @RequestBody CreateSyncCredentialRequest request) {
    var context = identities.accessContext(authentication);
    return credentials.create(request.toCommand(), context.subject());
  }

  @DeleteMapping("/sync-credentials/{id}")
  public SyncCredentialService.CredentialView revokeSyncCredential(
      Authentication authentication, @PathVariable UUID id) {
    var context = identities.accessContext(authentication);
    try {
      return credentials.revoke(id, context.subject());
    } catch (SyncCredentialService.CredentialNotFoundException exception) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
    }
  }

  record CreateSyncCredentialRequest(String name, String scope, int expiresInDays) {

    SyncCredentialService.CreateCommand toCommand() {
      return new SyncCredentialService.CreateCommand(name, scope, expiresInDays);
    }
  }
}
