/** Catalog queries, authorized document access, and catalog change subscriptions. */
@ApplicationModule(
    displayName = "Catalog",
    allowedDependencies = {"audit", "eventing", "model", "security::identity"})
@NullMarked
package com.stevenbuglione.registry.catalog;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
