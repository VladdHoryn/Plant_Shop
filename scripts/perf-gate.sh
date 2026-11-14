#!/usr/bin/env bash
# Performance quality gate hardened against infrastructure noise (lessons of docs/stability-report.md):
#   - pre-flight: refuses to measure when the environment is not the reference one (CPU limit on the
#     service changed, noise containers running, other containers busy) - reports INVALID, not FAIL;
#   - pinned, resource-limited load generator on the compose network, 15 s warm-up;
#   - RUNS repetitions, the gate uses the median of the per-run p95 values (one outlier run cannot flip it).
#   P95_MS=150 RUNS=3 DURATION=30s ./scripts/perf-gate.sh        exit 0 PASS, 1 FAIL, 3 INVALID environment
set -uo pipefail
cd "$(dirname "$0")/.."
export MSYS_NO_PATHCONV=1

SUT=plantshop-catalog-service
K6_IMAGE=grafana/k6:0.54.0
P95_MS=${P95_MS:-150}
RUNS=${RUNS:-3}
DURATION=${DURATION:-30s}
VUS=${VUS:-20}
MAX_BACKGROUND_CPU=${MAX_BACKGROUND_CPU:-50}   # % summed over all other containers
WORK=target/perf-gate
NETWORK=$(docker inspect -f '{{range $k, $v := .NetworkSettings.Networks}}{{$k}}{{end}}' "$SUT")
HOST_DIR=$(pwd -W 2>/dev/null || pwd)
mkdir -p "$WORK"

invalid() { echo "INVALID environment: $1 - no measurement taken" >&2; exit 3; }

# --- pre-flight -----------------------------------------------------------------------------
ncpu=$(docker info --format '{{.NCPU}}')
nano=$(docker inspect -f '{{.HostConfig.NanoCpus}}' "$SUT")
[ "$nano" = 0 ] || [ "$nano" = "$((ncpu * 1000000000))" ] || invalid "$SUT has a CPU limit of $nano nano-CPUs"
[ -z "$(docker ps -q --filter label=plantshop.noise)" ] || invalid "noise containers are running"
busy=$(docker stats --no-stream --format '{{.Name}} {{.CPUPerc}}' | grep -v "^$SUT " | tr -d '%' \
       | awk '{ s += $2 } END { printf "%.0f", s }')
[ "$busy" -le "$MAX_BACKGROUND_CPU" ] || invalid "other containers use ${busy}% CPU (limit ${MAX_BACKGROUND_CPU}%)"
echo "pre-flight OK: no CPU limit on $SUT, no noise containers, background CPU ${busy}%"

k6() {
  docker run --rm --network "$NETWORK" --cpus 1 --memory 512m \
    -e P95_MS=100000 -e VUS="$VUS" -e DURATION="$2" \
    -v "$HOST_DIR/perf/k6:/scripts:ro" -v "$HOST_DIR/$WORK:/out" \
    "$K6_IMAGE" run --quiet --no-color --summary-export "/out/$1.json" /scripts/catalog-search.js > /dev/null 2>&1
}
p95() { sed -n '/"http_req_duration"/,/}/p' "$1" | grep -o '"p(95)": *[0-9.]*' | sed 's/.*: *//'; }

k6 warmup 15s
values=()
for i in $(seq "$RUNS"); do
  k6 "run-$i" "$DURATION"
  values+=("$(p95 "$WORK/run-$i.json")")
  echo "run $i: p95 = ${values[-1]} ms"
done
printf '%s\n' "${values[@]}" | sort -n | awk -v max="$P95_MS" '
  { v[NR] = $1 }
  END {
    med = (NR % 2) ? v[(NR + 1) / 2] : (v[NR / 2] + v[NR / 2 + 1]) / 2
    printf "Gate: median(p95 of %d runs) = %.1f ms < %s ms  =>  %s\n", NR, med, max, (med < max ? "PASS" : "FAIL")
    exit (med < max ? 0 : 1) }'
