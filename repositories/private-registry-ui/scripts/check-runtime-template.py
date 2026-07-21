#!/usr/bin/env python3
from pathlib import Path
import json
import os
import re
import sys

path = Path(__file__).resolve().parents[1] / "deploy/nginx/runtime.json.template"
text = path.read_text()
values = {
    "REGISTRY_DATA_API_URL": "",
    "REGISTRY_ENTERPRISE_API_URL": "/api/v1/enterprise",
    "REGISTRY_JFROG_HOSTNAME": "artifacts.example.invalid",
    "REGISTRY_ENVIRONMENT": "test",
    "REGISTRY_SUPPORT_URL": "https://support.example.invalid/registry",
    "REGISTRY_FEATURE_PROVIDERS": "true",
    "REGISTRY_FEATURE_MODULES": "true",
    "REGISTRY_FEATURE_SECURITY": "true",
    "REGISTRY_FEATURE_AUDIT": "false",
}
for key, value in values.items():
    text = text.replace("${" + key + "}", value)
remaining = re.findall(r"\$\{[^}]+\}", text)
if remaining:
    print(f"Unresolved template variables: {remaining}", file=sys.stderr)
    raise SystemExit(1)
json.loads(text)
print("runtime configuration template is valid JSON after substitution")
