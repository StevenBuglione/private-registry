package com.stevenbuglione.registry.web;

import com.stevenbuglione.registry.administration.SyncCredentialService;
import com.stevenbuglione.registry.administration.SyncTriggerService;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/sync/artifacts")
public class SyncTriggerController {

  private final SyncTriggerService sync;

  public SyncTriggerController(SyncTriggerService sync) {
    this.sync = sync;
  }

  @PostMapping
  public ResponseEntity<SyncTriggerService.TriggerReceipt> trigger(
      @RequestHeader("Authorization") String authorization,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @RequestBody TriggerSyncRequest request) {
    try {
      var command =
          new SyncTriggerService.TriggerCommand(
              idempotencyKey,
              request.kind(),
              request.repository(),
              request.path(),
              request.action() == null ? SyncTriggerService.Action.DEPLOYED : request.action());
      var receipt = sync.trigger(authorization, command);
      return ResponseEntity.accepted().body(receipt);
    } catch (SyncCredentialService.CredentialAuthenticationException exception) {
      throw new ResponseStatusException(
          HttpStatus.UNAUTHORIZED,
          "The sync credential is invalid, expired, or revoked",
          exception);
    } catch (SyncTriggerService.CredentialScopeException exception) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
    } catch (SyncTriggerService.SyncUnavailableException exception) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage(), exception);
    }
  }

  record TriggerSyncRequest(
      String kind, String repository, String path, SyncTriggerService.@Nullable Action action) {}
}
