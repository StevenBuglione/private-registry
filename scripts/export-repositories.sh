#!/usr/bin/env bash
set -euo pipefail

SOURCE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DESTINATION="${1:-${SOURCE_ROOT}/exported}"
DESTINATION="$(python3 -c 'import os,sys; print(os.path.abspath(sys.argv[1]))' "${DESTINATION}")"

case "${DESTINATION}" in
  /|/mnt|/mnt/data|"${SOURCE_ROOT}")
    echo "Refusing unsafe export destination: ${DESTINATION}" >&2
    exit 2
    ;;
esac

mkdir -p "${DESTINATION}"
for repo in private-registry-ui private-registry-api; do
  source_dir="${SOURCE_ROOT}/repositories/${repo}"
  target_dir="${DESTINATION}/${repo}"
  if [[ ! -d "${source_dir}" ]]; then
    echo "Missing repository template: ${source_dir}" >&2
    exit 1
  fi
  if [[ -e "${target_dir}" ]]; then
    echo "Refusing to overwrite existing export target: ${target_dir}" >&2
    exit 2
  fi
done

for repo in private-registry-ui private-registry-api; do
  source_dir="${SOURCE_ROOT}/repositories/${repo}"
  target_dir="${DESTINATION}/${repo}"
  mkdir -p "${target_dir}"
  while IFS= read -r -d '' tracked_path; do
    relative_path="${tracked_path#repositories/${repo}/}"
    source_path="${SOURCE_ROOT}/${tracked_path}"
    destination_path="${target_dir}/${relative_path}"
    if [[ ! -e "${source_path}" && ! -L "${source_path}" ]]; then
      continue
    fi
    mkdir -p "$(dirname "${destination_path}")"
    cp -Pp "${source_path}" "${destination_path}"
  done < <(
    git -C "${SOURCE_ROOT}" ls-files -z --cached --others --exclude-standard -- "repositories/${repo}/"
  )
done

printf 'Exported repositories to %s\n' "${DESTINATION}"
find "${DESTINATION}" -maxdepth 2 -type f -print | sort
