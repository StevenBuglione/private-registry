/** Curated, checksum-verified catalog bootstrapping. */
@ApplicationModule(
    displayName = "Catalog seeding",
    allowedDependencies = {"artifactory", "config", "ingestion"})
@NullMarked
package com.stevenbuglione.registry.seed;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
