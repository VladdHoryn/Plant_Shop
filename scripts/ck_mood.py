#!/usr/bin/env python3
"""MOOD project factors (Abreu) for the Lab 8 report.

    python scripts/ck_mood.py <ck class.csv> <jdeps-class.txt> <out.json>

MHF = non-public methods / all methods          (from CK class.csv, src/main only)
AHF = non-public fields  / all fields           (from CK class.csv, src/main only)
COF = couplings between project classes / (TC^2 - TC)   (from `jdeps -verbose:class -filter:none`)

Simplifications, stated in the report:
* visibility is binary: public = visible (factor 0), everything else = hidden (factor 1). Abreu weights a
  protected/package member by the share of classes that can see it; this project has almost no such members.
* interface methods without a modifier are implicitly public; CK reports them as "default", so for
  interfaces every non-private method is counted as public.
* CK 0.7.0 skips Java records, so MHF/AHF cover the classes, enums and interfaces that CK reports. COF uses
  bytecode (jdeps), so records are included there: TC = every com.plantshop type compiled from src/main.
* a coupling is an ordered pair (A, B), A != B, where A references B in its bytecode (any use: field,
  parameter, call, inheritance). Abreu excludes inheritance; with no own hierarchies the difference is nil.
"""
import csv
import json
import re
import sys

PROJECT_PREFIX = "com.plantshop."
EDGE = re.compile(r"^\s+(\S+)\s+->\s+(\S+)\s")


def visibility_counts(csv_path):
    methods = public_methods = fields = public_fields = 0
    with open(csv_path, newline="", encoding="utf-8") as handle:
        for row in csv.DictReader(handle):
            total_m = int(row["totalMethodsQty"])
            pub_m = int(row["publicMethodsQty"])
            if row["type"] == "interface":
                pub_m = total_m - int(row["privateMethodsQty"])
            methods += total_m
            public_methods += pub_m
            fields += int(row["totalFieldsQty"])
            public_fields += int(row["publicFieldsQty"])
    return methods, public_methods, fields, public_fields


def couplings(jdeps_path):
    classes, pairs = set(), set()
    with open(jdeps_path, encoding="utf-8") as handle:
        for line in handle:
            match = EDGE.match(line)
            if not match:
                continue
            source, target = match.groups()
            if source.startswith(PROJECT_PREFIX):
                classes.add(source)
                if target.startswith(PROJECT_PREFIX) and target != source:
                    pairs.add((source, target))
    # a target outside the compiled set (e.g. a missing class) is not a project class
    return classes, {(a, b) for a, b in pairs if b in classes}


def main(argv):
    if len(argv) != 4:
        sys.exit(__doc__)
    methods, public_methods, fields, public_fields = visibility_counts(argv[1])
    classes, pairs = couplings(argv[2])
    tc = len(classes)
    hidden_m, hidden_f = methods - public_methods, fields - public_fields
    mhf, ahf = hidden_m / methods, hidden_f / fields
    cof = len(pairs) / (tc * tc - tc)
    result = {
        "MHF": {"hiddenMethods": hidden_m, "allMethods": methods, "value": round(mhf, 4)},
        "AHF": {"hiddenFields": hidden_f, "allFields": fields, "value": round(ahf, 4)},
        "COF": {"couplings": len(pairs), "TC": tc, "maxCouplings": tc * tc - tc, "value": round(cof, 4)},
        "sources": {"MHF/AHF": "CK class.csv (records not reported by CK 0.7.0)",
                    "COF": "jdeps -verbose:class -filter:none over */target/classes"},
    }
    with open(argv[3], "w", encoding="utf-8", newline="\n") as out:
        json.dump(result, out, indent=2)
        out.write("\n")
    print(f"MHF = {hidden_m} / {methods} = {mhf:.4f} ({mhf:.1%})")
    print(f"AHF = {hidden_f} / {fields} = {ahf:.4f} ({ahf:.1%})")
    print(f"COF = {len(pairs)} / ({tc}^2 - {tc}) = {len(pairs)} / {tc * tc - tc} = {cof:.4f} ({cof:.2%})")


if __name__ == "__main__":
    main(sys.argv)
