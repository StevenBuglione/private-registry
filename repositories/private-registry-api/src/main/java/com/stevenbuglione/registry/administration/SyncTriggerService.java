package com.stevenbuglione.registry.administration;

import com.stevenbuglione.registry.audit.AuditLogService;
import com.stevenbuglione.registry.eventing.CatalogArtifactChanged;
import com.stevenbuglione.registry.eventing.CatalogEventPublisher;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class SyncTriggerService {

  private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9._:-]{8,128}");
  private static final Set<String> PACKAGE_KINDS = Set.of("module", "provider");
  private static final Map<String, String> REPOSITORIES =
      Map.of(
          "module", "iac-module-release-local",
          "provider", "iac-provider-release-local");

  private final SyncCredentialService credentials;
  private final ObjectProvider<CatalogEventPublisher> publisher;
  private final AuditLogService audit;

  public SyncTriggerService(
      SyncCredentialService credentials,
      ObjectProvider<CatalogEventPublisher> publisher,
      AuditLogService audit) {
    this.credentials = credentials;
    this.publisher = publisher;
    this.audit = audit;
  }

  public TriggerReceipt trigger(String authorization, TriggerCommand command) {
    var kind = command.packageKind().trim().toLowerCase(Locale.ROOT);
    validateIdempotencyKey(command.idempotencyKey());
    validateRepository(kind, command.repository());
    validatePath(command.path());
    var credential = credentials.authenticate(authorization);
    if (!credential.scope().allows(kind)) {
      throw new CredentialScopeException();
    }
    var eventPublisher = publisher.getIfAvailable();
    if (eventPublisher == null) {
      throw new SyncUnavailableException();
    }
    var eventId = eventId(credential.id().toString(), command.idempotencyKey());
    var event =
        new CatalogArtifactChanged(
            1,
            eventId,
            command.action().catalogAction(),
            "github-runner",
            credential.id().toString(),
            command.repository(),
            command.path(),
            Instant.now(),
            command.idempotencyKey(),
            Map.of("package_kind", kind, "trigger", "github-runner"));
    var publication = eventPublisher.publish(event);
    audit.record(
        new AuditLogService.AuditEntry(
            "api_key",
            credential.id().toString(),
            "registry.sync.triggered",
            "artifact",
            command.repository() + "/" + command.path(),
            Map.of(
                "event_id",
                eventId,
                "package_kind",
                kind,
                "action",
                command.action().name().toLowerCase(Locale.ROOT),
                "publication_id",
                publication.eventId())));
    return new TriggerReceipt(eventId, publication.eventId(), "accepted");
  }

  private static void validateIdempotencyKey(String idempotencyKey) {
    if (!IDEMPOTENCY_KEY.matcher(idempotencyKey).matches()) {
      throw new IllegalArgumentException("Idempotency-Key must contain 8 to 128 safe characters");
    }
  }

  private static void validateRepository(String kind, String repository) {
    if (!PACKAGE_KINDS.contains(kind)) {
      throw new IllegalArgumentException("Package kind must be module or provider");
    }
    var expectedRepository = REPOSITORIES.get(kind);
    if (expectedRepository == null || !expectedRepository.equals(repository)) {
      throw new IllegalArgumentException("Repository does not match the package kind");
    }
  }

  private static void validatePath(String path) {
    if (path.isBlank()
        || path.length() > 1_024
        || path.startsWith("/")
        || path.contains("..")
        || path.contains("\\")) {
      throw new IllegalArgumentException("Artifact path must be a safe relative path");
    }
  }

  private static String eventId(String credentialId, String idempotencyKey) {
    try {
      var digest =
          MessageDigest.getInstance("SHA-256")
              .digest((credentialId + ":" + idempotencyKey).getBytes(StandardCharsets.UTF_8));
      return "runner-" + HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  public record TriggerReceipt(String eventId, String publicationId, String status) {}

  public record TriggerCommand(
      String idempotencyKey, String packageKind, String repository, String path, Action action) {}

  public enum Action {
    DEPLOYED(CatalogArtifactChanged.Action.DEPLOYED),
    PROPERTIES_CHANGED(CatalogArtifactChanged.Action.PROPERTIES_CHANGED),
    DELETED(CatalogArtifactChanged.Action.DELETED),
    MOVED(CatalogArtifactChanged.Action.MOVED),
    COPIED(CatalogArtifactChanged.Action.COPIED);

    private final CatalogArtifactChanged.Action catalogAction;

    Action(CatalogArtifactChanged.Action catalogAction) {
      this.catalogAction = catalogAction;
    }

    CatalogArtifactChanged.Action catalogAction() {
      return catalogAction;
    }
  }

  public static final class CredentialScopeException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    CredentialScopeException() {
      super("The sync credential does not permit this package kind");
    }
  }

  public static final class SyncUnavailableException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    SyncUnavailableException() {
      super("Catalog event ingestion is not enabled");
    }
  }
}
