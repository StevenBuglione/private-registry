/** HTTP adapters for the authenticated catalog and operational endpoints. */
@ApplicationModule(
    displayName = "Web API",
    allowedDependencies = {"artifactory", "catalog", "health", "model", "security::identity"})
@NullMarked
package com.stevenbuglione.registry.web;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
