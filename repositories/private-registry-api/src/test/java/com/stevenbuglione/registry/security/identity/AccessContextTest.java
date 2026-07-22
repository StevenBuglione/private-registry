package com.stevenbuglione.registry.security.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class AccessContextTest {

  @Test
  void scopesMembersAndAdministratorsToTheSelectedApm() {
    var member = new AccessContext("member", Set.of("APM0000001", "APM0000002"), false);
    var administrator = new AccessContext("admin", Set.of(), true);

    assertThat(member.scopedToApm("APM0000002"))
        .isEqualTo(new AccessContext("member", Set.of("APM0000002"), false));
    assertThat(administrator.scopedToApm("APM0000003"))
        .isEqualTo(new AccessContext("admin", Set.of("APM0000003"), false));
  }

  @Test
  void unknownSelectedApmProducesAnEmptyFailClosedContext() {
    var member = new AccessContext("member", Set.of("APM0000001"), false);

    assertThat(member.scopedToApm("APM9999999"))
        .isEqualTo(new AccessContext("member", Set.of(), false));
  }
}
