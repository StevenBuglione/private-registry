#!/bin/sh
set -eu

: "${REGISTRY_DATA_API_URL:=/registry/docs/}"
: "${REGISTRY_ENTERPRISE_API_URL:=/api/v1/enterprise}"
: "${REGISTRY_JFROG_HOSTNAME:=}"
: "${REGISTRY_ENVIRONMENT:=unknown}"
: "${REGISTRY_SUPPORT_URL:=}"
: "${REGISTRY_FEATURE_PROVIDERS:=true}"
: "${REGISTRY_FEATURE_MODULES:=true}"
: "${REGISTRY_FEATURE_SECURITY:=true}"
: "${REGISTRY_FEATURE_AUDIT:=false}"

case "${REGISTRY_FEATURE_PROVIDERS}" in true|false) ;; *) echo 'REGISTRY_FEATURE_PROVIDERS must be true or false' >&2; exit 1 ;; esac
case "${REGISTRY_FEATURE_MODULES}" in true|false) ;; *) echo 'REGISTRY_FEATURE_MODULES must be true or false' >&2; exit 1 ;; esac
case "${REGISTRY_FEATURE_SECURITY}" in true|false) ;; *) echo 'REGISTRY_FEATURE_SECURITY must be true or false' >&2; exit 1 ;; esac
case "${REGISTRY_FEATURE_AUDIT}" in true|false) ;; *) echo 'REGISTRY_FEATURE_AUDIT must be true or false' >&2; exit 1 ;; esac

mkdir -p /usr/share/nginx/html/config
envsubst \
  '${REGISTRY_DATA_API_URL} ${REGISTRY_ENTERPRISE_API_URL} ${REGISTRY_JFROG_HOSTNAME} ${REGISTRY_ENVIRONMENT} ${REGISTRY_SUPPORT_URL} ${REGISTRY_FEATURE_PROVIDERS} ${REGISTRY_FEATURE_MODULES} ${REGISTRY_FEATURE_SECURITY} ${REGISTRY_FEATURE_AUDIT}' \
  < /opt/private-registry/runtime.json.template \
  > /usr/share/nginx/html/config/runtime.json
