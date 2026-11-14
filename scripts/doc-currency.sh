#!/usr/bin/env bash
# Lab 10: documentation currency audit (Documentation Decay Audit) from Git history only.
# For every knowledge carrier listed in docs/lab-10/doc-map.txt it prints:
#   last   git log -1 --format=%cs -- <doc>                  date of the last commit of the document
#   age    reference date - last, in days                    > 90 suspicious, > 180 probably stale
#   docC   git log --oneline -- <doc> | wc -l                 commits of the document
#   codeC  git log --oneline -- <code paths> | wc -l          commits of the code the document describes
#   lag    git log --oneline <last doc commit>..HEAD -- <code paths> | wc -l
#          code commits made after the document was last touched; > 20 = structural drift (assignment threshold)
#   refs   git grep -l -F <doc file name> | wc -l              other tracked files that link or name the document
#   30d    git log --since=<ref - 30 days> --oneline -- <doc> | wc -l   changes in the last month
# Usage: ./scripts/doc-currency.sh [reference-date YYYY-MM-DD] [revision]   (defaults: today, HEAD)
# The map has one line per document: <doc path>|<kind: product|process>|<code pathspecs separated by spaces or ->
set -uo pipefail
cd "$(dirname "$0")/.."

REF_DATE=${1:-$(date +%F)}
REV=${2:-HEAD}
MAP=docs/lab-10/doc-map.txt
ref_epoch=$(date -d "$REF_DATE" +%s)
since="$(date -d "$REF_DATE -30 days" +%F) 00:00:00"

echo "Documentation currency audit: reference date $REF_DATE, revision $(git rev-parse --short "$REV")"
echo "Thresholds: age > 90 d SUSPICIOUS, > 180 d STALE; lag > 20 code commits DRIFT (lag > 0 is reported as 'behind')"
echo
echo "| Document | Kind | Last updated | Age, d | Doc commits | Code commits | Lag (code commits after last doc commit) | Inbound refs | Changes in 30 d | Signal |"
echo "|---|---|---|---|---|---|---|---|---|---|"

while IFS='|' read -r doc kind paths; do
  [[ -z "$doc" || "$doc" == \#* ]] && continue
  last=$(git log -1 --format=%cs "$REV" -- "$doc")
  if [[ -z "$last" ]]; then
    echo "| \`$doc\` | $kind | not in Git | - | 0 | - | - | - | - | MISSING |"
    continue
  fi
  age=$(( (ref_epoch - $(date -d "$last" +%s)) / 86400 ))
  doc_commits=$(git log --oneline "$REV" -- "$doc" | wc -l | tr -d ' ')
  last_hash=$(git log -1 --format=%H "$REV" -- "$doc")
  name=$(basename "$doc")
  refs=$(git grep -l -F "$name" "$REV" -- ":(exclude)$doc" ':(exclude)docs/lab-10/*' 2>/dev/null | wc -l | tr -d ' ')
  recent=$(git log --since="$since" --oneline "$REV" -- "$doc" | wc -l | tr -d ' ')
  if [[ "$paths" == "-" ]]; then
    code="n/a"; lag="n/a"
  else
    # shellcheck disable=SC2086  # pathspecs are intentionally split on spaces
    code=$(git log --oneline "$REV" -- $paths | wc -l | tr -d ' ')
    # shellcheck disable=SC2086
    lag=$(git log --oneline "$last_hash..$REV" -- $paths | wc -l | tr -d ' ')
  fi
  signal=OK
  if (( age > 180 )); then signal=STALE; elif (( age > 90 )); then signal=SUSPICIOUS; fi
  if [[ "$lag" != "n/a" ]]; then
    if (( lag > 20 )); then signal="$signal, DRIFT"; elif (( lag > 0 )); then signal="$signal, behind"; fi
  fi
  [[ "$refs" == 0 ]] && signal="$signal, no inbound refs"
  echo "| \`$doc\` | $kind | $last | $age | $doc_commits | $code | $lag | $refs | $recent | $signal |"
done < "$MAP"
