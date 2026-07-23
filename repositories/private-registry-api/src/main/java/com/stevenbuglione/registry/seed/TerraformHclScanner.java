package com.stevenbuglione.registry.seed;

import org.jspecify.annotations.Nullable;

/** Minimal framework-free lexical scanner for the HCL constructs used by Registry metadata. */
final class TerraformHclScanner {

  private TerraformHclScanner() {}

  static @Nullable String attribute(String body, String wanted) {
    var index = 0;
    while (index < body.length()) {
      var lineEnd = lineEnd(body, index);
      var cursor = skipWhitespace(body, index, lineEnd);
      var nameStart = cursor;
      while (cursor < lineEnd && isIdentifier(body.charAt(cursor))) {
        cursor++;
      }
      var name = body.substring(nameStart, cursor);
      cursor = skipWhitespace(body, cursor, lineEnd);
      if (name.equals(wanted) && cursor < lineEnd && body.charAt(cursor) == '=') {
        return TerraformHclExpressionReader.read(body, cursor + 1);
      }
      index = lineEnd + 1;
    }
    return null;
  }

  static int findClosingBrace(String value, int opening) {
    var state = new TerraformHclQuoteState();
    var depth = 0;
    for (var index = opening; index < value.length(); index++) {
      var current = value.charAt(index);
      if (state.consume(current)) {
        continue;
      }
      var heredocEnd = TerraformHclHeredoc.endAt(value, index);
      if (heredocEnd > index) {
        index = heredocEnd - 1;
      } else if (current == '"') {
        state.beginQuote();
      } else if (current == '{') {
        depth++;
      } else if (current == '}' && --depth == 0) {
        return index;
      }
    }
    return -1;
  }

  static String stripComments(String source) {
    return TerraformHclCommentStripper.strip(source);
  }

  static @Nullable String unquote(@Nullable String value) {
    if (value == null) {
      return null;
    }
    var trimmed = value.trim();
    var heredoc = TerraformHclHeredoc.value(trimmed);
    if (heredoc != null) {
      return heredoc;
    }
    if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
      return trimmed.substring(1, trimmed.length() - 1).replace("\\\"", "\"").replace("\\n", "\n");
    }
    return trimmed;
  }

  private static int lineEnd(String value, int start) {
    var end = value.indexOf('\n', start);
    return end < 0 ? value.length() : end;
  }

  private static int skipWhitespace(String value, int start, int end) {
    var cursor = start;
    while (cursor < end && Character.isWhitespace(value.charAt(cursor))) {
      cursor++;
    }
    return cursor;
  }

  private static boolean isIdentifier(char value) {
    return Character.isLetterOrDigit(value) || value == '_' || value == '-';
  }
}
