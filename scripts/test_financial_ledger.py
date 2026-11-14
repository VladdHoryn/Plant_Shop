"""Unit tests for scripts/financial_ledger.py (stdlib unittest).

Run: python -m unittest discover -s scripts -p "test_financial_ledger.py" -v
The numbers below are synthetic test fixtures, not audit data.
"""

import json
import tempfile
import unittest
from pathlib import Path

import financial_ledger as fl


def report(ncloc="10000", sqale_index="6000", rating="1.0"):
    return {"component": {"key": "demo", "name": "demo", "measures": [
        {"metric": "ncloc", "value": ncloc},
        {"metric": "sqale_index", "value": sqale_index},
        {"metric": "sqale_rating", "value": rating},
    ]}}


def write(tmp: Path, payload) -> Path:
    path = tmp / "report.json"
    path.write_text(json.dumps(payload), encoding="utf-8")
    return path


CONFIG = fl.Config(hourly_rate=40.0, overload_coeff=1.3, discount_rate=0.01)


class LoadBaselineTest(unittest.TestCase):

    def test_reads_metrics_from_json(self):
        with tempfile.TemporaryDirectory() as tmp:
            baseline = fl.load_baseline(write(Path(tmp), report(rating="3.0")))
        self.assertEqual((10000, 6000, "C"), (baseline.ncloc, baseline.sqale_index, baseline.sqale_rating))

    def test_missing_metric_is_rejected(self):
        payload = report()
        payload["component"]["measures"].pop(1)
        with tempfile.TemporaryDirectory() as tmp, self.assertRaises(fl.LedgerInputError):
            fl.load_baseline(write(Path(tmp), payload))

    def test_sonar_error_response_is_rejected(self):
        with tempfile.TemporaryDirectory() as tmp, self.assertRaises(fl.LedgerInputError):
            fl.load_baseline(write(Path(tmp), {"errors": [{"msg": "Component key 'x' not found"}]}))


class LoadConfigTest(unittest.TestCase):

    def test_rate_outside_range_is_rejected(self):
        with self.assertRaises(fl.LedgerInputError):
            fl.load_config({"HOURLY_RATE": "80", "OVERLOAD_COEFF": "1.3", "DISCOUNT_RATE": "0.01"})

    def test_missing_variable_is_rejected(self):
        with self.assertRaises(fl.LedgerInputError):
            fl.load_config({"HOURLY_RATE": "40", "OVERLOAD_COEFF": "1.3"})


class EconomicsTest(unittest.TestCase):

    def test_principal_and_cost_of_delay(self):
        baseline = fl.Baseline("demo", "demo", ncloc=10000, sqale_index=6000, sqale_rating="A")
        self.assertAlmostEqual(100 * 40 * 1.3, fl.principal(baseline, CONFIG))   # 6000/60 = 100 h
        self.assertAlmostEqual(100 * 40, fl.cost_of_delay(baseline, CONFIG))     # 10000/100 = 100 h

    def test_npv_is_cumulative_and_discounted(self):
        rows = fl.simulate_npv(monthly_benefit=101.0, investment=150.0, discount_rate=0.01, months=2)
        self.assertAlmostEqual(100.0 - 150.0, rows[0].npv)
        self.assertAlmostEqual(100.0 + 101 / 1.01 ** 2 - 150.0, rows[1].npv)
        self.assertEqual(2, fl.payback_month(rows))

    def test_payback_needs_strictly_positive_npv(self):
        rows = fl.simulate_npv(monthly_benefit=100.0, investment=100.0, discount_rate=0.0, months=1)
        self.assertIsNone(fl.payback_month(rows))

    def test_heavy_debt_fails_gate(self):
        # TDD = 50% -> principal = 65 * TDD * CoD = 32.5 months of CoD, far beyond 6 months
        baseline = fl.Baseline("demo", "demo", ncloc=10000, sqale_index=150000, sqale_rating="E")
        rows = fl.simulate_npv(fl.cost_of_delay(baseline, CONFIG), fl.principal(baseline, CONFIG), 0.01)
        self.assertIsNone(fl.payback_month(rows))
        self.assertIn("**FAIL**", fl.render_ledger(baseline, CONFIG, rows, "test"))

    def test_break_even_tdd_matches_simulation(self):
        limit = fl.break_even_tdd_percent(CONFIG)            # about 8.92 %
        for tdd, expect_pass in ((limit - 0.05, True), (limit + 0.05, False)):
            sqale_index = round(tdd / 100 * 100000 * 0.5 * 60)  # ncloc = 100000
            baseline = fl.Baseline("demo", "demo", 100000, sqale_index, "B")
            rows = fl.simulate_npv(fl.cost_of_delay(baseline, CONFIG), fl.principal(baseline, CONFIG), 0.01)
            self.assertEqual(expect_pass, fl.payback_month(rows) is not None, tdd)


if __name__ == "__main__":
    unittest.main()
