#!/usr/bin/env bash
# Lab 8, task 1: reads object-design metrics from the SonarQube Web API and saves every raw JSON response.
#   SONAR_TOKEN=... ./scripts/sonar-fetch.sh <out dir> <Label=ClassName>...
#   SONAR_TOKEN=... ./scripts/sonar-fetch.sh docs/lab-08/baseline/sonar \
#       core=PaymentService infra=PersonalDataExportService dto=PersonalDataExport
# For every class: api/components/tree finds the exact file key (multi-module keys contain the module path),
# then api/measures/component is called twice:
#   1. with the keys the assignment asks for (+ the standard ones) -> measures-<label>-lab-keys.json; the
#      server answers with an error when a key does not exist, and that error is the evidence;
#   2. with the keys the server really has                         -> measures-<label>.json
# Plus api/metrics/search (which metric keys exist at all) and one project-level call.
# Each request is logged to <out dir>/api-calls.txt with $SONAR_TOKEN shown as a variable, never its value.
set -euo pipefail
cd "$(dirname "$0")/.."

: "${SONAR_TOKEN:?export SONAR_TOKEN first}"
SONAR_URL=${SONAR_URL:-http://localhost:9000}
PROJECT=${SONAR_PROJECT:-plantshop}
LAB_KEYS="class_complexity,coupling_between_objects,cohesion_in_methods,lcom4,complexity,cognitive_complexity,ncloc,functions"
KEYS="complexity,cognitive_complexity,ncloc,functions,classes,statements,code_smells,sqale_index"
OUT=${1:?usage: sonar-fetch.sh <out dir> <Label=ClassName>...}
shift
mkdir -p "$OUT"
LOG="$OUT/api-calls.txt"
: > "$LOG"

# call <output file> <endpoint> <param>...  (GET with url-encoded parameters, like Postman's Params tab)
call() {
  local file=$1 endpoint=$2; shift 2
  local args=() shown=""
  for p in "$@"; do args+=(--data-urlencode "$p"); shown+=" --data-urlencode \"$p\""; done
  local code
  code=$(curl -s -G -u "$SONAR_TOKEN:" -o "$file" -w '%{http_code}' "$SONAR_URL/$endpoint" "${args[@]}")
  printf '$ curl -s -G -u "$SONAR_TOKEN:" "$SONAR_URL/%s"%s\n  -> HTTP %s, saved %s\n\n' \
    "$endpoint" "$shown" "$code" "$file" >> "$LOG"
  echo "HTTP $code $endpoint -> $file"
}

call "$OUT/sonar-metrics-available.json" api/metrics/search "ps=500"

for pair in "$@"; do
  label=${pair%%=*}
  class=${pair#*=}
  call "$OUT/components-$label.json" api/components/tree "component=$PROJECT" "qualifiers=FIL" "q=$class"
  key=$(grep -o "\"key\":\"[^\"]*/$class\.java\"" "$OUT/components-$label.json" | head -1 | cut -d'"' -f4)
  [ -n "$key" ] || { echo "sonar-fetch: no file key for $class" >&2; exit 1; }
  echo "  $label: $key"
  call "$OUT/measures-$label-lab-keys.json" api/measures/component "component=$key" "metricKeys=$LAB_KEYS"
  call "$OUT/measures-$label.json" api/measures/component "component=$key" "metricKeys=$KEYS"
done

call "$OUT/measures-project.json" api/measures/component "component=$PROJECT" "metricKeys=$KEYS,files"
