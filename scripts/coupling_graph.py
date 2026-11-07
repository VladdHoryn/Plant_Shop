#!/usr/bin/env python3
"""Class dependency graph and coupling matrix from `jdeps -verbose:class -filter:none` output.

    python scripts/coupling_graph.py <jdeps-class.txt> <focus FQN[,FQN...]> <out prefix> [module package]

Writes, for project classes only (com.plantshop.*):
  <prefix>.dot         Graphviz graph of the focus classes with their efferent and afferent couplings
                       (focus classes are filled red, the "epicentre")
  <prefix>.mmd         the same graph as a Mermaid flowchart (renders in Obsidian/Notion/GitHub)
  <prefix>-matrix.md   coupling matrix: the 12 classes of the module package with the most efferent project
                       couplings x target package; a cell is the number of distinct classes referenced there
"""
import re
import sys
from collections import defaultdict

PROJECT_PREFIX = "com.plantshop."
EDGE = re.compile(r"^\s+(\S+)\s+->\s+(\S+)\s")


def load_edges(path):
    edges = defaultdict(set)
    with open(path, encoding="utf-8") as handle:
        for line in handle:
            match = EDGE.match(line)
            if match:
                source, target = match.groups()
                if source.startswith(PROJECT_PREFIX) and target.startswith(PROJECT_PREFIX) and source != target:
                    edges[source].add(target)
    return edges


def short(fqn):
    return fqn.rsplit(".", 1)[-1].replace("$", ".")


def package(fqn):
    return fqn.rsplit(".", 1)[0]


def node_id(fqn):
    return re.sub(r"\W", "_", fqn[len(PROJECT_PREFIX):])


def write_graph(edges, focus, prefix):
    afferent = {s for s, targets in edges.items() for f in focus if f in targets and s not in focus}
    efferent = {t for f in focus for t in edges.get(f, ())} - set(focus)
    nodes = set(focus) | afferent | efferent
    graph_edges = sorted({(f, t) for f in focus for t in edges.get(f, ())} |
                         {(s, f) for s in afferent for f in focus if f in edges[s]})
    by_package = defaultdict(list)
    for node in sorted(nodes):
        by_package[package(node)].append(node)

    with open(prefix + ".dot", "w", encoding="utf-8", newline="\n") as dot:
        dot.write("digraph coupling {\n  rankdir=LR;\n  node [shape=box, style=filled, fillcolor=white, "
                  "fontname=Helvetica];\n")
        for index, (pkg, members) in enumerate(sorted(by_package.items())):
            dot.write(f'  subgraph cluster_{index} {{\n    label="{pkg[len(PROJECT_PREFIX):]}";\n')
            for member in members:
                fill = ', fillcolor="#e03131", fontcolor=white' if member in focus else ""
                dot.write(f'    {node_id(member)} [label="{short(member)}"{fill}];\n')
            dot.write("  }\n")
        for source, target in graph_edges:
            dot.write(f"  {node_id(source)} -> {node_id(target)};\n")
        dot.write("}\n")

    with open(prefix + ".mmd", "w", encoding="utf-8", newline="\n") as mmd:
        mmd.write("flowchart LR\n")
        for pkg, members in sorted(by_package.items()):
            mmd.write(f'  subgraph {node_id(pkg + ".")}["{pkg[len(PROJECT_PREFIX):]}"]\n')
            for member in members:
                mmd.write(f'    {node_id(member)}["{short(member)}"]\n')
            mmd.write("  end\n")
        for source, target in graph_edges:
            mmd.write(f"  {node_id(source)} --> {node_id(target)}\n")
        mmd.write("  classDef god fill:#e03131,color:#fff,stroke:#862e2e\n")
        mmd.write("  class " + ",".join(node_id(f) for f in focus) + " god\n")

    for f in focus:
        ce = len(edges.get(f, ()))
        ca = sum(1 for targets in edges.values() if f in targets)
        print(f"{short(f)}: efferent (project classes it uses) = {ce}, afferent (project classes using it) = {ca}")
    return nodes


def write_matrix(edges, module_package, prefix):
    rows = sorted((c for c in edges if c.startswith(module_package)), key=lambda c: (-len(edges[c]), c))[:12]
    columns = sorted({package(t)[len(PROJECT_PREFIX):] for r in rows for t in edges[r]})
    with open(prefix + "-matrix.md", "w", encoding="utf-8", newline="\n") as out:
        out.write("| Клас \\ пакет залежності | " + " | ".join(columns) + " | Σ |\n")
        out.write("|---|" + "---|" * (len(columns) + 1) + "\n")
        for row in rows:
            counts = defaultdict(int)
            for target in edges[row]:
                counts[package(target)[len(PROJECT_PREFIX):]] += 1
            cells = [str(counts[c]) if counts[c] else "·" for c in columns]
            out.write(f"| {short(row)} | " + " | ".join(cells) + f" | **{len(edges[row])}** |\n")


def main(argv):
    if len(argv) < 4:
        sys.exit(__doc__)
    edges = load_edges(argv[1])
    focus = [f for f in argv[2].split(",") if f]
    write_graph(edges, focus, argv[3])
    module_package = argv[4] if len(argv) > 4 else package(package(focus[0])) + "."
    write_matrix(edges, module_package, argv[3])


if __name__ == "__main__":
    main(sys.argv)
