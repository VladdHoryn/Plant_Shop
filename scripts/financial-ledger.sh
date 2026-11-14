#!/usr/bin/env bash
# Lab 9, task 2: runs scripts/financial_ledger.py with the assignment's parameters and writes FINANCIAL_LEDGER.md
# into the repository root. Override any value from the environment, e.g. HOURLY_RATE=50 ./scripts/financial-ledger.sh
# Uses a local Python 3 (py launcher / python3) or, if none, the python:3.12-slim Docker image.
set -euo pipefail
cd "$(dirname "$0")/.."

REPORT="${1:-docs/lab-09/report.json}"
export HOURLY_RATE="${HOURLY_RATE:-40}"
export OVERLOAD_COEFF="${OVERLOAD_COEFF:-1.3}"
export DISCOUNT_RATE="${DISCOUNT_RATE:-0.01}"
export PYTHONIOENCODING=utf-8

if command -v py >/dev/null 2>&1 && py -3 --version >/dev/null 2>&1; then
    exec py -3 scripts/financial_ledger.py "$REPORT"
elif command -v python3 >/dev/null 2>&1 && python3 --version >/dev/null 2>&1; then
    exec python3 scripts/financial_ledger.py "$REPORT"
else
    exec docker run --rm -v "$PWD:/work" -w /work \
        -e HOURLY_RATE -e OVERLOAD_COEFF -e DISCOUNT_RATE -e PYTHONIOENCODING \
        python:3.12-slim python scripts/financial_ledger.py "$REPORT"
fi
