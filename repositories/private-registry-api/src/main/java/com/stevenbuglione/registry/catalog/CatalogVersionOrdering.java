package com.stevenbuglione.registry.catalog;

/** Shared deterministic ordering for active package versions. */
final class CatalogVersionOrdering {

  private static final String VALID_SEMVER =
      "^(0|[1-9][0-9]*)[.](0|[1-9][0-9]*)[.](0|[1-9][0-9]*)"
          + "(-[0-9A-Za-z-]+([.][0-9A-Za-z-]+)*)?"
          + "([+][0-9A-Za-z-]+([.][0-9A-Za-z-]+)*)?$";

  static final String NEWEST_FIRST =
      """
      CASE WHEN pv.version ~ '%s' THEN 1 ELSE 0 END DESC,
      CASE
          WHEN pv.version ~ '%s'
              THEN string_to_array(
                  split_part(split_part(pv.version, '+', 1), '-', 1),
                  '.')::numeric[]
          ELSE NULL
      END DESC NULLS LAST,
      pv.prerelease ASC,
      pv.version COLLATE "C" DESC,
      pv.published_at DESC,
      pv.id DESC
      """
          .formatted(VALID_SEMVER, VALID_SEMVER);

  private CatalogVersionOrdering() {}
}
