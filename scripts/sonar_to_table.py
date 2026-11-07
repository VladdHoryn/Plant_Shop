#!/usr/bin/env python3
"""Parse raw SonarQube `api/measures/component` responses into a Markdown table.

    python scripts/sonar_to_table.py <response.json>...

A response is either {"component": {"key": ..., "measures": [{"metric": ..., "value": ...}]}} or an error
{"errors": [{"msg": ...}]} (e.g. unknown metric keys); errors are printed as a row, not hidden.
"""
import json
import sys


def main(paths):
    if not paths:
        sys.exit(__doc__)
    rows, metrics = [], []
    for path in paths:
        with open(path, encoding="utf-8") as handle:
            data = json.load(handle)
        if "errors" in data:
            rows.append((path, None, "; ".join(e["msg"] for e in data["errors"])))
            continue
        component = data["component"]
        values = {m["metric"]: m.get("value", m.get("period", {}).get("value", "")) for m in component["measures"]}
        metrics += [m for m in values if m not in metrics]
        rows.append((path, component["key"], values))
    print("| Компонент | " + " | ".join(metrics) + " |")
    print("|---|" + "---|" * len(metrics))
    for path, key, values in rows:
        if key is None:
            print(f"| `{path}` | ERROR: {values} |" + " |" * (len(metrics) - 1))
        else:
            print(f"| `{key}` | " + " | ".join(values.get(m, "—") for m in metrics) + " |")


if __name__ == "__main__":
    main(sys.argv[1:])
