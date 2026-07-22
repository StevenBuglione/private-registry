#!/usr/bin/env python3
from pathlib import Path
import json
import re
import sys

path = Path(__file__).resolve().parents[1] / "deploy/nginx/runtime.json.template"
text = path.read_text()
values = {
    "REGISTRY_API_BASE_URL": "/api/v1",
    "REGISTRY_JFROG_HOSTNAME": "artifacts.example.invalid",
    "REGISTRY_ENVIRONMENT": "test",
    "REGISTRY_SUPPORT_URL": "https://support.example.invalid/registry",
}
for key, value in values.items():
    text = text.replace("${" + key + "}", value)
remaining = re.findall(r"\$\{[^}]+\}", text)
if remaining:
    print(f"Unresolved template variables: {remaining}", file=sys.stderr)
    raise SystemExit(1)
candidate = json.loads(text)
if not candidate["apiBaseUrl"].startswith("/"):
    raise SystemExit("apiBaseUrl must remain same-origin")
print("runtime configuration template is valid JSON after substitution")
