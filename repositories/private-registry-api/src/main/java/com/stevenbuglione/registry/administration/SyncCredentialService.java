package com.stevenbuglione.registry.administration;

import com.stevenbuglione.registry.audit.AuditLogService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SyncCredentialService {

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();
  private static final byte[] INVALID_SECRET_HASH = new byte[32];
  private static final int SECRET_BYTES = 32;
  private static final int MAXIMUM_LIFETIME_DAYS = 365;

  private final JdbcClient jdbc;
  private final AuditLogService audit;
  private final Clock clock;

  @Autowired
  public SyncCredentialService(JdbcClient jdbc, AuditLogService audit) {
    this(jdbc, audit, Clock.systemUTC());
  }

  SyncCredentialService(JdbcClient jdbc, AuditLogService audit, Clock clock) {
    this.jdbc = jdbc;
    this.audit = audit;
    this.clock = clock;
  }

  @Transactional
  public CreatedCredential create(CreateCommand command, String actorSubject) {
    var name = validateName(command.name());
    var scope = Scope.parse(command.scope());
    var lifetimeDays = validateLifetime(command.expiresInDays());
    var id = UUID.randomUUID();
    var secretBytes = new byte[SECRET_BYTES];
    SECURE_RANDOM.nextBytes(secretBytes);
    var token =
        "rgs." + id + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes);
    var expiresAt = clock.instant().plus(lifetimeDays, ChronoUnit.DAYS);
    var credential =
        jdbc.sql(
                """
                INSERT INTO registry_sync_credentials (
                    id, name, scope, secret_hash, created_by, expires_at)
                VALUES (
                    :id, :name, :scope, :secretHash, :createdBy, :expiresAt)
                RETURNING id,
                          name,
                          scope,
                          created_by,
                          created_at,
                          expires_at,
                          revoked_at,
                          revoked_by,
                          last_used_at,
                          use_count
                """)
            .param("id", id)
            .param("name", name)
            .param("scope", scope.value())
            .param("secretHash", sha256(secretBytes))
            .param("createdBy", actorSubject)
            .param("expiresAt", Timestamp.from(expiresAt))
            .query(this::map)
            .single();
    audit.record(
        new AuditLogService.AuditEntry(
            "user",
            actorSubject,
            "registry.sync_credential.created",
            "sync_credential",
            id.toString(),
            Map.of(
                "name", credential.name(),
                "scope", credential.scope().value(),
                "expires_at", credential.expiresAt().toString())));
    return new CreatedCredential(credential, token);
  }

  public List<CredentialView> list() {
    return jdbc.sql(
            """
            SELECT id,
                   name,
                   scope,
                   created_by,
                   created_at,
                   expires_at,
                   revoked_at,
                   revoked_by,
                   last_used_at,
                   use_count
              FROM registry_sync_credentials
             ORDER BY created_at DESC
            """)
        .query(this::map)
        .list();
  }

  @Transactional
  public CredentialView revoke(UUID id, String actorSubject) {
    var credential =
        jdbc.sql(
                """
                UPDATE registry_sync_credentials
                   SET revoked_at = COALESCE(revoked_at, now()),
                       revoked_by = COALESCE(revoked_by, :revokedBy)
                 WHERE id = :id
                RETURNING id,
                          name,
                          scope,
                          created_by,
                          created_at,
                          expires_at,
                          revoked_at,
                          revoked_by,
                          last_used_at,
                          use_count
                """)
            .param("id", id)
            .param("revokedBy", actorSubject)
            .query(this::map)
            .optional()
            .orElseThrow(() -> new CredentialNotFoundException(id));
    audit.record(
        new AuditLogService.AuditEntry(
            "user",
            actorSubject,
            "registry.sync_credential.revoked",
            "sync_credential",
            id.toString(),
            Map.of("name", credential.name(), "scope", credential.scope().value())));
    return credential;
  }

  @Transactional
  public AuthenticatedCredential authenticate(String bearerToken) {
    var parsed = ParsedToken.parse(bearerToken);
    var stored =
        parsed
            .flatMap(
                token ->
                    jdbc.sql(
                            """
                            SELECT id, scope, secret_hash, expires_at, revoked_at
                              FROM registry_sync_credentials
                             WHERE id = :id
                            """)
                        .param("id", token.id())
                        .query(
                            (resultSet, rowNumber) ->
                                new StoredCredential(
                                    resultSet.getObject("id", UUID.class),
                                    Scope.parse(resultSet.getString("scope")),
                                    resultSet.getBytes("secret_hash"),
                                    resultSet.getTimestamp("expires_at").toInstant(),
                                    nullableInstant(resultSet, "revoked_at")))
                        .optional())
            .orElse(null);
    var actualHash =
        parsed
            .map(ParsedToken::secret)
            .map(SyncCredentialService::sha256)
            .orElse(INVALID_SECRET_HASH);
    var expectedHash = stored == null ? INVALID_SECRET_HASH : stored.secretHash();
    var validSecret = MessageDigest.isEqual(expectedHash, actualHash);
    if (!validSecret
        || stored == null
        || stored.revokedAt() != null
        || !stored.expiresAt().isAfter(clock.instant())) {
      throw new CredentialAuthenticationException();
    }
    jdbc.sql(
            """
            UPDATE registry_sync_credentials
               SET last_used_at = now(),
                   use_count = use_count + 1
             WHERE id = :id
            """)
        .param("id", stored.id())
        .update();
    return new AuthenticatedCredential(stored.id(), stored.scope());
  }

  private CredentialView map(ResultSet resultSet, int rowNumber) throws SQLException {
    var id = resultSet.getObject("id", UUID.class);
    var revokedAt = nullableInstant(resultSet, "revoked_at");
    var expiresAt = resultSet.getTimestamp("expires_at").toInstant();
    return new CredentialView(
        id,
        resultSet.getString("name"),
        Scope.parse(resultSet.getString("scope")),
        "rgs." + id.toString().substring(0, 8),
        resultSet.getString("created_by"),
        resultSet.getTimestamp("created_at").toInstant(),
        expiresAt,
        revokedAt,
        resultSet.getString("revoked_by"),
        nullableInstant(resultSet, "last_used_at"),
        resultSet.getLong("use_count"),
        status(revokedAt, expiresAt));
  }

  private Status status(@Nullable Instant revokedAt, Instant expiresAt) {
    if (revokedAt != null) {
      return Status.REVOKED;
    }
    return expiresAt.isAfter(clock.instant()) ? Status.ACTIVE : Status.EXPIRED;
  }

  private static @Nullable Instant nullableInstant(ResultSet resultSet, String column)
      throws SQLException {
    var timestamp = resultSet.getTimestamp(column);
    return timestamp == null ? null : timestamp.toInstant();
  }

  private static String validateName(String value) {
    var name = value.trim();
    if (name.length() < 3 || name.length() > 80) {
      throw new IllegalArgumentException("Credential name must contain 3 to 80 characters");
    }
    return name;
  }

  private static int validateLifetime(int value) {
    if (value < 1 || value > MAXIMUM_LIFETIME_DAYS) {
      throw new IllegalArgumentException("Credential lifetime must be between 1 and 365 days");
    }
    return value;
  }

  private static byte[] sha256(byte[] value) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(value);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  public enum Scope {
    MODULE,
    PROVIDER,
    ALL;

    public static Scope parse(String value) {
      return switch (value.trim().toLowerCase(Locale.ROOT)) {
        case "module" -> MODULE;
        case "provider" -> PROVIDER;
        case "all" -> ALL;
        default ->
            throw new IllegalArgumentException("Credential scope must be module, provider, or all");
      };
    }

    public String value() {
      return name().toLowerCase(Locale.ROOT);
    }

    public boolean allows(String packageKind) {
      return this == ALL || value().equals(packageKind);
    }
  }

  public enum Status {
    ACTIVE,
    EXPIRED,
    REVOKED
  }

  public record CreateCommand(String name, String scope, int expiresInDays) {}

  public record CreatedCredential(CredentialView credential, String token) {}

  public record CredentialView(
      UUID id,
      String name,
      Scope scope,
      String keyPrefix,
      String createdBy,
      Instant createdAt,
      Instant expiresAt,
      @Nullable Instant revokedAt,
      @Nullable String revokedBy,
      @Nullable Instant lastUsedAt,
      long useCount,
      Status status) {}

  public record AuthenticatedCredential(UUID id, Scope scope) {}

  private static final class StoredCredential {

    private final UUID id;
    private final Scope scope;
    private final byte[] secretHash;
    private final Instant expiresAt;
    private final @Nullable Instant revokedAt;

    private StoredCredential(
        UUID id, Scope scope, byte[] secretHash, Instant expiresAt, @Nullable Instant revokedAt) {
      this.id = id;
      this.scope = scope;
      this.secretHash = secretHash.clone();
      this.expiresAt = expiresAt;
      this.revokedAt = revokedAt;
    }

    private UUID id() {
      return id;
    }

    private Scope scope() {
      return scope;
    }

    private byte[] secretHash() {
      return secretHash.clone();
    }

    private Instant expiresAt() {
      return expiresAt;
    }

    private @Nullable Instant revokedAt() {
      return revokedAt;
    }
  }

  private static final class ParsedToken {

    private final UUID id;
    private final byte[] secret;

    private ParsedToken(UUID id, byte[] secret) {
      this.id = id;
      this.secret = secret.clone();
    }

    private UUID id() {
      return id;
    }

    private byte[] secret() {
      return secret.clone();
    }

    private static Optional<ParsedToken> parse(String value) {
      if (!value.startsWith("Bearer ")) {
        return Optional.empty();
      }
      var token = value.substring("Bearer ".length()).trim();
      var parts = token.split("\\.", -1);
      if (parts.length != 3 || !"rgs".equals(parts[0])) {
        return Optional.empty();
      }
      try {
        var id = UUID.fromString(parts[1]);
        var secret = Base64.getUrlDecoder().decode(parts[2].getBytes(StandardCharsets.US_ASCII));
        return secret.length == SECRET_BYTES
            ? Optional.of(new ParsedToken(id, secret))
            : Optional.empty();
      } catch (IllegalArgumentException exception) {
        return Optional.empty();
      }
    }
  }

  public static final class CredentialAuthenticationException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    CredentialAuthenticationException() {
      super("The sync credential is invalid, expired, or revoked");
    }
  }

  public static final class CredentialNotFoundException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    CredentialNotFoundException(UUID id) {
      super("Sync credential " + id + " was not found");
    }
  }
}
