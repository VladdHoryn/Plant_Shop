#!/usr/bin/env bash
# Lab 11: generates the Java client SDK from the API contract (OpenAPI Generator 7.14.0, pinned image).
# The SDK is a build artifact of docs/api/openapi.yaml, so sdk-client/ is not committed (.gitignore).
#   ./scripts/api-sdk.sh [generator]      default generator: java (native java.net.http client, Jackson)
set -uo pipefail
cd "$(dirname "$0")/.."
export MSYS_NO_PATHCONV=1
root=$(pwd -W 2>/dev/null || pwd)
GEN=${1:-java}
rm -rf sdk-client
docker run --rm -v "$root:/local" openapitools/openapi-generator-cli:v7.14.0 generate \
  -i /local/docs/api/openapi.yaml -g "$GEN" -o /local/sdk-client \
  --additional-properties=library=native,groupId=com.plantshop,artifactId=plantshop-api-client,artifactVersion=1.0.0,apiPackage=com.plantshop.client.api,modelPackage=com.plantshop.client.model,invokerPackage=com.plantshop.client,hideGenerationTimestamp=true
