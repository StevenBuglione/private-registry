package com.stevenbuglione.registry.catalog;

import com.stevenbuglione.registry.model.CatalogPackage;
import java.util.UUID;

record CatalogReadRow(
    UUID databaseId, CatalogPackage item, long downloadCount, double relevanceScore) {}
