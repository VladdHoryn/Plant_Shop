#!/usr/bin/env python3
"""Software Economics simulator (Lab 9, task 2).

Reads the raw SonarCloud response saved in task 1 (``api/measures/component`` with ``sqale_index``, ``ncloc``,
``sqale_rating``) and turns technical debt into money:

* Principal (remediation cost)  = sqale_index / 60 * HOURLY_RATE * OVERLOAD_COEFF
* Cost of Delay (per month)     = ncloc / 100 * HOURLY_RATE
* NPV_t = sum_{k=1..t} CoD / (1 + DISCOUNT_RATE)^k - Principal,   t = 1..HORIZON

The payback month is the first t with NPV_t > 0. The result is written to FINANCIAL_LEDGER.md; the quality gate
is PASS when NPV becomes positive within the horizon (6 months), otherwise FAIL.

Usage:
    HOURLY_RATE=40 OVERLOAD_COEFF=1.3 DISCOUNT_RATE=0.01 python scripts/financial_ledger.py docs/lab-09/report.json

Only the standard library is used. Exit code: 0 = PASS, 2 = FAIL, 1 = bad input.
"""

from __future__ import annotations

import json
import os
import sys
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path

HORIZON_MONTHS = 6
MINUTES_PER_HOUR = 60
LINES_PER_LOST_HOUR = 100          # every 100 ncloc cost 1 engineer-hour per month of regression fighting
HOURLY_RATE_RANGE = (35.0, 50.0)   # range required by the assignment
SQALE_RATING_LETTERS = {1: "A", 2: "B", 3: "C", 4: "D", 5: "E"}
DEFAULT_REPORT = Path("docs/lab-09/report.json")
DEFAULT_OUTPUT = Path("FINANCIAL_LEDGER.md")


class LedgerInputError(Exception):
    """Raised when the JSON report or an environment variable cannot be used."""


@dataclass(frozen=True)
class Baseline:
    project_key: str
    project_name: str
    ncloc: int
    sqale_index: int
    sqale_rating: str


@dataclass(frozen=True)
class Config:
    hourly_rate: float
    overload_coeff: float
    discount_rate: float


@dataclass(frozen=True)
class MonthRow:
    month: int
    discount_factor: float
    nominal_benefit: float
    discounted_benefit: float
    cumulative_discounted: float
    npv: float


# ---------------------------------------------------------------- input

def load_baseline(report_path: Path) -> Baseline:
    """Parses the SonarCloud measures response; every number comes from the JSON, nothing is hard-coded."""
    try:
        payload = json.loads(report_path.read_text(encoding="utf-8"))
    except FileNotFoundError as exc:
        raise LedgerInputError(f"report not found: {report_path}") from exc
    except json.JSONDecodeError as exc:
        raise LedgerInputError(f"report is not valid JSON: {exc}") from exc

    if "errors" in payload:
        raise LedgerInputError(f"SonarCloud returned an error: {payload['errors']}")
    component = payload.get("component")
    if not isinstance(component, dict):
        raise LedgerInputError("JSON has no 'component' object (expected api/measures/component response)")

    measures = {m.get("metric"): m.get("value") for m in component.get("measures", [])}
    missing = [key for key in ("ncloc", "sqale_index", "sqale_rating") if key not in measures]
    if missing:
        raise LedgerInputError(f"metrics missing in JSON: {', '.join(missing)}")

    ncloc = _to_int(measures["ncloc"], "ncloc")
    if ncloc <= 0:
        raise LedgerInputError("ncloc must be positive")
    return Baseline(
        project_key=component.get("key", "?"),
        project_name=component.get("name", "?"),
        ncloc=ncloc,
        sqale_index=_to_int(measures["sqale_index"], "sqale_index"),
        sqale_rating=_rating_letter(measures["sqale_rating"]),
    )


def _to_int(raw: object, name: str) -> int:
    try:
        value = int(float(str(raw)))
    except ValueError as exc:
        raise LedgerInputError(f"{name} is not a number: {raw!r}") from exc
    if value < 0:
        raise LedgerInputError(f"{name} must not be negative: {value}")
    return value


def _rating_letter(raw: object) -> str:
    """SonarCloud encodes ratings as 1.0..5.0 (A..E)."""
    try:
        return SQALE_RATING_LETTERS[int(float(str(raw)))]
    except (ValueError, KeyError) as exc:
        raise LedgerInputError(f"unknown sqale_rating: {raw!r}") from exc


def load_config(env: dict[str, str]) -> Config:
    hourly_rate = _env_float(env, "HOURLY_RATE")
    low, high = HOURLY_RATE_RANGE
    if not low <= hourly_rate <= high:
        raise LedgerInputError(f"HOURLY_RATE must be within ${low:.0f}-${high:.0f}/h, got {hourly_rate}")
    overload = _env_float(env, "OVERLOAD_COEFF")
    discount = _env_float(env, "DISCOUNT_RATE")
    if overload <= 0 or discount < 0:
        raise LedgerInputError("OVERLOAD_COEFF must be > 0 and DISCOUNT_RATE >= 0")
    return Config(hourly_rate, overload, discount)


def _env_float(env: dict[str, str], name: str) -> float:
    raw = env.get(name)
    if raw is None or raw.strip() == "":
        raise LedgerInputError(f"environment variable {name} is not set")
    try:
        return float(raw)
    except ValueError as exc:
        raise LedgerInputError(f"{name} is not a number: {raw!r}") from exc


# ---------------------------------------------------------------- economics

def debt_hours(baseline: Baseline) -> float:
    return baseline.sqale_index / MINUTES_PER_HOUR


def principal(baseline: Baseline, config: Config) -> float:
    """Remediation cost of the whole debt today: sqale_index / 60 * rate * overload."""
    return debt_hours(baseline) * config.hourly_rate * config.overload_coeff


def cost_of_delay(baseline: Baseline, config: Config) -> float:
    """Monthly loss while the debt stays: 1 engineer-hour per 100 ncloc per month."""
    return baseline.ncloc / LINES_PER_LOST_HOUR * config.hourly_rate


def simulate_npv(monthly_benefit: float, investment: float, discount_rate: float,
                 months: int = HORIZON_MONTHS) -> list[MonthRow]:
    """Cumulative discounted NPV for t = 1..months (benefit arrives at the end of each month)."""
    rows = []
    cumulative = 0.0
    for t in range(1, months + 1):
        factor = 1 / (1 + discount_rate) ** t
        discounted = monthly_benefit * factor
        cumulative += discounted
        rows.append(MonthRow(t, factor, monthly_benefit, discounted, cumulative, cumulative - investment))
    return rows


def payback_month(rows: list[MonthRow]) -> int | None:
    for row in rows:
        if row.npv > 0:
            return row.month
    return None


def tdd_percent(baseline: Baseline) -> float:
    """SQALE technical debt density: debt hours / (ncloc * 0.5 h) * 100 %."""
    return debt_hours(baseline) / (baseline.ncloc * 0.5) * 100


def break_even_tdd_percent(config: Config, months: int = HORIZON_MONTHS) -> float:
    """Largest TDD for which NPV still turns positive within the horizon.

    Principal / CoD = (sqale_index/60 * k) / (ncloc/100) = 50 * k * TDD, so payback within ``months`` needs
    50 * k * TDD < sum 1/(1+r)^t  ->  TDD_max = annuity / (50 * k). The hourly rate cancels out.
    """
    annuity = sum(1 / (1 + config.discount_rate) ** t for t in range(1, months + 1))
    return annuity / (50 * config.overload_coeff) * 100


# ---------------------------------------------------------------- output

def money(value: float) -> str:
    sign = "-" if value < 0 else ""
    return f"{sign}${abs(value):,.2f}"


def render_ledger(baseline: Baseline, config: Config, rows: list[MonthRow], generated_at: str) -> str:
    debt = principal(baseline, config)
    cod = cost_of_delay(baseline, config)
    payback = payback_month(rows)
    gate = "PASS" if payback is not None else "FAIL"
    hours = debt_hours(baseline)
    lines = [
        "# SOFTWARE ECONOMICS & FINANCIAL QUALITY LEDGER",
        "",
        f"> Згенеровано автоматично скриптом `scripts/financial_ledger.py` ({generated_at}).",
        "> Усі вхідні метрики прочитано з JSON-відповіді SonarCloud API, нічого не введено вручну.",
        "",
        "## 1. Вхідні параметри аудиту (System Baseline)",
        "",
        "| Параметр | Значення |",
        "|---|---|",
        f"| Проєкт (key) | `{baseline.project_key}` ({baseline.project_name}) |",
        f"| Масштаб системи (ncloc) | {baseline.ncloc:,} |",
        f"| Початковий технічний борг (sqale_index) | {baseline.sqale_index:,} хв = {hours:,.2f} год |",
        f"| Рейтинг супроводжуваності (sqale_rating) | {baseline.sqale_rating} |",
        f"| Щільність боргу TDD | {tdd_percent(baseline):.2f}% |",
        f"| HOURLY_RATE | ${config.hourly_rate:,.2f}/год |",
        f"| OVERLOAD_COEFF | {config.overload_coeff} |",
        f"| DISCOUNT_RATE | {config.discount_rate} ({config.discount_rate * 100:.2f}% на місяць) |",
        "",
        "## 2. Капіталізація інвестиційних маркерів",
        "",
        "| Маркер | Формула | Значення |",
        "|---|---|---|",
        f"| Тіло боргу (Principal, вартість рефакторингу) | {baseline.sqale_index:,} / 60 × "
        f"{config.hourly_rate:g} × {config.overload_coeff:g} | {money(debt)} |",
        f"| Щомісячні збитки від бездіяльності (Cost of Delay) | {baseline.ncloc:,} / 100 × "
        f"{config.hourly_rate:g} | {money(cod)} / міс |",
        "",
        f"## 3. Динаміка окупності інвестицій ({len(rows)} ітерацій розробки)",
        "",
        "| Місяць t | Коефіцієнт дисконтування 1/(1+r)^t | Щомісячний прибуток (CoD) | "
        "Дисконтований прибуток | Кумулятивний дисконтований прибуток | Кумулятивний NPV_t | Стан |",
        "|---|---|---|---|---|---|---|",
    ]
    for row in rows:
        state = "в плюсі" if row.npv > 0 else "в мінусі"
        lines.append(f"| {row.month} | {row.discount_factor:.6f} | {money(row.nominal_benefit)} | "
                     f"{money(row.discounted_benefit)} | {money(row.cumulative_discounted)} | "
                     f"{money(row.npv)} | {state} |")
    lines += ["", "## 4. Фінальний інженерно-економічний вердикт", ""]
    if payback is not None:
        lines.append(f"Точка виходу в нуль (Payback Period): окупність настає на **{payback}-му місяці** "
                     f"після рефакторингу (NPV_{payback} = {money(rows[payback - 1].npv)}).")
    else:
        lines.append(f"Окупність за {len(rows)} місяців **не досягнута** (NPV_{len(rows)} = "
                     f"{money(rows[-1].npv)}).")
    lines.append(f"NPV наприкінці горизонту: {money(rows[-1].npv)}. Межа окупності за {len(rows)} міс.: "
                 f"TDD < {break_even_tdd_percent(config, len(rows)):.2f}% (межа не залежить від HOURLY_RATE).")
    lines += ["", "## 5. СТАТУС ВОРІТ ЯКОСТІ (Quality Gate)", "", f"**{gate}**", ""]
    if gate == "FAIL":
        lines += ["> ⚠️ Архітектурне попередження: борг занадто великий відносно розміру системи; повний "
                  "рефакторинг не окупається за 6 місяців. Потрібне поетапне погашення боргу (за модулями з "
                  "найвищим TDD) або перегляд архітектури.", ""]
    return "\n".join(lines)


def main(argv: list[str]) -> int:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    report_path = Path(argv[1]) if len(argv) > 1 else DEFAULT_REPORT
    output_path = Path(os.environ.get("LEDGER_OUTPUT", str(DEFAULT_OUTPUT)))
    try:
        baseline = load_baseline(report_path)
        config = load_config(dict(os.environ))
    except LedgerInputError as exc:
        print(f"[ERROR] {exc}", file=sys.stderr)
        return 1

    rows = simulate_npv(cost_of_delay(baseline, config), principal(baseline, config), config.discount_rate)
    payback = payback_month(rows)
    generated_at = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M UTC")
    output_path.write_text(render_ledger(baseline, config, rows, generated_at) + "\n", encoding="utf-8",
                           newline="\n")

    print(f"Project      : {baseline.project_key} (ncloc={baseline.ncloc:,}, sqale_index={baseline.sqale_index:,} min,"
          f" rating={baseline.sqale_rating})")
    print(f"Principal    : {money(principal(baseline, config))}")
    print(f"Cost of Delay: {money(cost_of_delay(baseline, config))} / month")
    for row in rows:
        print(f"  t={row.month}  factor={row.discount_factor:.6f}  cumulative={money(row.cumulative_discounted):>16}"
              f"  NPV={money(row.npv):>16}")
    if payback is not None:
        print(f"Payback      : month {payback}")
    print(f"Quality gate : {'PASS' if payback is not None else 'FAIL'}")
    print(f"[OK] Financial ledger written to {output_path.resolve()}")
    return 0 if payback is not None else 2


if __name__ == "__main__":
    sys.exit(main(sys.argv))
