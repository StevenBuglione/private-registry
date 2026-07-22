package com.stevenbuglione.registry.seed;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.ZipInputStream;

/** Extracts searchable Terraform metadata directly from a pinned upstream source archive. */
final class TerraformMetadataExtractor {

    private static final int MAX_ARCHIVE_ENTRIES = 50_000;
    private static final int MAX_TEXT_FILE_BYTES = 16 * 1024 * 1024;
    private static final Pattern BLOCK = Pattern.compile(
            "(?m)(?<![A-Za-z0-9_-])(variable|output|resource|data|module)\\s+\"([^\"]+)\"(?:\\s+\"([^\"]+)\")?\\s*\\{");
    private static final Pattern REQUIRED_PROVIDERS =
            Pattern.compile("(?m)(?<![A-Za-z0-9_-])required_providers\\s*(?:=\\s*)?\\{");
    private static final Pattern PROVIDER_ENTRY =
            Pattern.compile("(?m)^\\s*([A-Za-z0-9_-]+)\\s*=\\s*\\{");
    private static final Pattern LEADING_FRONTMATTER =
            Pattern.compile("(?s)\\A---\\R.*?\\R---(?:\\R|\\z)");
    private static final Pattern HEREDOC_START =
            Pattern.compile("<<(-?)([A-Za-z_][A-Za-z0-9_-]*)[ \\t]*\\r?\\n");
    private static final Pattern HEREDOC_VALUE = Pattern.compile(
            "(?s)^<<-?([A-Za-z_][A-Za-z0-9_-]*)[ \\t]*\\r?\\n(.*)\\r?\\n[ \\t]*\\1[ \\t]*$");

    private TerraformMetadataExtractor() {}

    static Extraction extract(Path sourceArchive, boolean provider) {
        var terraform = new LinkedHashMap<String, String>();
        var documents = new LinkedHashMap<String, ExtractedDocument>();
        ExtractedDocument fallbackReadme = null;
        var entries = 0;
        try (var input = Files.newInputStream(sourceArchive); var archive = new ZipInputStream(input)) {
            for (var entry = archive.getNextEntry(); entry != null; entry = archive.getNextEntry()) {
                entries++;
                if (entries > MAX_ARCHIVE_ENTRIES) {
                    throw new IllegalStateException("Source archive contains too many entries");
                }
                if (entry.isDirectory()) {
                    continue;
                }
                var path = relativePath(entry.getName());
                if (path == null) {
                    continue;
                }
                var lower = path.toLowerCase(Locale.ROOT);
                if (lower.endsWith(".tf") && !provider && !path.contains("/")) {
                    terraform.put(path, decode(readTextEntry(archive, path)));
                    continue;
                }
                if (isReadme(lower)) {
                    var content = readTextEntry(archive, path);
                    var readme = new ExtractedDocument(
                            "README.md",
                            markdownTitle(content, "README"),
                            "text/markdown",
                            markdownDescription(content),
                            stripLeadingFrontmatter(content));
                    if (path.equalsIgnoreCase("README.md")) {
                        documents.put("README.md", readme);
                    } else if (fallbackReadme == null) {
                        fallbackReadme = readme;
                    }
                    continue;
                }
                if (provider) {
                    var classified = classifyProviderDocument(path);
                    if (classified != null) {
                        var content = readTextEntry(archive, path);
                        var document = new ExtractedDocument(
                                classified.path(),
                                markdownTitle(content, classified.name()),
                                "text/markdown",
                                markdownDescription(content),
                                stripLeadingFrontmatter(content));
                        if (classified.path().equals("index.md")
                                && lower.endsWith("website/docs/index.html.markdown")) {
                            documents.put("index.md", document);
                        } else {
                            documents.putIfAbsent(classified.path(), document);
                        }
                    }
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to extract Terraform source metadata from " + sourceArchive, exception);
        }
        if (!documents.containsKey("README.md") && fallbackReadme != null) {
            documents.put("README.md", fallbackReadme);
        }
        if (!documents.containsKey("README.md")) {
            throw new IllegalStateException("Pinned upstream source archive does not contain a README");
        }
        if (provider && !documents.containsKey("index.md")) {
            var readme = documents.get("README.md");
            documents.put("index.md", new ExtractedDocument(
                    "index.md", readme.title(), readme.contentType(), readme.description(), readme.content()));
        }

        var symbols = provider
                ? providerSymbols(documents.values())
                : moduleSymbols(terraform);
        return new Extraction(
                documents.values().stream()
                        .sorted(Comparator.comparing(ExtractedDocument::path))
                        .toList(),
                symbols,
                archiveDigest(sourceArchive));
    }

    private static String archiveDigest(Path archive) {
        try (var input = Files.newInputStream(archive)) {
            var digest = MessageDigest.getInstance("SHA-256");
            var buffer = new byte[64 * 1024];
            for (var read = input.read(buffer); read >= 0; read = input.read(buffer)) {
                digest.update(buffer, 0, read);
            }
            return "sha256:" + HexFormat.of().formatHex(digest.digest());
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to checksum source archive " + archive, exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static List<ExtractedSymbol> providerSymbols(Iterable<ExtractedDocument> documents) {
        var symbols = new ArrayList<ExtractedSymbol>();
        for (var document : documents) {
            if (document.path().equals("README.md") || document.path().equals("index.md")) {
                continue;
            }
            var slash = document.path().indexOf('/');
            var directory = slash < 0 ? "guides" : document.path().substring(0, slash);
            var kind = switch (directory) {
                case "resources" -> "resource";
                case "data-sources" -> "data-source";
                case "functions" -> "function";
                default -> "guide";
            };
            var filename = slash < 0 ? document.path() : document.path().substring(slash + 1);
            var name = filename.endsWith(".md") ? filename.substring(0, filename.length() - 3) : filename;
            symbols.add(new ExtractedSymbol(
                    kind,
                    name,
                    document.description(),
                    document.path(),
                    kind,
                    null,
                    false,
                    false));
        }
        return symbols.stream()
                .sorted(Comparator.comparing(ExtractedSymbol::kind).thenComparing(ExtractedSymbol::name))
                .toList();
    }

    private static List<ExtractedSymbol> moduleSymbols(Map<String, String> terraform) {
        var symbols = new LinkedHashMap<String, ExtractedSymbol>();
        terraform.forEach((path, content) -> {
            var normalized = stripComments(content);
            var matcher = BLOCK.matcher(normalized);
            while (matcher.find()) {
                var closing = findClosingBrace(normalized, matcher.end() - 1);
                if (closing < 0) {
                    throw new IllegalStateException("Unclosed Terraform block in " + path);
                }
                var blockKind = matcher.group(1);
                var firstLabel = matcher.group(2);
                var secondLabel = matcher.group(3);
                var body = normalized.substring(matcher.end(), closing);
                var symbol = moduleSymbol(path, blockKind, firstLabel, secondLabel, body);
                symbols.putIfAbsent(symbol.kind() + ":" + symbol.name(), symbol);
            }
            requiredProviderSymbols(path, normalized).forEach(symbol ->
                    symbols.putIfAbsent(symbol.kind() + ":" + symbol.name(), symbol));
        });
        return symbols.values().stream()
                .sorted(Comparator.comparing(ExtractedSymbol::kind).thenComparing(ExtractedSymbol::name))
                .toList();
    }

    private static ExtractedSymbol moduleSymbol(
            String path, String blockKind, String firstLabel, String secondLabel, String body) {
        var sensitive = Boolean.parseBoolean(attribute(body, "sensitive"));
        return switch (blockKind) {
            case "variable" -> {
                var defaultValue = attribute(body, "default");
                yield new ExtractedSymbol(
                        "input",
                        firstLabel,
                        unquote(attribute(body, "description")),
                        path,
                        attribute(body, "type"),
                        defaultValue,
                        defaultValue == null,
                        sensitive);
            }
            case "output" -> new ExtractedSymbol(
                    "output",
                    firstLabel,
                    unquote(attribute(body, "description")),
                    path,
                    null,
                    null,
                    false,
                    sensitive);
            case "resource", "data" -> new ExtractedSymbol(
                    blockKind.equals("data") ? "data-source" : "resource",
                    firstLabel + "." + secondLabel,
                    null,
                    path,
                    firstLabel,
                    null,
                    false,
                    false);
            case "module" -> new ExtractedSymbol(
                    "dependency",
                    firstLabel,
                    unquote(attribute(body, "source")),
                    path,
                    "module",
                    null,
                    true,
                    false);
            default -> throw new IllegalStateException("Unexpected Terraform block kind " + blockKind);
        };
    }

    private static List<ExtractedSymbol> requiredProviderSymbols(String path, String content) {
        var symbols = new ArrayList<ExtractedSymbol>();
        var required = REQUIRED_PROVIDERS.matcher(content);
        while (required.find()) {
            var end = findClosingBrace(content, required.end() - 1);
            if (end < 0) {
                throw new IllegalStateException("Unclosed required_providers block in " + path);
            }
            var body = content.substring(required.end(), end);
            var provider = PROVIDER_ENTRY.matcher(body);
            while (provider.find()) {
                var providerEnd = findClosingBrace(body, provider.end() - 1);
                if (providerEnd < 0) {
                    continue;
                }
                var providerBody = body.substring(provider.end(), providerEnd);
                symbols.add(new ExtractedSymbol(
                        "dependency",
                        provider.group(1),
                        unquote(attribute(providerBody, "source")),
                        path,
                        "provider",
                        unquote(attribute(providerBody, "version")),
                        true,
                        false));
            }
        }
        return List.copyOf(symbols);
    }

    private static String attribute(String body, String wanted) {
        var index = 0;
        while (index < body.length()) {
            var lineEnd = body.indexOf('\n', index);
            if (lineEnd < 0) {
                lineEnd = body.length();
            }
            var cursor = index;
            while (cursor < lineEnd && Character.isWhitespace(body.charAt(cursor))) {
                cursor++;
            }
            var nameStart = cursor;
            while (cursor < lineEnd && isIdentifier(body.charAt(cursor))) {
                cursor++;
            }
            var name = body.substring(nameStart, cursor);
            while (cursor < lineEnd && Character.isWhitespace(body.charAt(cursor))) {
                cursor++;
            }
            if (name.equals(wanted) && cursor < lineEnd && body.charAt(cursor) == '=') {
                return readExpression(body, cursor + 1);
            }
            index = lineEnd + 1;
        }
        return null;
    }

    private static String readExpression(String body, int start) {
        var cursor = start;
        while (cursor < body.length() && Character.isWhitespace(body.charAt(cursor))) {
            cursor++;
        }
        var expressionStart = cursor;
        var braces = 0;
        var brackets = 0;
        var parentheses = 0;
        var quoted = false;
        var escaped = false;
        while (cursor < body.length()) {
            var value = body.charAt(cursor);
            if (quoted) {
                if (escaped) {
                    escaped = false;
                } else if (value == '\\') {
                    escaped = true;
                } else if (value == '"') {
                    quoted = false;
                }
            } else if (value == '<' && cursor + 1 < body.length() && body.charAt(cursor + 1) == '<') {
                var heredocEnd = heredocEnd(body, cursor);
                if (heredocEnd > cursor) {
                    cursor = heredocEnd;
                    break;
                }
            } else if (value == '"') {
                quoted = true;
            } else if (value == '{') {
                braces++;
            } else if (value == '}') {
                if (braces == 0) {
                    break;
                }
                braces--;
            } else if (value == '[') {
                brackets++;
            } else if (value == ']') {
                brackets--;
            } else if (value == '(') {
                parentheses++;
            } else if (value == ')') {
                parentheses--;
            } else if ((value == '\n' || value == '\r') && braces == 0 && brackets == 0 && parentheses == 0) {
                break;
            }
            cursor++;
        }
        var result = body.substring(expressionStart, cursor).trim();
        return result.isEmpty() ? null : result;
    }

    private static boolean isIdentifier(char value) {
        return Character.isLetterOrDigit(value) || value == '_' || value == '-';
    }

    private static int findClosingBrace(String value, int opening) {
        var depth = 0;
        var quoted = false;
        var escaped = false;
        for (var index = opening; index < value.length(); index++) {
            var current = value.charAt(index);
            if (quoted) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    quoted = false;
                }
                continue;
            }
            if (current == '<' && index + 1 < value.length() && value.charAt(index + 1) == '<') {
                var heredocEnd = heredocEnd(value, index);
                if (heredocEnd > index) {
                    index = heredocEnd - 1;
                }
            } else if (current == '"') {
                quoted = true;
            } else if (current == '{') {
                depth++;
            } else if (current == '}' && --depth == 0) {
                return index;
            }
        }
        return -1;
    }

    private static String stripComments(String source) {
        var result = new StringBuilder(source.length());
        var quoted = false;
        var escaped = false;
        for (var index = 0; index < source.length(); index++) {
            var current = source.charAt(index);
            if (quoted) {
                result.append(current);
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    quoted = false;
                }
                continue;
            }
            if (current == '<' && index + 1 < source.length() && source.charAt(index + 1) == '<') {
                var heredocEnd = heredocEnd(source, index);
                if (heredocEnd > index) {
                    result.append(source, index, heredocEnd);
                    index = heredocEnd - 1;
                } else {
                    result.append(current);
                }
            } else if (current == '"') {
                quoted = true;
                result.append(current);
            } else if (current == '#'
                    || (current == '/' && index + 1 < source.length() && source.charAt(index + 1) == '/')) {
                while (index < source.length() && source.charAt(index) != '\n') {
                    index++;
                }
                result.append('\n');
            } else if (current == '/' && index + 1 < source.length() && source.charAt(index + 1) == '*') {
                index += 2;
                while (index + 1 < source.length()
                        && !(source.charAt(index) == '*' && source.charAt(index + 1) == '/')) {
                    if (source.charAt(index) == '\n') {
                        result.append('\n');
                    }
                    index++;
                }
                index++;
            } else {
                result.append(current);
            }
        }
        return result.toString();
    }

    private static int heredocEnd(String value, int opening) {
        var start = HEREDOC_START.matcher(value).region(opening, value.length());
        if (!start.lookingAt()) {
            return -1;
        }
        var delimiter = start.group(2);
        var cursor = start.end();
        while (cursor <= value.length()) {
            var lineEnd = value.indexOf('\n', cursor);
            if (lineEnd < 0) {
                lineEnd = value.length();
            }
            var line = value.substring(cursor, lineEnd);
            if (line.endsWith("\r")) {
                line = line.substring(0, line.length() - 1);
            }
            // Be liberal here: several published modules indent an ordinary heredoc terminator
            // even though only <<- formally promises indentation handling.
            if (line.strip().equals(delimiter)) {
                return lineEnd < value.length() ? lineEnd + 1 : lineEnd;
            }
            if (lineEnd == value.length()) {
                break;
            }
            cursor = lineEnd + 1;
        }
        return -1;
    }

    private static byte[] readTextEntry(ZipInputStream archive, String path) throws IOException {
        var output = new ByteArrayOutputStream();
        var buffer = new byte[16 * 1024];
        var size = 0;
        for (var read = archive.read(buffer); read >= 0; read = archive.read(buffer)) {
            size += read;
            if (size > MAX_TEXT_FILE_BYTES) {
                throw new IllegalStateException("Source metadata file exceeds limit: " + path);
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static String decode(byte[] content) {
        return new String(content, StandardCharsets.UTF_8);
    }

    private static String relativePath(String archivePath) {
        var normalized = archivePath.replace('\\', '/');
        if (normalized.isBlank()
                || normalized.startsWith("/")
                || Path.of(normalized).normalize().startsWith("..")) {
            throw new IllegalStateException("Source archive contains an unsafe path");
        }
        var separator = normalized.indexOf('/');
        return separator < 0 || separator == normalized.length() - 1
                ? null
                : normalized.substring(separator + 1);
    }

    private static boolean isReadme(String path) {
        return path.equals("readme.md") || path.endsWith("/readme.md");
    }

    private static ClassifiedDocument classifyProviderDocument(String path) {
        var normalized = path.replace('\\', '/');
        var lower = normalized.toLowerCase(Locale.ROOT);
        var kind = "";
        var marker = "";
        if (lower.endsWith("website/docs/index.html.markdown") || lower.endsWith("docs/index.md")) {
            return new ClassifiedDocument("guides", "index", "index.md");
        } else if (lower.contains("website/docs/r/")) {
            kind = "resources";
            marker = "website/docs/r/";
        } else if (lower.contains("website/docs/d/")) {
            kind = "data-sources";
            marker = "website/docs/d/";
        } else if (lower.contains("website/docs/functions/")) {
            kind = "functions";
            marker = "website/docs/functions/";
        } else if (lower.contains("website/docs/guides/")) {
            kind = "guides";
            marker = "website/docs/guides/";
        } else if (lower.contains("docs/resources/")) {
            kind = "resources";
            marker = "docs/resources/";
        } else if (lower.contains("docs/data-sources/")) {
            kind = "data-sources";
            marker = "docs/data-sources/";
        } else if (lower.contains("docs/functions/")) {
            kind = "functions";
            marker = "docs/functions/";
        } else if (lower.contains("docs/guides/")) {
            kind = "guides";
            marker = "docs/guides/";
        }
        if (kind.isEmpty()) {
            return null;
        }
        var relative = normalized.substring(lower.indexOf(marker) + marker.length());
        if (relative.contains("/") || !(lower.endsWith(".md") || lower.endsWith(".markdown"))) {
            return null;
        }
        var name = relative.replaceFirst("(?i)\\.html\\.markdown$", "")
                .replaceFirst("(?i)\\.markdown$", "")
                .replaceFirst("(?i)\\.md$", "");
        return name.isBlank() ? null : new ClassifiedDocument(kind, name, kind + "/" + name + ".md");
    }

    private static String markdownTitle(byte[] content, String fallback) {
        var markdown = decode(content);
        var frontmatter = frontmatterValue(markdown, "page_title");
        if (frontmatter == null) {
            frontmatter = frontmatterValue(markdown, "title");
        }
        if (frontmatter != null) {
            return frontmatter;
        }
        return markdown.lines()
                .map(String::trim)
                .filter(line -> line.startsWith("# "))
                .map(line -> line.substring(2).trim())
                .findFirst()
                .orElse(fallback);
    }

    private static String markdownDescription(byte[] content) {
        var markdown = decode(content);
        var frontmatter = frontmatterValue(markdown, "description");
        if (frontmatter != null) {
            return frontmatter;
        }
        var inFrontmatter = markdown.startsWith("---");
        var frontmatterClosed = !inFrontmatter;
        var firstDelimiter = inFrontmatter;
        for (var line : markdown.lines().toList()) {
            var trimmed = line.trim();
            if (!frontmatterClosed) {
                if (trimmed.equals("---") && !firstDelimiter) {
                    frontmatterClosed = true;
                }
                firstDelimiter = false;
                continue;
            }
            if (!trimmed.isBlank() && !trimmed.startsWith("#") && !trimmed.startsWith("<")) {
                return trimmed.length() > 500 ? trimmed.substring(0, 500) : trimmed;
            }
        }
        return null;
    }

    private static String frontmatterValue(String markdown, String key) {
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
        if (raw.matches("[|>][-+]?")) {
            var folded = raw.startsWith(">");
            var lines = frontmatter.substring(matcher.end()).lines().toList();
            var result = new ArrayList<String>();
            for (var line : lines) {
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
            var separator = folded ? " " : "\n";
            return result.isEmpty() ? null : String.join(separator, result).trim();
        }
        return unquote(raw);
    }

    private static byte[] stripLeadingFrontmatter(byte[] content) {
        var markdown = decode(content);
        var matcher = LEADING_FRONTMATTER.matcher(markdown);
        return (matcher.find() ? markdown.substring(matcher.end()) : markdown).getBytes(StandardCharsets.UTF_8);
    }

    private static String unquote(String value) {
        if (value == null) {
            return null;
        }
        var trimmed = value.trim();
        var heredoc = HEREDOC_VALUE.matcher(trimmed);
        if (heredoc.matches()) {
            return heredoc.group(2).stripIndent().strip();
        }
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1).replace("\\\"", "\"").replace("\\n", "\n");
        }
        return trimmed;
    }

    record Extraction(List<ExtractedDocument> documents, List<ExtractedSymbol> symbols, String archiveDigest) {
        Extraction {
            documents = List.copyOf(documents);
            symbols = List.copyOf(symbols);
        }
    }

    record ExtractedDocument(String path, String title, String contentType, String description, byte[] content) {}

    record ExtractedSymbol(
            String kind,
            String name,
            String description,
            String path,
            String type,
            String defaultValue,
            boolean required,
            boolean sensitive) {}

    private record ClassifiedDocument(String kind, String name, String path) {}
}
