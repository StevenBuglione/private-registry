package com.stevenbuglione.registry.ingestion;

public final class QuarantineException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  private final String code;

  public QuarantineException(String code, String message) {
    super(message);
    this.code = code;
  }

  public QuarantineException(String code, String message, Throwable cause) {
    super(message, cause);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
