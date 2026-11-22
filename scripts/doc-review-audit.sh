#!/usr/bin/env bash
# Lab 11: continuous currency audit. Reads the front matter passport (owner, status, review_date) of every
# product document and compares review_date with the reference date.
#   ./scripts/doc-review-audit.sh [YYYY-MM-DD]      default: today; prints a Markdown table (CI: Step Summary)
#   STRICT=1 ./scripts/doc-review-audit.sh          exit 1 when a page is overdue or has no passport
# Scope: README.md, CHANGELOG.md and every docs/**/*.md that mkdocs.yml does not exclude (process documentation
# such as lab reports has no review cycle). Deprecated pages are listed but never counted as overdue.
set -uo pipefail
cd "$(dirname "$0")/.."

REF_DATE=${1:-$(date +%F)}
ref=$(date -d "$REF_DATE" +%s)

is_process_doc() {
  case "$1" in
    docs/lab-*|docs/*-report.md|docs/reports/*|docs/complexity/*|docs/privacy-demo.md|docs/goodhart-demo.md|docs/gqm-metrics.md) return 0 ;;
  esac
  return 1
}

field() {  # field <name> <file>: value of a top-level key inside the leading --- ... --- block
  awk -v key="$2" 'NR == 1 && $0 != "---" { exit } NR > 1 && $0 == "---" { exit }
       NR > 1 && index($0, key ":") == 1 { sub("^" key ":[ ]*", ""); print; exit }' "$1"
}

total=0; overdue=0; missing=0; deprecated=0
echo "Documentation review audit: reference date $REF_DATE"
echo
echo "| Document | Owner | Status | review_date | Days left | Signal |"
echo "|---|---|---|---|---|---|"
for f in README.md CHANGELOG.md $(git ls-files --cached --others --exclude-standard 'docs/*.md' 'docs/**/*.md' | sort -u); do
  is_process_doc "$f" && continue
  total=$((total + 1))
  owner=$(field "$f" owner); status=$(field "$f" status); review=$(field "$f" review_date)
  if [[ -z "$owner" || -z "$status" || -z "$review" ]]; then
    echo "| \`$f\` | ${owner:--} | ${status:--} | ${review:--} | - | NO PASSPORT |"; missing=$((missing + 1)); continue
  fi
  left=$(( ($(date -d "$review" +%s) - ref) / 86400 ))
  signal=OK
  if [[ "$status" == deprecated ]]; then
    signal="DEPRECATED -> $(field "$f" superseded_by)"; deprecated=$((deprecated + 1))
  elif (( left < 0 )); then
    signal=OVERDUE; overdue=$((overdue + 1))
  elif (( left <= 30 )); then
    signal="due soon"
  fi
  echo "| \`$f\` | $owner | $status | $review | $left | $signal |"
done
echo
echo "Documents: $total, overdue: $overdue, without passport: $missing, deprecated: $deprecated"
if (( overdue + missing > 0 )); then
  echo "RESULT: ATTENTION - review the OVERDUE pages (update review_date) or deprecate them"
  [[ "${STRICT:-0}" == 1 ]] && exit 1
else
  echo "RESULT: PASS - every product document is within its review cycle"
fi
exit 0
