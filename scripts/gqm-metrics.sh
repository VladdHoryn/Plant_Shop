#!/usr/bin/env bash
# Collects the current values of the six GQM metrics (docs/metrics-report.md) and aggregates each one only
# with operations its Stevens scale allows. Output: docs/gqm-metrics.md
#   M1 cyclomatic complexity per function      ratio     Lizard (pinned container)
#   M2 highest CVE severity per service        ordinal   Trivy (pinned container) over the built jars
#   M3 mutation score per module               ratio     PIT reports (./mvnw -Pquality verify ...)
#   M4 p95 response time per run               ratio     k6 reports of scripts/perf-gate.sh
#   M5 kind of component a change touches      nominal   git log --numstat
#   M6 date of the last change of a module     interval  git log
# Prerequisites: the quality profile and scripts/perf-gate.sh have run; jars built (./mvnw package -DskipTests).
set -uo pipefail
cd "$(dirname "$0")/.."
export MSYS_NO_PATHCONV=1

OUT=docs/gqm-metrics.md
HOST_DIR=$(pwd -W 2>/dev/null || pwd)
LIZARD=plantshop/lizard:1.24.3-py3.13.17
TRIVY=aquasec/trivy:0.56.2
MODULES=(secure-logging catalog-service inventory-service order-service payment-service user-service)
SERVICES=(api-gateway catalog-service config-service discovery-service inventory-service order-service payment-service user-service)
WORK=target/gqm
mkdir -p "$WORK"

# nearest-rank percentile of numbers on stdin: percentile <p>
percentile() { sort -n | awk -v p="$1" '{ v[NR] = $1 } END { i = int((p / 100) * NR + 0.999999); if (i < 1) i = 1; print v[i] }'; }

m1() {
  echo "## M1. Cyclomatic complexity per function (ratio scale, Lizard 1.24.3)"
  echo
  docker image inspect "$LIZARD" > /dev/null 2>&1 || docker build -q -t "$LIZARD" tools/lizard > /dev/null
  docker run --rm --network none --cpus 1 --memory 256m -v "$HOST_DIR:/src:ro" "$LIZARD" \
    -l java -x "*/test/*" -x "*/target/*" -x "./scripts/*" . | awk '$1 ~ /^[0-9]+$/ && $6 ~ /@/ { print $2 }' > "$WORK/ccn.txt"
  local n mean med p90 max over
  n=$(wc -l < "$WORK/ccn.txt" | tr -d ' ')
  mean=$(awk '{ s += $1 } END { printf "%.2f", s / NR }' "$WORK/ccn.txt")
  med=$(percentile 50 < "$WORK/ccn.txt"); p90=$(percentile 90 < "$WORK/ccn.txt"); max=$(sort -n "$WORK/ccn.txt" | tail -1)
  over=$(awk '$1 > 10 { c++ } END { printf "%.1f", 100 * c / NR }' "$WORK/ccn.txt")
  echo "| Functions | Median | P90 | Max | Mean | Share with CCN > 10 |"
  echo "|---|---|---|---|---|---|"
  echo "| $n | $med | $p90 | $max | $mean | $over % |"
  echo
  echo "Distribution (CCN: number of functions):"
  echo
  echo '```'
  sort -n "$WORK/ccn.txt" | uniq -c | awk '{ printf "CCN %2d: %4d  ", $2, $1; for (i = 0; i < $1 / 4; i++) printf "#"; print "" }'
  echo '```'
}

m2() {
  echo "## M2. Highest CVE severity of the dependencies, per service (ordinal scale, Trivy 0.56.2)"
  echo
  echo "Unique (CVE, package) pairs found in the built Spring Boot jar of each service."
  echo
  echo "| Service | CRITICAL | HIGH | MEDIUM | LOW | Max severity (= worst) | Median severity |"
  echo "|---|---|---|---|---|---|---|"
  : > "$WORK/all-severities.txt"
  for s in "${SERVICES[@]}"; do
    local jar="$s/target/$s-1.0.0-SNAPSHOT.jar"
    [ -f "$jar" ] || { echo "| $s | jar missing, run ./mvnw package -DskipTests | | | | | |"; continue; }
    docker run --rm -v "$HOST_DIR/$jar:/jars/$s.jar:ro" -v trivy-cache:/root/.cache "$TRIVY" \
      rootfs --scanners vuln --format json --quiet /jars > "$WORK/trivy-$s.json" 2> /dev/null
    grep -oE '"(VulnerabilityID|PkgName|Severity)": "[^"]*"' "$WORK/trivy-$s.json" \
      | awk -F'"' '/VulnerabilityID/ { id = $4 } /PkgName/ { p = $4 } /Severity/ { print id, p, $4 }' \
      | sort -u > "$WORK/vulns-$s.txt"
    awk '{ print $3 }' "$WORK/vulns-$s.txt" >> "$WORK/all-severities.txt"
    awk -v s="$s" '
      BEGIN { rank["LOW"] = 1; rank["MEDIUM"] = 2; rank["HIGH"] = 3; rank["CRITICAL"] = 4; name[0] = "none"
              name[1] = "LOW"; name[2] = "MEDIUM"; name[3] = "HIGH"; name[4] = "CRITICAL" }
      { c[$3]++; r = rank[$3] + 0; v[NR] = r; if (r > max) max = r }
      END { n = asort(v); med = n ? v[int((n + 1) / 2)] : 0
            printf "| %s | %d | %d | %d | %d | **%s** | %s |\n", s, c["CRITICAL"], c["HIGH"], c["MEDIUM"], c["LOW"],
                   name[max + 0], name[med] }' "$WORK/vulns-$s.txt"
  done
  echo
  echo "Mode over all services: $(sort "$WORK/all-severities.txt" | uniq -c | sort -nr | head -1 | awk '{ print $2 " (" $1 " findings)" }')."
}

m3() {
  echo "## M3. Mutation score per module (ratio scale, PIT 1.20.0)"
  echo
  echo "| Module | Mutants | Detected | Score |"
  echo "|---|---|---|---|"
  local tm=0 td=0 sum=0 k=0
  for m in "${MODULES[@]}"; do
    local xml="$m/target/pit-reports/mutations.xml" t d
    [ -f "$xml" ] || continue
    t=$(grep -o '<mutation ' "$xml" | wc -l); d=$(grep -o "detected='true'" "$xml" | wc -l)
    [ "$t" -gt 0 ] || continue
    awk -v m="$m" -v t="$t" -v d="$d" 'BEGIN { printf "| %s | %d | %d | %.1f %% |\n", m, t, d, 100 * d / t }'
    tm=$((tm + t)); td=$((td + d)); sum=$(awk -v s="$sum" -v t="$t" -v d="$d" 'BEGIN { print s + 100 * d / t }'); k=$((k + 1))
  done
  awk -v tm="$tm" -v td="$td" -v sum="$sum" -v k="$k" 'BEGIN {
    printf "| **pooled (Σ detected / Σ mutants)** | %d | %d | **%.1f %%** |\n", tm, td, 100 * td / tm
    printf "\nFor comparison, the unweighted mean of the module percentages would be %.1f %%: it gives a module with\n", sum / k
    printf "23 mutants the same weight as one with 63, so it is not used.\n" }'
}

m4() {
  echo "## M4. p95 response time per measurement run (ratio scale, k6 0.54.0, scripts/perf-gate.sh)"
  echo
  local files=(target/perf-gate/run-*.json)
  [ -f "${files[0]}" ] || { echo "No runs yet: ./scripts/perf-gate.sh"; return; }
  echo "| Run | median, ms | mean, ms | p95, ms | max, ms |"
  echo "|---|---|---|---|---|"
  for f in "${files[@]}"; do
    sed -n '/"http_req_duration"/,/}/p' "$f" > "$WORK/d.txt"
    awk -v r="$(basename "$f" .json)" '
      /"med"/ { gsub(/[^0-9.]/, "", $2); med = $2 } /"avg"/ { gsub(/[^0-9.]/, "", $2); avg = $2 }
      /"p\(95\)"/ { gsub(/[^0-9.]/, "", $2); p95 = $2 } /"max"/ { gsub(/[^0-9.]/, "", $2); max = $2 }
      END { printf "| %s | %.1f | %.1f | %.1f | %.1f |\n", r, med, avg, p95, max }' "$WORK/d.txt"
    awk '/"p\(95\)"/ { gsub(/[^0-9.]/, "", $2); print $2 }' "$WORK/d.txt" >> "$WORK/p95.txt"
  done
  echo
  printf 'Gate value: median of the run p95 values = %.1f ms (p95 values are not averaged).\n' \
    "$(percentile 50 < "$WORK/p95.txt")"
  rm -f "$WORK/p95.txt"
}

category() {   # nominal category of a changed path
  case "$1" in
    */src/main/java/*) echo "product code" ;;
    */src/test/*) echo "tests" ;;
    docs/*|*.md) echo "documentation" ;;
    *Dockerfile|docker-compose*|k8s/*|helm/*|.github/*|scripts/*|tools/*|perf/*|*pom.xml|mvnw*|.gitattributes|.editorconfig|.dockerignore) echo "build & infrastructure" ;;
    */src/main/resources/*|config-service/*) echo "configuration & migrations" ;;
    *) echo "other" ;;
  esac
}

m5() {
  echo "## M5. Kind of component touched by a change (nominal scale, git log --numstat)"
  echo
  echo "All commits up to HEAD ($(git rev-parse --short HEAD)); changed lines = added + deleted."
  echo
  git log --numstat --format='' | awk -F'\t' '$1 ~ /^[0-9]+$/ { print $1 + $2 "\t" $3 }' > "$WORK/numstat.txt"
  while IFS=$'\t' read -r lines path; do printf '%s\t%s\n' "$(category "$path")" "$lines"; done < "$WORK/numstat.txt" \
    | awk -F'\t' '{ n[$1] += $2; f[$1]++; t += $2 }
        END { for (c in n) printf "%s\t%d\t%d\t%.1f\n", c, f[c], n[c], 100 * n[c] / t }' | sort -t$'\t' -k3 -nr > "$WORK/m5.txt"
  echo "| Category | File changes | Changed lines | Share of lines |"
  echo "|---|---|---|---|"
  awk -F'\t' '{ printf "| %s | %d | %d | %s %% |\n", $1, $2, $3, $4 }' "$WORK/m5.txt"
  echo
  echo "Mode: **$(head -1 "$WORK/m5.txt" | cut -f1)**. Only counts, shares and the mode are computed: the categories have no order."
}

m6() {
  echo "## M6. Date of the last change of each module (interval scale, git log)"
  echo
  local ref; ref=$(git log -1 --format=%ct)
  echo "Reference point: HEAD commit time $(git log -1 --format=%cI) (not the wall clock, so the result is reproducible)."
  echo
  echo "| Module | First commit | Last commit | Days before HEAD |"
  echo "|---|---|---|---|"
  for m in "${MODULES[@]}" api-gateway config-service discovery-service; do
    local first last lastTs
    first=$(git log --reverse --format=%cs -- "$m" | head -1); last=$(git log -1 --format=%cs -- "$m")
    lastTs=$(git log -1 --format=%ct -- "$m")
    [ -n "$last" ] || { echo "| $m | not committed yet | | |"; continue; }
    awk -v m="$m" -v f="$first" -v l="$last" -v d="$(( (ref - lastTs) / 86400 ))" 'BEGIN { printf "| %s | %s | %s | %d |\n", m, f, l, d }'
  done
  echo
  echo "Differences of dates (days) are meaningful; ratios of dates are not (a date has no true zero)."
}

{
  echo "# Current values of the GQM metrics"
  echo
  echo "Generated by \`scripts/gqm-metrics.sh\` on $(date '+%Y-%m-%d %H:%M'), commit $(git rev-parse --short HEAD)"
  echo "(plus uncommitted changes of the working tree for M1-M4). Definitions and reasoning: [metrics-report.md](metrics-report.md)."
  echo
  m1; echo; m2; echo; m3; echo; m4; echo; m5; echo; m6
} | tee "$OUT"
