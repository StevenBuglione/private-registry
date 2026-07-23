/** Readiness reporting for the API and background workers. */
@ApplicationModule(
    displayName = "Health",
    allowedDependencies = {"eventing", "storage"})
@NullMarked
package com.stevenbuglione.registry.health;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
