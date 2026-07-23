package com.stevenbuglione.registry.seed;

import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/** Recognizes HCL heredocs so their contents are treated as opaque text by the symbol parser. */
final class TerraformHclHeredoc {

  private static final Pattern START =
      Pattern.compile("<<(-?)([A-Za-z_][A-Za-z0-9_-]*)[ \\t]*\\r?\\n");
  private static final Pattern VALUE =
      Pattern.compile(
          "(?s)^<<-?([A-Za-z_][A-Za-z0-9_-]*)[ \\t]*\\r?\\n(.*)\\r?\\n[ \\t]*\\1[ \\t]*$");

  private TerraformHclHeredoc() {}

  static int endAt(String value, int opening) {
    if (!isOpening(value, opening)) {
      return -1;
    }
    var start = START.matcher(value).region(opening, value.length());
    return start.lookingAt() ? findTerminator(value, start.end(), start.group(2)) : -1;
  }

  static @Nullable String value(String candidate) {
    var matcher = VALUE.matcher(candidate);
    return matcher.matches() ? matcher.group(2).stripIndent().strip() : null;
  }

  private static boolean isOpening(String value, int opening) {
    return value.charAt(opening) == '<'
        && opening + 1 < value.length()
        && value.charAt(opening + 1) == '<';
  }

  private static int findTerminator(String value, int contentStart, String delimiter) {
    var cursor = contentStart;
    while (cursor <= value.length()) {
      var lineEnd = lineEnd(value, cursor);
      var line = value.substring(cursor, lineEnd).strip();
      if (line.equals(delimiter)) {
        return lineEnd < value.length() ? lineEnd + 1 : lineEnd;
      }
      if (lineEnd == value.length()) {
        return -1;
      }
      cursor = lineEnd + 1;
    }
    return -1;
  }

  private static int lineEnd(String value, int start) {
    var end = value.indexOf('\n', start);
    return end < 0 ? value.length() : end;
  }
}
