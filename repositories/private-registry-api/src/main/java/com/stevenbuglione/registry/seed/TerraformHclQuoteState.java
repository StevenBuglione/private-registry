package com.stevenbuglione.registry.seed;

/** Tracks quoted HCL text without interpreting escaped characters as syntax. */
final class TerraformHclQuoteState {

  private boolean quoted;
  private boolean escaped;

  boolean quoted() {
    return quoted;
  }

  void beginQuote() {
    quoted = true;
  }

  boolean consume(char value) {
    if (!quoted) {
      return false;
    }
    if (escaped) {
      escaped = false;
    } else if (value == '\\') {
      escaped = true;
    } else if (value == '"') {
      quoted = false;
    }
    return true;
  }
}
