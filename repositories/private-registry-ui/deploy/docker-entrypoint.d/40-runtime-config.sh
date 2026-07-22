#!/bin/sh
set -eu

: "${REGISTRY_API_BASE_URL:=/api/v1}"
: "${REGISTRY_JFROG_HOSTNAME:=}"
: "${REGISTRY_ENVIRONMENT:=unknown}"
: "${REGISTRY_SUPPORT_URL:=}"

case "${REGISTRY_API_BASE_URL}" in
  /*) ;;
  *) echo 'REGISTRY_API_BASE_URL must be a same-origin absolute path' >&2; exit 1 ;;
esac

mkdir -p /usr/share/nginx/html/config
envsubst \
  '${REGISTRY_API_BASE_URL} ${REGISTRY_JFROG_HOSTNAME} ${REGISTRY_ENVIRONMENT} ${REGISTRY_SUPPORT_URL}' \
  < /opt/registry/runtime.json.template \
  > /usr/share/nginx/html/config/runtime.json
