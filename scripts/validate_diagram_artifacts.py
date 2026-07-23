#!/usr/bin/env python3
"""Validate the committed Mermaid source and extracted image package."""

from pathlib import Path
import re
import sys


ROOT = Path(__file__).resolve().parents[1]
DIAGRAM_DIRECTORY = ROOT / "docs" / "diagrams"


def fail(message: str) -> None:
    print(f"diagram validation failed: {message}", file=sys.stderr)
    raise SystemExit(1)


sources = sorted(DIAGRAM_DIRECTORY.glob("*.mmd"))
if len(sources) < 9:
    fail(f"expected at least 9 Mermaid sources, found {len(sources)}")

for source in sources:
    contents = source.read_text(encoding="utf-8")
    if re.search(r"</?[A-Za-z][^>]*>", contents):
        fail(f"{source.name} contains an HTML-like label")
    if "\\n" in contents:
        fail(f"{source.name} contains a literal newline escape")

    for extension, signature in (
        (".svg", b"<svg"),
        (".png", b"\x89PNG\r\n\x1a\n"),
    ):
        artifact = source.with_suffix(extension)
        if not artifact.is_file():
            fail(f"missing {artifact.relative_to(ROOT)}")
        data = artifact.read_bytes()
        if len(data) < 512:
            fail(f"{artifact.relative_to(ROOT)} is unexpectedly small")
        if signature not in data[:512]:
            fail(f"{artifact.relative_to(ROOT)} has an invalid signature")

print(f"diagrams: validated {len(sources)} Mermaid source/SVG/PNG sets")
