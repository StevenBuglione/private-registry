/**
 * The sole outbound adapter for governed artifact storage.
 *
 * <p>All Artifactory reads and writes must remain behind this module's public gateway.
 */
@ApplicationModule(displayName = "Artifactory adapter", allowedDependencies = "config")
@NullMarked
package com.stevenbuglione.registry.artifactory;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
