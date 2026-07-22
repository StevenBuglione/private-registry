package com.stevenbuglione.registry.catalog;

import com.stevenbuglione.registry.model.CatalogPackage;
import com.stevenbuglione.registry.model.Governance;
import com.stevenbuglione.registry.model.PackageKind;
import com.stevenbuglione.registry.security.identity.AccessContext;
import org.jspecify.annotations.Nullable;

public interface CatalogService {

  CatalogPage<CatalogPackage> findPackages(AccessContext accessContext, CatalogQuery query);

  long countPackages(AccessContext accessContext, PackageKind kind);

  CatalogPackage getPackage(AccessContext accessContext, String id);

  CatalogPackage getPackage(AccessContext accessContext, String id, @Nullable String version);

  Governance getGovernance(AccessContext accessContext, String id);

  Governance getGovernance(AccessContext accessContext, String id, @Nullable String version);

  DocumentContent readDocument(AccessContext accessContext, String packageId, String path);

  DocumentContent readDocument(
      AccessContext accessContext, String packageId, @Nullable String version, String path);

  record DocumentContent(String content, String contentType) {}
}
