package com.stevenbuglione.registry.seed;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/** Extracts normalized registry metadata from upstream Markdown documents. */
final class TerraformMarkdown {

  private static final Pattern LEADING_FRONTMATTER =
      Pattern.compile("(?s)\\A---\\R.*?\\R---(?:\\R|\\z)");

  private TerraformMarkdown() {}

  static String title(byte[] content, String fallback) {
    var markdown = decode(content);
    var frontmatter = frontmatterValue(markdown, "page_title");
    if (frontmatter == null) {
      frontmatter = frontmatterValue(markdown, "title");
    }
    if (frontmatter != null) {
      return frontmatter;
    }
    return markdown
        .lines()
        .map(String::trim)
        .filter(line -> line.startsWith("# "))
        .map(line -> line.substring(2).trim())
        .findFirst()
        .orElse(fallback);
  }

  static @Nullable String description(byte[] content) {
    var markdown = decode(content);
    var frontmatter = frontmatterValue(markdown, "description");
    if (frontmatter != null) {
      return frontmatter;
    }
    var body = bodyWithoutFrontmatter(markdown);
    return body.lines()
        .map(String::trim)
        .filter(TerraformMarkdown::isDescriptionLine)
        .findFirst()
        .map(TerraformMarkdown::truncateDescription)
        .orElse(null);
  }

  static byte[] stripLeadingFrontmatter(byte[] content) {
    return bodyWithoutFrontmatter(decode(content)).getBytes(StandardCharsets.UTF_8);
  }

  static String decode(byte[] content) {
    return new String(content, StandardCharsets.UTF_8);
  }

  private static String bodyWithoutFrontmatter(String markdown) {
    if (!markdown.startsWith("---")) {
      return markdown;
    }
    var matcher = LEADING_FRONTMATTER.matcher(markdown);
    return matcher.find() ? markdown.substring(matcher.end()) : "";
  }

  private static boolean isDescriptionLine(String line) {
    return !line.isBlank() && !line.startsWith("#") && !line.startsWith("<");
  }

  private static String truncateDescription(String line) {
    return line.length() > 500 ? line.substring(0, 500) : line;
  }

  private static @Nullable String frontmatterValue(String markdown, String key) {
    if (!markdown.startsWith("---")) {
      return null;
    }
    var end = markdown.indexOf("\n---", 3);
    if (end < 0) {
      return null;
    }
    var frontmatter = markdown.substring(0, end);
    var pattern = Pattern.compile("(?m)^" + Pattern.quote(key) + ":\\s*([^\\r\\n]*)$");
    var matcher = pattern.matcher(frontmatter);
    if (!matcher.find()) {
      return null;
    }
    var raw = matcher.group(1).trim();
    return raw.matches("[|>][-+]?")
        ? multilineFrontmatter(frontmatter, matcher.end(), raw.startsWith(">"))
        : unquote(raw);
  }

  private static @Nullable String multilineFrontmatter(
      String frontmatter, int valueStart, boolean folded) {
    var result = new ArrayList<String>();
    for (var line : frontmatter.substring(valueStart).lines().toList()) {
      if (line.isBlank()) {
        if (!result.isEmpty()) {
          result.add("");
        }
      } else if (Character.isWhitespace(line.charAt(0))) {
        result.add(line.trim());
      } else {
        break;
      }
    }
    return result.isEmpty() ? null : String.join(folded ? " " : "\n", result).trim();
  }

  private static String unquote(String value) {
    var trimmed = value.trim();
    if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
      return trimmed.substring(1, trimmed.length() - 1).replace("\\\"", "\"").replace("\\n", "\n");
    }
    return trimmed;
  }
}
