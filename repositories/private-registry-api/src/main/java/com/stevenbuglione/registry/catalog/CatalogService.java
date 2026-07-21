package com.stevenbuglione.registry.catalog;

import com.stevenbuglione.registry.model.CatalogPackage;
import com.stevenbuglione.registry.model.Governance;
import com.stevenbuglione.registry.model.PackageKind;
import com.stevenbuglione.registry.model.SearchResult;
import java.util.List;

public interface CatalogService {

    List<CatalogPackage> listPackages(PackageKind kind);

    CatalogPackage getPackage(String id);

    Governance getGovernance(String id);

    List<SearchResult> search(String query, PackageKind kind, int limit);

    DocumentContent readDocument(String packageId, String path);

    record DocumentContent(String content, String contentType) {}
}
