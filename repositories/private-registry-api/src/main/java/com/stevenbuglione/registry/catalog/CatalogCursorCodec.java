package com.stevenbuglione.registry.catalog;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.Map;

final class CatalogCursorCodec {

  private CatalogCursorCodec() {}

  static String encode(CatalogQuery query, CatalogReadRow row) {
    var value =
        switch (query.sort()) {
          case "name" -> "";
          case "updated" -> row.item().updatedAt().toString();
          case "relevance" -> Double.toString(row.relevanceScore());
          case "downloads" -> Long.toString(row.downloadCount());
          default -> throw new IllegalArgumentException("Unsupported catalog sort");
        };
    var plain = String.join("\u001f", query.sort(), value, row.item().id());
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(plain.getBytes(StandardCharsets.UTF_8));
  }

  static void append(StringBuilder sql, Map<String, Object> parameters, CatalogQuery query) {
    if (query.cursor() == null) {
      return;
    }
    var cursor = decode(query.cursor());
    if (!query.sort().equals(cursor.sort())) {
      throw new IllegalArgumentException("Cursor does not match the requested sort");
    }
    parameters.put("cursorId", cursor.publicId());
    switch (query.sort()) {
      case "name" ->
          sql.append(" AND ").append(CatalogQueryBuilder.PUBLIC_ID).append(" > :cursorId");
      case "updated" -> appendUpdated(sql, parameters, cursor);
      case "relevance" -> appendRelevance(sql, parameters, cursor);
      case "downloads" -> appendDownloads(sql, parameters, cursor);
      default -> throw new IllegalArgumentException("Unsupported catalog sort");
    }
  }

  private static void appendUpdated(
      StringBuilder sql, Map<String, Object> parameters, DecodedCursor cursor) {
    try {
      parameters.put("cursorUpdated", Instant.parse(cursor.value()));
    } catch (DateTimeParseException exception) {
      throw new IllegalArgumentException("Invalid catalog cursor", exception);
    }
    sql.append(" AND (p.updated_at < :cursorUpdated OR (p.updated_at = :cursorUpdated AND ")
        .append(CatalogQueryBuilder.PUBLIC_ID)
        .append(" > :cursorId))");
  }

  private static void appendRelevance(
      StringBuilder sql, Map<String, Object> parameters, DecodedCursor cursor) {
    final double score;
    try {
      score = Double.parseDouble(cursor.value());
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException("Invalid catalog cursor", exception);
    }
    if (!Double.isFinite(score)) {
      throw new IllegalArgumentException("Invalid catalog cursor");
    }
    parameters.put("cursorRelevance", score);
    sql.append(" AND (")
        .append(CatalogQueryBuilder.RELEVANCE_SCORE)
        .append(" < :cursorRelevance OR (")
        .append(CatalogQueryBuilder.RELEVANCE_SCORE)
        .append(" = :cursorRelevance AND ")
        .append(CatalogQueryBuilder.PUBLIC_ID)
        .append(" > :cursorId))");
  }

  private static void appendDownloads(
      StringBuilder sql, Map<String, Object> parameters, DecodedCursor cursor) {
    try {
      parameters.put("cursorDownloads", Long.parseLong(cursor.value()));
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException("Invalid catalog cursor", exception);
    }
    sql.append(" AND (")
        .append(CatalogQueryBuilder.DOWNLOAD_COUNT)
        .append(" < :cursorDownloads OR (")
        .append(CatalogQueryBuilder.DOWNLOAD_COUNT)
        .append(" = :cursorDownloads AND ")
        .append(CatalogQueryBuilder.PUBLIC_ID)
        .append(" > :cursorId))");
  }

  private static DecodedCursor decode(String encoded) {
    try {
      var plain = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
      var segments = plain.split("\u001f", -1);
      if (segments.length != 3 || segments[0].isBlank() || segments[2].isBlank()) {
        throw new IllegalArgumentException("Invalid catalog cursor");
      }
      return new DecodedCursor(segments[0], segments[1], segments[2]);
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("Invalid catalog cursor", exception);
    }
  }

  private record DecodedCursor(String sort, String value, String publicId) {}
}
