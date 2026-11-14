#!/usr/bin/env bash
# Waits until each app runs the expected image on a healthy revision. It uses the Azure control plane because
# build agents are outside the portal's allowed networks and the API has no public address.
set -euo pipefail

RESOURCE_GROUP="$1"
TAG="$2"

for APP in careconnect-api careconnect-web; do
  for ATTEMPT in $(seq 1 40); do
    REVISION="$(az containerapp show -g "$RESOURCE_GROUP" -n "$APP" --query properties.latestRevisionName -o tsv)"
    IMAGE="$(az containerapp revision show -g "$RESOURCE_GROUP" -n "$APP" --revision "$REVISION" --query 'properties.template.containers[0].image' -o tsv)"
    HEALTH="$(az containerapp revision show -g "$RESOURCE_GROUP" -n "$APP" --revision "$REVISION" --query properties.healthState -o tsv)"
    echo "$APP attempt $ATTEMPT: $REVISION health=$HEALTH"
    if [[ "$IMAGE" == *":$TAG" && "$HEALTH" == "Healthy" ]]; then
      continue 2
    fi
    sleep 15
  done
  echo "ERROR: $APP did not become healthy on tag $TAG" >&2
  exit 1
done
