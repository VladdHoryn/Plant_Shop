#!/usr/bin/env bash
# Lab 11: the knowledge graph of the product documentation without Obsidian - the same edges Obsidian draws
# (relative Markdown links between documents, plus front matter tags). Prints a Mermaid graph and every
# document without links in either direction ("island"). Exit 1 if an island exists.
#   ./scripts/doc-link-graph.sh > docs/lab-11/knowledge-graph.md
set -uo pipefail
cd "$(dirname "$0")/.."

is_process_doc() {
  case "$1" in
    docs/lab-*|docs/*-report.md|docs/reports/*|docs/complexity/*|docs/privacy-demo.md|docs/goodhart-demo.md|docs/gqm-metrics.md) return 0 ;;
  esac
  return 1
}
docs=()
for f in README.md CHANGELOG.md $(git ls-files --cached --others --exclude-standard 'docs/*.md' 'docs/**/*.md' | sort -u); do
  is_process_doc "$f" || docs+=("$f")
done

declare -A deg; edges=()
norm() { realpath -m --relative-to=. "$1"; }
for f in "${docs[@]}"; do
  dir=$(dirname "$f")
  # include-markdown pages (index.md, changelog.md) embed another document: that is an edge too
  inc=$(grep -o 'include-markdown "[^"]*"' "$f" | sed 's/include-markdown "//; s/"$//')
  links=$(grep -o '\]([^)#:]*\.md\(#[^)]*\)\?)' "$f" | sed 's/^](//; s/#.*//; s/)$//')
  for l in $inc $links; do
    t=$(norm "$dir/$l")
    [[ -f "$t" && "$t" != "$f" ]] || continue
    edges+=("$f|$t"); deg[$f]=$(( ${deg[$f]:-0} + 1 )); deg[$t]=$(( ${deg[$t]:-0} + 1 ))
  done
done
mapfile -t uniq_edges < <(printf '%s\n' "${edges[@]}" | sort -u)

id() { echo "$1" | sed 's/[^A-Za-z0-9]/_/g'; }
echo "# Граф знань бази документації"
echo
echo "Згенеровано \`./scripts/doc-link-graph.sh\`: вузли — документи продукту, ребра — відносні Markdown-посилання"
echo "та include (ті самі зв'язки, які показує граф Obsidian)."
echo
echo '```mermaid'
echo "graph LR"
for f in "${docs[@]}"; do echo "  $(id "$f")[\"${f#docs/}\"]"; done
for e in "${uniq_edges[@]}"; do echo "  $(id "${e%%|*}") --> $(id "${e##*|}")"; done
echo '```'
echo
islands=0
echo "| Документ | Зв'язків (вхідні + вихідні) | Теги |"
echo "|---|---|---|"
for f in "${docs[@]}"; do
  tags=$(awk 'NR==1&&$0!="---"{exit} NR>1&&$0=="---"{exit} /^  - /{sub(/^  - /,"#"); printf "%s ", $0}' "$f")
  d=${deg[$f]:-0}; (( d == 0 )) && islands=$((islands + 1))
  echo "| \`$f\` | $d | ${tags:--} |"
done
echo
echo "Documents: ${#docs[@]}, edges: ${#uniq_edges[@]}, islands (no links at all): $islands"
(( islands == 0 )) && echo "RESULT: PASS - the knowledge base is connected" || { echo "RESULT: FAIL - isolated documents"; exit 1; }
