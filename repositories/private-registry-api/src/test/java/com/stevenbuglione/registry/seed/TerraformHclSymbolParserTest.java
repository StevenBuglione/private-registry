package com.stevenbuglione.registry.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TerraformHclSymbolParserTest {

  @Test
  void ignoresAllCommentFormsWithoutTreatingQuotedCommentMarkersAsComments() {
    var symbols =
        parse(
            """
            # variable "commented_hash" {}
            // output "commented_slash" {}
            /*
              resource "commented" "block" {}
            */
            variable "endpoint" {
              description = "https://registry.example.test/path#fragment"
              type        = string
            }
            """);

    assertThat(symbols)
        .singleElement()
        .satisfies(
            symbol -> {
              assertThat(symbol.kind()).isEqualTo("input");
              assertThat(symbol.name()).isEqualTo("endpoint");
              assertThat(symbol.description())
                  .isEqualTo("https://registry.example.test/path#fragment");
            });
  }

  @Test
  void preservesNestedCollectionExpressionsAndFindsFollowingBlocks() {
    var symbols =
        parse(
            """
            variable "routes" {
              default = {
                public = [
                  {
                    path     = "/"
                    template = "${join("-", ["a", "b"])}"
                  }
                ]
              }
            }

            resource "test_service" "main" {
              routes = var.routes
            }
            """);

    assertThat(symbols)
        .extracting(TerraformMetadataExtractor.ExtractedSymbol::name)
        .containsExactly("routes", "test_service.main");
    assertThat(symbols.getFirst().defaultValue())
        .contains("public", "template", "${join(\"-\", [\"a\", \"b\"])}");
    assertThat(symbols.getFirst().required()).isFalse();
  }

  @Test
  void treatsIndentedHeredocsAsAtomicValuesDespiteCommentsAndBracesInside() {
    var symbols =
        parse(
            """
            variable "policy" {
              description = <<-DESCRIPTION
                Policy with // text, # text, and { braces }.
              DESCRIPTION
              default = <<-JSON
                {
                  "statement": "${value}",
                  "nested": { "enabled": true }
                }
              JSON
            }

            output "policy" {
              value = var.policy
            }
            """);

    assertThat(symbols)
        .extracting(TerraformMetadataExtractor.ExtractedSymbol::name)
        .containsExactly("policy", "policy");
    var input = symbols.stream().filter(symbol -> symbol.kind().equals("input")).findFirst();
    assertThat(input)
        .hasValueSatisfying(
            symbol -> {
              assertThat(symbol.description())
                  .isEqualTo("Policy with // text, # text, and { braces }.");
              assertThat(symbol.defaultValue()).contains("\"nested\"", "${value}", "JSON");
            });
  }

  private static List<TerraformMetadataExtractor.ExtractedSymbol> parse(String source) {
    return TerraformHclSymbolParser.moduleSymbols(Map.of("main.tf", source), List.of(), List.of());
  }
}
