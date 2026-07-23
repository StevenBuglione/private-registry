/** Administrator operations, operational telemetry, and automation credentials. */
@ApplicationModule(
    displayName = "Registry administration",
    allowedDependencies = {"audit", "eventing", "health"})
@NullMarked
package com.stevenbuglione.registry.administration;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
