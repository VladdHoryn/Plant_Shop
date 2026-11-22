#!/usr/bin/env bash
# Lab 11: documentation linters, the same as the "lint" job of .github/workflows/docs.yml.
#   1. markdownlint-cli2 0.18.1 over the product documentation (.markdownlint-cli2.yaml)
#   2. Spectral 6.15.0 over docs/api/openapi.yaml (.spectral.yaml), threshold: 0 errors
#   3. review_date audit of the front matter passports (report only)
set -uo pipefail
cd "$(dirname "$0")/.."
export MSYS_NO_PATHCONV=1
root=$(pwd -W 2>/dev/null || pwd)
fail=0

echo "== 1. markdownlint"
docker run --rm -v "$root:/workdir" davidanson/markdownlint-cli2:v0.18.1 || fail=1

echo "== 2. Spectral (docs/api/openapi.yaml)"
docker run --rm -v "$root:/work" -w /work stoplight/spectral:6.15.0 \
  lint docs/api/openapi.yaml --ruleset .spectral.yaml --fail-severity error || fail=1

echo "== 3. review_date audit"
./scripts/doc-review-audit.sh | tail -2

if (( fail )); then echo "docs-lint: FAIL"; else echo "docs-lint: PASS"; fi
exit $fail
