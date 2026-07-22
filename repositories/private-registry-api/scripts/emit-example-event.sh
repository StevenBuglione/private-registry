#!/usr/bin/env bash
set -euo pipefail
: "${EVENT_BUS_NAME:?Set EVENT_BUS_NAME}"
: "${AWS_REGION:?Set AWS_REGION}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
detail="$(jq -c . "${ROOT}/contracts/examples/package-promoted-event.json")"
aws events put-events --region "${AWS_REGION}" --entries "$(jq -nc --arg bus "$EVENT_BUS_NAME" --arg detail "$detail" '[{EventBusName:$bus,Source:"private-registry.release",DetailType:"PackagePromoted",Detail:$detail}]')"
