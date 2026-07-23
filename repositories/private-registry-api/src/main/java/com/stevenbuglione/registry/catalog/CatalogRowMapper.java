package com.stevenbuglione.registry.catalog;

import com.stevenbuglione.registry.model.CatalogPackage;
import com.stevenbuglione.registry.model.PackageKind;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

final class CatalogRowMapper {

  private CatalogRowMapper() {}

  static CatalogReadRow packageRow(ResultSet resultSet, int rowNumber) throws SQLException {
    var ownerIds = resultSet.getString("owner_ids");
    var owners = ownerIds.isBlank() ? List.<String>of() : Arrays.asList(ownerIds.split(",", -1));
    var item =
        new CatalogPackage(
            resultSet.getString("public_id"),
            Objects.requireNonNull(PackageKind.from(resultSet.getString("kind"))),
            resultSet.getString("namespace"),
            resultSet.getString("name"),
            resultSet.getString("target"),
            resultSet.getString("title"),
            resultSet.getString("description"),
            resultSet.getString("latest_version"),
            List.copyOf(owners),
            resultSet.getString("support_level"),
            resultSet.getString("lifecycle"),
            resultSet.getString("verification"),
            resultSet.getString("registry_tier"),
            resultSet.getString("risk_tier"),
            resultSet.getString("source_address"),
            resultSet.getTimestamp("updated_at").toInstant(),
            List.of(),
            List.of());
    return new CatalogReadRow(
        resultSet.getObject("database_id", UUID.class),
        item,
        resultSet.getLong("download_count"),
        resultSet.getDouble("relevance_score"));
  }
}
