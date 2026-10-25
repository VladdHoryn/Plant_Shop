#!/usr/bin/env bash
# Balanced quality gate: line coverage (JaCoCo) AND mutation score (PIT) must both reach their threshold.
# Coverage alone can be gamed with tests that execute code but assert nothing; such tests kill no
# mutants, so the mutation score exposes them.
#
#   ./mvnw -Pquality verify -pl <modules> -Dtest='!*IntegrationTest' -Dsurefire.failIfNoSpecifiedTests=false
#   COVERAGE_MIN=80 MUTATION_MIN=65 ./scripts/quality-gate.sh <module>...
#
# Exit code 0 = PASS, 1 = FAIL, 2 = a report is missing.
set -uo pipefail
cd "$(dirname "$0")/.."

COVERAGE_MIN=${COVERAGE_MIN:-80}
MUTATION_MIN=${MUTATION_MIN:-65}
[ $# -gt 0 ] || { echo "usage: $0 <module>..." >&2; exit 2; }

line_missed=0; line_covered=0; mutants=0; killed=0
printf '%-20s %12s %10s %10s %10s\n' module "line cov %" mutants detected "score %"
for module in "$@"; do
  csv="$module/target/site/jacoco/jacoco.csv"
  xml="$module/target/pit-reports/mutations.xml"
  [ -f "$csv" ] && [ -f "$xml" ] || { echo "missing $csv or $xml, run the quality profile first" >&2; exit 2; }
  read -r lm lc < <(awk -F, 'NR > 1 { m += $8; c += $9 } END { print m + 0, c + 0 }' "$csv")
  # every mutant counts, including those no test reaches (NO_COVERAGE): detected / generated
  total=$(grep -o '<mutation ' "$xml" | wc -l)
  detected=$(grep -o "detected='true'" "$xml" | wc -l)
  awk -v m="$module" -v lm="$lm" -v lc="$lc" -v t="$total" -v d="$detected" 'BEGIN {
    printf "%-20s %12.1f %10d %10d %10.1f\n", m, (lm + lc ? 100 * lc / (lm + lc) : 0), t, d, (t ? 100 * d / t : 0) }'
  line_missed=$((line_missed + lm)); line_covered=$((line_covered + lc))
  mutants=$((mutants + total)); killed=$((killed + detected))
done

# Totals are ratios of summed counts (not averages of per-module percentages)
awk -v lm="$line_missed" -v lc="$line_covered" -v t="$mutants" -v d="$killed" \
    -v cmin="$COVERAGE_MIN" -v mmin="$MUTATION_MIN" 'BEGIN {
  cov = (lm + lc ? 100 * lc / (lm + lc) : 0); score = (t ? 100 * d / t : 0)
  covOk = cov >= cmin; mutOk = score >= mmin
  printf "%-20s %12.1f %10d %10d %10.1f\n\n", "TOTAL", cov, t, d, score
  printf "Gate: (Line Coverage %.1f%% >= %s%%) AND (Mutation Score %.1f%% >= %s%%)\n", cov, cmin, score, mmin
  printf "      %s AND %s  =>  %s\n", (covOk ? "true" : "false"), (mutOk ? "true" : "false"), (covOk && mutOk ? "PASS" : "FAIL")
  exit (covOk && mutOk ? 0 : 1) }'
