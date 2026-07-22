#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: bootstrap-github-repositories.sh --owner OWNER [--visibility private|internal|public] [--execute]

Default mode prints the commands. --execute requires an authenticated GitHub CLI and creates/pushes:
  OWNER/private-registry-ui
  OWNER/private-registry-api
USAGE
}

OWNER=""
VISIBILITY="private"
EXECUTE=false
while [[ $# -gt 0 ]]; do
  case "$1" in
    --owner) OWNER="${2:?owner required}"; shift 2 ;;
    --visibility) VISIBILITY="${2:?visibility required}"; shift 2 ;;
    --execute) EXECUTE=true; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown argument: $1" >&2; usage >&2; exit 2 ;;
  esac
done
[[ -n "${OWNER}" ]] || { usage >&2; exit 2; }
case "${VISIBILITY}" in private|internal|public) ;; *) echo "Invalid visibility" >&2; exit 2 ;; esac

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
EXPORT_ROOT="$(mktemp -d /tmp/private-registry-export.XXXXXX)"
cleanup() { rm -rf -- "${EXPORT_ROOT}"; }
trap cleanup EXIT
"${ROOT}/scripts/export-repositories.sh" "${EXPORT_ROOT}"

for name in private-registry-ui private-registry-api; do
  path="${EXPORT_ROOT}/${name}"
  echo "gh repo create ${OWNER}/${name} --${VISIBILITY} --source ${path} --remote origin --push"
  if [[ "${EXECUTE}" == true ]]; then
    command -v gh >/dev/null || { echo "gh is required for --execute" >&2; exit 1; }
    git -C "${path}" init -b main
    git -C "${path}" add -A
    git -C "${path}" -c user.name='Repository Bootstrap' -c user.email='noreply@example.invalid' commit -m "Initialize ${name}"
    gh repo create "${OWNER}/${name}" "--${VISIBILITY}" --source "${path}" --remote origin --push
  fi
done
