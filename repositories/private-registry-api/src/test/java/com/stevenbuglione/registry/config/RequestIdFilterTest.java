package com.stevenbuglione.registry.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

  private final RequestIdFilter filter = new RequestIdFilter();

  @Test
  void preservesAValidCallerRequestId() throws Exception {
    var request = new MockHttpServletRequest();
    request.addHeader("X-Request-ID", "trace-1234.parent_2");
    var response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    assertThat(response.getHeader("X-Request-ID")).isEqualTo("trace-1234.parent_2");
  }

  @Test
  void replacesARequestIdThatCouldForgeLogsOrHeaders() throws Exception {
    var request = new MockHttpServletRequest();
    request.addHeader("X-Request-ID", "trusted\r\nforged=true");
    var response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    assertThat(response.getHeader("X-Request-ID"))
        .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
  }
}
