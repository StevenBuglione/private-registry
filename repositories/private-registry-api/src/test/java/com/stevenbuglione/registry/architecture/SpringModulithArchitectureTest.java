package com.stevenbuglione.registry.architecture;

import com.stevenbuglione.registry.PrivateRegistryApplication;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class SpringModulithArchitectureTest {

  @Test
  void applicationModulesHaveValidBoundariesAndNoCycles() {
    ApplicationModules.of(PrivateRegistryApplication.class).verify();
  }
}
