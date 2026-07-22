package com.stevenbuglione.registry.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RequestIdFilter extends OncePerRequestFilter {

  private static final Logger LOGGER = LoggerFactory.getLogger(RequestIdFilter.class);
  private static final String REQUEST_ID_HEADER = "X-Request-ID";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    var requestId = request.getHeader(REQUEST_ID_HEADER);
    if (requestId == null || requestId.isBlank()) {
      requestId = UUID.randomUUID().toString();
    }

    response.setHeader(REQUEST_ID_HEADER, requestId);
    response.setHeader("X-Content-Type-Options", "nosniff");
    response.setHeader("Cache-Control", "no-store");
    var started = System.nanoTime();
    MDC.put("request_id", requestId);
    try {
      filterChain.doFilter(request, response);
    } finally {
      var durationMillis = (System.nanoTime() - started) / 1_000_000;
      LOGGER.info(
          "request method={} path={} status={} duration_ms={}",
          request.getMethod(),
          request.getRequestURI(),
          response.getStatus(),
          durationMillis);
      MDC.remove("request_id");
    }
  }
}
