package com.stevenbuglione.registry.catalog;

import com.stevenbuglione.registry.model.CatalogPackage;
import com.stevenbuglione.registry.model.Governance;
import com.stevenbuglione.registry.model.PackageKind;
import com.stevenbuglione.registry.security.identity.AccessContext;

public interface CatalogService {

    CatalogPage<CatalogPackage> findPackages(AccessContext accessContext, CatalogQuery query);

    long countPackages(AccessContext accessContext, PackageKind kind);

    CatalogPackage getPackage(AccessContext accessContext, String id);

    CatalogPackage getPackage(AccessContext accessContext, String id, String version);

    Governance getGovernance(AccessContext accessContext, String id);

    Governance getGovernance(AccessContext accessContext, String id, String version);

    DocumentContent readDocument(AccessContext accessContext, String packageId, String path);

    DocumentContent readDocument(AccessContext accessContext, String packageId, String version, String path);

    record DocumentContent(String content, String contentType) {}
}
