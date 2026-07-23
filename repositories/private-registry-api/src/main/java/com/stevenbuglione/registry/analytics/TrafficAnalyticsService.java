package com.stevenbuglione.registry.analytics;

import com.stevenbuglione.registry.security.identity.RegistryPrincipal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class TrafficAnalyticsService {

  private static final int MINIMUM_DAYS = 1;
  private static final int MAXIMUM_DAYS = 90;
  private static final int MINIMUM_VISITOR_LIMIT = 1;
  private static final int MAXIMUM_VISITOR_LIMIT = 100;
  private static final int RECENT_ACCESS_LIMIT = 50;
  private static final int RETENTION_DAYS = 180;

  private final JdbcClient jdbc;
  private final Clock clock;

  @Autowired
  public TrafficAnalyticsService(JdbcClient jdbc) {
    this(jdbc, Clock.systemUTC());
  }

  TrafficAnalyticsService(JdbcClient jdbc, Clock clock) {
    this.jdbc = jdbc;
    this.clock = clock;
  }

  public void recordPageView(RegistryPrincipal principal, String requestedPath) {
    var path = safePath(requestedPath);
    var occurredAt = timestamp(clock.instant());
    jdbc.sql(
            """
            INSERT INTO registry_page_views (subject, display_name, email, path, occurred_at)
            VALUES (:subject, :displayName, :email, :path, :occurredAt)
            """)
        .param("subject", principal.subject())
        .param("displayName", principal.displayName())
        .param("email", principal.email(), Types.VARCHAR)
        .param("path", path)
        .param("occurredAt", occurredAt)
        .update();
  }

  public TrafficReport report(int requestedDays, int requestedVisitorLimit) {
    var days = constrained(requestedDays, MINIMUM_DAYS, MAXIMUM_DAYS, "days");
    var visitorLimit =
        constrained(
            requestedVisitorLimit, MINIMUM_VISITOR_LIMIT, MAXIMUM_VISITOR_LIMIT, "visitor limit");
    var today = LocalDate.now(clock);
    var since = today.minusDays(days - 1L).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
    return new TrafficReport(
        clock.instant(),
        days,
        summary(since, today),
        dailyTraffic(days),
        topRoutes(since),
        visitors(since, visitorLimit),
        recentAccess(since));
  }

  @Scheduled(cron = "${registry.analytics.cleanup-cron:0 11 3 * * *}", zone = "UTC")
  public void purgeExpiredPageViews() {
    jdbc.sql("DELETE FROM registry_page_views WHERE occurred_at < :cutoff")
        .param(
            "cutoff", timestamp(clock.instant().minus(java.time.Duration.ofDays(RETENTION_DAYS))))
        .update();
  }

  private TrafficSummary summary(Instant since, LocalDate today) {
    return jdbc.sql(
            """
            SELECT COUNT(*) AS page_views,
                   COUNT(DISTINCT subject) AS unique_visitors,
                   COUNT(*) FILTER (
                       WHERE occurred_at >= :todayStart) AS page_views_today,
                   COUNT(DISTINCT subject) FILTER (
                       WHERE occurred_at >= :todayStart) AS visitors_today
              FROM registry_page_views
             WHERE occurred_at >= :since
            """)
        .param("todayStart", timestamp(today.atStartOfDay(java.time.ZoneOffset.UTC).toInstant()))
        .param("since", timestamp(since))
        .query(
            (resultSet, rowNumber) ->
                new TrafficSummary(
                    resultSet.getLong("page_views"),
                    resultSet.getLong("unique_visitors"),
                    resultSet.getLong("page_views_today"),
                    resultSet.getLong("visitors_today")))
        .single();
  }

  private List<DailyTraffic> dailyTraffic(int days) {
    return jdbc.sql(
            """
            WITH report_days AS (
                SELECT generate_series(
                    current_date - (:days - 1),
                    current_date,
                    interval '1 day')::date AS day
            )
            SELECT report_days.day,
                   COUNT(page_views.id) AS page_views,
                   COUNT(DISTINCT page_views.subject) AS unique_visitors
              FROM report_days
              LEFT JOIN registry_page_views page_views
                ON (page_views.occurred_at AT TIME ZONE 'UTC')::date = report_days.day
             GROUP BY report_days.day
             ORDER BY report_days.day
            """)
        .param("days", days)
        .query(
            (resultSet, rowNumber) ->
                new DailyTraffic(
                    resultSet.getObject("day", LocalDate.class),
                    resultSet.getLong("page_views"),
                    resultSet.getLong("unique_visitors")))
        .list();
  }

  private List<RouteTraffic> topRoutes(Instant since) {
    return jdbc.sql(
            """
            SELECT path,
                   COUNT(*) AS page_views,
                   COUNT(DISTINCT subject) AS unique_visitors,
                   MAX(occurred_at) AS last_viewed_at
              FROM registry_page_views
             WHERE occurred_at >= :since
             GROUP BY path
             ORDER BY page_views DESC, unique_visitors DESC, path
             LIMIT 15
            """)
        .param("since", timestamp(since))
        .query(
            (resultSet, rowNumber) ->
                new RouteTraffic(
                    resultSet.getString("path"),
                    resultSet.getLong("page_views"),
                    resultSet.getLong("unique_visitors"),
                    resultSet.getTimestamp("last_viewed_at").toInstant()))
        .list();
  }

  private List<VisitorTraffic> visitors(Instant since, int limit) {
    return jdbc.sql(
            """
            WITH scoped AS (
                SELECT *
                  FROM registry_page_views
                 WHERE occurred_at >= :since
            ),
            latest AS (
                SELECT DISTINCT ON (subject)
                       subject,
                       display_name,
                       email,
                       path AS last_path
                  FROM scoped
                 ORDER BY subject, occurred_at DESC, id DESC
            )
            SELECT latest.subject,
                   latest.display_name,
                   latest.email,
                   latest.last_path,
                   COUNT(scoped.id) AS page_views,
                   MIN(scoped.occurred_at) AS first_seen_at,
                   MAX(scoped.occurred_at) AS last_seen_at
              FROM latest
              JOIN scoped USING (subject)
             GROUP BY latest.subject,
                      latest.display_name,
                      latest.email,
                      latest.last_path
             ORDER BY last_seen_at DESC
             LIMIT :limit
            """)
        .param("since", timestamp(since))
        .param("limit", limit)
        .query(TrafficAnalyticsService::visitor)
        .list();
  }

  private List<RecentAccess> recentAccess(Instant since) {
    return jdbc.sql(
            """
            SELECT subject,
                   display_name,
                   email,
                   path,
                   occurred_at
              FROM registry_page_views
             WHERE occurred_at >= :since
             ORDER BY occurred_at DESC, id DESC
             LIMIT :limit
            """)
        .param("since", timestamp(since))
        .param("limit", RECENT_ACCESS_LIMIT)
        .query(
            (resultSet, rowNumber) ->
                new RecentAccess(
                    resultSet.getString("subject"),
                    resultSet.getString("display_name"),
                    nullableString(resultSet, "email"),
                    resultSet.getString("path"),
                    resultSet.getTimestamp("occurred_at").toInstant()))
        .list();
  }

  private static VisitorTraffic visitor(ResultSet resultSet, int rowNumber) throws SQLException {
    return new VisitorTraffic(
        resultSet.getString("subject"),
        resultSet.getString("display_name"),
        nullableString(resultSet, "email"),
        resultSet.getLong("page_views"),
        resultSet.getTimestamp("first_seen_at").toInstant(),
        resultSet.getTimestamp("last_seen_at").toInstant(),
        resultSet.getString("last_path"));
  }

  private static @Nullable String nullableString(ResultSet resultSet, String column)
      throws SQLException {
    return resultSet.getString(column);
  }

  private static java.sql.Timestamp timestamp(Instant value) {
    return java.sql.Timestamp.from(value);
  }

  static String safePath(String requestedPath) {
    var path = requestedPath.trim();
    if (path.isEmpty()
        || path.length() > 512
        || path.charAt(0) != '/'
        || path.indexOf('?') >= 0
        || path.indexOf('#') >= 0
        || path.indexOf('\\') >= 0
        || path.chars().anyMatch(Character::isISOControl)) {
      throw new IllegalArgumentException("A safe Registry-relative page path is required");
    }
    return path;
  }

  private static int constrained(int value, int minimum, int maximum, String name) {
    if (value < minimum || value > maximum) {
      throw new IllegalArgumentException(name + " must be between " + minimum + " and " + maximum);
    }
    return value;
  }

  public record TrafficReport(
      Instant generatedAt,
      int days,
      TrafficSummary summary,
      List<DailyTraffic> daily,
      List<RouteTraffic> topRoutes,
      List<VisitorTraffic> visitors,
      List<RecentAccess> recentAccess) {}

  public record TrafficSummary(
      long pageViews, long uniqueVisitors, long pageViewsToday, long visitorsToday) {}

  public record DailyTraffic(LocalDate day, long pageViews, long uniqueVisitors) {}

  public record RouteTraffic(
      String path, long pageViews, long uniqueVisitors, Instant lastViewedAt) {}

  public record VisitorTraffic(
      String subject,
      String displayName,
      @Nullable String email,
      long pageViews,
      Instant firstSeenAt,
      Instant lastSeenAt,
      String lastPath) {}

  public record RecentAccess(
      String subject,
      String displayName,
      @Nullable String email,
      String path,
      Instant occurredAt) {}
}
