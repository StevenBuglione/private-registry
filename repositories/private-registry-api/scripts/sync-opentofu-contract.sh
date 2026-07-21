#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMMIT="$(tr -d '\r\n' < "${ROOT}/contracts/upstream/OPEN_TOFU_COMMIT")"
URL="https://raw.githubusercontent.com/opentofu/registry-ui/${COMMIT}/backend/internal/server/openapi.yml"
OUTPUT="${ROOT}/api/opentofu-compatibility-upstream.yaml"
TMP="$(mktemp /tmp/opentofu-openapi.XXXXXX)"
trap 'rm -f -- "${TMP}"' EXIT
curl --fail --location --silent --show-error "${URL}" -o "${TMP}"
grep -q 'OpenTofu Registry Docs API' "${TMP}"
mv "${TMP}" "${OUTPUT}"
echo "Synchronized OpenTofu compatibility contract at ${COMMIT}"
