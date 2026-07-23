#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d /tmp/private-registry-validate.XXXXXX)"
cleanup() { rm -rf -- "${TMP_DIR}"; }
trap cleanup EXIT

printf 'Validating blueprint at %s\n' "${ROOT}"

# Optionally enforce an organization-neutral deliverable in downstream forks.
# Set FORBIDDEN_ORG_NAME in CI when a specific source organization name must not appear.
if [[ -n "${FORBIDDEN_ORG_NAME:-}" ]] && grep -Rni --exclude-dir=.git --exclude='validate-blueprint.sh' "${FORBIDDEN_ORG_NAME}" "${ROOT}"; then
  echo 'ERROR: prohibited organization-specific name found' >&2
  exit 1
fi

python3 "${ROOT}/scripts/validate_contracts.py"
python3 "${ROOT}/scripts/validate_deployment_handoff.py"

while IFS= read -r -d '' contract; do
  relative="${contract#${ROOT}/contracts/}"
  api_copy="${ROOT}/repositories/private-registry-api/contracts/${relative}"
  if [[ ! -f "${api_copy}" ]] || ! cmp -s "${contract}" "${api_copy}"; then
    echo "ERROR: API contract copy is missing or stale: ${relative}" >&2
    exit 1
  fi
done < <(find "${ROOT}/contracts" -type f -print0)
echo 'contracts: root and API copies match'

if ! cmp -s "${ROOT}/scripts/validate_contracts.py" "${ROOT}/repositories/private-registry-api/scripts/validate-contracts.py"; then
  echo 'ERROR: root and API contract validators have drifted' >&2
  exit 1
fi

python3 - "${ROOT}" <<'PYVALIDATE'
from pathlib import Path
import os
import sys

root = Path(sys.argv[1])
ignored_parts = {".bootstrap", ".git", ".gradle", ".terraform", "build", "dist", "node_modules"}

def source_files(start: Path, suffixes: tuple[str, ...]) -> list[Path]:
    result = []
    for directory, child_directories, filenames in os.walk(start):
        current = Path(directory)
        child_directories[:] = [
            name
            for name in child_directories
            if name not in ignored_parts
            and not (name == "app" and "private-registry-ui" in current.parts)
        ]
        result.extend(current / name for name in filenames if name.endswith(suffixes))
    return sorted(result)

try:
    import yaml
except ImportError:
    print("yaml:  skipped (PyYAML unavailable)")
else:
    files = source_files(root, (".yml", ".yaml"))
    for path in files:
        with path.open(encoding="utf-8") as handle:
            yaml.safe_load(handle)
    print(f"yaml:  parsed {len(files)} files")

try:
    import hcl2
except ImportError:
    print("hcl:   skipped (python-hcl2 unavailable)")
else:
    files = source_files(root / "repositories/private-registry-api/infrastructure/terraform", (".tf",))
    for path in files:
        with path.open(encoding="utf-8") as handle:
            hcl2.load(handle)
    print(f"hcl:   parsed {len(files)} Terraform files")
PYVALIDATE

while IFS= read -r file; do
  bash -n "${file}"
done < <(find "${ROOT}" -type f -name '*.sh' \
  -not -path '*/.git/*' \
  -not -path '*/node_modules/*' \
  -not -path '*/private-registry-ui/app/*' \
  | sort)

echo 'shell: syntax checks passed'

python3 "${ROOT}/repositories/private-registry-ui/scripts/check-runtime-template.py"
echo 'ui:    runtime configuration template passed'

docker compose -f "${ROOT}/repositories/private-registry-api/compose.yaml" config --quiet
echo 'compose: configuration is valid'

java_major=""
if command -v java >/dev/null 2>&1; then
  java_major="$(java -version 2>&1 | sed -nE '1s/.*version "([0-9]+).*/\1/p')"
fi
if [[ "${SKIP_JAVA_VALIDATION:-false}" == "true" ]]; then
  echo 'java:  skipped here (enforced by the dedicated API quality workflow)'
elif [[ -x "${ROOT}/repositories/private-registry-api/gradlew" && "${java_major}" == "25" ]]; then
  (
    cd "${ROOT}/repositories/private-registry-api"
    ./gradlew --no-daemon check bootJar
  )
  echo 'java:  Gradle checks and executable JAR build passed'
else
  echo 'java:  skipped (Java 25 or Gradle wrapper unavailable)'
fi

if command -v terraform >/dev/null 2>&1 && [[ -d "${ROOT}/repositories/private-registry-api/infrastructure/terraform" ]]; then
  terraform_root="${ROOT}/repositories/private-registry-api/infrastructure/terraform"
  plugin_cache="${TMP_DIR}/terraform-plugin-cache"
  mkdir -p "${plugin_cache}"
  export TF_PLUGIN_CACHE_DIR="${plugin_cache}"
  terraform -chdir="${terraform_root}" fmt -check -recursive
  for terraform_module in bootstrap global modules/platform live/dev live/prod live/dr jfrog; do
    terraform -chdir="${terraform_root}/${terraform_module}" init -backend=false -input=false >/dev/null
    terraform -chdir="${terraform_root}/${terraform_module}" validate
  done
  echo 'tf:    formatting and validation passed for every root'
else
  echo 'tf:    skipped (Terraform CLI unavailable)'
fi

EXPORT_DIR="${TMP_DIR}/exported"
bash "${ROOT}/scripts/export-repositories.sh" "${EXPORT_DIR}" >/dev/null
for repo in private-registry-ui private-registry-api; do
  [[ -f "${EXPORT_DIR}/${repo}/README.md" ]] || { echo "ERROR: export missing ${repo}/README.md" >&2; exit 1; }
done
echo 'export: two repository templates exported safely'

if grep -RniE --exclude-dir=.git --exclude-dir=.terraform --exclude-dir=node_modules --exclude-dir=dist --exclude='validate-blueprint.sh' '(AKIA[0-9A-Z]{16}|aws_secret_access_key[[:space:]]*=|-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----)' "${ROOT}"; then
  echo 'ERROR: possible secret material found' >&2
  exit 1
fi

echo 'Blueprint validation completed successfully.'
