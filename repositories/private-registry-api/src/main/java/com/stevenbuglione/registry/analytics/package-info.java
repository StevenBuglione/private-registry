/** Privacy-minimized authenticated Registry traffic analytics. */
@ApplicationModule(
    displayName = "Traffic analytics",
    allowedDependencies = {"security::identity"})
@NullMarked
package com.stevenbuglione.registry.analytics;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
