/** HTTP adapters for the authenticated catalog and operational endpoints. */
@ApplicationModule(
    displayName = "Web API",
    allowedDependencies = {
      "administration",
      "analytics",
      "artifactory",
      "audit",
      "catalog",
      "health",
      "model",
      "security::identity"
    })
@NullMarked
package com.stevenbuglione.registry.web;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
