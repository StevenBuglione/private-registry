package com.stevenbuglione.registry.administration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.stevenbuglione.registry.audit.AuditLogService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;

class SyncCredentialServiceWiringTest {

  @Test
  void springSelectsTheProductionConstructor() {
    try (var context = new AnnotationConfigApplicationContext()) {
      context.registerBean(JdbcClient.class, () -> mock(JdbcClient.class));
      context.registerBean(AuditLogService.class, () -> mock(AuditLogService.class));
      context.register(SyncCredentialService.class);
      context.refresh();

      assertThat(context.getBean(SyncCredentialService.class)).isNotNull();
    }
  }
}
