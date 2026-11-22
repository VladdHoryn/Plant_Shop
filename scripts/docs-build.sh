#!/usr/bin/env bash
# Lab 11: builds the knowledge base exactly like CI (.github/workflows/docs.yml): mkdocs build --strict.
# Any warning (dead link or anchor, page outside nav, broken page) fails the build with exit code 1.
#   ./scripts/docs-build.sh            strict build into site/
#   ./scripts/docs-build.sh serve      live preview on http://localhost:8000
# MkDocs runs in the pinned image tools/mkdocs (Python is not installed on the developer machine).
set -uo pipefail
cd "$(dirname "$0")/.."
export MSYS_NO_PATHCONV=1

IMAGE=plantshop/mkdocs:1.6.1-material9.7.6
docker image inspect "$IMAGE" > /dev/null 2>&1 || docker build -q -t "$IMAGE" tools/mkdocs > /dev/null \
  || { echo "docs-build: cannot build $IMAGE (is Docker running?)" >&2; exit 2; }
root=$(pwd -W 2>/dev/null || pwd)

if [ "${1:-}" = "serve" ]; then
  exec docker run --rm -it -p 8000:8000 -v "$root:/docs" "$IMAGE" serve --dev-addr 0.0.0.0:8000
fi
docker run --rm -v "$root:/docs" "$IMAGE" build --strict "$@"
status=$?
if [ $status -eq 0 ]; then echo "docs-build: PASS (strict)"; else echo "docs-build: FAIL, exit code $status"; fi
exit $status
