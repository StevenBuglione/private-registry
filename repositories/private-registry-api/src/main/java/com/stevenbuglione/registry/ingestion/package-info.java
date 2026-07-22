/** Reconciliation, quarantine, activation, and search-outbox processing. */
@ApplicationModule(
    displayName = "Catalog ingestion",
    allowedDependencies = {"artifactory", "catalog", "eventing", "model"})
@NullMarked
package com.stevenbuglione.registry.ingestion;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
