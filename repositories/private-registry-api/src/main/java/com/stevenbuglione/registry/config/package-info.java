/** Application wiring and external-client configuration. */
@ApplicationModule(
    displayName = "Configuration",
    allowedDependencies = {"model", "security::identity"})
@NullMarked
package com.stevenbuglione.registry.config;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
