#!/usr/bin/env python3
"""Validate contract JSON and enforce the minimum cross-file invariants.

Full JSON Schema validation is performed when the optional `jsonschema` package is
available. The fallback validation deliberately uses only the Python standard
library so the blueprint can be checked in restricted build environments.
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
CONTRACTS = ROOT / "contracts"
EXAMPLES = CONTRACTS / "examples"
SHA256_RE = re.compile(r"^sha256:[0-9a-f]{64}$")


def load(path: Path) -> Any:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception as exc:  # noqa: BLE001 - actionable validation output
        raise ValueError(f"{path}: invalid JSON: {exc}") from exc


def require(mapping: dict[str, Any], path: str, keys: list[str]) -> None:
    missing = [key for key in keys if key not in mapping]
    if missing:
        raise ValueError(f"{path}: missing required keys: {', '.join(missing)}")


def validate_manifest(document: dict[str, Any], path: Path) -> None:
    require(document, str(path), ["schemaVersion", "kind", "identity", "display", "registry", "compatibility", "source", "release"])
    if document["schemaVersion"] != 1:
        raise ValueError(f"{path}: only schemaVersion 1 is supported")
    if document["kind"] not in {"module", "provider"}:
        raise ValueError(f"{path}: kind must be module or provider")

    identity = document["identity"]
    require(identity, f"{path}.identity", ["namespace", "name", "version"])
    if document["kind"] == "module":
        require(identity, f"{path}.identity", ["target"])
    if document["kind"] == "provider":
        require(document, str(path), ["provider"])

    display = document["display"]
    require(display, f"{path}.display", ["title", "description", "owners", "supportLevel", "verification", "lifecycle", "riskTier", "visibility"])
    if not display["owners"]:
        raise ValueError(f"{path}: owners must not be empty")

    registry = document["registry"]
    require(registry, f"{path}.registry", ["hostname", "repository", "source", "artifactPath"])
    release = document["release"]
    require(release, f"{path}.release", ["publishedAt", "packageDigest", "documentationPath", "documentationDigest"])
    for key in ("packageDigest", "documentationDigest"):
        if not SHA256_RE.fullmatch(release[key]):
            raise ValueError(f"{path}: {key} must be sha256:<64 lowercase hex characters>")


def validate_event(document: dict[str, Any], path: Path) -> None:
    require(document, str(path), ["schemaVersion", "eventType", "eventId", "occurredAt", "correlationId", "producer", "detail"])
    if document["schemaVersion"] != 1:
        raise ValueError(f"{path}: only schemaVersion 1 is supported")
    if document["eventType"] not in {"PackagePromoted", "PackageDeprecated", "PackageRevoked"}:
        raise ValueError(f"{path}: unsupported eventType")
    detail = document["detail"]
    require(detail, f"{path}.detail", ["kind", "namespace", "name", "version", "repository", "artifactPath", "packageDigest", "manifestPath", "manifestDigest", "sourceCommit"])
    for key in ("packageDigest", "manifestDigest"):
        if not SHA256_RE.fullmatch(detail[key]):
            raise ValueError(f"{path}: detail.{key} must be sha256:<64 lowercase hex characters>")
    if detail["kind"] == "module" and not detail.get("target"):
        raise ValueError(f"{path}: module event requires detail.target")
    if document["eventType"] in {"PackageDeprecated", "PackageRevoked"}:
        require(detail, f"{path}.detail", ["reason", "effectiveAt"])


def optional_jsonschema_validation(schema: dict[str, Any], document: dict[str, Any], path: Path) -> bool:
    try:
        import jsonschema  # type: ignore
    except ImportError:
        return False
    jsonschema.Draft202012Validator(schema).validate(document)
    print(f"schema: {path.relative_to(ROOT)}")
    return True


def main() -> int:
    errors: list[str] = []
    schema_files = sorted(CONTRACTS.glob("*.schema.json"))
    example_files = sorted(EXAMPLES.glob("*.json"))

    loaded_schemas: dict[str, dict[str, Any]] = {}
    for path in schema_files:
        try:
            loaded_schemas[path.name] = load(path)
            print(f"json:   {path.relative_to(ROOT)}")
        except ValueError as exc:
            errors.append(str(exc))

    schema_map = {
        "module-manifest.json": "package-manifest.schema.json",
        "provider-manifest.json": "package-manifest.schema.json",
        "package-promoted-event.json": "package-promoted-event.schema.json",
        "package-deprecated-event.json": "package-deprecated-event.schema.json",
        "package-revoked-event.json": "package-revoked-event.schema.json",
    }

    used_jsonschema = False
    for path in example_files:
        try:
            document = load(path)
            print(f"json:   {path.relative_to(ROOT)}")
            if "manifest" in path.name:
                validate_manifest(document, path)
            else:
                validate_event(document, path)
            schema_name = schema_map[path.name]
            if schema_name in loaded_schemas:
                used_jsonschema = optional_jsonschema_validation(loaded_schemas[schema_name], document, path) or used_jsonschema
        except Exception as exc:  # noqa: BLE001 - aggregate all validation errors
            errors.append(str(exc))

    if not used_jsonschema:
        print("note: optional jsonschema package not installed; structural fallback checks completed")

    if errors:
        for error in errors:
            print(f"ERROR: {error}", file=sys.stderr)
        return 1
    print(f"validated {len(schema_files)} schemas and {len(example_files)} examples")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
