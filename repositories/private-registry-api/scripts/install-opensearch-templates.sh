#!/usr/bin/env bash
set -euo pipefail
: "${OPENSEARCH_ENDPOINT:?Set OPENSEARCH_ENDPOINT}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
for file in "${ROOT}"/opensearch/index-templates/*.json; do
  name="$(basename "${file}" .json)"
  curl --fail --silent --show-error \
    --request PUT "${OPENSEARCH_ENDPOINT}/_index_template/${name}" \
    --header 'Content-Type: application/json' \
    --data-binary "@${file}"
done
# Initial indices/aliases should be created in an idempotent initialization job.
echo "Installed OpenSearch index templates"
