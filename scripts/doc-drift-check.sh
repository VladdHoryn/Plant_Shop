#!/usr/bin/env bash
# Lab 10: "code change = documentation change" check. Fails (exit 1) when the product documentation no longer
# describes the running system:
#   1. every REST endpoint of every controller is listed in docs/architecture/sdd.md (API contracts) as
#      `METHOD /path`;
#   2. every operation of the API contract docs/api/openapi.yaml exists in a controller (Lab 11: the API-first
#      contract must never promise a route the code does not have; replaces the README "API at a glance" check
#      of Lab 10, the README no longer lists endpoints);
#   3. the startup dependencies of every container in docker-compose.yml (depends_on, after anchors are
#      resolved by `docker compose config`) match the startup table of docs/architecture/isd.md:
#      | `service` | dep1, dep2, ... |   (dependencies sorted alphabetically)
# Usage: ./scripts/doc-drift-check.sh        (Docker CLI with the compose plugin is needed for check 3)
set -uo pipefail
cd "$(dirname "$0")/.."
export MSYS_NO_PATHCONV=1

SDD=docs/architecture/sdd.md
ISD=docs/architecture/isd.md
OPENAPI=docs/api/openapi.yaml
fail=0

# --- 1 + 2: endpoints from the controllers ---------------------------------------------------------------
endpoints() {
  for f in $(git ls-files '*/src/main/java/*/controller/*.java'); do
    awk '
      /^@RequestMapping\(/ { match($0, /"[^"]*"/); prefix = substr($0, RSTART + 1, RLENGTH - 2) }
      /^[ \t]+@(Get|Post|Put|Delete|Patch)Mapping/ {
        verb = $0; sub(/^[ \t]+@/, "", verb); sub(/Mapping.*/, "", verb); verb = toupper(verb)
        path = ""
        if (match($0, /"[^"]*"/)) path = substr($0, RSTART + 1, RLENGTH - 2)
        print verb " " prefix path
      }' "$f"
  done | sort -u
}

echo "== 1. Controllers vs. $SDD (API contracts)"
n=0; miss=0
while read -r verb path; do
  n=$((n + 1))
  if ! grep -qF "\`$verb $path\`" "$SDD" 2>/dev/null; then
    echo "  MISSING in $SDD: $verb $path"; miss=$((miss + 1))
  fi
done < <(endpoints)
echo "  endpoints in code: $n, undocumented in SDD: $miss"
(( miss > 0 )) && fail=1

# Operations of the contract: "  /api/path:" lines under "paths:" followed by "    get:" / "    post:" ...
contract_operations() {
  awk '/^paths:/ { p = 1; next }
       p && /^[^ ]/ { p = 0 }
       p && /^  \/[^ ]*:$/ { path = $1; sub(/:$/, "", path) }
       p && /^    (get|post|put|delete|patch):/ { verb = $1; sub(/:$/, "", verb); print toupper(verb) " " path }' "$OPENAPI"
}

echo "== 2. $OPENAPI operations vs. controllers (contract drift)"
code=$(endpoints)
n=0; miss=0
while read -r verb path; do
  n=$((n + 1))
  if ! grep -qxF "$verb $path" <<< "$code"; then
    echo "  NOT IN CODE: $verb $path (promised by $OPENAPI)"; miss=$((miss + 1))
  fi
done < <(contract_operations)
echo "  contract operations: $n, missing in code: $miss"
(( n == 0 )) && { echo "  no operations parsed from $OPENAPI"; fail=1; }
(( miss > 0 )) && fail=1

# --- 3: startup order of docker-compose.yml vs. the ISD table -----------------------------------------------
echo "== 3. docker-compose.yml depends_on vs. $ISD (startup order)"
if ! docker compose version > /dev/null 2>&1; then
  echo "  SKIPPED: docker compose is not available"
else
  n=0; miss=0
  while IFS='|' read -r svc deps; do
    n=$((n + 1))
    [[ -z "$deps" ]] && deps="-"
    if ! grep -qF "| \`$svc\` | $deps |" "$ISD" 2>/dev/null; then
      echo "  MISMATCH in $ISD: $svc waits for: $deps"; miss=$((miss + 1))
    fi
  done < <(docker compose config 2>/dev/null | awk '
      /^services:/ { in_s = 1; next }
      in_s && /^[^ ]/ { in_s = 0 }
      in_s && /^  [a-z0-9-]+:$/ { if (svc != "") print svc "|" deps; svc = $1; sub(/:$/, "", svc); deps = ""; in_d = 0; next }
      in_s && /^    depends_on:/ { in_d = 1; next }
      in_s && in_d && /^      [a-z0-9-]+:$/ { d = $1; sub(/:$/, "", d); deps = (deps == "" ? d : deps ", " d); next }
      in_s && /^    [a-z_]+:/ { in_d = 0 }
      END { if (svc != "") print svc "|" deps }' | sort)
  echo "  containers: $n, startup rows that differ from ISD: $miss"
  (( miss > 0 )) && fail=1
fi

if (( fail )); then
  echo "RESULT: FAIL - the documentation does not describe the current system"
else
  echo "RESULT: PASS - SDD, OpenAPI contract and ISD match the code"
fi
exit $fail
