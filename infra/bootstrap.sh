#!/usr/bin/env bash
# One-time setup, run once by an Azure administrator. Creates the Terraform state storage and shared registry.
# Usage: ./bootstrap.sh <globally-unique-prefix> [location]
set -euo pipefail

PREFIX="${1:?Give a short lowercase prefix, for example ccacme}"
LOCATION="${2:-australiaeast}"
RG="rg-careconnect-shared"
STORAGE="${PREFIX}ccstate"
ACR="${PREFIX}ccacr"

az group create --name "$RG" --location "$LOCATION" --output none
az storage account create --name "$STORAGE" --resource-group "$RG" --location "$LOCATION" \
  --sku Standard_ZRS --min-tls-version TLS1_2 --allow-blob-public-access false --output none
az storage container create --name tfstate --account-name "$STORAGE" --auth-mode login --output none
az acr create --name "$ACR" --resource-group "$RG" --location "$LOCATION" --sku Standard \
  --admin-enabled false --output none

cat <<EOF
Set these as GitHub repository variables:
  TF_STATE_RESOURCE_GROUP=$RG
  TF_STATE_STORAGE_ACCOUNT=$STORAGE
  ACR_NAME=$ACR
  ACR_RESOURCE_GROUP=$RG
EOF
