"""Hand-counted checks of the token classification (run: python -m unittest test_halstead in the image)."""
import math
import os
import tempfile
import unittest

from halstead import analyse, metrics


def measure(java):
    with tempfile.NamedTemporaryFile("w", suffix=".java", delete=False, encoding="utf-8") as f:
        f.write(java)
    try:
        return analyse(f.name)
    finally:
        os.unlink(f.name)


class HalsteadTest(unittest.TestCase):

    def test_simple_method_counted_by_hand(self):
        # int f(int a) { return a + 1; }
        # operators: int x2, ( ) { return + ; }  -> 8 unique, 9 total
        # operands:  f, a x2, 1                  -> 3 unique, 4 total
        r = measure("int f(int a) { return a + 1; }")
        self.assertEqual(sorted(r["operators"]), sorted(["int", "(", ")", "{", "return", "+", ";", "}"]))
        self.assertEqual(sum(r["operators"].values()), 9)
        self.assertEqual(dict(r["operands"]), {"f": 1, "a": 2, "1": 1})
        m = metrics(r)
        self.assertAlmostEqual(m["V"], 13 * math.log2(11))          # N = 13, eta = 11
        self.assertAlmostEqual(m["D"], 8 / 2 * 4 / 3)                # eta1/2 * N2/eta2
        self.assertAlmostEqual(m["E"], m["V"] * m["D"])

    def test_compound_operators_are_single_operators(self):
        r = measure("boolean g(int x) { return x == 1 && x != 2 || x >= 3; }")
        for op in ["==", "&&", "!=", "||", ">="]:
            self.assertEqual(r["operators"][op], 1, op)
        for fragment in ["=", "&", "|", "!", ">"]:
            self.assertNotIn(fragment, r["operators"], fragment)

    def test_lambda_and_method_reference(self):
        r = measure("void h() { list.forEach(v -> print(v)); list.map(String::trim); }")
        self.assertEqual(r["operators"]["->"], 1)
        self.assertEqual(r["operators"]["::"], 1)

    def test_string_literal_is_one_operand(self):
        r = measure('String s = "a b" + "c\\"d";')
        self.assertEqual(r["operands"]['"a b"'], 1)
        self.assertEqual(r["operands"]['"c\\"d"'], 1)
        self.assertNotIn('"', r["operands"])

    def test_keywords_operators_constants_operands(self):
        r = measure("@Override public boolean ok() { return true; }")
        self.assertIn("@Override", r["operators"])
        self.assertIn("public", r["operators"])
        self.assertIn("true", r["operands"])

    def test_package_and_imports_are_ignored(self):
        r = measure("package a.b;\nimport java.util.List;\nclass C {}")
        self.assertNotIn("package", r["operators"])
        self.assertNotIn("List", r["operands"])
        self.assertEqual(dict(r["operands"]), {"C": 1})


if __name__ == "__main__":
    unittest.main()
