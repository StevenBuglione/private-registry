package com.stevenbuglione.registry.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stevenbuglione.registry.security.identity.RegistryPrincipal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.flywaydb.core.Flyway;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class TrafficAnalyticsIntegrationTest {

  private static final Instant NOW = Instant.parse("2026-07-23T14:30:00Z");

  @Container
  private static final PostgreSQLContainer POSTGRESQL =
      new PostgreSQLContainer("postgres:18-alpine")
          .withDatabaseName("registry_traffic_test")
          .withUsername("registry")
          .withPassword("registry");

  private static JdbcClient jdbc;

  @BeforeAll
  static void migrateDatabase() {
    var dataSource = new PGSimpleDataSource();
    dataSource.setUrl(POSTGRESQL.getJdbcUrl());
    dataSource.setUser(POSTGRESQL.getUsername());
    dataSource.setPassword(POSTGRESQL.getPassword());
    Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .cleanDisabled(true)
        .load()
        .migrate();
    jdbc = JdbcClient.create(dataSource);
  }

  @BeforeEach
  void resetTraffic() {
    jdbc.sql("TRUNCATE TABLE registry_page_views RESTART IDENTITY").update();
  }

  @Test
  void recordsAndReportsAuthenticatedTrafficWithoutQueryStringsOrTokens() {
    var initial =
        new TrafficAnalyticsService(
            jdbc, Clock.fixed(NOW.minus(Duration.ofHours(1)), ZoneOffset.UTC));
    initial.recordPageView(principal("user-1", "Ada Lovelace", "ada@example.test"), "/providers");
    initial.recordPageView(principal("user-2", "Grace Hopper", "grace@example.test"), "/modules");
    var current = new TrafficAnalyticsService(jdbc, Clock.fixed(NOW, ZoneOffset.UTC));
    current.recordPageView(principal("user-1", "Ada Lovelace", "ada@example.test"), "/modules");

    var report = current.report(30, 50);

    assertThat(report.summary().pageViews()).isEqualTo(3);
    assertThat(report.summary().uniqueVisitors()).isEqualTo(2);
    assertThat(report.summary().pageViewsToday()).isEqualTo(3);
    assertThat(report.summary().visitorsToday()).isEqualTo(2);
    assertThat(report.daily()).hasSize(30);
    assertThat(report.daily().getLast().pageViews()).isEqualTo(3);
    assertThat(report.topRoutes())
        .extracting(TrafficAnalyticsService.RouteTraffic::path)
        .containsExactly("/modules", "/providers");
    assertThat(report.visitors())
        .extracting(TrafficAnalyticsService.VisitorTraffic::displayName)
        .containsExactly("Ada Lovelace", "Grace Hopper");
    assertThat(report.visitors().getFirst().lastPath()).isEqualTo("/modules");
    assertThat(report.recentAccess())
        .extracting(TrafficAnalyticsService.RecentAccess::path)
        .containsExactly("/modules", "/modules", "/providers");
  }

  @Test
  void rejectsUnsafePathsAndPurgesExpiredEvents() {
    var service = new TrafficAnalyticsService(jdbc, Clock.fixed(NOW, ZoneOffset.UTC));
    var principal = principal("user-1", "Ada Lovelace", null);
    service.recordPageView(principal, "/");

    assertThatThrownBy(() -> service.recordPageView(principal, "providers"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.recordPageView(principal, "/browse?q=secret"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.report(0, 50)).isInstanceOf(IllegalArgumentException.class);

    jdbc.sql(
            """
            INSERT INTO registry_page_views (
                subject, display_name, path, occurred_at)
            VALUES ('old-user', 'Old User', '/', :occurredAt)
            """)
        .param("occurredAt", java.sql.Timestamp.from(NOW.minus(Duration.ofDays(181))))
        .update();
    service.purgeExpiredPageViews();

    assertThat(jdbc.sql("SELECT COUNT(*) FROM registry_page_views").query(Long.class).single())
        .isOne();
  }

  private static RegistryPrincipal principal(
      String subject, String displayName, @Nullable String email) {
    return new RegistryPrincipal(subject, displayName, email, Set.of("registry-user"), List.of());
  }
}
