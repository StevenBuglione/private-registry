package com.stevenbuglione.registry.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TerraformMetadataExtractorTest {

  @TempDir private Path temporaryDirectory;

  @Test
  void extractsModuleInputsOutputsResourcesAndDependencies() throws IOException {
    var archive =
        archive(
            Map.of(
                "module-v1/README.md", "# VPC\n\nCreates a real VPC.",
                "module-v1/variables.tf",
                    """
                        variable "name" {
                          description = "VPC name"
                          type        = string
                        }

                        variable "cidrs" {
                          type      = list(string)
                          default   = ["10.0.0.0/16"]
                          sensitive = true
                        }
                        """,
                "module-v1/main.tf",
                    """
                        terraform {
                          required_providers {
                            aws = {
                              source  = "hashicorp/aws"
                              version = ">= 5.0"
                            }
                          }
                        }

                        resource "aws_vpc" "this" {
                          cidr_block  = var.cidrs[0]
                          description = var.name
                        }
                        data "aws_caller_identity" "current" {}
                        module "labels" { source = "cloudposse/label/null" }
                        output "vpc_id" {
                          description = "Created VPC identifier"
                          value       = aws_vpc.this.id
                        }
                        """,
                "module-v1/examples/complete/variables.tf", "variable \"example_only\" {}",
                "module-v1/modules/nested/main.tf", "resource \"aws_vpc\" \"nested_only\" {}"));

    var result = TerraformMetadataExtractor.extract(archive, false);

    assertThat(result.documents())
        .extracting(TerraformMetadataExtractor.ExtractedDocument::path)
        .containsExactly("README.md");
    assertThat(result.symbols())
        .extracting(symbol -> symbol.kind() + ":" + symbol.name())
        .contains(
            "input:name",
            "input:cidrs",
            "output:vpc_id",
            "resource:aws_vpc.this",
            "data-source:aws_caller_identity.current",
            "dependency:labels",
            "dependency:aws");
    assertThat(result.symbols())
        .extracting(TerraformMetadataExtractor.ExtractedSymbol::name)
        .doesNotContain("example_only", "aws_vpc.nested_only");
    assertThat(result.symbols().stream().filter(symbol -> symbol.name().equals("name")).findFirst())
        .hasValueSatisfying(
            symbol -> {
              assertThat(symbol.type()).isEqualTo("string");
              assertThat(symbol.required()).isTrue();
              assertThat(symbol.description()).isEqualTo("VPC name");
            });
    assertThat(
            result.symbols().stream().filter(symbol -> symbol.name().equals("cidrs")).findFirst())
        .hasValueSatisfying(
            symbol -> {
              assertThat(symbol.defaultValue()).isEqualTo("[\"10.0.0.0/16\"]");
              assertThat(symbol.required()).isFalse();
              assertThat(symbol.sensitive()).isTrue();
            });
    assertThat(
            result.symbols().stream()
                .filter(symbol -> symbol.name().equals("aws_vpc.this"))
                .findFirst())
        .hasValueSatisfying(symbol -> assertThat(symbol.description()).isNull());
  }

  @Test
  void extractsProviderResourceDataSourceFunctionAndGuideDocuments() throws IOException {
    var entries = new LinkedHashMap<String, String>();
    entries.put("provider-v1/README.md", "# AWS Provider\n");
    entries.put("provider-v1/docs/index.md", "# Welcome\n\nContribute to provider development.\n");
    entries.put(
        "provider-v1/website/docs/index.html.markdown",
        """
                ---
                page_title: "AWS Provider"
                description: "Use the AWS provider."
                ---
                # AWS Provider Overview

                Configure the AWS provider.
                """);
    entries.put(
        "provider-v1/website/docs/r/vpc.html.markdown",
        """
                        ---
                        page_title: "AWS: aws_vpc"
                        description: |-
                          Provides a VPC resource.
                        ---
                        # Resource
                        """);
    entries.put(
        "provider-v1/website/docs/d/caller_identity.html.markdown",
        "# Caller identity\n\nReads identity.");
    entries.put(
        "provider-v1/website/docs/functions/arn_parse.md", "# ARN parser\n\nParses an ARN.");
    entries.put(
        "provider-v1/website/docs/guides/version-4-upgrade.html.markdown", "# Upgrade guide\n");
    var archive = archive(entries);

    var result = TerraformMetadataExtractor.extract(archive, true);

    assertThat(result.documents())
        .extracting(TerraformMetadataExtractor.ExtractedDocument::path)
        .containsExactly(
            "README.md",
            "data-sources/caller_identity.md",
            "functions/arn_parse.md",
            "guides/version-4-upgrade.md",
            "index.md",
            "resources/vpc.md");
    assertThat(result.symbols())
        .extracting(symbol -> symbol.kind() + ":" + symbol.name())
        .containsExactly(
            "data-source:caller_identity",
            "function:arn_parse",
            "guide:version-4-upgrade",
            "resource:vpc");
    assertThat(result.symbols().stream().filter(symbol -> symbol.name().equals("vpc")).findFirst())
        .hasValueSatisfying(
            symbol -> {
              assertThat(symbol.description()).isEqualTo("Provides a VPC resource.");
              assertThat(symbol.path()).isEqualTo("resources/vpc.md");
              assertThat(symbol.type()).isEqualTo("resource");
            });
    assertThat(
            result.documents().stream()
                .filter(document -> document.path().equals("index.md"))
                .findFirst())
        .hasValueSatisfying(
            document ->
                assertThat(new String(document.content(), StandardCharsets.UTF_8))
                    .contains("# AWS Provider Overview", "Configure the AWS provider.")
                    .doesNotContain("# Welcome", "Contribute to provider development."));
    assertThat(
            result.documents().stream()
                .filter(document -> document.path().equals("resources/vpc.md"))
                .findFirst())
        .hasValueSatisfying(
            document ->
                assertThat(new String(document.content(), StandardCharsets.UTF_8))
                    .startsWith("# Resource")
                    .doesNotContain("page_title:", "description:", "---"));
  }

  @Test
  void treatsHeredocBodiesAsTerraformExpressionsInsteadOfStructuralBraces() throws IOException {
    var archive =
        archive(
            Map.of(
                "module-v1/README.md", "# Web site\n",
                "module-v1/variables.tf",
                    """
                        variable "site_config" {
                          description = <<DESCRIPTION
                            A configuration document with nested braces.
                            DESCRIPTION
                          default = <<JSON
                            {
                              "route": "// not a comment",
                              "template": "${ignored_inside_heredoc}",
                              "nested": { "enabled": true }
                            }
                            JSON
                        }

                        variable "enabled" {
                          type    = bool
                          default = true
                        }
                        """));

    var result = TerraformMetadataExtractor.extract(archive, false);

    assertThat(result.symbols())
        .extracting(TerraformMetadataExtractor.ExtractedSymbol::name)
        .containsExactly("enabled", "site_config");
    assertThat(
            result.symbols().stream()
                .filter(symbol -> symbol.name().equals("site_config"))
                .findFirst())
        .hasValueSatisfying(
            symbol -> {
              assertThat(symbol.defaultValue()).contains("<<JSON", "nested", "JSON");
              assertThat(symbol.description())
                  .isEqualTo("A configuration document with nested braces.");
              assertThat(symbol.required()).isFalse();
            });
  }

  private Path archive(Map<String, String> entries) throws IOException {
    var path = temporaryDirectory.resolve("source.zip");
    try (var output = new ZipOutputStream(Files.newOutputStream(path))) {
      for (var entry : entries.entrySet()) {
        output.putNextEntry(new ZipEntry(entry.getKey()));
        output.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
        output.closeEntry();
      }
    }
    return path;
  }
}
