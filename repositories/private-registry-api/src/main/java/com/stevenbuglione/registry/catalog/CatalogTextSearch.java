package com.stevenbuglione.registry.catalog;

import com.stevenbuglione.registry.security.identity.AccessContext;
import java.util.List;

public interface CatalogTextSearch {

  List<String> findPackageIds(AccessContext accessContext, CatalogQuery query, int maximumResults);
}
