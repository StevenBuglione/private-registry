#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
UPSTREAM_REPOSITORY="$(tr -d '\r\n' < "${ROOT}/.upstream/UPSTREAM_REPOSITORY")"
UPSTREAM_COMMIT="$(tr -d '\r\n' < "${ROOT}/.upstream/OPEN_TOFU_COMMIT")"
WORK_DIR="$(mktemp -d /tmp/private-registry-ui-upstream.XXXXXX)"
cleanup() { rm -rf -- "${WORK_DIR}"; }
trap cleanup EXIT

if [[ -d "${ROOT}/app" ]] && find "${ROOT}/app" -mindepth 1 ! -name '.gitkeep' -print -quit | grep -q .; then
  echo "app/ contains files. Move or commit them before importing upstream." >&2
  exit 2
fi

mkdir -p "${WORK_DIR}/repo"
git -C "${WORK_DIR}/repo" init -q
git -C "${WORK_DIR}/repo" remote add origin "${UPSTREAM_REPOSITORY}"
git -C "${WORK_DIR}/repo" fetch --depth 1 origin "${UPSTREAM_COMMIT}"
git -C "${WORK_DIR}/repo" checkout -q --detach FETCH_HEAD

[[ -d "${WORK_DIR}/repo/frontend" ]] || { echo "Pinned upstream commit has no frontend/ directory" >&2; exit 1; }
rm -rf -- "${ROOT}/app"
mkdir -p "${ROOT}/app"
cp -a "${WORK_DIR}/repo/frontend/." "${ROOT}/app/"

for notice in LICENSE NOTICE NOTICE.md THIRD_PARTY_LICENSES.md; do
  if [[ -f "${WORK_DIR}/repo/${notice}" ]]; then
    cp "${WORK_DIR}/repo/${notice}" "${ROOT}/app/UPSTREAM_${notice//\//_}"
  fi
done

cat > "${ROOT}/app/UPSTREAM_PROVENANCE.json" <<EOF
{
  "repository": "${UPSTREAM_REPOSITORY}",
  "commit": "${UPSTREAM_COMMIT}",
  "importedAt": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "component": "frontend"
}
EOF

echo "Imported OpenTofu Registry UI frontend at ${UPSTREAM_COMMIT} into app/"
