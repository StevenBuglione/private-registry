package com.stevenbuglione.registry.web;

import com.stevenbuglione.registry.catalog.NotFoundException;
import com.stevenbuglione.registry.security.identity.IdentityProviderUnavailableException;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(NotFoundException.class)
  ResponseEntity<ApiError> notFound(NotFoundException exception) {
    return error(HttpStatus.NOT_FOUND, "not_found", exception.getMessage());
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<ApiError> badRequest(IllegalArgumentException exception) {
    return error(HttpStatus.BAD_REQUEST, "bad_request", exception.getMessage());
  }

  @ExceptionHandler(IdentityProviderUnavailableException.class)
  ResponseEntity<ApiError> identityProviderUnavailable(
      IdentityProviderUnavailableException exception) {
    return error(
        HttpStatus.SERVICE_UNAVAILABLE, "identity_provider_unavailable", exception.getMessage());
  }

  private static ResponseEntity<ApiError> error(
      HttpStatus status, String code, @Nullable String message) {
    var safeMessage = message == null || message.isBlank() ? code : message;
    return ResponseEntity.status(status)
        .body(new ApiError(Map.of("code", code, "message", safeMessage)));
  }

  record ApiError(Map<String, String> error) {}
}
