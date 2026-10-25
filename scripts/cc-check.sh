#!/usr/bin/env bash
# Cyclomatic complexity gate (Lizard 1.24.3 in a pinned container): every function must have CCN <= CC_LIMIT.
#   ./scripts/cc-check.sh [--root DIR] [FILE...]      default: all Java sources under */src/main/java
#   CC_LIMIT=15 ./scripts/cc-check.sh payment-service/src/main/java/.../PaymentService.java
# Prints the function table of the scanned files and the violations; exit 0 PASS, 1 FAIL, 2 scanner error.
# Also used by the git pre-commit hook (scripts/git-hooks/pre-commit) with --root pointing at the staged snapshot.
set -uo pipefail
cd "$(dirname "$0")/.."
export MSYS_NO_PATHCONV=1

CC_LIMIT=${CC_LIMIT:-10}
IMAGE=plantshop/lizard:1.24.3-py3.13.17
ROOT=.
if [ "${1:-}" = "--root" ]; then ROOT=$2; shift 2; fi
RED=$'\e[1;31m'; GREEN=$'\e[1;32m'; RESET=$'\e[0m'

if [ $# -eq 0 ]; then
  mapfile -t FILES < <(cd "$ROOT" && git ls-files '*/src/main/java/*.java' 2>/dev/null || find . -path '*/src/main/java/*.java')
else
  FILES=("$@")
fi
[ ${#FILES[@]} -gt 0 ] || { echo "cc-check: no Java files to scan"; exit 0; }

docker image inspect "$IMAGE" > /dev/null 2>&1 || docker build -q -t "$IMAGE" tools/lizard > /dev/null \
  || { echo "cc-check: cannot build $IMAGE (is Docker running?)" >&2; exit 2; }
host_root=$(cd "$ROOT" && (pwd -W 2>/dev/null || pwd))

# -C sets the warning threshold; -w prints only the warnings, one per function over the limit. Lizard exits
# non-zero when a function exceeds its own default threshold (15), so the report run raises it out of the way.
report=$(docker run --rm --network none --cpus 1 --memory 256m -v "$host_root:/src:ro" "$IMAGE" \
  -l java -C 1000 "${FILES[@]}") || { echo "cc-check: scanner failed" >&2; exit 2; }
warnings=$(docker run --rm --network none --cpus 1 --memory 256m -v "$host_root:/src:ro" "$IMAGE" \
  -l java -C "$CC_LIMIT" -w "${FILES[@]}")

echo "$report" | sed -n '/^  NLOC/,/^-*$/p; /^ *[0-9]\+ \+[0-9]\+ .*@/p' | awk '!seen[$0]++' | head -60
echo
if [ -n "$warnings" ]; then
  echo "${RED}FAIL: cyclomatic complexity limit CC <= $CC_LIMIT exceeded${RESET}"
  echo "$warnings" | sed -E "s/^/${RED}  /; s/$/${RESET}/"
  exit 1
fi
max=$(echo "$report" | awk '$6 ~ /@/ && $2 > m { m = $2 } END { print m + 0 }')
echo "${GREEN}PASS: ${#FILES[@]} file(s), highest CCN $max <= $CC_LIMIT${RESET}"
