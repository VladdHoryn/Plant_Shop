#!/usr/bin/env python3
"""FPA (IFPUG) + Use Case Points (Karner) calculator and productivity calibration (Lab 9, task 3).

Inputs (docs/lab-09 by default), all collected from the order-service code:
  fpa-functions.csv   data/transaction functions with DET and RET/FTR counts
  ucp-actors.csv      actors with their interface complexity (simple/average/complex)
  ucp-use-cases.csv   use cases; ``steps`` is a ';'-separated list, its length is the transaction count
  lizard-order-service-main.txt   Lizard report, its "Total nloc" is the size used for the effort estimate

The complexity of every FPA function is derived from the IFPUG matrices (never chosen by hand).
Effort = NLOC * 0.5 h (the SQALE development-cost norm used in task 1), PF = effort / UCP.

Usage: python scripts/fpa_ucp.py [data_dir]     -> prints the ledger and writes <data_dir>/fpa-ucp-ledger.md
"""

from __future__ import annotations

import csv
import re
import sys
from pathlib import Path

HOURS_PER_LINE = 0.5            # SQALE: 30 minutes to develop one line of code
TCF = 1.0                       # technical complexity factor, industrial baseline per assignment
ECF = 1.0                       # environmental complexity factor, industrial baseline per assignment
VAF = 1.0                       # FPA value adjustment factor, neutral (TDI = 35)

FPA_WEIGHTS = {                 # IFPUG CPM: Low / Average / High
    "ILF": (7, 10, 15),
    "EIF": (5, 7, 10),
    "EI": (3, 4, 6),
    "EO": (4, 5, 7),
    "EQ": (3, 4, 6),
}
LEVELS = ("Low", "Average", "High")
ACTOR_WEIGHTS = {"simple": 1, "average": 2, "complex": 3}


def _band(value: int, bounds: tuple[int, int]) -> int:
    """0, 1 or 2 depending on which of three ranges ``value`` falls into; bounds are the range starts."""
    return 0 if value < bounds[0] else 1 if value < bounds[1] else 2


# Rows: RET/FTR band, columns: DET band -> complexity index into LEVELS
_DATA_MATRIX = ((0, 0, 1), (0, 1, 2), (1, 2, 2))           # RET 1 | 2-5 | 6+ ; DET 1-19 | 20-50 | 51+
_EI_MATRIX = ((0, 0, 1), (0, 1, 2), (1, 2, 2))             # FTR 0-1 | 2 | 3+ ; DET 1-4 | 5-15 | 16+
_EO_EQ_MATRIX = ((0, 0, 1), (0, 1, 2), (1, 2, 2))          # FTR 0-1 | 2-3 | 4+ ; DET 1-5 | 6-19 | 20+


def fpa_complexity(fn_type: str, det: int, ret_ftr: int) -> int:
    if fn_type in ("ILF", "EIF"):
        return _DATA_MATRIX[_band(ret_ftr, (2, 6))][_band(det, (20, 51))]
    if fn_type == "EI":
        return _EI_MATRIX[_band(ret_ftr, (2, 3))][_band(det, (5, 16))]
    if fn_type in ("EO", "EQ"):
        return _EO_EQ_MATRIX[_band(ret_ftr, (2, 4))][_band(det, (6, 20))]
    raise ValueError(f"unknown FPA function type: {fn_type}")


def use_case_weight(transactions: int) -> tuple[str, int]:
    if transactions <= 3:
        return "Simple", 5
    if transactions <= 7:
        return "Average", 10
    return "Complex", 15


def read_csv(path: Path) -> list[dict[str, str]]:
    with path.open(encoding="utf-8", newline="") as handle:
        return list(csv.DictReader(handle))


def lizard_total_nloc(path: Path) -> int:
    text = path.read_text(encoding="utf-8")
    match = re.search(r"Total nloc.*\n-+\n\s*(\d+)", text)
    if not match:
        raise ValueError(f"no 'Total nloc' in {path}")
    return int(match.group(1))


def build_ledger(data_dir: Path) -> tuple[str, dict[str, float]]:
    functions = read_csv(data_dir / "fpa-functions.csv")
    actors = read_csv(data_dir / "ucp-actors.csv")
    use_cases = read_csv(data_dir / "ucp-use-cases.csv")
    nloc = lizard_total_nloc(data_dir / "lizard-order-service-main.txt")

    out = ["## FPA (IFPUG) ledger", "",
           "| ID | Тип | Функція | DET | RET/FTR | Складність | Вага FP |", "|---|---|---|---|---|---|---|"]
    ufp = 0
    by_type: dict[str, int] = {}
    for fn in functions:
        det, ret_ftr = int(fn["det"]), int(fn["ret_ftr"])
        level = fpa_complexity(fn["type"], det, ret_ftr)
        weight = FPA_WEIGHTS[fn["type"]][level]
        ufp += weight
        by_type[fn["type"]] = by_type.get(fn["type"], 0) + weight
        out.append(f"| {fn['id']} | {fn['type']} | {fn['name']} | {det} | {ret_ftr} | {LEVELS[level]} | {weight} |")
    afp = ufp * VAF
    type_sum = " + ".join(f"{t} {by_type.get(t, 0)}" for t in FPA_WEIGHTS)
    out += ["", f"UFP = {type_sum} = **{ufp} FP**; VAF = {VAF:.2f} -> AFP = {ufp} × {VAF:.2f} = **{afp:g} FP**", ""]

    out += ["## UCP (Karner) ledger", "", "| ID | Актор | Інтерфейс | Складність | Вага |", "|---|---|---|---|---|"]
    uaw = 0
    for actor in actors:
        weight = ACTOR_WEIGHTS[actor["complexity"]]
        uaw += weight
        out.append(f"| {actor['id']} | {actor['actor']} | {actor['interface']} | {actor['complexity']} | {weight} |")
    out += ["", f"UAW = {' + '.join(str(ACTOR_WEIGHTS[a['complexity']]) for a in actors)} = **{uaw}**", "",
            "| ID | Use Case | Транзакцій | Складність | Вага |", "|---|---|---|---|---|"]
    uucw = 0
    weights = []
    for uc in use_cases:
        transactions = len([s for s in uc["steps"].split(";") if s.strip()])
        level, weight = use_case_weight(transactions)
        uucw += weight
        weights.append(str(weight))
        out.append(f"| {uc['id']} | {uc['use_case']} | {transactions} | {level} | {weight} |")
    uucp = uaw + uucw
    ucp = uucp * TCF * ECF
    effort = nloc * HOURS_PER_LINE
    pf = effort / ucp
    out += ["", f"UUCW = {' + '.join(weights)} = **{uucw}**", "",
            f"UUCP = UAW + UUCW = {uaw} + {uucw} = **{uucp}**", "",
            f"UCP = UUCP × TCF × ECF = {uucp} × {TCF} × {ECF} = **{ucp:g}**", "",
            "## Calibration", "",
            f"Effort = NLOC × {HOURS_PER_LINE} h = {nloc} × {HOURS_PER_LINE} = **{effort:g} person-hours**", "",
            f"PF = Effort / UCP = {effort:g} / {ucp:g} = **{pf:.2f} person-hours per UCP**", "",
            f"Hours per FP = {effort:g} / {afp:g} = {effort / afp:.2f} person-hours per FP", ""]
    totals = {"ufp": ufp, "afp": afp, "uaw": uaw, "uucw": uucw, "uucp": uucp, "ucp": ucp,
              "nloc": nloc, "effort": effort, "pf": pf}
    return "\n".join(out), totals


def main(argv: list[str]) -> int:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    data_dir = Path(argv[1]) if len(argv) > 1 else Path("docs/lab-09")
    ledger, totals = build_ledger(data_dir)
    target = data_dir / "fpa-ucp-ledger.md"
    target.write_text("# FPA & UCP ledger: order-service (generated by scripts/fpa_ucp.py)\n\n" + ledger,
                      encoding="utf-8", newline="\n")
    print(ledger)
    print("totals:", ", ".join(f"{k}={v:g}" for k, v in totals.items()))
    print(f"[OK] written {target}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
