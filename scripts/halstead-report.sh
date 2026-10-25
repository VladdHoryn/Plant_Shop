#!/usr/bin/env bash
# Halstead screening of the repository (tools/halstead: own CLI on the Pygments Java lexer, cross-checked with
# multimetric 2.4.5). Prints, and saves to docs/halstead-baseline.txt:
#   1. global baseline over all production sources (*/src/main/java), ranked by effort E
#   2. the three architecture representatives (core domain, integration, DTO) with their vocabulary
#   3. the same three files through multimetric, to compare the token classification of both tools
#   ./scripts/halstead-report.sh
set -uo pipefail
cd "$(dirname "$0")/.."
export MSYS_NO_PATHCONV=1

IMAGE=plantshop/halstead:1.0-py3.13.17
OUT=docs/complexity/halstead-baseline.txt
CORE=payment-service/src/main/java/com/plantshop/payment/service/PaymentService.java
INTEGRATION=user-service/src/main/java/com/plantshop/user/service/PersonalDataExportService.java
DTO=user-service/src/main/java/com/plantshop/user/dto/PersonalDataExport.java
HOST_DIR=$(pwd -W 2>/dev/null || pwd)

docker build -q -t "$IMAGE" tools/halstead > /dev/null || exit 1
run() { docker run --rm --network none --cpus 1 --memory 512m -v "$HOST_DIR:/src:ro" "$@"; }
mapfile -t FILES < <(git ls-files '*/src/main/java/*.java')

{
  echo "Halstead screening, $(date '+%Y-%m-%d %H:%M'), commit $(git rev-parse --short HEAD)"
  echo "Tool: tools/halstead/halstead.py on Pygments $(run --entrypoint python "$IMAGE" -c 'import pygments; print(pygments.__version__)') JavaLexer," \
       "Python 3.13.17, image $IMAGE"
  echo
  echo "\$ docker run --rm -v \"\$PWD:/src:ro\" $IMAGE \$(git ls-files '*/src/main/java/*.java')"
  echo
  echo "== 1. Global baseline: ${#FILES[@]} production files, ranked by effort E (top 20 + ALL) =="
  run "$IMAGE" "${FILES[@]}" | awk 'NR <= 22 || /ALL \(/'
  echo
  echo "== 2. Architecture representatives =="
  echo "Core domain:  $CORE"
  echo "Integration:  $INTEGRATION"
  echo "Utility/DTO:  $DTO"
  echo
  run "$IMAGE" --vocabulary "$CORE" "$INTEGRATION" "$DTO"
  echo
  echo "== 3. Cross-check: multimetric 2.4.5 on the same files =="
  echo "(multimetric counts class/method names as operators and ignores Java keywords entirely)"
  run --entrypoint multimetric "$IMAGE" "$CORE" "$INTEGRATION" "$DTO" \
    | awk '/"overall"/ { exit } { print }' \
    | grep -E '"/src/|operators_uniq|operands_uniq|operators_sum|operands_sum|halstead_volume|halstead_difficulty|halstead_effort|"loc"'
} | tee "$OUT"
