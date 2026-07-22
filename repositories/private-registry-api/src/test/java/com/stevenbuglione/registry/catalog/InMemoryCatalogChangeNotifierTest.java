package com.stevenbuglione.registry.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.stevenbuglione.registry.security.identity.AccessContext;
import java.util.Set;
import org.junit.jupiter.api.Test;

class InMemoryCatalogChangeNotifierTest {

  @Test
  void createsLongLivedEmitterWithoutMvcTimeout() {
    var notifier = new InMemoryCatalogChangeNotifier();

    var emitter = notifier.subscribe(new AccessContext("user-1", Set.of("apm-a"), false));

    assertThat(emitter.getTimeout()).isZero();
    emitter.complete();
  }
}
