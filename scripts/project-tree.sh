#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
find "${ROOT}" \
  -path "${ROOT}/.git" -prune -o \
  -path "${ROOT}/.bootstrap" -prune -o \
  -path "${ROOT}/repositories/private-registry-ui/app" -prune -o \
  -path '*/.terraform' -prune -o \
  -path '*/.gradle' -prune -o \
  -path '*/build' -prune -o \
  -path '*/node_modules' -prune -o \
  -type f -print \
  | sed "s#^${ROOT}/##" \
  | sort
