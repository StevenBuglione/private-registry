package com.stevenbuglione.registry.seed;

/** Removes HCL comments while preserving line structure, strings, and heredoc bodies. */
final class TerraformHclCommentStripper {

  private TerraformHclCommentStripper() {}

  static String strip(String source) {
    var result = new StringBuilder(source.length());
    var quote = new TerraformHclQuoteState();
    for (var index = 0; index < source.length(); index++) {
      var current = source.charAt(index);
      if (quote.quoted()) {
        result.append(current);
        quote.consume(current);
        continue;
      }
      var heredocEnd = TerraformHclHeredoc.endAt(source, index);
      if (heredocEnd > index) {
        result.append(source, index, heredocEnd);
        index = heredocEnd - 1;
      } else if (current == '"') {
        quote.beginQuote();
        result.append(current);
      } else if (startsLineComment(source, index)) {
        index = skipLineComment(source, index);
        result.append('\n');
      } else if (startsBlockComment(source, index)) {
        index = copyBlockCommentNewlines(source, index, result);
      } else {
        result.append(current);
      }
    }
    return result.toString();
  }

  private static boolean startsLineComment(String source, int index) {
    return source.charAt(index) == '#'
        || (source.charAt(index) == '/'
            && index + 1 < source.length()
            && source.charAt(index + 1) == '/');
  }

  private static int skipLineComment(String source, int index) {
    var cursor = index;
    while (cursor < source.length() && source.charAt(cursor) != '\n') {
      cursor++;
    }
    return cursor;
  }

  private static boolean startsBlockComment(String source, int index) {
    return source.charAt(index) == '/'
        && index + 1 < source.length()
        && source.charAt(index + 1) == '*';
  }

  private static int copyBlockCommentNewlines(
      String source, int commentStart, StringBuilder result) {
    var cursor = commentStart + 2;
    while (cursor + 1 < source.length()
        && !(source.charAt(cursor) == '*' && source.charAt(cursor + 1) == '/')) {
      if (source.charAt(cursor) == '\n') {
        result.append('\n');
      }
      cursor++;
    }
    return Math.min(cursor + 1, source.length() - 1);
  }
}
