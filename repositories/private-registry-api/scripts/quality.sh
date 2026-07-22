#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODE="${1:-local}"

case "${MODE}" in
  format)
    TASKS=(spotlessApply)
    ;;
  local)
    TASKS=(qualityLocal)
    ;;
  pr)
    TASKS=(qualityPr)
    ;;
  nightly)
    TASKS=(qualityNightly)
    ;;
  sonar-input)
    TASKS=(classes testClasses jacocoTestReport)
    ;;
  *)
    echo "Usage: $0 {format|local|pr|nightly|sonar-input}" >&2
    exit 2
    ;;
esac

cd "${ROOT}"
exec ./gradlew --no-daemon --stacktrace "${TASKS[@]}"
