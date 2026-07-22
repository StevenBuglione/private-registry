#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
[[ -f "${ROOT}/app/package.json" ]] || { echo "Run scripts/import-upstream.sh first" >&2; exit 1; }
mkdir -p "${ROOT}/app/src/enterprise"
cp -a "${ROOT}/overlays/src/enterprise/." "${ROOT}/app/src/enterprise/"
python3 "${ROOT}/scripts/patch-upstream.py"
cat <<'EOF'
Enterprise overlays and reviewed source hooks applied.
Branding, public-registry-only behavior, and design-system remediation remain governed by PATCHES.md and the integration checklist.
EOF
