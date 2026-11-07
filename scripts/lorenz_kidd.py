#!/usr/bin/env python3
"""Lorenz-Kidd inheritance metrics for a chain of Java classes (Lab 8, task 3).

    python scripts/lorenz_kidd.py [--ck class.csv] <Root.java> <Child.java> <GrandChild.java> ...

Per class:
  DIT  depth of inheritance tree. With --ck the value is taken from CK, which counts java.lang.Object, so a class
       without `extends` has DIT = 1. Without --ck it is computed from the `extends` clauses of the given files.
  NOM  public methods declared in the class (constructors excluded)
  NMO  overridden methods: declared with @Override (equals/hashCode/toString are not counted)
  NMA  added methods: public methods declared without @Override
  PIM  public interface size: public methods callable on the class, own and inherited from the given chain
       (methods of java.lang.Object are not counted)
The parser is a line-based regex for plain classes like the ones of this task, not a general Java parser.
"""
import csv
import re
import sys

CLASS = re.compile(r"\bclass\s+(\w+)(?:\s+extends\s+(\w+))?")
METHOD = re.compile(r"^\s*public\s+(?:(?:final|static|synchronized)\s+)*[\w<>\[\],.? ]+?\s+(\w+)\s*\(([^)]*)\)")
OBJECT_METHODS = {"equals", "hashCode", "toString"}


def parse(path):
    with open(path, encoding="utf-8") as handle:
        lines = handle.readlines()
    name = parent = None
    methods, override_pending = {}, False
    for line in lines:
        stripped = line.strip()
        if name is None and (match := CLASS.search(line)):
            name, parent = match.groups()
            continue
        if stripped.startswith("@Override"):
            override_pending = True
            continue
        if (match := METHOD.match(line)) and name:
            method, params = match.groups()
            arity = 0 if not params.strip() else params.count(",") + 1
            methods[(method, arity)] = override_pending
        if stripped and not stripped.startswith(("@", "/", "*")):
            override_pending = False
    return name, parent, methods


def ck_dit(path):
    dit = {}
    with open(path, newline="", encoding="utf-8") as handle:
        for row in csv.DictReader(handle):
            dit[row["class"].rsplit(".", 1)[-1]] = int(row["dit"])
    return dit


def main(argv):
    ck = {}
    if argv[:1] == ["--ck"]:
        ck, argv = ck_dit(argv[1]), argv[2:]
    if not argv:
        sys.exit(__doc__)
    classes = {}
    for path in argv:
        name, parent, methods = parse(path)
        classes[name] = (parent, methods)

    def chain(name):
        result = []
        while name in classes:
            result.append(name)
            name = classes[name][0]
        return result

    print(f"{'Class':<22} {'DIT':>3} {'NOM':>3} {'NMO':>3} {'NMA':>3} {'PIM':>3}  overridden")
    for name, (parent, methods) in classes.items():
        lineage = chain(name)
        dit = ck.get(name, len(lineage))   # own classes + java.lang.Object
        overridden = sorted(m for (m, _), o in methods.items() if o and m not in OBJECT_METHODS)
        added = [m for (m, _), o in methods.items() if not o]
        pim = {key for cls in lineage for key in classes[cls][1] if key[0] not in OBJECT_METHODS}
        print(f"{name:<22} {dit:>3} {len(methods):>3} {len(overridden):>3} {len(added):>3} {len(pim):>3}  "
              f"{', '.join(overridden) or '-'}")


if __name__ == "__main__":
    main(sys.argv[1:])
