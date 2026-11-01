#!/usr/bin/env python3
"""Converts the CSV report of Lizard into the JSON build artifact of the code-metrics workflow.

    lizard -l java -C 1000 --csv ./*/src/main/java > lizard-report.csv
    python scripts/lizard_to_json.py lizard-report.csv lizard-report.json [--cc-limit 10]

Lizard writes one row per function and no header:
    NLOC, CCN, token, PARAM, length, location, file, function, long_name, start, end
The JSON keeps every function (file, method, CC, NLOC, parameters, lines) and adds one aggregate per file
(max and summed CC), which is the complexity input of the Hotspot matrix (docs/reports/lab-07-report.md).
A short markdown summary goes to stdout, so the workflow can append it to the job summary.
Exit code 0 = converted (the report never fails the build), 2 = the CSV is not a Lizard report.
"""
import argparse
import csv
import json
import sys
from collections import defaultdict

COLUMNS = ("nloc", "ccn", "tokens", "params", "length", "location", "file", "function", "long_name",
           "start_line", "end_line")


def read_functions(path):
    functions = []
    with open(path, newline="", encoding="utf-8") as report:
        for number, row in enumerate(csv.reader(report), start=1):
            if not row or row[0].strip().upper() == "NLOC":  # blank line or a header (lizard -V --csv)
                continue
            if len(row) != len(COLUMNS):
                raise ValueError(f"{path}:{number}: expected {len(COLUMNS)} columns, got {len(row)}")
            cell = dict(zip(COLUMNS, row))
            functions.append({
                "file": cell["file"].replace("\\", "/").removeprefix("./"),
                "method": cell["function"],
                "cc": int(cell["ccn"]),
                "nloc": int(cell["nloc"]),
                "params": int(cell["params"]),
                "tokens": int(cell["tokens"]),
                "start_line": int(cell["start_line"]),
                "end_line": int(cell["end_line"]),
            })
    return functions


def aggregate_files(functions):
    by_file = defaultdict(list)
    for function in functions:
        by_file[function["file"]].append(function)
    files = [{
        "file": name,
        "functions": len(members),
        "max_cc": max(f["cc"] for f in members),
        "sum_cc": sum(f["cc"] for f in members),
        "nloc": sum(f["nloc"] for f in members),
    } for name, members in by_file.items()]
    return sorted(files, key=lambda f: (-f["sum_cc"], -f["max_cc"], f["file"]))


def markdown_summary(report, top):
    summary = report["summary"]
    lines = [
        "## Lizard complexity report",
        "",
        f"{summary['functions']} functions in {summary['files']} files, highest CC {summary['max_cc']}, "
        f"{len(summary['over_limit'])} above CC {report['cc_limit']}.",
        "",
        f"| File (top {top} by summed CC) | Functions | Max CC | Sum CC |",
        "|---|---:|---:|---:|",
    ]
    lines += [f"| `{f['file']}` | {f['functions']} | {f['max_cc']} | {f['sum_cc']} |" for f in report["files"][:top]]
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("csv_report")
    parser.add_argument("json_report")
    parser.add_argument("--cc-limit", type=int, default=10, help="functions above this CC are listed (default 10)")
    parser.add_argument("--top", type=int, default=10, help="files shown in the markdown summary (default 10)")
    args = parser.parse_args()

    try:
        functions = read_functions(args.csv_report)
    except (OSError, ValueError) as error:
        print(f"lizard_to_json: {error}", file=sys.stderr)
        return 2

    files = aggregate_files(functions)
    report = {
        "tool": "lizard",
        "cc_limit": args.cc_limit,
        "summary": {
            "files": len(files),
            "functions": len(functions),
            "max_cc": max((f["cc"] for f in functions), default=0),
            "over_limit": [f"{f['file']}:{f['start_line']} {f['method']} (CC {f['cc']})"
                           for f in functions if f["cc"] > args.cc_limit],
        },
        "files": files,
        "functions": functions,
    }
    with open(args.json_report, "w", encoding="utf-8") as out:
        json.dump(report, out, indent=2)
        out.write("\n")
    print(markdown_summary(report, args.top))
    return 0


if __name__ == "__main__":
    sys.exit(main())
