#!/usr/bin/env python3
"""Convert CK class.csv (Chidamber & Kemerer metrics) into a JSON package for the Lab 8 report.

    python scripts/ck_to_json.py <class.csv> <out.json> [--strip /scan/]

The CSV comes from scripts/ck-run.sh, which feeds CK only the src/main/java trees of the modules, mounted as
/scan/<module>/..., so the first path segment after the prefix is the module name. Prints a short table
of the classes sorted by CBO.

Known CK 0.7.0 limitation: Java `record` types are not reported at all (the parser skips them), so DTO records
appear only in the jdeps-based coupling data (scripts/ck_mood.py, scripts/coupling_graph.py).
"""
import csv
import json
import math
import sys

INT_COLUMNS = ("cbo", "cboModified", "fanin", "fanout", "wmc", "dit", "noc", "rfc", "lcom", "totalMethodsQty",
               "staticMethodsQty", "publicMethodsQty", "privateMethodsQty", "protectedMethodsQty",
               "defaultMethodsQty", "abstractMethodsQty", "totalFieldsQty", "staticFieldsQty", "publicFieldsQty",
               "privateFieldsQty", "protectedFieldsQty", "defaultFieldsQty", "finalFieldsQty", "loc")
FLOAT_COLUMNS = ("lcom*", "tcc", "lcc")


def number(value, kind):
    try:
        parsed = kind(float(value))
    except ValueError:
        return None
    return None if isinstance(parsed, float) and math.isnan(parsed) else parsed


def read_classes(path, strip):
    with open(path, newline="", encoding="utf-8") as handle:
        for row in csv.DictReader(handle):
            file = row["file"].replace("\\", "/")
            if strip and file.startswith(strip):
                file = file[len(strip):]
            item = {"module": file.split("/", 1)[0], "file": file, "class": row["class"], "type": row["type"]}
            item.update({column: number(row[column], int) for column in INT_COLUMNS})
            item.update({column: number(row[column], float) for column in FLOAT_COLUMNS})
            yield item


def main(argv):
    if len(argv) < 3:
        sys.exit(__doc__)
    strip = argv[argv.index("--strip") + 1] if "--strip" in argv else ""
    classes = sorted(read_classes(argv[1], strip), key=lambda c: (-(c["cbo"] or 0), c["class"]))
    package = {"tool": "CK 0.7.0 (com.github.mauricioaniche:ck)", "scope": "src/main/java of every module",
               "classCount": len(classes), "classes": classes}
    with open(argv[2], "w", encoding="utf-8", newline="\n") as out:
        json.dump(package, out, indent=2, ensure_ascii=False)
        out.write("\n")
    print(f"{len(classes)} classes -> {argv[2]}")
    print(f"{'CBO':>4} {'WMC':>4} {'LCOM*':>6} {'RFC':>4} {'DIT':>4}  class")
    for c in classes[:15]:
        lcom = "n/a" if c["lcom*"] is None else f"{c['lcom*']:.2f}"
        print(f"{c['cbo']:>4} {c['wmc']:>4} {lcom:>6} {c['rfc']:>4} {c['dit']:>4}  {c['class']}")


if __name__ == "__main__":
    main(sys.argv)
