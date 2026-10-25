#!/usr/bin/env python3
"""Halstead metrics for Java sources, with the token classification of the lab assignment.

Operators: keywords (if, return, new, class, public, int, ...), operation signs, separators ( ) { } [ ] ; , .
           and annotations (@Transactional - a modifier, like a keyword).
Operands:  identifiers (variables, fields, methods, types, arguments), constants (true/false/null),
           string, character and number literals.
Not counted: comments, whitespace, package and import declarations (no algorithm in them).
Normalization of the Pygments token stream: multi-character operators (==, &&, ->, ::, ...) are glued back
into one operator, and the parts of a string literal form one operand.

    halstead.py [--format table|csv] [--vocabulary] FILE...

Derived metrics: eta = eta1 + eta2, N = N1 + N2, V = N * log2(eta), D = eta1/2 * N2/eta2, E = V * D.
The ALL row counts the whole set as one program: unique operators/operands over all files (global vocabulary).
"""
import argparse
import math
import sys
from collections import Counter

from pygments.lexers import JavaLexer
from pygments.token import Comment, Keyword, Literal, Name, Operator, Punctuation, Text, Whitespace

OPERAND_KEYWORDS = Keyword.Constant  # true, false, null
# Pygments emits every multi-character Java operator one character at a time ("&&" -> "&", "&").
# Adjacent characters are glued back, longest match first. ">>" and ">>>" are left out on purpose:
# in this code base they close nested generics (Map<K, List<V>>), not bit shifts.
JAVA_OPERATORS = sorted(["->", "::", "==", "!=", "<=", ">=", "&&", "||", "++", "--", "+=", "-=", "*=", "/=",
                         "%=", "&=", "|=", "^=", "<<", "<<=", "..."], key=len, reverse=True)


def is_symbol(token_type):
    return token_type in Operator or token_type in Punctuation


def normalized_tokens(text):
    """Pygments tokens with multi-character operators glued and each string literal as one token."""
    raw = list(JavaLexer().get_tokens(text))
    i = 0
    while i < len(raw):
        token_type, value = raw[i]
        if is_symbol(token_type):
            run = value
            j = i + 1
            while j < len(raw) and is_symbol(raw[j][0]):
                run += raw[j][1]
                j += 1
            k = 0
            while k < len(run):
                op = next((o for o in JAVA_OPERATORS if run.startswith(o, k)), run[k])
                yield (Operator if len(op) > 1 else token_type), op
                k += len(op)
            i = j
        elif token_type in Literal.String and token_type not in Literal.String.Char:
            literal = value
            i += 1
            while i < len(raw) and raw[i][0] in Literal.String and raw[i][0] not in Literal.String.Char:
                literal += raw[i][1]
                i += 1
            yield Literal.String, literal
        else:
            yield token_type, value
            i += 1


def classify(token_type):
    if token_type in Comment or token_type in Text or token_type in Whitespace:
        return None
    if token_type in OPERAND_KEYWORDS:
        return "operand"
    if token_type in Keyword or token_type in Operator or token_type in Punctuation or token_type in Name.Decorator:
        return "operator"
    if token_type in Name or token_type in Literal:
        return "operand"
    return None


def analyse(path):
    with open(path, encoding="utf-8-sig") as source:
        text = source.read()
    operators, operands, code_lines = Counter(), Counter(), set()
    skip_declaration = False
    line = 1
    for token_type, value in normalized_tokens(text):
        kind = classify(token_type)
        if token_type in Keyword.Namespace and value in ("package", "import"):
            skip_declaration = True
        if kind and not skip_declaration:
            (operators if kind == "operator" else operands)[value] += 1
            code_lines.add(line)
        if skip_declaration and value == ";":
            skip_declaration = False
        line += value.count("\n")
    return {"file": path, "loc": len(text.splitlines()), "sloc": len(code_lines),
            "operators": operators, "operands": operands}


def derive(eta1, eta2, n1, n2):
    eta, length = eta1 + eta2, n1 + n2
    volume = length * math.log2(eta) if eta > 1 else 0.0
    difficulty = (eta1 / 2) * (n2 / eta2) if eta2 else 0.0
    return {"eta1": eta1, "eta2": eta2, "N1": n1, "N2": n2, "eta": eta, "N": length,
            "V": volume, "D": difficulty, "E": volume * difficulty}


def metrics(result):
    m = derive(len(result["operators"]), len(result["operands"]),
               sum(result["operators"].values()), sum(result["operands"].values()))
    m.update(file=result["file"], loc=result["loc"], sloc=result["sloc"])
    return m


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--format", choices=["table", "csv"], default="table")
    parser.add_argument("--vocabulary", action="store_true", help="list every operator and operand with its count")
    parser.add_argument("files", nargs="+")
    args = parser.parse_args()

    results = [analyse(f) for f in args.files]
    rows = sorted((metrics(r) for r in results), key=lambda m: m["E"], reverse=True)
    total_ops, total_opnds = Counter(), Counter()
    for r in results:
        total_ops.update(r["operators"])
        total_opnds.update(r["operands"])
    overall = derive(len(total_ops), len(total_opnds), sum(total_ops.values()), sum(total_opnds.values()))
    overall.update(file=f"ALL ({len(results)} files)", loc=sum(r["loc"] for r in results),
                   sloc=sum(r["sloc"] for r in results))

    columns = ["file", "loc", "sloc", "eta1", "eta2", "N1", "N2", "eta", "N", "V", "D", "E"]
    if args.format == "csv":
        print(",".join(columns))
        for m in rows + [overall]:
            print(",".join(f"{m[c]:.2f}" if isinstance(m[c], float) else str(m[c]) for c in columns))
    else:
        header = f"{'#':>3} {'file':<60} {'LOC':>5} {'SLOC':>5} {'η1':>4} {'η2':>5} {'N1':>6} {'N2':>6} " \
                 f"{'η':>5} {'N':>6} {'V':>9} {'D':>7} {'E':>12}"
        print(header)
        print("-" * len(header))
        for i, m in enumerate(rows + [overall], start=1):
            rank = str(i) if i <= len(rows) else ""
            name = m["file"] if len(m["file"]) <= 60 else "…" + m["file"][-59:]
            print(f"{rank:>3} {name:<60} {m['loc']:>5} {m['sloc']:>5} {m['eta1']:>4} {m['eta2']:>5} {m['N1']:>6} "
                  f"{m['N2']:>6} {m['eta']:>5} {m['N']:>6} {m['V']:>9.1f} {m['D']:>7.2f} {m['E']:>12.1f}")

    if args.vocabulary:
        for r in results:
            print(f"\n{r['file']}\n  operators ({len(r['operators'])} unique, {sum(r['operators'].values())} total): "
                  + ", ".join(f"{k}×{v}" for k, v in r["operators"].most_common()))
            print(f"  operands ({len(r['operands'])} unique, {sum(r['operands'].values())} total): "
                  + ", ".join(f"{k}×{v}" for k, v in r["operands"].most_common()))
    return 0


if __name__ == "__main__":
    sys.exit(main())
