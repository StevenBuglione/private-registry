#!/bin/sh
set -eu

REGION="${AWS_DEFAULT_REGION:-us-east-1}"
ACCOUNT_ID="000000000000"
EVENT_BUS="${REGISTRY_EVENT_BUS_NAME:-registry-catalog}"
QUEUE_NAME="${REGISTRY_EVENT_QUEUE_NAME:-registry-catalog-events}"
DLQ_NAME="${REGISTRY_EVENT_DLQ_NAME:-registry-catalog-events-dlq}"
BUCKET="${REGISTRY_DOCUMENT_BUCKET:-registry-documents}"

awslocal events create-event-bus --name "$EVENT_BUS" >/dev/null 2>&1 || true
awslocal s3api create-bucket --bucket "$BUCKET" >/dev/null 2>&1 || true

DLQ_URL="$(awslocal sqs create-queue --queue-name "$DLQ_NAME" --query QueueUrl --output text)"
DLQ_ARN="$(awslocal sqs get-queue-attributes --queue-url "$DLQ_URL" --attribute-names QueueArn --query Attributes.QueueArn --output text)"
QUEUE_URL="$(awslocal sqs create-queue --queue-name "$QUEUE_NAME" --attributes "{\"RedrivePolicy\":\"{\\\"deadLetterTargetArn\\\":\\\"$DLQ_ARN\\\",\\\"maxReceiveCount\\\":\\\"5\\\"}\",\"VisibilityTimeout\":\"120\"}" --query QueueUrl --output text)"
QUEUE_ARN="arn:aws:sqs:${REGION}:${ACCOUNT_ID}:${QUEUE_NAME}"

awslocal events put-rule \
  --event-bus-name "$EVENT_BUS" \
  --name registry-artifact-changed \
  --event-pattern '{"source":["registry.jfrog"],"detail-type":["CatalogArtifactChanged"]}' >/dev/null

awslocal events put-targets \
  --event-bus-name "$EVENT_BUS" \
  --rule registry-artifact-changed \
  --targets "Id=registry-indexer,Arn=$QUEUE_ARN" >/dev/null

cat <<EOF
Registry LocalStack resources are ready.
event_bus=$EVENT_BUS
queue_url=$QUEUE_URL
dead_letter_queue_url=$DLQ_URL
document_bucket=$BUCKET
EOF
