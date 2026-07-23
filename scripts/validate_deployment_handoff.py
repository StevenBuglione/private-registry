#!/usr/bin/env python3
"""Validate the deployment handoff's files, links, and process contracts."""

from __future__ import annotations

import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DOCS = (
    ROOT / "docs/30-deployment-configuration-handoff.md",
    ROOT / "docs/31-environment-variable-reference.md",
    ROOT / "docs/32-deployment-readiness-audit.md",
)
ENVIRONMENT_DIRECTORY = ROOT / "repositories/private-registry-api/deploy/environment"
TEMPLATES = {
    "api.env.example": {
        "SPRING_DATASOURCE_USERNAME",
        "SPRING_DATASOURCE_PASSWORD",
        "REGISTRY_ALLOWED_ALB_SIGNER_ARN",
        "REGISTRY_ALLOWED_OIDC_CLIENT_ID",
        "REGISTRY_ALLOWED_OIDC_ISSUER",
        "REGISTRY_ENTRA_APM_GROUPS",
        "REGISTRY_ENTRA_ADMIN_GROUP_ID",
        "REGISTRY_ARTIFACTORY_URL",
        "REGISTRY_ARTIFACTORY_ACCESS_TOKEN",
        "REGISTRY_WEBHOOK_SECRET",
    },
    "indexer.env.example": {
        "SPRING_MAIN_WEB_APPLICATION_TYPE",
        "SPRING_MAIN_KEEP_ALIVE",
        "SPRING_DATASOURCE_USERNAME",
        "REGISTRY_ARTIFACTORY_URL",
        "REGISTRY_EVENTING_ENABLED",
        "REGISTRY_INGESTION_ENABLED",
        "REGISTRY_GOVERNED_REPOSITORIES",
    },
    "migrations.env.example": {
        "SPRING_MAIN_WEB_APPLICATION_TYPE",
        "SPRING_FLYWAY_URL",
        "SPRING_FLYWAY_USER",
        "SPRING_FLYWAY_PASSWORD",
        "REGISTRY_RUNTIME_EXIT_AFTER_STARTUP",
    },
    "seeder.env.example": {
        "SPRING_MAIN_WEB_APPLICATION_TYPE",
        "REGISTRY_ARTIFACTORY_URL",
        "REGISTRY_ARTIFACTORY_ACCESS_TOKEN",
        "REGISTRY_SEED_ENABLED",
        "REGISTRY_SEED_EXIT_AFTER_COMPLETION",
    },
    "ui.env.example": {
        "REGISTRY_API_BASE_URL",
        "REGISTRY_JFROG_HOSTNAME",
        "REGISTRY_ENVIRONMENT",
        "REGISTRY_SUPPORT_URL",
    },
}
OBSOLETE_ACTIVE_VARIABLES = {
    "REGISTRY_DATABASE_HOST",
    "REGISTRY_DATABASE_PORT",
    "REGISTRY_DATABASE_NAME",
    "REGISTRY_DATABASE_USER",
    "REGISTRY_DATABASE_SECRET_ARN",
    "REGISTRY_JFROG_BASE_URL",
    "REGISTRY_JFROG_TOKEN_SECRET_ARN",
    "REGISTRY_AUTHORIZATION_CONFIG_SECRET_ARN",
}
MARKDOWN_LINK = re.compile(r"\[[^\]]+\]\(([^)]+)\)")
ENVIRONMENT_ENTRY = re.compile(r"^([A-Z][A-Z0-9_]*)=(.*)$")


def fail(message: str) -> None:
    print(f"ERROR: {message}", file=sys.stderr)
    raise SystemExit(1)


def validate_links(document: Path) -> None:
    content = document.read_text(encoding="utf-8")
    for raw_target in MARKDOWN_LINK.findall(content):
        target = raw_target.split("#", 1)[0]
        if not target or "://" in target or target.startswith(("mailto:", "#")):
            continue
        resolved = (document.parent / target).resolve()
        if not resolved.exists():
            fail(f"{document.relative_to(ROOT)} has a broken link: {raw_target}")


def read_environment(path: Path) -> dict[str, str]:
    result: dict[str, str] = {}
    for line_number, line in enumerate(
        path.read_text(encoding="utf-8").splitlines(), start=1
    ):
        stripped = line.strip()
        if not stripped or stripped.startswith("#"):
            continue
        match = ENVIRONMENT_ENTRY.fullmatch(stripped)
        if match is None:
            fail(f"{path.relative_to(ROOT)}:{line_number} is not KEY=value")
        name, value = match.groups()
        if name in result:
            fail(f"{path.relative_to(ROOT)} contains duplicate key {name}")
        result[name] = value
    return result


def main() -> None:
    for document in DOCS:
        if not document.is_file():
            fail(f"missing deployment document {document.relative_to(ROOT)}")
        validate_links(document)

    parsed_templates: dict[str, dict[str, str]] = {}
    for filename, required_keys in TEMPLATES.items():
        path = ENVIRONMENT_DIRECTORY / filename
        if not path.is_file():
            fail(f"missing process template {path.relative_to(ROOT)}")
        entries = read_environment(path)
        missing = required_keys - entries.keys()
        if missing:
            fail(f"{path.relative_to(ROOT)} is missing {sorted(missing)}")
        obsolete = OBSOLETE_ACTIVE_VARIABLES & entries.keys()
        if obsolete:
            fail(f"{path.relative_to(ROOT)} uses obsolete variables {sorted(obsolete)}")
        parsed_templates[filename] = entries

    required_values = {
        "api.env.example": {
            "SPRING_DATASOURCE_USERNAME": "registry_web",
            "SPRING_FLYWAY_ENABLED": "false",
            "REGISTRY_SECURITY_PERMIT_ALL": "false",
            "REGISTRY_INGESTION_ENABLED": "false",
        },
        "indexer.env.example": {
            "SPRING_DATASOURCE_USERNAME": "registry_indexer",
            "SPRING_FLYWAY_ENABLED": "false",
            "REGISTRY_WEBHOOK_ENABLED": "false",
            "REGISTRY_INGESTION_ENABLED": "true",
        },
        "migrations.env.example": {
            "SPRING_FLYWAY_ENABLED": "true",
            "REGISTRY_RUNTIME_EXIT_AFTER_STARTUP": "true",
            "REGISTRY_EVENTING_ENABLED": "false",
        },
        "seeder.env.example": {
            "SPRING_DATASOURCE_USERNAME": "registry_indexer",
            "SPRING_FLYWAY_ENABLED": "false",
            "REGISTRY_SEED_ENABLED": "true",
            "REGISTRY_SEED_EXIT_AFTER_COMPLETION": "true",
        },
    }
    for filename, expectations in required_values.items():
        entries = parsed_templates[filename]
        for name, expected in expectations.items():
            if entries.get(name) != expected:
                fail(f"{filename} must set {name}={expected}")

    ui_keys = parsed_templates["ui.env.example"].keys()
    if any(
        marker in name
        for name in ui_keys
        for marker in ("SECRET", "PASSWORD", "ACCESS_TOKEN", "CLIENT_ID")
    ):
        fail("ui.env.example must contain public runtime values only")

    active_configuration = (
        ROOT / "repositories/private-registry-api/src/main/resources/application.yaml",
        ROOT / "repositories/private-registry-api/compose.yaml",
        ROOT / "repositories/private-registry-api/.env.example",
        ROOT / "repositories/private-registry-api/scripts/export-jfrog-env.ps1",
        ROOT
        / "repositories/private-registry-api/scripts/bootstrap-local-eventing-env.ps1",
    )
    for path in active_configuration:
        content = path.read_text(encoding="utf-8")
        if "trialwbgt07" in content:
            fail(f"{path.relative_to(ROOT)} contains the original JFrog tenant")

    print(
        f"deployment handoff: {len(DOCS)} documents and "
        f"{len(TEMPLATES)} process templates passed"
    )


if __name__ == "__main__":
    main()
