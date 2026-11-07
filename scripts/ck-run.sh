#!/usr/bin/env bash
# Lab 8: CK metrics (WMC, CBO, LCOM*, RFC, DIT, NOC, visibility counts), class dependencies (jdeps) and MOOD.
#   ./scripts/ck-run.sh <out dir> [focus FQN[,FQN...]]
#   ./scripts/ck-run.sh docs/lab-08/baseline com.plantshop.user.service.ConsentService
# Needs compiled classes (./mvnw -DskipTests package) and Docker. Writes into <out dir>:
#   class.csv, method.csv   raw CK output (only src/main/java of every module is given to CK)
#   ck-class.json, ck.txt   JSON package + top classes by CBO (scripts/ck_to_json.py)
#   jdeps-class.txt         jdeps -verbose:class -filter:none over */target/classes
#   mood.json, mood.txt     MHF / AHF / COF with substitution (scripts/ck_mood.py)
#   coupling.{dot,mmd}, coupling-matrix.md   graph around the focus classes (scripts/coupling_graph.py)
set -euo pipefail
cd "$(dirname "$0")/.."
export MSYS_NO_PATHCONV=1

OUT=${1:?usage: ck-run.sh <out dir> [focus FQN,...]}
FOCUS=${2:-}
CK_IMAGE=plantshop/ck:0.7.0-jre21
PY_IMAGE=plantshop/lizard:1.24.3-py3.13.17     # pinned python:3.13.17-slim, reused as the Python runner
JDEPS=${JAVA_HOME:+$JAVA_HOME/bin/}jdeps
MODULES=(secure-logging config-service discovery-service api-gateway catalog-service inventory-service
         order-service payment-service user-service)

host() { (cd "$1" && (pwd -W 2>/dev/null || pwd)); }
docker image inspect "$CK_IMAGE" > /dev/null 2>&1 || docker build -q -t "$CK_IMAGE" tools/ck > /dev/null
docker image inspect "$PY_IMAGE" > /dev/null 2>&1 || docker build -q -t "$PY_IMAGE" tools/lizard > /dev/null
mkdir -p "$OUT"
rm -f "$OUT/class.csv" "$OUT/method.csv"

mounts=()
for m in "${MODULES[@]}"; do mounts+=(-v "$(host "$m/src/main/java"):/scan/$m:ro"); done
docker run --rm --network none "${mounts[@]}" -v "$(host "$OUT"):/out" "$CK_IMAGE" /scan false 0 false /out/ \
  2>&1 | grep -v '^log4j:' || true
[ -s "$OUT/class.csv" ] || { echo "ck-run: CK produced no class.csv" >&2; exit 1; }

classes=()
for m in "${MODULES[@]}"; do classes+=("$m/target/classes"); done
"$JDEPS" -verbose:class -filter:none "${classes[@]}" > "$OUT/jdeps-class.txt"

py() { docker run --rm --network none -v "$(pwd -W 2>/dev/null || pwd):/src" -w /src --entrypoint python "$PY_IMAGE" "$@"; }
py scripts/ck_to_json.py "$OUT/class.csv" "$OUT/ck-class.json" --strip /scan/ | tee "$OUT/ck.txt"
py scripts/ck_mood.py "$OUT/class.csv" "$OUT/jdeps-class.txt" "$OUT/mood.json" | tee "$OUT/mood.txt"
if [ -n "$FOCUS" ]; then
  py scripts/coupling_graph.py "$OUT/jdeps-class.txt" "$FOCUS" "$OUT/coupling" | tee "$OUT/coupling.txt"
fi
