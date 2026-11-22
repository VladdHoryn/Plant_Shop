#!/usr/bin/env bash
# Lab 11: contract drift check - does the running API answer as docs/api/openapi.yaml promises?
# Schemathesis 4.1.0 generates requests from the contract and validates every response against it:
# status code documented, content type documented, body matches the schema, no 5xx server errors.
#   docker compose up -d --build        (the whole stack must be healthy first)
#   ./scripts/api-contract-test.sh [extra schemathesis options]
set -uo pipefail
cd "$(dirname "$0")/.."
export MSYS_NO_PATHCONV=1
root=$(pwd -W 2>/dev/null || pwd)
URL=${API_URL:-http://host.docker.internal:8080}
docker run --rm --add-host=host.docker.internal:host-gateway -v "$root:/work" -w /work \
  schemathesis/schemathesis:v4.1.0 run docs/api/openapi.yaml --url "$URL" \
  --checks not_a_server_error,status_code_conformance,content_type_conformance,response_schema_conformance \
  --max-examples "${MAX_EXAMPLES:-50}" --seed "${SEED:-11}" --no-color "$@"
