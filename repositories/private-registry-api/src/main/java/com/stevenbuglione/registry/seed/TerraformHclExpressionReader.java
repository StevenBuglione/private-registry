package com.stevenbuglione.registry.seed;

import org.jspecify.annotations.Nullable;

/** Reads one HCL attribute expression while respecting nested delimiters, strings, and heredocs. */
final class TerraformHclExpressionReader {

  private TerraformHclExpressionReader() {}

  static @Nullable String read(String body, int start) {
    var cursor = skipWhitespace(body, start);
    var expressionStart = cursor;
    var depth = new ExpressionDepth();
    var quote = new TerraformHclQuoteState();
    while (cursor < body.length()) {
      var value = body.charAt(cursor);
      if (quote.consume(value)) {
        cursor++;
        continue;
      }
      var heredocEnd = TerraformHclHeredoc.endAt(body, cursor);
      if (heredocEnd > cursor) {
        cursor = heredocEnd;
        break;
      }
      if (value == '"') {
        quote.beginQuote();
      } else if (depth.consume(value) || isTopLevelLineEnd(value, depth)) {
        break;
      }
      cursor++;
    }
    var result = body.substring(expressionStart, cursor).trim();
    return result.isEmpty() ? null : result;
  }

  private static int skipWhitespace(String value, int start) {
    var cursor = start;
    while (cursor < value.length() && Character.isWhitespace(value.charAt(cursor))) {
      cursor++;
    }
    return cursor;
  }

  private static boolean isTopLevelLineEnd(char value, ExpressionDepth depth) {
    return (value == '\n' || value == '\r') && depth.atTopLevel();
  }

  private static final class ExpressionDepth {

    private int braces;
    private int brackets;
    private int parentheses;

    boolean consume(char value) {
      return switch (value) {
        case '{' -> incrementBraces();
        case '}' -> decrementBraces();
        case '[' -> incrementBrackets();
        case ']' -> decrementBrackets();
        case '(' -> incrementParentheses();
        case ')' -> decrementParentheses();
        default -> false;
      };
    }

    boolean atTopLevel() {
      return braces == 0 && brackets == 0 && parentheses == 0;
    }

    private boolean incrementBraces() {
      braces++;
      return false;
    }

    private boolean decrementBraces() {
      if (braces == 0) {
        return true;
      }
      braces--;
      return false;
    }

    private boolean incrementBrackets() {
      brackets++;
      return false;
    }

    private boolean decrementBrackets() {
      brackets--;
      return false;
    }

    private boolean incrementParentheses() {
      parentheses++;
      return false;
    }

    private boolean decrementParentheses() {
      parentheses--;
      return false;
    }
  }
}
