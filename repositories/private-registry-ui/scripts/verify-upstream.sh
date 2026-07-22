#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
EXPECTED="$(tr -d '\r\n' < "${ROOT}/.upstream/OPEN_TOFU_COMMIT")"
[[ -f "${ROOT}/app/UPSTREAM_PROVENANCE.json" ]] || { echo "Missing app/UPSTREAM_PROVENANCE.json" >&2; exit 1; }
ACTUAL="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["commit"])' "${ROOT}/app/UPSTREAM_PROVENANCE.json")"
[[ "${ACTUAL}" == "${EXPECTED}" ]] || { echo "Imported commit ${ACTUAL} does not match approved ${EXPECTED}" >&2; exit 1; }
[[ -f "${ROOT}/app/pnpm-lock.yaml" ]] || { echo "Imported frontend must retain pnpm-lock.yaml" >&2; exit 1; }
grep -q '"packageManager": "pnpm@9.10.0"' "${ROOT}/app/package.json" || {
  echo "Imported frontend package manager does not match the reviewed pnpm version" >&2
  exit 1
}
echo "Verified imported OpenTofu UI commit ${EXPECTED}"
