package com.stevenbuglione.registry.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.bind.annotation.RestController;

@ApplicationModuleTest
@TestPropertySource(
    properties =
        "spring.autoconfigure.exclude="
            + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
            + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration")
class CatalogModelModuleIntegrationTest {

  private final ApplicationContext applicationContext;

  CatalogModelModuleIntegrationTest(@Autowired ApplicationContext applicationContext) {
    this.applicationContext = applicationContext;
  }

  @Test
  void bootsTheModelModuleWithoutLeakingWebAdapters() {
    assertThat(applicationContext.getBeansWithAnnotation(RestController.class)).isEmpty();
  }
}
